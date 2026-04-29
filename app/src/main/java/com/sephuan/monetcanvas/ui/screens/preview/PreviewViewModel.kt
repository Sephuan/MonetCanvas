package com.sephuan.monetcanvas.ui.screens.preview

import android.app.WallpaperManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sephuan.monetcanvas.R
import com.sephuan.monetcanvas.data.datastore.SettingsDataStore
import com.sephuan.monetcanvas.data.db.WallpaperEntity
import com.sephuan.monetcanvas.data.model.ColorRegion
import com.sephuan.monetcanvas.data.model.FillMode
import com.sephuan.monetcanvas.data.model.FramePickPosition
import com.sephuan.monetcanvas.data.model.ImageAdjustment
import com.sephuan.monetcanvas.data.model.MonetRule
import com.sephuan.monetcanvas.data.model.TonePreference
import com.sephuan.monetcanvas.data.model.WallpaperType
import com.sephuan.monetcanvas.data.repository.WallpaperRepository
import com.sephuan.monetcanvas.util.ColorEngine
import com.sephuan.monetcanvas.util.ExtractedColors
import com.sephuan.monetcanvas.util.LiveWallpaperSetter
import com.sephuan.monetcanvas.util.WallpaperSetter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ApplyState {
    IDLE,
    APPLYING,
    WAITING_CONFIRM,
    SUCCESS,
    FAILED
}

enum class LiveWpResult {
    IDLE,
    FAILED
}

private data class ColorAnalysisSnapshot(
    val wallpaperId: Long,
    val filePath: String,
    val ruleKey: String,
    val colors: ExtractedColors?
)

@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val repository: WallpaperRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    companion object {
        private const val TAG = "PreviewVM"
        private const val ADJUSTMENT_SAVE_DEBOUNCE_MS = 180L

        private const val LIVE_CONFIRM_POLL_INTERVAL_MS = 250L
        private const val LIVE_CONFIRM_TIMEOUT_MS = 5_000L

        private const val BANNER_SUCCESS_DURATION_MS = 1200L

        // 动态壁纸确认后，等待 Service 写入 seed 的最长时间
        private const val LIVE_SEED_WAIT_TIMEOUT_MS = 2500L
        private const val LIVE_SEED_WAIT_INTERVAL_MS = 100L
    }

    private val _extractedColors = MutableStateFlow<ExtractedColors?>(null)
    val extractedColors: StateFlow<ExtractedColors?> = _extractedColors.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _applyState = MutableStateFlow(ApplyState.IDLE)
    val applyState: StateFlow<ApplyState> = _applyState.asStateFlow()

    private val _liveWpResult = MutableStateFlow(LiveWpResult.IDLE)
    val liveWpResult: StateFlow<LiveWpResult> = _liveWpResult.asStateFlow()

    private val _showBanner = MutableStateFlow(false)
    val showBanner: StateFlow<Boolean> = _showBanner.asStateFlow()

    private val _bannerSuccess = MutableStateFlow(false)
    val bannerSuccess: StateFlow<Boolean> = _bannerSuccess.asStateFlow()

    private var wasOurLiveWallpaperActiveBeforeLaunch = false
    private var previousSysWallpaperId: Int = -1
    private var previousLockWallpaperId: Int = -1

    private var adjustmentSaveJob: Job? = null
    private var pendingAdjustmentWallpaper: WallpaperEntity? = null
    private var pendingAdjustment: ImageAdjustment? = null
    private val lastSavedAdjustments = mutableMapOf<Long, ImageAdjustment>()

    private var confirmCheckJob: Job? = null
    private val analyzeGeneration = AtomicLong(0L)
    private var latestColorSnapshot: ColorAnalysisSnapshot? = null

    fun clearLiveWpResult() {
        _liveWpResult.value = LiveWpResult.IDLE
    }

    fun resetApplyState() {
        _applyState.value = ApplyState.IDLE
    }

    fun dismissBanner() {
        _showBanner.value = false
    }

    fun loadRuleForWallpaper(wallpaper: WallpaperEntity): MonetRule {
        return MonetRule(
            framePosition = runCatching { enumValueOf<FramePickPosition>(wallpaper.framePosition) }
                .getOrDefault(FramePickPosition.FIRST),
            colorRegion = runCatching { enumValueOf<ColorRegion>(wallpaper.colorRegion) }
                .getOrDefault(ColorRegion.FULL_FRAME),
            tonePreference = runCatching { enumValueOf<TonePreference>(wallpaper.tonePreference) }
                .getOrDefault(TonePreference.AUTO),
            manualOverrideColor = wallpaper.manualColor
        )
    }

    suspend fun saveRuleForWallpaper(wallpaper: WallpaperEntity, rule: MonetRule) {
        val base = latestWallpaperOrFallback(wallpaper)
        val updated = base.copy(
            framePosition = rule.framePosition.name,
            colorRegion = rule.colorRegion.name,
            tonePreference = rule.tonePreference.name,
            manualColor = rule.manualOverrideColor
        )
        repository.update(updated)
    }

    fun loadAdjustmentForWallpaper(wallpaper: WallpaperEntity): ImageAdjustment {
        val adjustment = ImageAdjustment(
            fillMode = runCatching { enumValueOf<FillMode>(wallpaper.fillMode) }
                .getOrDefault(FillMode.COVER),
            mirrorHorizontal = wallpaper.mirrorHorizontal,
            mirrorVertical = wallpaper.mirrorVertical,
            brightness = wallpaper.brightness,
            contrast = wallpaper.contrast,
            saturation = wallpaper.saturation,
            backgroundColor = Color(wallpaper.bgColorArgb),
            offsetX = wallpaper.adjustOffsetX,
            offsetY = wallpaper.adjustOffsetY,
            scale = wallpaper.adjustScale
        )
        lastSavedAdjustments[wallpaper.id] = adjustment
        return adjustment
    }

    fun saveAdjustmentForWallpaper(wallpaper: WallpaperEntity, adj: ImageAdjustment) {
        pendingAdjustmentWallpaper = wallpaper
        pendingAdjustment = adj

        adjustmentSaveJob?.cancel()
        adjustmentSaveJob = viewModelScope.launch {
            delay(ADJUSTMENT_SAVE_DEBOUNCE_MS)
            persistWallpaperAdjustment(wallpaper, adj)

            if (pendingAdjustmentWallpaper?.id == wallpaper.id && pendingAdjustment == adj) {
                pendingAdjustmentWallpaper = null
                pendingAdjustment = null
            }
        }
    }

    suspend fun flushAdjustmentForWallpaper(
        wallpaper: WallpaperEntity,
        adj: ImageAdjustment
    ) {
        adjustmentSaveJob?.cancel()
        adjustmentSaveJob = null
        pendingAdjustmentWallpaper = null
        pendingAdjustment = null
        persistWallpaperAdjustment(wallpaper, adj)
    }

    /**
     * 预览页提前取色：
     * 只更新预览色卡与内存快照，绝不提前写入 appSeedColor。
     */
    fun analyzeColors(wallpaper: WallpaperEntity, rule: MonetRule) {
        viewModelScope.launch {
            analyzeColorsInternal(
                wallpaper = wallpaper,
                rule = rule,
                clearOldIfMismatch = true
            )
        }
    }

    private suspend fun analyzeColorsInternal(
        wallpaper: WallpaperEntity,
        rule: MonetRule,
        clearOldIfMismatch: Boolean
    ): ExtractedColors? {
        val requestId = analyzeGeneration.incrementAndGet()
        val expectedRuleKey = buildRuleKey(rule)

        if (clearOldIfMismatch && !isSnapshotFor(wallpaper, rule)) {
            _extractedColors.value = null
        }

        _isAnalyzing.value = true
        Log.d(
            TAG,
            "分析颜色: file=${wallpaper.fileName}, id=${wallpaper.id}, rule=$expectedRuleKey, requestId=$requestId"
        )

        val colors = try {
            ColorEngine.extractColors(
                filePath = wallpaper.filePath,
                rule = rule,
                type = wallpaper.type
            )
        } catch (e: Exception) {
            Log.e(TAG, "analyzeColorsInternal failed", e)
            null
        }

        if (analyzeGeneration.get() != requestId) {
            Log.d(TAG, "分析结果已过期，丢弃 requestId=$requestId")
            return null
        }

        latestColorSnapshot = ColorAnalysisSnapshot(
            wallpaperId = wallpaper.id,
            filePath = wallpaper.filePath,
            ruleKey = expectedRuleKey,
            colors = colors
        )

        _extractedColors.value = colors
        _isAnalyzing.value = false

        Log.d(
            TAG,
            "颜色分析完成: file=${wallpaper.fileName}, color=${colors?.primary?.toHex()}"
        )

        return colors
    }

    private suspend fun getFreshColorsFor(
        wallpaper: WallpaperEntity,
        rule: MonetRule
    ): ExtractedColors? {
        val snapshot = latestColorSnapshot

        if (
            snapshot != null &&
            snapshot.wallpaperId == wallpaper.id &&
            snapshot.filePath == wallpaper.filePath &&
            snapshot.ruleKey == buildRuleKey(rule) &&
            snapshot.colors != null
        ) {
            Log.d(
                TAG,
                "使用匹配缓存颜色: file=${wallpaper.fileName}, color=${snapshot.colors.primary.toHex()}"
            )
            return snapshot.colors
        }

        Log.d(TAG, "缓存颜色不匹配，补取色: file=${wallpaper.fileName}")
        return analyzeColorsInternal(
            wallpaper = wallpaper,
            rule = rule,
            clearOldIfMismatch = true
        )
    }

    fun applyWallpaper(
        context: Context,
        wallpaper: WallpaperEntity,
        target: Int,
        rule: MonetRule,
        adjustment: ImageAdjustment = ImageAdjustment.DEFAULT
    ) {
        if (_applyState.value == ApplyState.APPLYING ||
            _applyState.value == ApplyState.WAITING_CONFIRM
        ) return

        _applyState.value = ApplyState.APPLYING

        viewModelScope.launch {
            when (wallpaper.type) {
                WallpaperType.STATIC -> {
                    flushAdjustmentForWallpaper(wallpaper, adjustment)
                    applyStaticWallpaper(context, wallpaper, target, adjustment, rule)
                }

                WallpaperType.LIVE -> {
                    applyLiveWallpaper(context, wallpaper, rule)
                }
            }
        }
    }

    private suspend fun applyStaticWallpaper(
        context: Context,
        wallpaper: WallpaperEntity,
        target: Int,
        adjustment: ImageAdjustment,
        rule: MonetRule
    ) {
        val success = withContext(Dispatchers.Default) {
            WallpaperSetter.setStaticWallpaper(
                context = context,
                imagePath = wallpaper.filePath,
                target = target,
                adjustment = adjustment
            )
        }

        if (success) {
            repository.markAsUsed(wallpaper.id)

            val currentMode = settingsDataStore.appColorModeFlow.first()
            if (currentMode == SettingsDataStore.COLOR_MODE_RULE) {
                val colors = getFreshColorsFor(wallpaper, rule)
                colors?.primary?.let {
                    Log.d(TAG, "STATIC: 本地写入 appSeedColor=${it.toHex()}")
                    settingsDataStore.saveAppSeedColor(it)
                }
            }

            _applyState.value = ApplyState.SUCCESS
            Toast.makeText(context, "✓", Toast.LENGTH_SHORT).show()
            delay(1000)
            _applyState.value = ApplyState.IDLE
        } else {
            _applyState.value = ApplyState.FAILED
            Toast.makeText(context, "设置失败", Toast.LENGTH_SHORT).show()
            delay(1000)
            _applyState.value = ApplyState.IDLE
        }
    }

    private suspend fun applyLiveWallpaper(
        context: Context,
        wallpaper: WallpaperEntity,
        rule: MonetRule
    ) {
        confirmCheckJob?.cancel()
        confirmCheckJob = null

        val saved = LiveWallpaperSetter.savePendingConfig(
            context = context,
            videoPath = wallpaper.filePath,
            framePosition = rule.framePosition.name,
            colorRegion = rule.colorRegion.name,
            tonePreference = rule.tonePreference.name
        )

        if (!saved) {
            _applyState.value = ApplyState.FAILED
            Toast.makeText(
                context,
                context.getString(R.string.live_wp_file_invalid),
                Toast.LENGTH_SHORT
            ).show()
            delay(1000)
            _applyState.value = ApplyState.IDLE
            return
        }

        val wm = WallpaperManager.getInstance(context)
        previousSysWallpaperId = wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        previousLockWallpaperId = wm.getWallpaperId(WallpaperManager.FLAG_LOCK)

        wasOurLiveWallpaperActiveBeforeLaunch =
            LiveWallpaperSetter.isOurLiveWallpaperActive(context)

        val launched = LiveWallpaperSetter.tryActivate(context)

        if (launched) {
            _applyState.value = ApplyState.WAITING_CONFIRM
            Log.d(TAG, "已跳转系统动态壁纸确认页")
        } else {
            LiveWallpaperSetter.clearPendingConfig(context)
            _applyState.value = ApplyState.IDLE
            _liveWpResult.value = LiveWpResult.FAILED
        }
    }

    fun onReturnFromSystemPage(
        context: Context,
        wallpaper: WallpaperEntity,
        rule: MonetRule
    ) {
        if (_applyState.value != ApplyState.WAITING_CONFIRM) return

        confirmCheckJob?.cancel()
        confirmCheckJob = viewModelScope.launch {
            val startAt = System.currentTimeMillis()
            val wm = WallpaperManager.getInstance(context)

            while (_applyState.value == ApplyState.WAITING_CONFIRM) {
                val hasPending = LiveWallpaperSetter.hasPendingConfig(context)
                if (!hasPending) {
                    _applyState.value = ApplyState.IDLE
                    return@launch
                }

                val isActiveNow = LiveWallpaperSetter.isOurLiveWallpaperActive(context)

                val newSysId = wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
                val newLockId = wm.getWallpaperId(WallpaperManager.FLAG_LOCK)

                val idChanged =
                    (newSysId != previousSysWallpaperId) ||
                            (newLockId != previousLockWallpaperId)

                val shouldConfirm = if (wasOurLiveWallpaperActiveBeforeLaunch) {
                    isActiveNow && idChanged
                } else {
                    isActiveNow
                }

                if (shouldConfirm) {
                    Log.d(TAG, "系统页返回：确认成功 (idChanged=$idChanged)")
                    handleConfirmed(context, wallpaper, rule)
                    return@launch
                }

                if (System.currentTimeMillis() - startAt >= LIVE_CONFIRM_TIMEOUT_MS) {
                    val isStillOurs = LiveWallpaperSetter.isOurLiveWallpaperActive(context)
                    if (isStillOurs) {
                        Log.d(TAG, "系统页返回：等待超时，检测到壁纸已被应用，执行确认")
                        handleConfirmed(context, wallpaper, rule)
                    } else {
                        Log.d(TAG, "系统页返回：等待超时，壁纸未被应用，视为取消")
                        LiveWallpaperSetter.clearPendingConfig(context)
                        _applyState.value = ApplyState.IDLE
                    }
                    return@launch
                }

                delay(LIVE_CONFIRM_POLL_INTERVAL_MS)
            }
        }
    }

    fun onUserConfirmed(
        context: Context,
        wallpaper: WallpaperEntity,
        rule: MonetRule
    ) {
        if (_applyState.value != ApplyState.WAITING_CONFIRM) return
        confirmCheckJob?.cancel()
        confirmCheckJob = null
        viewModelScope.launch {
            handleConfirmed(context, wallpaper, rule)
        }
    }

    fun onUserCancelled(context: Context) {
        if (_applyState.value != ApplyState.WAITING_CONFIRM) return
        confirmCheckJob?.cancel()
        confirmCheckJob = null
        LiveWallpaperSetter.clearPendingConfig(context)
        _applyState.value = ApplyState.IDLE
    }

    /**
     * ★ 关键修复：
     * LIVE 不再本地写 appSeedColor，只等待 Service(active) 写入。
     * STATIC 仍然本地写入。
     */
    private suspend fun handleConfirmed(
        context: Context,
        wallpaper: WallpaperEntity,
        rule: MonetRule
    ) {
        confirmCheckJob?.cancel()
        confirmCheckJob = null

        val currentMode = settingsDataStore.appColorModeFlow.first()
        val beforeSeed = if (currentMode == SettingsDataStore.COLOR_MODE_RULE) {
            settingsDataStore.appSeedColorFlow.first()
        } else {
            null
        }

        if (currentMode == SettingsDataStore.COLOR_MODE_RULE) {
            _showBanner.value = true
            _bannerSuccess.value = false
            delay(50)
        }

        when (wallpaper.type) {
            WallpaperType.LIVE -> {
                Log.d(TAG, "LIVE: 系统确认，立即取色并写入 seed 作为兜底")

                LiveWallpaperSetter.promotePendingToActive(context)
                repository.markAsUsed(wallpaper.id)

                // ★ 修复：立即取色并直接写入 appSeedColor，不再仅依赖 Service 异步写入
                if (currentMode == SettingsDataStore.COLOR_MODE_RULE) {
                    val colors = getFreshColorsFor(wallpaper, rule)
                    if (colors?.primary != null && colors.primary != beforeSeed) {
                        Log.d(TAG, "LIVE: 立即写入 appSeedColor=${colors.primary.toHex()}")
                        settingsDataStore.saveAppSeedColor(colors.primary)
                    }
                }

                _applyState.value = ApplyState.SUCCESS

                if (currentMode == SettingsDataStore.COLOR_MODE_RULE) {
                    var latestSeed = beforeSeed
                    val startAt = System.currentTimeMillis()

                    while (System.currentTimeMillis() - startAt < LIVE_SEED_WAIT_TIMEOUT_MS) {
                        delay(LIVE_SEED_WAIT_INTERVAL_MS)
                        latestSeed = settingsDataStore.appSeedColorFlow.first()
                        Log.d(
                            TAG,
                            "LIVE: 等待 Service 写入 seed, before=${beforeSeed?.toHex()}, now=${latestSeed?.toHex()}"
                        )
                        if (latestSeed != null && latestSeed != beforeSeed) {
                            break
                        }
                    }

                    if (latestSeed != null && latestSeed != beforeSeed) {
                        Log.d(TAG, "LIVE: 观察到 Service 已写入新 seed=${latestSeed.toHex()}")
                        _bannerSuccess.value = true
                        delay(BANNER_SUCCESS_DURATION_MS)
                    } else {
                        Log.d(TAG, "LIVE: 未观察到 seed 变化，结束等待")
                    }

                    _showBanner.value = false
                }

                delay(200)
                _applyState.value = ApplyState.IDLE
            }

            WallpaperType.STATIC -> {
                val colors = if (currentMode == SettingsDataStore.COLOR_MODE_RULE) {
                    getFreshColorsFor(wallpaper, rule)
                } else {
                    null
                }

                if (currentMode == SettingsDataStore.COLOR_MODE_RULE && colors?.primary != null) {
                    Log.d(TAG, "STATIC(handleConfirmed): 本地写入 appSeedColor=${colors.primary.toHex()}")
                    settingsDataStore.saveAppSeedColor(colors.primary)
                }

                LiveWallpaperSetter.promotePendingToActive(context)
                repository.markAsUsed(wallpaper.id)

                _applyState.value = ApplyState.SUCCESS

                if (currentMode == SettingsDataStore.COLOR_MODE_RULE) {
                    if (colors?.primary != null && colors.primary != beforeSeed) {
                        _bannerSuccess.value = true
                        delay(BANNER_SUCCESS_DURATION_MS)
                    }
                    _showBanner.value = false
                }

                delay(200)
                _applyState.value = ApplyState.IDLE
            }
        }
    }

    fun deleteWallpaper(
        context: Context,
        wallpaper: WallpaperEntity,
        onDeleted: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.delete(wallpaper)

                runCatching {
                    val file = File(wallpaper.filePath)
                    if (file.exists()) file.delete()
                }

                runCatching {
                    if (wallpaper.thumbnailPath != wallpaper.filePath) {
                        val thumb = File(wallpaper.thumbnailPath)
                        if (thumb.exists()) thumb.delete()
                    }
                }

                Toast.makeText(
                    context,
                    context.getString(R.string.delete) + " ✓",
                    Toast.LENGTH_SHORT
                ).show()

                onDeleted()
            } catch (e: Exception) {
                Log.e(TAG, "deleteWallpaper failed", e)
                Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun persistWallpaperAdjustment(
        wallpaper: WallpaperEntity,
        adj: ImageAdjustment
    ) {
        if (lastSavedAdjustments[wallpaper.id] == adj) return

        val base = latestWallpaperOrFallback(wallpaper)
        val updated = base.copy(
            fillMode = adj.fillMode.name,
            mirrorHorizontal = adj.mirrorHorizontal,
            mirrorVertical = adj.mirrorVertical,
            brightness = adj.brightness,
            contrast = adj.contrast,
            saturation = adj.saturation,
            bgColorArgb = adj.backgroundColor.toArgbInt(),
            adjustOffsetX = adj.offsetX,
            adjustOffsetY = adj.offsetY,
            adjustScale = adj.scale
        )

        repository.update(updated)
        lastSavedAdjustments[wallpaper.id] = adj
    }

    private suspend fun latestWallpaperOrFallback(wallpaper: WallpaperEntity): WallpaperEntity {
        return withContext(Dispatchers.IO) {
            repository.getById(wallpaper.id)
        } ?: wallpaper
    }

    private fun buildRuleKey(rule: MonetRule): String {
        return buildString {
            append(rule.framePosition.name)
            append('|')
            append(rule.colorRegion.name)
            append('|')
            append(rule.tonePreference.name)
            append('|')
            append(rule.manualOverrideColor?.toString() ?: "null")
        }
    }

    private fun isSnapshotFor(
        wallpaper: WallpaperEntity,
        rule: MonetRule
    ): Boolean {
        val snapshot = latestColorSnapshot ?: return false
        return snapshot.wallpaperId == wallpaper.id &&
                snapshot.filePath == wallpaper.filePath &&
                snapshot.ruleKey == buildRuleKey(rule)
    }

    private fun Color.toArgbInt(): Int {
        return android.graphics.Color.argb(
            (alpha * 255).toInt(),
            (red * 255).toInt(),
            (green * 255).toInt(),
            (blue * 255).toInt()
        )
    }

    private fun Int.toHex(): String = "#${Integer.toHexString(this)}"

    override fun onCleared() {
        adjustmentSaveJob?.cancel()
        confirmCheckJob?.cancel()
        super.onCleared()
    }
}
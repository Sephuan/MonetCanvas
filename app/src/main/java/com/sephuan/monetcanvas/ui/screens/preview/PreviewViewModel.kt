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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class ApplyState {
    IDLE,
    APPLYING,
    WAITING_CONFIRM,
    CONFIRMING,   // 用户已确认，正在处理中
    SUCCESS,
    FAILED
}

enum class LiveWpResult {
    IDLE,
    FAILED
}

@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val repository: WallpaperRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    companion object {
        private const val TAG = "PreviewVM"
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

    fun clearLiveWpResult() {
        _liveWpResult.value = LiveWpResult.IDLE
    }

    fun resetApplyState() {
        _applyState.value = ApplyState.IDLE
    }

    fun dismissBanner() {
        _showBanner.value = false
    }

    suspend fun loadRuleForWallpaper(wallpaper: WallpaperEntity): MonetRule {
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
        val updated = wallpaper.copy(
            framePosition = rule.framePosition.name,
            colorRegion = rule.colorRegion.name,
            tonePreference = rule.tonePreference.name,
            manualColor = rule.manualOverrideColor
        )
        repository.update(updated)
    }

    fun loadAdjustmentForWallpaper(wallpaper: WallpaperEntity): ImageAdjustment {
        return ImageAdjustment(
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
    }

    fun saveAdjustmentForWallpaper(wallpaper: WallpaperEntity, adj: ImageAdjustment) {
        viewModelScope.launch {
            val updated = wallpaper.copy(
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
            Log.d(TAG, "图片调整参数已保存: ${wallpaper.fileName}")
        }
    }

    fun analyzeColors(wallpaper: WallpaperEntity, rule: MonetRule) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            Log.d(TAG, "分析颜色: ${wallpaper.fileName}")

            val colors = ColorEngine.extractColors(
                filePath = wallpaper.filePath,
                rule = rule,
                type = wallpaper.type
            )

            _extractedColors.value = colors
            _isAnalyzing.value = false

            Log.d(TAG, "颜色结果: ${colors?.primary?.let { "#${Integer.toHexString(it)}" }}")

            val currentMode = settingsDataStore.appColorModeFlow.first()
            if (currentMode == SettingsDataStore.COLOR_MODE_RULE) {
                settingsDataStore.saveAppSeedColor(colors?.primary)
            }
        }
    }

    fun applyWallpaper(
        context: Context,
        wallpaper: WallpaperEntity,
        target: Int,
        rule: MonetRule,
        adjustment: ImageAdjustment = ImageAdjustment.DEFAULT
    ) {
        if (_applyState.value == ApplyState.APPLYING ||
            _applyState.value == ApplyState.WAITING_CONFIRM ||
            _applyState.value == ApplyState.CONFIRMING
        ) return

        _applyState.value = ApplyState.APPLYING

        viewModelScope.launch {
            if (wallpaper.type == WallpaperType.STATIC && adjustment.hasAnyAdjustment) {
                saveAdjustmentForWallpaper(wallpaper, adjustment)
            }

            when (wallpaper.type) {
                WallpaperType.STATIC -> applyStaticWallpaper(context, wallpaper, target, adjustment)
                WallpaperType.LIVE -> applyLiveWallpaper(context, wallpaper, rule)
            }
        }
    }

    private suspend fun applyStaticWallpaper(
        context: Context,
        wallpaper: WallpaperEntity,
        target: Int,
        adjustment: ImageAdjustment
    ) {
        delay(100)

        val success = WallpaperSetter.setStaticWallpaper(
            context = context,
            imagePath = wallpaper.filePath,
            target = target,
            adjustment = adjustment
        )
        if (success) {
            repository.markAsUsed(wallpaper.id)
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

        _applyState.value = ApplyState.WAITING_CONFIRM
        Log.d(TAG, "等待 UI 层启动系统确认页")
    }

    fun onUserConfirmed(
        context: Context,
        wallpaper: WallpaperEntity,
        rule: MonetRule
    ) {
        if (_applyState.value != ApplyState.WAITING_CONFIRM) return

        // 立即切换到 CONFIRMING 状态，防止 LaunchedEffect 再次触发
        _applyState.value = ApplyState.CONFIRMING
        Log.d(TAG, "用户确认了动态壁纸，开始处理")

        viewModelScope.launch {
            handleConfirmed(context, wallpaper, rule)
        }
    }

    fun onUserCancelled(context: Context) {
        if (_applyState.value != ApplyState.WAITING_CONFIRM) return

        LiveWallpaperSetter.clearPendingConfig(context)
        Log.d(TAG, "用户取消了动态壁纸设置")
        _applyState.value = ApplyState.IDLE
    }

    private suspend fun handleConfirmed(
        context: Context,
        wallpaper: WallpaperEntity,
        rule: MonetRule
    ) {
        // 1. 先提升配置
        LiveWallpaperSetter.promotePendingToActive(context)
        Log.d(TAG, "★ 配置已提升")

        delay(200)

        // 2. 分析颜色
        val colors = ColorEngine.extractColors(
            filePath = wallpaper.filePath,
            rule = rule,
            type = wallpaper.type
        )
        _extractedColors.value = colors
        Log.d(TAG, "新颜色已分析: ${colors?.primary?.let { "#${Integer.toHexString(it)}" }}")

        // 3. 更新 seed color
        val currentMode = settingsDataStore.appColorModeFlow.first()
        if (currentMode == SettingsDataStore.COLOR_MODE_RULE) {
            settingsDataStore.saveAppSeedColor(colors?.primary)
        }

        // 4. 通知系统颜色变化
        runCatching {
            val wm = WallpaperManager.getInstance(context)
            val method = wm.javaClass.getMethod("notifyColorsChanged")
            method.invoke(wm)
            Log.d(TAG, "已通知系统颜色变化（反射调用成功）")
        }.onFailure {
            Log.w(TAG, "通知系统颜色变化失败", it)
        }

        // 5. UI 反馈
        _applyState.value = ApplyState.SUCCESS
        _showBanner.value = true
        _bannerSuccess.value = true

        delay(1200)
        _showBanner.value = false
        _applyState.value = ApplyState.IDLE
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

    private fun Color.toArgbInt(): Int {
        return android.graphics.Color.argb(
            (alpha * 255).toInt(),
            (red * 255).toInt(),
            (green * 255).toInt(),
            (blue * 255).toInt()
        )
    }
}
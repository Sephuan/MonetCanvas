package com.sephuan.monetcanvas.ui.screens.preview

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sephuan.monetcanvas.R
import com.sephuan.monetcanvas.data.datastore.SettingsDataStore
import com.sephuan.monetcanvas.data.db.WallpaperEntity
import com.sephuan.monetcanvas.data.model.ColorRegion
import com.sephuan.monetcanvas.data.model.FramePickPosition
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
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ━━━━━ 状态枚举 ━━━━━

enum class ApplyState {
    IDLE,           // 初始状态
    APPLYING,       // 正在设置中（显示遮罩 + 转圈）
    WAITING_CONFIRM,// 已跳转系统确认页，等待返回
    SUCCESS,        // 设置成功
    FAILED          // 设置失败
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

    // ━━━━━ 颜色分析 ━━━━━

    private val _extractedColors = MutableStateFlow<ExtractedColors?>(null)
    val extractedColors: StateFlow<ExtractedColors?> = _extractedColors.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    // ━━━━━ 设置壁纸状态 ━━━━━

    private val _applyState = MutableStateFlow(ApplyState.IDLE)
    val applyState: StateFlow<ApplyState> = _applyState.asStateFlow()

    private val _liveWpResult = MutableStateFlow(LiveWpResult.IDLE)
    val liveWpResult: StateFlow<LiveWpResult> = _liveWpResult.asStateFlow()

    // ━━━━━ 返回横幅 ━━━━━

    private val _showBanner = MutableStateFlow(false)
    val showBanner: StateFlow<Boolean> = _showBanner.asStateFlow()

    private val _bannerSuccess = MutableStateFlow(false)
    val bannerSuccess: StateFlow<Boolean> = _bannerSuccess.asStateFlow()

    // ━━━━━ 状态清理 ━━━━━

    fun clearLiveWpResult() {
        _liveWpResult.value = LiveWpResult.IDLE
    }

    fun resetApplyState() {
        _applyState.value = ApplyState.IDLE
    }

    fun dismissBanner() {
        _showBanner.value = false
    }

    // ━━━━━ 取色规则加载/保存 ━━━━━

    suspend fun loadRuleForWallpaper(wallpaper: WallpaperEntity): MonetRule {
        return MonetRule(
            framePosition = runCatching {
                enumValueOf<FramePickPosition>(wallpaper.framePosition)
            }.getOrDefault(FramePickPosition.FIRST),
            colorRegion = runCatching {
                enumValueOf<ColorRegion>(wallpaper.colorRegion)
            }.getOrDefault(ColorRegion.FULL_FRAME),
            tonePreference = runCatching {
                enumValueOf<TonePreference>(wallpaper.tonePreference)
            }.getOrDefault(TonePreference.AUTO),
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

    // ━━━━━ 颜色分析 ━━━━━

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

            // 同步到 App 主题
            val currentMode = settingsDataStore.appColorModeFlow.first()
            if (currentMode == SettingsDataStore.COLOR_MODE_RULE) {
                settingsDataStore.saveAppSeedColor(colors?.primary)
            }
        }
    }

    // ━━━━━ ★ 设为壁纸（核心流程）━━━━━

    fun applyWallpaper(
        context: Context,
        wallpaper: WallpaperEntity,
        target: Int,
        rule: MonetRule
    ) {
        // 防止重复点击
        if (_applyState.value == ApplyState.APPLYING ||
            _applyState.value == ApplyState.WAITING_CONFIRM
        ) return

        _applyState.value = ApplyState.APPLYING

        viewModelScope.launch {
            when (wallpaper.type) {
                WallpaperType.STATIC -> applyStaticWallpaper(context, wallpaper, target)
                WallpaperType.LIVE -> applyLiveWallpaper(context, wallpaper, rule)
            }
        }
    }

    private suspend fun applyStaticWallpaper(
        context: Context,
        wallpaper: WallpaperEntity,
        target: Int
    ) {
        // 短暂延迟让遮罩动画完整显示
        delay(300)

        val success = WallpaperSetter.setStaticWallpaper(
            context = context,
            imagePath = wallpaper.filePath,
            target = target
        )

        if (success) {
            repository.markAsUsed(wallpaper.id)
            _applyState.value = ApplyState.SUCCESS
            Toast.makeText(context, "✓", Toast.LENGTH_SHORT).show()

            // 成功后自动恢复 IDLE
            delay(1500)
            _applyState.value = ApplyState.IDLE
        } else {
            _applyState.value = ApplyState.FAILED
            Toast.makeText(context, "设置失败", Toast.LENGTH_SHORT).show()

            delay(1500)
            _applyState.value = ApplyState.IDLE
        }
    }

    private suspend fun applyLiveWallpaper(
        context: Context,
        wallpaper: WallpaperEntity,
        rule: MonetRule
    ) {
        // ★ 第1步：保存预览配置
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
            delay(1500)
            _applyState.value = ApplyState.IDLE
            return
        }

        // 短暂延迟让遮罩动画显示
        delay(200)

        // ★ 第2步：跳转系统确认页
        val launched = LiveWallpaperSetter.tryActivate(context)

        if (launched) {
            repository.markAsUsed(wallpaper.id)
            _applyState.value = ApplyState.WAITING_CONFIRM
            Log.d(TAG, "✓ 已跳转系统确认页")
        } else {
            LiveWallpaperSetter.clearPendingConfig(context)
            _applyState.value = ApplyState.IDLE
            _liveWpResult.value = LiveWpResult.FAILED
        }
    }

    // ━━━━━ ★ 从系统确认页返回后的回检 ━━━━━

    fun onReturnFromSystemPage(
        context: Context,
        wallpaper: WallpaperEntity,
        rule: MonetRule
    ) {
        // 只在等待确认状态下处理
        if (_applyState.value != ApplyState.WAITING_CONFIRM) return

        viewModelScope.launch {
            // 等待系统完成壁纸切换
            delay(600)

            val isActive = LiveWallpaperSetter.isOurLiveWallpaperActive(context)
            Log.d(TAG, "从系统页返回: isActive=$isActive")

            if (isActive && LiveWallpaperSetter.hasPendingConfig(context)) {
                // ★ 用户确认了 → 提升配置
                LiveWallpaperSetter.promotePendingToActive(context)
                Log.d(TAG, "★ 配置已提升，桌面即将更新")

                _applyState.value = ApplyState.SUCCESS
                _showBanner.value = true
                _bannerSuccess.value = false

                // 重新分析颜色
                analyzeColors(wallpaper, rule)

                // 等分析完成
                delay(2000)
                _bannerSuccess.value = true
                _applyState.value = ApplyState.IDLE

                // 横幅自动消失
                delay(3000)
                _showBanner.value = false

            } else if (LiveWallpaperSetter.hasPendingConfig(context)) {
                // ★ 用户取消了 → 清理
                LiveWallpaperSetter.clearPendingConfig(context)
                Log.d(TAG, "用户取消了设置")
                _applyState.value = ApplyState.IDLE

            } else {
                // 不是从设置壁纸流程回来的
                _applyState.value = ApplyState.IDLE
            }
        }
    }

    // ━━━━━ 删除壁纸 ━━━━━

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
}
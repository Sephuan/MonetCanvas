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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
        private const val TAG = "PreviewViewModel"
    }

    private val _extractedColors = MutableStateFlow<ExtractedColors?>(null)
    val extractedColors: StateFlow<ExtractedColors?> = _extractedColors.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _liveWpResult = MutableStateFlow(LiveWpResult.IDLE)
    val liveWpResult: StateFlow<LiveWpResult> = _liveWpResult.asStateFlow()

    fun clearLiveWpResult() {
        _liveWpResult.value = LiveWpResult.IDLE
    }

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

    /**
     * ★ 设为壁纸
     *
     * 静态壁纸：直接用 WallpaperManager API
     * 动态壁纸：
     *   1. savePendingConfig() — 写入预览配置（桌面不受影响）
     *   2. tryActivate()       — 跳转系统确认页
     *   3. 用户确认后 onResume → 调用 onReturnFromSystemPage()
     */
    fun applyWallpaper(
        context: Context,
        wallpaper: WallpaperEntity,
        target: Int,
        rule: MonetRule
    ) {
        viewModelScope.launch {
            when (wallpaper.type) {
                WallpaperType.STATIC -> {
                    val success = WallpaperSetter.setStaticWallpaper(
                        context = context,
                        imagePath = wallpaper.filePath,
                        target = target
                    )
                    if (success) {
                        repository.markAsUsed(wallpaper.id)
                        Toast.makeText(context, "✓", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "设置失败", Toast.LENGTH_SHORT).show()
                    }
                }

                WallpaperType.LIVE -> {
                    // ★ 第1步：只写入预览配置！桌面完全不变
                    val saved = LiveWallpaperSetter.savePendingConfig(
                        context = context,
                        videoPath = wallpaper.filePath,
                        framePosition = rule.framePosition.name,
                        colorRegion = rule.colorRegion.name,
                        tonePreference = rule.tonePreference.name
                    )

                    if (!saved) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.live_wp_file_invalid),
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }

                    // ★ 第2步：跳转系统确认页
                    val launched = LiveWallpaperSetter.tryActivate(context)

                    if (launched) {
                        repository.markAsUsed(wallpaper.id)
                        Log.d(TAG, "✓ 已跳转系统确认页，等待用户确认")
                        // 不做任何其他事！等 PreviewScreen 的 onResume 处理
                    } else {
                        // 清除预览配置
                        LiveWallpaperSetter.clearPendingConfig(context)
                        _liveWpResult.value = LiveWpResult.FAILED
                    }
                }
            }
        }
    }

    /**
     * ★ 第3步：用户从系统确认页返回后调用
     *
     * 检查壁纸是否真的切换成功：
     *   - 成功 → promotePendingToActive()，桌面实例重新加载 + 取色
     *   - 失败 → clearPendingConfig()，什么都不变
     */
    fun onReturnFromSystemPage(context: Context, wallpaper: WallpaperEntity, rule: MonetRule) {
        viewModelScope.launch {
            // 等一下让系统完成壁纸切换
            kotlinx.coroutines.delay(500)

            val isActive = LiveWallpaperSetter.isOurLiveWallpaperActive(context)
            Log.d(TAG, "从系统页返回: isActive=$isActive")

            if (isActive && LiveWallpaperSetter.hasPendingConfig(context)) {
                // ★ 用户确认了！把预览配置提升为正式配置
                LiveWallpaperSetter.promotePendingToActive(context)
                Log.d(TAG, "★ 预览配置已提升为正式配置，桌面即将更新")

                Toast.makeText(context, "✓ 动态壁纸已设置", Toast.LENGTH_SHORT).show()

                // 重新分析颜色（在 App 内展示）
                analyzeColors(wallpaper, rule)
            } else if (!isActive && LiveWallpaperSetter.hasPendingConfig(context)) {
                // 用户取消了
                LiveWallpaperSetter.clearPendingConfig(context)
                Log.d(TAG, "用户取消了设置，已清除预览配置")
            }
            // 如果没有 pending config，说明不是从设置壁纸流程回来的，不做任何事
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
}
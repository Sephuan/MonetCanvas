@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sephuan.monetcanvas.ui.screens.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.sephuan.monetcanvas.data.db.WallpaperEntity
import com.sephuan.monetcanvas.data.model.FillMode
import com.sephuan.monetcanvas.data.model.ImageAdjustment
import com.sephuan.monetcanvas.data.model.WallpaperType

/**
 * 全屏壁纸背景层
 * 底层 = 可换色画布
 * 上层 = 壁纸图片（可移动、缩放、调色）
 */
@Composable
fun PreviewMediaSection(
    wallpaper: WallpaperEntity,
    player: ExoPlayer?,
    modifier: Modifier = Modifier,
    adjustment: ImageAdjustment = ImageAdjustment.DEFAULT,
    onAdjustmentChange: ((ImageAdjustment) -> Unit)? = null
) {
    // ★ 始终显示背景色画布（不只是 FIT 模式）
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(adjustment.backgroundColor)
    ) {
        when (wallpaper.type) {
            WallpaperType.STATIC -> {
                StaticWallpaperLayer(
                    wallpaper = wallpaper,
                    adjustment = adjustment,
                    onAdjustmentChange = onAdjustmentChange
                )
            }

            WallpaperType.LIVE -> {
                VideoWallpaperLayer(player = player)
            }
        }
    }
}

// ━━━━━ 静态壁纸层（画布模式）━━━━━

@Composable
private fun StaticWallpaperLayer(
    wallpaper: WallpaperEntity,
    adjustment: ImageAdjustment,
    onAdjustmentChange: ((ImageAdjustment) -> Unit)?
) {
    // ★ 用 rememberUpdatedState 保证手势 lambda 里拿到的永远是最新值
    val latestAdjustment by rememberUpdatedState(adjustment)
    val latestCallback by rememberUpdatedState(onAdjustmentChange)

    val contentScale = when (adjustment.fillMode) {
        FillMode.COVER -> ContentScale.Crop
        FillMode.FIT -> ContentScale.Fit
        FillMode.FREE -> ContentScale.Fit
    }

    val colorFilter = buildColorFilter(adjustment)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (latestCallback != null) {
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)

                            do {
                                val event = awaitPointerEvent()
                                val pointers = event.changes

                                val currentAdj = latestAdjustment
                                val callback = latestCallback ?: continue

                                if (pointers.size >= 2) {
                                    // ★ 多指：缩放 + 平移（所有模式都支持）
                                    val pan = event.calculatePan()
                                    val zoom = event.calculateZoom()
                                    val newScale = (currentAdj.scale * zoom).coerceIn(0.2f, 8f)

                                    callback(
                                        currentAdj.copy(
                                            offsetX = currentAdj.offsetX + pan.x,
                                            offsetY = currentAdj.offsetY + pan.y,
                                            scale = newScale
                                        )
                                    )
                                    pointers.forEach { it.consume() }

                                } else if (pointers.size == 1) {
                                    val change = pointers.first()
                                    if (change.positionChanged()) {
                                        val delta = change.position - change.previousPosition

                                        when (currentAdj.fillMode) {
                                            FillMode.COVER -> {
                                                // 覆盖模式：左右移动
                                                callback(
                                                    currentAdj.copy(
                                                        offsetX = currentAdj.offsetX + delta.x
                                                    )
                                                )
                                            }
                                            FillMode.FIT -> {
                                                // 填充模式：上下移动
                                                callback(
                                                    currentAdj.copy(
                                                        offsetY = currentAdj.offsetY + delta.y
                                                    )
                                                )
                                            }
                                            FillMode.FREE -> {
                                                // 自由模式：任意方向移动
                                                callback(
                                                    currentAdj.copy(
                                                        offsetX = currentAdj.offsetX + delta.x,
                                                        offsetY = currentAdj.offsetY + delta.y
                                                    )
                                                )
                                            }
                                        }
                                        change.consume()
                                    }
                                }
                            } while (pointers.any { it.pressed })
                        }
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = wallpaper.filePath,
            contentDescription = wallpaper.fileName,
            contentScale = contentScale,
            colorFilter = colorFilter,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // ★ 统一用 adjustment.scale 控制缩放
                    val mirrorX = if (adjustment.mirrorHorizontal) -1f else 1f
                    val mirrorY = if (adjustment.mirrorVertical) -1f else 1f

                    scaleX = adjustment.scale * mirrorX
                    scaleY = adjustment.scale * mirrorY
                    translationX = adjustment.offsetX
                    translationY = adjustment.offsetY
                }
        )
    }
}

// ━━━━━ 动态壁纸全屏层 ━━━━━

@Composable
private fun VideoWallpaperLayer(player: ExoPlayer?) {
    player?.let { exoPlayer ->
        AndroidView(
            factory = { ctx ->
                androidx.media3.ui.PlayerView(ctx).apply {
                    useController = false
                    this.player = exoPlayer
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { view ->
                if (view.player != exoPlayer) {
                    view.player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ━━━━━ 色彩滤镜 ━━━━━

private fun buildColorFilter(adjustment: ImageAdjustment): ColorFilter? {
    val b = adjustment.brightness
    val c = adjustment.contrast
    val s = adjustment.saturation

    if (b == 0f && c == 0f && s == 0f) return null

    val brightnessOffset = b * 255f
    val brightnessMatrix = ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, brightnessOffset,
            0f, 1f, 0f, 0f, brightnessOffset,
            0f, 0f, 1f, 0f, brightnessOffset,
            0f, 0f, 0f, 1f, 0f
        )
    )

    val contrastScale = 1f + c
    val contrastOffset = (-0.5f * contrastScale + 0.5f) * 255f
    val contrastMatrix = ColorMatrix(
        floatArrayOf(
            contrastScale, 0f, 0f, 0f, contrastOffset,
            0f, contrastScale, 0f, 0f, contrastOffset,
            0f, 0f, contrastScale, 0f, contrastOffset,
            0f, 0f, 0f, 1f, 0f
        )
    )

    val saturationMatrix = ColorMatrix()
    saturationMatrix.setToSaturation((1f + s).coerceIn(0f, 2f))

    val result = ColorMatrix()
    result.timesAssign(brightnessMatrix)
    result.timesAssign(contrastMatrix)
    result.timesAssign(saturationMatrix)

    return ColorFilter.colorMatrix(result)
}
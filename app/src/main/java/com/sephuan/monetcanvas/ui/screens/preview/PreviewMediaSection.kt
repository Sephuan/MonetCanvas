@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sephuan.monetcanvas.ui.screens.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
 * 静态壁纸：支持调整参数 + 触屏手势
 * 动态壁纸：全屏视频播放
 */
@Composable
fun PreviewMediaSection(
    wallpaper: WallpaperEntity,
    player: ExoPlayer?,
    modifier: Modifier = Modifier,
    adjustment: ImageAdjustment = ImageAdjustment.DEFAULT,
    onAdjustmentChange: ((ImageAdjustment) -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                if (wallpaper.type == WallpaperType.STATIC && adjustment.fillMode == FillMode.FIT) {
                    adjustment.backgroundColor
                } else {
                    Color.Black
                }
            )
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

// ━━━━━ 静态壁纸全屏层 ━━━━━

@Composable
private fun StaticWallpaperLayer(
    wallpaper: WallpaperEntity,
    adjustment: ImageAdjustment,
    onAdjustmentChange: ((ImageAdjustment) -> Unit)?
) {
    val contentScale = when (adjustment.fillMode) {
        FillMode.COVER -> ContentScale.Crop
        FillMode.FIT -> ContentScale.Fit
        FillMode.FREE -> ContentScale.Fit
    }

    val colorFilter = buildColorFilter(adjustment)

    val gestureModifier = when {
        onAdjustmentChange == null -> Modifier

        adjustment.fillMode == FillMode.FREE -> {
            Modifier.pointerInput(adjustment.fillMode) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (adjustment.scale * zoom).coerceIn(0.3f, 5f)
                    onAdjustmentChange(
                        adjustment.copy(
                            offsetX = adjustment.offsetX + pan.x,
                            offsetY = adjustment.offsetY + pan.y,
                            scale = newScale
                        )
                    )
                }
            }
        }

        adjustment.fillMode == FillMode.COVER -> {
            Modifier.pointerInput(adjustment.fillMode) {
                detectDragGestures { _, dragAmount ->
                    onAdjustmentChange(
                        adjustment.copy(
                            offsetX = adjustment.offsetX + dragAmount.x
                        )
                    )
                }
            }
        }

        adjustment.fillMode == FillMode.FIT -> {
            Modifier.pointerInput(adjustment.fillMode) {
                detectDragGestures { _, dragAmount ->
                    onAdjustmentChange(
                        adjustment.copy(
                            offsetY = adjustment.offsetY + dragAmount.y
                        )
                    )
                }
            }
        }

        else -> Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(gestureModifier),
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
                    scaleX = adjustment.scale * (if (adjustment.mirrorHorizontal) -1f else 1f)
                    scaleY = (if (adjustment.mirrorVertical) -1f else 1f) *
                            (if (adjustment.fillMode == FillMode.FREE) adjustment.scale else 1f)
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
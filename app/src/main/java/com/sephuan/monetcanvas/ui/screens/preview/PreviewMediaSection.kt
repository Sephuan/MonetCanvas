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
import androidx.compose.ui.geometry.Offset
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
import kotlin.math.max
import kotlin.math.min

@Composable
fun PreviewMediaSection(
    wallpaper: WallpaperEntity,
    player: ExoPlayer?,
    adjustment: ImageAdjustment,
    onAdjustmentChange: ((ImageAdjustment) -> Unit)?,
    screenWidth: Int,
    screenHeight: Int,
    modifier: Modifier = Modifier
) {
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
                    onAdjustmentChange = onAdjustmentChange,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight
                )
            }
            WallpaperType.LIVE -> {
                VideoWallpaperLayer(player = player)
            }
        }
    }
}

@Composable
private fun StaticWallpaperLayer(
    wallpaper: WallpaperEntity,
    adjustment: ImageAdjustment,
    onAdjustmentChange: ((ImageAdjustment) -> Unit)?,
    screenWidth: Int,
    screenHeight: Int
) {
    val latestAdjustment by rememberUpdatedState(adjustment)
    val callback = onAdjustmentChange

    val imageWidth = wallpaper.width
    val imageHeight = wallpaper.height
    if (imageWidth <= 0 || imageHeight <= 0) return

    // 基准缩放（COVER 铺满，FIT 适配）
    val baseScale = when (adjustment.fillMode) {
        FillMode.COVER -> max(screenWidth.toFloat() / imageWidth, screenHeight.toFloat() / imageHeight)
        FillMode.FIT, FillMode.FREE -> min(screenWidth.toFloat() / imageWidth, screenHeight.toFloat() / imageHeight)
    }

    // ★ 修改：所有模式都使用用户设置的 scale（缩放滑块）
    val finalScale = baseScale * adjustment.scale
    val scaledWidth = imageWidth * finalScale
    val scaledHeight = imageHeight * finalScale

    val initialOffsetX = (screenWidth - scaledWidth) / 2f
    val initialOffsetY = (screenHeight - scaledHeight) / 2f

    val offsetX = when (adjustment.fillMode) {
        FillMode.COVER -> initialOffsetX + adjustment.offsetX
        FillMode.FIT -> initialOffsetX
        FillMode.FREE -> initialOffsetX + adjustment.offsetX
    }

    val offsetY = when (adjustment.fillMode) {
        FillMode.COVER -> initialOffsetY
        FillMode.FIT -> initialOffsetY + adjustment.offsetY
        FillMode.FREE -> initialOffsetY + adjustment.offsetY
    }

    val contentScale = when (adjustment.fillMode) {
        FillMode.COVER -> ContentScale.Crop
        else -> ContentScale.Fit
    }

    val colorFilter = buildColorFilter(adjustment)

    // 手势处理（双指缩放仅 FREE 模式，但滑块缩放对所有模式有效）
    val gestureModifier = if (callback != null) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var isZooming = false
                do {
                    val event = awaitPointerEvent()
                    val pointers = event.changes

                    if (pointers.size >= 2 && latestAdjustment.fillMode == FillMode.FREE) {
                        isZooming = true
                        val pan = event.calculatePan()
                        val zoom = event.calculateZoom()
                        val newScale = (latestAdjustment.scale * zoom).coerceIn(0.2f, 8f)
                        callback.invoke(
                            latestAdjustment.copy(
                                offsetX = latestAdjustment.offsetX + pan.x,
                                offsetY = latestAdjustment.offsetY + pan.y,
                                scale = newScale
                            )
                        )
                        pointers.forEach { it.consume() }
                    } else if (pointers.size == 1 && !isZooming) {
                        val change = pointers.first()
                        if (change.positionChanged()) {
                            val delta = change.position - change.previousPosition
                            when (latestAdjustment.fillMode) {
                                FillMode.COVER -> {
                                    callback.invoke(
                                        latestAdjustment.copy(
                                            offsetX = latestAdjustment.offsetX + delta.x
                                        )
                                    )
                                }
                                FillMode.FIT -> {
                                    callback.invoke(
                                        latestAdjustment.copy(
                                            offsetY = latestAdjustment.offsetY + delta.y
                                        )
                                    )
                                }
                                FillMode.FREE -> {
                                    callback.invoke(
                                        latestAdjustment.copy(
                                            offsetX = latestAdjustment.offsetX + delta.x,
                                            offsetY = latestAdjustment.offsetY + delta.y
                                        )
                                    )
                                }
                            }
                            change.consume()
                        }
                    } else if (pointers.isEmpty()) {
                        isZooming = false
                    }
                } while (pointers.any { it.pressed })
            }
        }
    } else Modifier

    Box(
        modifier = Modifier.fillMaxSize().then(gestureModifier),
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
                    val mirrorX = if (adjustment.mirrorHorizontal) -1f else 1f
                    val mirrorY = if (adjustment.mirrorVertical) -1f else 1f
                    scaleX = finalScale * mirrorX
                    scaleY = finalScale * mirrorY
                    translationX = offsetX
                    translationY = offsetY
                }
        )
    }
}

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
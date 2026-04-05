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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
    adjustment: ImageAdjustment = ImageAdjustment.DEFAULT,
    onAdjustmentChange: ((ImageAdjustment) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }.toInt()
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }.toInt()

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
                    screenWidth = screenWidthPx,
                    screenHeight = screenHeightPx
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
    // ★ 关键修复：移除 latestAdjustment 作为 pointerInput 的 key
    val latestAdjustment by rememberUpdatedState(adjustment)
    val callback = onAdjustmentChange

    val imageWidth = wallpaper.width
    val imageHeight = wallpaper.height
    if (imageWidth <= 0 || imageHeight <= 0) return

    val baseScale = when (adjustment.fillMode) {
        FillMode.COVER -> max(screenWidth.toFloat() / imageWidth, screenHeight.toFloat() / imageHeight)
        FillMode.FIT, FillMode.FREE -> min(screenWidth.toFloat() / imageWidth, screenHeight.toFloat() / imageHeight)
    }

    val effectiveScale = when (adjustment.fillMode) {
        FillMode.COVER, FillMode.FIT -> 1f
        FillMode.FREE -> adjustment.scale
    }

    val finalScale = baseScale * effectiveScale
    val scaledWidth = imageWidth * finalScale
    val scaledHeight = imageHeight * finalScale

    val initialOffsetX = (screenWidth - scaledWidth) / 2f
    val initialOffsetY = (screenHeight - scaledHeight) / 2f

    val offsetX = initialOffsetX + adjustment.offsetX
    val offsetY = initialOffsetY + adjustment.offsetY

    val contentScale = when (adjustment.fillMode) {
        FillMode.COVER -> ContentScale.Crop
        else -> ContentScale.Fit
    }

    val colorFilter = buildColorFilter(adjustment)

    // 速度增益系数
    val MOVE_SPEED = 2.2f
    val ZOOM_SPEED = 1.8f

    Box(
        modifier = Modifier
            .fillMaxSize()
            // ★ 只依赖 callback 作为 key，不依赖 latestAdjustment，避免手势被中断
            .pointerInput(callback) {
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
                            val adjustedZoom = 1f + (zoom - 1f) * ZOOM_SPEED
                            val newScale = (latestAdjustment.scale * adjustedZoom).coerceIn(0.2f, 8f)
                            callback?.invoke(
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
                                val acceleratedDelta = Offset(delta.x * MOVE_SPEED, delta.y * MOVE_SPEED)
                                when (latestAdjustment.fillMode) {
                                    FillMode.COVER -> {
                                        callback?.invoke(
                                            latestAdjustment.copy(
                                                offsetX = latestAdjustment.offsetX + acceleratedDelta.x
                                            )
                                        )
                                    }
                                    FillMode.FIT -> {
                                        callback?.invoke(
                                            latestAdjustment.copy(
                                                offsetY = latestAdjustment.offsetY + acceleratedDelta.y
                                            )
                                        )
                                    }
                                    FillMode.FREE -> {
                                        callback?.invoke(
                                            latestAdjustment.copy(
                                                offsetX = latestAdjustment.offsetX + acceleratedDelta.x,
                                                offsetY = latestAdjustment.offsetY + acceleratedDelta.y
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
            },
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
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

    val imageWidth = wallpaper.width.toFloat()
    val imageHeight = wallpaper.height.toFloat()
    if (imageWidth <= 0f || imageHeight <= 0f) return

    val colorFilter = buildColorFilter(adjustment)

    // ━━━━━ 手势处理（拉伸模式无手势，COVER/FIT 自由拖动+缩放） ━━━━━
    val gestureModifier = if (callback != null) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var isZooming = false
                do {
                    val event = awaitPointerEvent()
                    val pointers = event.changes

                    // STRETCH 模式下忽略手势操作
                    if (latestAdjustment.fillMode == FillMode.STRETCH) {
                        continue
                    }

                    if (pointers.size >= 2) {
                        isZooming = true
                        val pan = event.calculatePan()
                        val zoom = event.calculateZoom()
                        val newScale = (latestAdjustment.scale * zoom)
                            .coerceIn(ImageAdjustment.SCALE_MIN, ImageAdjustment.SCALE_MAX)
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
                            // COVER 和 FIT 模式统一允许自由 X/Y 轴移动
                            callback.invoke(
                                latestAdjustment.copy(
                                    offsetX = latestAdjustment.offsetX + delta.x,
                                    offsetY = latestAdjustment.offsetY + delta.y
                                )
                            )
                            change.consume()
                        }
                    } else if (pointers.isEmpty()) {
                        isZooming = false
                    }
                } while (pointers.any { it.pressed })
            }
        }
    } else Modifier

    // ━━━━━ 渲染计算（Compose 与 Canvas Matrix 映射算法） ━━━━━
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(gestureModifier),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = wallpaper.filePath,
            contentDescription = wallpaper.fileName,
            contentScale = ContentScale.FillBounds, // 基底铺满，由 graphicsLayer 修正确切比例
            colorFilter = colorFilter,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val mirrorX = if (adjustment.mirrorHorizontal) -1f else 1f
                    val mirrorY = if (adjustment.mirrorVertical) -1f else 1f

                    if (adjustment.fillMode == FillMode.STRETCH) {
                        // STRETCH: 强制填满屏幕，直接应用镜像
                        scaleX = mirrorX
                        scaleY = mirrorY
                        translationX = 0f
                        translationY = 0f
                    } else {
                        // COVER & FIT
                        val baseScale = if (adjustment.fillMode == FillMode.COVER) {
                            max(screenWidth / imageWidth, screenHeight / imageHeight)
                        } else {
                            min(screenWidth / imageWidth, screenHeight / imageHeight)
                        }

                        val finalScale = baseScale * adjustment.scale

                        // 由于 AsyncImage 使用了 FillBounds 撑满屏幕，
                        // 我们需要计算出实际画面该有的视觉宽高，然后除以屏幕宽高进行反向修正。
                        val visualWidth = imageWidth * finalScale
                        val visualHeight = imageHeight * finalScale

                        scaleX = (visualWidth / screenWidth) * mirrorX
                        scaleY = (visualHeight / screenHeight) * mirrorY

                        // 默认 transformOrigin = Center，保证缩放后依旧居中。
                        // 此时的偏移量就是用户手指滑动的绝对物理距离。
                        translationX = adjustment.offsetX
                        translationY = adjustment.offsetY
                    }
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
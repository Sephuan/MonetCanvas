@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sephuan.monetcanvas.ui.screens.preview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.sephuan.monetcanvas.R
import com.sephuan.monetcanvas.data.db.WallpaperEntity
import com.sephuan.monetcanvas.data.model.FillMode
import com.sephuan.monetcanvas.data.model.ImageAdjustment
import com.sephuan.monetcanvas.data.model.WallpaperType

@Composable
fun PreviewMediaSection(
    wallpaper: WallpaperEntity,
    player: ExoPlayer?,
    isApplying: Boolean,
    onFullScreenClick: () -> Unit,
    modifier: Modifier = Modifier,
    adjustment: ImageAdjustment? = null,
    onAdjustmentChange: ((ImageAdjustment) -> Unit)? = null
) {
    val adj = adjustment ?: ImageAdjustment.DEFAULT

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(22.dp))
            .background(
                if (wallpaper.type == WallpaperType.STATIC && adj.fillMode == FillMode.FIT) {
                    adj.backgroundColor
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
    ) {
        if (wallpaper.type == WallpaperType.STATIC) {
            StaticImageContent(
                wallpaper = wallpaper,
                adjustment = adj,
                onAdjustmentChange = onAdjustmentChange
            )
        } else {
            VideoContent(player = player)
        }

        // 全屏按钮
        FilledTonalIconButton(
            onClick = onFullScreenClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
        ) {
            Icon(
                Icons.Outlined.Fullscreen,
                contentDescription = stringResource(R.string.fullscreen_preview)
            )
        }

        // 设置中遮罩
        AnimatedVisibility(
            visible = isApplying,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.30f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.setting_wallpaper),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

// ━━━━━ 静态图片内容（支持调整 + 手势）━━━━━

@Composable
private fun StaticImageContent(
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
                .then(
                    if (adjustment.fillMode == FillMode.FREE) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.fillMaxSize()
                    }
                )
                .graphicsLayer {
                    scaleX = adjustment.scale * (if (adjustment.mirrorHorizontal) -1f else 1f)
                    scaleY = if (adjustment.mirrorVertical) -1f else 1f
                    translationX = adjustment.offsetX
                    translationY = adjustment.offsetY
                }
        )
    }
}

// ━━━━━ 视频内容（不受调整参数影响）━━━━━

@Composable
private fun VideoContent(player: ExoPlayer?) {
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

// ━━━━━ 色彩滤镜构建 ━━━━━

private fun buildColorFilter(adjustment: ImageAdjustment): ColorFilter? {
    val b = adjustment.brightness
    val c = adjustment.contrast
    val s = adjustment.saturation

    if (b == 0f && c == 0f && s == 0f) return null

    // 亮度矩阵：平移 RGB
    val brightnessOffset = b * 255f
    val brightnessMatrix = ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, brightnessOffset,
            0f, 1f, 0f, 0f, brightnessOffset,
            0f, 0f, 1f, 0f, brightnessOffset,
            0f, 0f, 0f, 1f, 0f
        )
    )

    // 对比度矩阵：缩放 RGB 并偏移
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

    // 饱和度矩阵
    val saturationMatrix = ColorMatrix()
    // s 范围 -1~1，映射到 setToSaturation 的 0~2
    saturationMatrix.setToSaturation((1f + s).coerceIn(0f, 2f))

    // 合并
    val result = ColorMatrix()
    result.timesAssign(brightnessMatrix)
    result.timesAssign(contrastMatrix)
    result.timesAssign(saturationMatrix)

    return ColorFilter.colorMatrix(result)
}
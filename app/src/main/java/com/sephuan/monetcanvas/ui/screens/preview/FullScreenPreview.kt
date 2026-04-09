package com.sephuan.monetcanvas.ui.screens.preview

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.sephuan.monetcanvas.R
import com.sephuan.monetcanvas.data.db.WallpaperEntity
import com.sephuan.monetcanvas.data.model.FillMode
import com.sephuan.monetcanvas.data.model.ImageAdjustment
import com.sephuan.monetcanvas.data.model.WallpaperType
import kotlin.math.max
import kotlin.math.min

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun FullScreenPreview(
    wallpaper: WallpaperEntity,
    adjustment: ImageAdjustment = ImageAdjustment.DEFAULT,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }.toInt()
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }.toInt()

    val closeDesc = stringResource(R.string.close_fullscreen)

    var showControls by remember { mutableStateOf(true) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    // 沉浸模式
    DisposableEffect(activity) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // 初始化播放器（仅动态壁纸）
    DisposableEffect(wallpaper.filePath, wallpaper.type) {
        if (wallpaper.type == WallpaperType.LIVE) {
            player = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri("file://${wallpaper.filePath}"))
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f
                prepare()
                playWhenReady = true
            }
        }
        onDispose {
            player?.runCatching {
                pause()
                stop()
                release()
            }
            player = null
        }
    }

    fun safeExit() {
        player?.runCatching {
            pause()
            stop()
            release()
        }
        player = null
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showControls = !showControls
            }
    ) {
        // 当前内容层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            when (wallpaper.type) {
                WallpaperType.STATIC -> {
                    StaticFullScreenContent(
                        wallpaper = wallpaper,
                        adjustment = adjustment,
                        screenWidth = screenWidthPx,
                        screenHeight = screenHeightPx
                    )
                }

                WallpaperType.LIVE -> {
                    player?.let { exoPlayer ->
                        AndroidView(
                            factory = { ctx ->
                                androidx.media3.ui.PlayerView(ctx).apply {
                                    useController = false
                                    player = exoPlayer
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
            }
        }

        // 顶部关闭按钮
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(200)) + slideInVertically { -it / 2 },
            exit = fadeOut(tween(150)) + slideOutVertically { -it / 2 },
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Row(
                modifier = Modifier.padding(top = 48.dp, start = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(onClick = { safeExit() }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = closeDesc
                    )
                }
            }
        }

        // 底部信息栏
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(200)) + slideInVertically { it / 2 },
            exit = fadeOut(tween(150)) + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Surface(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 48.dp),
                shape = MaterialTheme.shapes.medium,
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (wallpaper.type == WallpaperType.LIVE) {
                        Icon(
                            imageVector = Icons.Outlined.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.width(8.dp))
                    }

                    Column {
                        Text(
                            text = wallpaper.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = "${wallpaper.width} × ${wallpaper.height}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StaticFullScreenContent(
    wallpaper: WallpaperEntity,
    adjustment: ImageAdjustment,
    screenWidth: Int,
    screenHeight: Int
) {
    val imageWidth = wallpaper.width.toFloat().coerceAtLeast(1f)
    val imageHeight = wallpaper.height.toFloat().coerceAtLeast(1f)
    val screenWidthF = screenWidth.toFloat().coerceAtLeast(1f)
    val screenHeightF = screenHeight.toFloat().coerceAtLeast(1f)

    val colorFilter = buildColorFilter(adjustment)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(adjustment.backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = wallpaper.filePath,
            contentDescription = wallpaper.fileName,
            contentScale = ContentScale.FillBounds,
            colorFilter = colorFilter,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val mirrorX = if (adjustment.mirrorHorizontal) -1f else 1f
                    val mirrorY = if (adjustment.mirrorVertical) -1f else 1f

                    if (adjustment.fillMode == FillMode.STRETCH) {
                        scaleX = mirrorX
                        scaleY = mirrorY
                        translationX = 0f
                        translationY = 0f
                    } else {
                        val baseScale = if (adjustment.fillMode == FillMode.COVER) {
                            max(screenWidthF / imageWidth, screenHeightF / imageHeight)
                        } else {
                            min(screenWidthF / imageWidth, screenHeightF / imageHeight)
                        }

                        val safeUserScale = adjustment.scale.coerceIn(
                            ImageAdjustment.SCALE_MIN,
                            ImageAdjustment.SCALE_MAX
                        )
                        val finalScale = baseScale * safeUserScale

                        val visualWidth = imageWidth * finalScale
                        val visualHeight = imageHeight * finalScale

                        scaleX = (visualWidth / screenWidthF) * mirrorX
                        scaleY = (visualHeight / screenHeightF) * mirrorY

                        translationX = adjustment.offsetX
                        translationY = adjustment.offsetY
                    }
                }
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
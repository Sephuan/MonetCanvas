package com.sephuan.monetcanvas.ui.screens.preview

import android.net.Uri
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.sephuan.monetcanvas.R
import com.sephuan.monetcanvas.data.db.WallpaperEntity
import com.sephuan.monetcanvas.data.model.WallpaperType
import kotlinx.coroutines.delay

@Composable
fun FullScreenPreview(
    wallpaper: WallpaperEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val systemUiController = rememberSystemUiController()

    var showControls by remember { mutableStateOf(true) }
    var isPlayerReady by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    // ★ 沉浸模式
    DisposableEffect(Unit) {
        systemUiController.isSystemBarsVisible = false
        onDispose {
            systemUiController.isSystemBarsVisible = true
        }
    }

    // ★ 初始化 ExoPlayer（仅动态壁纸）
    LaunchedEffect(wallpaper.filePath) {
        if (wallpaper.type == WallpaperType.LIVE) {
            player = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.parse("file://${wallpaper.filePath}")))
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            isPlayerReady = true
                        }
                    }
                })

                prepare()
                playWhenReady = true
            }
        }
    }

    // ★ 页面销毁时提前释放
    DisposableEffect(Unit) {
        onDispose {
            player?.stop()
            player?.release()
            player = null
        }
    }

    // ★ 自动隐藏控制栏
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }

    // ★ 安全退出：先释放播放器再导航
    fun safeExit() {
        player?.stop()
        player?.release()
        player = null
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showControls = !showControls
            }
    ) {
        // ━━━━━ 内容区 ━━━━━
        when (wallpaper.type) {
            WallpaperType.STATIC -> {
                AsyncImage(
                    model = wallpaper.filePath,
                    contentDescription = wallpaper.fileName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            WallpaperType.LIVE -> {
                player?.let { exo ->
                    AndroidView(
                        factory = { ctx ->
                            androidx.media3.ui.PlayerView(ctx).apply {
                                useController = false
                                this.player = exo
                                setShutterBackgroundColor(android.graphics.Color.BLACK)
                            }
                        },
                        update = { view ->
                            if (view.player != player) {
                                view.player = player
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // ━━━━━ 顶部控制栏（带动画） ━━━━━
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(250)) + slideInVertically(tween(300)) { -it },
            exit = fadeOut(tween(250)) + slideOutVertically(tween(300)) { -it },
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Row(
                modifier = Modifier
                    .padding(top = 48.dp, start = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(onClick = ::safeExit) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.close_fullscreen)
                    )
                }
            }
        }

        // ━━━━━ 底部信息栏（带动画） ━━━━━
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(250)) + slideInVertically(tween(300)) { it },
            exit = fadeOut(tween(250)) + slideOutVertically(tween(300)) { it },
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Surface(
                modifier = Modifier.padding(16.dp, bottom = 48.dp),
                shape = MaterialTheme.shapes.medium,
                color = Color.Black.copy(alpha = 0.55f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (wallpaper.type == WallpaperType.LIVE) {
                        Icon(
                            Icons.Outlined.PlayCircle, null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.width(6.dp))
                    }

                    Text(
                        text = wallpaper.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 1
                    )

                    Spacer(Modifier.width(12.dp))

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
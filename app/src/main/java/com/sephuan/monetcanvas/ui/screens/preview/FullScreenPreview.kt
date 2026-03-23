package com.sephuan.monetcanvas.ui.screens.preview

import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.sephuan.monetcanvas.data.db.WallpaperEntity
import com.sephuan.monetcanvas.data.model.WallpaperType

@Composable
fun FullScreenPreview(
    wallpaper: WallpaperEntity,
    onDismiss: () -> Unit
) {
    val systemUiController = rememberSystemUiController()
    var showControls by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        systemUiController.isSystemBarsVisible = false
        onDispose { systemUiController.isSystemBarsVisible = true }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { showControls = !showControls }
    ) {
        if (wallpaper.type == WallpaperType.STATIC) {
            AsyncImage(
                model = wallpaper.filePath,
                contentDescription = wallpaper.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    VideoView(context).apply {
                        setVideoPath(wallpaper.filePath)
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            mp.setVolume(0f, 0f)
                            start()
                        }
                        setOnCompletionListener {
                            start()
                        }
                        setOnErrorListener { _, _, _ ->
                            true
                        }
                    }
                },
                update = { videoView ->
                    if (!videoView.isPlaying) {
                        videoView.start()
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            FilledTonalIconButton(
                onClick = onDismiss,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "关闭全屏")
            }
        }
    }
}
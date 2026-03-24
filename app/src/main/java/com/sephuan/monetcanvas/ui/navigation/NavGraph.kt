package com.sephuan.monetcanvas.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sephuan.monetcanvas.data.db.WallpaperEntity
import com.sephuan.monetcanvas.ui.screens.home.HomeScreen
import com.sephuan.monetcanvas.ui.screens.preview.FullScreenPreview
import com.sephuan.monetcanvas.ui.screens.preview.PreviewScreen
import com.sephuan.monetcanvas.ui.screens.settings.SettingsScreen

private object Routes {
    const val HOME = "home"
    const val PREVIEW = "preview/{wallpaperId}"
    const val FULL_SCREEN = "full_screen/{wallpaperId}"
    const val SETTINGS = "settings"

    fun preview(id: Long) = "preview/$id"
    fun fullScreen(id: Long) = "full_screen/$id"
}

@Composable
fun MonetNavGraph(
    navController: NavHostController = rememberNavController()
) {
    // 简单内存缓存：减少重复查库和切页闪烁
    var cachedWallpaper by remember { mutableStateOf<WallpaperEntity?>(null) }
    var cachedWallpaperId by remember { mutableLongStateOf(-1L) }

    // ★ 给整个导航容器明确背景色，进一步减少切页露白
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize()
        ) {
            // ━━━━━ 主页 ━━━━━
            composable(route = Routes.HOME) {
                ScreenContainer {
                    HomeScreen(
                        onWallpaperClick = { wallpaper ->
                            cachedWallpaper = wallpaper
                            cachedWallpaperId = wallpaper.id
                            navController.navigate(Routes.preview(wallpaper.id))
                        },
                        onOpenSettings = {
                            navController.navigate(Routes.SETTINGS)
                        }
                    )
                }
            }

            // ━━━━━ 预览页 ━━━━━
            composable(
                route = Routes.PREVIEW,
                arguments = listOf(
                    navArgument("wallpaperId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val wallpaperId = backStackEntry.arguments?.getLong("wallpaperId") ?: -1L
                val viewModel: NavPreviewViewModel = hiltViewModel()

                var wallpaper by remember { mutableStateOf<WallpaperEntity?>(null) }
                var loadFailed by remember { mutableStateOf(false) }

                LaunchedEffect(wallpaperId) {
                    loadFailed = false
                    wallpaper = null

                    if (cachedWallpaperId == wallpaperId && cachedWallpaper != null) {
                        wallpaper = cachedWallpaper
                    } else {
                        val loaded = viewModel.loadWallpaper(wallpaperId)
                        if (loaded != null) {
                            wallpaper = loaded
                            cachedWallpaper = loaded
                            cachedWallpaperId = loaded.id
                        } else {
                            loadFailed = true
                        }
                    }
                }

                ScreenContainer {
                    when {
                        loadFailed -> {
                            RecoveryScreen(
                                message = "壁纸不存在或已删除",
                                onGoHome = {
                                    navController.navigate(Routes.HOME) {
                                        popUpTo(0) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }

                        wallpaper == null -> {
                            LoadingScreen()
                        }

                        else -> {
                            PreviewScreen(
                                wallpaper = wallpaper!!,
                                onBack = { navController.popBackStack() },
                                onFullScreenClick = {
                                    navController.navigate(Routes.fullScreen(wallpaperId))
                                }
                            )
                        }
                    }
                }
            }

            // ━━━━━ 全屏预览 ━━━━━
            composable(
                route = Routes.FULL_SCREEN,
                arguments = listOf(
                    navArgument("wallpaperId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val wallpaperId = backStackEntry.arguments?.getLong("wallpaperId") ?: -1L
                val viewModel: NavPreviewViewModel = hiltViewModel()

                var wallpaper by remember { mutableStateOf<WallpaperEntity?>(null) }
                var loadFailed by remember { mutableStateOf(false) }

                LaunchedEffect(wallpaperId) {
                    loadFailed = false
                    wallpaper = null

                    if (cachedWallpaperId == wallpaperId && cachedWallpaper != null) {
                        wallpaper = cachedWallpaper
                    } else {
                        val loaded = viewModel.loadWallpaper(wallpaperId)
                        if (loaded != null) {
                            wallpaper = loaded
                            cachedWallpaper = loaded
                            cachedWallpaperId = loaded.id
                        } else {
                            loadFailed = true
                        }
                    }
                }

                ScreenContainer {
                    when {
                        loadFailed -> {
                            RecoveryScreen(
                                message = "壁纸不存在或已删除",
                                onGoHome = {
                                    navController.navigate(Routes.HOME) {
                                        popUpTo(0) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }

                        wallpaper == null -> {
                            LoadingScreen()
                        }

                        else -> {
                            FullScreenPreview(
                                wallpaper = wallpaper!!,
                                onDismiss = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }

            // ━━━━━ 设置页 ━━━━━
            composable(route = Routes.SETTINGS) {
                ScreenContainer {
                    SettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenContainer(
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        content()
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "加载中...",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecoveryScreen(
    message: String,
    onGoHome: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onGoHome) {
            Text("返回主页")
        }
    }
}
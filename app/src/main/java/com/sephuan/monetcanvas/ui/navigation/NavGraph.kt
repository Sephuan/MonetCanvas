package com.sephuan.monetcanvas.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
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
import androidx.navigation.NavBackStackEntry
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

private const val ANIM_DURATION = 350
private const val ANIM_DURATION_FAST = 250

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
    var cachedWallpaper by remember { mutableStateOf<WallpaperEntity?>(null) }
    var cachedWallpaperId by remember { mutableLongStateOf(-1L) }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        // ━━━━━ 主页 ━━━━━
        composable(
            route = Routes.HOME,
            enterTransition = {
                fadeIn(tween(ANIM_DURATION))
            },
            exitTransition = {
                fadeOut(tween(ANIM_DURATION_FAST)) +
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(ANIM_DURATION_FAST, easing = FastOutSlowInEasing),
                            targetOffset = { it / 6 }
                        )
            },
            popEnterTransition = {
                fadeIn(tween(ANIM_DURATION, easing = FastOutSlowInEasing)) +
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(ANIM_DURATION, easing = FastOutSlowInEasing),
                            initialOffset = { it / 6 }
                        )
            },
            popExitTransition = {
                fadeOut(tween(ANIM_DURATION_FAST))
            }
        ) {
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

        // ━━━━━ 预览页 ━━━━━
        composable(
            route = Routes.PREVIEW,
            arguments = listOf(navArgument("wallpaperId") { type = NavType.LongType }),
            enterTransition = {
                fadeIn(tween(ANIM_DURATION, easing = FastOutSlowInEasing)) +
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(ANIM_DURATION, easing = FastOutSlowInEasing),
                            initialOffset = { it / 4 }
                        )
            },
            exitTransition = {
                fadeOut(tween(ANIM_DURATION_FAST, easing = FastOutSlowInEasing)) +
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(ANIM_DURATION_FAST, easing = FastOutSlowInEasing),
                            targetOffset = { it / 6 }
                        )
            },
            popEnterTransition = {
                fadeIn(tween(ANIM_DURATION, easing = FastOutSlowInEasing)) +
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(ANIM_DURATION, easing = FastOutSlowInEasing),
                            initialOffset = { it / 6 }
                        )
            },
            popExitTransition = {
                fadeOut(tween(ANIM_DURATION_FAST, easing = FastOutSlowInEasing)) +
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(ANIM_DURATION_FAST, easing = FastOutSlowInEasing),
                            targetOffset = { it / 4 }
                        )
            }
        ) { backStackEntry ->
            val wallpaperId = backStackEntry.arguments?.getLong("wallpaperId") ?: -1L
            val viewModel: NavPreviewViewModel = hiltViewModel()

            var wallpaper by remember { mutableStateOf<WallpaperEntity?>(null) }
            var loadFailed by remember { mutableStateOf(false) }

            LaunchedEffect(wallpaperId) {
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

            when {
                loadFailed -> {
                    RecoveryScreen(
                        onGoHome = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }
                wallpaper == null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("加载中...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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

        // ━━━━━ 全屏预览 ━━━━━
        composable(
            route = Routes.FULL_SCREEN,
            arguments = listOf(navArgument("wallpaperId") { type = NavType.LongType }),
            enterTransition = {
                fadeIn(tween(ANIM_DURATION_FAST)) +
                        scaleIn(
                            animationSpec = tween(ANIM_DURATION, easing = FastOutSlowInEasing),
                            initialScale = 0.92f
                        )
            },
            exitTransition = {
                fadeOut(tween(ANIM_DURATION_FAST)) +
                        scaleOut(
                            animationSpec = tween(ANIM_DURATION_FAST, easing = FastOutSlowInEasing),
                            targetScale = 0.92f
                        )
            },
            popEnterTransition = {
                fadeIn(tween(ANIM_DURATION_FAST)) +
                        scaleIn(
                            animationSpec = tween(ANIM_DURATION, easing = FastOutSlowInEasing),
                            initialScale = 0.92f
                        )
            },
            popExitTransition = {
                fadeOut(tween(ANIM_DURATION_FAST)) +
                        scaleOut(
                            animationSpec = tween(ANIM_DURATION_FAST, easing = FastOutSlowInEasing),
                            targetScale = 0.92f
                        )
            }
        ) { backStackEntry ->
            val wallpaperId = backStackEntry.arguments?.getLong("wallpaperId") ?: -1L
            val viewModel: NavPreviewViewModel = hiltViewModel()

            var wallpaper by remember { mutableStateOf<WallpaperEntity?>(null) }
            var loadFailed by remember { mutableStateOf(false) }

            LaunchedEffect(wallpaperId) {
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

            when {
                loadFailed -> {
                    RecoveryScreen(
                        onGoHome = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }
                wallpaper == null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("加载中...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    FullScreenPreview(
                        wallpaper = wallpaper!!,
                        onDismiss = { navController.popBackStack() }
                    )
                }
            }
        }

        // ━━━━━ 设置页 ━━━━━
        composable(
            route = Routes.SETTINGS,
            enterTransition = {
                fadeIn(tween(ANIM_DURATION, easing = FastOutSlowInEasing)) +
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(ANIM_DURATION, easing = FastOutSlowInEasing),
                            initialOffset = { it / 4 }
                        )
            },
            exitTransition = {
                fadeOut(tween(ANIM_DURATION_FAST, easing = FastOutSlowInEasing)) +
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(ANIM_DURATION_FAST, easing = FastOutSlowInEasing),
                            targetOffset = { it / 6 }
                        )
            },
            popEnterTransition = {
                fadeIn(tween(ANIM_DURATION, easing = FastOutSlowInEasing)) +
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(ANIM_DURATION, easing = FastOutSlowInEasing),
                            initialOffset = { it / 6 }
                        )
            },
            popExitTransition = {
                fadeOut(tween(ANIM_DURATION_FAST, easing = FastOutSlowInEasing)) +
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(ANIM_DURATION_FAST, easing = FastOutSlowInEasing),
                            targetOffset = { it / 4 }
                        )
            }
        ) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun RecoveryScreen(
    onGoHome: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("壁纸不存在或已删除", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onGoHome) { Text("返回主页") }
    }
}
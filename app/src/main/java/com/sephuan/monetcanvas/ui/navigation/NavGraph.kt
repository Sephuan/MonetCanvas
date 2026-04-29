package com.sephuan.monetcanvas.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sephuan.monetcanvas.data.db.WallpaperEntity
import com.sephuan.monetcanvas.data.model.ImageAdjustment
import com.sephuan.monetcanvas.ui.screens.home.HomeScreen
import com.sephuan.monetcanvas.ui.screens.preview.FullScreenPreview
import com.sephuan.monetcanvas.ui.screens.preview.PreviewScreen
import com.sephuan.monetcanvas.ui.screens.settings.AboutScreen
import com.sephuan.monetcanvas.ui.screens.settings.SettingsScreen

private object Routes {
    const val HOME = "home"
    const val PREVIEW = "preview/{wallpaperId}"
    const val FULL_SCREEN = "full_screen/{wallpaperId}"
    const val SETTINGS = "settings"
    const val ABOUT = "settings/about"

    fun preview(id: Long) = "preview/$id"
    fun fullScreen(id: Long) = "full_screen/$id"
}

private fun String?.isFullScreenRoute(): Boolean {
    return this?.startsWith("full_screen") == true
}

@Composable
fun MonetNavGraph(
    navController: NavHostController = rememberNavController()
) {
    var cachedWallpaper by remember { mutableStateOf<WallpaperEntity?>(null) }
    var cachedWallpaperId by remember { mutableLongStateOf(-1L) }
    var cachedAdjustment by remember { mutableStateOf(ImageAdjustment.DEFAULT) }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(
            route = Routes.HOME,
            enterTransition = {
                fadeIn(animationSpec = tween(220))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(160))
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(280)
                ) + fadeIn(animationSpec = tween(220))
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(240)
                ) + fadeOut(animationSpec = tween(160))
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

        composable(
            route = Routes.PREVIEW,
            arguments = listOf(navArgument("wallpaperId") { type = NavType.LongType }),
            enterTransition = {
                fadeIn(animationSpec = tween(180))
            },
            exitTransition = {
                // 打开全屏页时，底层预览页保持稳定，不再做突兀的退场动画
                if (targetState.destination.route.isFullScreenRoute()) {
                    ExitTransition.None
                } else {
                    fadeOut(animationSpec = tween(160))
                }
            },
            popEnterTransition = {
                // 从全屏页返回时，不让预览页再“滑进来”
                // 只显示全屏层自己淡出/缩回，更像覆盖层关闭
                if (initialState.destination.route.isFullScreenRoute()) {
                    EnterTransition.None
                } else {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(280)
                    ) + fadeIn(animationSpec = tween(220))
                }
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(240)
                ) + fadeOut(animationSpec = tween(160))
            }
        ) { backStackEntry ->
            val wallpaperId = backStackEntry.arguments?.getLong("wallpaperId") ?: -1L
            val viewModel: NavPreviewViewModel = hiltViewModel()

            var wallpaper by remember(wallpaperId) { mutableStateOf<WallpaperEntity?>(null) }
            var loadFailed by remember(wallpaperId) { mutableStateOf(false) }
            var isLoading by remember(wallpaperId) { mutableStateOf(true) }

            LaunchedEffect(wallpaperId) {
                isLoading = true
                loadFailed = false

                val cached = cachedWallpaper
                if (cached != null && cachedWallpaperId == wallpaperId) {
                    wallpaper = cached
                } else {
                    val loaded = viewModel.loadWallpaper(wallpaperId)
                    if (loaded != null) {
                        wallpaper = loaded
                        cachedWallpaper = loaded
                        cachedWallpaperId = wallpaperId
                    } else {
                        wallpaper = null
                        loadFailed = true
                    }
                }

                isLoading = false
            }

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                }

                loadFailed || wallpaper == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                }

                else -> {
                    PreviewScreen(
                        wallpaper = wallpaper!!,
                        onBack = { navController.popBackStack() },
                        onFullScreenClick = { adjustment ->
                            cachedWallpaper = wallpaper
                            cachedWallpaperId = wallpaperId
                            cachedAdjustment = adjustment
                            navController.navigate(Routes.fullScreen(wallpaperId))
                        }
                    )
                }
            }
        }

        composable(
            route = Routes.FULL_SCREEN,
            arguments = listOf(navArgument("wallpaperId") { type = NavType.LongType }),
            enterTransition = {
                // 更像“全屏覆盖层展开”
                fadeIn(animationSpec = tween(180)) + scaleIn(
                    animationSpec = tween(220),
                    initialScale = 0.985f
                )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(160))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(160))
            },
            popExitTransition = {
                // 返回预览页时，仅让全屏页轻微缩小并淡出
                fadeOut(animationSpec = tween(180)) + scaleOut(
                    animationSpec = tween(220),
                    targetScale = 0.985f
                )
            }
        ) { backStackEntry ->
            val wallpaperId = backStackEntry.arguments?.getLong("wallpaperId") ?: -1L
            val viewModel: NavPreviewViewModel = hiltViewModel()

            var wallpaper by remember(wallpaperId) { mutableStateOf<WallpaperEntity?>(null) }
            var loadFailed by remember(wallpaperId) { mutableStateOf(false) }
            var isLoading by remember(wallpaperId) { mutableStateOf(true) }

            LaunchedEffect(wallpaperId) {
                isLoading = true
                loadFailed = false

                val cached = cachedWallpaper
                if (cached != null && cached.id == wallpaperId) {
                    wallpaper = cached
                } else {
                    val loaded = viewModel.loadWallpaper(wallpaperId)
                    if (loaded != null) {
                        wallpaper = loaded
                        cachedWallpaper = loaded
                        cachedWallpaperId = wallpaperId
                    } else {
                        wallpaper = null
                        loadFailed = true
                    }
                }

                isLoading = false
            }

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                }

                loadFailed || wallpaper == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                }

                else -> {
                    FullScreenPreview(
                        wallpaper = wallpaper!!,
                        adjustment = cachedAdjustment,
                        onDismiss = { navController.popBackStack() }
                    )
                }
            }
        }

        composable(
            route = Routes.SETTINGS,
            enterTransition = {
                fadeIn(animationSpec = tween(180))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(160))
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(280)
                ) + fadeIn(animationSpec = tween(220))
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(240)
                ) + fadeOut(animationSpec = tween(160))
            }
        ) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenAbout = { navController.navigate(Routes.ABOUT) }
            )
        }

        composable(
            route = Routes.ABOUT,
            enterTransition = {
                fadeIn(animationSpec = tween(180))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(160))
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(280)
                ) + fadeIn(animationSpec = tween(220))
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(240)
                ) + fadeOut(animationSpec = tween(160))
            }
        ) {
            AboutScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
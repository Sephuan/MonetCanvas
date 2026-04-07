package com.sephuan.monetcanvas.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
            // 进入首页时不要突兀，轻微淡入
            enterTransition = {
                fadeIn()
            },
            exitTransition = {
                fadeOut()
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right
                ) + fadeIn()
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right
                ) + fadeOut()
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
                fadeIn()
            },
            exitTransition = {
                fadeOut()
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right
                ) + fadeIn()
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right
                ) + fadeOut()
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
                fadeIn()
            },
            exitTransition = {
                fadeOut()
            },
            popEnterTransition = {
                fadeIn()
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right
                ) + fadeOut()
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
                fadeIn()
            },
            exitTransition = {
                fadeOut()
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right
                ) + fadeIn()
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right
                ) + fadeOut()
            }
        ) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
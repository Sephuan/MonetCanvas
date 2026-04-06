package com.sephuan.monetcanvas.ui.navigation

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
        composable(Routes.HOME) {
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
            arguments = listOf(navArgument("wallpaperId") { type = NavType.LongType })
        ) { backStackEntry ->
            val wallpaperId = backStackEntry.arguments?.getLong("wallpaperId") ?: -1L
            val viewModel: NavPreviewViewModel = hiltViewModel()

            var wallpaper by remember { mutableStateOf<WallpaperEntity?>(null) }
            var loadFailed by remember { mutableStateOf(false) }
            var isLoading by remember { mutableStateOf(true) }

            LaunchedEffect(wallpaperId) {
                isLoading = true
                loadFailed = false

                if (cachedWallpaperId == wallpaperId && cachedWallpaper != null) {
                    wallpaper = cachedWallpaper
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
                            cachedAdjustment = adjustment
                            cachedWallpaper = wallpaper
                            cachedWallpaperId = wallpaperId
                            navController.navigate(Routes.fullScreen(wallpaperId))
                        }
                    )
                }
            }
        }

        composable(
            route = Routes.FULL_SCREEN,
            arguments = listOf(navArgument("wallpaperId") { type = NavType.LongType })
        ) { backStackEntry ->
            val wallpaperId = backStackEntry.arguments?.getLong("wallpaperId") ?: -1L
            val viewModel: NavPreviewViewModel = hiltViewModel()

            var wallpaper by remember { mutableStateOf<WallpaperEntity?>(null) }
            var isLoading by remember { mutableStateOf(true) }
            var loadFailed by remember { mutableStateOf(false) }

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

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
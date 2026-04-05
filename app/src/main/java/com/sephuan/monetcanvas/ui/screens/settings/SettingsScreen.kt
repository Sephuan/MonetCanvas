package com.sephuan.monetcanvas.ui.screens.settings

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sephuan.monetcanvas.ui.screens.settings.components.AboutSection
import com.sephuan.monetcanvas.ui.screens.settings.components.DarkModeSection
import com.sephuan.monetcanvas.ui.screens.settings.components.LanguageSection
import com.sephuan.monetcanvas.ui.screens.settings.components.SettingsHeader
import com.sephuan.monetcanvas.ui.screens.settings.components.StorageSection
import com.sephuan.monetcanvas.ui.screens.settings.components.ThemeSection
import kotlinx.coroutines.flow.collect

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var backProgress by remember { mutableFloatStateOf(0f) }
    var isBackExecuted by remember { mutableStateOf(false) }

    val animatedProgress by animateFloatAsState(
        targetValue = backProgress,
        animationSpec = tween(durationMillis = 0),
        label = "settingsBackProgress"
    )

    val scale = 1f - (animatedProgress * 0.03f)
    val contentAlpha = 1f - (animatedProgress * 0.05f)
    val translateX = animatedProgress * 30f

    PredictiveBackHandler(enabled = true) { backEventFlow ->
        isBackExecuted = false
        backEventFlow.collect { backEvent ->
            backProgress = backEvent.progress
        }
        if (!isBackExecuted) {
            isBackExecuted = true
            onBack()
        }
    }

    LaunchedEffect(Unit) {
        isBackExecuted = false
        backProgress = 0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = contentAlpha
                translationX = translateX
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            SettingsHeader(onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LanguageSection()
                ThemeSection(viewModel)
                DarkModeSection(viewModel)
                StorageSection(viewModel)
                AboutSection()
            }
        }
    }
}
package com.sephuan.monetcanvas.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sephuan.monetcanvas.R
import com.sephuan.monetcanvas.ui.screens.settings.components.AboutSection
import com.sephuan.monetcanvas.ui.screens.settings.components.DarkModeSection
import com.sephuan.monetcanvas.ui.screens.settings.components.FontScaleSection
import com.sephuan.monetcanvas.ui.screens.settings.components.LanguageSection
import com.sephuan.monetcanvas.ui.screens.settings.components.StorageSection
import com.sephuan.monetcanvas.ui.screens.settings.components.ThemeSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    // 这里故意不使用 BackHandler / PredictiveBackHandler
    // 让系统与 Navigation 自己接管返回手势，显示系统原生预测返回效果

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.settings))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp) // ★ 增加卡片之间的标准间距
            ) {
                LanguageSection()
                FontScaleSection() // ★ 新增：字体大小缩放卡片
                ThemeSection(viewModel)
                DarkModeSection(viewModel)
                StorageSection(viewModel)
                AboutSection(onOpenAbout = onOpenAbout)
            }
        }
    }
}
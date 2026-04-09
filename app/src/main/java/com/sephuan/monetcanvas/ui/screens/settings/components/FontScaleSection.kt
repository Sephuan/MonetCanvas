package com.sephuan.monetcanvas.ui.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sephuan.monetcanvas.R
import com.sephuan.monetcanvas.data.datastore.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FontScaleSection() {
    val context = LocalContext.current
    val settings = remember { SettingsDataStore(context.applicationContext) }
    val scope = rememberCoroutineScope()

    val fontScale by settings.fontScaleFlow.collectAsStateWithLifecycle(initialValue = 1.0f)

    // 内部维护一个滑动状态，滑动时只更新UI，松手时才落库
    var sliderValue by remember(fontScale) { mutableFloatStateOf(fontScale) }

    SettingsCard(
        title = stringResource(R.string.settings_font_scale),
        icon = Icons.Outlined.FormatSize
    ) {
        Text(
            text = stringResource(R.string.settings_font_scale_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "A",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = sliderValue,
                onValueChange = {
                    // 四舍五入保留两位小数，防止浮点数精度抖动
                    sliderValue = (it * 100f).roundToInt() / 100f
                },
                onValueChangeFinished = {
                    scope.launch(Dispatchers.IO) {
                        settings.saveFontScale(sliderValue)
                    }
                },
                valueRange = 0.8f..1.5f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            )
            Text(
                text = "A",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${(sliderValue * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
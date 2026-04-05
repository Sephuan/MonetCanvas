package com.sephuan.monetcanvas.ui.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sephuan.monetcanvas.R
import com.sephuan.monetcanvas.data.datastore.SettingsDataStore
import com.sephuan.monetcanvas.ui.screens.settings.SettingsViewModel
import com.sephuan.monetcanvas.ui.theme.PRESET_COLORS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val PRESET_COLOR_NAME_RES = listOf(
    R.string.color_sakura,
    R.string.color_sky,
    R.string.color_mint,
    R.string.color_amber,
    R.string.color_lavender,
    R.string.color_graphite,
    R.string.color_coral,
    R.string.color_teal,
    R.string.color_indigo,
    R.string.color_lemon
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeSection(viewModel: SettingsViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember { SettingsDataStore(context.applicationContext) }
    val scope = rememberCoroutineScope()

    val appColorMode by settings.appColorModeFlow.collectAsStateWithLifecycle(
        initialValue = SettingsDataStore.COLOR_MODE_MONET
    )
    val appCustomColor by settings.appCustomColorFlow.collectAsStateWithLifecycle(initialValue = null)

    SettingsCard(
        title = stringResource(R.string.settings_theme),
        icon = Icons.Outlined.Palette
    ) {
        Text(
            text = stringResource(R.string.settings_theme_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = appColorMode == SettingsDataStore.COLOR_MODE_MONET,
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        settings.saveAppColorMode(SettingsDataStore.COLOR_MODE_MONET)
                    }
                },
                label = { Text(stringResource(R.string.theme_monet)) }
            )
            FilterChip(
                selected = appColorMode == SettingsDataStore.COLOR_MODE_RULE,
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        settings.saveAppColorMode(SettingsDataStore.COLOR_MODE_RULE)
                    }
                },
                label = { Text(stringResource(R.string.theme_rule)) }
            )
            FilterChip(
                selected = appColorMode == SettingsDataStore.COLOR_MODE_CUSTOM,
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        settings.saveAppColorMode(SettingsDataStore.COLOR_MODE_CUSTOM)
                    }
                },
                label = { Text(stringResource(R.string.theme_custom)) }
            )
        }

        if (appColorMode == SettingsDataStore.COLOR_MODE_CUSTOM) {
            Spacer(Modifier.size(12.dp))
            Text(
                text = stringResource(R.string.choose_theme_color),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.size(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PRESET_COLORS.forEach { preset ->
                    val isSelected = appCustomColor == preset.colorInt
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(preset.color)
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        3.dp,
                                        MaterialTheme.colorScheme.onSurface,
                                        CircleShape
                                    )
                                } else Modifier
                            )
                            .clickable {
                                scope.launch(Dispatchers.IO) {
                                    settings.saveAppCustomColor(preset.colorInt)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.size(4.dp))
            val selectedIndex = PRESET_COLORS.indexOfFirst { it.colorInt == appCustomColor }
            val selectedName = if (selectedIndex in PRESET_COLOR_NAME_RES.indices) {
                stringResource(PRESET_COLOR_NAME_RES[selectedIndex])
            } else {
                stringResource(R.string.not_selected)
            }

            Text(
                text = stringResource(R.string.current_format, selectedName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (appColorMode == SettingsDataStore.COLOR_MODE_RULE) {
            Spacer(Modifier.size(4.dp))
            Text(
                text = stringResource(R.string.theme_rule_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
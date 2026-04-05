package com.sephuan.monetcanvas.ui.screens.settings.components

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PhoneAndroid
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sephuan.monetcanvas.R
import com.sephuan.monetcanvas.data.datastore.SettingsDataStore
import com.sephuan.monetcanvas.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun DarkModeSection(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val settings = remember { SettingsDataStore(context.applicationContext) }
    val scope = rememberCoroutineScope()

    val darkMode by settings.darkModeFlow.collectAsStateWithLifecycle(
        initialValue = SettingsDataStore.DARK_MODE_SYSTEM
    )

    SettingsCard(
        title = stringResource(R.string.settings_dark_mode),
        icon = Icons.Outlined.DarkMode
    ) {
        Text(
            text = stringResource(R.string.settings_dark_mode_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = darkMode == SettingsDataStore.DARK_MODE_SYSTEM,
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        settings.saveDarkMode(SettingsDataStore.DARK_MODE_SYSTEM)
                    }
                    (context as? Activity)?.recreate()
                },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.PhoneAndroid,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.dark_system))
                    }
                }
            )
            FilterChip(
                selected = darkMode == SettingsDataStore.DARK_MODE_LIGHT,
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        settings.saveDarkMode(SettingsDataStore.DARK_MODE_LIGHT)
                    }
                    (context as? Activity)?.recreate()
                },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.LightMode,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.dark_light))
                    }
                }
            )
            FilterChip(
                selected = darkMode == SettingsDataStore.DARK_MODE_DARK,
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        settings.saveDarkMode(SettingsDataStore.DARK_MODE_DARK)
                    }
                    (context as? Activity)?.recreate()
                },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.DarkMode,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.dark_dark))
                    }
                }
            )
        }
    }
}
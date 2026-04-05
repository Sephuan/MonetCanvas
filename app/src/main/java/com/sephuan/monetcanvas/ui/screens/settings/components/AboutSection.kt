package com.sephuan.monetcanvas.ui.screens.settings.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sephuan.monetcanvas.R

@Composable
fun AboutSection() {
    SettingsCard(
        title = stringResource(R.string.settings_about),
        icon = Icons.Outlined.Info
    ) {
        Text("MonetCanvas v1.0.0", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.size(4.dp))
        Text(
            text = stringResource(R.string.about_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = stringResource(R.string.about_storage_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
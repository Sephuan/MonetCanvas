package com.sephuan.monetcanvas.ui.screens.settings.components

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sephuan.monetcanvas.R
import com.sephuan.monetcanvas.util.LocaleHelper

@Composable
fun LanguageSection() {
    val context = LocalContext.current
    var currentLang by remember { mutableStateOf(LocaleHelper.getLanguage(context)) }
    var showRestartDialog by remember { mutableStateOf(false) }

    SettingsCard(
        title = stringResource(R.string.settings_language),
        icon = Icons.Outlined.Language
    ) {
        Text(
            text = stringResource(R.string.settings_language_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LanguageChip(
                label = stringResource(R.string.lang_system),
                selected = currentLang == LocaleHelper.LANG_SYSTEM,
                onClick = {
                    LocaleHelper.setLanguage(context, LocaleHelper.LANG_SYSTEM)
                    currentLang = LocaleHelper.LANG_SYSTEM
                    showRestartDialog = true
                }
            )
            LanguageChip(
                label = stringResource(R.string.lang_zh),
                selected = currentLang == LocaleHelper.LANG_ZH,
                onClick = {
                    LocaleHelper.setLanguage(context, LocaleHelper.LANG_ZH)
                    currentLang = LocaleHelper.LANG_ZH
                    showRestartDialog = true
                }
            )
            LanguageChip(
                label = stringResource(R.string.lang_en),
                selected = currentLang == LocaleHelper.LANG_EN,
                onClick = {
                    LocaleHelper.setLanguage(context, LocaleHelper.LANG_EN)
                    currentLang = LocaleHelper.LANG_EN
                    showRestartDialog = true
                }
            )
        }
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text(stringResource(R.string.lang_restart_title)) },
            text = { Text(stringResource(R.string.lang_restart_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        showRestartDialog = false
                        (context as? Activity)?.recreate()
                    }
                ) {
                    Text(stringResource(R.string.restart_now))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) {
                    Text(stringResource(R.string.restart_later))
                }
            }
        )
    }
}

@Composable
private fun LanguageChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}
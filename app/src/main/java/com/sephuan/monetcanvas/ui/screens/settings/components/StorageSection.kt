package com.sephuan.monetcanvas.ui.screens.settings.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sephuan.monetcanvas.R
import com.sephuan.monetcanvas.data.datastore.SettingsDataStore
import com.sephuan.monetcanvas.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun StorageSection(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val settings = remember { SettingsDataStore(context.applicationContext) }
    val scope = rememberCoroutineScope()

    val storageTreeUri by settings.storageTreeUriFlow.collectAsStateWithLifecycle(initialValue = null)
    val syncState by viewModel.syncUiState.collectAsStateWithLifecycle()
    val backupState by viewModel.backupUiState.collectAsStateWithLifecycle()
    val migrateState by viewModel.migrateUiState.collectAsStateWithLifecycle()

    var pendingNewTreeUri by remember { mutableStateOf<Uri?>(null) }
    var showMigrateDialog by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }

            if (!storageTreeUri.isNullOrBlank()) {
                pendingNewTreeUri = uri
                showMigrateDialog = true
            } else {
                scope.launch(Dispatchers.IO) {
                    settings.saveStorageTreeUri(uri.toString())
                }
            }
        }
    }

    SettingsCard(
        title = stringResource(R.string.settings_storage),
        icon = Icons.Outlined.Storage
    ) {
        val displayPath = remember(storageTreeUri) {
            if (storageTreeUri.isNullOrBlank()) null
            else parseTreeDisplayName(storageTreeUri!!)
        }

        Text(
            text = stringResource(
                R.string.backup_dir_format,
                displayPath ?: stringResource(R.string.default_storage)
            ),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.size(4.dp))

        Text(
            text = stringResource(R.string.storage_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { folderPicker.launch(null) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.select_dir))
            }

            OutlinedButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        settings.saveStorageTreeUri(null)
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Outlined.RestartAlt, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.cancel_backup_dir))
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 10.dp))

        Button(
            onClick = { viewModel.syncFromCustomStorage(context) },
            enabled = !syncState.running && !backupState.running,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Outlined.Sync, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (syncState.running) stringResource(R.string.syncing)
                else stringResource(R.string.sync_wallpapers)
            )
        }

        Spacer(Modifier.size(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { viewModel.cleanupOnly() },
                enabled = !syncState.running && !backupState.running,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Outlined.CleaningServices, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.cleanup_invalid))
            }

            OutlinedButton(
                onClick = { viewModel.backupAllToCustomStorage(context) },
                enabled = !syncState.running && !backupState.running && !storageTreeUri.isNullOrBlank(),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Outlined.Backup, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    if (backupState.running) stringResource(R.string.backing_up)
                    else stringResource(R.string.backup_all)
                )
            }
        }

        if (syncState.message.isNotBlank()) {
            Spacer(Modifier.size(8.dp))
            Text(
                text = syncState.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!syncState.running &&
            (syncState.imported > 0 || syncState.skipped > 0 || syncState.failed > 0 || syncState.cleaned > 0)
        ) {
            Text(
                text = buildString {
                    if (syncState.cleaned > 0) append("${context.getString(R.string.cleaned_format, syncState.cleaned)}  ")
                    if (syncState.imported > 0) append("${context.getString(R.string.imported_format, syncState.imported)}  ")
                    if (syncState.skipped > 0) append("${context.getString(R.string.skipped_format, syncState.skipped)}  ")
                    if (syncState.failed > 0) append(context.getString(R.string.failed_format, syncState.failed))
                }.trim(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (backupState.message.isNotBlank()) {
            Spacer(Modifier.size(8.dp))
            Text(
                text = backupState.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!backupState.running &&
            (backupState.backed > 0 || backupState.skipped > 0 || backupState.failed > 0)
        ) {
            Text(
                text = buildString {
                    if (backupState.backed > 0) append("${context.getString(R.string.new_backup_format, backupState.backed)}  ")
                    if (backupState.skipped > 0) append("${context.getString(R.string.skipped_format, backupState.skipped)}  ")
                    if (backupState.failed > 0) append(context.getString(R.string.failed_format, backupState.failed))
                }.trim(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (migrateState.message.isNotBlank()) {
            Spacer(Modifier.size(4.dp))
            Text(
                text = migrateState.message,
                style = MaterialTheme.typography.bodySmall,
                color = if (migrateState.failed > 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
        }
    }

    if (showMigrateDialog && pendingNewTreeUri != null) {
        AlertDialog(
            onDismissRequest = {
                showMigrateDialog = false
                pendingNewTreeUri = null
            },
            title = { Text(stringResource(R.string.switch_backup_dir)) },
            text = {
                Column {
                    Text(stringResource(R.string.migrate_dialog_text))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "• ${stringResource(R.string.migrate_option)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = "• ${stringResource(R.string.no_migrate_option)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = "• ${stringResource(R.string.migrate_note)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMigrateDialog = false
                        viewModel.switchStorageDirectory(context, pendingNewTreeUri!!, true)
                        pendingNewTreeUri = null
                    }
                ) {
                    Text(stringResource(R.string.migrate_files))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showMigrateDialog = false
                            viewModel.switchStorageDirectory(context, pendingNewTreeUri!!, false)
                            pendingNewTreeUri = null
                        }
                    ) {
                        Text(stringResource(R.string.no_migrate))
                    }
                    TextButton(
                        onClick = {
                            showMigrateDialog = false
                            pendingNewTreeUri = null
                        }
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }
}

private fun parseTreeDisplayName(uriStr: String): String {
    return runCatching {
        val uri = uriStr.toUri()
        val treeId = android.provider.DocumentsContract.getTreeDocumentId(uri)
        val volume = treeId.substringBefore(":", "")
        val path = treeId.substringAfter(":", "")
        val readableVolume = if (volume.equals("primary", ignoreCase = true)) "Internal" else volume
        if (path.isBlank()) "Root ($readableVolume)" else "$path ($readableVolume)"
    }.getOrElse { "Custom" }
}
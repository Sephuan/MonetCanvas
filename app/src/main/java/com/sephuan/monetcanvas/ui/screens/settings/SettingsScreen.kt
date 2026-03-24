package com.sephuan.monetcanvas.ui.screens.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sephuan.monetcanvas.R
import com.sephuan.monetcanvas.data.datastore.SettingsDataStore
import com.sephuan.monetcanvas.ui.theme.PRESET_COLORS
import com.sephuan.monetcanvas.util.LocaleHelper
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
fun SettingsScreen(
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val settings = remember { SettingsDataStore(context.applicationContext) }
    val ioScope = rememberCoroutineScope()

    val appColorMode by settings.appColorModeFlow.collectAsStateWithLifecycle(
        initialValue = SettingsDataStore.COLOR_MODE_MONET
    )
    val appCustomColor by settings.appCustomColorFlow.collectAsStateWithLifecycle(initialValue = null)
    val darkMode by settings.darkModeFlow.collectAsStateWithLifecycle(
        initialValue = SettingsDataStore.DARK_MODE_SYSTEM
    )
    val storageTreeUri by settings.storageTreeUriFlow.collectAsStateWithLifecycle(initialValue = null)

    val syncState by viewModel.syncUiState.collectAsStateWithLifecycle()
    val migrateState by viewModel.migrateUiState.collectAsStateWithLifecycle()
    val backupState by viewModel.backupUiState.collectAsStateWithLifecycle()

    var currentLang by remember { mutableStateOf(LocaleHelper.getLanguage(context)) }
    var pendingNewTreeUri by remember { mutableStateOf<Uri?>(null) }
    var showMigrateDialog by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }

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
                ioScope.launch(Dispatchers.IO) {
                    settings.saveStorageTreeUri(uri.toString())
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ━━━━━ 顶部栏 ━━━━━
        SettingsHeader(
            title = stringResource(R.string.settings),
            onBack = onBack
        )

        // ━━━━━ 内容区 ━━━━━
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ━━━━━ 1. 语言 ━━━━━
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

            // ━━━━━ 2. 软件配色 ━━━━━
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
                            ioScope.launch(Dispatchers.IO) {
                                settings.saveAppColorMode(SettingsDataStore.COLOR_MODE_MONET)
                            }
                        },
                        label = { Text(stringResource(R.string.theme_monet)) }
                    )
                    FilterChip(
                        selected = appColorMode == SettingsDataStore.COLOR_MODE_RULE,
                        onClick = {
                            ioScope.launch(Dispatchers.IO) {
                                settings.saveAppColorMode(SettingsDataStore.COLOR_MODE_RULE)
                            }
                        },
                        label = { Text(stringResource(R.string.theme_rule)) }
                    )
                    FilterChip(
                        selected = appColorMode == SettingsDataStore.COLOR_MODE_CUSTOM,
                        onClick = {
                            ioScope.launch(Dispatchers.IO) {
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
                                        ioScope.launch(Dispatchers.IO) {
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

            // ━━━━━ 3. 深色模式 ━━━━━
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
                    DarkModeChip(
                        label = stringResource(R.string.dark_system),
                        icon = Icons.Outlined.PhoneAndroid,
                        selected = darkMode == SettingsDataStore.DARK_MODE_SYSTEM,
                        onClick = {
                            ioScope.launch(Dispatchers.IO) {
                                settings.saveDarkMode(SettingsDataStore.DARK_MODE_SYSTEM)
                            }
                            (context as? Activity)?.recreate()
                        }
                    )
                    DarkModeChip(
                        label = stringResource(R.string.dark_light),
                        icon = Icons.Outlined.LightMode,
                        selected = darkMode == SettingsDataStore.DARK_MODE_LIGHT,
                        onClick = {
                            ioScope.launch(Dispatchers.IO) {
                                settings.saveDarkMode(SettingsDataStore.DARK_MODE_LIGHT)
                            }
                            (context as? Activity)?.recreate()
                        }
                    )
                    DarkModeChip(
                        label = stringResource(R.string.dark_dark),
                        icon = Icons.Outlined.DarkMode,
                        selected = darkMode == SettingsDataStore.DARK_MODE_DARK,
                        onClick = {
                            ioScope.launch(Dispatchers.IO) {
                                settings.saveDarkMode(SettingsDataStore.DARK_MODE_DARK)
                            }
                            (context as? Activity)?.recreate()
                        }
                    )
                }
            }

            // ━━━━━ 4. 存储管理 ━━━━━
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
                            ioScope.launch(Dispatchers.IO) {
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

            // ━━━━━ 5. 关于 ━━━━━
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

            Spacer(Modifier.size(24.dp))
        }
    }

    // ━━━━━ 重启确认弹窗（语言） ━━━━━
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

    // ━━━━━ 目录迁移弹窗 ━━━━━
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

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//  通用组件
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun SettingsHeader(
    title: String,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.size(10.dp))
            content()
        }
    }
}

@Composable
private fun DarkModeChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(label)
            }
        }
    )
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

private fun parseTreeDisplayName(uriStr: String): String {
    return runCatching {
        val uri = uriStr.toUri()
        val treeId = android.provider.DocumentsContract.getTreeDocumentId(uri)
        val volume = treeId.substringBefore(":", "")
        val path = treeId.substringAfter(":", "")
        val readableVolume =
            if (volume.equals("primary", ignoreCase = true)) "Internal" else volume

        if (path.isBlank()) "Root ($readableVolume)" else "$path ($readableVolume)"
    }.getOrElse { "Custom" }
}
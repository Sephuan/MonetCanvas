package com.sephuan.monetcanvas.ui.screens.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sephuan.monetcanvas.data.datastore.SettingsDataStore
import com.sephuan.monetcanvas.data.model.WallpaperType
import com.sephuan.monetcanvas.data.repository.WallpaperRepository
import com.sephuan.monetcanvas.util.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.provider.DocumentsContract

data class SyncUiState(
    val running: Boolean = false,
    val imported: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val cleaned: Int = 0,
    val message: String = ""
)

data class MigrateUiState(
    val running: Boolean = false,
    val moved: Int = 0,
    val failed: Int = 0,
    val message: String = ""
)

data class BackupUiState(
    val running: Boolean = false,
    val backed: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val message: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: WallpaperRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsVM"
    }

    private val _syncUiState = MutableStateFlow(SyncUiState())
    val syncUiState: StateFlow<SyncUiState> = _syncUiState.asStateFlow()

    private val _migrateUiState = MutableStateFlow(MigrateUiState())
    val migrateUiState: StateFlow<MigrateUiState> = _migrateUiState.asStateFlow()

    private val _backupUiState = MutableStateFlow(BackupUiState())
    val backupUiState: StateFlow<BackupUiState> = _backupUiState.asStateFlow()

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  同步（导入新文件 + 清理无效记录）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    fun syncFromCustomStorage(context: Context) {
        viewModelScope.launch {
            _syncUiState.value = SyncUiState(running = true, message = "正在清理无效记录...")

            val cleanupResult = withContext(Dispatchers.IO) {
                repository.cleanupInvalidEntries()
            }

            Log.d(TAG, "清理完成: 移除 ${cleanupResult.removed} 条无效记录")

            _syncUiState.value = SyncUiState(
                running = true,
                cleaned = cleanupResult.removed,
                message = if (cleanupResult.removed > 0)
                    "已清理 ${cleanupResult.removed} 条无效记录"
                else "无需清理"
            )

            val treeUriStr = settingsDataStore.storageTreeUriFlow.first()
            if (treeUriStr.isNullOrBlank()) {
                _syncUiState.value = _syncUiState.value.copy(
                    running = false,
                    message = if (cleanupResult.removed > 0)
                        "已清理 ${cleanupResult.removed} 条无效记录（未设置备份目录，跳过导入）"
                    else "未设置备份目录"
                )
                return@launch
            }

            val treeUri = Uri.parse(treeUriStr)
            var imported = 0
            var skipped = 0
            var failed = 0

            _syncUiState.value = _syncUiState.value.copy(message = "正在扫描备份目录...")

            val candidates = withContext(Dispatchers.IO) {
                collectSyncCandidates(context, treeUri)
            }

            Log.d(TAG, "扫描到 ${candidates.size} 个候选文件")

            for (candidate in candidates) {
                val isDup = repository.isDuplicate(
                    candidate.displayName, candidate.size, candidate.type
                )

                if (isDup) {
                    skipped++
                    _syncUiState.value = _syncUiState.value.copy(
                        imported = imported, skipped = skipped, failed = failed,
                        message = "跳过重复：${candidate.displayName}"
                    )
                    continue
                }

                val entity = withContext(Dispatchers.IO) {
                    FileUtils.importFromDocumentUri(
                        context, candidate.uri, candidate.displayName, candidate.type
                    )
                }

                if (entity != null) {
                    repository.insert(entity)
                    imported++
                    _syncUiState.value = _syncUiState.value.copy(
                        imported = imported, skipped = skipped, failed = failed,
                        message = "已导入：${candidate.displayName}"
                    )
                } else {
                    failed++
                    _syncUiState.value = _syncUiState.value.copy(
                        imported = imported, skipped = skipped, failed = failed,
                        message = "导入失败：${candidate.displayName}"
                    )
                }
            }

            _syncUiState.value = SyncUiState(
                running = false,
                imported = imported, skipped = skipped, failed = failed,
                cleaned = cleanupResult.removed,
                message = "同步完成"
            )
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  仅清理无效记录
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    fun cleanupOnly() {
        viewModelScope.launch {
            _syncUiState.value = SyncUiState(running = true, message = "正在清理...")

            val result = withContext(Dispatchers.IO) {
                repository.cleanupInvalidEntries()
            }

            _syncUiState.value = SyncUiState(
                running = false,
                cleaned = result.removed,
                message = if (result.removed > 0)
                    "已清理 ${result.removed} 条无效记录：${result.removedNames.take(3).joinToString()}"
                else "所有壁纸文件均有效"
            )
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  切换自定义目录（迁移文件）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    fun switchStorageDirectory(
        context: Context,
        newTreeUri: Uri,
        shouldMigrate: Boolean
    ) {
        viewModelScope.launch {
            val oldTreeUriStr = settingsDataStore.storageTreeUriFlow.first()

            if (shouldMigrate && !oldTreeUriStr.isNullOrBlank()) {
                _migrateUiState.value = MigrateUiState(running = true, message = "正在迁移文件...")

                val oldTreeUri = Uri.parse(oldTreeUriStr)

                val result = withContext(Dispatchers.IO) {
                    FileUtils.migrateCustomStorage(context, oldTreeUri, newTreeUri)
                }

                _migrateUiState.value = MigrateUiState(
                    running = false,
                    moved = result.moved,
                    failed = result.failed,
                    message = "迁移完成：移动 ${result.moved} 个文件" +
                            if (result.failed > 0) "，${result.failed} 个失败" else ""
                )

                Log.d(TAG, "目录迁移完成: moved=${result.moved}, failed=${result.failed}")
            } else {
                _migrateUiState.value = MigrateUiState(message = "已切换目录（未迁移旧文件）")
            }

            settingsDataStore.saveStorageTreeUri(newTreeUri.toString())
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  ★ 备份全部（带去重检查）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    fun backupAllToCustomStorage(context: Context) {
        viewModelScope.launch {
            val treeUriStr = settingsDataStore.storageTreeUriFlow.first()
            if (treeUriStr.isNullOrBlank()) {
                _backupUiState.value = BackupUiState(message = "未设置备份目录")
                return@launch
            }

            _backupUiState.value = BackupUiState(running = true, message = "正在备份...")

            val treeUri = Uri.parse(treeUriStr)
            val allWallpapers = repository.getAllWallpapers().first()
            var backed = 0
            var skipped = 0
            var failed = 0

            for ((index, entity) in allWallpapers.withIndex()) {
                if (!FileUtils.isFileValid(entity)) {
                    skipped++
                    continue
                }

                _backupUiState.value = _backupUiState.value.copy(
                    message = "正在检查：${entity.fileName} (${index + 1}/${allWallpapers.size})"
                )

                val result = withContext(Dispatchers.IO) {
                    FileUtils.mirrorToCustomStorage(context, treeUri, entity)
                }

                when (result) {
                    FileUtils.MirrorResult.SUCCESS -> {
                        backed++
                        _backupUiState.value = _backupUiState.value.copy(
                            backed = backed, skipped = skipped, failed = failed,
                            message = "已备份：${entity.fileName}"
                        )
                    }
                    FileUtils.MirrorResult.SKIPPED -> {
                        skipped++
                        _backupUiState.value = _backupUiState.value.copy(
                            backed = backed, skipped = skipped, failed = failed,
                            message = "跳过（已存在）：${entity.fileName}"
                        )
                    }
                    FileUtils.MirrorResult.FAILED -> {
                        failed++
                        _backupUiState.value = _backupUiState.value.copy(
                            backed = backed, skipped = skipped, failed = failed,
                            message = "备份失败：${entity.fileName}"
                        )
                    }
                }
            }

            _backupUiState.value = BackupUiState(
                running = false,
                backed = backed, skipped = skipped, failed = failed,
                message = "备份完成"
            )
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  内部工具
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private data class SyncCandidate(
        val uri: Uri,
        val displayName: String,
        val size: Long,
        val type: WallpaperType
    )

    private fun collectSyncCandidates(
        context: Context,
        treeUri: Uri
    ): List<SyncCandidate> {
        val result = mutableListOf<SyncCandidate>()
        val resolver = context.contentResolver

        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocId)

        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null, null, null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val docId = if (idCol >= 0) cursor.getString(idCol) else continue
                val name = if (nameCol >= 0) cursor.getString(nameCol) else continue
                val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else ""

                if (mime != DocumentsContract.Document.MIME_TYPE_DIR) continue

                val type = when (name.lowercase()) {
                    "static" -> WallpaperType.STATIC
                    "live" -> WallpaperType.LIVE
                    else -> null
                } ?: continue

                val folderUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                val folderId = DocumentsContract.getDocumentId(folderUri)
                val folderChildrenUri =
                    DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderId)

                resolver.query(
                    folderChildrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE
                    ),
                    null, null, null
                )?.use { fileCursor ->
                    val fId = fileCursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val fName = fileCursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val fMime = fileCursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val fSize = fileCursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

                    while (fileCursor.moveToNext()) {
                        val fileDocId = if (fId >= 0) fileCursor.getString(fId) else continue
                        val displayName = if (fName >= 0) fileCursor.getString(fName) else continue
                        val fileMime = if (fMime >= 0) fileCursor.getString(fMime) else ""
                        val size = if (fSize >= 0) fileCursor.getLong(fSize) else 0L

                        if (fileMime == DocumentsContract.Document.MIME_TYPE_DIR) continue

                        val documentUri =
                            DocumentsContract.buildDocumentUriUsingTree(treeUri, fileDocId)

                        result.add(
                            SyncCandidate(
                                uri = documentUri,
                                displayName = displayName,
                                size = size,
                                type = type
                            )
                        )
                    }
                }
            }
        }

        return result
    }
}
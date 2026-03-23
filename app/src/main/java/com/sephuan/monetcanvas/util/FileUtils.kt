package com.sephuan.monetcanvas.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import com.sephuan.monetcanvas.data.db.WallpaperEntity
import com.sephuan.monetcanvas.data.model.WallpaperType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object FileUtils {

    private const val TAG = "FileUtils"

    private val SUPPORTED_IMAGE_FORMATS = setOf("jpg", "jpeg", "png", "webp", "bmp")
    private val SUPPORTED_VIDEO_FORMATS = setOf("mp4", "3gp", "webm", "mkv", "mov", "avi")

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  目录工具
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    fun getStaticDir(context: Context): File {
        return File(context.filesDir, "Static").apply { if (!exists()) mkdirs() }
    }

    fun getLiveDir(context: Context): File {
        return File(context.filesDir, "Live").apply { if (!exists()) mkdirs() }
    }

    fun getThumbDir(context: Context): File {
        return File(context.filesDir, ".thumbnails").apply { if (!exists()) mkdirs() }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  格式判断
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    fun isSupportedImageFormat(fileName: String): Boolean {
        return fileName.substringAfterLast(".", "").lowercase() in SUPPORTED_IMAGE_FORMATS
    }

    fun isSupportedVideoFormat(fileName: String): Boolean {
        return fileName.substringAfterLast(".", "").lowercase() in SUPPORTED_VIDEO_FORMATS
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  文件有效性检查
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    fun isFileValid(entity: WallpaperEntity): Boolean {
        val file = File(entity.filePath)
        return file.exists() && file.canRead() && file.length() > 0
    }

    fun isThumbValid(entity: WallpaperEntity): Boolean {
        if (entity.thumbnailPath.isBlank()) return false
        val file = File(entity.thumbnailPath)
        return file.exists() && file.canRead() && file.length() > 0
    }

    fun findInvalidEntities(entities: List<WallpaperEntity>): List<WallpaperEntity> {
        return entities.filter { !isFileValid(it) }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  核心导入（从 Uri → App 私有目录）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    suspend fun importWallpaperFromUri(
        context: Context,
        uri: Uri
    ): WallpaperEntity? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri).orEmpty()

            var fileName = "imported_${System.currentTimeMillis()}"
            resolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) fileName = c.getString(idx)
                }
            }

            val isVideo = mimeType.startsWith("video/") || isSupportedVideoFormat(fileName)
            val isImage = mimeType.startsWith("image/") || isSupportedImageFormat(fileName)
            if (!isVideo && !isImage) {
                Log.e(TAG, "不支持的文件格式: mime=$mimeType name=$fileName")
                return@withContext null
            }

            val type = if (isVideo) WallpaperType.LIVE else WallpaperType.STATIC
            val safeName = ensureExtension(fileName, isVideo)
            val targetDir = if (isVideo) getLiveDir(context) else getStaticDir(context)
            val targetFile = getUniqueFile(targetDir, safeName)

            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output -> input.copyTo(output) }
            } ?: run {
                Log.e(TAG, "无法打开输入流: $uri")
                return@withContext null
            }

            if (!targetFile.exists() || targetFile.length() <= 0) {
                Log.e(TAG, "拷贝后文件无效: ${targetFile.absolutePath}")
                return@withContext null
            }

            Log.d(TAG, "文件已安全拷贝到: ${targetFile.absolutePath} (${targetFile.length()} bytes)")

            var width = 0
            var height = 0
            var duration: Long? = null
            val thumbnailPath: String

            if (isVideo) {
                val meta = extractVideoMeta(targetFile.absolutePath)
                width = meta.width
                height = meta.height
                duration = meta.duration
                thumbnailPath = generateVideoThumbnail(context, targetFile) ?: targetFile.absolutePath
            } else {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(targetFile.absolutePath, opts)
                width = opts.outWidth
                height = opts.outHeight
                thumbnailPath = targetFile.absolutePath
            }

            WallpaperEntity(
                fileName = targetFile.name,
                filePath = targetFile.absolutePath,
                type = type,
                fileSize = targetFile.length(),
                width = width,
                height = height,
                duration = duration,
                thumbnailPath = thumbnailPath,
                addedTimestamp = System.currentTimeMillis()
            )

        } catch (e: Exception) {
            Log.e(TAG, "importWallpaperFromUri failed", e)
            null
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  从 SAF 文档 Uri 导入（同步功能用）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    fun importFromDocumentUri(
        context: Context,
        uri: Uri,
        displayName: String,
        type: WallpaperType
    ): WallpaperEntity? {
        return try {
            val resolver = context.contentResolver
            val isVideo = type == WallpaperType.LIVE

            val safeName = ensureExtension(displayName, isVideo)
            val targetDir = if (isVideo) getLiveDir(context) else getStaticDir(context)
            val targetFile = getUniqueFile(targetDir, safeName)

            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output -> input.copyTo(output) }
            } ?: return null

            if (!targetFile.exists() || targetFile.length() <= 0) return null

            var width = 0
            var height = 0
            var duration: Long? = null
            val thumbnailPath: String

            if (isVideo) {
                val meta = extractVideoMeta(targetFile.absolutePath)
                width = meta.width
                height = meta.height
                duration = meta.duration
                thumbnailPath = generateVideoThumbnail(context, targetFile) ?: targetFile.absolutePath
            } else {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(targetFile.absolutePath, opts)
                width = opts.outWidth
                height = opts.outHeight
                thumbnailPath = targetFile.absolutePath
            }

            WallpaperEntity(
                fileName = targetFile.name,
                filePath = targetFile.absolutePath,
                type = type,
                fileSize = targetFile.length(),
                width = width,
                height = height,
                duration = duration,
                thumbnailPath = thumbnailPath,
                addedTimestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "importFromDocumentUri failed", e)
            null
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  镜像备份到用户自定义目录（带去重）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    enum class MirrorResult {
        SUCCESS,    // 成功备份
        SKIPPED,    // 已存在，跳过
        FAILED      // 失败
    }

    /**
     * 将壁纸文件备份到自定义目录
     * ★ 备份前检查目标目录中是否已有同名同大小的文件，有则跳过
     */
    fun mirrorToCustomStorage(
        context: Context,
        treeUri: Uri,
        entity: WallpaperEntity
    ): MirrorResult {
        try {
            val resolver = context.contentResolver
            val sourceFile = File(entity.filePath)
            if (!sourceFile.exists()) return MirrorResult.FAILED

            val categoryDirName = if (entity.type == WallpaperType.LIVE) "Live" else "Static"
            val mimeType = if (entity.type == WallpaperType.LIVE) "video/*" else "image/*"

            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)

            val categoryDirUri = findOrCreateDirectory(context, rootDocUri, categoryDirName)
                ?: return MirrorResult.FAILED

            // ★ 检查目标目录中是否已存在同名同大小文件
            if (fileExistsInDirectory(context, categoryDirUri, entity.fileName, sourceFile.length())) {
                Log.d(TAG, "备份跳过（已存在）: $categoryDirName/${entity.fileName}")
                return MirrorResult.SKIPPED
            }

            val outDocUri = DocumentsContract.createDocument(
                resolver, categoryDirUri, mimeType, entity.fileName
            ) ?: return MirrorResult.FAILED

            sourceFile.inputStream().use { input ->
                resolver.openOutputStream(outDocUri, "w")?.use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "备份成功: $categoryDirName/${entity.fileName}")
            return MirrorResult.SUCCESS
        } catch (e: Exception) {
            Log.e(TAG, "mirrorToCustomStorage failed", e)
            return MirrorResult.FAILED
        }
    }

    /**
     * ★ 检查 SAF 目录中是否已存在同名同大小的文件
     */
    private fun fileExistsInDirectory(
        context: Context,
        dirUri: Uri,
        targetFileName: String,
        targetFileSize: Long
    ): Boolean {
        val resolver = context.contentResolver
        val dirDocId = DocumentsContract.getDocumentId(dirUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(dirUri, dirDocId)

        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null, null, null
        )?.use { cursor ->
            val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val name = if (nameCol >= 0) cursor.getString(nameCol) else continue
                val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else ""

                // 跳过目录
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue

                // 同名同大小 = 已存在
                if (name == targetFileName && size == targetFileSize) {
                    return true
                }
            }
        }
        return false
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  目录迁移（旧自定义目录 → 新自定义目录）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    data class MigrateResult(
        val moved: Int = 0,
        val failed: Int = 0
    )

    fun migrateCustomStorage(
        context: Context,
        oldTreeUri: Uri,
        newTreeUri: Uri
    ): MigrateResult {
        val resolver = context.contentResolver
        var moved = 0
        var failed = 0

        try {
            val oldRootId = DocumentsContract.getTreeDocumentId(oldTreeUri)
            val oldChildrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(oldTreeUri, oldRootId)

            resolver.query(
                oldChildrenUri,
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
                    if (name.lowercase() !in listOf("static", "live")) continue

                    val folderDocUri = DocumentsContract.buildDocumentUriUsingTree(oldTreeUri, docId)
                    val folderId = DocumentsContract.getDocumentId(folderDocUri)
                    val folderChildrenUri =
                        DocumentsContract.buildChildDocumentsUriUsingTree(oldTreeUri, folderId)

                    val newRootId = DocumentsContract.getTreeDocumentId(newTreeUri)
                    val newRootDocUri = DocumentsContract.buildDocumentUriUsingTree(newTreeUri, newRootId)
                    val newCategoryDir = findOrCreateDirectory(context, newRootDocUri, name) ?: continue

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
                            val fileName = if (fName >= 0) fileCursor.getString(fName) else continue
                            val fileMime = if (fMime >= 0) fileCursor.getString(fMime) else ""
                            val fileSize = if (fSize >= 0) fileCursor.getLong(fSize) else 0L

                            if (fileMime == DocumentsContract.Document.MIME_TYPE_DIR) continue

                            val fileUri = DocumentsContract.buildDocumentUriUsingTree(oldTreeUri, fileDocId)

                            try {
                                // 检查新目录中是否已存在
                                if (fileExistsInDirectory(context, newCategoryDir, fileName, fileSize)) {
                                    // 已存在则只删旧的
                                    DocumentsContract.deleteDocument(resolver, fileUri)
                                    moved++
                                    continue
                                }

                                val newFileUri = DocumentsContract.createDocument(
                                    resolver, newCategoryDir, fileMime, fileName
                                )
                                if (newFileUri != null) {
                                    resolver.openInputStream(fileUri)?.use { input ->
                                        resolver.openOutputStream(newFileUri, "w")?.use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    DocumentsContract.deleteDocument(resolver, fileUri)
                                    moved++
                                } else {
                                    failed++
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "迁移文件失败: $fileName", e)
                                failed++
                            }
                        }
                    }

                    runCatching { DocumentsContract.deleteDocument(resolver, folderDocUri) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "migrateCustomStorage failed", e)
        }

        return MigrateResult(moved, failed)
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  删除壁纸文件
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    fun deleteWallpaperFiles(entity: WallpaperEntity) {
        runCatching {
            val file = File(entity.filePath)
            if (file.exists()) file.delete()
        }
        runCatching {
            if (entity.thumbnailPath != entity.filePath) {
                val thumb = File(entity.thumbnailPath)
                if (thumb.exists()) thumb.delete()
            }
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  内部工具
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private fun ensureExtension(fileName: String, isVideo: Boolean): String {
        return if (fileName.contains(".")) fileName
        else fileName + if (isVideo) ".mp4" else ".jpg"
    }

    private fun getUniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        if (!file.exists()) return file

        val baseName = name.substringBeforeLast(".")
        val ext = name.substringAfterLast(".", "")
        var counter = 1
        while (file.exists()) {
            val newName = if (ext.isNotBlank()) "${baseName}_$counter.$ext" else "${baseName}_$counter"
            file = File(dir, newName)
            counter++
        }
        return file
    }

    private data class VideoMeta(val width: Int, val height: Int, val duration: Long?)

    private fun extractVideoMeta(path: String): VideoMeta {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            var w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0

            if (rotation == 90 || rotation == 270) {
                val tmp = w; w = h; h = tmp
            }

            VideoMeta(w, h, d)
        } catch (e: Exception) {
            Log.e(TAG, "extractVideoMeta failed", e)
            VideoMeta(0, 0, null)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun generateVideoThumbnail(context: Context, videoFile: File): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null

            val thumbFile = File(getThumbDir(context), "${videoFile.nameWithoutExtension}_thumb.jpg")
            FileOutputStream(thumbFile).use { fos ->
                frame.compress(Bitmap.CompressFormat.JPEG, 85, fos)
            }
            frame.recycle()
            thumbFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "generateVideoThumbnail failed", e)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun findOrCreateDirectory(
        context: Context,
        parentDocUri: Uri,
        dirName: String
    ): Uri? {
        val resolver = context.contentResolver
        val parentDocId = DocumentsContract.getDocumentId(parentDocUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentDocUri, parentDocId)

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
                val name = if (nameCol >= 0) cursor.getString(nameCol) else null
                val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else null
                val docId = if (idCol >= 0) cursor.getString(idCol) else null

                if (name == dirName &&
                    mime == DocumentsContract.Document.MIME_TYPE_DIR &&
                    !docId.isNullOrBlank()
                ) {
                    return DocumentsContract.buildDocumentUriUsingTree(parentDocUri, docId)
                }
            }
        }

        return DocumentsContract.createDocument(
            resolver, parentDocUri, DocumentsContract.Document.MIME_TYPE_DIR, dirName
        )
    }
}
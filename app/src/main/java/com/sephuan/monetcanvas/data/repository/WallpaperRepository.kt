package com.sephuan.monetcanvas.data.repository

import com.sephuan.monetcanvas.data.db.WallpaperDao
import com.sephuan.monetcanvas.data.db.WallpaperEntity
import com.sephuan.monetcanvas.data.model.FilterType
import com.sephuan.monetcanvas.data.model.WallpaperType
import com.sephuan.monetcanvas.util.FileUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class WallpaperRepository(
    private val dao: WallpaperDao
) {

    fun getWallpapersByFilter(filter: FilterType): Flow<List<WallpaperEntity>> {
        return when (filter) {
            FilterType.ALL -> dao.getAll()
            FilterType.STATIC -> dao.getByType(WallpaperType.STATIC)
            FilterType.LIVE -> dao.getByType(WallpaperType.LIVE)
            FilterType.FAVORITES -> dao.getFavorites()
        }
    }

    fun getAllWallpapers(): Flow<List<WallpaperEntity>> = dao.getAll()

    fun getStaticWallpapers(): Flow<List<WallpaperEntity>> = dao.getByType(WallpaperType.STATIC)

    fun getLiveWallpapers(): Flow<List<WallpaperEntity>> = dao.getByType(WallpaperType.LIVE)

    fun getFavoriteWallpapers(): Flow<List<WallpaperEntity>> = dao.getFavorites()

    suspend fun getById(id: Long): WallpaperEntity? = dao.getById(id)

    suspend fun insert(item: WallpaperEntity): Long = dao.insert(item)

    suspend fun insertAll(items: List<WallpaperEntity>): List<Long> = dao.insertAll(items)

    suspend fun update(item: WallpaperEntity) = dao.update(item)

    suspend fun delete(item: WallpaperEntity) = dao.delete(item)

    suspend fun clearAll() = dao.clearAll()

    suspend fun toggleFavorite(id: Long) {
        val item = dao.getById(id) ?: return
        dao.update(item.copy(isFavorite = !item.isFavorite))
    }

    suspend fun markAsUsed(id: Long, timestamp: Long = System.currentTimeMillis()) {
        val item = dao.getById(id) ?: return
        dao.update(item.copy(lastUsedTimestamp = timestamp))
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  ★ 新增：清理无效壁纸记录
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    data class CleanupResult(
        val removed: Int = 0,
        val removedNames: List<String> = emptyList()
    )

    /**
     * 扫描数据库中所有壁纸记录，检查文件是否真实存在。
     * 如果文件已被删除/损坏，则从数据库中移除该记录。
     * 同时清理对应的缩略图文件。
     *
     * @return 清理结果（删除了几条、都叫什么名字）
     */
    suspend fun cleanupInvalidEntries(): CleanupResult {
        val all = dao.getAll().first()
        val invalidList = FileUtils.findInvalidEntities(all)

        if (invalidList.isEmpty()) {
            return CleanupResult(removed = 0)
        }

        val removedNames = mutableListOf<String>()

        for (entity in invalidList) {
            // 删除数据库记录
            dao.delete(entity)

            // 清理可能残留的缩略图
            FileUtils.deleteWallpaperFiles(entity)

            removedNames.add(entity.fileName)
        }

        return CleanupResult(
            removed = removedNames.size,
            removedNames = removedNames
        )
    }

    /**
     * 去重检查：按文件名 + 文件大小 + 类型判断是否已存在
     *
     * @return true = 已存在（重复），false = 不存在（可导入）
     */
    suspend fun isDuplicate(fileName: String, fileSize: Long, type: WallpaperType): Boolean {
        val all = dao.getAll().first()
        val key = dedupKey(fileName, fileSize, type)
        return all.any { dedupKey(it.fileName, it.fileSize, it.type) == key }
    }

    /**
     * 删除壁纸（数据库记录 + 文件）
     */
    suspend fun deleteWithFiles(entity: WallpaperEntity) {
        dao.delete(entity)
        FileUtils.deleteWallpaperFiles(entity)
    }

    private fun dedupKey(name: String, size: Long, type: WallpaperType): String {
        return "${type.name}|${name.lowercase()}|$size"
    }
}
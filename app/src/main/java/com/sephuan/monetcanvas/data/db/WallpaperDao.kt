package com.sephuan.monetcanvas.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import com.sephuan.monetcanvas.data.model.WallpaperType

@Dao
interface WallpaperDao {

    // 全部
    @Query("SELECT * FROM wallpapers ORDER BY addedTimestamp DESC")
    fun getAll(): Flow<List<WallpaperEntity>>

    // 静态
    @Query("SELECT * FROM wallpapers WHERE type = :type ORDER BY addedTimestamp DESC")
    fun getByType(type: WallpaperType): Flow<List<WallpaperEntity>>

    // 收藏
    @Query("SELECT * FROM wallpapers WHERE isFavorite = 1 ORDER BY addedTimestamp DESC")
    fun getFavorites(): Flow<List<WallpaperEntity>>

    // 按 ID
    @Query("SELECT * FROM wallpapers WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): WallpaperEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: WallpaperEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<WallpaperEntity>): List<Long>

    @Update
    suspend fun update(item: WallpaperEntity)

    @Delete
    suspend fun delete(item: WallpaperEntity)

    @Query("DELETE FROM wallpapers")
    suspend fun clearAll()
}
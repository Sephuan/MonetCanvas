package com.sephuan.monetcanvas.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sephuan.monetcanvas.data.model.WallpaperType

@Entity(tableName = "wallpapers")
data class WallpaperEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val filePath: String,
    val type: WallpaperType,
    val fileSize: Long,
    val width: Int,
    val height: Int,
    val duration: Long? = null,         // 动态壁纸时长
    val thumbnailPath: String,          // 缩略图路径
    val addedTimestamp: Long,           // 导入时间
    val lastUsedTimestamp: Long? = null,
    val isFavorite: Boolean = false,

    // 我们将在这里存储针对单个壁纸自定义的取色规则
    // 注意：Room存储复杂对象需要转换器，我们下一步会做
    val framePosition: String = "FIRST",
    val colorRegion: String = "FULL_FRAME",
    val tonePreference: String = "AUTO",
    val manualColor: Int? = null
)
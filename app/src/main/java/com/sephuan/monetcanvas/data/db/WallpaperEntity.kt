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
    val duration: Long? = null,
    val thumbnailPath: String,
    val addedTimestamp: Long,
    val lastUsedTimestamp: Long? = null,
    val isFavorite: Boolean = false,

    // Monet 取色规则
    val framePosition: String = "FIRST",
    val colorRegion: String = "FULL_FRAME",
    val tonePreference: String = "AUTO",
    val manualColor: Int? = null,

    // ━━━━━ 新增：图片调整参数 ━━━━━
    val fillMode: String = "COVER",
    val mirrorHorizontal: Boolean = false,
    val mirrorVertical: Boolean = false,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val bgColorArgb: Int = 0xFF000000.toInt(),  // 背景色 ARGB
    val adjustOffsetX: Float = 0f,
    val adjustOffsetY: Float = 0f,
    val adjustScale: Float = 1f
)
package com.sephuan.monetcanvas.data.db

import androidx.room.TypeConverter
import com.sephuan.monetcanvas.data.model.ColorRegion
import com.sephuan.monetcanvas.data.model.FramePickPosition
import com.sephuan.monetcanvas.data.model.TonePreference

/**
 * Room 无法直接存储枚举，这里把枚举转换为 String 存储。
 */
class Converters {

    @TypeConverter
    fun fromFramePickPosition(value: FramePickPosition?): String? = value?.name

    @TypeConverter
    fun toFramePickPosition(value: String?): FramePickPosition? =
        value?.let { enumValueOf<FramePickPosition>(it) }

    @TypeConverter
    fun fromColorRegion(value: ColorRegion?): String? = value?.name

    @TypeConverter
    fun toColorRegion(value: String?): ColorRegion? =
        value?.let { enumValueOf<ColorRegion>(it) }

    @TypeConverter
    fun fromTonePreference(value: TonePreference?): String? = value?.name

    @TypeConverter
    fun toTonePreference(value: String?): TonePreference? =
        value?.let { enumValueOf<TonePreference>(it) }
}
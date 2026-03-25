package com.sephuan.monetcanvas.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [WallpaperEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun wallpaperDao(): WallpaperDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 新增图片调整参数字段
                db.execSQL("ALTER TABLE wallpapers ADD COLUMN fillMode TEXT NOT NULL DEFAULT 'COVER'")
                db.execSQL("ALTER TABLE wallpapers ADD COLUMN mirrorHorizontal INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE wallpapers ADD COLUMN mirrorVertical INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE wallpapers ADD COLUMN brightness REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE wallpapers ADD COLUMN contrast REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE wallpapers ADD COLUMN saturation REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE wallpapers ADD COLUMN bgColorArgb INTEGER NOT NULL DEFAULT ${0xFF000000.toInt()}")
                db.execSQL("ALTER TABLE wallpapers ADD COLUMN adjustOffsetX REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE wallpapers ADD COLUMN adjustOffsetY REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE wallpapers ADD COLUMN adjustScale REAL NOT NULL DEFAULT 1.0")
            }
        }
    }
}
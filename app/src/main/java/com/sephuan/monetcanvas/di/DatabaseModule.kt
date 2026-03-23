package com.sephuan.monetcanvas.di

import android.content.Context
import androidx.room.Room
import com.sephuan.monetcanvas.data.datastore.SettingsDataStore
import com.sephuan.monetcanvas.data.db.AppDatabase
import com.sephuan.monetcanvas.data.db.WallpaperDao
import com.sephuan.monetcanvas.data.repository.WallpaperRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "monet_canvas.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideWallpaperDao(
        db: AppDatabase
    ): WallpaperDao = db.wallpaperDao()

    @Provides
    @Singleton
    fun provideWallpaperRepository(
        dao: WallpaperDao
    ): WallpaperRepository = WallpaperRepository(dao)

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context
    ): SettingsDataStore = SettingsDataStore(context)
}
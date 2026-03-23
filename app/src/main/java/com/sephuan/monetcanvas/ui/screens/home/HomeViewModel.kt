package com.sephuan.monetcanvas.ui.screens.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sephuan.monetcanvas.data.datastore.SettingsDataStore
import com.sephuan.monetcanvas.data.db.WallpaperEntity
import com.sephuan.monetcanvas.data.model.FilterType
import com.sephuan.monetcanvas.data.model.GridSize
import com.sephuan.monetcanvas.data.model.WallpaperType
import com.sephuan.monetcanvas.data.repository.WallpaperRepository
import com.sephuan.monetcanvas.util.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: WallpaperRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    // ━━━━━ 左页：壁纸库筛选 ━━━━━

    val currentFilter = settingsDataStore.filterTypeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FilterType.ALL
    )

    val gridSize = settingsDataStore.gridSizeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GridSize.MEDIUM
    )

    val filteredWallpapers = currentFilter
        .flatMapLatest { filter ->
            // 左页只处理 ALL / STATIC / LIVE，FAVORITES 由右页单独管理
            when (filter) {
                FilterType.FAVORITES -> repository.getAllWallpapers()
                else -> repository.getWallpapersByFilter(filter)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val staticCount = repository.getStaticWallpapers()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val liveCount = repository.getLiveWallpapers()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ━━━━━ 右页：收藏列表（独立 Flow）━━━━━

    val favoriteWallpapers = repository.getFavoriteWallpapers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val favoritesCount = repository.getFavoriteWallpapers()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ━━━━━ 操作 ━━━━━

    fun setFilter(filter: FilterType) {
        viewModelScope.launch {
            settingsDataStore.saveFilterType(filter)
        }
    }

    fun setGridSize(size: GridSize) {
        viewModelScope.launch {
            settingsDataStore.saveGridSize(size)
        }
    }

    fun toggleFavorite(item: WallpaperEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(item.id)
        }
    }

    fun importWallpaper(context: Context, uri: Uri) {
        viewModelScope.launch {
            val entity = FileUtils.importWallpaperFromUri(context, uri)
            if (entity != null) {
                repository.insert(entity)
            }
        }
    }

    fun seedDemoDataIfEmpty() {
        viewModelScope.launch {
            val all = repository.getAllWallpapers().first()
            if (all.isNotEmpty()) return@launch

            val now = System.currentTimeMillis()
            repository.insertAll(
                listOf(
                    WallpaperEntity(
                        fileName = "demo_static_1.jpg",
                        filePath = "/sdcard/MonetCanvas/Static/demo_static_1.jpg",
                        type = WallpaperType.STATIC,
                        fileSize = 1_250_000L,
                        width = 1080,
                        height = 2400,
                        duration = null,
                        thumbnailPath = "/sdcard/MonetCanvas/.thumbnails/demo_static_1.webp",
                        addedTimestamp = now
                    ),
                    WallpaperEntity(
                        fileName = "demo_live_1.mp4",
                        filePath = "/sdcard/MonetCanvas/Live/demo_live_1.mp4",
                        type = WallpaperType.LIVE,
                        fileSize = 5_800_000L,
                        width = 1080,
                        height = 2400,
                        duration = 12_000L,
                        thumbnailPath = "/sdcard/MonetCanvas/.thumbnails/demo_live_1.webp",
                        addedTimestamp = now - 10_000L
                    )
                )
            )
        }
    }
}
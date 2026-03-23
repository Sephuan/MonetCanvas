package com.sephuan.monetcanvas.ui.navigation

import androidx.lifecycle.ViewModel
import com.sephuan.monetcanvas.data.db.WallpaperEntity
import com.sephuan.monetcanvas.data.repository.WallpaperRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NavPreviewViewModel @Inject constructor(
    private val repository: WallpaperRepository
) : ViewModel() {

    suspend fun loadWallpaper(id: Long): WallpaperEntity? {
        return repository.getById(id)
    }
}
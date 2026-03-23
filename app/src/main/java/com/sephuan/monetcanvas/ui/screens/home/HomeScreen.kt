package com.sephuan.monetcanvas.ui.screens.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.sephuan.monetcanvas.R
import com.sephuan.monetcanvas.data.db.WallpaperEntity
import com.sephuan.monetcanvas.data.model.FilterType
import com.sephuan.monetcanvas.data.model.GridSize
import com.sephuan.monetcanvas.data.model.WallpaperType
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onWallpaperClick: (WallpaperEntity) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val wallpapers by viewModel.filteredWallpapers.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteWallpapers.collectAsStateWithLifecycle()
    val currentFilter by viewModel.currentFilter.collectAsStateWithLifecycle()
    val currentGridSize by viewModel.gridSize.collectAsStateWithLifecycle()

    val staticCount by viewModel.staticCount.collectAsStateWithLifecycle()
    val liveCount by viewModel.liveCount.collectAsStateWithLifecycle()
    val favoritesCount by viewModel.favoritesCount.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.seedDemoDataIfEmpty() }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) viewModel.importWallpaper(context, uri)
    }

    // ━━━━━ Pager 状态：0=壁纸库, 1=收藏 ━━━━━
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    GridSizeMenu(current = currentGridSize, onChange = viewModel::setGridSize)
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { pickerLauncher.launch("*/*") }) {
                Icon(Icons.Filled.Add, stringResource(R.string.import_wallpaper))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ━━━━━ TabRow：壁纸库 | 收藏 ━━━━━
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    if (pagerState.currentPage < tabPositions.size) {
                        TabRowDefaults.PrimaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            width = 48.dp,
                            shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                        )
                    }
                }
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Collections, null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.tab_library))
                        }
                    }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (pagerState.currentPage == 1) Icons.Filled.Favorite
                                else Icons.Outlined.FavoriteBorder,
                                null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("${stringResource(R.string.tab_favorites)}($favoritesCount)")
                        }
                    }
                )
            }

            // ━━━━━ HorizontalPager：丝滑左右滑动 ━━━━━
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1  // 预加载相邻页，让滑动更丝滑
            ) { page ->
                when (page) {
                    0 -> LibraryPage(
                        wallpapers = wallpapers,
                        currentFilter = currentFilter,
                        gridSize = currentGridSize,
                        staticCount = staticCount,
                        liveCount = liveCount,
                        onFilterChange = viewModel::setFilter,
                        onWallpaperClick = onWallpaperClick,
                        onLongClick = viewModel::toggleFavorite
                    )
                    1 -> FavoritesPage(
                        favorites = favorites,
                        gridSize = currentGridSize,
                        onWallpaperClick = onWallpaperClick,
                        onLongClick = viewModel::toggleFavorite
                    )
                }
            }
        }
    }
}

// ━━━━━ 左页：壁纸库 ━━━━━
@Composable
private fun LibraryPage(
    wallpapers: List<WallpaperEntity>,
    currentFilter: FilterType,
    gridSize: GridSize,
    staticCount: Int,
    liveCount: Int,
    onFilterChange: (FilterType) -> Unit,
    onWallpaperClick: (WallpaperEntity) -> Unit,
    onLongClick: (WallpaperEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 筛选栏：All / Static / Live
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = currentFilter == FilterType.ALL,
                onClick = { onFilterChange(FilterType.ALL) },
                label = { Text("${stringResource(R.string.filter_all)}(${staticCount + liveCount})") }
            )
            FilterChip(
                selected = currentFilter == FilterType.STATIC,
                onClick = { onFilterChange(FilterType.STATIC) },
                label = { Text("${stringResource(R.string.filter_static)}($staticCount)") }
            )
            FilterChip(
                selected = currentFilter == FilterType.LIVE,
                onClick = { onFilterChange(FilterType.LIVE) },
                label = { Text("${stringResource(R.string.filter_live)}($liveCount)") }
            )
        }

        if (wallpapers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.empty_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            WallpaperGrid(
                items = wallpapers,
                gridSize = gridSize,
                onClick = onWallpaperClick,
                onLongClick = onLongClick
            )
        }
    }
}

// ━━━━━ 右页：收藏 ━━━━━
@Composable
private fun FavoritesPage(
    favorites: List<WallpaperEntity>,
    gridSize: GridSize,
    onWallpaperClick: (WallpaperEntity) -> Unit,
    onLongClick: (WallpaperEntity) -> Unit
) {
    if (favorites.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    stringResource(R.string.favorites_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    } else {
        WallpaperGrid(
            items = favorites,
            gridSize = gridSize,
            onClick = onWallpaperClick,
            onLongClick = onLongClick
        )
    }
}

// ━━━━━ 通用壁纸网格 ━━━━━
@Composable
private fun WallpaperGrid(
    items: List<WallpaperEntity>,
    gridSize: GridSize,
    onClick: (WallpaperEntity) -> Unit,
    onLongClick: (WallpaperEntity) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(gridSize.columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(items, key = { it.id }) { item ->
            WallpaperCard(
                item = item,
                gridSize = gridSize,
                onClick = { onClick(item) },
                onLongClick = { onLongClick(item) }
            )
        }
    }
}

// ━━━━━ 壁纸卡片 ━━━━━
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WallpaperCard(
    item: WallpaperEntity,
    gridSize: GridSize,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val thumbnailExists = remember(item.thumbnailPath) {
        item.thumbnailPath.isNotBlank() && File(item.thumbnailPath).exists()
    }

    val previewModel = when {
        thumbnailExists -> item.thumbnailPath
        item.type == WallpaperType.LIVE -> ImageRequest.Builder(context)
            .data(item.filePath)
            .videoFrameMillis(0)
            .crossfade(true)
            .build()
        else -> item.filePath
    }

    val aspectRatio = when (gridSize) {
        GridSize.SMALL -> 0.6f
        GridSize.MEDIUM -> 0.6f
        GridSize.LARGE -> 0.56f
        GridSize.LIST -> 1.8f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
    ) {
        AsyncImage(
            model = previewModel,
            contentDescription = item.fileName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // 左上角：动态壁纸标签
        if (item.type == WallpaperType.LIVE) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color.White
                    )
                    if (gridSize != GridSize.SMALL) {
                        Spacer(Modifier.width(3.dp))
                        Text(
                            stringResource(R.string.live_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // 右上角：收藏标记
        if (item.isFavorite) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = stringResource(R.string.favorited_label),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(18.dp),
                tint = Color(0xFFFF4081)
            )
        }

        // 底部：文件名
        if (gridSize != GridSize.SMALL) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Column {
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (gridSize == GridSize.LARGE || gridSize == GridSize.LIST) {
                        Text(
                            text = "${item.width} × ${item.height}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

// ━━━━━ 网格大小菜单 ━━━━━
@Composable
private fun GridSizeMenu(current: GridSize, onChange: (GridSize) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = when (current) {
                GridSize.SMALL -> Icons.Outlined.GridView
                GridSize.MEDIUM -> Icons.Outlined.Apps
                GridSize.LARGE -> Icons.Outlined.ViewAgenda
                GridSize.LIST -> Icons.Outlined.ViewList
            },
            contentDescription = stringResource(R.string.grid_size_desc)
        )
    }

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        GridSize.entries.forEach { size ->
            val label = when (size) {
                GridSize.SMALL -> stringResource(R.string.grid_small_label)
                GridSize.MEDIUM -> stringResource(R.string.grid_medium_label)
                GridSize.LARGE -> stringResource(R.string.grid_large_label)
                GridSize.LIST -> stringResource(R.string.grid_list_label)
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.grid_columns, label, size.columns)) },
                onClick = {
                    onChange(size)
                    expanded = false
                }
            )
        }
    }
}
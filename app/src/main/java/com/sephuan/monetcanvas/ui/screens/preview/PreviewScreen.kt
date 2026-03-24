package com.sephuan.monetcanvas.ui.screens.preview

import android.net.Uri
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.sephuan.monetcanvas.R
import com.sephuan.monetcanvas.data.db.WallpaperEntity
import com.sephuan.monetcanvas.data.model.ImageAdjustment
import com.sephuan.monetcanvas.data.model.MonetRule
import com.sephuan.monetcanvas.data.model.WallpaperType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private sealed interface PreviewBlock {
    data object Banner : PreviewBlock
    data object MediaPager : PreviewBlock
    data object Monet : PreviewBlock
    data object Actions : PreviewBlock
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    wallpaper: WallpaperEntity,
    onBack: () -> Unit,
    onFullScreenClick: () -> Unit,
    viewModel: PreviewViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var showApplyDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showRuleSheet by rememberSaveable { mutableStateOf(false) }

    val applyState by viewModel.applyState.collectAsStateWithLifecycle()
    val showReturnBanner by viewModel.showBanner.collectAsStateWithLifecycle()
    val bannerSuccess by viewModel.bannerSuccess.collectAsStateWithLifecycle()
    val extractedColors by viewModel.extractedColors.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val liveWpResult by viewModel.liveWpResult.collectAsStateWithLifecycle()

    val isApplying = applyState == ApplyState.APPLYING
    val isWaitingConfirm = applyState == ApplyState.WAITING_CONFIRM

    var currentRule by remember { mutableStateOf<MonetRule?>(null) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var isExiting by remember { mutableStateOf(false) }

    // ━━━━━ 图片调整参数（仅静态壁纸使用）━━━━━
    var imageAdjustment by remember { mutableStateOf(ImageAdjustment.DEFAULT) }

    // ━━━━━ Predictive Back ━━━━━
    var backProgress by remember { mutableFloatStateOf(0f) }

    val animatedProgress by animateFloatAsState(
        targetValue = backProgress,
        animationSpec = tween(
            durationMillis = if (backProgress == 0f) 200 else 0,
            easing = FastOutSlowInEasing
        ),
        label = "previewBackProgress"
    )

    val scale = 1f - (animatedProgress * 0.06f)
    val contentAlpha = 1f - (animatedProgress * 0.10f)
    val translateX = animatedProgress * 80f
    val cornerRadius = (animatedProgress * 24f).dp

    val currentApplyState by rememberUpdatedState(applyState)
    val currentRuleState by rememberUpdatedState(currentRule)

    val blocks = remember {
        mutableStateListOf<PreviewBlock>(
            PreviewBlock.Banner,
            PreviewBlock.MediaPager,
            PreviewBlock.Monet,
            PreviewBlock.Actions
        )
    }

    // ━━━━━ Predictive Back Handler ━━━━━
    PredictiveBackHandler(enabled = !isApplying && !isWaitingConfirm) { progress ->
        try {
            progress.collectLatest { event ->
                backProgress = event.progress
            }
            backProgress = 1f
            player?.clearVideoSurface()
            player?.pause()
            player?.stop()
            player?.release()
            player = null
            isExiting = true
        } catch (_: Throwable) {
            backProgress = 0f
        }
    }

    // ━━━━━ 初始化播放器 ━━━━━
    LaunchedEffect(wallpaper.filePath, wallpaper.type) {
        if (wallpaper.type == WallpaperType.LIVE) {
            player?.release()
            player = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.parse("file://${wallpaper.filePath}")))
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f
                prepare()
                playWhenReady = true
            }
        }
    }

    // ━━━━━ 页面销毁时释放 ━━━━━
    DisposableEffect(Unit) {
        onDispose {
            player?.pause()
            player?.stop()
            player?.release()
            player = null
        }
    }

    // ━━━━━ 生命周期 ━━━━━
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player?.pause()
                Lifecycle.Event.ON_STOP -> player?.pause()
                Lifecycle.Event.ON_RESUME -> {
                    player?.play()
                    if (currentApplyState == ApplyState.WAITING_CONFIRM) {
                        viewModel.onReturnFromSystemPage(
                            context = context,
                            wallpaper = wallpaper,
                            rule = currentRuleState ?: MonetRule()
                        )
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ━━━━━ 初始化规则与颜色 ━━━━━
    LaunchedEffect(wallpaper.id) {
        val rule = viewModel.loadRuleForWallpaper(wallpaper)
        currentRule = rule
        viewModel.analyzeColors(wallpaper, rule)
    }

    // ━━━━━ 延迟退出 ━━━━━
    LaunchedEffect(isExiting) {
        if (isExiting) {
            delay(10)
            onBack()
        }
    }

    // ━━━━━ 安全返回 ━━━━━
    fun safeBack() {
        player?.clearVideoSurface()
        player?.pause()
        player?.stop()
        player?.release()
        player = null
        isExiting = true
    }

    fun openFullScreen() {
        player?.pause()
        onFullScreenClick()
    }

    fun handleApply(target: Int) {
        if (isApplying || isWaitingConfirm) return
        val rule = currentRule ?: MonetRule()
        player?.pause()
        viewModel.applyWallpaper(
            context = context,
            wallpaper = wallpaper,
            target = target,
            rule = rule
        )
    }

    val applyButtonAlpha by animateFloatAsState(
        targetValue = if (isApplying || isWaitingConfirm) 0.6f else 1f,
        animationSpec = tween(220),
        label = "applyAlpha"
    )

    val isStatic = wallpaper.type == WallpaperType.STATIC

    // ━━━━━ 页面主体 ━━━━━
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = contentAlpha
                    translationX = translateX
                }
                .clip(RoundedCornerShape(cornerRadius))
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = wallpaper.fileName,
                                maxLines = 1,
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = ::safeBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = stringResource(R.string.back)
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.delete)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(padding)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = 32.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(
                            items = blocks,
                            key = { block -> block::class.simpleName ?: block.hashCode() }
                        ) { block ->
                            when (block) {
                                PreviewBlock.Banner -> {
                                    ReturnBannerSection(
                                        visible = showReturnBanner,
                                        analyzing = isAnalyzing,
                                        success = bannerSuccess
                                    )
                                }

                                PreviewBlock.MediaPager -> {
                                    if (isStatic) {
                                        StaticMediaPager(
                                            wallpaper = wallpaper,
                                            player = player,
                                            isApplying = isApplying,
                                            adjustment = imageAdjustment,
                                            onAdjustmentChange = { imageAdjustment = it },
                                            onFullScreenClick = ::openFullScreen
                                        )
                                    } else {
                                        PreviewMediaSection(
                                            wallpaper = wallpaper,
                                            player = player,
                                            isApplying = isApplying,
                                            onFullScreenClick = ::openFullScreen
                                        )
                                    }
                                }

                                PreviewBlock.Monet -> {
                                    PreviewMonetSection(
                                        wallpaper = wallpaper,
                                        currentRule = currentRule,
                                        extractedColors = extractedColors,
                                        isAnalyzing = isAnalyzing,
                                        onConfigClick = { showRuleSheet = true }
                                    )
                                }

                                PreviewBlock.Actions -> {
                                    PreviewActionSection(
                                        applyButtonAlpha = applyButtonAlpha,
                                        isApplying = isApplying,
                                        isWaitingConfirm = isWaitingConfirm,
                                        onFullScreenClick = ::openFullScreen,
                                        onApplyClick = { showApplyDialog = true }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ━━━━━ 弹窗区域 ━━━━━

    if (showApplyDialog) {
        ApplyWallpaperDialog(
            isLive = wallpaper.type == WallpaperType.LIVE,
            onDismiss = { showApplyDialog = false },
            onApplyHome = { showApplyDialog = false; handleApply(1) },
            onApplyLock = { showApplyDialog = false; handleApply(2) },
            onApplyBoth = { showApplyDialog = false; handleApply(3) },
            onApplyLive = { showApplyDialog = false; handleApply(0) }
        )
    }

    if (showDeleteDialog) {
        DeleteWallpaperDialog(
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                player?.clearVideoSurface()
                player?.pause()
                player?.stop()
                player?.release()
                player = null
                viewModel.deleteWallpaper(context, wallpaper) { onBack() }
            }
        )
    }

    if (showRuleSheet && currentRule != null) {
        MonetRuleBottomSheet(
            rule = currentRule!!,
            isLiveWallpaper = wallpaper.type == WallpaperType.LIVE,
            onDismiss = { showRuleSheet = false },
            onSave = { newRule ->
                currentRule = newRule
                showRuleSheet = false
                scope.launch {
                    viewModel.saveRuleForWallpaper(wallpaper, newRule)
                    viewModel.analyzeColors(wallpaper, newRule)
                }
            }
        )
    }

    if (liveWpResult == LiveWpResult.FAILED) {
        LiveWallpaperFailedDialog(
            onDismiss = { viewModel.clearLiveWpResult() },
            onGoSettings = {
                viewModel.clearLiveWpResult()
                com.sephuan.monetcanvas.util.LiveWallpaperSetter.openAppSettings(context)
            },
            onRetry = {
                viewModel.clearLiveWpResult()
                viewModel.resetApplyState()
                com.sephuan.monetcanvas.util.LiveWallpaperSetter.tryActivate(context)
            }
        )
    }
}

// ━━━━━ 静态壁纸：预览 + 编辑 左右滑动 ━━━━━

@Composable
private fun StaticMediaPager(
    wallpaper: WallpaperEntity,
    player: ExoPlayer?,
    isApplying: Boolean,
    adjustment: ImageAdjustment,
    onAdjustmentChange: (ImageAdjustment) -> Unit,
    onFullScreenClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })

    Column(modifier = Modifier.fillMaxWidth()) {
        // ━━━━━ 标签栏 ━━━━━
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            indicator = { tabPositions ->
                if (pagerState.currentPage < tabPositions.size) {
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                        width = 40.dp,
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
                            Icons.Outlined.Image,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("预览")
                    }
                }
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("调整")
                    }
                }
            )
        }

        // ━━━━━ 左右滑动页面 ━━━━━
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            beyondViewportPageCount = 1,
            userScrollEnabled = true
        ) { page ->
            when (page) {
                0 -> {
                    // 预览页：带调整参数渲染
                    PreviewMediaSection(
                        wallpaper = wallpaper,
                        player = player,
                        isApplying = isApplying,
                        onFullScreenClick = onFullScreenClick,
                        adjustment = adjustment,
                        onAdjustmentChange = onAdjustmentChange
                    )
                }

                1 -> {
                    // 编辑页：滑块和选项
                    PreviewImageEditSection(
                        adjustment = adjustment,
                        onAdjustmentChange = onAdjustmentChange
                    )
                }
            }
        }
    }
}
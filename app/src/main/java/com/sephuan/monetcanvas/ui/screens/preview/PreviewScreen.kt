package com.sephuan.monetcanvas.ui.screens.preview

import android.net.Uri
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.alpha
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
import com.sephuan.monetcanvas.data.model.ColorRegion
import com.sephuan.monetcanvas.data.model.FramePickPosition
import com.sephuan.monetcanvas.data.model.MonetRule
import com.sephuan.monetcanvas.data.model.TonePreference
import com.sephuan.monetcanvas.data.model.WallpaperType
import com.sephuan.monetcanvas.util.LiveWallpaperSetter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private sealed interface PreviewBlock {
    data object Banner : PreviewBlock
    data object Media : PreviewBlock
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
            PreviewBlock.Media,
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
            // 手势提交
            backProgress = 1f
            player?.clearVideoSurface()
            player?.pause()
            player?.stop()
            player?.release()
            player = null
            isExiting = true
        } catch (_: Throwable) {
            // 手势取消
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
            delay(60)
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

                                PreviewBlock.Media -> {
                                    PreviewMediaSection(
                                        wallpaper = wallpaper,
                                        player = player,
                                        isApplying = isApplying,
                                        onFullScreenClick = ::openFullScreen
                                    )
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

    // ━━━━━ 设为壁纸弹窗 ━━━━━
    if (showApplyDialog) {
        ApplyWallpaperDialog(
            isLive = wallpaper.type == WallpaperType.LIVE,
            onDismiss = { showApplyDialog = false },
            onApplyHome = {
                showApplyDialog = false
                handleApply(1)
            },
            onApplyLock = {
                showApplyDialog = false
                handleApply(2)
            },
            onApplyBoth = {
                showApplyDialog = false
                handleApply(3)
            },
            onApplyLive = {
                showApplyDialog = false
                handleApply(0)
            }
        )
    }

    // ━━━━━ 删除弹窗 ━━━━━
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_wallpaper)) },
            text = { Text(stringResource(R.string.delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        player?.clearVideoSurface()
                        player?.pause()
                        player?.stop()
                        player?.release()
                        player = null
                        viewModel.deleteWallpaper(context, wallpaper) { onBack() }
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ━━━━━ 规则弹窗 ━━━━━
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

    // ━━━━━ 动态壁纸失败弹窗 ━━━━━
    if (liveWpResult == LiveWpResult.FAILED) {
        AlertDialog(
            onDismissRequest = { viewModel.clearLiveWpResult() },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.live_wp_perm_title)) },
            text = { Text(stringResource(R.string.live_wp_perm_desc)) },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.clearLiveWpResult()
                            LiveWallpaperSetter.openAppSettings(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Settings, null, Modifier.size(18.dp))
                        Text(" " + stringResource(R.string.live_wp_perm_go_settings))
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.clearLiveWpResult()
                            viewModel.resetApplyState()
                            LiveWallpaperSetter.tryActivate(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Wallpaper, null, Modifier.size(18.dp))
                        Text(" " + stringResource(R.string.live_wp_perm_retry))
                    }

                    TextButton(
                        onClick = { viewModel.clearLiveWpResult() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }
}

// ━━━━━ 横幅 ━━━━━
@Composable
private fun ReturnBannerSection(
    visible: Boolean,
    analyzing: Boolean,
    success: Boolean
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(260)) + fadeIn(tween(260)),
        exit = shrinkVertically(tween(220)) + fadeOut(tween(220))
    ) {
        val bannerColor by animateColorAsState(
            targetValue = if (success) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            animationSpec = tween(420),
            label = "bannerColor"
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = bannerColor)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (analyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "壁纸已设置，正在分析 Monet 配色…",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "✓ Monet 配色已更新",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                if (analyzing) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                    )
                }
            }
        }
    }
}

// ━━━━━ 操作按钮 ━━━━━
@Composable
private fun PreviewActionSection(
    applyButtonAlpha: Float,
    isApplying: Boolean,
    isWaitingConfirm: Boolean,
    onFullScreenClick: () -> Unit,
    onApplyClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onFullScreenClick,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Outlined.Fullscreen, null, Modifier.size(18.dp))
            Text(" ${stringResource(R.string.fullscreen)}")
        }

        Button(
            onClick = onApplyClick,
            modifier = Modifier
                .weight(1f)
                .alpha(applyButtonAlpha),
            enabled = !isApplying && !isWaitingConfirm
        ) {
            if (isApplying) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Outlined.Wallpaper, null, Modifier.size(18.dp))
            }
            Text(
                " ${
                    if (isApplying) stringResource(R.string.setting_wallpaper)
                    else stringResource(R.string.set_wallpaper)
                }"
            )
        }
    }
}

// ━━━━━ 规则弹窗 ━━━━━
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MonetRuleBottomSheet(
    rule: MonetRule,
    isLiveWallpaper: Boolean,
    onDismiss: () -> Unit,
    onSave: (MonetRule) -> Unit
) {
    var framePosition by remember { mutableStateOf(rule.framePosition) }
    var colorRegion by remember { mutableStateOf(rule.colorRegion) }
    var tonePreference by remember { mutableStateOf(rule.tonePreference) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.color_rules),
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(Modifier.height(16.dp))

            if (isLiveWallpaper) {
                Text(
                    text = stringResource(R.string.frame_position),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.frame_position_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    FramePickPosition.entries.forEachIndexed { index, pos ->
                        SegmentedButton(
                            selected = framePosition == pos,
                            onClick = { framePosition = pos },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = FramePickPosition.entries.size
                            )
                        ) {
                            Text(text = framePositionLabel(pos))
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            Text(
                text = stringResource(R.string.color_region),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ColorRegion.entries
                    .filter { it != ColorRegion.CUSTOM }
                    .forEach { region ->
                        FilterChip(
                            selected = colorRegion == region,
                            onClick = { colorRegion = region },
                            label = { Text(text = colorRegionLabel(region)) }
                        )
                    }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text(
                text = stringResource(R.string.tone_preference),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TonePreference.entries.forEach { tone ->
                    FilterChip(
                        selected = tonePreference == tone,
                        onClick = { tonePreference = tone },
                        label = { Text(text = tonePreferenceLabel(tone)) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    onSave(
                        rule.copy(
                            framePosition = framePosition,
                            colorRegion = colorRegion,
                            tonePreference = tonePreference
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.save_and_analyze))
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

// ━━━━━ 设为壁纸弹窗 ━━━━━
@Composable
private fun ApplyWallpaperDialog(
    isLive: Boolean,
    onDismiss: () -> Unit,
    onApplyHome: () -> Unit,
    onApplyLock: () -> Unit,
    onApplyBoth: () -> Unit,
    onApplyLive: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_wallpaper)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isLive) {
                    Button(
                        onClick = onApplyLive,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.set_live_wallpaper))
                    }
                } else {
                    Button(
                        onClick = onApplyHome,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.apply_home))
                    }

                    Button(
                        onClick = onApplyLock,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.apply_lock))
                    }

                    Button(
                        onClick = onApplyBoth,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.apply_both))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
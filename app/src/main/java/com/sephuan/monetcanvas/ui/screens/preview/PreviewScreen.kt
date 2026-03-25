package com.sephuan.monetcanvas.ui.screens.preview

import android.net.Uri
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.sephuan.monetcanvas.data.db.WallpaperEntity
import com.sephuan.monetcanvas.data.model.ImageAdjustment
import com.sephuan.monetcanvas.data.model.MonetRule
import com.sephuan.monetcanvas.data.model.WallpaperType
import com.sephuan.monetcanvas.util.LiveWallpaperSetter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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

    // ━━━━━ 弹窗状态 ━━━━━
    var showApplyDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showRuleSheet by rememberSaveable { mutableStateOf(false) }

    // ━━━━━ ViewModel 状态 ━━━━━
    val applyState by viewModel.applyState.collectAsStateWithLifecycle()
    val showReturnBanner by viewModel.showBanner.collectAsStateWithLifecycle()
    val bannerSuccess by viewModel.bannerSuccess.collectAsStateWithLifecycle()
    val extractedColors by viewModel.extractedColors.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val liveWpResult by viewModel.liveWpResult.collectAsStateWithLifecycle()

    val isApplying = applyState == ApplyState.APPLYING
    val isWaitingConfirm = applyState == ApplyState.WAITING_CONFIRM

    // ━━━━━ 本地状态 ━━━━━
    var currentRule by remember { mutableStateOf<MonetRule?>(null) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var isExiting by remember { mutableStateOf(false) }
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

    val applyButtonAlpha by animateFloatAsState(
        targetValue = if (isApplying || isWaitingConfirm) 0.6f else 1f,
        animationSpec = tween(220),
        label = "applyAlpha"
    )

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

    // ━━━━━ 操作函数 ━━━━━
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

    // ━━━━━ 页面主体：全屏壁纸 + 底部面板 ━━━━━
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
            // ━━━━━ 背景层：壁纸全屏 ━━━━━
            PreviewMediaSection(
                wallpaper = wallpaper,
                player = player,
                adjustment = imageAdjustment,
                onAdjustmentChange = { imageAdjustment = it },
                modifier = Modifier.fillMaxSize()
            )

            // ━━━━━ 前景层：底部可拉面板 ━━━━━
            PreviewBottomPanel(
                wallpaper = wallpaper,
                onBack = ::safeBack,
                onDelete = { showDeleteDialog = true },
                onFullScreenClick = ::openFullScreen,
                onApplyClick = { showApplyDialog = true },
                isApplying = isApplying,
                isWaitingConfirm = isWaitingConfirm,
                applyButtonAlpha = applyButtonAlpha,
                currentRule = currentRule,
                extractedColors = extractedColors,
                isAnalyzing = isAnalyzing,
                onConfigClick = { showRuleSheet = true },
                showReturnBanner = showReturnBanner,
                bannerSuccess = bannerSuccess,
                adjustment = imageAdjustment,
                onAdjustmentChange = { imageAdjustment = it }
            )
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
                LiveWallpaperSetter.openAppSettings(context)
            },
            onRetry = {
                viewModel.clearLiveWpResult()
                viewModel.resetApplyState()
                LiveWallpaperSetter.tryActivate(context)
            }
        )
    }
}
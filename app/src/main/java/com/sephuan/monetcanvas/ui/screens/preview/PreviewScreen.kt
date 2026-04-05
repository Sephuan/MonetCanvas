package com.sephuan.monetcanvas.ui.screens.preview

import android.net.Uri
import androidx.activity.compose.PredictiveBackHandler
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
import androidx.compose.ui.Alignment
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun PreviewScreen(
    wallpaper: WallpaperEntity,
    onBack: () -> Unit,
    onFullScreenClick: (ImageAdjustment) -> Unit,
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
    var imageAdjustment by remember { mutableStateOf(ImageAdjustment.DEFAULT) }

    // 跟手返回进度
    var backProgress by remember { mutableFloatStateOf(0f) }
    var isBackExecuted by remember { mutableStateOf(false) }

    // 动画立即跟随手指
    val animatedProgress by animateFloatAsState(
        targetValue = backProgress,
        animationSpec = tween(durationMillis = 0),
        label = "previewBackProgress"
    )

    // 柔和的变化系数（与设置页一致）
    val scale = 1f - (animatedProgress * 0.03f)
    val contentAlpha = 1f - (animatedProgress * 0.05f)
    val translateX = animatedProgress * 30f
    val cornerRadius = (animatedProgress * 20f).dp

    val currentApplyState by rememberUpdatedState(applyState)
    val currentRuleState by rememberUpdatedState(currentRule)
    val currentAdjustment by rememberUpdatedState(imageAdjustment)

    val applyButtonAlpha by animateFloatAsState(
        targetValue = if (isApplying || isWaitingConfirm) 0.6f else 1f,
        animationSpec = tween(220),
        label = "applyAlpha"
    )

    // 预测返回处理器：只更新进度，等待手势完成
    PredictiveBackHandler(enabled = !isApplying && !isWaitingConfirm) { backEventFlow ->
        isBackExecuted = false
        backEventFlow.collect { backEvent ->
            backProgress = backEvent.progress
        }
        if (!isBackExecuted) {
            isBackExecuted = true
            // 手势完成，执行返回
            if (wallpaper.type == WallpaperType.STATIC && currentAdjustment.hasAnyAdjustment) {
                viewModel.saveAdjustmentForWallpaper(wallpaper, currentAdjustment)
            }
            player?.clearVideoSurface()
            player?.pause()
            player?.stop()
            player?.release()
            player = null
            isExiting = true
        }
    }

    // 重置标志（页面重新进入时）
    LaunchedEffect(Unit) {
        isBackExecuted = false
        backProgress = 0f
    }

    // 初始化播放器（仅动态壁纸）
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

    DisposableEffect(Unit) {
        onDispose {
            player?.pause()
            player?.stop()
            player?.release()
            player = null
        }
    }

    // 生命周期监听（处理从系统确认页返回）
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

    // 加载取色规则和调整参数
    LaunchedEffect(wallpaper.id) {
        val rule = viewModel.loadRuleForWallpaper(wallpaper)
        currentRule = rule
        viewModel.analyzeColors(wallpaper, rule)

        if (wallpaper.type == WallpaperType.STATIC) {
            imageAdjustment = viewModel.loadAdjustmentForWallpaper(wallpaper)
        }
    }

    LaunchedEffect(isExiting) {
        if (isExiting) {
            kotlinx.coroutines.delay(10)
            onBack()
        }
    }

    // 安全返回（主动点击返回按钮时调用）
    fun safeBack() {
        if (wallpaper.type == WallpaperType.STATIC && imageAdjustment.hasAnyAdjustment) {
            viewModel.saveAdjustmentForWallpaper(wallpaper, imageAdjustment)
        }
        player?.clearVideoSurface()
        player?.pause()
        player?.stop()
        player?.release()
        player = null
        isExiting = true
    }

    fun openFullScreen() {
        if (wallpaper.type == WallpaperType.STATIC) {
            viewModel.saveAdjustmentForWallpaper(wallpaper, imageAdjustment)
        }
        player?.pause()
        onFullScreenClick(imageAdjustment)
    }

    fun handleApply(target: Int) {
        if (isApplying || isWaitingConfirm) return
        val rule = currentRule ?: MonetRule()
        player?.pause()
        viewModel.applyWallpaper(
            context = context,
            wallpaper = wallpaper,
            target = target,
            rule = rule,
            adjustment = imageAdjustment
        )
    }

    // 页面主体：带返回动画的容器
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
            PreviewMediaSection(
                wallpaper = wallpaper,
                player = player,
                adjustment = imageAdjustment,
                onAdjustmentChange = { imageAdjustment = it },
                modifier = Modifier.fillMaxSize()
            )

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
                onAdjustmentChange = { imageAdjustment = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    // 弹窗
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
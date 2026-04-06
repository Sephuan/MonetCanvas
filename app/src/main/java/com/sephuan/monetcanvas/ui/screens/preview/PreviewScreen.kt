@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sephuan.monetcanvas.ui.screens.preview

import android.app.Activity.RESULT_OK
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.sephuan.monetcanvas.data.db.WallpaperEntity
import com.sephuan.monetcanvas.data.model.ImageAdjustment
import com.sephuan.monetcanvas.data.model.MonetRule
import com.sephuan.monetcanvas.data.model.WallpaperType
import com.sephuan.monetcanvas.util.LiveWallpaperSetter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    wallpaper: WallpaperEntity,
    onBack: () -> Unit,
    onFullScreenClick: (ImageAdjustment) -> Unit,
    viewModel: PreviewViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val displayMetrics = context.resources.displayMetrics
    val screenWidthPx = displayMetrics.widthPixels
    val screenHeightPx = displayMetrics.heightPixels

    var showApplyDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRuleSheet by remember { mutableStateOf(false) }

    val applyState by viewModel.applyState.collectAsStateWithLifecycle()
    val extractedColors by viewModel.extractedColors.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val liveWpResult by viewModel.liveWpResult.collectAsStateWithLifecycle()
    val showBanner by viewModel.showBanner.collectAsStateWithLifecycle()
    val bannerSuccess by viewModel.bannerSuccess.collectAsStateWithLifecycle()

    val isApplying = applyState == ApplyState.APPLYING
    val isWaitingConfirm = applyState == ApplyState.WAITING_CONFIRM

    var currentRule by remember { mutableStateOf<MonetRule?>(null) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var imageAdjustment by remember { mutableStateOf(ImageAdjustment.DEFAULT) }

    // 防止重复启动系统确认页的标志
    var launcherInvoked by remember { mutableStateOf(false) }

    // ★ 精确监听系统确认页返回结果
    val liveWallpaperLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        launcherInvoked = false
        // 仅在 WAITING_CONFIRM 状态时处理回调，避免残留回调影响
        if (applyState == ApplyState.WAITING_CONFIRM) {
            if (result.resultCode == RESULT_OK) {
                viewModel.onUserConfirmed(
                    context = context,
                    wallpaper = wallpaper,
                    rule = currentRule ?: MonetRule()
                )
            } else {
                viewModel.onUserCancelled(context)
            }
        }
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
            player?.release()
            player = null
        }
    }

    // 加载规则和调整参数，同时重置状态（切换壁纸时清除残留的 WAITING_CONFIRM 和 pending 配置）
    LaunchedEffect(wallpaper.id) {
        // 重置启动标志
        launcherInvoked = false
        // 如果当前处于等待确认状态，强制重置并清除 pending 配置
        if (applyState == ApplyState.WAITING_CONFIRM || applyState == ApplyState.CONFIRMING) {
            viewModel.resetApplyState()
            LiveWallpaperSetter.clearPendingConfig(context)
        }
        val rule = viewModel.loadRuleForWallpaper(wallpaper)
        currentRule = rule
        viewModel.analyzeColors(wallpaper, rule)
        if (wallpaper.type == WallpaperType.STATIC) {
            imageAdjustment = viewModel.loadAdjustmentForWallpaper(wallpaper)
        }
    }

    // ★ 监听 applyState 变化，仅在 WAITING_CONFIRM 且未被调用时启动系统确认页
    LaunchedEffect(Unit) {
        snapshotFlow { applyState }
            .distinctUntilChanged()
            .collect { state ->
                if (state == ApplyState.WAITING_CONFIRM && !launcherInvoked) {
                    launcherInvoked = true
                    val intent = LiveWallpaperSetter.createActivationIntent(context)
                    liveWallpaperLauncher.launch(intent)
                }
            }
    }

    fun handleApply(target: Int) {
        if (isApplying || isWaitingConfirm) return
        val rule = currentRule ?: MonetRule()
        viewModel.applyWallpaper(context, wallpaper, target, rule, imageAdjustment)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PreviewMediaSection(
            wallpaper = wallpaper,
            player = player,
            adjustment = imageAdjustment,
            onAdjustmentChange = { newAdjustment ->
                imageAdjustment = newAdjustment
                if (wallpaper.type == WallpaperType.STATIC) {
                    viewModel.saveAdjustmentForWallpaper(wallpaper, newAdjustment)
                }
            },
            screenWidth = screenWidthPx,
            screenHeight = screenHeightPx,
            modifier = Modifier.fillMaxSize()
        )

        PreviewBottomPanel(
            wallpaper = wallpaper,
            onBack = onBack,
            onDelete = { showDeleteDialog = true },
            onFullScreenClick = { onFullScreenClick(imageAdjustment) },
            onApplyClick = { showApplyDialog = true },
            isApplying = isApplying,
            isWaitingConfirm = isWaitingConfirm,
            applyButtonAlpha = 1f,
            currentRule = currentRule,
            extractedColors = extractedColors,
            isAnalyzing = isAnalyzing,
            onConfigClick = { showRuleSheet = true },
            showReturnBanner = showBanner,
            bannerSuccess = bannerSuccess,
            adjustment = imageAdjustment,
            onAdjustmentChange = { newAdjustment ->
                imageAdjustment = newAdjustment
                if (wallpaper.type == WallpaperType.STATIC) {
                    viewModel.saveAdjustmentForWallpaper(wallpaper, newAdjustment)
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
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
                if (wallpaper.type == WallpaperType.LIVE) {
                    viewModel.applyWallpaper(
                        context,
                        wallpaper,
                        0,
                        currentRule ?: MonetRule(),
                        imageAdjustment
                    )
                }
            }
        )
    }
}
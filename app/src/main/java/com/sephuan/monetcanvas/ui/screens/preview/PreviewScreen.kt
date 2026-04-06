package com.sephuan.monetcanvas.ui.screens.preview

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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

    val liveWallpaperLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (applyState == ApplyState.WAITING_CONFIRM) {
            if (result.resultCode == android.app.Activity.RESULT_OK) {
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

    // 加载规则和调整参数
    LaunchedEffect(wallpaper.id) {
        val rule = viewModel.loadRuleForWallpaper(wallpaper)
        currentRule = rule
        viewModel.analyzeColors(wallpaper, rule)

        if (wallpaper.type == WallpaperType.STATIC) {
            imageAdjustment = viewModel.loadAdjustmentForWallpaper(wallpaper)
        }
    }

    // 启动动态壁纸系统确认页
    LaunchedEffect(applyState) {
        if (applyState == ApplyState.WAITING_CONFIRM) {
            val intent = LiveWallpaperSetter.createActivationIntent(context)
            liveWallpaperLauncher.launch(intent)
        }
    }

    fun handleApply(target: Int) {
        if (isApplying || isWaitingConfirm) return
        val rule = currentRule ?: MonetRule()
        viewModel.applyWallpaper(context, wallpaper, target, rule, imageAdjustment)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 底层：壁纸预览
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

        // 顶层：底部面板
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
                        context = context,
                        wallpaper = wallpaper,
                        target = 0,
                        rule = currentRule ?: MonetRule(),
                        adjustment = imageAdjustment
                    )
                }
            }
        )
    }
}
package com.sephuan.monetcanvas.ui.screens.preview

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.sephuan.monetcanvas.R
import com.sephuan.monetcanvas.data.db.WallpaperEntity
import com.sephuan.monetcanvas.data.model.ColorRegion
import com.sephuan.monetcanvas.data.model.FramePickPosition
import com.sephuan.monetcanvas.data.model.MonetRule
import com.sephuan.monetcanvas.data.model.TonePreference
import com.sephuan.monetcanvas.data.model.WallpaperType
import com.sephuan.monetcanvas.util.LiveWallpaperSetter
import kotlinx.coroutines.launch

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

    var showApplyDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRuleSheet by remember { mutableStateOf(false) }

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

    // ★ 用 rememberUpdatedState 保证 Observer 回调里拿到最新值
    val currentApplyState by rememberUpdatedState(applyState)
    val currentRuleState by rememberUpdatedState(currentRule)

    LaunchedEffect(wallpaper.filePath) {
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
            player?.stop()
            player?.release()
            player = null
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    player?.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    player?.play()
                    if (currentApplyState == ApplyState.WAITING_CONFIRM) {
                        viewModel.onReturnFromSystemPage(
                            context, wallpaper, currentRuleState ?: MonetRule()
                        )
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(wallpaper.id) {
        val rule = viewModel.loadRuleForWallpaper(wallpaper)
        currentRule = rule
        viewModel.analyzeColors(wallpaper, rule)
    }

    fun navigateBack() {
        player?.stop()
        player?.release()
        player = null
        onBack()
    }

    fun navigateFullScreen() {
        player?.pause()
        onFullScreenClick()
    }

    fun handleApply(target: Int) {
        if (isApplying || isWaitingConfirm) return
        val rule = currentRule ?: MonetRule()
        if (wallpaper.type == WallpaperType.LIVE) {
            player?.pause()
        }
        viewModel.applyWallpaper(context, wallpaper, target, rule)
    }

    val applyButtonAlpha by animateFloatAsState(
        targetValue = if (isApplying || isWaitingConfirm) 0.6f else 1f,
        animationSpec = tween(300),
        label = "applyAlpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        wallpaper.fileName,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = ::navigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Outlined.Delete, stringResource(R.string.delete))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ★ 横幅用 Box 包裹避免 Column 作用域歧义
            Box(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showReturnBanner,
                    enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                    exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
                ) {
                    val bannerColor by animateColorAsState(
                        targetValue = if (bannerSuccess)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        animationSpec = tween(500),
                        label = "bannerColor"
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = bannerColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    "壁纸已设置，正在分析 Monet 配色…",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                Icon(
                                    Icons.Outlined.CheckCircle, null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "✓ Monet 配色已更新",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        if (isAnalyzing) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(1.dp))
                            )
                        }
                    }
                }
            }

            // ━━━━━ 预览区 ━━━━━
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                if (wallpaper.type == WallpaperType.STATIC) {
                    AsyncImage(
                        model = wallpaper.filePath,
                        contentDescription = wallpaper.fileName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    player?.let { exo ->
                        AndroidView(
                            factory = { ctx ->
                                androidx.media3.ui.PlayerView(ctx).apply {
                                    useController = false
                                    this.player = exo
                                }
                            },
                            update = { view ->
                                if (view.player != player) {
                                    view.player = player
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = ::navigateFullScreen,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                ) {
                    Icon(Icons.Outlined.Fullscreen, stringResource(R.string.fullscreen_preview))
                }

                // ★ 遮罩用 Box 包裹
                Box(modifier = Modifier.fillMaxSize()) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isApplying,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(200)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.setting_wallpaper),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ━━━━━ Monet 取色预览卡片 ━━━━━
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Outlined.Palette, null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            if (wallpaper.type == WallpaperType.LIVE)
                                stringResource(R.string.color_preview_live)
                            else
                                stringResource(R.string.color_preview_static),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { showRuleSheet = true }) {
                            Text(stringResource(R.string.configure))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // ★ 用 Box 包裹每个 AnimatedVisibility
                    Box {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isAnalyzing,
                            enter = fadeIn(tween(200)),
                            exit = fadeOut(tween(200))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    stringResource(R.string.analyzing),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    Box {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !isAnalyzing && extractedColors != null,
                            enter = fadeIn(tween(400)) + expandVertically(tween(400)),
                            exit = fadeOut(tween(200))
                        ) {
                            extractedColors?.let { colors ->
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    ColorCircle(colors.primary, stringResource(R.string.primary_color))
                                    colors.secondary?.let {
                                        ColorCircle(it, stringResource(R.string.secondary_color))
                                    }
                                    colors.tertiary?.let {
                                        ColorCircle(it, stringResource(R.string.tertiary_color))
                                    }
                                }
                            }
                        }
                    }

                    Box {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !isAnalyzing && extractedColors == null,
                            enter = fadeIn(tween(300)),
                            exit = fadeOut(tween(200))
                        ) {
                            Text(
                                stringResource(R.string.extract_failed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    currentRule?.let { rule ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                R.string.rule_format,
                                buildRuleDescription(rule, wallpaper.type == WallpaperType.LIVE)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (wallpaper.type == WallpaperType.LIVE) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.monet_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ━━━━━ 操作按钮 ━━━━━
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = ::navigateFullScreen,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Fullscreen, null, Modifier.size(18.dp))
                    Text(" ${stringResource(R.string.fullscreen)}")
                }

                Button(
                    onClick = { showApplyDialog = true },
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

            Spacer(Modifier.height(32.dp))
        }
    }

    // ━━━━━ 弹窗们 ━━━━━

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
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_wallpaper)) },
            text = { Text(stringResource(R.string.delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        player?.stop()
                        player?.release()
                        player = null
                        viewModel.deleteWallpaper(context, wallpaper) { onBack() }
                    }
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
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
        AlertDialog(
            onDismissRequest = { viewModel.clearLiveWpResult() },
            icon = {
                Icon(
                    Icons.Outlined.Warning, null,
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
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.live_wp_perm_go_settings))
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
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.live_wp_perm_retry))
                    }

                    TextButton(
                        onClick = { viewModel.clearLiveWpResult() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
    }
}

// ━━━━━ 取色规则弹窗 ━━━━━
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
            Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(stringResource(R.string.color_rules), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))

            if (isLiveWallpaper) {
                Text(stringResource(R.string.frame_position), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.frame_position_desc),
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
                            Text(framePositionLabel(pos), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 16.dp))
            }

            Text(stringResource(R.string.color_region), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorRegion.entries.filter { it != ColorRegion.CUSTOM }.forEach { region ->
                    FilterChip(
                        selected = colorRegion == region,
                        onClick = { colorRegion = region },
                        label = { Text(colorRegionLabel(region)) }
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            Text(stringResource(R.string.tone_preference), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TonePreference.entries.forEach { tone ->
                    FilterChip(
                        selected = tonePreference == tone,
                        onClick = { tonePreference = tone },
                        label = { Text(tonePreferenceLabel(tone)) }
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
            ) { Text(stringResource(R.string.save_and_analyze)) }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ColorCircle(colorInt: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(colorInt))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

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
                    Button(onClick = onApplyLive, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.set_live_wallpaper))
                    }
                } else {
                    Button(onClick = onApplyHome, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.apply_home))
                    }
                    Button(onClick = onApplyLock, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.apply_lock))
                    }
                    Button(onClick = onApplyBoth, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.apply_both))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun framePositionLabel(pos: FramePickPosition): String = when (pos) {
    FramePickPosition.FIRST -> stringResource(R.string.frame_first)
    FramePickPosition.MIDDLE -> stringResource(R.string.frame_middle)
    FramePickPosition.LAST -> stringResource(R.string.frame_last)
    FramePickPosition.RANDOM -> stringResource(R.string.frame_random)
}

@Composable
private fun colorRegionLabel(region: ColorRegion): String = when (region) {
    ColorRegion.FULL_FRAME -> stringResource(R.string.region_full)
    ColorRegion.CENTER -> stringResource(R.string.region_center)
    ColorRegion.TOP_HALF -> stringResource(R.string.region_top)
    ColorRegion.BOTTOM_HALF -> stringResource(R.string.region_bottom)
    ColorRegion.CUSTOM -> "Custom"
}

@Composable
private fun tonePreferenceLabel(tone: TonePreference): String = when (tone) {
    TonePreference.AUTO -> stringResource(R.string.tone_auto)
    TonePreference.VIBRANT -> stringResource(R.string.tone_vibrant)
    TonePreference.MUTED -> stringResource(R.string.tone_muted)
    TonePreference.DOMINANT -> stringResource(R.string.tone_dominant)
    TonePreference.DARK_PREFERRED -> stringResource(R.string.tone_dark)
    TonePreference.LIGHT_PREFERRED -> stringResource(R.string.tone_light)
}

@Composable
private fun buildRuleDescription(rule: MonetRule, isLive: Boolean): String {
    return buildString {
        if (isLive) {
            append(framePositionLabel(rule.framePosition))
            append(" · ")
        }
        append(colorRegionLabel(rule.colorRegion))
        append(" · ")
        append(tonePreferenceLabel(rule.tonePreference))
    }
}
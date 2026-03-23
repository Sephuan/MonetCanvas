package com.sephuan.monetcanvas.ui.screens.preview

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
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
import kotlinx.coroutines.delay
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
    var isApplying by remember { mutableStateOf(false) }

    // ★ 跟踪是否正在等待用户从系统确认页返回
    var waitingForLiveWpReturn by remember { mutableStateOf(false) }
    var showReturnBanner by remember { mutableStateOf(false) }

    val extractedColors by viewModel.extractedColors.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val liveWpResult by viewModel.liveWpResult.collectAsStateWithLifecycle()
    var currentRule by remember { mutableStateOf<MonetRule?>(null) }

    // 首次加载
    LaunchedEffect(wallpaper.id) {
        val rule = viewModel.loadRuleForWallpaper(wallpaper)
        currentRule = rule
        viewModel.analyzeColors(wallpaper, rule)
    }

    // ★ 监听生命周期：用户从系统确认页返回时触发
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && waitingForLiveWpReturn) {
                waitingForLiveWpReturn = false
                showReturnBanner = true

                // ★ 调用 ViewModel 的回检逻辑
                //   确认 → promote + 重新取色
                //   取消 → clear pending
                viewModel.onReturnFromSystemPage(
                    context, wallpaper, currentRule ?: MonetRule()
                )

                // 横幅显示几秒后自动消失
                scope.launch {
                    delay(4000)
                    showReturnBanner = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun handleApply(target: Int) {
        if (isApplying) return
        isApplying = true
        val rule = currentRule ?: MonetRule()

        if (wallpaper.type == WallpaperType.LIVE) {
            // ★ 标记：即将跳转系统确认页
            waitingForLiveWpReturn = true
        }

        viewModel.applyWallpaper(context, wallpaper, target, rule)
        isApplying = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(wallpaper.fileName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            // ★ 返回后提示横幅
            AnimatedVisibility(
                visible = showReturnBanner,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
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
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(
                            if (isAnalyzing) "壁纸已设置，正在重新分析 Monet 配色…"
                            else "✓ Monet 配色已更新",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
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
                    LiveVideoPreview(filePath = wallpaper.filePath)
                }

                FilledTonalIconButton(
                    onClick = onFullScreenClick,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                ) {
                    Icon(Icons.Outlined.Fullscreen, stringResource(R.string.fullscreen_preview))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ━━━━━ Monet 取色预览 ━━━━━
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

                    if (isAnalyzing) {
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
                    } else if (extractedColors != null) {
                        val colors = extractedColors!!
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            ColorCircle(colors.primary, stringResource(R.string.primary_color))
                            colors.secondary?.let {
                                ColorCircle(it, stringResource(R.string.secondary_color))
                            }
                            colors.tertiary?.let {
                                ColorCircle(it, stringResource(R.string.tertiary_color))
                            }
                        }
                    } else {
                        Text(
                            stringResource(R.string.extract_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    currentRule?.let { rule ->
                        Spacer(modifier = Modifier.height(8.dp))
                        val desc = buildRuleDescription(
                            rule, wallpaper.type == WallpaperType.LIVE
                        )
                        Text(
                            text = stringResource(R.string.rule_format, desc),
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
                    onClick = onFullScreenClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Fullscreen, null, Modifier.size(18.dp))
                    Text(" ${stringResource(R.string.fullscreen)}")
                }

                Button(
                    onClick = { showApplyDialog = true },
                    modifier = Modifier.weight(1f),
                    enabled = !isApplying
                ) {
                    Icon(Icons.Outlined.Wallpaper, null, Modifier.size(18.dp))
                    Text(
                        " ${
                            if (isApplying) stringResource(R.string.setting_wallpaper)
                            else stringResource(R.string.set_wallpaper)
                        }"
                    )
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

    // ━━━━━ 删除确认 ━━━━━
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_wallpaper)) },
            text = { Text(stringResource(R.string.delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
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

    // ━━━━━ 取色规则配置弹窗 ━━━━━
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

    // ━━━━━ 动态壁纸激活失败弹窗 ━━━━━
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
                            waitingForLiveWpReturn = true
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
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }
}

// ━━━━━ 视频预览组件 ━━━━━
@Composable
private fun LiveVideoPreview(filePath: String) {
    val context = LocalContext.current
    val player = remember(filePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse("file://$filePath")))
            repeatMode = ExoPlayer.REPEAT_MODE_ALL
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    AndroidView(
        factory = {
            androidx.media3.ui.PlayerView(it).apply {
                useController = false
                this.player = player
            }
        },
        modifier = Modifier.fillMaxSize()
    )
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
            Text(
                stringResource(R.string.color_rules),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(16.dp))

            if (isLiveWallpaper) {
                Text(
                    stringResource(R.string.frame_position),
                    style = MaterialTheme.typography.titleMedium
                )
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
                            Text(
                                framePositionLabel(pos),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 16.dp))
            }

            Text(
                stringResource(R.string.color_region),
                style = MaterialTheme.typography.titleMedium
            )
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

            Text(
                stringResource(R.string.tone_preference),
                style = MaterialTheme.typography.titleMedium
            )
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

// ━━━━━ 颜色圆点 ━━━━━
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

// ━━━━━ 多语言标签 ━━━━━
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
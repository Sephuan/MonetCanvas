package com.sephuan.monetcanvas.ui.screens.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sephuan.monetcanvas.R
import com.sephuan.monetcanvas.data.db.WallpaperEntity
import com.sephuan.monetcanvas.data.model.ImageAdjustment
import com.sephuan.monetcanvas.data.model.MonetRule
import com.sephuan.monetcanvas.data.model.WallpaperType
import com.sephuan.monetcanvas.util.ExtractedColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PreviewBottomPanel(
    wallpaper: WallpaperEntity,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onFullScreenClick: () -> Unit,
    onApplyClick: () -> Unit,
    isApplying: Boolean,
    isWaitingConfirm: Boolean,
    applyButtonAlpha: Float,
    currentRule: MonetRule?,
    extractedColors: ExtractedColors?,
    isAnalyzing: Boolean,
    onConfigClick: () -> Unit,
    showReturnBanner: Boolean,
    bannerSuccess: Boolean,
    adjustment: ImageAdjustment,
    onAdjustmentChange: (ImageAdjustment) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val screenHeightPx = with(density) { screenHeightDp.toPx() }

    val isStatic = wallpaper.type == WallpaperType.STATIC

    // 面板高度范围（像素）
    val collapsedPx = with(density) { 160.dp.toPx() }
    val expandedPx = screenHeightPx * 0.68f

    // 当前面板高度
    var panelHeightPx by remember { mutableFloatStateOf(collapsedPx) }

    val panelHeightDp = with(density) { panelHeightPx.toDp() }
    val panelColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)

    // ★ 面板只占底部区域，不 fillMaxSize
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(panelHeightDp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(panelColor)
    ) {
        // ━━━━━ 拖拽手柄（只有这个区域拦截垂直拖拽）━━━━━
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        // 向上拖 = dragAmount < 0 = 增大高度
                        // 向下拖 = dragAmount > 0 = 减小高度
                        panelHeightPx = (panelHeightPx - dragAmount)
                            .coerceIn(collapsedPx, expandedPx)
                    }
                }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
            )
        }

        // ━━━━━ 标题栏 ━━━━━
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = wallpaper.fileName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // ━━━━━ 横幅 ━━━━━
        ReturnBannerSection(
            visible = showReturnBanner,
            analyzing = isAnalyzing,
            success = bannerSuccess
        )

        // ━━━━━ 标签页内容（占满剩余空间）━━━━━
        Box(modifier = Modifier.weight(1f)) {
            if (isStatic) {
                StaticPanelContent(
                    wallpaper = wallpaper,
                    adjustment = adjustment,
                    onAdjustmentChange = onAdjustmentChange,
                    currentRule = currentRule,
                    extractedColors = extractedColors,
                    isAnalyzing = isAnalyzing,
                    onConfigClick = onConfigClick,
                    applyButtonAlpha = applyButtonAlpha,
                    isApplying = isApplying,
                    isWaitingConfirm = isWaitingConfirm,
                    onFullScreenClick = onFullScreenClick,
                    onApplyClick = onApplyClick
                )
            } else {
                LivePanelContent(
                    wallpaper = wallpaper,
                    currentRule = currentRule,
                    extractedColors = extractedColors,
                    isAnalyzing = isAnalyzing,
                    onConfigClick = onConfigClick,
                    applyButtonAlpha = applyButtonAlpha,
                    isApplying = isApplying,
                    isWaitingConfirm = isWaitingConfirm,
                    onFullScreenClick = onFullScreenClick,
                    onApplyClick = onApplyClick
                )
            }
        }
    }
}

// ━━━━━ 静态壁纸：三个标签页 ━━━━━

@Composable
private fun StaticPanelContent(
    wallpaper: WallpaperEntity,
    adjustment: ImageAdjustment,
    onAdjustmentChange: (ImageAdjustment) -> Unit,
    currentRule: MonetRule?,
    extractedColors: ExtractedColors?,
    isAnalyzing: Boolean,
    onConfigClick: () -> Unit,
    applyButtonAlpha: Float,
    isApplying: Boolean,
    isWaitingConfirm: Boolean,
    onFullScreenClick: () -> Unit,
    onApplyClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                if (pagerState.currentPage < tabPositions.size) {
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                        width = 36.dp,
                        shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                    )
                }
            }
        ) {
            PanelTab(
                selected = pagerState.currentPage == 0,
                icon = Icons.Outlined.Image,
                label = "操作",
                onClick = { scope.launch { pagerState.animateScrollToPage(0) } }
            )
            PanelTab(
                selected = pagerState.currentPage == 1,
                icon = Icons.Outlined.Edit,
                label = "调整",
                onClick = { scope.launch { pagerState.animateScrollToPage(1) } }
            )
            PanelTab(
                selected = pagerState.currentPage == 2,
                icon = Icons.Outlined.Palette,
                label = "取色",
                onClick = { scope.launch { pagerState.animateScrollToPage(2) } }
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 2
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (page) {
                    0 -> {
                        PreviewActionSection(
                            applyButtonAlpha = applyButtonAlpha,
                            isApplying = isApplying,
                            isWaitingConfirm = isWaitingConfirm,
                            onFullScreenClick = onFullScreenClick,
                            onApplyClick = onApplyClick
                        )
                        Spacer(Modifier.height(40.dp))
                    }

                    1 -> {
                        PreviewImageEditSection(
                            adjustment = adjustment,
                            onAdjustmentChange = onAdjustmentChange
                        )
                        Spacer(Modifier.height(40.dp))
                    }

                    2 -> {
                        PreviewMonetSection(
                            wallpaper = wallpaper,
                            currentRule = currentRule,
                            extractedColors = extractedColors,
                            isAnalyzing = isAnalyzing,
                            onConfigClick = onConfigClick
                        )
                        Spacer(Modifier.height(40.dp))
                    }
                }
            }
        }
    }
}

// ━━━━━ 动态壁纸：两个标签页 ━━━━━

@Composable
private fun LivePanelContent(
    wallpaper: WallpaperEntity,
    currentRule: MonetRule?,
    extractedColors: ExtractedColors?,
    isAnalyzing: Boolean,
    onConfigClick: () -> Unit,
    applyButtonAlpha: Float,
    isApplying: Boolean,
    isWaitingConfirm: Boolean,
    onFullScreenClick: () -> Unit,
    onApplyClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                if (pagerState.currentPage < tabPositions.size) {
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                        width = 36.dp,
                        shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                    )
                }
            }
        ) {
            PanelTab(
                selected = pagerState.currentPage == 0,
                icon = Icons.Outlined.Image,
                label = "操作",
                onClick = { scope.launch { pagerState.animateScrollToPage(0) } }
            )
            PanelTab(
                selected = pagerState.currentPage == 1,
                icon = Icons.Outlined.Palette,
                label = "取色",
                onClick = { scope.launch { pagerState.animateScrollToPage(1) } }
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (page) {
                    0 -> {
                        PreviewActionSection(
                            applyButtonAlpha = applyButtonAlpha,
                            isApplying = isApplying,
                            isWaitingConfirm = isWaitingConfirm,
                            onFullScreenClick = onFullScreenClick,
                            onApplyClick = onApplyClick
                        )
                        Spacer(Modifier.height(40.dp))
                    }

                    1 -> {
                        PreviewMonetSection(
                            wallpaper = wallpaper,
                            currentRule = currentRule,
                            extractedColors = extractedColors,
                            isAnalyzing = isAnalyzing,
                            onConfigClick = onConfigClick
                        )
                        Spacer(Modifier.height(40.dp))
                    }
                }
            }
        }
    }
}

// ━━━━━ 通用标签 ━━━━━

@Composable
private fun PanelTab(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    )
}
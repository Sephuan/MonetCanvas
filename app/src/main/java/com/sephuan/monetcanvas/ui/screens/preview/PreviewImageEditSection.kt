package com.sephuan.monetcanvas.ui.screens.preview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sephuan.monetcanvas.data.model.FillMode
import com.sephuan.monetcanvas.data.model.ImageAdjustment
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreviewImageEditSection(
    adjustment: ImageAdjustment,
    onAdjustmentChange: (ImageAdjustment) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // ━━━━━ 重置按钮 ━━━━━
        AnimatedVisibility(
            visible = adjustment.hasAnyAdjustment,
            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { onAdjustmentChange(ImageAdjustment.DEFAULT) }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("重置全部")
                }
            }
        }

        // ━━━━━ 1. 填充方式 ━━━━━
        SectionLabel("填充方式")
        Spacer(Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FillModeChip(
                label = "覆盖",
                hint = "左右移动",
                selected = adjustment.fillMode == FillMode.COVER,
                onClick = {
                    onAdjustmentChange(
                        adjustment.copy(
                            fillMode = FillMode.COVER,
                            offsetX = 0f,
                            offsetY = 0f,
                            scale = 1f
                        )
                    )
                }
            )
            FillModeChip(
                label = "填充",
                hint = "上下移动",
                selected = adjustment.fillMode == FillMode.FIT,
                onClick = {
                    onAdjustmentChange(
                        adjustment.copy(
                            fillMode = FillMode.FIT,
                            offsetX = 0f,
                            offsetY = 0f,
                            scale = 1f
                        )
                    )
                }
            )
            FillModeChip(
                label = "自由",
                hint = "缩放+移动",
                selected = adjustment.fillMode == FillMode.FREE,
                onClick = {
                    onAdjustmentChange(
                        adjustment.copy(
                            fillMode = FillMode.FREE,
                            offsetX = 0f,
                            offsetY = 0f,
                            scale = 1f
                        )
                    )
                }
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = when (adjustment.fillMode) {
                FillMode.COVER -> "在壁纸上左右滑动调整位置"
                FillMode.FIT -> "在壁纸上上下滑动调整位置"
                FillMode.FREE -> "在壁纸上双指缩放、单指拖动"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionDivider()

        // ━━━━━ 2. 背景色（所有模式都显示）━━━━━
        SectionLabel("画布背景色")
        Text(
            text = "壁纸未覆盖区域的颜色（缩小或移动壁纸时可见）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ImageAdjustment.BACKGROUND_COLORS.forEach { color ->
                val isSelected = adjustment.backgroundColor == color
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    3.dp,
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape
                                )
                            } else {
                                Modifier.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape
                                )
                            }
                        )
                        .clickable {
                            onAdjustmentChange(
                                adjustment.copy(backgroundColor = color)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (color == Color.White) Color.Black
                            else Color.White
                        )
                    }
                }
            }
        }

        SectionDivider()

        // ━━━━━ 3. 镜像翻转 ━━━━━
        SectionLabel("镜像翻转")
        Spacer(Modifier.height(4.dp))

        MirrorRow(
            label = "水平翻转",
            checked = adjustment.mirrorHorizontal,
            onCheckedChange = {
                onAdjustmentChange(adjustment.copy(mirrorHorizontal = it))
            }
        )

        MirrorRow(
            label = "垂直翻转",
            checked = adjustment.mirrorVertical,
            onCheckedChange = {
                onAdjustmentChange(adjustment.copy(mirrorVertical = it))
            }
        )

        SectionDivider()

        // ━━━━━ 4. 色彩调整 ━━━━━
        SectionLabel("色彩调整")
        Spacer(Modifier.height(4.dp))

        AdjustSlider(
            label = "亮度",
            value = adjustment.brightness,
            onValueChange = {
                onAdjustmentChange(adjustment.copy(brightness = it))
            }
        )

        AdjustSlider(
            label = "对比度",
            value = adjustment.contrast,
            onValueChange = {
                onAdjustmentChange(adjustment.copy(contrast = it))
            }
        )

        AdjustSlider(
            label = "饱和度",
            value = adjustment.saturation,
            onValueChange = {
                onAdjustmentChange(adjustment.copy(saturation = it))
            }
        )
    }
}

// ━━━━━ 通用组件 ━━━━━

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun SectionDivider() {
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun FillModeChip(
    label: String,
    hint: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun MirrorRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun AdjustSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(56.dp)
        )

        Slider(
            value = value,
            onValueChange = { onValueChange((it * 100f).roundToInt() / 100f) },
            valueRange = -1f..1f,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = "${(value * 100).roundToInt()}",
            style = MaterialTheme.typography.labelMedium,
            color = if (value != 0f) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp)
        )
    }
}
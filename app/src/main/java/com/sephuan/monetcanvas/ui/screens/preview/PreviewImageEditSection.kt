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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sephuan.monetcanvas.R
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
        // 重置按钮
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
                    Text(stringResource(R.string.reset_all))
                }
            }
        }

        // ━━━━━ 1. 填充方式 ━━━━━
        SectionLabel(stringResource(R.string.fill_mode))
        Spacer(Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FillModeChip(
                label = stringResource(R.string.fill_cover),
                hint = stringResource(R.string.fill_cover_hint),
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
                label = stringResource(R.string.fill_fit),
                hint = stringResource(R.string.fill_fit_hint),
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
                label = stringResource(R.string.fill_free),
                hint = stringResource(R.string.fill_free_hint),
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
                FillMode.COVER -> stringResource(R.string.fill_cover_desc)
                FillMode.FIT -> stringResource(R.string.fill_fit_desc)
                FillMode.FREE -> stringResource(R.string.fill_free_desc)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionDivider()

        // ━━━━━ 2. 缩放（新增）━━━━━
        SectionLabel(stringResource(R.string.scale))
        Text(
            text = stringResource(R.string.scale_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "0.2",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = adjustment.scale,
                onValueChange = { onAdjustmentChange(adjustment.copy(scale = it)) },
                valueRange = 0.2f..2.0f,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "2.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.small),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = "${(adjustment.scale * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.large),
                style = MaterialTheme.typography.labelSmall
            )
        }

        SectionDivider()

        // ━━━━━ 3. 画布背景色 ━━━━━
        SectionLabel(stringResource(R.string.canvas_bg))
        Text(
            text = stringResource(R.string.canvas_bg_desc),
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
                            tint = if (color == Color.White) Color.Black else Color.White
                        )
                    }
                }
            }
        }

        SectionDivider()

        // ━━━━━ 4. 镜像翻转 ━━━━━
        SectionLabel(stringResource(R.string.mirror))
        Spacer(Modifier.height(4.dp))

        MirrorRow(
            label = stringResource(R.string.mirror_horizontal),
            checked = adjustment.mirrorHorizontal,
            onCheckedChange = {
                onAdjustmentChange(adjustment.copy(mirrorHorizontal = it))
            }
        )

        MirrorRow(
            label = stringResource(R.string.mirror_vertical),
            checked = adjustment.mirrorVertical,
            onCheckedChange = {
                onAdjustmentChange(adjustment.copy(mirrorVertical = it))
            }
        )

        SectionDivider()

        // ━━━━━ 5. 色彩调整 ━━━━━
        SectionLabel(stringResource(R.string.color_adjust))
        Spacer(Modifier.height(4.dp))

        AdjustSlider(
            label = stringResource(R.string.brightness),
            value = adjustment.brightness,
            onValueChange = {
                onAdjustmentChange(adjustment.copy(brightness = it))
            }
        )

        AdjustSlider(
            label = stringResource(R.string.contrast),
            value = adjustment.contrast,
            onValueChange = {
                onAdjustmentChange(adjustment.copy(contrast = it))
            }
        )

        AdjustSlider(
            label = stringResource(R.string.saturation),
            value = adjustment.saturation,
            onValueChange = {
                onAdjustmentChange(adjustment.copy(saturation = it))
            }
        )
    }
}

// ━━━━━ 通用组件（保持原有）━━━━━

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
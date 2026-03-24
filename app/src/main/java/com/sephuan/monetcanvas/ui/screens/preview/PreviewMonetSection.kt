package com.sephuan.monetcanvas.ui.screens.preview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.sephuan.monetcanvas.data.db.WallpaperEntity
import com.sephuan.monetcanvas.data.model.ColorRegion
import com.sephuan.monetcanvas.data.model.FramePickPosition
import com.sephuan.monetcanvas.data.model.MonetRule
import com.sephuan.monetcanvas.data.model.TonePreference
import com.sephuan.monetcanvas.data.model.WallpaperType
import com.sephuan.monetcanvas.util.ExtractedColors

@Composable
fun PreviewMonetSection(
    wallpaper: WallpaperEntity,
    currentRule: MonetRule?,
    extractedColors: ExtractedColors?,
    isAnalyzing: Boolean,
    onConfigClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Palette,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = if (wallpaper.type == WallpaperType.LIVE) {
                        stringResource(R.string.color_preview_live)
                    } else {
                        stringResource(R.string.color_preview_static)
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onConfigClick) {
                    Text(stringResource(R.string.configure))
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                AnimatedVisibility(
                    visible = isAnalyzing,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(180))
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
                            text = stringResource(R.string.analyzing),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                AnimatedVisibility(
                    visible = !isAnalyzing && extractedColors != null,
                    enter = fadeIn(tween(320)) + expandVertically(tween(320)),
                    exit = fadeOut(tween(160))
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

                AnimatedVisibility(
                    visible = !isAnalyzing && extractedColors == null,
                    enter = fadeIn(tween(220)),
                    exit = fadeOut(tween(160))
                ) {
                    Text(
                        text = stringResource(R.string.extract_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                currentRule?.let { rule ->
                    Spacer(Modifier.height(8.dp))
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
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.monet_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun ColorCircle(
    colorInt: Int,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(colorInt))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                )
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun framePositionLabel(pos: FramePickPosition): String = when (pos) {
    FramePickPosition.FIRST -> stringResource(R.string.frame_first)
    FramePickPosition.MIDDLE -> stringResource(R.string.frame_middle)
    FramePickPosition.LAST -> stringResource(R.string.frame_last)
    FramePickPosition.RANDOM -> stringResource(R.string.frame_random)
}

@Composable
fun colorRegionLabel(region: ColorRegion): String = when (region) {
    ColorRegion.FULL_FRAME -> stringResource(R.string.region_full)
    ColorRegion.CENTER -> stringResource(R.string.region_center)
    ColorRegion.TOP_HALF -> stringResource(R.string.region_top)
    ColorRegion.BOTTOM_HALF -> stringResource(R.string.region_bottom)
    ColorRegion.CUSTOM -> "Custom"
}

@Composable
fun tonePreferenceLabel(tone: TonePreference): String = when (tone) {
    TonePreference.AUTO -> stringResource(R.string.tone_auto)
    TonePreference.VIBRANT -> stringResource(R.string.tone_vibrant)
    TonePreference.MUTED -> stringResource(R.string.tone_muted)
    TonePreference.DOMINANT -> stringResource(R.string.tone_dominant)
    TonePreference.DARK_PREFERRED -> stringResource(R.string.tone_dark)
    TonePreference.LIGHT_PREFERRED -> stringResource(R.string.tone_light)
}

@Composable
fun buildRuleDescription(
    rule: MonetRule,
    isLive: Boolean
): String {
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
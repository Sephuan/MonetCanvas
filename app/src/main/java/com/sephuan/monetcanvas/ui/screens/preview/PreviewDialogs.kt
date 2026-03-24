package com.sephuan.monetcanvas.ui.screens.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sephuan.monetcanvas.R
import com.sephuan.monetcanvas.data.model.ColorRegion
import com.sephuan.monetcanvas.data.model.FramePickPosition
import com.sephuan.monetcanvas.data.model.MonetRule
import com.sephuan.monetcanvas.data.model.TonePreference

@Composable
fun ApplyWallpaperDialog(
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
                    Button(
                        onClick = onApplyLive,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.set_live_wallpaper))
                    }
                } else {
                    Button(
                        onClick = onApplyHome,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.apply_home))
                    }

                    Button(
                        onClick = onApplyLock,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.apply_lock))
                    }

                    Button(
                        onClick = onApplyBoth,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.apply_both))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun DeleteWallpaperDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_wallpaper)) },
        text = { Text(stringResource(R.string.delete_confirm)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun LiveWallpaperFailedDialog(
    onDismiss: () -> Unit,
    onGoSettings: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
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
                    onClick = onGoSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Settings, null, Modifier.size(18.dp))
                    Text(" " + stringResource(R.string.live_wp_perm_go_settings))
                }

                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Wallpaper, null, Modifier.size(18.dp))
                    Text(" " + stringResource(R.string.live_wp_perm_retry))
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MonetRuleBottomSheet(
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
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.color_rules),
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(Modifier.height(16.dp))

            if (isLiveWallpaper) {
                Text(
                    text = stringResource(R.string.frame_position),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.frame_position_desc),
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
                            Text(text = framePositionLabel(pos))
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            Text(
                text = stringResource(R.string.color_region),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ColorRegion.entries
                    .filter { it != ColorRegion.CUSTOM }
                    .forEach { region ->
                        FilterChip(
                            selected = colorRegion == region,
                            onClick = { colorRegion = region },
                            label = { Text(text = colorRegionLabel(region)) }
                        )
                    }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text(
                text = stringResource(R.string.tone_preference),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TonePreference.entries.forEach { tone ->
                    FilterChip(
                        selected = tonePreference == tone,
                        onClick = { tonePreference = tone },
                        label = { Text(text = tonePreferenceLabel(tone)) }
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
            ) {
                Text(text = stringResource(R.string.save_and_analyze))
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}
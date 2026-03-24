package com.sephuan.monetcanvas

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sephuan.monetcanvas.data.datastore.SettingsDataStore
import com.sephuan.monetcanvas.ui.navigation.MonetNavGraph
import com.sephuan.monetcanvas.ui.theme.MonetCanvasTheme
import com.sephuan.monetcanvas.util.LiveWallpaperSetter
import com.sephuan.monetcanvas.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME = "app_first_launch"
        private const val KEY_FIRST_LAUNCH = "is_first_launch"

        private const val STEP_NONE = 0
        private const val STEP_WELCOME = 1
        private const val STEP_NOTIFICATION = 2
        private const val STEP_LIVE_WP = 3
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            val settings = remember { SettingsDataStore(applicationContext) }
            val context = LocalContext.current

            val appSeedColor by settings.appSeedColorFlow.collectAsStateWithLifecycle(initialValue = null)
            val appColorMode by settings.appColorModeFlow.collectAsStateWithLifecycle(
                initialValue = SettingsDataStore.COLOR_MODE_MONET
            )
            val appCustomColor by settings.appCustomColorFlow.collectAsStateWithLifecycle(initialValue = null)
            val darkMode by settings.darkModeFlow.collectAsStateWithLifecycle(
                initialValue = SettingsDataStore.DARK_MODE_SYSTEM
            )

            // ★ 不再在这里调用 SystemBarsEffect
            //   所有系统栏设置已经集中到 Theme.kt 的 SideEffect 中

            MonetCanvasTheme(
                appSeedColor = appSeedColor,
                appCustomColor = appCustomColor,
                appColorMode = appColorMode,
                darkModeSetting = darkMode
            ) {
                var onboardingStep by remember {
                    mutableIntStateOf(
                        if (isFirstLaunch(context)) STEP_WELCOME else STEP_NONE
                    )
                }

                val notifPermLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) {
                    onboardingStep = STEP_LIVE_WP
                }

                if (onboardingStep == STEP_WELCOME) {
                    WelcomeDialog(
                        onDismiss = {
                            markFirstLaunchDone(context)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                                onboardingStep = if (granted) STEP_LIVE_WP else STEP_NOTIFICATION
                            } else {
                                onboardingStep = STEP_LIVE_WP
                            }
                        }
                    )
                }

                if (onboardingStep == STEP_NOTIFICATION) {
                    NotificationPermissionDialog(
                        onAllow = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onSkip = {
                            onboardingStep = STEP_LIVE_WP
                        }
                    )
                }

                if (onboardingStep == STEP_LIVE_WP) {
                    val isActive = remember {
                        LiveWallpaperSetter.isOurLiveWallpaperActive(context)
                    }

                    if (isActive) {
                        onboardingStep = STEP_NONE
                    } else {
                        LiveWallpaperGuideDialog(
                            onGoSettings = {
                                LiveWallpaperSetter.openAppSettings(context)
                                onboardingStep = STEP_NONE
                            },
                            onTryActivate = {
                                LiveWallpaperSetter.tryActivate(context)
                                onboardingStep = STEP_NONE
                            },
                            onSkip = {
                                onboardingStep = STEP_NONE
                            }
                        )
                    }
                }

                MonetNavGraph()
            }
        }
    }

    private fun isFirstLaunch(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(KEY_FIRST_LAUNCH, true)
    }

    private fun markFirstLaunchDone(context: Context) {
        context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FIRST_LAUNCH, false)
            .apply()
    }
}

@Composable
private fun WelcomeDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.welcome_subtitle),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                FeatureRow(Icons.Outlined.Palette, stringResource(R.string.welcome_feature_1))
                FeatureRow(Icons.Outlined.LiveTv, stringResource(R.string.welcome_feature_2))
                FeatureRow(Icons.Outlined.Storage, stringResource(R.string.welcome_feature_3))
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.welcome_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.welcome_start))
            }
        }
    )
}

@Composable
private fun NotificationPermissionDialog(
    onAllow: () -> Unit,
    onSkip: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onSkip,
        icon = {
            Icon(
                Icons.Outlined.Notifications,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(stringResource(R.string.notif_perm_title)) },
        text = { Text(stringResource(R.string.notif_perm_desc)) },
        confirmButton = {
            Button(onClick = onAllow) {
                Text(stringResource(R.string.notif_perm_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.notif_perm_skip))
            }
        }
    )
}

@Composable
private fun LiveWallpaperGuideDialog(
    onGoSettings: () -> Unit,
    onTryActivate: () -> Unit,
    onSkip: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onSkip,
        icon = {
            Icon(
                Icons.Outlined.Wallpaper,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(stringResource(R.string.live_wp_setup_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.live_wp_setup_desc))
                Text(
                    stringResource(R.string.live_wp_setup_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onTryActivate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Wallpaper, null, Modifier.size(18.dp))
                    Text(" " + stringResource(R.string.live_wp_setup_enable))
                }

                OutlinedButton(
                    onClick = onGoSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Settings, null, Modifier.size(18.dp))
                    Text(" " + stringResource(R.string.live_wp_perm_go_settings))
                }

                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.live_wp_setup_later))
                }
            }
        }
    )
}

@Composable
private fun FeatureRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.size(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
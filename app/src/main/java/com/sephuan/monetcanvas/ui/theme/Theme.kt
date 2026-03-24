package com.sephuan.monetcanvas.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.sephuan.monetcanvas.data.datastore.SettingsDataStore

data class PresetColor(
    val name: String,
    val colorInt: Int,
    val color: Color
)

val PRESET_COLORS = listOf(
    PresetColor("樱花粉", 0xFFE91E63.toInt(), Color(0xFFE91E63)),
    PresetColor("天空蓝", 0xFF2196F3.toInt(), Color(0xFF2196F3)),
    PresetColor("薄荷绿", 0xFF4CAF50.toInt(), Color(0xFF4CAF50)),
    PresetColor("琥珀橙", 0xFFFF9800.toInt(), Color(0xFFFF9800)),
    PresetColor("薰衣紫", 0xFF9C27B0.toInt(), Color(0xFF9C27B0)),
    PresetColor("石墨黑", 0xFF607D8B.toInt(), Color(0xFF607D8B)),
    PresetColor("珊瑚红", 0xFFFF5722.toInt(), Color(0xFFFF5722)),
    PresetColor("青碧色", 0xFF009688.toInt(), Color(0xFF009688)),
    PresetColor("靛蓝色", 0xFF3F51B5.toInt(), Color(0xFF3F51B5)),
    PresetColor("柠檬黄", 0xFFCDDC39.toInt(), Color(0xFFCDDC39))
)

private fun generateSchemeFromSeed(seedColor: Int, isDark: Boolean): ColorScheme {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seedColor, hsv)
    val hue = hsv[0]
    val sat = hsv[1]
    val hue2 = (hue + 28f) % 360f
    val hue3 = (hue + 330f) % 360f

    fun c(h: Float, s: Float, v: Float): Color {
        return Color(
            android.graphics.Color.HSVToColor(
                floatArrayOf(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
            )
        )
    }

    return if (isDark) {
        val bg = Color(0xFF101114)
        val sf = Color(0xFF15171B)
        darkColorScheme(
            primary = c(hue, sat * 0.75f, 0.82f),
            onPrimary = Color(0xFF101114),
            primaryContainer = c(hue, sat * 0.45f, 0.30f),
            onPrimaryContainer = c(hue, sat * 0.18f, 0.96f),
            secondary = c(hue2, sat * 0.55f, 0.76f),
            onSecondary = Color(0xFF101114),
            secondaryContainer = c(hue2, sat * 0.28f, 0.26f),
            onSecondaryContainer = c(hue2, sat * 0.15f, 0.93f),
            tertiary = c(hue3, sat * 0.50f, 0.76f),
            onTertiary = Color(0xFF101114),
            tertiaryContainer = c(hue3, sat * 0.25f, 0.26f),
            onTertiaryContainer = c(hue3, sat * 0.15f, 0.93f),
            background = bg,
            onBackground = Color(0xFFE7EAF0),
            surface = sf,
            onSurface = Color(0xFFE7EAF0),
            surfaceVariant = Color(0xFF23262D),
            onSurfaceVariant = Color(0xFFC3C7D0),
            surfaceContainer = sf,
            surfaceContainerLow = Color(0xFF121418),
            surfaceContainerHigh = Color(0xFF1C1F25),
            surfaceContainerHighest = Color(0xFF252932),
            outline = Color(0xFF8B909A),
            outlineVariant = Color(0xFF3A3E47)
        )
    } else {
        val bg = Color(0xFFF7F8FC)
        val sf = Color(0xFFFCFCFF)
        lightColorScheme(
            primary = c(hue, sat * 0.82f, 0.55f),
            onPrimary = Color.White,
            primaryContainer = c(hue, sat * 0.22f, 0.95f),
            onPrimaryContainer = c(hue, sat * 0.75f, 0.22f),
            secondary = c(hue2, sat * 0.55f, 0.52f),
            onSecondary = Color.White,
            secondaryContainer = c(hue2, sat * 0.18f, 0.94f),
            onSecondaryContainer = c(hue2, sat * 0.55f, 0.22f),
            tertiary = c(hue3, sat * 0.52f, 0.52f),
            onTertiary = Color.White,
            tertiaryContainer = c(hue3, sat * 0.18f, 0.94f),
            onTertiaryContainer = c(hue3, sat * 0.52f, 0.22f),
            background = bg,
            onBackground = Color(0xFF171C24),
            surface = sf,
            onSurface = Color(0xFF171C24),
            surfaceVariant = Color(0xFFE6E8F0),
            onSurfaceVariant = Color(0xFF464B57),
            surfaceContainer = sf,
            surfaceContainerLow = Color(0xFFF4F6FA),
            surfaceContainerHigh = Color(0xFFEEF1F7),
            surfaceContainerHighest = Color(0xFFE7EBF3),
            outline = Color(0xFF757B87),
            outlineVariant = Color(0xFFC7CCD6)
        )
    }
}

private fun fallbackScheme(isDark: Boolean): ColorScheme {
    return if (isDark) {
        darkColorScheme(
            primary = Color(0xFFAAC7FF),
            onPrimary = Color(0xFF0A305F),
            primaryContainer = Color(0xFF224777),
            onPrimaryContainer = Color(0xFFD6E3FF),
            secondary = Color(0xFFBEC6DC),
            onSecondary = Color(0xFF283141),
            secondaryContainer = Color(0xFF3E4758),
            onSecondaryContainer = Color(0xFFDAE2F9),
            tertiary = Color(0xFFDDBCE0),
            onTertiary = Color(0xFF402843),
            tertiaryContainer = Color(0xFF583E5A),
            onTertiaryContainer = Color(0xFFFAD8FD),
            background = Color(0xFF101114),
            onBackground = Color(0xFFE7EAF0),
            surface = Color(0xFF15171B),
            onSurface = Color(0xFFE7EAF0),
            surfaceVariant = Color(0xFF23262D),
            onSurfaceVariant = Color(0xFFC3C7D0),
            surfaceContainer = Color(0xFF15171B),
            surfaceContainerLow = Color(0xFF121418),
            surfaceContainerHigh = Color(0xFF1C1F25),
            surfaceContainerHighest = Color(0xFF252932),
            outline = Color(0xFF8B909A),
            outlineVariant = Color(0xFF3A3E47)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF3E63DD),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDEE7FF),
            onPrimaryContainer = Color(0xFF001A41),
            secondary = Color(0xFF566177),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFD9E2F9),
            onSecondaryContainer = Color(0xFF131C2B),
            tertiary = Color(0xFF705574),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFAD8FD),
            onTertiaryContainer = Color(0xFF29132E),
            background = Color(0xFFF7F8FC),
            onBackground = Color(0xFF171C24),
            surface = Color(0xFFFCFCFF),
            onSurface = Color(0xFF171C24),
            surfaceVariant = Color(0xFFE6E8F0),
            onSurfaceVariant = Color(0xFF464B57),
            surfaceContainer = Color(0xFFFCFCFF),
            surfaceContainerLow = Color(0xFFF4F6FA),
            surfaceContainerHigh = Color(0xFFEEF1F7),
            surfaceContainerHighest = Color(0xFFE7EBF3),
            outline = Color(0xFF757B87),
            outlineVariant = Color(0xFFC7CCD6)
        )
    }
}

@Composable
fun MonetCanvasTheme(
    appSeedColor: Int? = null,
    appCustomColor: Int? = null,
    appColorMode: String = SettingsDataStore.COLOR_MODE_MONET,
    darkModeSetting: String = SettingsDataStore.DARK_MODE_SYSTEM,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val isDark = when (darkModeSetting) {
        SettingsDataStore.DARK_MODE_LIGHT -> false
        SettingsDataStore.DARK_MODE_DARK -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = when (appColorMode) {
        SettingsDataStore.COLOR_MODE_CUSTOM -> {
            val seed = appCustomColor ?: PRESET_COLORS.first().colorInt
            generateSchemeFromSeed(seed, isDark)
        }
        SettingsDataStore.COLOR_MODE_RULE -> {
            if (appSeedColor != null) {
                generateSchemeFromSeed(appSeedColor, isDark)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                fallbackScheme(isDark)
            }
        }
        else -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                fallbackScheme(isDark)
            }
        }
    }

    val bgArgb = colorScheme.background.toArgb()

    DisposableEffect(bgArgb, isDark) {
        val activity = context as? Activity
        activity?.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(bgArgb))
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT

            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
        onDispose { }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colorScheme.background
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.background)
            ) {
                content()
            }
        }
    }
}
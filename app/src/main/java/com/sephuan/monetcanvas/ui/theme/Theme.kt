package com.sephuan.monetcanvas.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.sephuan.monetcanvas.data.datastore.SettingsDataStore

/**
 * 预设颜色方案（用户可在设置里选）
 */
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

    val hue2 = (hue + 30f) % 360f
    val hue3 = (hue + 330f) % 360f

    fun c(h: Float, s: Float, v: Float): Color {
        val color = android.graphics.Color.HSVToColor(floatArrayOf(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))
        return Color(color)
    }

    return if (isDark) {
        darkColorScheme(
            primary = c(hue, sat, 0.75f),
            onPrimary = c(hue, sat * 0.3f, 0.1f),
            primaryContainer = c(hue, sat * 0.5f, 0.25f),
            onPrimaryContainer = c(hue, sat * 0.3f, 0.9f),

            secondary = c(hue2, sat * 0.6f, 0.7f),
            onSecondary = c(hue2, sat * 0.3f, 0.1f),
            secondaryContainer = c(hue2, sat * 0.3f, 0.2f),
            onSecondaryContainer = c(hue2, sat * 0.3f, 0.85f),

            tertiary = c(hue3, sat * 0.6f, 0.7f),
            onTertiary = c(hue3, sat * 0.3f, 0.1f),
            tertiaryContainer = c(hue3, sat * 0.3f, 0.2f),
            onTertiaryContainer = c(hue3, sat * 0.3f, 0.85f),

            background = Color(0xFF121212),
            onBackground = Color(0xFFE0E0E0),
            surface = Color(0xFF1E1E1E),
            onSurface = Color(0xFFE0E0E0),
            surfaceVariant = Color(0xFF2C2C2C),
            onSurfaceVariant = Color(0xFFCACACA),
            surfaceContainerHigh = Color(0xFF2A2A2A),
            outline = Color(0xFF888888),
            outlineVariant = Color(0xFF444444)
        )
    } else {
        lightColorScheme(
            primary = c(hue, sat, 0.45f),
            onPrimary = Color.White,
            primaryContainer = c(hue, sat * 0.4f, 0.92f),
            onPrimaryContainer = c(hue, sat, 0.15f),

            secondary = c(hue2, sat * 0.5f, 0.45f),
            onSecondary = Color.White,
            secondaryContainer = c(hue2, sat * 0.25f, 0.92f),
            onSecondaryContainer = c(hue2, sat * 0.5f, 0.15f),

            tertiary = c(hue3, sat * 0.5f, 0.45f),
            onTertiary = Color.White,
            tertiaryContainer = c(hue3, sat * 0.25f, 0.92f),
            onTertiaryContainer = c(hue3, sat * 0.5f, 0.15f),

            background = Color(0xFFFFFBFE),
            onBackground = Color(0xFF1C1B1F),
            surface = Color(0xFFFFFBFE),
            onSurface = Color(0xFF1C1B1F),
            surfaceVariant = Color(0xFFE7E0EC),
            onSurfaceVariant = Color(0xFF49454F),
            surfaceContainerHigh = Color(0xFFF0F0F0),
            outline = Color(0xFF79747E),
            outlineVariant = Color(0xFFCAC4D0)
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

    val colorScheme: ColorScheme = when (appColorMode) {
        // 用户手选颜色
        SettingsDataStore.COLOR_MODE_CUSTOM -> {
            val seed = appCustomColor ?: PRESET_COLORS[0].colorInt
            generateSchemeFromSeed(seed, isDark)
        }

        // 按取色规则
        SettingsDataStore.COLOR_MODE_RULE -> {
            if (appSeedColor != null) {
                generateSchemeFromSeed(appSeedColor, isDark)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (isDark) darkColorScheme() else lightColorScheme()
            }
        }

        // Monet 跟随系统
        else -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (isDark) darkColorScheme() else lightColorScheme()
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
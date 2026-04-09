package com.sephuan.monetcanvas.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sephuan.monetcanvas.data.model.ColorRegion
import com.sephuan.monetcanvas.data.model.FilterType
import com.sephuan.monetcanvas.data.model.FramePickPosition
import com.sephuan.monetcanvas.data.model.GridSize
import com.sephuan.monetcanvas.data.model.MonetRule
import com.sephuan.monetcanvas.data.model.TonePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val SETTINGS_NAME = "settings"
val Context.settingsDataStore by preferencesDataStore(name = SETTINGS_NAME)

class SettingsDataStore(private val context: Context) {

    companion object {
        // 配色模式
        const val COLOR_MODE_MONET = "MONET"       // 跟随系统 Monet
        const val COLOR_MODE_RULE = "RULE"          // 按取色规则
        const val COLOR_MODE_CUSTOM = "CUSTOM"      // 用户手选颜色

        // 暗色模式
        const val DARK_MODE_SYSTEM = "SYSTEM"       // 跟随系统
        const val DARK_MODE_LIGHT = "LIGHT"         // 始终亮色
        const val DARK_MODE_DARK = "DARK"           // 始终暗色

        const val DEFAULT_STORAGE_DISPLAY = "应用私有目录（默认）"
    }

    private object Keys {
        val FRAME_POS = stringPreferencesKey("frame_position")
        val COLOR_REGION = stringPreferencesKey("color_region")
        val TONE = stringPreferencesKey("tone_preference")
        val MANUAL_COLOR = intPreferencesKey("manual_color")

        val GRID_SIZE = stringPreferencesKey("grid_size")
        val FILTER_TYPE = stringPreferencesKey("filter_type")

        val APP_SEED_COLOR = intPreferencesKey("app_seed_color")
        val APP_COLOR_MODE = stringPreferencesKey("app_color_mode")
        val APP_CUSTOM_COLOR = intPreferencesKey("app_custom_color")

        val DARK_MODE = stringPreferencesKey("dark_mode")

        val STORAGE_TREE_URI = stringPreferencesKey("storage_tree_uri")

        // ★ 新增：字体大小缩放比例
        val FONT_SCALE = floatPreferencesKey("font_scale")
    }

    // ━━━━━ Monet 取色规则 ━━━━━

    val monetRuleFlow: Flow<MonetRule> = context.settingsDataStore.data.map { prefs ->
        MonetRule(
            framePosition = prefs[Keys.FRAME_POS]?.let { safeEnum(FramePickPosition.FIRST, it) }
                ?: FramePickPosition.FIRST,
            colorRegion = prefs[Keys.COLOR_REGION]?.let { safeEnum(ColorRegion.FULL_FRAME, it) }
                ?: ColorRegion.FULL_FRAME,
            tonePreference = prefs[Keys.TONE]?.let { safeEnum(TonePreference.AUTO, it) }
                ?: TonePreference.AUTO,
            manualOverrideColor = prefs[Keys.MANUAL_COLOR]
        )
    }

    suspend fun saveMonetRule(rule: MonetRule) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.FRAME_POS] = rule.framePosition.name
            prefs[Keys.COLOR_REGION] = rule.colorRegion.name
            prefs[Keys.TONE] = rule.tonePreference.name
            if (rule.manualOverrideColor != null) prefs[Keys.MANUAL_COLOR] = rule.manualOverrideColor
            else prefs.remove(Keys.MANUAL_COLOR)
        }
    }

    // ━━━━━ 显示 ━━━━━

    val gridSizeFlow: Flow<GridSize> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.GRID_SIZE]?.let { safeEnum(GridSize.MEDIUM, it) } ?: GridSize.MEDIUM
    }

    suspend fun saveGridSize(size: GridSize) {
        context.settingsDataStore.edit { it[Keys.GRID_SIZE] = size.name }
    }

    val filterTypeFlow: Flow<FilterType> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.FILTER_TYPE]?.let { safeEnum(FilterType.ALL, it) } ?: FilterType.ALL
    }

    suspend fun saveFilterType(filter: FilterType) {
        context.settingsDataStore.edit { it[Keys.FILTER_TYPE] = filter.name }
    }

    // ━━━━━ App 配色 ━━━━━

    val appColorModeFlow: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.APP_COLOR_MODE] ?: COLOR_MODE_MONET
    }

    suspend fun saveAppColorMode(mode: String) {
        context.settingsDataStore.edit { it[Keys.APP_COLOR_MODE] = mode }
    }

    val appSeedColorFlow: Flow<Int?> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.APP_SEED_COLOR]
    }

    suspend fun saveAppSeedColor(color: Int?) {
        context.settingsDataStore.edit { prefs ->
            if (color == null) prefs.remove(Keys.APP_SEED_COLOR) else prefs[Keys.APP_SEED_COLOR] = color
        }
    }

    val appCustomColorFlow: Flow<Int?> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.APP_CUSTOM_COLOR]
    }

    suspend fun saveAppCustomColor(color: Int) {
        context.settingsDataStore.edit { it[Keys.APP_CUSTOM_COLOR] = color }
    }

    // ━━━━━ 暗色模式 ━━━━━

    val darkModeFlow: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.DARK_MODE] ?: DARK_MODE_SYSTEM
    }

    suspend fun saveDarkMode(mode: String) {
        context.settingsDataStore.edit { it[Keys.DARK_MODE] = mode }
    }

    // ━━━━━ 字体缩放 (新增) ━━━━━

    val fontScaleFlow: Flow<Float> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.FONT_SCALE] ?: 1.0f
    }

    suspend fun saveFontScale(scale: Float) {
        context.settingsDataStore.edit { it[Keys.FONT_SCALE] = scale }
    }

    // ━━━━━ 存储路径 ━━━━━

    val storageTreeUriFlow: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.STORAGE_TREE_URI]
    }

    suspend fun saveStorageTreeUri(uri: String?) {
        context.settingsDataStore.edit { prefs ->
            if (uri.isNullOrBlank()) prefs.remove(Keys.STORAGE_TREE_URI) else prefs[Keys.STORAGE_TREE_URI] = uri
        }
    }

    private inline fun <reified T : Enum<T>> safeEnum(default: T, value: String): T {
        return runCatching { enumValueOf<T>(value) }.getOrDefault(default)
    }
}
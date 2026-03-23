package com.sephuan.monetcanvas.util

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {

    private const val PREFS_NAME = "language_prefs"
    private const val KEY_LANGUAGE = "app_language"
    private const val KEY_SYSTEM_LANG = "real_system_language"
    private const val KEY_SYSTEM_COUNTRY = "real_system_country"

    const val LANG_SYSTEM = "system"
    const val LANG_ZH = "zh"
    const val LANG_EN = "en"

    /**
     * ★ 必须在 Application.onCreate() 中调用一次
     *   在任何 Locale.setDefault() 之前，保存真正的系统语言
     */
    fun saveSystemLocale(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // 只在第一次启动或系统语言变化时保存
        val systemLocale = LocaleList.getDefault().get(0) ?: Locale.getDefault()
        prefs.edit()
            .putString(KEY_SYSTEM_LANG, systemLocale.language)
            .putString(KEY_SYSTEM_COUNTRY, systemLocale.country)
            .apply()
    }

    /**
     * 获取保存的真正系统 Locale
     */
    private fun getRealSystemLocale(context: Context): Locale {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lang = prefs.getString(KEY_SYSTEM_LANG, null)
        val country = prefs.getString(KEY_SYSTEM_COUNTRY, null)
        return if (lang != null) Locale(lang, country ?: "") else Locale.getDefault()
    }

    fun getLanguage(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, LANG_SYSTEM) ?: LANG_SYSTEM
    }

    fun setLanguage(context: Context, language: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language)
            .apply()
    }

    /**
     * 用指定语言包装 Context
     * ★ "跟随系统"时使用保存的真实系统 Locale，不受 Locale.setDefault() 污染
     */
    fun wrap(context: Context, language: String? = null): Context {
        val lang = language ?: getLanguage(context)

        val locale = when (lang) {
            LANG_ZH -> Locale.SIMPLIFIED_CHINESE
            LANG_EN -> Locale.ENGLISH
            else -> getRealSystemLocale(context)  // ★ 用保存的真实系统语言
        }

        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        return context.createConfigurationContext(config)
    }
}
package com.sephuan.monetcanvas

import android.app.Application
import com.sephuan.monetcanvas.util.LocaleHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MonetCanvasApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // ★ 在任何 Locale.setDefault() 之前，保存真正的系统语言
        LocaleHelper.saveSystemLocale(this)
    }
}
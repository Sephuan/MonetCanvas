package com.sephuan.monetcanvas.util

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.sephuan.monetcanvas.service.LiveWallpaperService
import java.io.File

object LiveWallpaperSetter {

    private const val TAG = "LiveWallpaperSetter"

    // 常量定义，与 LiveWallpaperService 保持一致
    const val PREFS_NAME = "wallpaper_prefs"
    const val KEY_LIVE_PATH = "live_wallpaper_path"
    const val KEY_PENDING_PATH = "pending_live_path"

    fun isOurLiveWallpaperActive(context: Context): Boolean {
        return try {
            val info = WallpaperManager.getInstance(context).wallpaperInfo ?: return false
            info.packageName == context.packageName &&
                    info.serviceName == LiveWallpaperService::class.java.name
        } catch (e: Exception) {
            Log.e(TAG, "检查服务状态失败", e)
            false
        }
    }

    fun savePendingConfig(
        context: Context,
        videoPath: String,
        framePosition: String = "FIRST",
        colorRegion: String = "FULL_FRAME",
        tonePreference: String = "AUTO"
    ): Boolean {
        val file = File(videoPath)
        if (!file.exists() || file.length() <= 0L) {
            Log.e(TAG, "视频文件无效: $videoPath")
            return false
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val currentVersion = prefs.getLong(LiveWallpaperService.KEY_PENDING_VERSION, 0L)

        prefs.edit()
            .putString(LiveWallpaperService.KEY_PENDING_PATH, videoPath)
            .putString(LiveWallpaperService.KEY_PENDING_FRAME, framePosition)
            .putString(LiveWallpaperService.KEY_PENDING_REGION, colorRegion)
            .putString(LiveWallpaperService.KEY_PENDING_TONE, tonePreference)
            .putLong(LiveWallpaperService.KEY_PENDING_VERSION, currentVersion + 1)
            .apply()

        Log.d(TAG, "保存预览配置: path=$videoPath, version=${currentVersion + 1}")
        return true
    }

    fun promotePendingToActive(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val pendingPath = prefs.getString(LiveWallpaperService.KEY_PENDING_PATH, null)
        val pendingFrame = prefs.getString(LiveWallpaperService.KEY_PENDING_FRAME, "FIRST")
        val pendingRegion = prefs.getString(LiveWallpaperService.KEY_PENDING_REGION, "FULL_FRAME")
        val pendingTone = prefs.getString(LiveWallpaperService.KEY_PENDING_TONE, "AUTO")

        if (pendingPath.isNullOrBlank()) {
            Log.e(TAG, "没有待提升的预览配置")
            return
        }

        val currentVersion = prefs.getLong(LiveWallpaperService.KEY_CONFIG_VERSION, 0L)
        val newVersion = currentVersion + 1

        prefs.edit()
            .putString(LiveWallpaperService.KEY_LIVE_PATH, pendingPath)
            .putString(LiveWallpaperService.KEY_FRAME_POSITION, pendingFrame)
            .putString(LiveWallpaperService.KEY_COLOR_REGION, pendingRegion)
            .putString(LiveWallpaperService.KEY_TONE_PREFERENCE, pendingTone)
            .putLong(LiveWallpaperService.KEY_CONFIG_VERSION, newVersion)
            .apply()

        Log.d(TAG, "★ 预览配置已提升为正式配置: path=$pendingPath, version=$newVersion")
    }

    fun hasPendingConfig(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getString(LiveWallpaperService.KEY_PENDING_PATH, null).isNullOrBlank()
    }

    fun clearPendingConfig(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(LiveWallpaperService.KEY_PENDING_PATH)
            .remove(LiveWallpaperService.KEY_PENDING_FRAME)
            .remove(LiveWallpaperService.KEY_PENDING_REGION)
            .remove(LiveWallpaperService.KEY_PENDING_TONE)
            .apply()
        Log.d(TAG, "预览配置已清除")
    }

    fun createActivationIntent(context: Context): Intent {
        val component = ComponentName(context, LiveWallpaperService::class.java)
        return Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        }
    }

    fun tryActivate(context: Context): Boolean {
        val activity = context as? Activity
        if (activity == null) {
            Log.e(TAG, "tryActivate 需要 Activity context")
            return false
        }

        return try {
            activity.startActivity(createActivationIntent(context))
            Log.d(TAG, "✓ ACTION_CHANGE_LIVE_WALLPAPER 启动成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "✗ ACTION_CHANGE_LIVE_WALLPAPER 启动失败", e)
            false
        }
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "跳转应用设置失败", e)
        }
    }
}
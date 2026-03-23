package com.sephuan.monetcanvas.util

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

    /**
     * ★ 第1步：保存到【预览配置】
     *   桌面实例不会响应这些 key，所以桌面不会变化！
     *   只有系统确认页的预览实例会读取这些配置
     */
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

        val prefs = context.getSharedPreferences(
            LiveWallpaperService.PREFS_NAME,
            Context.MODE_PRIVATE
        )

        val currentVersion = prefs.getLong(LiveWallpaperService.KEY_PENDING_VERSION, 0L)

        val success = prefs.edit()
            .putString(LiveWallpaperService.KEY_PENDING_PATH, videoPath)
            .putString(LiveWallpaperService.KEY_PENDING_FRAME, framePosition)
            .putString(LiveWallpaperService.KEY_PENDING_REGION, colorRegion)
            .putString(LiveWallpaperService.KEY_PENDING_TONE, tonePreference)
            .putLong(LiveWallpaperService.KEY_PENDING_VERSION, currentVersion + 1)
            .commit()

        Log.d(TAG, "保存预览配置: path=$videoPath, version=${currentVersion + 1}")
        return success
    }

    /**
     * ★ 第2步（用户确认后调用）：把预览配置提升为正式配置
     *   此时桌面实例才会响应，重新加载视频 + 取色
     */
    fun promotePendingToActive(context: Context) {
        val prefs = context.getSharedPreferences(
            LiveWallpaperService.PREFS_NAME,
            Context.MODE_PRIVATE
        )

        val pendingPath = prefs.getString(LiveWallpaperService.KEY_PENDING_PATH, null)
        val pendingFrame = prefs.getString(LiveWallpaperService.KEY_PENDING_FRAME, "FIRST")
        val pendingRegion = prefs.getString(LiveWallpaperService.KEY_PENDING_REGION, "FULL_FRAME")
        val pendingTone = prefs.getString(LiveWallpaperService.KEY_PENDING_TONE, "AUTO")

        if (pendingPath.isNullOrBlank()) {
            Log.e(TAG, "没有待提升的预览配置")
            return
        }

        val currentVersion = prefs.getLong(LiveWallpaperService.KEY_CONFIG_VERSION, 0L)

        prefs.edit()
            .putString(LiveWallpaperService.KEY_LIVE_PATH, pendingPath)
            .putString(LiveWallpaperService.KEY_FRAME_POSITION, pendingFrame)
            .putString(LiveWallpaperService.KEY_COLOR_REGION, pendingRegion)
            .putString(LiveWallpaperService.KEY_TONE_PREFERENCE, pendingTone)
            .putLong(LiveWallpaperService.KEY_CONFIG_VERSION, currentVersion + 1)
            .commit()

        Log.d(TAG, "★ 预览配置已提升为正式配置: path=$pendingPath, version=${currentVersion + 1}")
    }

    /**
     * 检查是否有待确认的预览配置
     */
    fun hasPendingConfig(context: Context): Boolean {
        val prefs = context.getSharedPreferences(
            LiveWallpaperService.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        return !prefs.getString(LiveWallpaperService.KEY_PENDING_PATH, null).isNullOrBlank()
    }

    /**
     * 清除预览配置（用户取消或超时）
     */
    fun clearPendingConfig(context: Context) {
        val prefs = context.getSharedPreferences(
            LiveWallpaperService.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        prefs.edit()
            .remove(LiveWallpaperService.KEY_PENDING_PATH)
            .remove(LiveWallpaperService.KEY_PENDING_FRAME)
            .remove(LiveWallpaperService.KEY_PENDING_REGION)
            .remove(LiveWallpaperService.KEY_PENDING_TONE)
            .commit()

        Log.d(TAG, "预览配置已清除")
    }

    /**
     * 打开系统确认页
     */
    fun tryActivate(context: Context): Boolean {
        val component = ComponentName(context, LiveWallpaperService::class.java)

        val directIntent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (safeStart(context, directIntent)) {
            Log.d(TAG, "✓ ACTION_CHANGE_LIVE_WALLPAPER 成功")
            return true
        }

        val chooserIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (safeStart(context, chooserIntent)) {
            Log.d(TAG, "✓ ACTION_LIVE_WALLPAPER_CHOOSER 成功")
            return true
        }

        Log.e(TAG, "✗ 所有 Intent 均失败")
        return false
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "跳转应用设置失败", e)
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) {}
        }
    }

    private fun safeStart(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "启动失败: ${intent.action}", e)
            false
        }
    }
}
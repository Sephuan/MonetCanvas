package com.sephuan.monetcanvas.util

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.sephuan.monetcanvas.service.LiveWallpaperService
import java.io.File

object LiveWallpaperSetter {

    private const val TAG = "LiveWallpaperSetter"
    const val PREFS_NAME = "wallpaper_prefs"

    // ★ 备份旧 active，用于用户取消时恢复
    private const val KEY_ACTIVE_BACKUP_EXISTS = "active_backup_exists"
    private const val KEY_ACTIVE_BACKUP_WAS_PRESENT = "active_backup_was_present"
    private const val KEY_ACTIVE_BACKUP_PATH = "active_backup_path"
    private const val KEY_ACTIVE_BACKUP_FRAME = "active_backup_frame"
    private const val KEY_ACTIVE_BACKUP_REGION = "active_backup_region"
    private const val KEY_ACTIVE_BACKUP_TONE = "active_backup_tone"
    private const val KEY_ACTIVE_BACKUP_VERSION = "active_backup_version"

    // ★ 标记本次是否对 active 做过“预镜像”
    private const val KEY_PREVIEW_MIRRORED_TO_ACTIVE = "preview_mirrored_to_active"

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
     * 保存待确认配置。
     *
     * 关键修复：
     * - 如果当前系统已经是 MonetCanvas 动态壁纸，则在打开系统设置页前，
     *   先把 active 临时镜像成新配置。
     * - 这样系统页创建出来的 active engine 就会直接使用“新壁纸”，
     *   而不是误读旧 active。
     *
     * 这正是“第一次正常、后续动态切换乱掉”的根因修复。
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

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        val pendingVersion = nextVersion(prefs, LiveWallpaperService.KEY_PENDING_VERSION)

        // 1) 写 pending
        editor.putString(LiveWallpaperService.KEY_PENDING_PATH, videoPath)
        editor.putString(LiveWallpaperService.KEY_PENDING_FRAME, framePosition)
        editor.putString(LiveWallpaperService.KEY_PENDING_REGION, colorRegion)
        editor.putString(LiveWallpaperService.KEY_PENDING_TONE, tonePreference)
        editor.putLong(LiveWallpaperService.KEY_PENDING_VERSION, pendingVersion)

        val currentlyOurLive = isOurLiveWallpaperActive(context)

        // 2) 如果当前已经是我们的动态壁纸，则临时镜像 active
        if (currentlyOurLive) {
            snapshotActiveIfNeeded(prefs, editor)

            val activeVersion = nextVersion(prefs, LiveWallpaperService.KEY_CONFIG_VERSION)

            editor.putString(LiveWallpaperService.KEY_LIVE_PATH, videoPath)
            editor.putString(LiveWallpaperService.KEY_FRAME_POSITION, framePosition)
            editor.putString(LiveWallpaperService.KEY_COLOR_REGION, colorRegion)
            editor.putString(LiveWallpaperService.KEY_TONE_PREFERENCE, tonePreference)
            editor.putLong(LiveWallpaperService.KEY_CONFIG_VERSION, activeVersion)

            editor.putBoolean(KEY_PREVIEW_MIRRORED_TO_ACTIVE, true)

            Log.d(
                TAG,
                "保存 pending 并预镜像 active: path=$videoPath, pendingVersion=$pendingVersion, activeVersion=$activeVersion"
            )
        } else {
            editor.putBoolean(KEY_PREVIEW_MIRRORED_TO_ACTIVE, false)
            Log.d(
                TAG,
                "保存 pending（当前不是我们的动态壁纸，不镜像 active）: path=$videoPath, pendingVersion=$pendingVersion"
            )
        }

        val committed = editor.commit()
        if (!committed) {
            Log.e(TAG, "savePendingConfig commit 失败")
        }
        return committed
    }

    /**
     * 正式将 Pending 提升为 Active。
     */
    fun promotePendingToActive(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val pendingPath = prefs.getString(LiveWallpaperService.KEY_PENDING_PATH, null)
        val pendingFrame = prefs.getString(LiveWallpaperService.KEY_PENDING_FRAME, "FIRST")
        val pendingRegion = prefs.getString(LiveWallpaperService.KEY_PENDING_REGION, "FULL_FRAME")
        val pendingTone = prefs.getString(LiveWallpaperService.KEY_PENDING_TONE, "AUTO")

        if (pendingPath.isNullOrBlank()) {
            Log.e(TAG, "提升失败：没有待提升的 Pending 配置")
            return
        }

        val activeVersion = nextVersion(prefs, LiveWallpaperService.KEY_CONFIG_VERSION)

        val committed = prefs.edit()
            .putString(LiveWallpaperService.KEY_LIVE_PATH, pendingPath)
            .putString(LiveWallpaperService.KEY_FRAME_POSITION, pendingFrame)
            .putString(LiveWallpaperService.KEY_COLOR_REGION, pendingRegion)
            .putString(LiveWallpaperService.KEY_TONE_PREFERENCE, pendingTone)
            .putLong(LiveWallpaperService.KEY_CONFIG_VERSION, activeVersion)

            // 清理 pending
            .remove(LiveWallpaperService.KEY_PENDING_PATH)
            .remove(LiveWallpaperService.KEY_PENDING_FRAME)
            .remove(LiveWallpaperService.KEY_PENDING_REGION)
            .remove(LiveWallpaperService.KEY_PENDING_TONE)
            .remove(LiveWallpaperService.KEY_PENDING_VERSION)

            // 清理 backup / mirror 标记
            .remove(KEY_ACTIVE_BACKUP_EXISTS)
            .remove(KEY_ACTIVE_BACKUP_WAS_PRESENT)
            .remove(KEY_ACTIVE_BACKUP_PATH)
            .remove(KEY_ACTIVE_BACKUP_FRAME)
            .remove(KEY_ACTIVE_BACKUP_REGION)
            .remove(KEY_ACTIVE_BACKUP_TONE)
            .remove(KEY_ACTIVE_BACKUP_VERSION)
            .remove(KEY_PREVIEW_MIRRORED_TO_ACTIVE)
            .commit()

        if (committed) {
            Log.d(
                TAG,
                "★ Pending 已正式提升为 Active: path=$pendingPath, activeVersion=$activeVersion"
            )
        } else {
            Log.e(TAG, "promotePendingToActive commit 失败")
        }
    }

    fun hasPendingConfig(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getString(LiveWallpaperService.KEY_PENDING_PATH, null).isNullOrBlank()
    }

    /**
     * 用户取消时：
     * - 清除 pending
     * - 如果本次曾临时镜像过 active，则恢复旧 active
     */
    fun clearPendingConfig(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val mirrored = prefs.getBoolean(KEY_PREVIEW_MIRRORED_TO_ACTIVE, false)
        val hasBackup = prefs.getBoolean(KEY_ACTIVE_BACKUP_EXISTS, false)

        val editor = prefs.edit()

        // 1) 清 pending
        editor.remove(LiveWallpaperService.KEY_PENDING_PATH)
        editor.remove(LiveWallpaperService.KEY_PENDING_FRAME)
        editor.remove(LiveWallpaperService.KEY_PENDING_REGION)
        editor.remove(LiveWallpaperService.KEY_PENDING_TONE)
        editor.remove(LiveWallpaperService.KEY_PENDING_VERSION)

        // 2) 如果本次预镜像过 active，则恢复
        if (mirrored && hasBackup) {
            val hadActive = prefs.getBoolean(KEY_ACTIVE_BACKUP_WAS_PRESENT, false)

            if (hadActive) {
                val backupPath = prefs.getString(KEY_ACTIVE_BACKUP_PATH, null)
                val backupFrame = prefs.getString(KEY_ACTIVE_BACKUP_FRAME, "FIRST") ?: "FIRST"
                val backupRegion = prefs.getString(KEY_ACTIVE_BACKUP_REGION, "FULL_FRAME") ?: "FULL_FRAME"
                val backupTone = prefs.getString(KEY_ACTIVE_BACKUP_TONE, "AUTO") ?: "AUTO"

                if (!backupPath.isNullOrBlank()) {
                    val restoreVersion = nextVersion(prefs, LiveWallpaperService.KEY_CONFIG_VERSION)

                    editor.putString(LiveWallpaperService.KEY_LIVE_PATH, backupPath)
                    editor.putString(LiveWallpaperService.KEY_FRAME_POSITION, backupFrame)
                    editor.putString(LiveWallpaperService.KEY_COLOR_REGION, backupRegion)
                    editor.putString(LiveWallpaperService.KEY_TONE_PREFERENCE, backupTone)
                    editor.putLong(LiveWallpaperService.KEY_CONFIG_VERSION, restoreVersion)

                    Log.d(
                        TAG,
                        "取消设置：恢复旧 active path=$backupPath, restoreVersion=$restoreVersion"
                    )
                }
            } else {
                val restoreVersion = nextVersion(prefs, LiveWallpaperService.KEY_CONFIG_VERSION)

                editor.remove(LiveWallpaperService.KEY_LIVE_PATH)
                editor.remove(LiveWallpaperService.KEY_FRAME_POSITION)
                editor.remove(LiveWallpaperService.KEY_COLOR_REGION)
                editor.remove(LiveWallpaperService.KEY_TONE_PREFERENCE)
                editor.putLong(LiveWallpaperService.KEY_CONFIG_VERSION, restoreVersion)

                Log.d(TAG, "取消设置：清除临时 active, restoreVersion=$restoreVersion")
            }
        }

        // 3) 清 backup / mirror 标记
        editor.remove(KEY_ACTIVE_BACKUP_EXISTS)
        editor.remove(KEY_ACTIVE_BACKUP_WAS_PRESENT)
        editor.remove(KEY_ACTIVE_BACKUP_PATH)
        editor.remove(KEY_ACTIVE_BACKUP_FRAME)
        editor.remove(KEY_ACTIVE_BACKUP_REGION)
        editor.remove(KEY_ACTIVE_BACKUP_TONE)
        editor.remove(KEY_ACTIVE_BACKUP_VERSION)
        editor.remove(KEY_PREVIEW_MIRRORED_TO_ACTIVE)

        val committed = editor.commit()
        if (committed) {
            Log.d(TAG, "Pending 配置已清除")
        } else {
            Log.e(TAG, "clearPendingConfig commit 失败")
        }
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

    private fun snapshotActiveIfNeeded(
        prefs: SharedPreferences,
        editor: SharedPreferences.Editor
    ) {
        val alreadyBackedUp = prefs.getBoolean(KEY_ACTIVE_BACKUP_EXISTS, false)
        if (alreadyBackedUp) return

        val activePath = prefs.getString(LiveWallpaperService.KEY_LIVE_PATH, null)
        val hadActive = !activePath.isNullOrBlank()
        val activeVersion = prefs.getLong(LiveWallpaperService.KEY_CONFIG_VERSION, 0L)

        editor.putBoolean(KEY_ACTIVE_BACKUP_EXISTS, true)
        editor.putBoolean(KEY_ACTIVE_BACKUP_WAS_PRESENT, hadActive)
        editor.putLong(KEY_ACTIVE_BACKUP_VERSION, activeVersion)

        if (hadActive) {
            editor.putString(KEY_ACTIVE_BACKUP_PATH, activePath)
            editor.putString(
                KEY_ACTIVE_BACKUP_FRAME,
                prefs.getString(LiveWallpaperService.KEY_FRAME_POSITION, "FIRST")
            )
            editor.putString(
                KEY_ACTIVE_BACKUP_REGION,
                prefs.getString(LiveWallpaperService.KEY_COLOR_REGION, "FULL_FRAME")
            )
            editor.putString(
                KEY_ACTIVE_BACKUP_TONE,
                prefs.getString(LiveWallpaperService.KEY_TONE_PREFERENCE, "AUTO")
            )
        } else {
            editor.remove(KEY_ACTIVE_BACKUP_PATH)
            editor.remove(KEY_ACTIVE_BACKUP_FRAME)
            editor.remove(KEY_ACTIVE_BACKUP_REGION)
            editor.remove(KEY_ACTIVE_BACKUP_TONE)
        }

        Log.d(
            TAG,
            "已备份旧 active 配置: hadActive=$hadActive, path=$activePath, version=$activeVersion"
        )
    }

    private fun nextVersion(
        prefs: SharedPreferences,
        key: String
    ): Long {
        val old = prefs.getLong(key, 0L)
        val now = System.currentTimeMillis()
        return if (now > old) now else old + 1L
    }
}
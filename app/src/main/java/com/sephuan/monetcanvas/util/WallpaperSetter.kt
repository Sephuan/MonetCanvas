package com.sephuan.monetcanvas.util

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File

object WallpaperSetter {

    private const val TAG = "WallpaperSetter"

    /**
     * 设置静态壁纸
     * @param target 1=桌面, 2=锁屏, 3=两者
     */
    fun setStaticWallpaper(
        context: Context,
        imagePath: String,
        target: Int
    ): Boolean {
        val file = File(imagePath)
        if (!file.exists() || file.length() <= 0L) {
            Log.e(TAG, "图片文件无效: $imagePath")
            return false
        }

        return try {
            val wm = WallpaperManager.getInstance(context)
            val bitmap = BitmapFactory.decodeFile(imagePath)

            if (bitmap == null) {
                Log.e(TAG, "无法解码图片: $imagePath")
                return false
            }

            when (target) {
                1 -> {
                    wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                    Log.d(TAG, "✓ 已设置桌面壁纸")
                }
                2 -> {
                    wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                    Log.d(TAG, "✓ 已设置锁屏壁纸")
                }
                3 -> {
                    wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                    Log.d(TAG, "✓ 已设置桌面+锁屏壁纸")
                }
                else -> {
                    Log.e(TAG, "未知 target: $target")
                    bitmap.recycle()
                    return false
                }
            }

            bitmap.recycle()
            true
        } catch (e: Exception) {
            Log.e(TAG, "设置静态壁纸失败", e)
            false
        }
    }
}
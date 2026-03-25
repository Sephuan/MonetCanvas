package com.sephuan.monetcanvas.util

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.sephuan.monetcanvas.data.model.FillMode
import com.sephuan.monetcanvas.data.model.ImageAdjustment
import java.io.File

object WallpaperSetter {

    private const val TAG = "WallpaperSetter"

    /**
     * 设置静态壁纸（支持图片调整参数）
     * @param target 1=桌面, 2=锁屏, 3=两者
     */
    fun setStaticWallpaper(
        context: Context,
        imagePath: String,
        target: Int,
        adjustment: ImageAdjustment = ImageAdjustment.DEFAULT
    ): Boolean {
        val file = File(imagePath)
        if (!file.exists() || file.length() <= 0L) {
            Log.e(TAG, "图片文件无效: $imagePath")
            return false
        }

        return try {
            val wm = WallpaperManager.getInstance(context)

            val originalBitmap = BitmapFactory.decodeFile(imagePath)
            if (originalBitmap == null) {
                Log.e(TAG, "无法解码图片: $imagePath")
                return false
            }

            val screenSize = getScreenSize(context)
            val screenW = screenSize.first
            val screenH = screenSize.second

            val finalBitmap = applyAdjustments(
                original = originalBitmap,
                adjustment = adjustment,
                targetWidth = screenW,
                targetHeight = screenH
            )

            when (target) {
                1 -> {
                    wm.setBitmap(finalBitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                    Log.d(TAG, "✓ 已设置桌面壁纸 (${finalBitmap.width}x${finalBitmap.height})")
                }
                2 -> {
                    wm.setBitmap(finalBitmap, null, true, WallpaperManager.FLAG_LOCK)
                    Log.d(TAG, "✓ 已设置锁屏壁纸")
                }
                3 -> {
                    wm.setBitmap(
                        finalBitmap, null, true,
                        WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                    )
                    Log.d(TAG, "✓ 已设置桌面+锁屏壁纸")
                }
                else -> {
                    Log.e(TAG, "未知 target: $target")
                    originalBitmap.recycle()
                    if (finalBitmap !== originalBitmap) finalBitmap.recycle()
                    return false
                }
            }

            if (finalBitmap !== originalBitmap) finalBitmap.recycle()
            originalBitmap.recycle()
            true
        } catch (e: Exception) {
            Log.e(TAG, "设置静态壁纸失败", e)
            false
        }
    }

    /**
     * ★ 核心：应用所有调整参数，生成最终 Bitmap
     *
     * 流程：
     * 1. 先翻转原图（如果需要）→ 得到内容正确的图
     * 2. 在画布上绘制背景色
     * 3. 根据填充模式计算缩放和位置
     * 4. 应用色彩滤镜
     * 5. 绘制到画布
     */
    private fun applyAdjustments(
        original: Bitmap,
        adjustment: ImageAdjustment,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        if (!adjustment.hasAnyAdjustment) {
            return cropToScreen(original, targetWidth, targetHeight)
        }

        // ★ 第1步：先翻转原图（和预览行为一致）
        val flipped = applyMirror(original, adjustment.mirrorHorizontal, adjustment.mirrorVertical)

        val srcW = flipped.width.toFloat()
        val srcH = flipped.height.toFloat()
        val dstW = targetWidth.toFloat()
        val dstH = targetHeight.toFloat()

        // 第2步：创建画布 + 填充背景色
        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val bgColor = adjustment.backgroundColor
        val bgArgb = android.graphics.Color.argb(
            (bgColor.alpha * 255).toInt(),
            (bgColor.red * 255).toInt(),
            (bgColor.green * 255).toInt(),
            (bgColor.blue * 255).toInt()
        )
        canvas.drawColor(bgArgb)

        // 第3步：计算缩放和位置（不再处理镜像，因为图已经翻好了）
        val matrix = Matrix()

        when (adjustment.fillMode) {
            FillMode.COVER -> {
                val scale = maxOf(dstW / srcW, dstH / srcH) * adjustment.scale
                val scaledW = srcW * scale
                val scaledH = srcH * scale
                val tx = (dstW - scaledW) / 2f + adjustment.offsetX
                val ty = (dstH - scaledH) / 2f + adjustment.offsetY
                matrix.setScale(scale, scale)
                matrix.postTranslate(tx, ty)
            }

            FillMode.FIT -> {
                val scale = minOf(dstW / srcW, dstH / srcH) * adjustment.scale
                val scaledW = srcW * scale
                val scaledH = srcH * scale
                val tx = (dstW - scaledW) / 2f + adjustment.offsetX
                val ty = (dstH - scaledH) / 2f + adjustment.offsetY
                matrix.setScale(scale, scale)
                matrix.postTranslate(tx, ty)
            }

            FillMode.FREE -> {
                val baseScale = minOf(dstW / srcW, dstH / srcH)
                val finalScale = baseScale * adjustment.scale
                val scaledW = srcW * finalScale
                val scaledH = srcH * finalScale
                val tx = (dstW - scaledW) / 2f + adjustment.offsetX
                val ty = (dstH - scaledH) / 2f + adjustment.offsetY
                matrix.setScale(finalScale, finalScale)
                matrix.postTranslate(tx, ty)
            }
        }

        // 第4步：色彩滤镜
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val colorFilter = buildColorFilter(adjustment)
        if (colorFilter != null) {
            paint.colorFilter = colorFilter
        }

        // 第5步：绘制
        canvas.drawBitmap(flipped, matrix, paint)

        // 清理翻转的临时 Bitmap
        if (flipped !== original) {
            flipped.recycle()
        }

        return result
    }

    /**
     * ★ 翻转原图（先翻好再处理位置，和预览行为一致）
     */
    private fun applyMirror(
        bitmap: Bitmap,
        mirrorH: Boolean,
        mirrorV: Boolean
    ): Bitmap {
        if (!mirrorH && !mirrorV) return bitmap

        val matrix = Matrix()
        val sx = if (mirrorH) -1f else 1f
        val sy = if (mirrorV) -1f else 1f
        matrix.setScale(sx, sy, bitmap.width / 2f, bitmap.height / 2f)

        return Bitmap.createBitmap(
            bitmap, 0, 0,
            bitmap.width, bitmap.height,
            matrix, true
        )
    }

    /**
     * 居中裁剪（无调整时使用）
     */
    private fun cropToScreen(
        bitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        val srcW = bitmap.width.toFloat()
        val srcH = bitmap.height.toFloat()
        val dstW = targetWidth.toFloat()
        val dstH = targetHeight.toFloat()

        val scale = maxOf(dstW / srcW, dstH / srcH)
        val scaledW = (srcW * scale).toInt()
        val scaledH = (srcH * scale).toInt()

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)

        val x = ((scaledW - targetWidth) / 2).coerceAtLeast(0)
        val y = ((scaledH - targetHeight) / 2).coerceAtLeast(0)
        val w = targetWidth.coerceAtMost(scaledW - x)
        val h = targetHeight.coerceAtMost(scaledH - y)

        val cropped = Bitmap.createBitmap(scaledBitmap, x, y, w, h)

        if (scaledBitmap !== bitmap && scaledBitmap !== cropped) {
            scaledBitmap.recycle()
        }

        return cropped
    }

    /**
     * 色彩滤镜
     */
    private fun buildColorFilter(adjustment: ImageAdjustment): ColorMatrixColorFilter? {
        val b = adjustment.brightness
        val c = adjustment.contrast
        val s = adjustment.saturation

        if (b == 0f && c == 0f && s == 0f) return null

        val brightnessOffset = b * 255f
        val brightnessMatrix = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, brightnessOffset,
                0f, 1f, 0f, 0f, brightnessOffset,
                0f, 0f, 1f, 0f, brightnessOffset,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val contrastScale = 1f + c
        val contrastOffset = (-0.5f * contrastScale + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrastScale, 0f, 0f, 0f, contrastOffset,
                0f, contrastScale, 0f, 0f, contrastOffset,
                0f, 0f, contrastScale, 0f, contrastOffset,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val saturationMatrix = ColorMatrix()
        saturationMatrix.setSaturation((1f + s).coerceIn(0f, 2f))

        val result = ColorMatrix()
        result.postConcat(brightnessMatrix)
        result.postConcat(contrastMatrix)
        result.postConcat(saturationMatrix)

        return ColorMatrixColorFilter(result)
    }

    /**
     * 屏幕尺寸
     */
    private fun getScreenSize(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()

        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        return Pair(metrics.widthPixels, metrics.heightPixels)
    }
}
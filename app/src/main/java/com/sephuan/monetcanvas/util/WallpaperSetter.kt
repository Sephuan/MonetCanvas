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
    // 降低最大图片尺寸，减少解码和变换耗时
    private const val MAX_BITMAP_SIZE = 1440

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
            val screenSize = getScreenSize(context)
            val screenW = screenSize.first
            val screenH = screenSize.second

            val originalBitmap = decodeSampledBitmap(imagePath, screenW, screenH)
            if (originalBitmap == null) {
                Log.e(TAG, "无法解码图片: $imagePath")
                return false
            }

            val finalBitmap = applyAdjustments(
                original = originalBitmap,
                adjustment = adjustment,
                targetWidth = screenW,
                targetHeight = screenH
            )

            when (target) {
                1 -> wm.setBitmap(finalBitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                2 -> wm.setBitmap(finalBitmap, null, true, WallpaperManager.FLAG_LOCK)
                3 -> wm.setBitmap(finalBitmap, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                else -> {
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
     * 优化采样：根据目标屏幕尺寸和最大允许尺寸计算采样率，避免解码超大图
     */
    private fun decodeSampledBitmap(imagePath: String, targetWidth: Int, targetHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(imagePath, options)

        val originalWidth = options.outWidth
        val originalHeight = options.outHeight
        if (originalWidth <= 0 || originalHeight <= 0) return null

        // 计算合适的采样率：使得解码后的图片尺寸接近目标尺寸或 MAX_BITMAP_SIZE 的较小者
        val maxDimension = maxOf(originalWidth, originalHeight)
        val targetMaxDimension = minOf(maxOf(targetWidth, targetHeight), MAX_BITMAP_SIZE)
        var sampleSize = 1
        if (maxDimension > targetMaxDimension) {
            sampleSize = (maxDimension.toFloat() / targetMaxDimension).toInt().coerceAtLeast(1)
        }

        options.inSampleSize = sampleSize
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.RGB_565
        return BitmapFactory.decodeFile(imagePath, options)
    }

    private fun applyAdjustments(
        original: Bitmap,
        adjustment: ImageAdjustment,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        val flipped = applyMirror(original, adjustment.mirrorHorizontal, adjustment.mirrorVertical)

        val srcW = flipped.width.toFloat()
        val srcH = flipped.height.toFloat()
        val dstW = targetWidth.toFloat()
        val dstH = targetHeight.toFloat()

        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val bgArgb = android.graphics.Color.argb(
            (adjustment.backgroundColor.alpha * 255).toInt(),
            (adjustment.backgroundColor.red * 255).toInt(),
            (adjustment.backgroundColor.green * 255).toInt(),
            (adjustment.backgroundColor.blue * 255).toInt()
        )
        canvas.drawColor(bgArgb)

        val baseScale = when (adjustment.fillMode) {
            FillMode.COVER -> maxOf(dstW / srcW, dstH / srcH)
            FillMode.FIT, FillMode.FREE -> minOf(dstW / srcW, dstH / srcH)
        }

        val effectiveScale = when (adjustment.fillMode) {
            FillMode.COVER, FillMode.FIT -> 1f
            FillMode.FREE -> adjustment.scale
        }

        val finalScale = baseScale * effectiveScale
        val scaledW = srcW * finalScale
        val scaledH = srcH * finalScale

        val centerX = (dstW - scaledW) / 2f
        val centerY = (dstH - scaledH) / 2f

        val offsetX = when (adjustment.fillMode) {
            FillMode.COVER -> centerX + adjustment.offsetX
            FillMode.FIT -> centerX
            FillMode.FREE -> centerX + adjustment.offsetX
        }

        val offsetY = when (adjustment.fillMode) {
            FillMode.COVER -> centerY
            FillMode.FIT -> centerY + adjustment.offsetY
            FillMode.FREE -> centerY + adjustment.offsetY
        }

        val matrix = Matrix()
        matrix.setScale(finalScale, finalScale)
        matrix.postTranslate(offsetX, offsetY)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        buildColorFilter(adjustment)?.let { paint.colorFilter = it }

        canvas.drawBitmap(flipped, matrix, paint)

        if (flipped !== original) flipped.recycle()
        return result
    }

    private fun applyMirror(bitmap: Bitmap, mirrorH: Boolean, mirrorV: Boolean): Bitmap {
        if (!mirrorH && !mirrorV) return bitmap
        val matrix = Matrix()
        val sx = if (mirrorH) -1f else 1f
        val sy = if (mirrorV) -1f else 1f
        matrix.setScale(sx, sy, bitmap.width / 2f, bitmap.height / 2f)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

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

    private fun getScreenSize(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        return Pair(metrics.widthPixels, metrics.heightPixels)
    }
}
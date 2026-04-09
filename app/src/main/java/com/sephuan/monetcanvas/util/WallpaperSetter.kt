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
import android.graphics.RectF
import android.util.Log
import android.view.WindowManager
import com.sephuan.monetcanvas.data.model.FillMode
import com.sephuan.monetcanvas.data.model.ImageAdjustment
import java.io.File
import kotlin.math.roundToInt

object WallpaperSetter {

    private const val TAG = "WallpaperSetter"
    private const val MAX_BITMAP_SIZE = 1600
    private const val MIN_BITMAP_SIZE = 720
    private const val DECODE_OVERSCAN = 1.12f

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
            val wallpaperManager = WallpaperManager.getInstance(context)
            val (screenW, screenH) = getScreenSize(context)

            val originalBitmap = decodeSampledBitmap(
                imagePath = imagePath,
                targetWidth = screenW,
                targetHeight = screenH,
                adjustment = adjustment
            )

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
                1 -> wallpaperManager.setBitmap(
                    finalBitmap,
                    null,
                    true,
                    WallpaperManager.FLAG_SYSTEM
                )

                2 -> wallpaperManager.setBitmap(
                    finalBitmap,
                    null,
                    true,
                    WallpaperManager.FLAG_LOCK
                )

                3 -> wallpaperManager.setBitmap(
                    finalBitmap,
                    null,
                    true,
                    WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                )

                else -> {
                    if (finalBitmap !== originalBitmap) finalBitmap.recycle()
                    originalBitmap.recycle()
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
     * Keep the old function signature.
     */
    @Suppress("unused")
    private fun decodeSampledBitmap(
        imagePath: String,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        return decodeSampledBitmap(
            imagePath = imagePath,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            adjustment = ImageAdjustment.DEFAULT
        )
    }

    /**
     * Optimized decode path used internally.
     */
    private fun decodeSampledBitmap(
        imagePath: String,
        targetWidth: Int,
        targetHeight: Int,
        adjustment: ImageAdjustment
    ): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(imagePath, options)

        val originalWidth = options.outWidth
        val originalHeight = options.outHeight
        if (originalWidth <= 0 || originalHeight <= 0) return null

        val requestedEdge = calculateRequestedDecodeEdge(
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            adjustment = adjustment
        )

        var inSampleSize = 1
        var halfWidth = originalWidth / 2
        var halfHeight = originalHeight / 2

        while ((halfWidth / inSampleSize) >= requestedEdge &&
            (halfHeight / inSampleSize) >= requestedEdge
        ) {
            inSampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inJustDecodeBounds = false
            inPreferredConfig = if (needsHighPrecision(adjustment)) {
                Bitmap.Config.ARGB_8888
            } else {
                Bitmap.Config.RGB_565
            }
        }

        return BitmapFactory.decodeFile(imagePath, decodeOptions)
    }

    private fun calculateRequestedDecodeEdge(
        targetWidth: Int,
        targetHeight: Int,
        adjustment: ImageAdjustment
    ): Int {
        val screenEdge = maxOf(targetWidth, targetHeight).coerceAtLeast(1)
        val scaleFactor = adjustment.scale.coerceAtLeast(1f)
        return (screenEdge * DECODE_OVERSCAN * scaleFactor)
            .roundToInt()
            .coerceIn(MIN_BITMAP_SIZE, MAX_BITMAP_SIZE)
    }

    private fun needsHighPrecision(adjustment: ImageAdjustment): Boolean {
        return adjustment.brightness != 0f ||
                adjustment.contrast != 0f ||
                adjustment.saturation != 0f
    }

    private fun applyAdjustments(
        original: Bitmap,
        adjustment: ImageAdjustment,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        val srcW = original.width.toFloat().coerceAtLeast(1f)
        val srcH = original.height.toFloat().coerceAtLeast(1f)
        val dstW = targetWidth.toFloat().coerceAtLeast(1f)
        val dstH = targetHeight.toFloat().coerceAtLeast(1f)

        val result = Bitmap.createBitmap(
            targetWidth.coerceAtLeast(1),
            targetHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(result)
        canvas.drawColor(colorToArgb(adjustment.backgroundColor))

        val baseScale = when (adjustment.fillMode) {
            FillMode.COVER -> maxOf(dstW / srcW, dstH / srcH)
            FillMode.FIT,
            FillMode.FREE -> minOf(dstW / srcW, dstH / srcH)
        }

        // Keep behavior aligned with current preview:
        // all modes can use the scale slider.
        val userScale = adjustment.scale.coerceIn(0.2f, 8f)
        val finalScale = baseScale * userScale

        val mirrorX = if (adjustment.mirrorHorizontal) -1f else 1f
        val mirrorY = if (adjustment.mirrorVertical) -1f else 1f

        val matrix = Matrix().apply {
            setScale(finalScale * mirrorX, finalScale * mirrorY)
        }

        val srcRect = RectF(0f, 0f, srcW, srcH)
        val mappedRect = RectF()
        matrix.mapRect(mappedRect, srcRect)

        val centeredX = (dstW - mappedRect.width()) / 2f - mappedRect.left
        val centeredY = (dstH - mappedRect.height()) / 2f - mappedRect.top

        val extraOffsetX = when (adjustment.fillMode) {
            FillMode.COVER -> adjustment.offsetX
            FillMode.FIT -> 0f
            FillMode.FREE -> adjustment.offsetX
        }

        val extraOffsetY = when (adjustment.fillMode) {
            FillMode.COVER -> 0f
            FillMode.FIT -> adjustment.offsetY
            FillMode.FREE -> adjustment.offsetY
        }

        matrix.postTranslate(
            centeredX + extraOffsetX,
            centeredY + extraOffsetY
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        buildColorFilter(adjustment)?.let { paint.colorFilter = it }

        canvas.drawBitmap(original, matrix, paint)
        return result
    }

    /**
     * Keep the old function. It is no longer the main path,
     * but stays here so previous functionality is not removed.
     */
    @Suppress("unused")
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
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
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

        val saturationMatrix = ColorMatrix().apply {
            setSaturation((1f + s).coerceIn(0f, 2f))
        }

        val result = ColorMatrix()
        result.postConcat(brightnessMatrix)
        result.postConcat(contrastMatrix)
        result.postConcat(saturationMatrix)

        return ColorMatrixColorFilter(result)
    }

    private fun getScreenSize(context: Context): Pair<Int, Int> {
        return runCatching {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val bounds = wm.currentWindowMetrics.bounds
            Pair(
                bounds.width().coerceAtLeast(1),
                bounds.height().coerceAtLeast(1)
            )
        }.getOrElse {
            val dm = context.resources.displayMetrics
            Pair(
                dm.widthPixels.coerceAtLeast(1),
                dm.heightPixels.coerceAtLeast(1)
            )
        }
    }

    private fun colorToArgb(color: androidx.compose.ui.graphics.Color): Int {
        return android.graphics.Color.argb(
            (color.alpha * 255).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt()
        )
    }
}
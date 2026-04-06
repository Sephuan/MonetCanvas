// 文件路径：app/src/main/java/com/sephuan/monetcanvas/util/ColorEngine.kt
package com.sephuan.monetcanvas.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import androidx.palette.graphics.Palette
import com.sephuan.monetcanvas.data.model.ColorRegion
import com.sephuan.monetcanvas.data.model.FramePickPosition
import com.sephuan.monetcanvas.data.model.MonetRule
import com.sephuan.monetcanvas.data.model.WallpaperType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class ExtractedColors(
    val primary: Int,
    val secondary: Int? = null,
    val tertiary: Int? = null
)

object ColorEngine {

    suspend fun extractColors(
        filePath: String,
        rule: MonetRule,
        type: WallpaperType = WallpaperType.STATIC
    ): ExtractedColors? = withContext(Dispatchers.IO) {

        rule.manualOverrideColor?.let { return@withContext ExtractedColors(primary = it) }

        val rawBitmap: Bitmap? = when (type) {
            WallpaperType.STATIC -> decodeSampledBitmap(filePath)
            WallpaperType.LIVE -> extractVideoFrame(filePath, rule.framePosition)
        }

        rawBitmap ?: return@withContext null

        val cropped = safeCropBitmap(rawBitmap, rule.colorRegion)
        val palette = Palette.from(cropped).maximumColorCount(16).generate()

        val primarySwatch = pickSwatch(palette, rule.tonePreference)
        val primaryColor = primarySwatch?.rgb ?: Color.TRANSPARENT

        val others = palette.swatches
            .sortedByDescending { it.population }
            .filter { it.rgb != primaryColor }

        val secondaryColor = others.getOrNull(0)?.rgb
        val tertiaryColor = others.getOrNull(1)?.rgb

        if (cropped !== rawBitmap) cropped.recycle()
        rawBitmap.recycle()

        if (primaryColor == Color.TRANSPARENT) null
        else ExtractedColors(primaryColor, secondaryColor, tertiaryColor)
    }

    suspend fun extractColors(
        imagePath: String,
        rule: MonetRule
    ): ExtractedColors? = extractColors(imagePath, rule, WallpaperType.STATIC)

    private fun decodeSampledBitmap(filePath: String): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, options)

        val targetSize = 800 // 取色不需要太大图片
        var sampleSize = 1
        if (options.outHeight > targetSize || options.outWidth > targetSize) {
            val heightRatio = options.outHeight.toDouble() / targetSize
            val widthRatio = options.outWidth.toDouble() / targetSize
            sampleSize = maxOf(1, minOf(heightRatio, widthRatio).toInt())
        }

        options.inSampleSize = sampleSize
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.RGB_565
        return BitmapFactory.decodeFile(filePath, options)
    }

    fun extractVideoFrame(
        videoPath: String,
        position: FramePickPosition
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoPath)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L

            val targetTimeUs = when (position) {
                FramePickPosition.FIRST -> 0L
                FramePickPosition.MIDDLE -> (durationMs / 2) * 1000L
                FramePickPosition.LAST -> maxOf(0L, (durationMs - 100) * 1000L)
                FramePickPosition.RANDOM -> {
                    if (durationMs > 0) Random.nextLong(0, durationMs * 1000L) else 0L
                }
            }

            retriever.getFrameAtTime(targetTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun safeCropBitmap(bitmap: Bitmap, region: ColorRegion): Bitmap {
        return try {
            when (region) {
                ColorRegion.FULL_FRAME -> bitmap
                ColorRegion.CENTER -> {
                    val ox = (bitmap.width * 0.3f).toInt().coerceAtLeast(1)
                    val oy = (bitmap.height * 0.3f).toInt().coerceAtLeast(1)
                    val w = (bitmap.width - 2 * ox).coerceAtLeast(1)
                    val h = (bitmap.height - 2 * oy).coerceAtLeast(1)
                    Bitmap.createBitmap(bitmap, ox, oy, w, h)
                }
                ColorRegion.TOP_HALF -> {
                    val h = (bitmap.height / 2).coerceAtLeast(1)
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, h)
                }
                ColorRegion.BOTTOM_HALF -> {
                    val h = (bitmap.height / 2).coerceAtLeast(1)
                    Bitmap.createBitmap(bitmap, 0, bitmap.height - h, bitmap.width, h)
                }
                ColorRegion.CUSTOM -> bitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            bitmap
        }
    }

    private fun pickSwatch(palette: Palette, tone: com.sephuan.monetcanvas.data.model.TonePreference): Palette.Swatch? {
        return when (tone) {
            com.sephuan.monetcanvas.data.model.TonePreference.AUTO -> palette.dominantSwatch
            com.sephuan.monetcanvas.data.model.TonePreference.VIBRANT ->
                palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch
            com.sephuan.monetcanvas.data.model.TonePreference.MUTED ->
                palette.mutedSwatch ?: palette.lightMutedSwatch ?: palette.dominantSwatch
            com.sephuan.monetcanvas.data.model.TonePreference.DOMINANT -> palette.dominantSwatch
            com.sephuan.monetcanvas.data.model.TonePreference.DARK_PREFERRED ->
                palette.darkVibrantSwatch ?: palette.darkMutedSwatch ?: palette.dominantSwatch
            com.sephuan.monetcanvas.data.model.TonePreference.LIGHT_PREFERRED ->
                palette.lightVibrantSwatch ?: palette.lightMutedSwatch ?: palette.dominantSwatch
        }
    }
}
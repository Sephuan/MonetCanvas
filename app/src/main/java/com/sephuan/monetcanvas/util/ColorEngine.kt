package com.sephuan.monetcanvas.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.core.graphics.scale
import androidx.palette.graphics.Palette
import com.sephuan.monetcanvas.data.model.ColorRegion
import com.sephuan.monetcanvas.data.model.FramePickPosition
import com.sephuan.monetcanvas.data.model.MonetRule
import com.sephuan.monetcanvas.data.model.TonePreference
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

    private const val TAG = "ColorEngine"

    suspend fun extractColors(
        filePath: String,
        rule: MonetRule,
        type: WallpaperType = WallpaperType.STATIC
    ): ExtractedColors? = withContext(Dispatchers.IO) {

        rule.manualOverrideColor?.let { return@withContext ExtractedColors(primary = it) }

        val rawBitmap: Bitmap? = when (type) {
            WallpaperType.STATIC -> decodeSampledBitmap(filePath)
            WallpaperType.LIVE -> extractStableVideoFrame(filePath, rule.framePosition)
        }

        rawBitmap ?: run {
            Log.e(TAG, "获取图像帧失败: $filePath")
            return@withContext null
        }

        val cropped = safeCropBitmap(rawBitmap, rule.colorRegion)
        val sampled = downsampleForPalette(cropped)

        val palette = Palette.from(sampled).maximumColorCount(16).generate()

        val primarySwatch = pickSwatch(palette, rule.tonePreference)
        val primaryColor = primarySwatch?.rgb

        if (primaryColor == null) {
            Log.e(TAG, "Palette 未能提取到有效颜色")
            if (sampled !== cropped) sampled.recycle()
            if (cropped !== rawBitmap) cropped.recycle()
            rawBitmap.recycle()
            return@withContext null
        }

        val others = palette.swatches
            .sortedByDescending { it.population }
            .filter { it.rgb != primaryColor }

        val secondaryColor = others.getOrNull(0)?.rgb
        val tertiaryColor = others.getOrNull(1)?.rgb

        if (sampled !== cropped) sampled.recycle()
        if (cropped !== rawBitmap) cropped.recycle()
        rawBitmap.recycle()

        ExtractedColors(primaryColor, secondaryColor, tertiaryColor)
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

    /**
     * ★ 核心修复：稳健的视频帧提取算法
     * 不再死盯 0ms（经常失败或全黑），提供时间点后备方案
     */
    private fun extractStableVideoFrame(
        videoPath: String,
        position: FramePickPosition
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoPath)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L

            val candidatesUs = buildCandidateTimesUs(durationMs, position)

            for (timeUs in candidatesUs) {
                // 优先尝试同步帧（关键帧），解码更快更安全
                val syncFrame = runCatching {
                    retriever.getFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                }.getOrNull()

                if (syncFrame != null) return syncFrame

                // 退而求其次，使用精确帧解码
                val closestFrame = runCatching {
                    retriever.getFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST
                    )
                }.getOrNull()

                if (closestFrame != null) return closestFrame
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "extractStableVideoFrame 异常", e)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun buildCandidateTimesUs(durationMs: Long, position: FramePickPosition): LongArray {
        val safeDurationMs = durationMs.coerceAtLeast(0L)
        if (safeDurationMs <= 0L) return longArrayOf(0L)

        val lastMs = (safeDurationMs - 1L).coerceAtLeast(0L)
        fun clampMs(value: Long): Long = value.coerceIn(0L, lastMs)

        val candidatesMs = mutableListOf<Long>()

        when (position) {
            FramePickPosition.FIRST -> {
                candidatesMs += clampMs(250L)
                candidatesMs += clampMs(0L)
                candidatesMs += clampMs(500L)
                candidatesMs += clampMs(1000L)
            }
            FramePickPosition.MIDDLE -> {
                val mid = safeDurationMs / 2L
                listOf(0L, -500L, 500L, -1000L, 1000L).forEach { delta ->
                    candidatesMs += clampMs(mid + delta)
                }
            }
            FramePickPosition.LAST -> {
                listOf(500L, 1000L, 1500L, 2000L).forEach { back ->
                    candidatesMs += clampMs(lastMs - back)
                }
                candidatesMs += clampMs(lastMs)
            }
            FramePickPosition.RANDOM -> {
                val start = (safeDurationMs * 10L / 100L).coerceIn(0L, lastMs)
                val endExclusive = (safeDurationMs * 90L / 100L)
                    .coerceAtLeast(start + 1L)
                    .coerceAtMost(safeDurationMs)

                val randomMs = if (endExclusive > start) {
                    Random.nextLong(start, endExclusive)
                } else {
                    start
                }

                candidatesMs += clampMs(randomMs)
                candidatesMs += clampMs(safeDurationMs / 2L)
                candidatesMs += clampMs(250L)
                candidatesMs += clampMs(lastMs - 500L)
            }
        }

        return candidatesMs
            .distinct()
            .map { it * 1000L }
            .toLongArray()
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
            Log.e(TAG, "safeCropBitmap 异常", e)
            bitmap
        }
    }

    private fun downsampleForPalette(bitmap: Bitmap): Bitmap {
        val maxEdge = 720
        val width = bitmap.width.coerceAtLeast(1)
        val height = bitmap.height.coerceAtLeast(1)

        if (width <= maxEdge && height <= maxEdge) {
            return bitmap
        }

        val ratio = minOf(
            maxEdge / width.toFloat(),
            maxEdge / height.toFloat()
        )

        val targetW = (width * ratio).toInt().coerceAtLeast(1)
        val targetH = (height * ratio).toInt().coerceAtLeast(1)

        return try {
            bitmap.scale(targetW, targetH, true)
        } catch (e: Exception) {
            Log.e(TAG, "downsampleForPalette 异常", e)
            bitmap
        }
    }

    private fun pickSwatch(palette: Palette, tone: TonePreference): Palette.Swatch? {
        return when (tone) {
            TonePreference.AUTO -> palette.dominantSwatch
            TonePreference.VIBRANT ->
                palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch
            TonePreference.MUTED ->
                palette.mutedSwatch ?: palette.lightMutedSwatch ?: palette.dominantSwatch
            TonePreference.DOMINANT -> palette.dominantSwatch
            TonePreference.DARK_PREFERRED ->
                palette.darkVibrantSwatch ?: palette.darkMutedSwatch ?: palette.dominantSwatch
            TonePreference.LIGHT_PREFERRED ->
                palette.lightVibrantSwatch ?: palette.lightMutedSwatch ?: palette.dominantSwatch
        }
    }
}
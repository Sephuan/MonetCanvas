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

    /**
     * 统一入口：支持静态图片和动态视频
     */
    suspend fun extractColors(
        filePath: String,
        rule: MonetRule,
        type: WallpaperType = WallpaperType.STATIC
    ): ExtractedColors? = withContext(Dispatchers.IO) {

        // 手动指定颜色时直接返回
        rule.manualOverrideColor?.let { return@withContext ExtractedColors(primary = it) }

        // 获取原始 Bitmap
        val rawBitmap: Bitmap? = when (type) {
            WallpaperType.STATIC -> BitmapFactory.decodeFile(filePath)
            WallpaperType.LIVE -> extractVideoFrame(filePath, rule.framePosition)
        }

        rawBitmap ?: return@withContext null

        // 按区域裁剪
        val cropped = cropBitmap(rawBitmap, rule.colorRegion)

        // Palette 分析
        val palette = Palette.from(cropped).maximumColorCount(16).generate()

        // 按偏好选主色
        val primarySwatch = pickSwatch(palette, rule.tonePreference)
        val primaryColor = primarySwatch?.rgb ?: Color.TRANSPARENT

        // 次色和第三色
        val others = palette.swatches
            .sortedByDescending { it.population }
            .filter { it.rgb != primaryColor }

        val secondaryColor = others.getOrNull(0)?.rgb
        val tertiaryColor = others.getOrNull(1)?.rgb

        // 清理
        if (cropped !== rawBitmap) cropped.recycle()
        rawBitmap.recycle()

        if (primaryColor == Color.TRANSPARENT) null
        else ExtractedColors(primaryColor, secondaryColor, tertiaryColor)
    }

    /**
     * 兼容旧调用（静态图片，不传 type）
     */
    suspend fun extractColors(
        imagePath: String,
        rule: MonetRule
    ): ExtractedColors? = extractColors(imagePath, rule, WallpaperType.STATIC)

    /**
     * 从视频中提取指定帧
     */
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

    private fun cropBitmap(bitmap: Bitmap, region: ColorRegion): Bitmap {
        return when (region) {
            ColorRegion.FULL_FRAME -> bitmap
            ColorRegion.CENTER -> {
                val ox = (bitmap.width * 0.3f).toInt()
                val oy = (bitmap.height * 0.3f).toInt()
                Bitmap.createBitmap(bitmap, ox, oy, bitmap.width - 2 * ox, bitmap.height - 2 * oy)
            }
            ColorRegion.TOP_HALF -> {
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height / 2)
            }
            ColorRegion.BOTTOM_HALF -> {
                Bitmap.createBitmap(bitmap, 0, bitmap.height / 2, bitmap.width, bitmap.height / 2)
            }
            ColorRegion.CUSTOM -> bitmap
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
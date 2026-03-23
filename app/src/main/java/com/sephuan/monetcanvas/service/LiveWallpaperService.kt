package com.sephuan.monetcanvas.service

import android.app.WallpaperColors
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.palette.graphics.Palette
import java.io.File
import kotlin.random.Random

class LiveWallpaperService : WallpaperService() {

    companion object {
        const val PREFS_NAME = "wallpaper_prefs"

        // ★ 正式桌面配置（Active）
        const val KEY_LIVE_PATH = "live_wallpaper_path"
        const val KEY_FRAME_POSITION = "live_frame_position"
        const val KEY_COLOR_REGION = "live_color_region"
        const val KEY_TONE_PREFERENCE = "live_tone_preference"
        const val KEY_CONFIG_VERSION = "live_config_version"

        // ★ 预览界面配置（Pending）
        const val KEY_PENDING_PATH = "pending_live_path"
        const val KEY_PENDING_FRAME = "pending_frame_position"
        const val KEY_PENDING_REGION = "pending_color_region"
        const val KEY_PENDING_TONE = "pending_tone_preference"
        const val KEY_PENDING_VERSION = "pending_config_version"

        private const val TAG = "LiveWallpaperSvc"
    }

    override fun onCreateEngine(): Engine {
        return VideoEngine()
    }

    inner class VideoEngine : Engine(),
        SharedPreferences.OnSharedPreferenceChangeListener {

        private var exoPlayer: ExoPlayer? = null
        private var isPlayerReady = false
        private var currentVideoPath: String? = null
        private var currentHolder: SurfaceHolder? = null
        private var retryCount = 0
        private val maxRetries = 3
        private val mainHandler = Handler(Looper.getMainLooper())

        private var computedColors: WallpaperColors? = null

        private val prefs: SharedPreferences by lazy {
            applicationContext.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        }

        // ★ 核心魔法：判断当前是系统确认页（预览），还是真正的桌面
        private val isPreviewEngine get() = isPreview

        // 根据状态动态选择读取哪一套配置
        private val pathKey get() = if (isPreviewEngine) KEY_PENDING_PATH else KEY_LIVE_PATH
        private val frameKey get() = if (isPreviewEngine) KEY_PENDING_FRAME else KEY_FRAME_POSITION
        private val regionKey get() = if (isPreviewEngine) KEY_PENDING_REGION else KEY_COLOR_REGION
        private val toneKey get() = if (isPreviewEngine) KEY_PENDING_TONE else KEY_TONE_PREFERENCE
        private val versionKey get() = if (isPreviewEngine) KEY_PENDING_VERSION else KEY_CONFIG_VERSION

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            Log.d(TAG, "onCreate (isPreview=$isPreviewEngine)")
            prefs.registerOnSharedPreferenceChangeListener(this)

            // 如果是预览页，优先读 pending，读不到则降级读 live
            val path = prefs.getString(pathKey, null) ?: if (isPreviewEngine) prefs.getString(KEY_LIVE_PATH, null) else null

            if (!path.isNullOrBlank() && File(path).exists()) {
                currentVideoPath = path
                Thread { extractAndReportColors(path) }.start()
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            currentHolder = holder
            loadAndPlay()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            currentHolder = holder
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (!visible) {
                try { exoPlayer?.pause() } catch (_: Exception) {}
                return
            }

            // 每次回到桌面且不是预览时，通知系统颜色
            if (!isPreviewEngine && computedColors != null) {
                mainHandler.post {
                    try { notifyColorsChanged() } catch (_: Exception) {}
                }
            }

            if (isPlayerReady) {
                try { exoPlayer?.play() } catch (e: Exception) {
                    if (currentHolder != null) loadAndPlay()
                }
            } else if (currentHolder != null) {
                loadAndPlay()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            releasePlayer()
            currentHolder = null
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            mainHandler.removeCallbacksAndMessages(null)
            releasePlayer()
            currentHolder = null
            super.onDestroy()
        }

        override fun onComputeColors(): WallpaperColors? {
            return computedColors
        }

        override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
            // ★ 只响应属于自己这套配置的版本号变化
            // 桌面只管 KEY_CONFIG_VERSION，预览页只管 KEY_PENDING_VERSION
            if (key == versionKey) {
                val path = prefs.getString(pathKey, null) ?: if (isPreviewEngine) prefs.getString(KEY_LIVE_PATH, null) else null

                if (!path.isNullOrBlank() && File(path).exists()) {
                    val pathChanged = path != currentVideoPath
                    currentVideoPath = path
                    Log.d(TAG, "配置更新 (isPreview=$isPreviewEngine), path=$path")

                    Thread { extractAndReportColors(path) }.start()

                    if (pathChanged && currentHolder != null) {
                        retryCount = 0
                        mainHandler.post { loadAndPlay() }
                    }
                }
            }
        }

        private fun loadAndPlay() {
            val holder = currentHolder ?: return
            val path = prefs.getString(pathKey, null) ?: if (isPreviewEngine) prefs.getString(KEY_LIVE_PATH, null) else null
            if (path.isNullOrBlank() || !File(path).exists()) return

            releasePlayer()
            currentVideoPath = path

            try {
                exoPlayer = ExoPlayer.Builder(applicationContext).build().apply {
                    setVideoSurface(holder.surface)
                    setMediaItem(MediaItem.fromUri(Uri.parse("file://$path")))
                    repeatMode = Player.REPEAT_MODE_ALL
                    volume = 0f

                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) {
                                isPlayerReady = true
                                retryCount = 0
                                if (isVisible) play()
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            isPlayerReady = false
                            if (retryCount < maxRetries) {
                                retryCount++
                                mainHandler.postDelayed({ loadAndPlay() }, 1000)
                            }
                        }
                    })
                    prepare()
                }
            } catch (e: Exception) {
                releasePlayer()
            }
        }

        private fun extractAndReportColors(videoPath: String) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(videoPath)

                // ★ 根据当前是桌面还是预览，读取对应的规则
                val framePos = prefs.getString(frameKey, "FIRST") ?: "FIRST"
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

                val targetTimeUs = when (framePos) {
                    "FIRST" -> 0L
                    "MIDDLE" -> (durationMs / 2) * 1000L
                    "LAST" -> maxOf(0L, (durationMs - 100) * 1000L)
                    "RANDOM" -> if (durationMs > 0) Random.nextLong(0, durationMs * 1000L) else 0L
                    else -> 0L
                }

                val frame: Bitmap = retriever.getFrameAtTime(targetTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return

                val region = prefs.getString(regionKey, "FULL_FRAME") ?: "FULL_FRAME"
                val cropped = cropBitmap(frame, region)
                val palette = Palette.from(cropped).maximumColorCount(16).generate()

                val tonePref = prefs.getString(toneKey, "AUTO") ?: "AUTO"
                val primarySwatch = when (tonePref) {
                    "VIBRANT" -> palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch
                    "MUTED" -> palette.mutedSwatch ?: palette.lightMutedSwatch ?: palette.dominantSwatch
                    "DOMINANT" -> palette.dominantSwatch
                    "DARK_PREFERRED" -> palette.darkVibrantSwatch ?: palette.darkMutedSwatch ?: palette.dominantSwatch
                    "LIGHT_PREFERRED" -> palette.lightVibrantSwatch ?: palette.lightMutedSwatch ?: palette.dominantSwatch
                    else -> palette.dominantSwatch
                }

                val primaryColor = primarySwatch?.rgb ?: Color.BLUE
                val allSwatches = palette.swatches.sortedByDescending { it.population }.filter { it.rgb != primaryColor }

                computedColors = WallpaperColors(
                    Color.valueOf(primaryColor),
                    allSwatches.getOrNull(0)?.rgb?.let { Color.valueOf(it) },
                    allSwatches.getOrNull(1)?.rgb?.let { Color.valueOf(it) }
                )

                mainHandler.post {
                    try { notifyColorsChanged() } catch (_: Exception) {}
                }

                if (cropped !== frame) cropped.recycle()
                frame.recycle()
            } catch (e: Exception) {
                Log.e(TAG, "颜色提取失败", e)
            } finally {
                runCatching { retriever.release() }
            }
        }

        private fun cropBitmap(bitmap: Bitmap, region: String): Bitmap {
            return try {
                when (region) {
                    "CENTER" -> {
                        val ox = (bitmap.width * 0.3f).toInt().coerceAtLeast(1)
                        val oy = (bitmap.height * 0.3f).toInt().coerceAtLeast(1)
                        Bitmap.createBitmap(bitmap, ox, oy, (bitmap.width - 2 * ox).coerceAtLeast(1), (bitmap.height - 2 * oy).coerceAtLeast(1))
                    }
                    "TOP_HALF" -> Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, (bitmap.height / 2).coerceAtLeast(1))
                    "BOTTOM_HALF" -> {
                        val h = (bitmap.height / 2).coerceAtLeast(1)
                        Bitmap.createBitmap(bitmap, 0, bitmap.height - h, bitmap.width, h)
                    }
                    else -> bitmap
                }
            } catch (_: Exception) { bitmap }
        }

        private fun releasePlayer() {
            isPlayerReady = false
            runCatching { exoPlayer?.stop() }
            runCatching { exoPlayer?.clearVideoSurface() }
            runCatching { exoPlayer?.release() }
            exoPlayer = null
        }
    }
}
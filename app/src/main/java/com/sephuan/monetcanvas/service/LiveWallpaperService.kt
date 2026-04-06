package com.sephuan.monetcanvas.service

import android.app.WallpaperColors
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
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
import java.util.concurrent.atomic.AtomicBoolean

class LiveWallpaperService : WallpaperService() {

    companion object {
        const val PREFS_NAME = "wallpaper_prefs"

        const val KEY_LIVE_PATH = "live_wallpaper_path"
        const val KEY_FRAME_POSITION = "live_frame_position"
        const val KEY_COLOR_REGION = "live_color_region"
        const val KEY_TONE_PREFERENCE = "live_tone_preference"
        const val KEY_CONFIG_VERSION = "live_config_version"

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

        // 当前有效的颜色（提取成功时更新，失败时保留旧值）
        private var computedColors: WallpaperColors? = null

        // 颜色提取的简单防重复标志
        private val isExtracting = AtomicBoolean(false)
        private val extractHandler = Handler(Looper.getMainLooper())
        private var pendingExtractPath: String? = null

        private val prefs: SharedPreferences by lazy {
            applicationContext.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        }

        private val isPreviewEngine get() = isPreview

        private val pathKey get() = if (isPreviewEngine) KEY_PENDING_PATH else KEY_LIVE_PATH
        private val frameKey get() = if (isPreviewEngine) KEY_PENDING_FRAME else KEY_FRAME_POSITION
        private val regionKey get() = if (isPreviewEngine) KEY_PENDING_REGION else KEY_COLOR_REGION
        private val toneKey get() = if (isPreviewEngine) KEY_PENDING_TONE else KEY_TONE_PREFERENCE
        private val versionKey get() = if (isPreviewEngine) KEY_PENDING_VERSION else KEY_CONFIG_VERSION

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            Log.d(TAG, "onCreate (isPreview=$isPreviewEngine)")
            prefs.registerOnSharedPreferenceChangeListener(this)

            val path = resolveCurrentPath()
            if (!path.isNullOrBlank() && File(path).exists()) {
                currentVideoPath = path
                scheduleColorExtract(path)
                if (surfaceHolder.surface.isValid) {
                    currentHolder = surfaceHolder
                    loadAndPlay()
                }
            } else {
                Log.w(TAG, "onCreate: 当前无有效路径, path=$path")
                if (computedColors == null) {
                    setDefaultColors()
                }
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
                try {
                    exoPlayer?.pause()
                } catch (_: Exception) {
                }
                return
            }

            val latestPath = resolveCurrentPath()
            Log.d(TAG, "onVisibilityChanged: visible=true, isPreview=$isPreviewEngine, latestPath=$latestPath, currentPath=$currentVideoPath")

            if (!latestPath.isNullOrBlank() && File(latestPath).exists()) {
                if (exoPlayer == null || latestPath != currentVideoPath) {
                    Log.d(TAG, "onVisibilityChanged: 需要重新加载 (path=$latestPath)")
                    currentVideoPath = latestPath
                    retryCount = 0
                    loadAndPlay()
                    scheduleColorExtract(latestPath)
                } else if (!isPlayerReady) {
                    loadAndPlay()
                }
            } else if (latestPath.isNullOrBlank()) {
                Log.w(TAG, "当前引擎没有可用路径")
                releasePlayer()
            } else if (!File(latestPath).exists()) {
                Log.e(TAG, "视频文件不存在: $latestPath")
                releasePlayer()
            }

            if (isPlayerReady) {
                try {
                    exoPlayer?.play()
                } catch (_: Exception) {
                    if (currentHolder != null) loadAndPlay()
                }
            } else if (currentHolder != null && !resolveCurrentPath().isNullOrBlank()) {
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
            extractHandler.removeCallbacksAndMessages(null)
            releasePlayer()
            currentHolder = null
            super.onDestroy()
        }

        override fun onComputeColors(): WallpaperColors? {
            return computedColors
        }

        override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
            if (key == versionKey) {
                val newPath = resolveCurrentPath()
                if (!newPath.isNullOrBlank() && File(newPath).exists()) {
                    Log.d(TAG, "配置变化: 新路径=$newPath, 旧路径=$currentVideoPath")
                    currentVideoPath = newPath
                    releasePlayer()
                    retryCount = 0
                    if (currentHolder != null) {
                        loadAndPlay()
                    } else {
                        Log.d(TAG, "holder 未就绪，将在 surface 创建时加载")
                    }
                    scheduleColorExtract(newPath)
                } else {
                    Log.w(TAG, "配置变化但新路径无效: $newPath")
                }
            }
        }

        private fun resolveCurrentPath(): String? {
            return if (isPreviewEngine) {
                prefs.getString(KEY_PENDING_PATH, null) ?: prefs.getString(KEY_LIVE_PATH, null)
            } else {
                prefs.getString(KEY_LIVE_PATH, null)
            }
        }

        private fun resolveFrameKey(): String {
            return if (isPreviewEngine) {
                prefs.getString(KEY_PENDING_FRAME, null) ?: prefs.getString(KEY_FRAME_POSITION, "FIRST") ?: "FIRST"
            } else {
                prefs.getString(KEY_FRAME_POSITION, "FIRST") ?: "FIRST"
            }
        }

        private fun resolveRegionKey(): String {
            return if (isPreviewEngine) {
                prefs.getString(KEY_PENDING_REGION, null) ?: prefs.getString(KEY_COLOR_REGION, "FULL_FRAME") ?: "FULL_FRAME"
            } else {
                prefs.getString(KEY_COLOR_REGION, "FULL_FRAME") ?: "FULL_FRAME"
            }
        }

        private fun resolveToneKey(): String {
            return if (isPreviewEngine) {
                prefs.getString(KEY_PENDING_TONE, null) ?: prefs.getString(KEY_TONE_PREFERENCE, "AUTO") ?: "AUTO"
            } else {
                prefs.getString(KEY_TONE_PREFERENCE, "AUTO") ?: "AUTO"
            }
        }

        /**
         * 调度颜色提取：延迟 300ms 后执行，避免短时间内多次请求。
         * 如果已有正在进行的提取任务，则只记录最新路径，等当前任务完成后立即启动新任务。
         */
        private fun scheduleColorExtract(videoPath: String) {
            synchronized(this) {
                pendingExtractPath = videoPath
            }
            // 如果已经在提取中，则等待当前任务完成后会检查 pendingExtractPath 并再次调度
            if (isExtracting.get()) {
                Log.d(TAG, "颜色提取已在执行中，记录最新路径: $videoPath")
                return
            }

            extractHandler.postDelayed({
                // 取出最新的待提取路径
                val pathToExtract = synchronized(this) {
                    val path = pendingExtractPath
                    pendingExtractPath = null
                    path
                }
                if (pathToExtract.isNullOrBlank() || !File(pathToExtract).exists()) {
                    Log.e(TAG, "颜色提取路径无效: $pathToExtract")
                    isExtracting.set(false)
                    return@postDelayed
                }

                isExtracting.set(true)
                Thread {
                    val success = extractColorsOnce(pathToExtract)
                    isExtracting.set(false)

                    // 提取完成后，检查是否有新的待提取路径（可能在提取期间又收到了配置变化）
                    val nextPath = synchronized(this) { pendingExtractPath }
                    if (!nextPath.isNullOrBlank() && nextPath != pathToExtract) {
                        Log.d(TAG, "提取完成后发现新的待提取路径: $nextPath，继续调度")
                        scheduleColorExtract(nextPath)
                    } else {
                        // 没有新路径，但若提取失败且当前 computedColors 仍为 null，则设置默认颜色
                        if (!success && computedColors == null) {
                            mainHandler.post {
                                setDefaultColors()
                                notifyColorsChanged()
                            }
                        }
                    }
                }.start()
            }, 300)
        }

        /**
         * 单次提取，返回是否成功。
         * 成功时更新 computedColors 并通知系统。
         * 失败时不做任何改变（保留原有颜色）。
         */
        private fun extractColorsOnce(videoPath: String): Boolean {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(videoPath)

                val framePos = resolveFrameKey()
                val durationMs = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull() ?: 0L

                val targetTimeUs = when (framePos) {
                    "FIRST" -> 0L
                    "MIDDLE" -> (durationMs / 2) * 1000L
                    "LAST" -> maxOf(0L, (durationMs - 100) * 1000L)
                    "RANDOM" -> if (durationMs > 0) Random.nextLong(0, durationMs * 1000L) else 0L
                    else -> 0L
                }

                val frame: Bitmap = retriever.getFrameAtTime(
                    targetTimeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: run {
                    Log.e(TAG, "提取帧失败: $videoPath")
                    return false
                }

                val region = resolveRegionKey()
                val cropped = cropBitmap(frame, region)
                val palette = Palette.from(cropped).maximumColorCount(16).generate()

                val tonePref = resolveToneKey()
                val primarySwatch = when (tonePref) {
                    "VIBRANT" -> palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch
                    "MUTED" -> palette.mutedSwatch ?: palette.lightMutedSwatch ?: palette.dominantSwatch
                    "DOMINANT" -> palette.dominantSwatch
                    "DARK_PREFERRED" -> palette.darkVibrantSwatch ?: palette.darkMutedSwatch ?: palette.dominantSwatch
                    "LIGHT_PREFERRED" -> palette.lightVibrantSwatch ?: palette.lightMutedSwatch ?: palette.dominantSwatch
                    else -> palette.dominantSwatch
                }

                val primaryColor = primarySwatch?.rgb ?: Color.BLUE
                val allSwatches = palette.swatches
                    .sortedByDescending { it.population }
                    .filter { it.rgb != primaryColor }

                val newColors = WallpaperColors(
                    Color.valueOf(primaryColor),
                    allSwatches.getOrNull(0)?.rgb?.let { Color.valueOf(it) },
                    allSwatches.getOrNull(1)?.rgb?.let { Color.valueOf(it) }
                )

                computedColors = newColors
                Log.d(TAG, "★ 颜色提取成功：#${Integer.toHexString(primaryColor)} (isPreview=$isPreviewEngine)")

                if (cropped !== frame) cropped.recycle()
                frame.recycle()

                mainHandler.post {
                    try {
                        notifyColorsChanged()
                    } catch (_: Exception) {
                    }
                }
                return true
            } catch (e: Exception) {
                Log.e(TAG, "颜色提取异常", e)
                return false
            } finally {
                runCatching { retriever.release() }
            }
        }

        private fun setDefaultColors() {
            computedColors = WallpaperColors(
                Color.valueOf(Color.BLUE),
                Color.valueOf(Color.GRAY),
                Color.valueOf(Color.LTGRAY)
            )
            Log.d(TAG, "使用默认颜色")
        }

        private fun cropBitmap(bitmap: Bitmap, region: String): Bitmap {
            return try {
                when (region) {
                    "CENTER" -> {
                        val ox = (bitmap.width * 0.3f).toInt().coerceAtLeast(1)
                        val oy = (bitmap.height * 0.3f).toInt().coerceAtLeast(1)
                        val w = (bitmap.width - 2 * ox).coerceAtLeast(1)
                        val h = (bitmap.height - 2 * oy).coerceAtLeast(1)
                        Bitmap.createBitmap(bitmap, ox, oy, w, h)
                    }
                    "TOP_HALF" -> {
                        val h = (bitmap.height / 2).coerceAtLeast(1)
                        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, h)
                    }
                    "BOTTOM_HALF" -> {
                        val h = (bitmap.height / 2).coerceAtLeast(1)
                        Bitmap.createBitmap(bitmap, 0, bitmap.height - h, bitmap.width, h)
                    }
                    else -> bitmap
                }
            } catch (_: Exception) {
                bitmap
            }
        }

        @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
        private fun loadAndPlay() {
            val holder = currentHolder ?: run {
                Log.d(TAG, "loadAndPlay: holder is null")
                return
            }
            val path = resolveCurrentPath()

            if (path.isNullOrBlank()) {
                Log.d(TAG, "loadAndPlay: path is null/blank, skip")
                return
            }

            val file = File(path)
            if (!file.exists()) {
                Log.e(TAG, "loadAndPlay: file not exists, path=$path")
                return
            }

            if (exoPlayer != null && currentVideoPath == path && isPlayerReady) {
                Log.d(TAG, "loadAndPlay: 已加载相同视频且状态正常，无需重建")
                return
            }

            Log.d(TAG, "loadAndPlay: 创建新播放器 (path=$path)")
            releasePlayer()
            currentVideoPath = path

            try {
                exoPlayer = ExoPlayer.Builder(applicationContext).build().apply {
                    setVideoSurface(holder.surface)
                    setMediaItem(MediaItem.fromUri(android.net.Uri.parse("file://$path")))
                    repeatMode = Player.REPEAT_MODE_ALL
                    volume = 0f

                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_READY -> {
                                    isPlayerReady = true
                                    retryCount = 0
                                    if (isVisible) play()
                                    Log.d(TAG, "播放器就绪，开始播放")
                                }
                                Player.STATE_ENDED -> {
                                    seekTo(0)
                                    play()
                                }
                                Player.STATE_BUFFERING -> {
                                    Log.d(TAG, "播放器缓冲中")
                                }
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            Log.e(TAG, "播放器错误: ${error.errorCodeName}", error)
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
                Log.e(TAG, "loadAndPlay failed", e)
                releasePlayer()
            }
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
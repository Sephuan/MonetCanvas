package com.sephuan.monetcanvas.service

import android.app.WallpaperColors
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import androidx.annotation.RequiresApi
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.sephuan.monetcanvas.data.datastore.SettingsDataStore
import com.sephuan.monetcanvas.data.model.ColorRegion
import com.sephuan.monetcanvas.data.model.FramePickPosition
import com.sephuan.monetcanvas.data.model.MonetRule
import com.sephuan.monetcanvas.data.model.TonePreference
import com.sephuan.monetcanvas.data.model.WallpaperType
import com.sephuan.monetcanvas.util.ColorEngine
import com.sephuan.monetcanvas.util.ExtractedColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference

private data class ResolvedConfig(
    val videoPath: String,
    val rule: MonetRule,
    val source: String
)

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

        // 全局互斥锁：所有 Engine 实例共享，避免并发抢视频解码器
        private val globalExtractMutex = Mutex()

        // 全局缓存：配置 + 原始提取颜色 + WallpaperColors
        private var globalCachedConfig: ResolvedConfig? = null
        private var globalCachedExtractedColors: ExtractedColors? = null
        private var globalCachedWallpaperColors: WallpaperColors? = null

        // 全局最后一次写入到 DataStore 的 seed，避免重复写
        private var globalLastSavedSeedColor: Int? = null
    }

    override fun onCreateEngine(): Engine = VideoEngine()

    inner class VideoEngine : Engine(),
        SharedPreferences.OnSharedPreferenceChangeListener {

        private val mainHandler = Handler(Looper.getMainLooper())
        private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        private val pendingExtractConfig = AtomicReference<ResolvedConfig?>(null)
        private var extractJob: kotlinx.coroutines.Job? = null

        private var exoPlayer: ExoPlayer? = null
        private var isPlayerReady = false
        private var currentVideoPath: String? = null
        private var currentHolder: SurfaceHolder? = null
        private var retryCount = 0
        private val maxRetries = 3

        private var computedColors: WallpaperColors? = null
        private var lastAppliedConfigVersion = -1L

        // ★ 仅 active engine 使用：延迟重上报的 token
        private var activeNotifyToken = 0L

        private val prefs: SharedPreferences by lazy {
            applicationContext.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        }

        private val settingsDataStore by lazy {
            SettingsDataStore(applicationContext)
        }

        private val isPreviewEngine get() = isPreview
        private val versionKey get() = if (isPreviewEngine) KEY_PENDING_VERSION else KEY_CONFIG_VERSION

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            prefs.registerOnSharedPreferenceChangeListener(this)
            lastAppliedConfigVersion = prefs.getLong(versionKey, 0L)

            val config = resolveEffectiveConfig()
            Log.d(
                TAG,
                "onCreate: isPreview=$isPreviewEngine, versionKey=$versionKey, version=$lastAppliedConfigVersion, config=${config?.videoPath}"
            )

            if (config != null) {
                currentVideoPath = config.videoPath
                startExtraction(config)
                if (surfaceHolder.surface?.isValid == true) {
                    currentHolder = surfaceHolder
                    loadAndPlay(config)
                }
            } else {
                if (computedColors == null) setDefaultColors()
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            currentHolder = holder
            val config = resolveEffectiveConfig()
            Log.d(TAG, "onSurfaceCreated: config=${config?.videoPath}")
            if (config != null) loadAndPlay(config) else releasePlayer()
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            currentHolder = holder
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            Log.d(TAG, "onVisibilityChanged: visible=$visible, isPreview=$isPreviewEngine")

            if (!visible) {
                runCatching { exoPlayer?.pause() }
                return
            }

            checkVersionDrift()

            val config = resolveEffectiveConfig()
            if (config != null) {
                val shouldReload = exoPlayer == null || config.videoPath != currentVideoPath
                Log.d(
                    TAG,
                    "onVisibilityChanged: shouldReload=$shouldReload, current=$currentVideoPath, next=${config.videoPath}"
                )
                if (shouldReload) {
                    retryCount = 0
                    loadAndPlay(config)
                } else if (!isPlayerReady && currentHolder != null) {
                    loadAndPlay(config)
                }
                startExtraction(config)
            } else {
                releasePlayer()
                currentVideoPath = null
                if (computedColors == null) {
                    setDefaultColors()
                    // ★ 只有 active engine 才允许真正上报给系统
                    if (!isPreviewEngine) {
                        notifyColorsChangedCompat()
                    }
                }
            }

            if (isPlayerReady) {
                runCatching { exoPlayer?.play() }
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            releasePlayer()
            currentHolder = null
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            Log.d(TAG, "onDestroy: isPreview=$isPreviewEngine")
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            extractJob?.cancel()
            engineScope.cancel()
            mainHandler.removeCallbacksAndMessages(null)
            releasePlayer()
            currentHolder = null
            super.onDestroy()
        }

        override fun onComputeColors(): WallpaperColors? {
            checkVersionDrift()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                val config = resolveEffectiveConfig()
                if (config != null && globalCachedConfig == config && globalCachedWallpaperColors != null) {
                    computedColors = globalCachedWallpaperColors
                    Log.d(
                        TAG,
                        "onComputeColors: 命中全局缓存 source=${config.source}, color=${globalCachedExtractedColors?.primary?.toHex()}"
                    )
                } else {
                    Log.d(
                        TAG,
                        "onComputeColors: 当前无缓存命中 config=${config?.videoPath}, source=${config?.source}"
                    )
                    // ★ 修复：缓存不匹配时尽快发起一次取色请求
                    if (config != null && globalCachedConfig != config) {
                        Log.d(TAG, "onComputeColors: 缓存与当前配置不匹配，触发补取色")
                        startExtraction(config)
                    }
                }
                return computedColors
            }
            return null
        }

        override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
            if (key != versionKey) return

            val newVersion = prefs.getLong(versionKey, 0L)
            if (newVersion == lastAppliedConfigVersion) return
            lastAppliedConfigVersion = newVersion

            val config = resolveEffectiveConfig()
            Log.d(
                TAG,
                "[$versionKey] 配置变更触发, version=$newVersion, config=${config?.videoPath}, source=${config?.source}"
            )

            if (config != null) {
                if (currentHolder != null && (exoPlayer == null || !isPlayerReady || config.videoPath != currentVideoPath)) {
                    retryCount = 0
                    loadAndPlay(config)
                }
                startExtraction(config)
            } else {
                releasePlayer()
                currentVideoPath = null
            }
        }

        private fun checkVersionDrift() {
            val currentVersion = prefs.getLong(versionKey, 0L)
            if (currentVersion != lastAppliedConfigVersion) {
                val old = lastAppliedConfigVersion
                lastAppliedConfigVersion = currentVersion
                val config = resolveEffectiveConfig()
                Log.d(
                    TAG,
                    "巡检发现版本偏移: $old -> $currentVersion, config=${config?.videoPath}, source=${config?.source}"
                )
                if (config != null) startExtraction(config)
            }
        }

        private fun resolveEffectiveConfig(): ResolvedConfig? {
            return if (isPreviewEngine) {
                readConfig(
                    KEY_PENDING_PATH,
                    KEY_PENDING_FRAME,
                    KEY_PENDING_REGION,
                    KEY_PENDING_TONE,
                    "pending"
                ) ?: readConfig(
                    KEY_LIVE_PATH,
                    KEY_FRAME_POSITION,
                    KEY_COLOR_REGION,
                    KEY_TONE_PREFERENCE,
                    "active"
                )
            } else {
                readConfig(
                    KEY_LIVE_PATH,
                    KEY_FRAME_POSITION,
                    KEY_COLOR_REGION,
                    KEY_TONE_PREFERENCE,
                    "active"
                ) ?: readConfig(
                    KEY_PENDING_PATH,
                    KEY_PENDING_FRAME,
                    KEY_PENDING_REGION,
                    KEY_PENDING_TONE,
                    "pending"
                )
            }
        }

        private fun readConfig(
            pathKey: String,
            frameKey: String,
            regionKey: String,
            toneKey: String,
            source: String
        ): ResolvedConfig? {
            val path = prefs.getString(pathKey, null)
            if (path.isNullOrBlank() || !File(path).exists()) return null

            val rule = MonetRule(
                framePosition = safeEnum(
                    prefs.getString(frameKey, "FIRST"),
                    FramePickPosition.FIRST
                ),
                colorRegion = safeEnum(
                    prefs.getString(regionKey, "FULL_FRAME"),
                    ColorRegion.FULL_FRAME
                ),
                tonePreference = safeEnum(
                    prefs.getString(toneKey, "AUTO"),
                    TonePreference.AUTO
                )
            )

            return ResolvedConfig(path, rule, source)
        }

        private inline fun <reified T : Enum<T>> safeEnum(
            value: String?,
            default: T
        ): T = runCatching { enumValueOf<T>(value!!) }.getOrDefault(default)

        /**
         * 用 pendingExtractConfig 记住“最后一次请求”。
         * 如果中途来了新请求，旧结果会被丢弃，但协程不会结束，
         * 会继续 while 循环把最新请求做完。
         */
        private fun startExtraction(config: ResolvedConfig) {
            pendingExtractConfig.set(config)
            Log.d(
                TAG,
                "startExtraction: 入队 config=${config.videoPath}, source=${config.source}, rule=${config.rule.logString()}"
            )

            if (extractJob?.isActive == true) {
                Log.d(TAG, "startExtraction: 处理协程已在运行，仅更新最后请求")
                return
            }

            extractJob = engineScope.launch {
                while (isActive) {
                    delay(150)

                    val target = pendingExtractConfig.getAndSet(null)
                    if (target == null) {
                        Log.d(TAG, "startExtraction: 队列为空，退出循环")
                        break
                    }

                    globalExtractMutex.withLock {
                        val ctx = currentCoroutineContext()
                        if (!ctx.isActive) return@withLock

                        if (pendingExtractConfig.get() != null) {
                            Log.d(
                                TAG,
                                "startExtraction: 获取全局锁后发现已有更新请求，放弃本轮 target=${target.videoPath}"
                            )
                            return@withLock
                        }

                        if (globalCachedConfig == target &&
                            globalCachedWallpaperColors != null &&
                            globalCachedExtractedColors != null
                        ) {
                            Log.d(
                                TAG,
                                "全局缓存命中, 直接应用: source=${target.source}, path=${target.videoPath}, color=${globalCachedExtractedColors?.primary?.toHex()}"
                            )
                            applyResolvedColors(
                                extracted = globalCachedExtractedColors,
                                wpColors = globalCachedWallpaperColors,
                                config = target
                            )
                            return@withLock
                        }

                        Log.d(
                            TAG,
                            "开始提取颜色(全局锁独占): path=${target.videoPath}, source=${target.source}, rule=${target.rule.logString()}"
                        )

                        try {
                            val colors = ColorEngine.extractColors(
                                target.videoPath,
                                target.rule,
                                WallpaperType.LIVE
                            )

                            Log.d(
                                TAG,
                                "ColorEngine 返回: path=${target.videoPath}, source=${target.source}, color=${colors?.primary?.toHex()}, secondary=${colors?.secondary?.toHex()}, tertiary=${colors?.tertiary?.toHex()}"
                            )

                            if (!ctx.isActive || pendingExtractConfig.get() != null) {
                                Log.d(
                                    TAG,
                                    "提取完成但已有新请求，安全丢弃旧结果: path=${target.videoPath}, color=${colors?.primary?.toHex()}"
                                )
                                return@withLock
                            }

                            if (colors != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                val wpColors = buildWallpaperColorsApi27(
                                    colors.primary,
                                    colors.secondary,
                                    colors.tertiary
                                )

                                globalCachedConfig = target
                                globalCachedExtractedColors = colors
                                globalCachedWallpaperColors = wpColors

                                applyResolvedColors(
                                    extracted = colors,
                                    wpColors = wpColors,
                                    config = target
                                )
                            } else if (computedColors == null) {
                                withContext(Dispatchers.Main) {
                                    setDefaultColors()
                                    if (!isPreviewEngine) {
                                        notifyColorsChangedCompat()
                                    }
                                }
                            }
                        } catch (e: CancellationException) {
                            Log.d(TAG, "协程取消")
                            throw e
                        } catch (e: Exception) {
                            Log.e(TAG, "提取异常: path=${target.videoPath}, source=${target.source}", e)
                        }
                    }
                }
            }
        }

        private suspend fun applyResolvedColors(
            extracted: ExtractedColors?,
            wpColors: WallpaperColors?,
            config: ResolvedConfig
        ) {
            withContext(Dispatchers.Main) {
                computedColors = wpColors

                Log.d(
                    TAG,
                    "✓ 颜色应用成功: source=${config.source}, path=${config.videoPath}, primary=${extracted?.primary?.toHex()}, secondary=${extracted?.secondary?.toHex()}, tertiary=${extracted?.tertiary?.toHex()}"
                )

                // ★ 关键修复：
                // 只有真正 active engine 才上报给系统。
                if (!isPreviewEngine && config.source == "active") {
                    notifyColorsChangedCompat()
                    scheduleActiveColorRebroadcast(config)
                } else {
                    Log.d(
                        TAG,
                        "跳过 notifyColorsChanged（preview/pending 不污染系统通道）: isPreview=$isPreviewEngine, source=${config.source}"
                    )
                }
            }

            // ★ 也只有 active engine 才能成为最终真源写回 appSeedColor
            if (!isPreviewEngine &&
                config.source == "active" &&
                extracted?.primary != null
            ) {
                maybePushSeedColorFromService(extracted.primary, config)
            }
        }

        /**
         * active engine 颜色应用后，延迟重上报两次，确保系统在 engine 切换稳定后
         * 仍然能收到最终正确颜色。
         *
         * ★ 关键修复：重上报时必须验证 globalCachedConfig 仍匹配当前 config，
         *   防止另一个壁纸的 Engine 在延迟期间更新了全局缓存导致颜色污染。
         */
        private fun scheduleActiveColorRebroadcast(config: ResolvedConfig) {
            activeNotifyToken += 1L
            val token = activeNotifyToken

            val rebroadcast = Runnable {
                if (token != activeNotifyToken) return@Runnable

                val latest = resolveEffectiveConfig()
                if (latest == null || latest != config) {
                    Log.d(TAG, "延迟重上报取消：active 配置已变化")
                    return@Runnable
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    // ★ 必须验证全局缓存仍然匹配本壁纸，防止被另一 Engine 覆盖
                    if (globalCachedConfig != config) {
                        Log.d(
                            TAG,
                            "延迟重上报取消：全局缓存已被另一壁纸覆盖, expected=${config.videoPath}, actual=${globalCachedConfig?.videoPath}"
                        )
                        return@Runnable
                    }

                    if (globalCachedWallpaperColors == null) {
                        Log.d(TAG, "延迟重上报取消：全局缓存颜色为空")
                        return@Runnable
                    }

                    computedColors = globalCachedWallpaperColors
                    Log.d(
                        TAG,
                        "延迟重上报 notifyColorsChanged: path=${config.videoPath}, color=${globalCachedExtractedColors?.primary?.toHex()}"
                    )
                    notifyColorsChangedCompat()
                }
            }

            mainHandler.postDelayed(rebroadcast, 400L)
            mainHandler.postDelayed(rebroadcast, 1200L)
        }

        private fun maybePushSeedColorFromService(primary: Int, config: ResolvedConfig) {
            // ★ 修复：全局缓存必须匹配当前壁纸，才允许写入 seed
            //   防止跨壁纸的 globalLastSavedSeedColor 污染
            if (globalCachedConfig != config) {
                Log.d(
                    TAG,
                    "maybePushSeedColorFromService 跳过：全局缓存不匹配当前壁纸, config=${config.videoPath}, cache=${globalCachedConfig?.videoPath}"
                )
                return
            }

            if (globalLastSavedSeedColor == primary) {
                Log.d(
                    TAG,
                    "Service 写入 appSeedColor 跳过（与上次相同）: ${primary.toHex()} path=${config.videoPath}"
                )
                return
            }

            engineScope.launch {
                try {
                    settingsDataStore.saveAppSeedColor(primary)
                    globalLastSavedSeedColor = primary
                    Log.d(
                        TAG,
                        "Service 已写入 appSeedColor: ${primary.toHex()} source=${config.source}, path=${config.videoPath}"
                    )
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "Service 写入 appSeedColor 失败: ${primary.toHex()} path=${config.videoPath}",
                        e
                    )
                }
            }
        }

        private fun setDefaultColors() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                computedColors = buildWallpaperColorsApi27(
                    android.graphics.Color.BLUE,
                    android.graphics.Color.GRAY,
                    android.graphics.Color.LTGRAY
                )
            }
        }

        @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
        private fun loadAndPlay(config: ResolvedConfig) {
            val holder = currentHolder ?: return
            if (holder.surface?.isValid != true) return

            val path = config.videoPath
            if (!File(path).exists()) return

            if (exoPlayer != null && currentVideoPath == path && isPlayerReady) {
                exoPlayer?.setVideoSurface(holder.surface)
                if (isVisible) exoPlayer?.play()
                Log.d(TAG, "loadAndPlay: 复用现有播放器 path=$path")
                return
            }

            releasePlayer()
            currentVideoPath = path

            Log.d(TAG, "loadAndPlay: 创建新播放器 path=$path")

            exoPlayer = ExoPlayer.Builder(applicationContext).build().apply {
                setVideoSurface(holder.surface)
                setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_READY -> {
                                isPlayerReady = true
                                retryCount = 0
                                if (isVisible) play()
                                Log.d(TAG, "播放器就绪 path=$path")
                            }

                            Player.STATE_ENDED -> {
                                seekTo(0)
                                play()
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "播放器错误: ${error.errorCodeName}, path=$path")
                        isPlayerReady = false
                        if (retryCount < maxRetries) {
                            retryCount++
                            mainHandler.postDelayed({
                                loadAndPlay(resolveEffectiveConfig() ?: return@postDelayed)
                            }, 1000)
                        }
                    }
                })
                prepare()
            }
        }

        private fun releasePlayer() {
            isPlayerReady = false
            runCatching { exoPlayer?.stop() }
            runCatching { exoPlayer?.clearVideoSurface() }
            runCatching { exoPlayer?.release() }
            exoPlayer = null
        }

        private fun notifyColorsChangedCompat() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                Log.d(TAG, "notifyColorsChanged()")
                notifyColorsChanged()
            }
        }

        @RequiresApi(Build.VERSION_CODES.O_MR1)
        private fun buildWallpaperColorsApi27(
            p: Int,
            s: Int?,
            t: Int?
        ): WallpaperColors = WallpaperColors(
            android.graphics.Color.valueOf(p),
            s?.let { android.graphics.Color.valueOf(it) },
            t?.let { android.graphics.Color.valueOf(it) }
        )

        private fun Int.toHex(): String = "#${Integer.toHexString(this)}"

        private fun MonetRule.logString(): String {
            return "frame=${framePosition.name}, region=${colorRegion.name}, tone=${tonePreference.name}, manual=${manualOverrideColor?.toHexString() ?: "null"}"
        }

        private fun Int.toHexString(): String = "#${Integer.toHexString(this)}"
    }
}
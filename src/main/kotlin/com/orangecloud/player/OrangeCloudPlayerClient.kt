package com.orangecloud.player

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.rtmp.RtmpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

/**
 * OrangeCloud 视频播放器客户端
 *
 * 基于 ExoPlayer (Media3) 实现的高性能视频播放器，提供点播/直播播放能力。
 * API 设计对齐腾讯云视立方播放器 SDK，方便迁移。
 *
 * 使用示例：
 * ```kotlin
 * // 初始化 SDK
 * OrangeCloudPlayerClient.initialize(context, appId = "your_app_id", licenseUrl = "your_license_url")
 *
 * // 创建播放器
 * val player = OrangeCloudPlayerClient(context)
 * player.setPlayerListener(listener)
 * player.setSurface(surfaceView.holder.surface)
 * player.startVodPlay("https://example.com/video.mp4")
 * ```
 */
class OrangeCloudPlayerClient(private val context: Context) {

    companion object {
        private var isInitialized = false
        private var appId: String = ""

        /**
         * 初始化 SDK
         * @param context 应用上下文
         * @param appId 应用 ID
         * @param licenseUrl License 验证地址
         */
        fun initialize(context: Context, appId: String, licenseUrl: String) {
            this.appId = appId
            isInitialized = true
        }

        /** 获取 SDK 版本号 */
        fun getSDKVersion(): String = BuildConfig.VERSION_NAME
    }

    // ── ExoPlayer 实例 ──
    private var exoPlayer: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null

    // ── 状态 ──
    private var playerState: PlayerState = PlayerState.IDLE
    private var listener: PlayerListener? = null
    private var surface: Surface? = null
    private var currentUrl: String? = null

    // ── 配置 ──
    private var isLooping: Boolean = false
    private var rate: Float = 1.0f
    private var volume: Int = 100
    private var isMuted: Boolean = false
    private var isAutoPlay: Boolean = true
    private var hardwareDecodeEnabled: Boolean = true
    private var renderMode: RenderMode = RenderMode.ADJUST_RESOLUTION
    private var rotation: Rotation = Rotation.ROTATION_0
    private var httpHeaders: Map<String, String> = emptyMap()
    private var startTimeMs: Long = 0L

    // ── 进度更新 ──
    private val mainHandler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null
    private val progressInterval = 500L
    private var firstFrameRendered = false

    // ═══════════════════════════════════════════════════════════════
    // 监听器
    // ═══════════════════════════════════════════════════════════════

    /** 设置播放器事件监听器 */
    fun setPlayerListener(listener: PlayerListener?) {
        this.listener = listener
    }

    /** 设置视频渲染 Surface */
    fun setSurface(surface: Surface?) {
        this.surface = surface
        exoPlayer?.setVideoSurface(surface)
    }

    /** 设置 SurfaceView 作为渲染目标 */
    fun setSurfaceView(surfaceView: SurfaceView) {
        exoPlayer?.setVideoSurfaceView(surfaceView)
    }

    /** 设置 TextureView 作为渲染目标 */
    fun setTextureView(textureView: TextureView) {
        exoPlayer?.setVideoTextureView(textureView)
    }

    // ═══════════════════════════════════════════════════════════════
    // 播放控制
    // ═══════════════════════════════════════════════════════════════

    /**
     * 通过 URL 开始点播播放
     * @param url 视频地址（支持 MP4/HLS/DASH/FLV/RTMP）
     * @return 是否成功启动播放
     */
    fun startVodPlay(url: String): Boolean {
        if (!isInitialized) return false
        currentUrl = url
        firstFrameRendered = false
        updateState(PlayerState.LOADING)

        ensurePlayer()
        try {
            val mediaSource = createMediaSource(url)
            exoPlayer?.apply {
                setMediaSource(mediaSource)
                prepare()
                playWhenReady = isAutoPlay
                if (startTimeMs > 0) seekTo(startTimeMs)
            }
            startProgressUpdates()
            return true
        } catch (e: Exception) {
            listener?.onError(this, 1000, "播放失败: ${e.message}")
            updateState(PlayerState.ERROR)
            return false
        }
    }

    /**
     * 通过 FileID 开始点播播放
     * @param params 认证参数
     * @return 是否成功启动播放
     */
    fun startVodPlayWithFileId(params: PlayerAuthParams): Boolean {
        if (!isInitialized) return false
        updateState(PlayerState.LOADING)
        // FileID 解析需要通过后端 API 获取播放地址
        // 实际项目中这里会调用后端签名接口获取 URL
        listener?.onError(this, 6000, "FileID 播放需要配置后端签名服务")
        return false
    }

    /**
     * 开始直播播放
     * @param url 直播流地址（支持 RTMP/FLV/HLS）
     * @return 是否成功启动播放
     */
    fun startLivePlay(url: String): Boolean {
        if (!isInitialized) return false
        currentUrl = url
        firstFrameRendered = false
        updateState(PlayerState.LOADING)

        ensurePlayer()
        try {
            val mediaSource = createMediaSource(url)
            exoPlayer?.apply {
                setMediaSource(mediaSource)
                prepare()
                playWhenReady = true
            }
            startProgressUpdates()
            return true
        } catch (e: Exception) {
            listener?.onError(this, 1000, "直播播放失败: ${e.message}")
            updateState(PlayerState.ERROR)
            return false
        }
    }

    /** 停止播放 */
    fun stopPlay() {
        stopProgressUpdates()
        exoPlayer?.apply {
            stop()
            clearMediaItems()
        }
        currentUrl = null
        firstFrameRendered = false
        updateState(PlayerState.STOPPED)
    }

    /** 暂停播放 */
    fun pause() {
        if (playerState == PlayerState.PLAYING) {
            exoPlayer?.playWhenReady = false
            updateState(PlayerState.PAUSED)
        }
    }

    /** 恢复播放 */
    fun resume() {
        if (playerState == PlayerState.PAUSED) {
            exoPlayer?.playWhenReady = true
            updateState(PlayerState.PLAYING)
        }
    }

    /**
     * 跳转到指定时间
     * @param time 目标时间（秒）
     */
    fun seek(time: Float) {
        exoPlayer?.seekTo((time * 1000).toLong())
    }

    /** 设置播放速率 (0.5 ~ 3.0) */
    fun setRate(rate: Float) {
        this.rate = rate.coerceIn(0.5f, 3.0f)
        exoPlayer?.setPlaybackSpeed(this.rate)
    }

    /** 设置音量 (0.0 ~ 1.0) */
    fun setAudioPlayoutVolume(volume: Float) {
        this.volume = (volume.coerceIn(0f, 1f) * 100).toInt()
        if (!isMuted) {
            exoPlayer?.volume = volume.coerceIn(0f, 1f)
        }
    }

    /** 设置静音 */
    fun setMute(mute: Boolean) {
        this.isMuted = mute
        exoPlayer?.volume = if (mute) 0f else volume / 100f
    }

    /** 设置渲染模式 */
    fun setRenderMode(mode: RenderMode) {
        this.renderMode = mode
    }

    /** 设置视频旋转角度 */
    fun setRenderRotation(rotation: Rotation) {
        this.rotation = rotation
    }

    /** 设置循环播放 */
    fun setLoop(loop: Boolean) {
        this.isLooping = loop
        exoPlayer?.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    /** 设置是否自动播放（默认 true） */
    fun setAutoPlay(auto: Boolean) {
        this.isAutoPlay = auto
    }

    /** 设置起播时间（秒） */
    fun setStartTime(time: Float) {
        this.startTimeMs = (time * 1000).toLong()
    }

    /** 设置自定义 HTTP Headers */
    fun setHeaders(headers: Map<String, String>) {
        this.httpHeaders = headers
    }

    /** 启用/禁用硬件解码 */
    fun enableHardwareDecode(enable: Boolean) {
        if (hardwareDecodeEnabled == enable) return
        hardwareDecodeEnabled = enable
        // 需要重建播放器才能生效
        val wasPlaying = exoPlayer?.isPlaying == true
        val pos = exoPlayer?.currentPosition ?: 0L
        val url = currentUrl
        releasePlayer()
        if (url != null && wasPlaying) {
            startTimeMs = pos
            startVodPlay(url)
        }
    }

    /** 截图 */
    fun snapshot(callback: (Bitmap?) -> Unit) {
        // ExoPlayer 不直接支持截图，需要通过 TextureView 获取
        // 实际项目中建议使用 TextureView.getBitmap()
        callback(null)
    }

    /** 获取当前播放时间（秒） */
    fun getCurrentPlayTime(): Float = (exoPlayer?.currentPosition ?: 0L) / 1000f

    /** 获取视频总时长（秒） */
    fun getDuration(): Float {
        val duration = exoPlayer?.duration ?: C.TIME_UNSET
        return if (duration == C.TIME_UNSET) 0f else duration / 1000f
    }

    /** 获取缓冲位置（秒） */
    fun getBufferedPosition(): Float = (exoPlayer?.bufferedPosition ?: 0L) / 1000f

    /** 获取缓冲百分比 */
    fun getBufferedPercentage(): Int = exoPlayer?.bufferedPercentage ?: 0

    /** 获取当前是否正在播放 */
    fun isPlaying(): Boolean = exoPlayer?.isPlaying ?: false

    /** 获取当前播放状态 */
    fun getPlayerState(): PlayerState = playerState

    /** 获取当前播放 URL */
    fun getPlayUrl(): String? = currentUrl

    /** 切换清晰度（HLS 多码率） */
    fun switchResolution(index: Int) {
        // 通过 TrackSelector 切换 HLS 码率
        trackSelector?.let { selector ->
            val params = selector.buildUponParameters()
                .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                .build()
            selector.setParameters(params)
        }
    }

    /** 兼容腾讯云 API 名称 */
    fun setBitrateIndex(index: Int) = switchResolution(index)

    /** 释放播放器资源 */
    fun release() {
        stopPlay()
        releasePlayer()
        listener = null
        surface = null
    }

    // ═══════════════════════════════════════════════════════════════
    // 内部实现
    // ═══════════════════════════════════════════════════════════════

    private fun ensurePlayer() {
        if (exoPlayer != null) return

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(
                if (hardwareDecodeEnabled) DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
            )
            setEnableDecoderFallback(true)
        }

        trackSelector = DefaultTrackSelector(context)

        exoPlayer = ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector!!)
            .build().apply {
                addListener(playerListener)
                volume = if (isMuted) 0f else this@OrangeCloudPlayerClient.volume / 100f
                repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                setPlaybackSpeed(rate)
            }

        surface?.let { exoPlayer?.setVideoSurface(it) }
    }

    private fun releasePlayer() {
        stopProgressUpdates()
        exoPlayer?.apply {
            removeListener(playerListener)
            setVideoSurface(null)
            stop()
            release()
        }
        exoPlayer = null
        trackSelector = null
    }

    private fun createMediaSource(url: String): MediaSource {
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase() ?: ""
        val path = uri.path?.lowercase() ?: ""

        if (scheme == "rtmp") {
            val factory = RtmpDataSource.Factory()
            return ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(uri))
        }

        val httpFactory = DefaultHttpDataSource.Factory().apply {
            setDefaultRequestProperties(httpHeaders)
            setConnectTimeoutMs(15_000)
            setReadTimeoutMs(15_000)
            setAllowCrossProtocolRedirects(true)
        }
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)

        return when {
            path.endsWith(".m3u8") || path.contains("/hls/") ->
                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri))
            path.endsWith(".mpd") || path.contains("/dash/") ->
                DashMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri))
            path.endsWith(".flv") ->
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.Builder().setUri(uri).setMimeType(MimeTypes.VIDEO_FLV).build())
            else ->
                ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri))
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    updateState(PlayerState.LOADING)
                    listener?.onBuffering(this@OrangeCloudPlayerClient, true, getBufferedPercentage())
                }
                Player.STATE_READY -> {
                    if (!firstFrameRendered) {
                        firstFrameRendered = true
                        listener?.onFirstFrameRendered(this@OrangeCloudPlayerClient)
                    }
                    listener?.onBuffering(this@OrangeCloudPlayerClient, false, getBufferedPercentage())
                    if (exoPlayer?.playWhenReady == true) updateState(PlayerState.PLAYING)
                }
                Player.STATE_ENDED -> {
                    stopProgressUpdates()
                    updateState(PlayerState.ENDED)
                    listener?.onPlayEnd(this@OrangeCloudPlayerClient)
                }
                Player.STATE_IDLE -> {}
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) updateState(PlayerState.PLAYING)
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                listener?.onVideoSizeChanged(this@OrangeCloudPlayerClient, videoSize.width, videoSize.height)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            stopProgressUpdates()
            val code = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> 1001
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> 1002
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> 2001
                PlaybackException.ERROR_CODE_DECODING_FAILED -> 2002
                PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> 3001
                else -> 8001
            }
            listener?.onError(this@OrangeCloudPlayerClient, code, error.message ?: "Unknown error")
            updateState(PlayerState.ERROR)
        }

        override fun onRenderedFirstFrame() {
            if (!firstFrameRendered) {
                firstFrameRendered = true
                listener?.onFirstFrameRendered(this@OrangeCloudPlayerClient)
            }
        }
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressRunnable = object : Runnable {
            override fun run() {
                exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        val pos = player.currentPosition / 1000f
                        val dur = if (player.duration == C.TIME_UNSET) 0f else player.duration / 1000f
                        val buf = player.bufferedPosition / 1000f
                        listener?.onPlayProgress(this@OrangeCloudPlayerClient, pos, dur, buf)
                    }
                }
                mainHandler.postDelayed(this, progressInterval)
            }
        }
        mainHandler.postDelayed(progressRunnable!!, progressInterval)
    }

    private fun stopProgressUpdates() {
        progressRunnable?.let { mainHandler.removeCallbacks(it) }
        progressRunnable = null
    }

    private fun updateState(newState: PlayerState) {
        val oldState = playerState
        playerState = newState
        listener?.onPlayerStateChanged(this, oldState, newState)
    }
}

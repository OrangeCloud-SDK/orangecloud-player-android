package com.orangecloud.player

/**
 * 视频渲染模式
 */
enum class RenderMode {
    /** 自适应（保持宽高比，可能有黑边） */
    ADJUST_RESOLUTION,
    /** 铺满（可能裁剪） */
    FILL_SCREEN,
    /** 全屏拉伸（可能变形） */
    FULL_FILL_SCREEN
}

/**
 * 视频旋转角度
 */
enum class Rotation {
    ROTATION_0,
    ROTATION_90,
    ROTATION_180,
    ROTATION_270
}

/**
 * 播放器状态
 */
enum class PlayerState {
    IDLE,
    LOADING,
    PLAYING,
    PAUSED,
    STOPPED,
    ENDED,
    ERROR
}

/**
 * 播放器事件类型
 */
enum class PlayerEvent {
    /** 播放开始 */
    PLAY_BEGIN,
    /** 播放进度变化 */
    PLAY_PROGRESS,
    /** 播放结束 */
    PLAY_END,
    /** 加载开始 */
    PLAY_LOADING,
    /** 缓冲中 */
    BUFFERING,
    /** 缓冲结束 */
    BUFFERING_END,
    /** 首帧渲染 */
    FIRST_FRAME_RENDERED,
    /** 分辨率变化 */
    RESOLUTION_CHANGED,
    /** 错误 */
    ERROR,
    /** 网络状态变化 */
    NET_STATUS
}

/**
 * 播放器认证参数（FileID 播放方式）
 */
data class PlayerAuthParams(
    val appId: Int,
    val fileId: String,
    val sign: String,
    val timeout: Long = 0,
    val exper: Int = 0,
    val us: String = ""
)

/**
 * 播放器错误
 */
data class PlayerError(
    val code: Int,
    val message: String,
    val extra: Map<String, Any>? = null
)

/**
 * 网络状态数据
 */
data class NetStatusData(
    val cpuUsage: Float = 0f,
    val videoBitrate: Int = 0,
    val audioBitrate: Int = 0,
    val videoFps: Int = 0,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val netSpeed: Int = 0,
    val cacheSize: Long = 0
)

/**
 * 播放质量数据
 */
data class PlayQualityData(
    val url: String = "",
    val firstFrameTime: Long = 0,
    val bufferingCount: Int = 0,
    val bufferingDuration: Long = 0,
    val totalDuration: Long = 0,
    val playDuration: Long = 0
)

/**
 * 下载任务状态
 */
enum class DownloadState {
    IDLE,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * 下载任务信息
 */
data class DownloadTask(
    val url: String,
    val filePath: String = "",
    val totalSize: Long = 0,
    val downloadedSize: Long = 0,
    val state: DownloadState = DownloadState.IDLE,
    val progress: Float = 0f
)

/**
 * 缓冲状态
 */
data class BufferStatus(
    val currentBufferSize: Long = 0,
    val maxBufferSize: Long = 0,
    val isBuffering: Boolean = false,
    val bufferedPercentage: Int = 0
)

/**
 * 清晰度定义
 */
data class VideoQuality(
    val index: Int,
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val name: String = ""
)

/**
 * 画中画模式
 */
enum class PIPMode {
    /** 未激活 */
    INACTIVE,
    /** App 内小窗 */
    IN_APP,
    /** 系统级画中画 */
    SYSTEM
}

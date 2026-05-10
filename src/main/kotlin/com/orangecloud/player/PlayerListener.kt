package com.orangecloud.player

/**
 * 播放器事件回调接口
 *
 * 开发者通过实现此接口来监听播放器的各种事件，
 * 包括播放状态变化、进度更新、错误通知等。
 *
 * API 对齐腾讯云视立方播放器 SDK 的 ITXVodPlayListener。
 */
interface PlayerListener {

    /**
     * 播放状态变化回调
     * @param player 播放器实例
     * @param oldState 旧状态
     * @param newState 新状态
     */
    fun onPlayerStateChanged(player: OrangeCloudPlayerClient, oldState: PlayerState, newState: PlayerState) {}

    /**
     * 播放进度回调（每 500ms 触发一次）
     * @param player 播放器实例
     * @param currentTime 当前播放时间（秒）
     * @param duration 总时长（秒）
     * @param bufferedPosition 缓冲位置（秒）
     */
    fun onPlayProgress(player: OrangeCloudPlayerClient, currentTime: Float, duration: Float, bufferedPosition: Float) {}

    /**
     * 播放结束回调
     * @param player 播放器实例
     */
    fun onPlayEnd(player: OrangeCloudPlayerClient) {}

    /**
     * 首帧渲染回调
     * @param player 播放器实例
     */
    fun onFirstFrameRendered(player: OrangeCloudPlayerClient) {}

    /**
     * 视频尺寸变化回调
     * @param player 播放器实例
     * @param width 视频宽度
     * @param height 视频高度
     */
    fun onVideoSizeChanged(player: OrangeCloudPlayerClient, width: Int, height: Int) {}

    /**
     * 缓冲状态变化回调
     * @param player 播放器实例
     * @param isBuffering 是否正在缓冲
     * @param percentage 缓冲百分比
     */
    fun onBuffering(player: OrangeCloudPlayerClient, isBuffering: Boolean, percentage: Int) {}

    /**
     * 错误回调
     * @param player 播放器实例
     * @param code 错误码
     * @param message 错误信息
     */
    fun onError(player: OrangeCloudPlayerClient, code: Int, message: String) {}

    /**
     * 网络状态回调
     * @param player 播放器实例
     * @param status 网络状态数据
     */
    fun onNetStatus(player: OrangeCloudPlayerClient, status: NetStatusData) {}
}

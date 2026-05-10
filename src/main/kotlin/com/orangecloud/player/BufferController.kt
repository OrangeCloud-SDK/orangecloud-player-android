package com.orangecloud.player

/**
 * 缓冲控制器
 *
 * 精准控制预加载缓冲和播放缓冲大小，
 * 在流量消耗和播放流畅度之间取得最佳平衡。
 */
class BufferController {

    private var maxPreloadSize: Long = 5 * 1024 * 1024 // 默认 5MB
    private var maxBufferSize: Long = 20 * 1024 * 1024 // 默认 20MB
    private var currentBufferSize: Long = 0
    private var isBuffering: Boolean = false
    private var resumeThreshold: Float = 0.3f // 缓冲恢复阈值
    private var pauseThreshold: Float = 0.9f // 缓冲暂停阈值

    /**
     * 设置最大预加载缓冲大小（字节）
     * @param size 最大预加载大小
     */
    fun setMaxPreloadSize(size: Long) {
        this.maxPreloadSize = size.coerceAtLeast(0)
    }

    /**
     * 获取最大预加载缓冲大小
     */
    fun getMaxPreloadSize(): Long = maxPreloadSize

    /**
     * 设置最大播放缓冲大小（字节）
     * @param size 最大缓冲大小
     */
    fun setMaxBufferSize(size: Long) {
        this.maxBufferSize = size.coerceAtLeast(0)
    }

    /**
     * 获取最大播放缓冲大小
     */
    fun getMaxBufferSize(): Long = maxBufferSize

    /**
     * 设置缓冲恢复阈值
     * @param threshold 阈值比例（0.0 ~ 1.0）
     */
    fun setResumeThreshold(threshold: Float) {
        this.resumeThreshold = threshold.coerceIn(0f, 1f)
    }

    /**
     * 设置缓冲暂停阈值
     * @param threshold 阈值比例（0.0 ~ 1.0）
     */
    fun setPauseThreshold(threshold: Float) {
        this.pauseThreshold = threshold.coerceIn(0f, 1f)
    }

    /**
     * 更新当前缓冲大小
     * @param size 当前缓冲字节数
     */
    fun updateBufferSize(size: Long) {
        this.currentBufferSize = size
    }

    /**
     * 判断是否应该暂停缓冲
     * @return true 表示应暂停缓冲
     */
    fun shouldPauseBuffering(): Boolean {
        if (maxBufferSize <= 0) return false
        return currentBufferSize.toFloat() / maxBufferSize >= pauseThreshold
    }

    /**
     * 判断是否应该恢复缓冲
     * @return true 表示应恢复缓冲
     */
    fun shouldResumeBuffering(): Boolean {
        if (maxBufferSize <= 0) return true
        return currentBufferSize.toFloat() / maxBufferSize <= resumeThreshold
    }

    /**
     * 获取当前缓冲状态
     */
    fun getBufferStatus(): BufferStatus {
        val percentage = if (maxBufferSize > 0) {
            ((currentBufferSize.toFloat() / maxBufferSize) * 100).toInt().coerceIn(0, 100)
        } else 0

        return BufferStatus(
            currentBufferSize = currentBufferSize,
            maxBufferSize = maxBufferSize,
            isBuffering = isBuffering,
            bufferedPercentage = percentage
        )
    }

    /**
     * 设置缓冲状态
     */
    fun setBuffering(buffering: Boolean) {
        this.isBuffering = buffering
    }

    /**
     * 重置缓冲控制器
     */
    fun reset() {
        currentBufferSize = 0
        isBuffering = false
    }
}

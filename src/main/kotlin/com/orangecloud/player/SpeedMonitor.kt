package com.orangecloud.player

import kotlinx.coroutines.*

/**
 * 网速监控器
 *
 * 实时监测下载速度并上报，每秒计算当前网速（KB/s）。
 */
class SpeedMonitor {

    /**
     * 网速回调接口
     */
    interface SpeedListener {
        /**
         * 网速更新回调
         * @param speedKBps 当前网速（KB/s）
         */
        fun onSpeedUpdate(speedKBps: Float)
    }

    private var listener: SpeedListener? = null
    private var isMonitoring: Boolean = false
    private var totalBytesInWindow: Long = 0
    private var windowStartTime: Long = 0
    private var currentSpeedKBps: Float = 0f
    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * 设置网速监听器
     */
    fun setSpeedListener(listener: SpeedListener?) {
        this.listener = listener
    }

    /**
     * 开始监控
     */
    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        windowStartTime = System.currentTimeMillis()
        totalBytesInWindow = 0

        monitorJob = scope.launch {
            while (isActive && isMonitoring) {
                delay(1000) // 每秒计算一次
                calculateSpeed()
            }
        }
    }

    /**
     * 停止监控
     */
    fun stopMonitoring() {
        isMonitoring = false
        monitorJob?.cancel()
        monitorJob = null
        currentSpeedKBps = 0f
    }

    /**
     * 上报已下载字节数（由下载模块调用）
     * @param bytes 本次下载的字节数
     */
    fun reportBytes(bytes: Long) {
        totalBytesInWindow += bytes
    }

    /**
     * 获取当前网速（KB/s）
     */
    fun getCurrentSpeed(): Float = currentSpeedKBps

    /**
     * 是否正在监控
     */
    fun isActive(): Boolean = isMonitoring

    /**
     * 释放资源
     */
    fun release() {
        stopMonitoring()
        scope.cancel()
        listener = null
    }

    private fun calculateSpeed() {
        val now = System.currentTimeMillis()
        val elapsed = (now - windowStartTime).toFloat() / 1000f // 秒
        if (elapsed > 0) {
            currentSpeedKBps = totalBytesInWindow.toFloat() / 1024f / elapsed
        }
        // 重置窗口
        totalBytesInWindow = 0
        windowStartTime = now
        listener?.onSpeedUpdate(currentSpeedKBps)
    }
}

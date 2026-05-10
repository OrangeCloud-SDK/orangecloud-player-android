package com.orangecloud.player

import android.content.Context
import kotlinx.coroutines.*

/**
 * 播放器实例池
 *
 * 管理播放器实例的复用，避免频繁创建销毁带来的性能开销。
 * 支持 acquire/release 接口，优先复用空闲实例。
 */
class PlayerPool(
    private val context: Context,
    private val maxPoolSize: Int = 3
) {

    private data class PoolEntry(
        val player: OrangeCloudPlayerClient,
        var isIdle: Boolean = true,
        var lastReleaseTime: Long = System.currentTimeMillis()
    )

    private val pool = mutableListOf<PoolEntry>()
    private var idleTimeoutMs: Long = 30_000 // 默认 30 秒空闲超时
    private var cleanupJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * 设置空闲超时时间（毫秒）
     * @param timeoutMs 超时时间
     */
    fun setIdleTimeout(timeoutMs: Long) {
        this.idleTimeoutMs = timeoutMs.coerceAtLeast(5000)
    }

    /**
     * 获取一个播放器实例（优先复用空闲实例）
     * @return 播放器实例
     */
    fun acquire(): OrangeCloudPlayerClient {
        // 优先复用空闲实例
        val idleEntry = pool.find { it.isIdle }
        if (idleEntry != null) {
            idleEntry.isIdle = false
            return idleEntry.player
        }

        // 池已满，LRU 销毁最久未使用的空闲实例
        if (pool.size >= maxPoolSize) {
            val oldest = pool.filter { it.isIdle }
                .minByOrNull { it.lastReleaseTime }
            if (oldest != null) {
                oldest.player.release()
                pool.remove(oldest)
            }
        }

        // 创建新实例
        val player = OrangeCloudPlayerClient(context)
        val entry = PoolEntry(player = player, isIdle = false)
        pool.add(entry)
        return player
    }

    /**
     * 归还播放器实例到池中
     * @param player 播放器实例
     */
    fun release(player: OrangeCloudPlayerClient) {
        val entry = pool.find { it.player === player }
        if (entry != null) {
            // 重置播放状态
            player.stopPlay()
            entry.isIdle = true
            entry.lastReleaseTime = System.currentTimeMillis()
        }
        startCleanupIfNeeded()
    }

    /**
     * 获取当前池中实例数量
     */
    fun getPoolSize(): Int = pool.size

    /**
     * 获取当前空闲实例数量
     */
    fun getIdleCount(): Int = pool.count { it.isIdle }

    /**
     * 获取当前活跃实例数量
     */
    fun getActiveCount(): Int = pool.count { !it.isIdle }

    /**
     * 释放所有实例并清空池
     */
    fun releaseAll() {
        cleanupJob?.cancel()
        pool.forEach { it.player.release() }
        pool.clear()
        scope.cancel()
    }

    /**
     * 启动空闲超时清理任务
     */
    private fun startCleanupIfNeeded() {
        if (cleanupJob?.isActive == true) return
        cleanupJob = scope.launch {
            while (isActive) {
                delay(idleTimeoutMs)
                cleanupIdleInstances()
            }
        }
    }

    /**
     * 清理超时的空闲实例
     */
    private fun cleanupIdleInstances() {
        val now = System.currentTimeMillis()
        val expired = pool.filter {
            it.isIdle && (now - it.lastReleaseTime) > idleTimeoutMs
        }
        expired.forEach { entry ->
            entry.player.release()
            pool.remove(entry)
        }
        if (pool.none { it.isIdle }) {
            cleanupJob?.cancel()
            cleanupJob = null
        }
    }
}

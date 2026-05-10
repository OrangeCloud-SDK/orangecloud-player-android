package com.orangecloud.player

import android.content.Context

/**
 * 缓存管理器
 *
 * 负责边播边缓存策略和本地缓存文件管理，
 * 使用 LRU 淘汰策略控制缓存容量。
 */
class CacheManager(private val context: Context) {

    private var maxCacheSize: Long = 500 * 1024 * 1024 // 默认 500MB
    private var cacheDir: String = ""
    private val cacheEntries = LinkedHashMap<String, CacheEntry>(16, 0.75f, true)

    data class CacheEntry(
        val url: String,
        val filePath: String,
        val size: Long,
        val lastAccessTime: Long = System.currentTimeMillis()
    )

    init {
        cacheDir = context.cacheDir.absolutePath + "/orangecloud_player_cache"
    }

    /**
     * 设置最大缓存容量（字节）
     * @param size 最大缓存大小
     */
    fun setMaxCacheSize(size: Long) {
        this.maxCacheSize = size.coerceAtLeast(0)
    }

    /**
     * 获取最大缓存容量
     */
    fun getMaxCacheSize(): Long = maxCacheSize

    /**
     * 设置缓存目录
     * @param dir 缓存目录路径
     */
    fun setCacheDir(dir: String) {
        this.cacheDir = dir
    }

    /**
     * 获取缓存目录
     */
    fun getCacheDir(): String = cacheDir

    /**
     * 检查 URL 是否已缓存
     * @param url 视频地址
     * @return 是否已缓存
     */
    fun isCached(url: String): Boolean {
        return cacheEntries.containsKey(url)
    }

    /**
     * 获取缓存文件路径
     * @param url 视频地址
     * @return 缓存文件路径，未缓存返回 null
     */
    fun getCachedFilePath(url: String): String? {
        return cacheEntries[url]?.filePath
    }

    /**
     * 添加缓存条目
     * @param url 视频地址
     * @param filePath 本地文件路径
     * @param size 文件大小
     */
    fun addCache(url: String, filePath: String, size: Long) {
        cacheEntries[url] = CacheEntry(url = url, filePath = filePath, size = size)
        evictIfNeeded()
    }

    /**
     * 删除指定 URL 的缓存
     * @param url 视频地址
     */
    fun removeCache(url: String) {
        val entry = cacheEntries.remove(url)
        // 删除本地文件
        if (entry != null) {
            try {
                java.io.File(entry.filePath).delete()
            } catch (_: Exception) {}
        }
    }

    /**
     * 清除所有缓存
     */
    fun clearAllCache() {
        // 删除所有缓存文件
        for (entry in cacheEntries.values) {
            try {
                java.io.File(entry.filePath).delete()
            } catch (_: Exception) {}
        }
        cacheEntries.clear()
        // 清空缓存目录
        try {
            val dir = java.io.File(cacheDir)
            dir.listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {}
    }

    /**
     * 获取当前缓存总大小（字节）
     */
    fun getCurrentCacheSize(): Long {
        return cacheEntries.values.sumOf { it.size }
    }

    /**
     * 获取缓存条目数量
     */
    fun getCacheCount(): Int = cacheEntries.size

    /**
     * LRU 淘汰：当缓存超出最大容量时，移除最久未访问的条目
     */
    private fun evictIfNeeded() {
        while (getCurrentCacheSize() > maxCacheSize && cacheEntries.isNotEmpty()) {
            val oldest = cacheEntries.entries.firstOrNull() ?: break
            // 删除本地文件
            try {
                java.io.File(oldest.value.filePath).delete()
            } catch (_: Exception) {}
            cacheEntries.remove(oldest.key)
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        cacheEntries.clear()
    }
}

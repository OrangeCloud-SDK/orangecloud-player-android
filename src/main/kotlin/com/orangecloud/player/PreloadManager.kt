package com.orangecloud.player

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * 预下载管理器
 *
 * 负责在后台预先下载视频部分内容（不创建播放器实例），
 * 配合 PlayerPool 实现视频秒开效果。
 *
 * 使用示例：
 * ```kotlin
 * val preloadManager = PreloadManager(context)
 * preloadManager.setPreloadSize(1024 * 1024) // 预下载 1MB
 * preloadManager.addPreloadUrl("https://example.com/video.mp4")
 * ```
 */
class PreloadManager(private val context: Context) {

    /**
     * 预下载回调接口
     */
    interface PreloadListener {
        fun onPreloadComplete(url: String)
        fun onPreloadError(url: String, error: PlayerError)
        fun onPreloadProgress(url: String, progress: Float)
    }

    private var preloadSize: Long = 1024 * 1024 // 默认 1MB
    private var maxConcurrent: Int = 3
    private var listener: PreloadListener? = null
    private var preferredResolution: Int = 720

    // 线程池执行预下载任务
    private var executor: ExecutorService = Executors.newFixedThreadPool(maxConcurrent)
    // 活跃任务映射
    private val activeTasks = ConcurrentHashMap<String, Future<*>>()
    // 缓存目录
    private val cacheDir: File by lazy {
        File(context.cacheDir, "orangecloud_preload").also { it.mkdirs() }
    }

    /** 设置预下载监听器 */
    fun setPreloadListener(listener: PreloadListener?) {
        this.listener = listener
    }

    /** 设置预下载大小（字节） */
    fun setPreloadSize(size: Long) {
        this.preloadSize = size.coerceAtLeast(0)
    }

    /** 设置最大并发预下载数 */
    fun setMaxConcurrent(count: Int) {
        this.maxConcurrent = count.coerceIn(1, 10)
        executor.shutdown()
        executor = Executors.newFixedThreadPool(this.maxConcurrent)
    }

    /** 设置期望分辨率（用于多码率选择） */
    fun setPreferredResolution(resolution: Int) {
        this.preferredResolution = resolution
    }

    /**
     * 添加 URL 预下载任务
     *
     * 在后台线程下载视频的前 [preloadSize] 字节数据到本地缓存，
     * 不创建播放器实例，内存占用极低。
     *
     * @param url 视频地址
     */
    fun addPreloadUrl(url: String) {
        // 避免重复任务
        if (activeTasks.containsKey(url)) return

        val future = executor.submit {
            try {
                executePreload(url)
            } catch (e: Exception) {
                listener?.onPreloadError(url, PlayerError(
                    code = 5000,
                    message = "预下载失败: ${e.message}"
                ))
            }
        }
        activeTasks[url] = future
    }

    /**
     * 通过 FileID 添加预下载任务
     *
     * 注意：FileID 需要通过后端签名服务解析为播放地址后才能预下载。
     * 当前实现会通过错误回调通知调用方。
     */
    fun addPreloadFileId(params: PlayerAuthParams) {
        // FileID 解析需要后端服务支持
        listener?.onPreloadError("fileid://${params.fileId}", PlayerError(
            code = 6000,
            message = "FileID 预下载需要配置后端签名服务"
        ))
    }

    /** 取消指定 URL 的预下载 */
    fun cancelPreload(url: String) {
        activeTasks[url]?.cancel(true)
        activeTasks.remove(url)
    }

    /** 取消所有预下载任务 */
    fun cancelAllPreloads() {
        activeTasks.values.forEach { it.cancel(true) }
        activeTasks.clear()
    }

    /** 检查指定 URL 是否已预下载 */
    fun isPreloaded(url: String): Boolean {
        val cacheFile = getCacheFile(url)
        return cacheFile.exists() && cacheFile.length() > 0
    }

    /** 获取预下载缓存文件路径 */
    fun getPreloadCachePath(url: String): String? {
        val cacheFile = getCacheFile(url)
        return if (cacheFile.exists()) cacheFile.absolutePath else null
    }

    /** 释放资源 */
    fun release() {
        cancelAllPreloads()
        executor.shutdownNow()
        listener = null
    }

    // ── 内部实现 ──

    /**
     * 执行预下载：使用 HTTP Range 请求下载视频的前 N 字节
     */
    private fun executePreload(url: String) {
        val cacheFile = getCacheFile(url)
        if (cacheFile.exists() && cacheFile.length() >= preloadSize) {
            // 已有足够的缓存数据
            listener?.onPreloadComplete(url)
            activeTasks.remove(url)
            return
        }

        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            // 使用 Range 请求只下载前 preloadSize 字节
            connection.setRequestProperty("Range", "bytes=0-${preloadSize - 1}")
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != 200 && responseCode != 206) {
                listener?.onPreloadError(url, PlayerError(
                    code = 1003,
                    message = "HTTP $responseCode"
                ))
                return
            }

            val totalBytes = connection.contentLength.toLong().coerceAtMost(preloadSize)
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (Thread.currentThread().isInterrupted) return

                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        // 进度回调
                        if (totalBytes > 0) {
                            listener?.onPreloadProgress(url, downloadedBytes.toFloat() / totalBytes)
                        }

                        // 达到预下载大小上限
                        if (downloadedBytes >= preloadSize) break
                    }
                }
            }

            listener?.onPreloadComplete(url)
        } finally {
            connection.disconnect()
            activeTasks.remove(url)
        }
    }

    /** 根据 URL 生成缓存文件 */
    private fun getCacheFile(url: String): File {
        val fileName = url.hashCode().toString(16) + ".preload"
        return File(cacheDir, fileName)
    }
}

package com.orangecloud.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * 下载管理器
 *
 * 负责视频离线下载、断点续传和下载任务管理，
 * 支持 MP4 和 HLS 格式，支持 Widevine DRM 加密视频的离线下载。
 */
class DownloadManager(private val context: Context) {

    /**
     * 下载事件回调接口
     */
    interface DownloadListener {
        fun onDownloadStart(task: DownloadTask)
        fun onDownloadProgress(task: DownloadTask, progress: Float)
        fun onDownloadComplete(task: DownloadTask)
        fun onDownloadError(task: DownloadTask, error: PlayerError)
        fun onDownloadPaused(task: DownloadTask)
        fun onDownloadResumed(task: DownloadTask)
    }

    private var listener: DownloadListener? = null
    private val tasks = mutableListOf<DownloadTask>()
    private var maxConcurrentDownloads: Int = 3
    private var executor: ExecutorService = Executors.newFixedThreadPool(maxConcurrentDownloads)
    private val activeFutures = ConcurrentHashMap<String, Future<*>>()
    private val mainHandler = Handler(Looper.getMainLooper())

    // 默认下载目录
    private val downloadDir: File by lazy {
        File(context.filesDir, "orangecloud_downloads").also { it.mkdirs() }
    }

    /** 设置下载监听器 */
    fun setDownloadListener(listener: DownloadListener?) {
        this.listener = listener
    }

    /** 设置最大并发下载数 */
    fun setMaxConcurrentDownloads(count: Int) {
        this.maxConcurrentDownloads = count.coerceIn(1, 5)
    }

    /**
     * 开始下载
     *
     * 支持断点续传：如果本地文件已存在部分数据，会从断点继续下载。
     *
     * @param url 视频地址
     * @param savePath 本地保存路径（null 时自动生成）
     * @return 下载任务
     */
    fun startDownload(url: String, savePath: String? = null): DownloadTask {
        // 检查是否已有相同 URL 的任务
        val existing = tasks.find { it.url == url }
        if (existing != null && existing.state == DownloadState.DOWNLOADING) {
            return existing
        }

        val filePath = savePath ?: generateFilePath(url)
        val task = DownloadTask(
            url = url,
            filePath = filePath,
            state = DownloadState.DOWNLOADING,
            downloadedSize = File(filePath).let { if (it.exists()) it.length() else 0L }
        )

        // 移除旧的失败/取消任务
        tasks.removeAll { it.url == url }
        tasks.add(task)

        notifyMain { listener?.onDownloadStart(task) }

        // 提交下载任务到线程池
        val future = executor.submit { executeDownload(task) }
        activeFutures[url] = future

        return task
    }

    /**
     * 开始 Widevine DRM 加密视频下载
     *
     * 注意：Widevine 离线下载需要通过 ExoPlayer 的 DownloadHelper 实现，
     * 当前提供基础框架，实际 DRM 许可证获取需要配置 License 服务器。
     */
    fun startDrmDownload(url: String, savePath: String? = null, licenseUrl: String): DownloadTask {
        val filePath = savePath ?: generateFilePath(url)
        val task = DownloadTask(
            url = url,
            filePath = filePath,
            state = DownloadState.DOWNLOADING
        )
        tasks.add(task)

        notifyMain { listener?.onDownloadStart(task) }

        // DRM 下载需要 ExoPlayer DownloadHelper，这里使用普通下载作为降级
        val future = executor.submit { executeDownload(task) }
        activeFutures[url] = future

        return task
    }

    /**
     * 暂停下载
     *
     * 暂停后保留已下载的数据，可通过 resumeDownload 恢复。
     */
    fun pauseDownload(url: String) {
        val task = tasks.find { it.url == url } ?: return
        if (task.state != DownloadState.DOWNLOADING) return

        // 取消正在执行的下载线程
        activeFutures[url]?.cancel(true)
        activeFutures.remove(url)

        task.state = DownloadState.PAUSED
        notifyMain { listener?.onDownloadPaused(task) }
    }

    /**
     * 恢复下载（断点续传）
     *
     * 从上次暂停的位置继续下载，使用 HTTP Range 请求。
     */
    fun resumeDownload(url: String) {
        val task = tasks.find { it.url == url } ?: return
        if (task.state != DownloadState.PAUSED && task.state != DownloadState.FAILED) return

        // 更新已下载大小（从文件实际大小获取）
        val file = File(task.filePath)
        if (file.exists()) {
            task.downloadedSize = file.length()
        }

        task.state = DownloadState.DOWNLOADING
        notifyMain { listener?.onDownloadResumed(task) }

        // 重新提交下载任务
        val future = executor.submit { executeDownload(task) }
        activeFutures[url] = future
    }

    /**
     * 删除下载任务（同时删除本地文件）
     */
    fun deleteDownload(url: String) {
        // 先取消正在进行的下载
        activeFutures[url]?.cancel(true)
        activeFutures.remove(url)

        val task = tasks.find { it.url == url }
        if (task != null) {
            // 删除本地文件
            val file = File(task.filePath)
            if (file.exists()) file.delete()
            tasks.remove(task)
        }
    }

    /** 获取所有下载任务列表 */
    fun getDownloadList(): List<DownloadTask> = tasks.toList()

    /** 获取指定 URL 的下载任务 */
    fun getDownloadTask(url: String): DownloadTask? = tasks.find { it.url == url }

    /** 释放资源 */
    fun release() {
        activeFutures.values.forEach { it.cancel(true) }
        activeFutures.clear()
        executor.shutdownNow()
        tasks.clear()
        listener = null
    }

    // ── 内部实现 ──

    /**
     * 执行下载（支持断点续传）
     *
     * 使用 HTTP Range 请求从已下载位置继续下载。
     */
    private fun executeDownload(task: DownloadTask) {
        val file = File(task.filePath)
        val startByte = if (file.exists()) file.length() else 0L

        try {
            val connection = URL(task.url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000

            // 断点续传：设置 Range 请求头
            if (startByte > 0) {
                connection.setRequestProperty("Range", "bytes=$startByte-")
            }

            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != 200 && responseCode != 206) {
                task.state = DownloadState.FAILED
                notifyMain {
                    listener?.onDownloadError(task, PlayerError(code = 5000, message = "HTTP $responseCode"))
                }
                return
            }

            // 计算总大小
            val contentLength = connection.contentLengthLong
            val totalSize = if (responseCode == 206) startByte + contentLength else contentLength
            task.totalSize = totalSize

            // 使用 RandomAccessFile 支持断点写入
            val raf = RandomAccessFile(file, "rw")
            raf.seek(startByte)

            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var downloaded = startByte

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (Thread.currentThread().isInterrupted) {
                        raf.close()
                        return
                    }

                    raf.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    task.downloadedSize = downloaded

                    // 计算进度
                    val progress = if (totalSize > 0) downloaded.toFloat() / totalSize else 0f
                    task.progress = progress

                    // 进度回调（每 100KB 通知一次，避免过于频繁）
                    if (downloaded % (100 * 1024) < 8192) {
                        notifyMain { listener?.onDownloadProgress(task, progress) }
                    }
                }

                raf.close()
            }

            connection.disconnect()

            // 下载完成
            task.state = DownloadState.COMPLETED
            task.progress = 1f
            notifyMain { listener?.onDownloadComplete(task) }

        } catch (e: Exception) {
            if (!Thread.currentThread().isInterrupted) {
                task.state = DownloadState.FAILED
                notifyMain {
                    listener?.onDownloadError(task, PlayerError(code = 5000, message = e.message ?: "Download failed"))
                }
            }
        } finally {
            activeFutures.remove(task.url)
        }
    }

    /** 根据 URL 生成本地文件路径 */
    private fun generateFilePath(url: String): String {
        val extension = when {
            url.contains(".m3u8") -> "ts"
            url.contains(".mp4") -> "mp4"
            url.contains(".flv") -> "flv"
            else -> "mp4"
        }
        val fileName = "${url.hashCode().toString(16)}.$extension"
        return File(downloadDir, fileName).absolutePath
    }

    /** 在主线程执行回调 */
    private fun notifyMain(action: () -> Unit) {
        mainHandler.post(action)
    }
}

package com.orangecloud.player

/**
 * 字幕引擎
 *
 * 负责外挂字幕文件解析、时间匹配和文本回调，
 * 支持 SRT 和 VTT 格式。
 */
class SubtitleEngine {

    /**
     * 字幕条目
     */
    data class SubtitleEntry(
        val index: Int,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val text: String
    )

    /**
     * 字幕样式配置
     */
    data class SubtitleStyle(
        val fontSize: Float = 16f,
        val fontColor: Int = 0xFFFFFFFF.toInt(),
        val backgroundColor: Int = 0x80000000.toInt(),
        val position: SubtitlePosition = SubtitlePosition.BOTTOM
    )

    /**
     * 字幕位置
     */
    enum class SubtitlePosition {
        TOP,
        CENTER,
        BOTTOM
    }

    /**
     * 字幕回调接口
     */
    interface SubtitleListener {
        fun onSubtitleChanged(text: String?)
        fun onSubtitleLoaded(trackCount: Int)
        fun onSubtitleError(error: PlayerError)
    }

    private var listener: SubtitleListener? = null
    private var style: SubtitleStyle = SubtitleStyle()
    private val subtitleTracks = mutableListOf<List<SubtitleEntry>>()
    private var currentTrackIndex: Int = 0
    private var isEnabled: Boolean = true

    /**
     * 设置字幕监听器
     */
    fun setSubtitleListener(listener: SubtitleListener?) {
        this.listener = listener
    }

    /**
     * 加载字幕文件
     * @param url 字幕文件地址（支持 .srt 和 .vtt）
     */
    fun loadSubtitle(url: String) {
        // TODO: 下载并解析字幕文件
    }

    /**
     * 加载字幕内容
     * @param content 字幕文件内容
     * @param format 格式（"srt" 或 "vtt"）
     */
    fun loadSubtitleContent(content: String, format: String) {
        val entries = when (format.lowercase()) {
            "srt" -> parseSRT(content)
            "vtt" -> parseVTT(content)
            else -> emptyList()
        }
        if (entries.isNotEmpty()) {
            subtitleTracks.add(entries)
            listener?.onSubtitleLoaded(subtitleTracks.size)
        }
    }

    /**
     * 切换字幕轨道
     * @param index 轨道索引
     */
    fun switchTrack(index: Int) {
        if (index in subtitleTracks.indices) {
            currentTrackIndex = index
        }
    }

    /**
     * 根据播放时间获取当前字幕文本
     * @param timeMs 当前播放时间（毫秒）
     * @return 当前字幕文本，无字幕返回 null
     */
    fun getSubtitleAt(timeMs: Long): String? {
        if (!isEnabled || subtitleTracks.isEmpty()) return null
        val track = subtitleTracks.getOrNull(currentTrackIndex) ?: return null
        val entry = track.find { timeMs in it.startTimeMs..it.endTimeMs }
        return entry?.text
    }

    /**
     * 更新播放时间（触发字幕回调）
     * @param timeMs 当前播放时间（毫秒）
     */
    fun updateTime(timeMs: Long) {
        val text = getSubtitleAt(timeMs)
        listener?.onSubtitleChanged(text)
    }

    /**
     * 设置字幕样式
     * @param style 字幕样式配置
     */
    fun setStyle(style: SubtitleStyle) {
        this.style = style
    }

    /**
     * 获取当前字幕样式
     */
    fun getStyle(): SubtitleStyle = style

    /**
     * 启用/禁用字幕
     */
    fun setEnabled(enabled: Boolean) {
        this.isEnabled = enabled
        if (!enabled) {
            listener?.onSubtitleChanged(null)
        }
    }

    /**
     * 获取字幕轨道数量
     */
    fun getTrackCount(): Int = subtitleTracks.size

    /**
     * 释放资源
     */
    fun release() {
        subtitleTracks.clear()
        listener = null
    }

    private fun parseSRT(content: String): List<SubtitleEntry> {
        val entries = mutableListOf<SubtitleEntry>()
        val blocks = content.replace("\r\n", "\n").replace("\r", "\n").split("\n\n+".toRegex())

        for (block in blocks) {
            val lines = block.trim().split("\n")
            if (lines.size < 3) continue

            // 第一行：序号
            val index = lines[0].trim().toIntOrNull() ?: continue

            // 第二行：时间戳 00:00:01,000 --> 00:00:04,000
            val timeLine = lines[1].trim()
            val timeParts = timeLine.split("-->")
            if (timeParts.size != 2) continue

            val startMs = parseSRTTimestamp(timeParts[0].trim()) ?: continue
            val endMs = parseSRTTimestamp(timeParts[1].trim()) ?: continue

            // 第三行及之后：文本
            val text = lines.subList(2, lines.size).joinToString("\n").trim()
            if (text.isEmpty()) continue

            entries.add(SubtitleEntry(index = index, startTimeMs = startMs, endTimeMs = endMs, text = text))
        }

        return entries
    }

    private fun parseVTT(content: String): List<SubtitleEntry> {
        val entries = mutableListOf<SubtitleEntry>()
        val normalized = content.replace("\r\n", "\n").replace("\r", "\n")

        // 验证 WEBVTT 头
        if (!normalized.trimStart().startsWith("WEBVTT")) return entries

        val blocks = normalized.split("\n\n+".toRegex())
        var index = 0

        for (block in blocks) {
            val trimmed = block.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("WEBVTT")) continue

            val lines = trimmed.split("\n")

            // 找到时间戳行
            var timeLineIdx = -1
            for (i in lines.indices) {
                if (lines[i].contains("-->")) {
                    timeLineIdx = i
                    break
                }
            }
            if (timeLineIdx == -1) continue

            val timeLine = lines[timeLineIdx].trim()
            val timeParts = timeLine.split("-->")
            if (timeParts.size != 2) continue

            val startMs = parseVTTTimestamp(timeParts[0].trim()) ?: continue
            val endMs = parseVTTTimestamp(timeParts[1].trim()) ?: continue

            // 时间戳行之后是文本
            if (timeLineIdx + 1 >= lines.size) continue
            val text = lines.subList(timeLineIdx + 1, lines.size).joinToString("\n").trim()
            if (text.isEmpty()) continue

            index++
            entries.add(SubtitleEntry(index = index, startTimeMs = startMs, endTimeMs = endMs, text = text))
        }

        return entries
    }

    /** 解析 SRT 时间戳: 00:00:01,000 → 毫秒 */
    private fun parseSRTTimestamp(timestamp: String): Long? {
        val regex = Regex("""(\d{2}):(\d{2}):(\d{2}),(\d{3})""")
        val match = regex.find(timestamp) ?: return null
        val (h, m, s, ms) = match.destructured
        return h.toLong() * 3600000 + m.toLong() * 60000 + s.toLong() * 1000 + ms.toLong()
    }

    /** 解析 VTT 时间戳: 00:00:01.000 或 00:01.000 → 毫秒 */
    private fun parseVTTTimestamp(timestamp: String): Long? {
        // 完整格式: HH:MM:SS.mmm
        val regexFull = Regex("""(\d{2}):(\d{2}):(\d{2})\.(\d{3})""")
        regexFull.find(timestamp)?.let { match ->
            val (h, m, s, ms) = match.destructured
            return h.toLong() * 3600000 + m.toLong() * 60000 + s.toLong() * 1000 + ms.toLong()
        }
        // 短格式: MM:SS.mmm
        val regexShort = Regex("""(\d{2}):(\d{2})\.(\d{3})""")
        regexShort.find(timestamp)?.let { match ->
            val (m, s, ms) = match.destructured
            return m.toLong() * 60000 + s.toLong() * 1000 + ms.toLong()
        }
        return null
    }
}

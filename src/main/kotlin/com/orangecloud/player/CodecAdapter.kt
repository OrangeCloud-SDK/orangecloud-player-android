package com.orangecloud.player

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build

/**
 * 编解码适配器
 *
 * 负责检测设备编解码能力并执行降级策略，
 * 当设备不支持 HEVC (H.265) 时自动降级到 H.264。
 */
class CodecAdapter(private val context: Context) {

    /**
     * 编解码器类型
     */
    enum class CodecType {
        H264,
        H265_HEVC,
        VP9,
        AV1
    }

    /**
     * 编解码能力信息
     */
    data class CodecCapability(
        val codecType: CodecType,
        val isHardwareAccelerated: Boolean,
        val maxWidth: Int,
        val maxHeight: Int,
        val isSupported: Boolean
    )

    private var capabilities: Map<CodecType, CodecCapability> = emptyMap()
    private var isDetected: Boolean = false

    /**
     * 检测设备编解码能力（SDK 初始化时调用）
     *
     * 使用 MediaCodecList 遍历设备支持的解码器，
     * 检测 H.264、H.265、VP9、AV1 的硬件解码支持情况。
     */
    fun detectCapabilities() {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        val codecInfos = codecList.codecInfos

        val h264Cap = detectCodec(codecInfos, MediaFormat.MIMETYPE_VIDEO_AVC, CodecType.H264)
        val h265Cap = detectCodec(codecInfos, MediaFormat.MIMETYPE_VIDEO_HEVC, CodecType.H265_HEVC)
        val vp9Cap = detectCodec(codecInfos, MediaFormat.MIMETYPE_VIDEO_VP9, CodecType.VP9)
        val av1Cap = if (Build.VERSION.SDK_INT >= 29) {
            detectCodec(codecInfos, "video/av01", CodecType.AV1)
        } else {
            CodecCapability(CodecType.AV1, false, 0, 0, false)
        }

        capabilities = mapOf(
            CodecType.H264 to h264Cap,
            CodecType.H265_HEVC to h265Cap,
            CodecType.VP9 to vp9Cap,
            CodecType.AV1 to av1Cap
        )
        isDetected = true
    }

    /**
     * 检测指定 MIME 类型的解码器能力
     */
    private fun detectCodec(
        codecInfos: Array<MediaCodecInfo>,
        mimeType: String,
        codecType: CodecType
    ): CodecCapability {
        // 查找支持该 MIME 类型的解码器（非编码器）
        val decoders = codecInfos.filter { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
        }

        if (decoders.isEmpty()) {
            return CodecCapability(codecType, false, 0, 0, false)
        }

        // 优先查找硬件解码器
        val hwDecoder = decoders.firstOrNull { info ->
            if (Build.VERSION.SDK_INT >= 29) {
                info.isHardwareAccelerated
            } else {
                // API 29 以下通过名称判断（硬件解码器通常不包含 "OMX.google" 前缀）
                !info.name.startsWith("OMX.google.") && !info.name.startsWith("c2.android.")
            }
        }

        val bestDecoder = hwDecoder ?: decoders.first()
        val isHardware = hwDecoder != null

        // 获取最大支持分辨率
        var maxWidth = 1920
        var maxHeight = 1080
        try {
            val caps = bestDecoder.getCapabilitiesForType(mimeType)
            val videoCaps = caps.videoCapabilities
            if (videoCaps != null) {
                maxWidth = videoCaps.supportedWidths.upper
                maxHeight = videoCaps.supportedHeights.upper
            }
        } catch (_: Exception) {
            // 部分设备可能抛出异常，使用默认值
        }

        return CodecCapability(
            codecType = codecType,
            isHardwareAccelerated = isHardware,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            isSupported = true
        )
    }

    /**
     * 检查指定编解码器是否支持
     */
    fun isCodecSupported(codecType: CodecType): Boolean {
        if (!isDetected) detectCapabilities()
        return capabilities[codecType]?.isSupported ?: false
    }

    /**
     * 检查是否支持硬件加速
     */
    fun isHardwareAccelerated(codecType: CodecType): Boolean {
        if (!isDetected) detectCapabilities()
        return capabilities[codecType]?.isHardwareAccelerated ?: false
    }

    /**
     * 获取推荐的编解码器（考虑降级策略）
     *
     * 如果期望的编解码器不支持，自动降级到 H.264。
     */
    fun getRecommendedCodec(preferredCodec: CodecType): CodecType {
        if (!isDetected) detectCapabilities()
        return if (isCodecSupported(preferredCodec)) {
            preferredCodec
        } else {
            CodecType.H264
        }
    }

    /** 获取所有编解码能力信息 */
    fun getAllCapabilities(): Map<CodecType, CodecCapability> {
        if (!isDetected) detectCapabilities()
        return capabilities
    }

    /** 是否已完成能力检测 */
    fun isDetectionComplete(): Boolean = isDetected
}

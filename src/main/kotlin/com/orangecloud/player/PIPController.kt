package com.orangecloud.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.os.Build
import android.util.Rational

/**
 * 画中画控制器
 *
 * 负责 Android 平台的画中画（Picture-in-Picture）功能管理，
 * 支持系统级画中画（API 26+）。
 *
 * 使用前提：
 * - Activity 需要在 AndroidManifest.xml 中声明 `android:supportsPictureInPicture="true"`
 * - 需要 API 26 (Android 8.0) 及以上
 *
 * 使用示例：
 * ```kotlin
 * val pipController = PIPController(activity)
 * pipController.setPIPListener(listener)
 * pipController.enterPIP() // 进入系统画中画
 * ```
 */
class PIPController(private val context: Context) {

    /**
     * 画中画事件回调接口
     */
    interface PIPListener {
        fun onEnterPIP()
        fun onExitPIP()
        fun onPIPModeChanged(mode: PIPMode)
        fun onPIPSizeChanged(width: Int, height: Int)
    }

    private var listener: PIPListener? = null
    private var currentMode: PIPMode = PIPMode.INACTIVE
    private var player: OrangeCloudPlayerClient? = null

    // 画中画宽高比（默认 16:9）
    private var aspectRatioWidth: Int = 16
    private var aspectRatioHeight: Int = 9

    /** 设置画中画监听器 */
    fun setPIPListener(listener: PIPListener?) {
        this.listener = listener
    }

    /** 绑定播放器实例 */
    fun bindPlayer(player: OrangeCloudPlayerClient) {
        this.player = player
    }

    /** 设置画中画宽高比 */
    fun setAspectRatio(width: Int, height: Int) {
        this.aspectRatioWidth = width.coerceAtLeast(1)
        this.aspectRatioHeight = height.coerceAtLeast(1)
    }

    /** 检查设备是否支持画中画 */
    fun isPIPSupported(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val activity = context as? Activity ?: return false
        return activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    /**
     * 进入系统级画中画模式
     *
     * 使用 PictureInPictureParams 配置宽高比，调用 Activity.enterPictureInPictureMode()。
     * 需要 API 26+，Activity 需声明 supportsPictureInPicture="true"。
     *
     * @return 是否成功进入
     */
    fun enterPIP(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false

        val activity = context as? Activity ?: return false

        if (!isPIPSupported()) return false

        try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(aspectRatioWidth, aspectRatioHeight))
                .build()

            activity.enterPictureInPictureMode(params)
            currentMode = PIPMode.SYSTEM
            listener?.onEnterPIP()
            listener?.onPIPModeChanged(currentMode)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * 更新画中画参数（在画中画模式中动态更新）
     *
     * 可用于视频尺寸变化时更新宽高比。
     */
    fun updatePIPParams(width: Int, height: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (currentMode != PIPMode.SYSTEM) return

        val activity = context as? Activity ?: return

        try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(width.coerceAtLeast(1), height.coerceAtLeast(1)))
                .build()
            activity.setPictureInPictureParams(params)
            listener?.onPIPSizeChanged(width, height)
        } catch (_: Exception) {}
    }

    /**
     * 退出画中画模式
     *
     * 注意：系统级画中画的退出通常由用户操作触发（点击关闭按钮或展开），
     * 此方法通过将 Activity 移到前台来退出画中画。
     */
    fun exitPIP() {
        if (currentMode == PIPMode.INACTIVE) return

        val activity = context as? Activity
        if (currentMode == PIPMode.SYSTEM && activity != null) {
            // 将 Activity 移到前台，系统会自动退出画中画
            activity.moveTaskToBack(false)
        }

        currentMode = PIPMode.INACTIVE
        listener?.onExitPIP()
        listener?.onPIPModeChanged(currentMode)
    }

    /**
     * 通知画中画模式变化（由 Activity.onPictureInPictureModeChanged 调用）
     *
     * 开发者需要在 Activity 中重写 onPictureInPictureModeChanged 并调用此方法：
     * ```kotlin
     * override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration?) {
     *     super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
     *     pipController.onPIPModeChanged(isInPictureInPictureMode)
     * }
     * ```
     */
    fun onPIPModeChanged(isInPIP: Boolean) {
        if (isInPIP) {
            currentMode = PIPMode.SYSTEM
            listener?.onEnterPIP()
        } else {
            currentMode = PIPMode.INACTIVE
            listener?.onExitPIP()
        }
        listener?.onPIPModeChanged(currentMode)
    }

    /** 获取当前画中画模式 */
    fun getCurrentMode(): PIPMode = currentMode

    /** 是否处于画中画模式 */
    fun isInPIPMode(): Boolean = currentMode != PIPMode.INACTIVE

    /** 释放资源 */
    fun release() {
        exitPIP()
        player = null
        listener = null
    }
}

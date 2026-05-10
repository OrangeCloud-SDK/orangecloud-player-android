# OrangeCloud Player SDK - Android

[![Platform](https://img.shields.io/badge/platform-Android%2021%2B-3DDC84?logo=android)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin)](https://kotlinlang.org)

企业级 Android 视频播放器 SDK。

## 安装

```kotlin
implementation(files("libs/orangecloud-player-client-release.aar"))
implementation("com.google.android.exoplayer:exoplayer:2.19.+")
```

## 快速开始

```kotlin
import com.orangecloud.player.*

OrangeCloudPlayerClient.initialize(context, "your_app_id", "your_license_url")

val player = OrangeCloudPlayerClient(context)
player.listener = object : PlayerListener { ... }
player.startVodPlay("https://example.com/video.m3u8")
```

## 功能

点播/直播 · DRM · 字幕 · 画中画 · 离线下载 · 预加载 · 自适应码率 · 截图

## Demo

[orangecloud-player-demos/android](https://github.com/OrangeCloud-SDK/orangecloud-player-demos/tree/main/android)

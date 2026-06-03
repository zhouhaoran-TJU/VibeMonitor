# 性能监视器

一个原生 Android 手机性能监控应用，包含清晰的主监控界面和可添加到桌面的 App Widget。

## 功能

- 实时显示设备温度、电池温度、CPU 占用、内存使用、电量状态和内部存储占用。
- 主界面包含温度仪表、指标卡和最近趋势线。
- 桌面小组件显示温度、CPU、内存和刷新时间，点击可进入应用。
- App 内可请求添加桌面小组件，并在桌面不响应时展示诊断信息和手动添加指引。
- 支持启动时自动检查更新，也支持在 App 内手动检查版本、查看清单详情、下载进度、大小校验、SHA-256 校验并引导安装 APK。
- 监控数据使用 Android 系统公开 API 和可访问的 `/proc`、`/sys/class/thermal` 信息，更新安装流程使用 AndroidX Core 的 `FileProvider`。

## 构建

```bash
JAVA_HOME=/home/mi/WorkSpace/Projects/VibeCoding/.build-env/jdk-17 \
ANDROID_HOME=/home/mi/WorkSpace/Projects/VibeCoding/.build-env/android-sdk \
ANDROID_SDK_ROOT=/home/mi/WorkSpace/Projects/VibeCoding/.build-env/android-sdk \
GRADLE_USER_HOME=/home/mi/WorkSpace/Projects/VibeCoding/monitor/.gradle-home \
/home/mi/WorkSpace/Projects/VibeCoding/.build-env/gradle-7.6.4/bin/gradle --no-daemon :app:assembleDebug :app:assembleBeta
```

## 发布

- beta APK：`dist/VibeMonitor-beta.apk`
- debug APK：`dist/VibeMonitor-debug.apk`
- 更新清单：`dist/version.json`
- 下载二维码：`dist/download-qr.png`
- 固定下载地址：`https://raw.githubusercontent.com/zhouhaoran-TJU/VibeMonitor/main/dist/VibeMonitor-beta.apk`

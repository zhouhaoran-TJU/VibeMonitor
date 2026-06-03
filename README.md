# 性能监视器

一个原生 Android 手机性能监控应用，包含清晰的主监控界面和可添加到桌面的 App Widget。

## 功能

- 实时显示设备温度、电池温度、CPU 占用、内存使用、电量状态和内部存储占用。
- 主界面包含温度仪表、紧凑指标卡和带时间轴的最近趋势线。
- 顶部固定操作栏提供更新、小组件和显示风格切换；仪表盘内容可滚动，减少小屏和大字体下的文字重叠。
- 显示风格支持默认和暗夜两种模式，并记住上次选择。
- 前台服务在后台每 1 分钟采样一次，保留最近 24 小时温度历史。
- 最近趋势默认显示最近 2 小时温度曲线，可在图表区域左右滑动查看 24 小时内历史窗口。
- 标题区显示 24 小时内最高温度。
- 指标卡副标题、进度条、趋势图横轴和滑动提示保留独立间距，减少文本互相遮挡。
- 高温告警阈值可在顶部“阈值”入口自定义输入，支持 30 到 120°C；温度降至阈值下方 5°C 后才会再次提醒。
- 高温告警支持后台全局提醒：后台服务超过阈值时会发送高优先级通知和全屏告警页。
- 高温告警页包含醒目的危险叹号标识，告警文案保持简短。
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

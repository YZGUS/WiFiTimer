# WiFi计时器

一款 Android 应用，自动监控 WiFi 连接时长，帮助你追踪每日在目标 WiFi 网络上的有效在线时间。

## 功能

- **自动监控** — 连接到白名单 WiFi 后自动开始计时，断开即停止
- **有效时长计算** — 支持设置忽略时段（如午餐休息），自动排除非工作时间
- **每日统计** — 实时显示今日有效时长与目标时长的进度
- **达标提醒** — 通过定时任务检查并推送达标/未达标通知
- **跨天拆分** — 自动处理跨午夜的长连接，按天分别统计
- **开机自启** — 设备重启后自动恢复监控服务
- **白名单管理** — 按 SSID/BSSID 匹配，可设置各自的目标时长
- **历史记录** — 查看每日连接明细和统计数据

## 技术栈

- **UI**: Jetpack Compose + Material 3
- **架构**: MVVM + Hilt 依赖注入
- **数据库**: Room
- **后台服务**: Foreground Service + WorkManager
- **异步**: Kotlin Coroutines + Flow

## 构建

1. 安装 Android Studio 或 Android SDK（compileSdk 34, minSdk 26）
2. 克隆仓库后打开项目
3. 同步 Gradle 并运行

```bash
./gradlew assembleDebug
```

APK 输出路径: `app/build/outputs/apk/debug/app-debug.apk`

## 权限说明

| 权限 | 用途 |
|------|------|
| ACCESS_WIFI_STATE | 读取 WiFi 连接信息 |
| ACCESS_NETWORK_STATE | 检测网络连接状态 |
| ACCESS_FINE_LOCATION | Android 8+ 获取 WiFi SSID 必需 |
| ACCESS_COARSE_LOCATION | 获取 WiFi SSID 辅助权限 |
| FOREGROUND_SERVICE | 前台监控服务 |
| FOREGROUND_SERVICE_CONNECTED_DEVICE | Android 14+ 前台服务类型 |
| POST_NOTIFICATIONS | Android 13+ 推送通知 |
| RECEIVE_BOOT_COMPLETED | 开机自动启动监控 |

## 项目结构

```
app/src/main/java/com/cengyi/wifitimer/
├── WiFiTimerApp.kt              # Application + WorkManager 配置
├── di/
│   └── DatabaseModule.kt        # Hilt 数据库模块
├── data/
│   ├── local/                   # Room 数据库、Entity、DAO
│   └── repository/              # 数据仓库层
├── service/
│   ├── WiFiMonitorService.kt    # 前台监控服务
│   ├── DailyCheckWorker.kt      # 定时达标检查
│   ├── ActiveSession.kt         # 活跃会话数据
│   └── BootReceiver.kt          # 开机自启广播
├── ui/
│   ├── MainActivity.kt          # 主入口
│   ├── navigation/              # Compose Navigation
│   ├── theme/                   # Material 3 主题
│   └── screen/                  # 各功能页面 + ViewModel
└── util/                        # 工具类（时间、WiFi、忽略时段计算）
```

## 许可

MIT

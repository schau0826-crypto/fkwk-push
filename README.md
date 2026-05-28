# fkwk-push

[![Android Debug APK](https://github.com/schau0826-crypto/fkwk-push/actions/workflows/android-debug.yml/badge.svg)](https://github.com/schau0826-crypto/fkwk-push/actions/workflows/android-debug.yml)

Languages: [中文](#中文) | [English](#english)

---

## 中文

fkwk-push 是一个 Android 通知转发工具：在 Android 设备上读取系统通知栏中已经展示出来的通知内容，并通过 Bark 转发到 iPhone。

它适合这样的场景：工作应用在 Android 上，但你下班后主要看 iPhone，只想接收通知弹窗里的标题和摘要，不想打开另一台手机。

```text
Android 通知监听
  -> App 过滤 / 关键词屏蔽 / 优先级映射 / 本地历史
  -> Bark HTTPS API
  -> iPhone 通知
```

### 功能

- 读取 Android 系统通知栏可见内容，不进入目标 App 读取聊天记录或私有数据库
- 可在 App 内勾选要监听的应用，列表显示应用名称和图标
- 通知历史显示应用图标、应用名、标题、正文摘要、转发状态和错误原因
- 支持从通知历史快速进入某个应用的转发规则设置
- 支持低 / 普通 / 紧急三档优先级，并映射到 Bark 的 iOS 通知级别
- 支持 Bark 标题模板，例如 `{app}-{title}`
- 支持关键词屏蔽：命中标题或正文时只写入历史，不转发到 iPhone
- 支持“亮屏且未锁屏时暂停转发”，避免正在使用 Android 时 iPhone 重复响
- 支持 Bark 自定义通知图标，可选上传 Android 应用图标到自托管图标服务
- 发送失败会写入本地历史，并由 WorkManager 在网络恢复后重试
- 提供前台服务、开机自启、无障碍保活入口等 Android 后台存活辅助

### 隐私说明

本应用可以读取 Android 通知栏里已经展示出来的通知标题和消息预览。请只在你拥有或被授权管理的设备和账号上使用。

本应用不会：

- 读取目标 App 的私有数据库
- 进入聊天界面
- 自动回复
- 收集统计数据
- 上传数据给本仓库作者

通知内容会发送到你配置的 Bark server。如果使用 Bark 官方服务，通知内容会经过 Bark 的基础设施；如果你需要更严格的隐私，可以使用自托管的 Bark 兼容服务。

### 项目结构

```text
android/   Android app，Kotlin + Jetpack Compose + Room + Hilt + WorkManager
docs/      配置说明、Android 保活、Bark 验证步骤
server/    可选自托管组件，主要用于图标上传服务和旧版 ntfy 参考配置
```

### 构建

用 Android Studio 打开 `android/`，或在终端构建：

```bash
cd android
./gradlew assembleDebug
```

如果 macOS 终端没有独立 JDK，但安装了 Android Studio，可以使用 Android Studio 自带 JBR：

```bash
cd android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
```

Debug APK 输出位置：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

### 下载 APK

APK 不提交到 git。GitHub Actions 会在每次 push 到 `main` 时自动构建 debug APK。

下载方式：

1. 打开 Actions 页里的 `Android Debug APK` workflow。
2. 打开最新一次成功运行。
3. 在页面底部下载 `fkwk-push-debug-apk` artifact。

如果要发布带 APK 的 GitHub Release，可以创建并推送版本 tag：

```bash
git tag v0.1.1
git push origin v0.1.1
```

### 基础配置

1. 在 iPhone 安装 Bark。
2. 从 Bark 首页推送 URL 里复制 Bark key，不要使用 iOS device token。
3. 安装 Android debug APK。
4. 在 Android App 设置页填写：
   - Bark server URL，例如 `https://api.day.app`
   - Bark key
   - 可选标题模板，例如 `{app}-{title}`
5. 授予 Android 通知读取权限。
6. 勾选要转发的应用。
7. 给 Android 设备发送一条真实通知，并查看 App 内通知历史。

更多说明：[docs/03-Bark验证步骤.md](docs/03-Bark验证步骤.md)

### Android 保活

Android 厂商通常会限制后台服务。为了稳定转发，建议配置：

- 通知读取权限
- 允许前台服务通知
- 开启自启动
- 电池策略设为无限制
- 允许后台联网
- 可选开启无障碍保活服务

详见：[docs/02-Android保活配置.md](docs/02-Android保活配置.md)

### 可选图标服务

Bark 支持自定义通知图标。fkwk-push 可以读取 Android 来源应用图标，上传到简单的自托管图标服务，并在 Bark 推送里携带图标 URL。

`server/` 目录包含这部分可选 Docker 组件。普通 Bark 转发不需要部署服务端。

详见：[docs/01-可选服务端.md](docs/01-可选服务端.md)

### 安全提示

不要提交这些文件或值：

- Bark key
- DNS provider token
- icon upload token
- `.env`
- `android/local.properties`
- release keystore

仓库已经包含 `.gitignore` 规则来忽略常见本地密钥和构建产物，但公开前仍建议自行 review。

### License

MIT

---

## English

fkwk-push is an Android notification forwarding tool. It reads notification content that is already visible in the Android notification shade and forwards it to iPhone through Bark.

It is designed for people who keep work apps on an Android device but mainly use an iPhone after work, and only need notification titles and previews instead of opening the Android phone.

```text
Android notification listener
  -> app filter / keyword block / priority mapping / local history
  -> Bark HTTPS API
  -> iPhone notification
```

### Features

- Reads visible Android notification content only, without opening target apps or reading private databases
- Lets you select monitored apps in the Android UI, with app names and icons
- Shows notification history with app icon, app name, title, preview text, forwarding status, and error reason
- Lets you jump from a history item to that app's forwarding settings
- Supports low / normal / urgent priority levels mapped to Bark iOS notification levels
- Supports Bark title templates, for example `{app}-{title}`
- Supports keyword blocking: matching title or body is logged locally but not forwarded to iPhone
- Supports pausing forwarding while the Android device is screen-on and unlocked
- Supports Bark custom icons through an optional self-hosted icon upload service
- Stores failed sends locally and retries through WorkManager when network is available
- Provides foreground service, boot receiver, and optional accessibility keep-alive entry points

### Privacy

This app can read notification titles and message previews visible in the Android notification shade. Use it only on devices and accounts you own or are authorized to manage.

The app does not:

- read app private databases
- enter chat screens
- perform automatic replies
- collect analytics
- upload data to this repository's author

Notification content is sent to the Bark server configured by the user. If you use the public Bark service, notification content is sent to Bark's infrastructure. If you need stricter privacy, use a self-hosted Bark-compatible server.

### Project Structure

```text
android/   Android app, Kotlin + Jetpack Compose + Room + Hilt + WorkManager
docs/      setup notes, Android keep-alive guide, Bark validation guide
server/    optional self-hosted components, mainly icon upload service and legacy ntfy stack
```

### Build

Open `android/` with Android Studio, or build from terminal:

```bash
cd android
./gradlew assembleDebug
```

If your shell does not have a standalone JDK but Android Studio is installed on macOS, you can use Android Studio's bundled JBR:

```bash
cd android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
```

Debug APK output:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

### Download APK

APK files are not committed to git. GitHub Actions builds a debug APK on every push to `main`.

To download it:

1. Open the `Android Debug APK` workflow in the Actions tab.
2. Open the latest successful run.
3. Download the `fkwk-push-debug-apk` artifact.

To publish a GitHub Release with an APK attached, create and push a version tag:

```bash
git tag v0.1.1
git push origin v0.1.1
```

### Basic Setup

1. Install Bark on iPhone.
2. Copy the Bark key from Bark's home screen URL. Do not use the iOS device token.
3. Install the Android debug APK.
4. In the Android app, fill in:
   - Bark server URL, for example `https://api.day.app`
   - Bark key
   - optional title template, for example `{app}-{title}`
5. Grant Android notification listener permission.
6. Select the apps you want to forward.
7. Send a real notification to the Android device and check the in-app notification history.

More details: [docs/03-Bark验证步骤.md](docs/03-Bark验证步骤.md)

### Android Keep-Alive

Android vendors often restrict background services. For reliable forwarding, configure:

- notification listener permission
- foreground service notification allowed
- autostart enabled
- battery optimization disabled or set to unrestricted
- background network allowed
- optional accessibility keep-alive service

See [docs/02-Android保活配置.md](docs/02-Android保活配置.md).

### Optional Icon Server

Bark supports custom notification icons. fkwk-push can read the source app icon on Android and upload it to a simple self-hosted icon endpoint, then include the icon URL in Bark pushes.

The `server/` directory contains optional Docker components for this. It is not required for normal Bark forwarding.

See [docs/01-可选服务端.md](docs/01-可选服务端.md).

### Security Notes

Never commit these files or values:

- Bark key
- DNS provider token
- icon upload token
- `.env`
- `android/local.properties`
- release keystores

The repository includes `.gitignore` rules for common local secrets and build outputs, but you should still review your changes before publishing.

### License

MIT

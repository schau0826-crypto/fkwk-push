# fkwk-push

[![Android Debug APK](https://github.com/schau0826-crypto/fkwk-push/actions/workflows/android-debug.yml/badge.svg)](https://github.com/schau0826-crypto/fkwk-push/actions/workflows/android-debug.yml)

fkwk-push 是一个 Android 通知转发工具：在 Android 设备上读取系统通知栏中已经展示出来的通知内容，并通过 Bark 转发到 iPhone。

它适合这样的场景：工作应用在 Android 上，但你下班后主要看 iPhone，只想接收通知弹窗里的标题和摘要，不想打开另一台手机。

```text
Android notification listener
  -> app filter / keyword block / priority mapping / local history
  -> Bark HTTPS API
  -> iPhone notification
```

## Features

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

## Privacy

This app can read notification titles and message previews that are visible in the Android notification shade. Use it only on devices and accounts you own or are authorized to manage.

The app does not:

- read app private databases
- enter chat screens
- perform automatic replies
- collect analytics
- upload data to this repository's author

Notification content is sent to the Bark server configured by the user. If you use the public Bark service, notification content is sent to Bark's infrastructure. If you need stricter privacy, use a self-hosted Bark-compatible server.

## Project Structure

```text
android/   Android app, Kotlin + Jetpack Compose + Room + Hilt + WorkManager
docs/      setup notes, Android keep-alive guide, Bark validation guide
server/    optional self-hosted components, mainly icon upload service and legacy ntfy stack
```

## Build

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


## Download APK

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

## Basic Setup

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

## Android Keep-Alive

Android vendors often restrict background services. For reliable forwarding, configure:

- notification listener permission
- foreground service notification allowed
- autostart enabled
- battery optimization disabled or set to unrestricted
- background network allowed
- optional accessibility keep-alive service

See [docs/02-Android保活配置.md](docs/02-Android保活配置.md).

## Optional Icon Server

Bark supports custom notification icons. fkwk-push can read the source app icon on Android and upload it to a simple self-hosted icon endpoint, then include the icon URL in Bark pushes.

The `server/` directory contains optional Docker components for this. It is not required for normal Bark forwarding.

See [docs/01-可选服务端.md](docs/01-可选服务端.md).

## Security Notes

Never commit these files or values:

- Bark key
- DNS provider token
- icon upload token
- `.env`
- `android/local.properties`
- release keystores

The repository includes `.gitignore` rules for common local secrets and build outputs, but you should still review your changes before publishing.

## License

MIT

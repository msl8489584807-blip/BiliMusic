# 构建 BiliMusic

## 环境

- JDK 17；JDK 21 也可用于当前 Gradle，推荐先使用已验证的 17。
- Android SDK Platform 35、Build Tools 35.0.0、Platform Tools。
- Android Studio，或可访问 Google / Maven Central 的命令行环境。

固定版本：Gradle 8.11.1、AGP 8.9.2、Kotlin/Compose compiler 2.1.20、KSP 2.1.20-1.0.32、Compose BOM 2025.04.01、Media3 1.6.1、Room 2.7.1。最低 API 26，target/compile API 35。

## 获取源码

```bash
git clone https://github.com/msl8489584807-blip/BiliMusic.git
cd BiliMusic
```

也可解压项目源码包，进入包含 `settings.gradle.kts` 的目录。

Android Studio 打开根目录，选择 Gradle JDK 17 并等待同步。Studio 通常会生成 `local.properties`；命令行构建时也可自行创建并配置 `sdk.dir`。SDK 路径属于本机配置，不提交到 Git。

若 Studio 自带运行时版本高于当前 Gradle 支持范围，请单独选择兼容 JDK，避免直接使用未经验证的 JBR。

## 编译与检查

```powershell
# Windows；JAVA_HOME 指向 JDK 17
.\gradlew.bat --version
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

```bash
# macOS / Linux；JAVA_HOME 指向 JDK 17
bash ./gradlew --version
bash ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Windows 也可用 `scripts/build.ps1`，优先使用 `JAVA_HOME`，其次使用项目可选的 `.tooling/jdk-17`。该目录不是源码依赖，不要求其他机器具备。Windows 非 ASCII 缓存路径出现 Gradle worker 加载问题时，可为 Gradle 缓存建立 ASCII 路径映射；脚本会识别可选的 `.tooling/gradle-home`。

输出：`app/build/outputs/apk/debug/app-debug.apk`。本次发布使用 Debug 包；在另一台机器重新构建时签名和 SHA256 可能不同。

## 设备测试

启动 Android 模拟器或连接开启 USB 调试的手机，确认 `adb devices` 可见设备后执行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true'
```

筛选联网测试：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.bilimusic.player.OnlinePlaybackTest,com.bilimusic.player.OnlineUiTest'
```

PowerShell 的含点 `-P` 参数需加单引号。macOS / Linux 使用 `bash ./gradlew` 替换 `gradlew.bat`。联网测试会访问真实 B站接口，网络、接口或平台状态变化会导致失败；本地播放测试包含真实一分钟睡眠计时。

构建和设备测试串行运行，避免同时写入同一输出目录。当前验证范围见 [TEST_REPORT.md](../TEST_REPORT.md)。

## 安装

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

不同签名不能直接覆盖安装。首次启动不会预置测试歌曲、歌单或搜索结果。

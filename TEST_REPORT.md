# BiliMusic 0.2.0 测试报告

日期：2026-10-24（Asia/Shanghai）。应用 ID：`com.bilimusic.player`。

本版核心已经实现并实测：**在 BiliMusic 内搜索 B站音乐，点击真实结果后通过原生 Media3 播放音轨**。搜索、播放和后台续播不启动 Bilibili 或浏览器。本人账号扫码确认后的身份、登录恢复和私有收藏夹仍需本人手机验收。

## 构建与安装

| 检查 | 实际结果 |
|---|---|
| 构建环境 | Gradle 8.11.1、AGP 8.9.2、Temurin 17.0.20.1、SDK / Build Tools 35 |
| `:app:testDebugUnitTest` | 12 项通过，0 失败/跳过 |
| `:app:lintDebug` | 成功，0 错误、20 警告 |
| `:app:assembleDebug` | 成功；最终联合构建 58s |
| 模拟器 | 项目专用 bilimusic_api35，Android 15 / API 35，x86_64，1080×2400 |
| `connectedDebugAndroidTest` | 完整 20 项通过，2m35s；之后在线增强及界面复测 5 项通过，30s；共 21 个不同用例 |
| 最终交付 APK 独立安装 | `adb install -r releases/BiliMusic-0.2.0-debug.apk` 返回 Success |
| 清除模拟器测试数据后的冷启动 | Status: ok，LaunchState: COLD，TotalTime: 2173ms；无 AndroidRuntime 崩溃 |
| APK 签名 | apksigner verify 成功，APK Signature Scheme v2，Android Debug RSA 2048 |
| 包版本 | 0.2.0 / versionCode 2，minSdk 26，targetSdk / compileSdk 35 |
| 包中 ABI | arm64-v8a、armeabi-v7a、x86、x86_64 |

没有连接真实手机，没有将模拟器或编译结果等同于 Galaxy S24+ 实测。最后的资源改动仅增加系统备份/迁移会话排除规则，随后重新完成 unit/lint/assemble 和 APK 安装冷启动；没有再次运行整个集成套件。

## 在线功能的真实验证

| 用例 | 已实际断言 |
|---|---|
| OnlinePlaybackTest（3 项） | 第一方“钢琴”搜索返回真实结果；第 2 页内容与第 1 页不同；解析 HTTPS 音轨；MediaSession 实际播放且进度超过 300ms；退后台后继续推进；服务自行解析下一首；跳到 2000ms 后继续；官方二维码生成和未确认轮询状态；测试 Cookie 密文持久化/重载、不发送到 CDN、清除 |
| OnlineUiTest（2 项） | 首页 → B站音乐搜索 → 点击真实结果 → 迷你/完整播放器 → 暂停；前台包始终为 BiliMusic；账号页生成真实二维码并显示，返回后取消轮询和清除二维码状态 |
| SessionStoreTest（2 项） | 退出后的旧请求迟到响应不能写回登录 Cookie；注入本机写入失败转换为 IOException，避免网络工作线程未处理异常 |
| StorageTest 新增（1 项） | 再次播放或队列刷新时保留用户编辑的标题、作者、标签和记录 ID |

第一次联网测试实际发现并修复了移动站重定向、二维码域名和 DASH 平台请求头不匹配的问题；未使用假搜索数据或外部跳转替代播放。音轨测试检查 Media3 解码/播放状态与实际进度，模拟器以无音频输出模式运行，未由人耳验收扬声器音质。

证据保存在 `docs/verification/v0.2/`：

公开源码压缩包只保留本报告的验证摘要，原始设备日志和本机构建记录不随压缩包分发。

- `android-integration.log` / `android-tests.xml`：完整 20 项；其中 OnlineUiTest 当时为 1 项。
- `online-ui.log` / `online-ui-tests.xml`：之后增强的 OnlinePlaybackTest 3 项和 OnlineUiTest 2 项。
- `playback-evidence.log`：筛选后的实际播放、后台、下一首、seek 和本地测试记录，无会话密钥或临时流地址。
- `build.log`、两份 `TEST-*.xml`、`lint-results.txt`、`apk-signature.txt`、`apk-badging.txt`、`cold-start.txt`：最终构建与交付校验。

实际截图：`docs/screenshots/online-search.png`、`online-player.png`。截图为真实服务返回内容，APK 没有预置这些搜索结果或测试音乐。二维码图片、二维码 key、用户会话和临时签名流 URL 不写入交付证据。

## 本地功能回归

StorageTest 共 9 项，覆盖 Room 外键/关联/顺序、重命名与收藏不丢关联、删除压紧顺序、备份合并去重、替换恢复、损坏引用拒绝、事务注入失败回滚、5000 条标签查询，以及上述在线元数据回归。

PlaybackTest、PlayerUiTest、ResumeTest、SafTest、UiTest 各 1 项全部通过：实际生成 WAV 经 ContentResolver 导入去重、重新关联、服务播放、后台/暂停/seek/Activity 重建、音频焦点、媒体通知、真实一分钟睡眠计时、新服务队列/5400ms/播放模式恢复且不自动播放、系统 SAF 选择与持久授权、完整播放器/同步歌词/队列，以及本地搜索与主题导航。5000 条 Room 标签查询本次测得 5ms，仅为单次模拟器测量。

单元测试 12 项：原 BV/LRC/去重/排序/搜索规则 7 项，新增逻辑 URI、HTTPS CDN 域名边界、时长与音轨缓存过期规则 5 项。

## 尚需本人或实际设备验证

- B站本人扫码确认、登录后身份和私有收藏夹、重开后会话恢复、真实账号退出。代码和二维码入口已实现，未使用他人账号测试。
- Galaxy S24+ / One UI 安装、锁屏/省电、真实蓝牙/耳机和系统媒体按键、长时间稳定性。
- 真实本地 MP3/FLAC 等文件与设备音质；本轮本地音频为 PCM WAV，在线实际播放为返回的 AAC/MP4 音轨。
- 真实 LRC 文件经系统选择器关联、云端 DocumentsProvider 离线/超时、大字体/旋转全矩阵。
- 下架、区域限制、付费及平台验证等所有响应组合。在线功能依赖当前接口、网络和账号访问权限，不能保证每个视频都可播放。
- Room 和 DataStore 在两次提交之间进程被强杀的跨存储原子性；当前有异常/取消补偿。

MediaStore 扫描、目录级导入、b23.tv 展开、音频下载/离线缓存和超长歌单跨屏拖动自动滚屏未实现。数据库结构仍为 v1，未使用破坏性迁移；未来改结构必须增加显式迁移和测试。

## Lint

20 个警告：UseKtx 13；ApplySharedPref、ModifierParameter、ExportedService、DataExtractionRules、ObsoleteSdkInt、MonochromeLauncherIcon、UseTomlInstead 各 1。会话提交在网络/IO 线程执行且检查结果；MediaSessionService 在 onConnect 检查 UID/系统信任；API 33 提供单色图标；旧 Android 保留 allowBackup=false，Android 12+ 明确排除会话的云备份和设备迁移。未禁用 Lint 错误或设置忽略错误构建。

## APK

- `releases/BiliMusic-0.2.0-debug.apk`
- 大小：24,302,611 字节，约 23.18 MiB。
- SHA256：`3d8d85d42b2645f1dbbe2371d9bedb95b29a1c69cd899ef20a866152ea959dbd`
- 校验文件：`releases/SHA256SUMS.txt`；旧 0.1.0 包保留。
- 未向真实手机自动安装，未上传商店或推送远程仓库。

## 复现

```powershell
.\scripts\build.ps1 ':app:testDebugUnitTest' ':app:lintDebug' ':app:assembleDebug' '--console=plain'
.\scripts\build.ps1 ':app:connectedDebugAndroidTest' '-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true' '--console=plain'
```

测试需要已启动的 Android 设备；在线用例还需要能访问 B站的网络，会真实失败并报告错误。筛选联网用例可加 `'-Pandroid.testInstrumentationRunnerArguments.class=com.bilimusic.player.OnlinePlaybackTest,com.bilimusic.player.OnlineUiTest'`。PowerShell 中含点的 `-P` 参数加单引号；所有 Gradle/设备测试串行执行。

0.1.0 历史报告存于 `docs/verification/v0.1/TEST_REPORT.md`，不代表当前在线范围。

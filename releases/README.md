# 安装包与版本

当前版本 `BiliMusic-0.2.0-debug.apk`，24,302,611 字节。版本说明见 [v0.2.0.md](v0.2.0.md)，单文件校验见 [BiliMusic-0.2.0-debug.apk.sha256](BiliMusic-0.2.0-debug.apk.sha256)。完整历史校验见 SHA256SUMS.txt。

APK 和整理后的源码 ZIP 作为发布附件，不提交进 Git；发布后从[仓库 Releases](https://github.com/msl8489584807-blip/BiliMusic/releases)下载。构建命令和环境见 [docs/BUILDING.md](../docs/BUILDING.md)。

提交所需源码和文档后，可生成不含本机开发记录的源码快照：

```bash
git archive --format=zip --prefix=BiliMusic/ -o releases/BiliMusic-0.2.0-source.zip HEAD
```

打包排除规则位于 `.gitattributes`。源码 ZIP 不包含 APK，安装包需单独作为 Release 附件上传。

首次安装为空音乐库，在线搜索结果实时获取。截图中的测试记录不会预置进 APK。Samsung One UI 等手机测试状态见根目录 TEST_REPORT.md，发布步骤见 [docs/PUBLISHING.md](../docs/PUBLISHING.md)。

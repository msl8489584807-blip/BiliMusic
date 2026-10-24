# GitHub 发布资料

目标仓库：https://github.com/msl8489584807-blip/BiliMusic

## 仓库简介（About / Description）

```text
Android 原生 B站音乐播放器，支持应用内搜索、在线播放、扫码登录、本地音乐与歌单管理。Kotlin + Jetpack Compose + Media3。
```

Topics：

```text
android kotlin jetpack-compose material3 bilibili music-player media3 exoplayer room
```

首页使用根目录 README.md。当前尚未指定源码许可证，不新增 LICENSE 文件，也不添加许可证徽章。

## 上传源码

`BiliMusic-0.2.0-source.zip` 是整理后的源码快照，包含构建脚本、Wrapper、资源、测试、截图和公开说明；不包含 Git 历史、本机配置、构建产物、开发对话或原始设备日志。

解压后，进入 `BiliMusic` 目录。首次发布到空仓库可执行：

```bash
git init -b main
git add .
git commit -m "Initial import of BiliMusic 0.2.0"
git remote add origin https://github.com/msl8489584807-blip/BiliMusic.git
git push -u origin main
```

以上命令用于首次导入空仓库，由仓库所有者或获授权的维护者在解压目录执行。仓库已有提交后，应从远程仓库克隆并正常提交更新，不要重复执行初始导入。也可用 GitHub Desktop 发布解压后的目录。

若使用 GitHub 网页上传，应上传解压后的文件和文件夹，不要只把 ZIP 上传到代码页；网页单次最多 100 个文件，必要时分批上传。确认 `.gitignore`、`.gitattributes` 等点文件一并加入。[GitHub 文件上传说明](https://docs.github.com/en/repositories/working-with-files/managing-files/adding-a-file-to-a-repository)

## 发布安装包

在仓库 Releases 创建草稿，填写：

- Tag：`v0.2.0`
- 标题：`BiliMusic v0.2.0 — 应用内搜歌与在线播放`
- 正文：复制 [releases/v0.2.0.md](../releases/v0.2.0.md)。
- 附件：`BiliMusic-0.2.0-debug.apk`、`BiliMusic-0.2.0-debug.apk.sha256`，可附上整理后的源码 ZIP。
- 本轮仍有真实账号与手机验收未完成，建议标记 Pre-release。

检查草稿的正文与附件后再发布。README 已指向本仓库 Releases，安装包上传后可从该入口获取。[GitHub Release 说明](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)

v0.2.0 的目标发布页为 https://github.com/msl8489584807-blip/BiliMusic/releases/tag/v0.2.0 。远程发布状态以仓库和 Releases 页面为准。

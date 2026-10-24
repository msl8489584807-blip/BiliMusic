# 交互约定

| 能力 | 统一实现 | 行为 |
|---|---|---|
| 表单 | Compose OutlinedTextField + AlertDialog | 明确标签、校验失败保留输入、保存期间禁用重复操作 |
| 选择 | Material 3 FilterChip / DropdownMenu | 中文标签，选中语义 |
| 消息 | 根 Scaffold SnackbarHost | 写入完成后确认，失败包含恢复动作 |
| CRUD | LibraryRepository | 事务写入；删除歌单/曲目记录前确认，不删除源文件 |
| 列表 | LazyColumn/LazyRow | 稳定 ID，保留导航返回位置，空态指向导入 |
| 播放 | PlaybackConnection | 唯一 MediaController；进度 500ms 更新 |
| 恢复 | BackupManager | 先校验再事务；用户选择合并/替换；缺授权保留并标记 |

原生 Android 安全区域与返回行为。曲目失效提供重新关联。搜索默认 B站音乐，另有本地音乐页签。点击在线搜索结果或 BV 默认通过原生 Media3 播放，不启动其他应用。加载、网络失败、重试、分页均使用真实状态。账号页提供扫码/过期/确认/退出状态；会话仅加密保存本机。大字体内容可滚动；图标按钮包含中文描述。

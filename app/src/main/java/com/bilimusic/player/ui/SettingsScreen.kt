package com.bilimusic.player.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.bilimusic.player.BuildConfig

@OptIn(ExperimentalLayoutApi::class)
@UnstableApi
@Composable fun SettingsScreen(vm:MainViewModel,onAccount:()->Unit,onBackup:(Boolean)->Unit,onRestore:()->Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var clearHistory by rememberSaveable{mutableStateOf(false)}
    var includeHistory by rememberSaveable{mutableStateOf(false)}
    var backupDialog by rememberSaveable{mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=32.dp)) {
        item{Column(Modifier.padding(24.dp)){Text("设置",style=MaterialTheme.typography.headlineLarge);Text("音乐与数据，都在你手里。",color=MaterialTheme.colorScheme.onSurfaceVariant)}}
        item{SheetAction("哔哩哔哩账号与收藏夹",Icons.Rounded.AccountCircle,onAccount)}
        item{SectionTitle("外观");FlowRow(Modifier.padding(horizontal=24.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf("dark" to "深色","light" to "浅色","system" to "跟随系统").forEach{(value,label)->FilterChip(selected=settings.theme==value,onClick={vm.updateSettings(settings.copy(theme=value))},label={Text(label)},enabled=!busy)}}}
        item{ListItem(headlineContent={Text("Material 动态配色")},supportingContent={Text("Android 12 及以上跟随壁纸配色")},trailingContent={Switch(settings.dynamicColor,{vm.updateSettings(settings.copy(dynamicColor=it))},enabled=!busy)})}
        item{SectionTitle("播放");Text("默认循环模式",modifier=Modifier.padding(horizontal=24.dp));FlowRow(Modifier.padding(horizontal=24.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf("不循环","单曲循环","列表循环").forEachIndexed{i,label->FilterChip(selected=settings.repeat==i,onClick={vm.updateSettings(settings.copy(repeat=i))},label={Text(label)},enabled=!busy)}}}
        item{ListItem(headlineContent={Text("默认随机播放")},trailingContent={Switch(settings.shuffle,{vm.updateSettings(settings.copy(shuffle=it))},enabled=!busy)})}
        item{SectionTitle("睡眠定时器","定时结束暂停播放，切换页面后仍然有效");FlowRow(Modifier.padding(horizontal=24.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(15,30,60,90).forEach{m->OutlinedButton(onClick={vm.sleep(m)},enabled=!busy){Text("$m 分钟")}};TextButton(onClick={vm.sleep(0)},enabled=!busy){Text("取消定时")}}}
        item{SectionTitle("数据管理");Column(Modifier.padding(horizontal=16.dp)) {
            SheetAction("备份音乐库",Icons.Rounded.Save){backupDialog=true}
            SheetAction("恢复备份",Icons.Rounded.Restore,onRestore)
            SheetAction("清除播放历史",Icons.Rounded.History){clearHistory=true}
        }}
        item{SectionTitle("关于 BiliMusic","版本 ${BuildConfig.VERSION_NAME}");Text("个人音乐播放器\nB站音乐搜索与应用内播放 · 本地音乐 · 无广告\n\nB站来源的音乐需要联网，登录可使用自己的收藏夹和账号访问权限。会话只加密保存在本机，不包含在数据备份中。\n\n图标来源于用户提供的高坂桐乃图片，仅用于个人安装。角色及原图的权利归原权利人所有。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(horizontal=24.dp,vertical=12.dp))}
    }
    if(clearHistory)ConfirmDialog("清除播放历史？","收藏、歌单和歌曲会保留。",busy,{clearHistory=false}){vm.action("历史已清除"){vm.repository.dao.clearHistory();clearHistory=false}}
    if(backupDialog)AlertDialog(onDismissRequest={backupDialog=false},title={Text("备份音乐库")},text={Column{Text("包含曲目元数据、歌单顺序、BV 收藏、标签与设置。默认不包含音频文件。恢复后可能需要重新关联文件。");Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Checkbox(includeHistory,{includeHistory=it});Text("包含播放历史")}}},confirmButton={TextButton(onClick={backupDialog=false;onBackup(includeHistory)}){Text("选择保存位置")}},dismissButton={TextButton(onClick={backupDialog=false}){Text("取消")}})
}

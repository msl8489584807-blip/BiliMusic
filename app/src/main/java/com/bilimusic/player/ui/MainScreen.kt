package com.bilimusic.player.ui

import android.Manifest
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.*
import com.bilimusic.player.database.*
import com.bilimusic.player.domain.BiliReference
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable fun MainScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val playback by vm.playback.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "home"
    val snackbar = remember { SnackbarHostState() }
    var moreId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var referenceEditor by rememberSaveable { mutableStateOf(false) }
    var playlistPicker by rememberSaveable { mutableStateOf<String?>(null) }
    var createPlaylist by rememberSaveable { mutableStateOf(false) }
    var renamePlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletePlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTrackId by rememberSaveable { mutableStateOf<String?>(null) }
    var tagsTrackId by rememberSaveable { mutableStateOf<String?>(null) }
    var documentTrackId by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreUri by rememberSaveable { mutableStateOf<String?>(null) }
    var includeHistory by rememberSaveable { mutableStateOf(false) }
    var libraryFilter by rememberSaveable { mutableStateOf("全部") }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    fun persist(uri: Uri) {
        try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        catch (_: SecurityException) { vm.message("此文件提供程序未授予长期访问权限，重启后可能需要重新关联") }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if(!granted) vm.message("通知权限未开启；系统媒体控制仍由 Android 管理") }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if(uris.isNotEmpty()) { uris.forEach(::persist); vm.import(uris); if(Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
    val lyrics = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val track = tracks.find { it.id == documentTrackId }
        if(uri != null && track != null) { persist(uri); vm.action("歌词已关联") { vm.repository.associateLyrics(track,uri) } }
    }
    val relink = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val track = tracks.find { it.id == documentTrackId }
        if(uri != null && track != null) { persist(uri); vm.action("音频已重新关联") { vm.repository.relink(track,uri) } }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if(uri != null) vm.backup(uri,includeHistory) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri?.toString() }
    val onImport = { import.launch(arrayOf("audio/*")) }
    fun openReference(track: Track) {
        val ref = BiliReference(track.bv ?: return,track.page)
        for (packageName in listOf("tv.danmaku.bili","com.bilibili.app.in")) {
            try { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(ref.webUrl)).setPackage(packageName));return }
            catch (_: ActivityNotFoundException) { /* Try the next official client, then a trusted web URL. */ }
        }
        try { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(ref.webUrl))) }
        catch (_: ActivityNotFoundException) { vm.message("未找到 Bilibili 或可打开视频链接的浏览器") }
    }
    fun play(track: Track, queue: List<Track>) { vm.play(track,queue) }
    fun openPlaylist(id: String) { nav.navigate("playlist/$id") }
    Scaffold(snackbarHost={ SnackbarHost(snackbar) },bottomBar={
        if(route != "player" && route != "account") Column {
            tracks.find { it.id == playback.trackId }?.let { MiniPlayer(vm,it) { nav.navigate("player") } }
            NavigationBar {
                listOf(Triple("home","首页",Icons.Rounded.Home),Triple("search","搜索",Icons.Rounded.Search),Triple("library","音乐库",Icons.Rounded.LibraryMusic),Triple("settings","设置",Icons.Rounded.Settings)).forEach { (path,label,icon) ->
                    NavigationBarItem(selected=route==path,onClick={ nav.navigate(path) { popUpTo("home") { saveState=true }; launchSingleTop=true; restoreState=true } },icon={ Icon(icon,label) },label={Text(label)})
                }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            NavHost(nav,startDestination="home",modifier=Modifier.weight(1f)) {
                composable("home") { HomeScreen(vm,onImport,onAddReference={ editorId=null; referenceEditor=true },onPlay=::play,onMore={moreId=it.id},onPlaylist=::openPlaylist,onCategory={libraryFilter=it; nav.navigate("library")},onSearch={vm.onlineMode.value=true;nav.navigate("search")}) }
                composable("search") { MusicSearchScreen(vm,onAccount={nav.navigate("account")},onPlay=::play,onMore={moreId=it.id},onPlaylist=::openPlaylist) }
                composable("account") { AccountScreen(vm,onBack={nav.popBackStack()}) }
                composable("library") { LibraryScreen(vm,libraryFilter,onFilter={libraryFilter=it},onImport=onImport,onReference={editorId=null;referenceEditor=true},onCreatePlaylist={createPlaylist=true},onPlay=::play,onMore={moreId=it.id},onPlaylist=::openPlaylist) }
                composable("settings") { SettingsScreen(vm,onAccount={nav.navigate("account")},onBackup={ includeHistory=it; export.launch("BiliMusic-${java.time.LocalDate.now()}.json") },onRestore={restore.launch(arrayOf("application/json","application/octet-stream"))}) }
                composable("player") { PlayerScreen(vm,onBack={nav.popBackStack()},onMore={moreId=it.id},onLyrics={documentTrackId=it.id;lyrics.launch(arrayOf("text/*","application/octet-stream"))}) }
                composable("playlist/{id}") { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    PlaylistScreen(vm,id,onBack={nav.popBackStack()},onPlay=::play,onMore={moreId=it.id},onRename={renamePlaylistId=id},onDelete={deletePlaylistId=id})
                }
            }
        }
    }
    val more = tracks.find { it.id == moreId }
    if(more != null) ModalBottomSheet(onDismissRequest={moreId=null}) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom=24.dp)) {
            ListItem(headlineContent={Text(more.title)},supportingContent={Text(more.artist)})
            SheetAction(if(more.favorite) "取消收藏" else "收藏歌曲",Icons.Rounded.Favorite) { vm.favorite(more);moreId=null }
            SheetAction("添加到歌单",Icons.Rounded.PlaylistAdd) { playlistPicker=more.id;moreId=null }
            SheetAction("编辑标签",Icons.Rounded.Label) { tagsTrackId=more.id;moreId=null }
            if(more.isLocal) {
                SheetAction("关联 / 更换 LRC 歌词",Icons.Rounded.Lyrics) { documentTrackId=more.id;lyrics.launch(arrayOf("text/*","application/octet-stream"));moreId=null }
                SheetAction("重新关联音频文件",Icons.Rounded.AudioFile) { documentTrackId=more.id;relink.launch(arrayOf("audio/*"));moreId=null }
            } else {
                SheetAction("编辑视频信息",Icons.Rounded.Edit) { editorId=more.id;referenceEditor=true;moreId=null }
                SheetAction("在 Bilibili 打开",Icons.AutoMirrored.Rounded.OpenInNew) { openReference(more);moreId=null }
            }
            SheetAction("从音乐库移除",Icons.Rounded.DeleteOutline) { deleteTrackId=more.id;moreId=null }
        }
    }
    if(referenceEditor) ReferenceDialog(vm,tracks.find {it.id==editorId}) { referenceEditor=false }
    if(createPlaylist) TextEntryDialog("新建歌单","歌单名称",busy=busy,onDismiss={createPlaylist=false}) { name -> vm.action { vm.repository.createPlaylist(name);createPlaylist=false } }
    renamePlaylistId?.let { id -> playlists.find {it.id==id}?.let { playlist -> TextEntryDialog("重命名歌单","歌单名称",playlist.name,busy,{renamePlaylistId=null}) { name -> vm.action {vm.repository.savePlaylist(playlist.copy(name=name.trim()));renamePlaylistId=null} } } }
    playlistPicker?.let { trackId -> AlertDialog(onDismissRequest={playlistPicker=null},title={Text("添加到歌单")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
        if(playlists.isEmpty()) Text("先在音乐库创建一个歌单")
        playlists.forEach { p -> TextButton(enabled=!busy,onClick={vm.action("已添加到 ${p.name}") { vm.repository.addToPlaylist(p.id,trackId);playlistPicker=null }}) {Text(p.name)} }
    }},confirmButton={ TextButton(onClick={playlistPicker=null}) {Text("关闭")} }) }
    tagsTrackId?.let { id ->
        var initial by remember(id) {mutableStateOf<String?>(null)}
        LaunchedEffect(id) { initial=vm.repository.dao.tagsFor(id).joinToString("，") {it.name} }
        initial?.let { TextEntryDialog("编辑标签","用逗号分隔标签",it,busy,{tagsTrackId=null},allowEmpty=true) { tags -> vm.action("标签已保存") { vm.repository.setTags(id,tags);tagsTrackId=null } } }
    }
    deletePlaylistId?.let { id -> ConfirmDialog("删除歌单？","歌单内的音频文件和音乐库记录会保留。",busy,{deletePlaylistId=null}) {vm.action {vm.repository.dao.deletePlaylist(id);deletePlaylistId=null;nav.popBackStack()}} }
    deleteTrackId?.let { id -> ConfirmDialog("从音乐库移除？","移除收藏、历史和歌单关联，原始音频文件会保留。",busy,{deleteTrackId=null}) {vm.action {val index=vm.playback.state.value.queueIds.indexOf(id);if(index>=0)vm.playback.remove(index);vm.repository.removeTrack(id);deleteTrackId=null}} }
    restoreUri?.let { value -> AlertDialog(onDismissRequest={if(!busy) restoreUri=null},title={Text("恢复 BiliMusic 备份")},text={Text("备份不包含音频。恢复不会自动获得原文件的访问权限；失效曲目会保留并提示重新关联。替换会清除当前音乐库元数据。")},confirmButton={TextButton(enabled=!busy,onClick={vm.restore(Uri.parse(value),false);restoreUri=null}) {Text("合并恢复")}},dismissButton={Row { TextButton(enabled=!busy,onClick={restoreUri=null}) {Text("取消")}; TextButton(enabled=!busy,onClick={vm.restore(Uri.parse(value),true);restoreUri=null}) {Text("替换恢复")} }}) }
}

@Composable fun SheetAction(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) { TextButton(onClick,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).padding(horizontal=12.dp)) { Icon(icon,null);Spacer(Modifier.width(16.dp));Text(text,modifier=Modifier.weight(1f)) } }
@Composable fun ConfirmDialog(title:String,text:String,busy:Boolean,onDismiss:()->Unit,onConfirm:()->Unit) { AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Text(text)},confirmButton={TextButton(enabled=!busy,onClick=onConfirm){Text("确认删除")}},dismissButton={TextButton(onClick=onDismiss){Text("取消")}}) }
@Composable fun TextEntryDialog(title:String,label:String,initial:String="",busy:Boolean=false,onDismiss:()->Unit,allowEmpty:Boolean=false,onSave:(String)->Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={OutlinedTextField(value,{value=it.take(1800)},label={Text(label)},modifier=Modifier.fillMaxWidth(),maxLines=4)},confirmButton={TextButton(enabled=!busy&&(allowEmpty||value.isNotBlank()),onClick={onSave(value)}){Text("保存")}},dismissButton={TextButton(enabled=!busy,onClick=onDismiss){Text("取消")}})
}

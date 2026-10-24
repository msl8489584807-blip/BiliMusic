package com.bilimusic.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.bilimusic.player.database.*
import com.bilimusic.player.domain.LibraryRules
import kotlin.math.roundToInt

val Categories = listOf("动漫 OST","Vocaloid","翻唱","钢琴演奏","游戏音乐","其他")
@UnstableApi
@Composable fun HomeScreen(vm:MainViewModel,onImport:()->Unit,onAddReference:()->Unit,onPlay:(Track,List<Track>)->Unit,onMore:(Track)->Unit,onPlaylist:(String)->Unit,onCategory:(String)->Unit,onSearch:()->Unit) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val state by vm.playback.state.collectAsStateWithLifecycle()
    val byId=remember(tracks){tracks.associateBy{it.id}}
    val recent=remember(history,tracks){history.mapNotNull{byId[it.trackId]}.distinctBy{it.id}.take(6)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp)) {
        item { Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("BiliMusic",style=MaterialTheme.typography.headlineLarge)
            Text("让喜欢的音乐，留在身边。",color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Button(onClick=onSearch) {Icon(Icons.Rounded.Search,null);Spacer(Modifier.width(6.dp));Text("搜索音乐")}
                OutlinedButton(onClick=onImport) {Text("导入音乐")}
            }
            TextButton(onClick=onAddReference){Text("通过 BV 添加音乐")}
        } }
        if(tracks.isEmpty()) item { EmptyState("从一首喜欢的歌开始","搜索 B站音乐，在 BiliMusic 中直接播放；也可以导入设备里的音频。") }
        if(recent.isNotEmpty()) { item {SectionTitle("最近播放")};items(recent,key={"recent:${it.id}"}) {t->TrackRow(t,t.id==state.trackId,{onPlay(t,recent)}, {onMore(t)})} }
        if(playlists.isNotEmpty()) item {
            SectionTitle("我的歌单")
            LazyRow(contentPadding=PaddingValues(horizontal=24.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                items(playlists.sortedByDescending{it.favorite}.take(8),key={it.id}) {p->
                    ElevatedCard(onClick={onPlaylist(p.id)},modifier=Modifier.width(160.dp)) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Icon(if(p.favorite) Icons.Rounded.Favorite else Icons.Rounded.QueueMusic,null,tint=MaterialTheme.colorScheme.primary);Text(p.name,style=MaterialTheme.typography.titleMedium,maxLines=2)}}
                }
            }
        }
        val favorites=tracks.filter{it.favorite}.take(5)
        if(favorites.isNotEmpty()){item{SectionTitle("心动收藏")};items(favorites,key={"favorite:${it.id}"}){t->TrackRow(t,t.id==state.trackId,{onPlay(t,favorites)},{onMore(t)})}}
        item {SectionTitle("音乐分类");LazyRow(contentPadding=PaddingValues(horizontal=24.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){items(Categories){c->SuggestionChip(onClick={onCategory(c)},label={Text(c)})}}}
        if(tracks.isNotEmpty()){item{SectionTitle("最近添加","${tracks.count{it.isLocal}} 首本地歌曲 · ${tracks.count{!it.isLocal}} 首 B站音乐")};items(tracks.take(8),key={"added:${it.id}"}){t->TrackRow(t,t.id==state.trackId,{onPlay(t,tracks)},{onMore(t)})}}
    }
}

@UnstableApi
@Composable fun SearchScreen(vm:MainViewModel,onPlay:(Track,List<Track>)->Unit,onMore:(Track)->Unit,onPlaylist:(String)->Unit) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val playlists by vm.playlistSearch.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        SectionTitle("寻找一段旋律")
        OutlinedTextField(query,{vm.query.value=it.take(200)},modifier=Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=12.dp),label={Text("曲名、艺术家、专辑、标签或歌单")},leadingIcon={Icon(Icons.Rounded.Search,null)},singleLine=true,trailingIcon={if(query.isNotEmpty())ActionIcon(Icons.Rounded.Close,"清空搜索"){vm.query.value=""}})
        LazyColumn(Modifier.weight(1f)) {
            if(query.isBlank())item{EmptyState("搜索你的音乐库","支持本地歌曲、歌单和 Bilibili 收藏信息。")}
            else if(results.isEmpty()&&playlists.isEmpty())item{EmptyState("没有找到结果","换一个曲名、艺术家或标签试试。")}
            if(playlists.isNotEmpty()) {item{SectionTitle("歌单")};items(playlists,key={"p:${it.id}"}){p->ListItem(headlineContent={Text(p.name)},leadingContent={Icon(Icons.Rounded.QueueMusic,null)},modifier=Modifier.clickable{onPlaylist(p.id)})}}
            if(results.isNotEmpty())item{SectionTitle("歌曲与视频引用","最多显示前 300 条结果")}
            items(results,key={it.id}){t->TrackRow(t,onClick={onPlay(t,results)},onMore={onMore(t)})}
        }
    }
}

@UnstableApi
@Composable fun LibraryScreen(vm:MainViewModel,filter:String,onFilter:(String)->Unit,onImport:()->Unit,onReference:()->Unit,onCreatePlaylist:()->Unit,onPlay:(Track,List<Track>)->Unit,onMore:(Track)->Unit,onPlaylist:(String)->Unit) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val state by vm.playback.state.collectAsStateWithLifecycle()
    val selected=remember(tracks,filter,history) { when {
        filter=="本地歌曲"->tracks.filter{it.isLocal}
        filter=="Bilibili"->tracks.filter{!it.isLocal}
        filter=="收藏"->tracks.filter{it.favorite}
        filter=="最近播放"->history.mapNotNull{h->tracks.find{it.id==h.trackId}}.distinctBy{it.id}
        filter.startsWith("艺术家：")->tracks.filter{it.artist==filter.removePrefix("艺术家：")}
        filter.startsWith("专辑：")->tracks.filter{it.album==filter.removePrefix("专辑：")}
        filter in Categories->tracks.filter{it.category==filter}
        else->tracks
    } }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp)) {
        item {Row(Modifier.fillMaxWidth().padding(24.dp),verticalAlignment=Alignment.CenterVertically){Text("音乐库",style=MaterialTheme.typography.headlineLarge,modifier=Modifier.weight(1f));ActionIcon(Icons.Rounded.Add,"导入音乐",onClick=onImport)}}
        item{LazyRow(contentPadding=PaddingValues(horizontal=24.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){items(listOf("全部","本地歌曲","收藏","最近播放","歌单","艺术家","专辑","Bilibili")){f->FilterChip(selected=f==filter,onClick={onFilter(f)},label={Text(f)})}}}
        if(filter=="歌单") {
            item {TextButton(onClick=onCreatePlaylist,modifier=Modifier.padding(horizontal=16.dp)){Icon(Icons.Rounded.PlaylistAdd,null);Spacer(Modifier.width(8.dp));Text("新建歌单")}}
            if(playlists.isEmpty())item{EmptyState("第一张歌单","把属于同一段心情的歌曲放在一起。","创建歌单",onCreatePlaylist)}
            items(playlists,key={it.id}){p->ListItem(headlineContent={Text(p.name)},leadingContent={Icon(Icons.Rounded.QueueMusic,null)},trailingContent={if(p.favorite)Icon(Icons.Rounded.Favorite,"已收藏歌单")},modifier=Modifier.clickable{onPlaylist(p.id)})}
        } else if(filter=="艺术家"||filter=="专辑") {
            val groups=if(filter=="艺术家") tracks.groupBy{it.artist} else tracks.groupBy{it.album}
            if(groups.isEmpty())item{EmptyState("还没有${filter}","导入音频后会按文件元数据归类。","导入音乐",onImport)}
            items(groups.keys.sorted(),key={it}){name->ListItem(headlineContent={Text(name)},supportingContent={Text("${groups[name]?.size} 首")},leadingContent={Icon(if(filter=="艺术家")Icons.Rounded.Person else Icons.Rounded.Album,null)},modifier=Modifier.clickable{onFilter("$filter：$name")})}
        } else {
            if(filter.startsWith("艺术家：")||filter.startsWith("专辑：")||filter in Categories)item{SectionTitle(filter)}
            if(filter=="Bilibili")item{Column(Modifier.padding(24.dp)){Text("BV 收藏用于管理视频引用，格式校验不代表视频存在。",style=MaterialTheme.typography.bodyMedium);TextButton(onClick=onReference){Text("添加 BV 收藏")}}}
            if(selected.isEmpty())item{EmptyState("这里还没有音乐","导入歌曲、收藏曲目，或切换上方分类。",if(filter=="Bilibili")"添加 BV 收藏" else "导入音乐",if(filter=="Bilibili")onReference else onImport)}
            items(selected,key={it.id}){t->TrackRow(t,t.id==state.trackId,{onPlay(t,selected)},{onMore(t)})}
        }
    }
}

@UnstableApi
@Composable fun PlaylistScreen(vm:MainViewModel,id:String,onBack:()->Unit,onPlay:(Track,List<Track>)->Unit,onMore:(Track)->Unit,onRename:()->Unit,onDelete:()->Unit) {
    val playlists by vm.playlists.collectAsStateWithLifecycle();val all by vm.tracks.collectAsStateWithLifecycle();val entries by vm.entries.collectAsStateWithLifecycle()
    val playlist=playlists.find{it.id==id}
    val tracks=remember(all,entries,id){val map=all.associateBy{it.id};entries.filter{it.playlistId==id}.sortedBy{it.position}.mapNotNull{map[it.trackId]}}
    var addDialog by rememberSaveable{mutableStateOf(false)}
    val rowHeight=with(LocalDensity.current){88.dp.toPx()}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=32.dp)) {
        item{Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically){ActionIcon(Icons.AutoMirrored.Rounded.ArrowBack,"返回",onClick=onBack);Text(playlist?.name?:"歌单",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f));ActionIcon(Icons.Rounded.Edit,"重命名歌单",onClick=onRename)}}
        if(playlist!=null)item{Column(Modifier.padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("${tracks.size} 首 · 长按右侧手柄拖动排序",color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                FilledTonalButton(onClick={addDialog=true}){Text("添加歌曲")}
                ActionIcon(if(playlist.favorite)Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,"收藏歌单",active=playlist.favorite){vm.action{vm.repository.savePlaylist(playlist.copy(favorite=!playlist.favorite))}}
                ActionIcon(Icons.Rounded.DeleteOutline,"删除歌单",onClick=onDelete)
            }
        }}
        if(tracks.isEmpty())item{EmptyState("把喜欢的歌放进来","支持加入本地歌曲和 BV 引用。","添加歌曲",{addDialog=true})}
        itemsIndexed(tracks,key={_,t->t.id}){index,t->
            var offset by remember(t.id){mutableFloatStateOf(0f)}
            Row(Modifier.fillMaxWidth().heightIn(min=88.dp).background(if(offset!=0f)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)){TrackRow(t,onClick={onPlay(t,tracks)},onMore={onMore(t)})}
                ActionIcon(Icons.Rounded.RemoveCircleOutline,"从歌单移除 ${t.title}"){vm.action{vm.repository.removeFromPlaylist(id,t.id)}}
                Box(Modifier.size(48.dp).pointerInput(t.id,tracks.map{it.id}) {
                    detectDragGesturesAfterLongPress(onDragStart={offset=0f},onDragCancel={offset=0f},onDragEnd={
                        val target=(index+(offset/rowHeight).roundToInt()).coerceIn(tracks.indices)
                        if(target!=index)vm.action{vm.repository.reorder(id,LibraryRules.move(tracks.map{it.id},index,target))}
                        offset=0f
                    },onDrag={change,amount->change.consume();offset+=amount.y})
                },contentAlignment=Alignment.Center){Icon(Icons.Rounded.DragHandle,"长按拖动 ${t.title} 排序")}
            }
        }
    }
    if(addDialog) AlertDialog(onDismissRequest={addDialog=false},title={Text("添加到歌单")},text={LazyColumn(Modifier.heightIn(max=420.dp)) {
        val candidates=all.filter {candidate->tracks.none{it.id==candidate.id}}
        if(candidates.isEmpty())item{Text("没有可添加的歌曲。先导入音乐或收藏 BV。")}
        items(candidates,key={it.id}){t->TextButton(onClick={vm.action("已添加 ${t.title}"){vm.repository.addToPlaylist(id,t.id)}}){Text(t.title)}}
    }},confirmButton={TextButton(onClick={addDialog=false}){Text("完成")}})
}

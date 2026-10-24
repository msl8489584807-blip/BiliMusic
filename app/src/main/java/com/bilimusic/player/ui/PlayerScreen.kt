package com.bilimusic.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.bilimusic.player.database.Track
import com.bilimusic.player.domain.LrcParser

@UnstableApi
@Composable fun MiniPlayer(vm:MainViewModel,track:Track,onOpen:()->Unit) {
    val state by vm.playback.state.collectAsStateWithLifecycle()
    val position by vm.playback.position.collectAsStateWithLifecycle()
    Surface(color=MaterialTheme.colorScheme.surfaceVariant,tonalElevation=4.dp,modifier=Modifier.fillMaxWidth().clickable(onClick=onOpen)) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start=16.dp,end=8.dp,top=8.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Artwork(track,size=44.dp)
                Column(Modifier.weight(1f)){Text(track.title,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis);Text(track.artist,maxLines=1,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                ActionIcon(if(state.playing)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,if(state.playing)"暂停" else "播放",enabled=state.connected){vm.playback.toggle()}
                ActionIcon(Icons.Rounded.SkipNext,"下一首",enabled=state.queueIds.size>1){vm.playback.next()}
            }
            LinearProgressIndicator(progress={if(state.duration>0)(position.toFloat()/state.duration).coerceIn(0f,1f) else 0f},modifier=Modifier.fillMaxWidth().height(2.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable fun PlayerScreen(vm:MainViewModel,onBack:()->Unit,onMore:(Track)->Unit,onLyrics:(Track)->Unit) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val state by vm.playback.state.collectAsStateWithLifecycle()
    val position by vm.playback.position.collectAsStateWithLifecycle()
    val track=tracks.find{it.id==state.trackId}
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var drag by remember { mutableStateOf<Float?>(null) }
    val lyrics=remember(track?.lyricsText){LrcParser.parse(track?.lyricsText.orEmpty())}
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            ActionIcon(Icons.AutoMirrored.Rounded.ArrowBack,"返回",onClick=onBack)
            Text("正在播放",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
            track?.let{ActionIcon(Icons.Rounded.MoreVert,"歌曲操作"){onMore(it)}}
        }
        if(track==null) EmptyState("还没有正在播放的歌曲","从音乐库选择一首本地歌曲开始。")
        else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=28.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                if(!showLyrics) {Spacer(Modifier.height(20.dp));Artwork(track,modifier=Modifier.widthIn(max=340.dp).fillMaxWidth().aspectRatio(1f),size=320.dp);Spacer(Modifier.height(28.dp))}
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(track.title,style=MaterialTheme.typography.headlineSmall);Text(track.artist,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp))};ActionIcon(if(track.favorite)Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,"收藏歌曲",active=track.favorite){vm.favorite(track)}}
                state.error?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(top=12.dp)) }
                if(state.buffering) Text("正在读取音频…",style=MaterialTheme.typography.bodyMedium)
                Slider(value=drag?:position.toFloat().coerceIn(0f,state.duration.toFloat().coerceAtLeast(1f)),onValueChange={drag=it},onValueChangeFinished={drag?.let{vm.playback.seek(it.toLong())};drag=null},valueRange=0f..state.duration.toFloat().coerceAtLeast(1f),enabled=state.duration>0,modifier=Modifier.fillMaxWidth().padding(top=16.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(formatTime(drag?.toLong()?:position),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodyMedium);Text(formatTime(state.duration),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodyMedium)}
                Row(Modifier.fillMaxWidth().padding(vertical=16.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                    ActionIcon(Icons.Rounded.Shuffle,if(state.shuffle)"关闭随机播放" else "随机播放",active=state.shuffle){vm.playback.shuffle()}
                    ActionIcon(Icons.Rounded.SkipPrevious,"上一首"){vm.playback.previous()}
                    FilledIconButton(onClick={vm.playback.toggle()},enabled=state.connected,modifier=Modifier.size(72.dp)){Icon(if(state.playing)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,if(state.playing)"暂停" else "播放",modifier=Modifier.size(38.dp))}
                    ActionIcon(Icons.Rounded.SkipNext,"下一首",enabled=state.queueIds.size>1){vm.playback.next()}
                    ActionIcon(if(state.repeat==1)Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,when(state.repeat){1->"单曲循环";2->"列表循环";else->"不循环"},active=state.repeat!=0){vm.playback.repeat()}
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){TextButton(onClick={showLyrics=!showLyrics}){Icon(Icons.Rounded.Lyrics,null);Spacer(Modifier.width(8.dp));Text(if(showLyrics)"封面" else "歌词")};TextButton(onClick={showQueue=true}){Icon(Icons.AutoMirrored.Rounded.QueueMusic,null);Spacer(Modifier.width(8.dp));Text("队列 · ${state.queueIds.size}")}}
                if(showLyrics) {
                    if(lyrics.isEmpty()) EmptyState("还没有歌词","选择 UTF-8 编码的本地 .lrc 文件。","关联歌词",{onLyrics(track)})
                    else {
                        val listState=rememberLazyListState()
                        val active=lyrics.indexOfLast{it.timeMs<=position}
                        LaunchedEffect(active){if(active>=0&&!listState.isScrollInProgress)listState.animateScrollToItem((active-1).coerceAtLeast(0))}
                        LazyColumn(Modifier.fillMaxWidth().height(280.dp),state=listState,contentPadding=PaddingValues(vertical=12.dp)) {
                            itemsIndexed(lyrics){index,line->Text(line.text.ifBlank{"♪"},style=MaterialTheme.typography.titleMedium,color=if(index==active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.fillMaxWidth().clickable{vm.playback.seek(line.timeMs)}.padding(vertical=14.dp))}
                        }
                        TextButton(onClick={onLyrics(track)}){Text("更换歌词")}
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
    if(showQueue) ModalBottomSheet(onDismissRequest={showQueue=false}) {
        SectionTitle("播放队列","本地与在线音乐")
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=480.dp),contentPadding=PaddingValues(bottom=32.dp)) {
            itemsIndexed(state.queueIds,key={_,id->id}){index,id->tracks.find{it.id==id}?.let{t->
                ListItem(headlineContent={Text(t.title)},supportingContent={Text(t.artist)},leadingContent={if(id==state.trackId)Icon(Icons.Rounded.GraphicEq,"当前歌曲",tint=MaterialTheme.colorScheme.primary)},trailingContent={ActionIcon(Icons.Rounded.Close,"从队列移除 ${t.title}"){vm.playback.remove(index)}},modifier=Modifier.clickable{vm.playback.jump(index)})
            }}
        }
    }
}

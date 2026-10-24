package com.bilimusic.player.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.bilimusic.player.data.bilibili.*
import com.bilimusic.player.database.Track
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@UnstableApi
@Composable fun MusicSearchScreen(vm:MainViewModel,onAccount:()->Unit,onPlay:(Track,List<Track>)->Unit,onMore:(Track)->Unit,onPlaylist:(String)->Unit) {
    val online by vm.onlineMode.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start=24.dp,end=12.dp,top=12.dp),verticalAlignment=Alignment.CenterVertically){Text("搜索音乐",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.weight(1f));ActionIcon(Icons.Rounded.AccountCircle,"哔哩哔哩账号",onClick=onAccount)}
        TabRow(selectedTabIndex=if(online)0 else 1){
            Tab(selected=online,onClick={vm.onlineMode.value=true},text={Text("B站音乐")})
            Tab(selected=!online,onClick={vm.onlineMode.value=false},text={Text("本地音乐")})
        }
        if(online)OnlineSearchPane(vm,onAccount) else SearchScreen(vm,onPlay,onMore,onPlaylist)
    }
}

@UnstableApi
@Composable private fun OnlineSearchPane(vm:MainViewModel,onAccount:()->Unit) {
    val query by vm.query.collectAsStateWithLifecycle()
    val state by vm.onlineSearch.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val keyboard=LocalSoftwareKeyboardController.current
    val favorites=remember(tracks){tracks.filter{it.favorite}.map{it.bv to it.page}.toSet()}
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(query,{vm.onlinePage.value=1;vm.query.value=it.take(200)},label={Text("搜索B站音乐、歌手或视频")},modifier=Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=16.dp),singleLine=true,leadingIcon={Icon(Icons.Rounded.Search,null)},trailingIcon={if(query.isNotEmpty())ActionIcon(Icons.Rounded.Close,"清空搜索"){vm.query.value=""}},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={keyboard?.hide();vm.retryOnline()}))
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=24.dp)) {
            if(query.isBlank())item{EmptyState("在这里搜歌，直接开听","搜索 B站音乐分区，选择结果后在 BiliMusic 内播放。")}
            if(state.loading)item{Column(Modifier.fillMaxWidth().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally){CircularProgressIndicator();Text("正在搜索 B站音乐…",modifier=Modifier.padding(top=16.dp))}}
            state.error?.let{error->item{NetworkError(error,{vm.retryOnline()},onAccount)}}
            state.data?.let{page->
                if(page.songs.isEmpty())item{EmptyState("没有找到音乐","换一个歌名、歌手或关键词试试。")}
                else item{Text("B站音乐分区 · 点击直接播放",modifier=Modifier.padding(horizontal=24.dp,vertical=8.dp),color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)}
                items(page.songs,key={it.bv to it.page}){song->OnlineSongRow(song,busy,(song.bv to song.page) in favorites,{vm.playOnline(song,page.songs)},{vm.favoriteOnline(song)})}
                if(page.songs.isNotEmpty())item{PageControls(page,{vm.onlinePage.value=page.page-1},{vm.onlinePage.value=page.page+1})}
            }
        }
    }
}

@Composable fun OnlineSongRow(song:OnlineSong,busy:Boolean=false,favorite:Boolean=false,onPlay:()->Unit,onFavorite:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled=!busy,onClick=onPlay).padding(start=24.dp,end=12.dp,top=10.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.size(64.dp)){
            Box(contentAlignment=Alignment.Center){Icon(Icons.Rounded.MusicNote,null,tint=MaterialTheme.colorScheme.primary);song.cover?.let{AsyncImage(model=it,contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())}}
        }
        Column(Modifier.weight(1f)){
            Text(song.title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text("${song.artist} · ${formatTime(song.durationMs)}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        ActionIcon(if(favorite)Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,"收藏 ${song.title}",enabled=!busy,active=favorite,onClick=onFavorite)
    }
}
@Composable private fun NetworkError(error:String,onRetry:()->Unit,onAccount:(()->Unit)?=null) {
    Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(error,color=MaterialTheme.colorScheme.error);Row{TextButton(onClick=onRetry){Text("重试")};onAccount?.let{TextButton(onClick=it){Text("账号与登录")}}}}
}
@Composable private fun PageControls(page:SearchPage,onPrevious:()->Unit,onNext:()->Unit) {
    Row(Modifier.fillMaxWidth().padding(20.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){OutlinedButton(enabled=page.page>1,onClick=onPrevious){Text("上一页")};Text("第 ${page.page} 页");OutlinedButton(enabled=page.page<page.pages,onClick=onNext){Text("下一页")}}
}

@UnstableApi
@Composable fun AccountScreen(vm:MainViewModel,onBack:()->Unit) {
    val state by vm.accountState.collectAsStateWithLifecycle()
    val qr by vm.qrState.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    fun closeFolder(){vm.closeFolder()}
    BackHandler(enabled=state.folder!=null){closeFolder()}
    DisposableEffect(vm){vm.refreshAccount();onDispose{vm.cancelLogin()}}
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){ActionIcon(Icons.AutoMirrored.Rounded.ArrowBack,"返回",onClick={if(state.folder!=null)closeFolder() else onBack()});Text(state.folder?.title?:"哔哩哔哩账号",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f));ActionIcon(Icons.Rounded.Refresh,"刷新账号"){vm.refreshAccount()}}
        if(state.loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=24.dp)) {
            state.error?.let{error->item{NetworkError(error,{if(state.folder!=null)vm.loadFolder(state.folder!!) else vm.refreshAccount()})}}
            val account=state.account
            if(account?.loggedIn==true) {
                if(state.folder==null){
                    item{ListItem(headlineContent={Text(account.name)},supportingContent={Text("已登录 · UID ${account.mid}")},leadingContent={AsyncImage(account.avatar,null,modifier=Modifier.size(48.dp))},trailingContent={TextButton(enabled=!busy,onClick={vm.logout()}){Text("退出本机登录")}})}
                    item{SectionTitle("我的 B站收藏夹","选择收藏夹，直接在 BiliMusic 中听")}
                    if(state.folders.isEmpty()&&!state.loading)item{EmptyState("没有读取到收藏夹","点击右上角刷新账号重试。")}
                    items(state.folders,key={it.id}){folder->ListItem(headlineContent={Text(folder.title)},supportingContent={Text("${folder.count} 个内容")},leadingContent={Icon(Icons.Rounded.Folder,null)},modifier=Modifier.clickable{vm.loadFolder(folder)})}
                } else state.songs?.let{page->
                    items(page.songs,key={it.bv to it.page}){song->OnlineSongRow(song,busy,onPlay={vm.playOnline(song,page.songs)},onFavorite={vm.favoriteOnline(song)})}
                    item{PageControls(page,{vm.loadFolder(state.folder!!,page.page-1)},{vm.loadFolder(state.folder!!,page.page+1)})}
                }
            } else {
                item{Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)){
                    Text("登录自己的 B站账号",style=MaterialTheme.typography.headlineSmall)
                    Text("可先搜索和播放公开音乐。登录后读取自己的收藏夹，并使用账号现有的访问权限。",style=MaterialTheme.typography.bodyMedium)
                    qr.qr?.let{session->
                        val bitmap by produceState<Bitmap?>(null,session.url){value=withContext(Dispatchers.Default){
                            val matrix=MultiFormatWriter().encode(session.url,BarcodeFormat.QR_CODE,640,640)
                            val pixels=IntArray(640*640){i->if(matrix[i%640,i/640])android.graphics.Color.BLACK else android.graphics.Color.WHITE}
                            Bitmap.createBitmap(pixels,640,640,Bitmap.Config.ARGB_8888)
                        }}
                        bitmap?.let{Image(it.asImageBitmap(),"B站登录二维码",modifier=Modifier.size(256.dp).background(Color.White).padding(8.dp))}
                        Text("使用 B站扫一扫；也可截屏后从 B站相册识别并确认登录。",style=MaterialTheme.typography.bodyMedium)
                    }
                    if(qr.message.isNotBlank())Text(qr.message,color=if(qr.expired)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Button(enabled=!qr.loading,onClick={vm.createLoginQr()}){Text(if(qr.qr==null)"生成登录二维码" else "刷新二维码")}
                    Text("登录信息加密保存在本机，不会加入音乐库备份。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }}
            }
        }
    }
}

package com.bilimusic.player.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.bilimusic.player.MusicApplication
import com.bilimusic.player.data.*
import com.bilimusic.player.database.*
import com.bilimusic.player.data.bilibili.*
import com.bilimusic.player.domain.BiliReference
import com.bilimusic.player.playback.PlaybackConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@UnstableApi
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MusicApplication
    val repository = app.repository
    val playback = PlaybackConnection(application, viewModelScope)
    val tracks = repository.tracks.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val playlists = repository.playlists.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val entries = repository.entries.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val history = repository.history.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val settings = app.settings.settings.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),AppSettings())
    val query = MutableStateFlow("")
    val onlineMode=MutableStateFlow(true)
    val onlinePage=MutableStateFlow(1)
    private val searchRetry=MutableStateFlow(0)
    val onlineSearch=combine(query.debounce(350),onlinePage,searchRetry,onlineMode){q,p,_,online->Triple(q,p,online)}.flatMapLatest{(q,p,online)->
        if(q.isBlank()||!online)flowOf(OnlineSearchState()) else flow {
            emit(OnlineSearchState(loading=true))
            emit(OnlineSearchState(data=app.bilibili.search(q,p)))
        }.catch{if(it is CancellationException)throw it else emit(OnlineSearchState(error=it.message?:"联网搜索失败，请重试"))}
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),OnlineSearchState())
    val accountState=MutableStateFlow(AccountState())
    val qrState=MutableStateFlow(QrState())
    private var accountJob:Job?=null
    private var loginJob:Job?=null
    private var folderJob:Job?=null
    val searchResults = query.debounce(300).distinctUntilChanged().flatMapLatest { if (it.isBlank()) flowOf(emptyList()) else repository.search(it) }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val playlistSearch = query.debounce(300).distinctUntilChanged().flatMapLatest { if (it.isBlank()) flowOf(emptyList()) else repository.dao.searchPlaylists(com.bilimusic.player.domain.LibraryRules.searchPattern(it)) }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    private val channel = Channel<String>(Channel.BUFFERED)
    val messages = channel.receiveAsFlow()
    val busy = MutableStateFlow(false)
    fun message(text: String) { viewModelScope.launch { channel.send(text) } }
    fun action(success: String? = null, task: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { task(); success?.let { channel.send(it) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { channel.send(e.message?.take(240) ?: "操作失败，请重试") }
            finally { busy.value = false }
        }
    }
    fun import(uris: List<Uri>) = action {
        val report = repository.importAudio(uris)
        channel.send("已导入 ${report.added} 首，重复 ${report.duplicates} 首" + if (report.failures.isNotEmpty()) "；${report.failures.size} 首失败：${report.failures.first()}" else "")
    }
    fun play(track: Track, queue: List<Track>) = action {
        if(!track.isLocal) {
            val ref=BiliReference(track.bv?:error("BV 编号缺失"),track.page)
            val fresh=repository.saveOnline(app.bilibili.metadata(ref))
            app.bilibili.resolve(ref)
            playback.play(queue.map{if(it.id==track.id)fresh else it},fresh.id,settings.value.repeat,settings.value.shuffle)
            return@action
        }
        val accessible = withContext(Dispatchers.IO) {
            try { app.contentResolver.openAssetFileDescriptor(Uri.parse(track.uri),"r")?.use { true } ?: false } catch (_: Exception) { false }
        }
        repository.available(track.id,accessible)
        require(accessible) { "无法读取音频，请重新关联文件；云端文件可能需要联网" }
        playback.play(queue.map { if (it.id == track.id) it.copy(available=true) else it }, track.id, settings.value.repeat, settings.value.shuffle)
    }
    fun favorite(track: Track) = action { repository.favorite(track) }
    fun playOnline(song:OnlineSong,queue:List<OnlineSong>)=action {
        app.bilibili.resolve(BiliReference(song.bv,song.page))
        val values=queue.distinctBy{it.bv to it.page}.map{repository.saveOnline(it)}
        val selected=values.firstOrNull{it.bv==song.bv&&it.page==song.page}?:repository.saveOnline(song)
        playback.play(if(selected in values)values else listOf(selected),selected.id,settings.value.repeat,settings.value.shuffle)
    }
    fun favoriteOnline(song:OnlineSong)=action("已加入我的收藏") {
        val track=repository.saveOnline(song)
        if(!track.favorite)repository.favorite(track)
    }
    fun retryOnline(){searchRetry.value++}
    fun refreshAccount() {
        accountJob?.cancel()
        accountJob=viewModelScope.launch {
            accountState.update{it.copy(loading=true,error=null)}
            try {
                val account=app.bilibili.account()
                accountState.value=AccountState(loading=account.loggedIn,account=account)
                val folders=if(account.loggedIn)app.bilibili.folders(account.mid) else emptyList()
                accountState.update{it.copy(loading=false,folders=folders)}
            } catch(e:CancellationException){throw e} catch(e:Exception){accountState.update{it.copy(loading=false,error=e.message?:"账号读取失败")}}
        }
    }
    fun loadFolder(folder:FavoriteFolder,page:Int=1) {
        folderJob?.cancel()
        folderJob=viewModelScope.launch {
            accountState.update{it.copy(loading=true,folder=folder,error=null)}
            try {val songs=app.bilibili.favorites(folder.id,page);accountState.update{it.copy(loading=false,songs=songs)}}
            catch(e:CancellationException){throw e} catch(e:Exception){accountState.update{it.copy(loading=false,songs=null,error=e.message?:"收藏夹读取失败")}}
        }
    }
    fun closeFolder(){folderJob?.cancel();accountState.update{it.copy(folder=null,songs=null,error=null,loading=false)}}
    fun createLoginQr() {
        loginJob?.cancel()
        loginJob=viewModelScope.launch {
            qrState.value=QrState(loading=true,message="正在生成二维码")
            try {
                val qr=app.bilibili.generateQr();qrState.value=QrState(qr=qr,message="等待扫码")
                repeat(90) {
                    delay(2000)
                    when(app.bilibili.pollQr(qr.key)) {
                        0->{qrState.value=QrState(message="登录成功");refreshAccount();retryOnline();return@launch}
                        86090->qrState.update{it.copy(message="已扫码，请在 B站确认登录")}
                        86038->{qrState.update{it.copy(expired=true,message="二维码已过期，请刷新")};return@launch}
                        86101->Unit
                        else->error("登录状态发生变化，请刷新二维码")
                    }
                }
                qrState.update{it.copy(expired=true,message="二维码已过期，请刷新")}
            } catch(e:CancellationException){throw e} catch(e:Exception){qrState.value=QrState(message=e.message?:"生成二维码失败",expired=true)}
        }
    }
    fun cancelLogin(){loginJob?.cancel();qrState.value=QrState()}
    fun logout()=action("已退出本机 B站登录") {
        cancelLogin();accountJob?.cancel();folderJob?.cancel()
        val map=tracks.value.associateBy{it.id}
        playback.state.value.queueIds.withIndex().filter{map[it.value]?.isLocal==false}.map{it.index}.reversed().forEach{playback.remove(it)}
        app.bilibili.logout();accountState.value=AccountState(account=BiliAccount())
    }
    fun updateSettings(value: AppSettings) = action { app.settings.update(value) }
    fun backup(uri: Uri, history: Boolean) = action("备份已保存；音频文件本体未包含") { app.backup.export(uri,history) }
    fun restore(uri: Uri, replace: Boolean) = action("数据已恢复；无法访问的音频请重新关联") { playback.stop(); app.backup.restore(uri,replace); app.settings.savePlayback(PlaybackSnapshot()) }
    fun sleep(minutes: Int) = action(if (minutes == 0) "睡眠定时器已取消" else "将在 $minutes 分钟后暂停") { playback.sleep(minutes) }
    override fun onCleared() { playback.close(); super.onCleared() }
}

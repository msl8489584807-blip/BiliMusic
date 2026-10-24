package com.bilimusic.player.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.*
import com.bilimusic.player.database.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class PlaybackState(val connected: Boolean = false, val trackId: String? = null, val playing: Boolean = false, val buffering: Boolean = false, val duration: Long = 0, val queueIds: List<String> = emptyList(), val repeat: Int = 0, val shuffle: Boolean = false, val error: String? = null)
@UnstableApi
class PlaybackConnection(context: Context, scope: CoroutineScope) {
    private val executor = ContextCompat.getMainExecutor(context)
    private val mutable = MutableStateFlow(PlaybackState())
    val state = mutable.asStateFlow()
    private val progress = MutableStateFlow(0L)
    val position = progress.asStateFlow()
    private var controller: MediaController? = null
    private val future = MediaController.Builder(context,SessionToken(context,ComponentName(context,PlaybackService::class.java))).buildAsync()
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { update() }
        override fun onPlayerError(error: PlaybackException) { mutable.update { it.copy(error=playbackError()) } }
    }
    init {
        future.addListener({
            try { controller = future.get().also { it.addListener(listener) }; update() }
            catch (e: Exception) { mutable.update { it.copy(error="无法连接播放服务，请重新打开应用") } }
        },ContextCompat.getMainExecutor(context))
        scope.launch { while (isActive) { controller?.let { progress.value = it.currentPosition.coerceAtLeast(0) }; delay(500) } }
    }
    private fun playbackError():String=if(controller?.currentMediaItem?.localConfiguration?.uri?.scheme=="bilimusic")"在线播放失败，请检查网络、登录状态或重新点击播放" else "无法播放，请检查文件权限、音频编码或重新关联文件"
    private fun update() { controller?.let { c -> mutable.value = PlaybackState(true,c.currentMediaItem?.mediaId,c.isPlaying,c.playbackState == Player.STATE_BUFFERING,c.duration.takeIf { it > 0 } ?: 0,(0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId },c.repeatMode,c.shuffleModeEnabled,c.playerError?.let { playbackError() }); progress.value = c.currentPosition.coerceAtLeast(0) } }
    fun play(tracks: List<Track>, selectedId: String, repeat: Int = 0, shuffle: Boolean = false) {
        val values = tracks.filter { (it.isLocal && it.uri != null && it.available)||(!it.isLocal&&it.bv!=null) }
        val index = values.indexOfFirst { it.id == selectedId }
        require(index >= 0) { "此曲目需要重新关联文件" }
        val c = controller ?: error("播放服务仍在连接，请稍后重试")
        c.repeatMode = repeat
        c.shuffleModeEnabled = shuffle
        c.setMediaItems(values.map { it.mediaItem() },index,0); c.prepare(); c.play()
    }
    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else { if (it.playbackState == Player.STATE_IDLE) it.prepare(); it.play() } } }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.let { if (it.currentPosition > 3000) it.seekTo(0) else it.seekToPreviousMediaItem() } }
    fun seek(position: Long) { controller?.seekTo(position.coerceAtLeast(0)) }
    fun shuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun repeat() { controller?.let { it.repeatMode = (it.repeatMode + 1) % 3 } }
    fun jump(index: Int) { controller?.let { it.seekTo(index,0); it.prepare(); it.play() } }
    fun remove(index: Int) { controller?.let { if (index in 0 until it.mediaItemCount) it.removeMediaItem(index) } }
    fun stop() { controller?.let { it.stop(); it.clearMediaItems() } }
    suspend fun sleep(minutes: Int) = suspendCancellableCoroutine<Unit> { continuation ->
        val result = controller?.sendCustomCommand(SessionCommand(PlaybackService.SLEEP,Bundle.EMPTY),Bundle().apply { putInt("minutes",minutes) })
        if (result == null) { continuation.resumeWith(Result.failure(IllegalStateException("播放服务仍在连接"))); return@suspendCancellableCoroutine }
        result.addListener({
            if (continuation.isActive) continuation.resumeWith(runCatching {
                check(result.get().resultCode == SessionResult.RESULT_SUCCESS) { "睡眠定时器设置失败，请重试" }
            })
        }, executor)
        continuation.invokeOnCancellation { result.cancel(false) }
    }
    fun close() { controller?.removeListener(listener); MediaController.releaseFuture(future) }
}

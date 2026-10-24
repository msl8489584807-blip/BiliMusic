package com.bilimusic.player.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import com.bilimusic.player.data.bilibili.BilibiliClient
import com.bilimusic.player.domain.OnlineRules
import androidx.media3.session.*
import com.bilimusic.player.MainActivity
import com.bilimusic.player.MusicApplication
import com.bilimusic.player.data.PlaybackSnapshot
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

@UnstableApi
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sleepJob: Job? = null
    private var saveJob: Job? = null
    private var lastRecordedId: String? = null
    private val app get() = application as MusicApplication
    override fun onCreate() {
        super.onCreate()
        val http=DefaultHttpDataSource.Factory().setUserAgent(BilibiliClient.USER_AGENT)
            .setDefaultRequestProperties(mapOf("Referer" to BilibiliClient.REFERER)).setConnectTimeoutMs(15000).setReadTimeoutMs(20000)
        val resolving=ResolvingDataSource.Factory(DefaultDataSource.Factory(this,http)) { spec ->
            if(spec.uri.scheme=="bilimusic") {
                val ref=OnlineRules.reference(spec.uri.toString())
                val url=runBlocking { app.bilibili.resolve(ref) }
                spec.withUri(android.net.Uri.parse(url))
            } else spec
        }
        player = ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(resolving)).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true)
            setHandleAudioBecomingNoisy(true)
            // ExoPlayer holds this only while actively playing, and releases it on pause.
            setWakeMode(C.WAKE_MODE_NETWORK)
        }
        val intent = PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this,player).setSessionActivity(intent).setCallback(object : MediaSession.Callback {
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                if (controller.uid != applicationInfo.uid && !controller.isTrusted) return MediaSession.ConnectionResult.reject()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(SessionCommand(SLEEP,Bundle.EMPTY)).build()).build()
            }
            override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, command: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                if (command.customAction != SLEEP) return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                val minutes = args.getInt("minutes")
                if (minutes !in 0..180) return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                sleepJob?.cancel()
                if (minutes > 0) sleepJob = scope.launch { delay(minutes * 60_000L); player.pause() }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }).build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                recordCurrentIfPlaying()
                persist()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { recordCurrentIfPlaying(); persist() }
            override fun onEvents(player: Player, events: Player.Events) {
                if(events.containsAny(Player.EVENT_POSITION_DISCONTINUITY,Player.EVENT_REPEAT_MODE_CHANGED,Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,Player.EVENT_TIMELINE_CHANGED)) persist()
            }
            override fun onPlayerError(error: PlaybackException) {
                player.currentMediaItem?.let { item -> scope.launch {
                    val track=app.repository.dao.track(item.mediaId)
                    if(track?.isLocal==true)app.repository.available(item.mediaId,false)
                    else track?.bv?.let{app.bilibili.invalidate(com.bilimusic.player.domain.BiliReference(it,track.page))}
                } }
                persist()
            }
        })
        scope.launch {
            val saved = app.settings.playback()
            val prefs = app.settings.settings.first()
            val tracks = withContext(Dispatchers.IO) { saved.ids.mapNotNull { app.repository.dao.track(it) }.filter { (it.isLocal && it.uri != null && it.available)||(!it.isLocal&&it.bv!=null) } }
            if (player.mediaItemCount == 0) {
                player.repeatMode = if (saved.ids.isEmpty()) prefs.repeat else saved.repeat
                player.shuffleModeEnabled = if (saved.ids.isEmpty()) prefs.shuffle else saved.shuffle
                if (tracks.isNotEmpty()) {
                    val previousId = saved.ids.getOrNull(saved.index)
                    val selected = tracks.indexOfFirst { it.id == previousId }.coerceAtLeast(0)
                    player.setMediaItems(tracks.map { it.mediaItem() },selected,saved.position.coerceAtLeast(0))
                    // Restoration does not autoplay or touch cloud providers until the user requests play.
                }
            }
        }
        scope.launch { while (isActive) { delay(5000); if (player.mediaItemCount > 0) persist() } }
    }
    private fun snapshot() = PlaybackSnapshot((0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId },player.currentMediaItemIndex.coerceAtLeast(0),player.currentPosition.coerceAtLeast(0),player.repeatMode,player.shuffleModeEnabled)
    private fun recordCurrentIfPlaying() {
        if(player.isPlaying) player.currentMediaItem?.mediaId?.let { id ->
            if(lastRecordedId != id) { lastRecordedId=id;scope.launch { app.repository.recordPlay(id) } }
        }
    }
    private fun persist() {
        val value = snapshot()
        saveJob?.cancel()
        saveJob = app.applicationScope.launch { app.settings.savePlayback(value) }
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onTaskRemoved(rootIntent: Intent?) { if (!player.playWhenReady || player.mediaItemCount == 0) { persist(); stopSelf() } }
    override fun onDestroy() {
        persist(); scope.cancel(); session?.release(); session = null; player.release(); super.onDestroy()
    }
    companion object { const val SLEEP = "com.bilimusic.player.SLEEP" }
}

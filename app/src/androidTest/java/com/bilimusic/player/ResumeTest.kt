package com.bilimusic.player

import android.content.ComponentName
import android.content.ContentValues
import android.content.Intent
import android.provider.MediaStore
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.bilimusic.player.data.PlaybackSnapshot
import com.bilimusic.player.domain.LibraryRules
import com.bilimusic.player.playback.PlaybackService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@UnstableApi
@SdkSuppress(minSdkVersion=29)
@RunWith(AndroidJUnit4::class)
class ResumeTest {
    @Test fun serviceRestoresQueueProgressAndModesWithoutAutoplay() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext as MusicApplication
        fun <T> main(block:()->T):T {var value:Result<T>?=null;instrumentation.runOnMainSync{value=runCatching(block)};return value!!.getOrThrow()}
        app.stopService(Intent(app,PlaybackService::class.java));Thread.sleep(500)
        val uris=(1..2).map { n->
            val uri=app.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,ContentValues().apply{put(MediaStore.Audio.Media.DISPLAY_NAME,"BiliMusic_resume_${System.currentTimeMillis()}_$n.wav");put(MediaStore.Audio.Media.MIME_TYPE,"audio/wav");put(MediaStore.Audio.Media.RELATIVE_PATH,"Music/BiliMusicTests");put(MediaStore.Audio.Media.IS_PENDING,1)})!!
            app.contentResolver.openOutputStream(uri)!!.use{it.write(TestAudio.tone(10))}
            app.contentResolver.update(uri,ContentValues().apply{put(MediaStore.Audio.Media.IS_PENDING,0)},null,null);uri
        }
        var scenario:ActivityScenario<MainActivity>?=null;var controller:MediaController?=null
        val ids=mutableListOf<String>()
        try {
            app.repository.importAudio(uris)
            uris.forEach{ids+=app.repository.dao.byIdentity(LibraryRules.localIdentity(it.toString()))!!.id}
            app.settings.savePlayback(PlaybackSnapshot(ids,1,5400,1,true))
            scenario=ActivityScenario.launch(MainActivity::class.java)
            val future=main{MediaController.Builder(app,SessionToken(app,ComponentName(app,PlaybackService::class.java))).buildAsync()}
            val c=future.get(15,TimeUnit.SECONDS);controller=c
            var restored=false
            repeat(50){if(!restored){Thread.sleep(100);restored=main{c.currentMediaItem?.mediaId==ids[1]}}}
            assertTrue("New service restores the selected track",restored)
            assertEquals(ids,main{(0 until c.mediaItemCount).map{c.getMediaItemAt(it).mediaId}})
            assertEquals(5400L,main{c.currentPosition});assertEquals(1,main{c.repeatMode});assertTrue(main{c.shuffleModeEnabled});assertFalse(main{c.playWhenReady})
            assertTrue("Restoration alone must not create listening history",app.repository.dao.history().none{it.trackId in ids})
            main{c.prepare();c.play()}
            var playing=false
            repeat(50){if(!playing){Thread.sleep(100);playing=main{c.isPlaying&&c.currentPosition>5400}}}
            assertTrue(playing)
            android.util.Log.i("BiliMusicTest","Service restart restored queue/index/5400ms/repeat/shuffle without autoplay")
        } finally {
            controller?.let{c->main{c.stop();c.clearMediaItems();c.release()}}
            scenario?.close();ids.forEach{app.repository.removeTrack(it)};uris.forEach{app.contentResolver.delete(it,null,null)}
        }
        Unit
    }
}

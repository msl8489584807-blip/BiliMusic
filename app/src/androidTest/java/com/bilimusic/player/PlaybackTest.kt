package com.bilimusic.player

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.bilimusic.player.domain.LibraryRules
import com.bilimusic.player.playback.PlaybackService
import com.bilimusic.player.playback.mediaItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

@UnstableApi
@SdkSuppress(minSdkVersion=29)
@RunWith(AndroidJUnit4::class)
class PlaybackTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun <T> onMain(block:()->T):T {
        var result:Result<T>?=null
        instrumentation.runOnMainSync{result=runCatching(block)}
        return result!!.getOrThrow()
    }
    @Test fun importsActualAudioAndPlaysThroughServiceInBackground() = runBlocking {
        val app=instrumentation.targetContext.applicationContext as MusicApplication
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        val values=ContentValues().apply{put(MediaStore.Audio.Media.DISPLAY_NAME,"BiliMusic_test_${System.currentTimeMillis()}.wav");put(MediaStore.Audio.Media.MIME_TYPE,"audio/wav");put(MediaStore.Audio.Media.RELATIVE_PATH,"Music/BiliMusicTests");put(MediaStore.Audio.Media.IS_PENDING,1)}
        val uri=app.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,values)!!
        var controller:MediaController?=null
        var trackId:String?=null
        try {
            app.contentResolver.openOutputStream(uri)!!.use{it.write(TestAudio.tone(30))}
            app.contentResolver.update(uri,ContentValues().apply{put(MediaStore.Audio.Media.IS_PENDING,0)},null,null)
            val report=app.repository.importAudio(listOf(uri));assertEquals(1,report.added)
            var track=app.repository.dao.byIdentity(LibraryRules.localIdentity(uri.toString()))!!;trackId=track.id
            assertTrue(track.durationMs>=29000)
            assertEquals(1,app.repository.importAudio(listOf(uri)).duplicates)
            val missing=track.copy(uri="content://bilimusic.invalid/deleted",identity="local:content://bilimusic.invalid/deleted",available=false)
            app.repository.dao.updateTrack(missing)
            assertTrue(runCatching{app.repository.relink(missing,android.net.Uri.parse("content://bilimusic.invalid/missing"))}.isFailure)
            app.repository.relink(missing,uri)
            track=app.repository.dao.track(track.id)!!
            assertTrue(track.available)
            val future=onMain{MediaController.Builder(app,SessionToken(app,ComponentName(app,PlaybackService::class.java))).buildAsync()}
            val c=future.get(15,TimeUnit.SECONDS);controller=c
            onMain{c.setMediaItems(listOf(track.mediaItem()));c.prepare();c.play()}
            var started=false
            repeat(100){if(!started){Thread.sleep(100);started=onMain{c.isPlaying&&c.currentPosition>250}}}
            assertTrue("Real WAV must play via MediaController",started)
            val screenshotDir=java.io.File(app.getExternalFilesDir(null),"screenshots").apply{mkdirs()}
            instrumentation.uiAutomation.takeScreenshot()?.let{bitmap->java.io.File(screenshotDir,"playing.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
            val notifications=app.getSystemService(android.app.NotificationManager::class.java).activeNotifications
            assertTrue("Media notification posted",notifications.any{it.notification.category==android.app.Notification.CATEGORY_TRANSPORT})
            val audio=app.getSystemService(android.media.AudioManager::class.java)
            val focus=android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM).build())
                .setOnAudioFocusChangeListener{}.build()
            assertEquals(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED,onMain{audio.requestAudioFocus(focus)})
            Thread.sleep(500)
            assertFalse("Transient audio focus loss pauses playback",onMain{c.isPlaying})
            onMain{audio.abandonAudioFocusRequest(focus)}
            var resumed=false
            repeat(40){if(!resumed){Thread.sleep(100);resumed=onMain{c.isPlaying}}}
            assertTrue("Playback resumes after transient focus is released",resumed)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            val before=onMain{c.currentPosition}
            Thread.sleep(1200)
            assertTrue("Playback continues with Activity stopped",onMain{c.isPlaying&&c.currentPosition>before+500})
            onMain{c.pause();c.seekTo(5000)}
            Thread.sleep(300)
            assertFalse(onMain{c.isPlaying})
            assertTrue(onMain{c.currentPosition}>=4900)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            scenario.recreate()
            assertEquals("Controller survives Activity recreation",track.id,onMain{c.currentMediaItem?.mediaId})
            // Pausing persists state without requiring Activity lifetime.
            Thread.sleep(700)
            val saved=app.settings.playback()
            assertEquals(track.id,saved.ids.getOrNull(saved.index))
            assertTrue("Seek position persisted",saved.position>=4900)
            val command=SessionCommand(PlaybackService.SLEEP,android.os.Bundle.EMPTY)
            val invalid=onMain{c.sendCustomCommand(command,android.os.Bundle().apply{putInt("minutes",181)})}.get(5,TimeUnit.SECONDS)
            assertNotEquals(SessionResult.RESULT_SUCCESS,invalid.resultCode)
            val timer=onMain{c.sendCustomCommand(command,android.os.Bundle().apply{putInt("minutes",1)})}.get(5,TimeUnit.SECONDS)
            assertEquals(SessionResult.RESULT_SUCCESS,timer.resultCode)
            onMain{c.repeatMode=Player.REPEAT_MODE_ONE;c.play()}
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            Thread.sleep(1500)
            assertTrue(onMain{c.isPlaying})
            // Observe a real service-owned timer while the Activity remains stopped.
            var timerPaused=false
            repeat(65){if(!timerPaused){Thread.sleep(1000);timerPaused=onMain{!c.isPlaying}}}
            assertTrue("Service sleep timer pauses without an Activity",timerPaused)
            val cancelled=onMain{c.sendCustomCommand(command,android.os.Bundle().apply{putInt("minutes",0)})}.get(5,TimeUnit.SECONDS)
            assertEquals(SessionResult.RESULT_SUCCESS,cancelled.resultCode)
            android.util.Log.i("BiliMusicTest","Real WAV import/play/background/pause/seek/recreate passed")
        } finally {
            controller?.let{c->onMain{c.stop();c.clearMediaItems();c.release()}}
            scenario.close()
            trackId?.let{app.repository.removeTrack(it)}
            app.contentResolver.delete(uri,null,null)
        }
        Unit
    }
}

object TestAudio {
    fun tone(seconds:Int):ByteArray {
        val sampleRate=22050;val samples=sampleRate*seconds;val dataSize=samples*2
        val b=ByteBuffer.allocate(44+dataSize).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray());b.putInt(36+dataSize);b.put("WAVEfmt ".toByteArray());b.putInt(16);b.putShort(1);b.putShort(1);b.putInt(sampleRate);b.putInt(sampleRate*2);b.putShort(2);b.putShort(16);b.put("data".toByteArray());b.putInt(dataSize)
        repeat(samples){i->b.putShort((kotlin.math.sin(i*2*Math.PI*440/sampleRate)*500).toInt().toShort())}
        return b.array()
    }
}

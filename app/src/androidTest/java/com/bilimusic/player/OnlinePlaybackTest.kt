package com.bilimusic.player

import android.content.ComponentName
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bilimusic.player.data.bilibili.*
import com.bilimusic.player.domain.*
import com.bilimusic.player.playback.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Live first-party network test; failure is reported, never substituted by fake results. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class OnlinePlaybackTest {
    @Test fun realSearchPlaysBiliAudioInsideMediaSession()=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext as MusicApplication
        fun <T> main(block:()->T):T{var r:Result<T>?=null;instrumentation.runOnMainSync{r=runCatching(block)};return r!!.getOrThrow()}
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        var controller:MediaController?=null;val created=mutableListOf<String>()
        try {
            val results=app.bilibili.search("钢琴",1)
            assertTrue("Real Bili search returns songs",results.songs.isNotEmpty())
            val second=app.bilibili.search("钢琴",2)
            assertEquals(2,second.page);assertTrue(second.songs.isNotEmpty())
            assertNotEquals(results.songs.map{it.bv},second.songs.map{it.bv})
            val song=results.songs.first();val ref=BiliReference(song.bv,song.page)
            val resolved=app.bilibili.resolve(ref)
            assertTrue(OnlineRules.isStreamUrl(resolved))
            val tracks=results.songs.take(2).map { value->
                val old=app.repository.dao.byIdentity(LibraryRules.biliIdentity(value.bv,value.page))
                app.repository.saveOnline(value).also{if(old==null)created+=it.id}
            }
            val future=main{MediaController.Builder(app,SessionToken(app,ComponentName(app,PlaybackService::class.java))).buildAsync()}
            val c=future.get(15,TimeUnit.SECONDS);controller=c
            main{c.setMediaItems(tracks.map{it.mediaItem()});c.prepare();c.play()}
            var playing=false;var error:String?=null
            repeat(400){if(!playing&&error==null){Thread.sleep(100);playing=main{c.isPlaying&&c.currentPosition>300};error=main{c.playerError?.errorCodeName}}}
            assertTrue("Native online playback: ${error?:"timeout"}",playing)
            assertEquals("bilimusic",main{c.currentMediaItem?.localConfiguration?.uri?.scheme})
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            val before=main{c.currentPosition};Thread.sleep(1300)
            assertTrue("Online audio continues in background",main{c.isPlaying&&c.currentPosition>before+500})
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            main{c.seekToNextMediaItem()}
            var nextPlaying=false
            repeat(300){if(!nextPlaying){Thread.sleep(100);nextPlaying=main{c.currentMediaItemIndex==1&&c.isPlaying&&c.currentPosition>300}}}
            assertTrue("The service resolves and plays the next online item",nextPlaying)
            main{c.seekTo(2000)};Thread.sleep(800)
            assertTrue("Online seeking advances to requested position",main{c.isPlaying&&c.currentPosition>=2000})
            android.util.Log.i("BiliMusicOnlineTest","Live search and page 2; native audio >300ms; background, next-item resolution and seek passed")
        } finally {controller?.let{c->main{c.stop();c.clearMediaItems();c.release()}};scenario.close();created.forEach{app.repository.removeTrack(it)}}
        Unit
    }
    @Test fun qrGenerationAndUnconfirmedPolling()=runBlocking {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MusicApplication
        val qr=app.bilibili.generateQr()
        assertTrue(java.net.URI(qr.url).host in setOf("passport.bilibili.com","account.bilibili.com"));assertTrue(qr.key.isNotBlank())
        assertEquals(86101,app.bilibili.pollQr(qr.key))
    }
    @Test fun cookiesAreEncryptedAndNeverSentToCdn() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val first=BilibiliSessionStore(context,"session_test","BiliMusicSessionTest")
        first.clear()
        val api=okhttp3.HttpUrl.Builder().scheme("https").host("api.bilibili.com").build()
        val cookie=okhttp3.Cookie.parse(api,"SESSDATA=not-a-real-account-secret; Domain=.bilibili.com; Path=/; Max-Age=300; Secure; HttpOnly")!!
        first.saveFromResponse(api,listOf(cookie))
        val stored=context.getSharedPreferences("session_test",0).getString("encrypted_cookies",null)!!
        assertFalse(stored.contains("not-a-real-account-secret"))
        val restored=BilibiliSessionStore(context,"session_test","BiliMusicSessionTest")
        assertEquals(cookie.value,restored.loadForRequest(api).single().value)
        assertTrue(restored.loadForRequest(okhttp3.HttpUrl.Builder().scheme("https").host("cdn.bilivideo.com").build()).isEmpty())
        restored.clear();assertTrue(restored.loadForRequest(api).isEmpty())
    }
}

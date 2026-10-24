package com.bilimusic.player

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bilimusic.player.data.bilibili.BilibiliSessionStore
import okhttp3.Cookie
import okhttp3.HttpUrl
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SessionStoreTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val api=HttpUrl.Builder().scheme("https").host("api.bilibili.com").build()
    private fun cookie()=Cookie.parse(api,"SESSDATA=test-only-cookie; Domain=.bilibili.com; Path=/; Max-Age=300; Secure; HttpOnly")!!

    @Test fun logoutDiscardsCookiesFromAnEarlierRequest() {
        val store=BilibiliSessionStore(context,"logout_race_test","BiliMusicLogoutTest")
        store.clear()
        val started=CountDownLatch(1);val released=CountDownLatch(1)
        val request=Thread {
            store.duringRequest {
                started.countDown()
                check(released.await(5,TimeUnit.SECONDS))
                store.saveFromResponse(api,listOf(cookie()))
            }
        }
        request.start()
        try {
            assertTrue(started.await(5,TimeUnit.SECONDS))
            store.clear();released.countDown();request.join(5000)
            assertFalse(request.isAlive)
            assertTrue("A late response must not log the user back in",store.loadForRequest(api).isEmpty())
            assertNull(context.getSharedPreferences("logout_race_test",0).getString("encrypted_cookies",null))
        } finally {released.countDown();request.join(5000);store.clear()}
    }

    @Test fun failedSessionWriteBecomesAnIoFailure() {
        val actual=context.getSharedPreferences("session_failure_test",0)
        actual.edit().clear().commit()
        val failing=object:ContextWrapper(context) {
            override fun getSharedPreferences(name:String,mode:Int):SharedPreferences = object:SharedPreferences by actual {
                override fun edit():SharedPreferences.Editor {
                    val editor=actual.edit()
                    return object:SharedPreferences.Editor by editor {
                        override fun putString(key:String,value:String?):SharedPreferences.Editor {editor.putString(key,value);return this}
                        override fun commit()=false
                    }
                }
            }
        }
        val store=BilibiliSessionStore(failing,"session_failure_test","BiliMusicFailureTest")
        val error=runCatching{store.saveFromResponse(api,listOf(cookie()))}.exceptionOrNull()
        assertTrue("OkHttp must receive an IOException rather than crash its worker",error is IOException)
        assertFalse(error!!.message.orEmpty().contains("test-only-cookie"))
        assertNull(actual.getString("encrypted_cookies",null))
    }
}

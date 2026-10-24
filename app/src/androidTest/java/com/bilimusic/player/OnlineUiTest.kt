package com.bilimusic.player

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.bilimusic.player.ui.MainViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class OnlineUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun searchAndListenWithoutLeavingBiliMusic()=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext as MusicApplication
        val vm=androidx.lifecycle.ViewModelProvider(compose.activity)[MainViewModel::class.java]
        val before=app.repository.dao.tracks().map{it.id}.toSet()
        try {
            compose.onNodeWithText("搜索音乐").performClick()
            compose.onNodeWithText("B站音乐").assertIsSelected()
            compose.onNodeWithText("搜索B站音乐、歌手或视频").performTextInput("钢琴")
            compose.onNodeWithText("搜索B站音乐、歌手或视频").performImeAction()
            compose.waitUntil(35000){vm.onlineSearch.value.data?.songs?.isNotEmpty()==true||vm.onlineSearch.value.error!=null}
            assertNull("Real UI search succeeds",vm.onlineSearch.value.error)
            val song=vm.onlineSearch.value.data!!.songs.first()
            compose.waitUntil(5000){compose.onAllNodesWithText(song.title).fetchSemanticsNodes().isNotEmpty()}
            compose.onAllNodesWithText(song.title).onFirst().assertIsDisplayed()
            val dir=java.io.File(app.getExternalFilesDir(null),"screenshots").apply{mkdirs()}
            fun shot(name:String){instrumentation.uiAutomation.takeScreenshot()?.let{b->java.io.File(dir,name).outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
            shot("online-search.png")
            compose.onAllNodesWithText(song.title).onFirst().performClick()
            compose.waitUntil(45000){vm.playback.state.value.playing||vm.playback.state.value.error!=null}
            assertTrue("Selected result plays natively",vm.playback.state.value.playing)
            assertEquals("No jump to Bilibili or browser",app.packageName,UiDevice.getInstance(instrumentation).currentPackageName)
            val mini=compose.onAllNodesWithText(song.title).fetchSemanticsNodes().maxBy{it.boundsInRoot.center.y}
            compose.onNode(SemanticsMatcher("mini player title"){it.id==mini.id}).performClick()
            compose.onNodeWithText("正在播放").assertIsDisplayed()
            compose.onNodeWithContentDescription("暂停").assertIsDisplayed()
            shot("online-player.png")
            compose.onNodeWithContentDescription("暂停").performClick()
            compose.waitUntil(3000){!vm.playback.state.value.playing}
            assertEquals(app.packageName,UiDevice.getInstance(instrumentation).currentPackageName)
        } finally {
            instrumentation.runOnMainSync{vm.playback.stop()}
            app.repository.dao.tracks().filter{it.id !in before}.forEach{app.repository.removeTrack(it.id)}
        }
        Unit
    }
    @Test fun accountShowsRealQrAndCancelsOnLeaving() {
        val vm=androidx.lifecycle.ViewModelProvider(compose.activity)[MainViewModel::class.java]
        compose.onNodeWithText("搜索音乐").performClick()
        compose.onNodeWithContentDescription("哔哩哔哩账号").performClick()
        compose.onNodeWithText("生成登录二维码").performClick()
        compose.waitUntil(30000){vm.qrState.value.qr!=null||vm.qrState.value.expired}
        assertNotNull("First-party login QR generated",vm.qrState.value.qr)
        compose.waitUntil(5000){compose.onAllNodesWithContentDescription("B站登录二维码").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("B站登录二维码").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.waitUntil(3000){vm.qrState.value.qr==null}
        assertNull(vm.qrState.value.qr)
    }
}

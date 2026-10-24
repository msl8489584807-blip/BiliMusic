package com.bilimusic.player

import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.bilimusic.player.domain.LibraryRules
import com.bilimusic.player.ui.MainViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@SdkSuppress(minSdkVersion=29)
@RunWith(AndroidJUnit4::class)
class PlayerUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun playerControlsQueueAndSynchronizedLyrics() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext as MusicApplication
        val name="播放器验证_${System.currentTimeMillis()}"
        val uri=app.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,ContentValues().apply{put(MediaStore.Audio.Media.DISPLAY_NAME,"$name.wav");put(MediaStore.Audio.Media.MIME_TYPE,"audio/wav");put(MediaStore.Audio.Media.RELATIVE_PATH,"Music/BiliMusicTests");put(MediaStore.Audio.Media.IS_PENDING,1)})!!
        var id:String?=null
        val vm=androidx.lifecycle.ViewModelProvider(compose.activity)[MainViewModel::class.java]
        try {
            app.contentResolver.openOutputStream(uri)!!.use{it.write(TestAudio.tone(10))}
            app.contentResolver.update(uri,ContentValues().apply{put(MediaStore.Audio.Media.IS_PENDING,0)},null,null)
            app.repository.importAudio(listOf(uri))
            val track=app.repository.dao.byIdentity(LibraryRules.localIdentity(uri.toString()))!!;id=track.id
            app.repository.dao.updateTrack(track.copy(lyricsText="[00:00.00]从这里开始\n[00:01.50]点击跳转的歌词"))
            compose.waitUntil(5000){compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()}
            compose.onAllNodesWithText(name).onFirst().performClick()
            compose.waitUntil(5000){compose.onAllNodesWithContentDescription("暂停").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithContentDescription("暂停").performClick()
            val mini=compose.onAllNodesWithText(name).fetchSemanticsNodes().maxBy{it.boundsInRoot.center.y}
            compose.onNode(SemanticsMatcher("lowest track title"){it.id==mini.id}).performClick()
            compose.onNodeWithText("正在播放").assertIsDisplayed()
            val dir=java.io.File(app.getExternalFilesDir(null),"screenshots").apply{mkdirs()}
            instrumentation.uiAutomation.takeScreenshot()?.let{b->java.io.File(dir,"player.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
            compose.onNodeWithText("歌词").performClick()
            compose.onNodeWithText("点击跳转的歌词").performScrollTo().performClick()
            compose.waitUntil(5000){vm.playback.position.value>=1400}
            compose.onNodeWithText("队列 · 1").performClick()
            compose.onNodeWithText("播放队列").assertIsDisplayed()
            compose.onNodeWithContentDescription("从队列移除 $name").performClick()
            compose.waitUntil(5000){vm.playback.state.value.queueIds.isEmpty()}
        } finally {
            instrumentation.runOnMainSync{vm.playback.stop()}
            id?.let{app.repository.removeTrack(it)};app.contentResolver.delete(uri,null,null)
        }
        Unit
    }
}

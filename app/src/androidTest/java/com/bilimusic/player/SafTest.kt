package com.bilimusic.player

import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@SdkSuppress(minSdkVersion=29)
@RunWith(AndroidJUnit4::class)
class SafTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun systemPickerImportsAndPersistsDocumentPermission() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext as MusicApplication
        val device=UiDevice.getInstance(instrumentation)
        val name="BiliMusic_SAF_${System.currentTimeMillis()}.wav"
        val uri=app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,ContentValues().apply{
            put(MediaStore.Downloads.DISPLAY_NAME,name);put(MediaStore.Downloads.MIME_TYPE,"audio/wav");put(MediaStore.Downloads.RELATIVE_PATH,"Download");put(MediaStore.Downloads.IS_PENDING,1)
        })!!
        var trackId:String?=null
        var documentUri:android.net.Uri?=null
        try {
            app.contentResolver.openOutputStream(uri)!!.use{it.write(TestAudio.tone(2))}
            app.contentResolver.update(uri,ContentValues().apply{put(MediaStore.Downloads.IS_PENDING,0)},null,null)
            compose.onNodeWithText("导入音乐").performClick()
            var document=device.wait(Until.findObject(By.text(name)),5000)
            if(document==null) {
                val roots=device.findObject(UiSelector().description("Show roots"))
                if(roots.waitForExists(3000))roots.click()
                val downloads=device.findObject(UiSelector().text("Downloads"))
                if(downloads.waitForExists(3000))downloads.click()
                document=device.wait(Until.findObject(By.text(name)),5000)
            }
            assertNotNull("Real document visible in Android file picker",document)
            device.waitForIdle()
            device.findObject(UiSelector().text(name)).click()
            val allow=UiSelector().resourceId("com.android.permissioncontroller:id/permission_allow_button")
            if(device.findObject(allow).waitForExists(2500))device.findObject(allow).click()
            compose.waitUntil(10000){runBlocking{app.repository.dao.tracks().any{it.title.startsWith("BiliMusic_SAF_")}}}
            val track=app.repository.dao.tracks().first{it.title.startsWith("BiliMusic_SAF_")}
            trackId=track.id;documentUri=android.net.Uri.parse(track.uri)
            assertTrue(track.isLocal&&track.durationMs>=1900)
            assertTrue("SAF permission persists beyond Activity",app.contentResolver.persistedUriPermissions.any{it.uri==documentUri&&it.isReadPermission})
            compose.activityRule.scenario.recreate()
            app.contentResolver.openAssetFileDescriptor(documentUri!!,"r")!!.use{assertTrue(it.length>0)}
            android.util.Log.i("BiliMusicTest","Real ACTION_OPEN_DOCUMENT import and persistent URI permission passed")
        } finally {
            trackId?.let{app.repository.removeTrack(it)}
            documentUri?.let{app.contentResolver.releasePersistableUriPermission(it,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)}
            app.contentResolver.delete(uri,null,null)
        }
        Unit
    }
}

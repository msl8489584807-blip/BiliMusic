package com.bilimusic.player

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class UiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun navigationSearchAndTheme() {
        compose.onNodeWithText("BiliMusic").assertIsDisplayed()
        compose.onNodeWithText("搜索",useUnmergedTree=true).performClick()
        compose.onNodeWithText("本地音乐").performClick()
        compose.onNodeWithText("曲名、艺术家、专辑、标签或歌单").performTextInput("no-such-track")
        compose.waitUntil(5000){compose.onAllNodesWithText("没有找到结果").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("音乐库",useUnmergedTree=true).performClick()
        compose.onNodeWithText("歌单",useUnmergedTree=true).performClick()
        compose.onNodeWithText("新建歌单").performClick()
        compose.onNodeWithText("歌单名称").performTextInput("集成测试歌单")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(5000){compose.onAllNodesWithText("集成测试歌单").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("设置",useUnmergedTree=true).performClick()
        compose.onNodeWithText("浅色").performClick()
        compose.onNodeWithText("跟随系统").performClick()
        compose.onNodeWithText("深色").performClick()
        compose.onNodeWithText("Material 动态配色").assertIsDisplayed()
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val dir=java.io.File(context.getExternalFilesDir(null),"screenshots").apply{mkdirs()}
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let{bitmap->java.io.File(dir,"settings.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    }
}

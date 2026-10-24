package com.bilimusic.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.media3.common.util.UnstableApi
import com.bilimusic.player.ui.MainScreen
import com.bilimusic.player.ui.MainViewModel
import com.bilimusic.player.ui.theme.BiliMusicTheme

@UnstableApi
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            BiliMusicTheme(settings) { MainScreen(vm) }
        }
    }
}

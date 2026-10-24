package com.bilimusic.player

import android.app.Application
import androidx.room.Room
import com.bilimusic.player.database.MusicDatabase
import com.bilimusic.player.data.*

class MusicApplication : Application() {
    val bilibili by lazy { com.bilimusic.player.data.bilibili.BilibiliClient(this) }
    val applicationScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    val database by lazy { Room.databaseBuilder(this,MusicDatabase::class.java,"bilimusic.db").build() }
    val repository by lazy { LibraryRepository(this,database) }
    val settings by lazy { SettingsStore(this) }
    val backup by lazy { BackupManager(this,database,settings) }
}

package com.bilimusic.player.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
import java.io.IOException

private val Context.musicPreferences by preferencesDataStore(name = "music_settings")
data class AppSettings(val theme: String = "dark", val dynamicColor: Boolean = false, val repeat: Int = 0, val shuffle: Boolean = false)
data class PlaybackSnapshot(val ids: List<String> = emptyList(), val index: Int = 0, val position: Long = 0, val repeat: Int = 0, val shuffle: Boolean = false)
class SettingsStore(context: Context) {
    private val store = context.musicPreferences
    private val theme = stringPreferencesKey("theme")
    private val dynamic = booleanPreferencesKey("dynamic")
    private val repeat = intPreferencesKey("repeat")
    private val shuffle = booleanPreferencesKey("shuffle")
    private val queue = stringPreferencesKey("queue")
    private val index = intPreferencesKey("index")
    private val position = longPreferencesKey("position")
    private val savedRepeat = intPreferencesKey("saved_repeat")
    private val savedShuffle = booleanPreferencesKey("saved_shuffle")
    private val data = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
    val settings: Flow<AppSettings> = data.map { AppSettings(it[theme] ?: "dark", it[dynamic] ?: false, it[repeat] ?: 0, it[shuffle] ?: false) }
    suspend fun update(value: AppSettings) {
        require(value.theme in setOf("dark", "light", "system") && value.repeat in 0..2)
        store.edit { it[theme] = value.theme; it[dynamic] = value.dynamicColor; it[repeat] = value.repeat; it[shuffle] = value.shuffle }
    }
    suspend fun savePlayback(value: PlaybackSnapshot) { store.edit {
        it[queue] = value.ids.joinToString("\n"); it[index] = value.index; it[position] = value.position
        it[savedRepeat] = value.repeat; it[savedShuffle] = value.shuffle
    } }
    suspend fun playback(): PlaybackSnapshot = data.first().let { PlaybackSnapshot(it[queue]?.split('\n')?.filter(String::isNotBlank).orEmpty(), it[index] ?: 0, it[position] ?: 0, it[savedRepeat] ?: 0, it[savedShuffle] ?: false) }
}

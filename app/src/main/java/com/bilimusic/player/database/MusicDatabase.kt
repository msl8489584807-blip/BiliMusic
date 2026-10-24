package com.bilimusic.player.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Track::class, Playlist::class, PlaylistTrack::class, PlayHistory::class, Tag::class, TrackTag::class], version = 1, exportSchema = true)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao
    // v1 is the initial schema; future versions must supply explicit Migration objects.
}

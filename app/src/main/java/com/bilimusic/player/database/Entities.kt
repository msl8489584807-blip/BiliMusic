package com.bilimusic.player.database

import androidx.room.*
import java.util.UUID

object TrackType { const val LOCAL = "LOCAL"; const val BILIBILI_REFERENCE = "BILIBILI_REFERENCE" }
@Entity(tableName = "tracks", indices = [Index(value = ["identity"], unique = true), Index("title"), Index("artist"), Index("album"), Index("addedAt")])
data class Track(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val identity: String,
    val type: String = TrackType.LOCAL,
    val uri: String? = null,
    val title: String,
    val artist: String = "未知艺术家",
    val album: String = "未知专辑",
    val durationMs: Long = 0,
    val artwork: String? = null,
    val bv: String? = null,
    val page: Int = 1,
    val category: String = "其他",
    val favorite: Boolean = false,
    val available: Boolean = true,
    val lyricsUri: String? = null,
    val lyricsText: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) { val isLocal get() = type == TrackType.LOCAL }

@Entity(tableName = "playlists", indices = [Index("name")])
data class Playlist(@PrimaryKey val id: String = UUID.randomUUID().toString(), val name: String, val favorite: Boolean = false, val updatedAt: Long = System.currentTimeMillis())
@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [ForeignKey(entity = Playlist::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE), ForeignKey(entity = Track::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("trackId"), Index(value = ["playlistId", "position"], unique = true)])
data class PlaylistTrack(val playlistId: String, val trackId: String, val position: Int)
@Entity(tableName = "play_history", foreignKeys = [ForeignKey(entity = Track::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE)], indices = [Index("trackId"), Index("playedAt")])
data class PlayHistory(@PrimaryKey(autoGenerate = true) val id: Long = 0, val trackId: String, val playedAt: Long = System.currentTimeMillis())
@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class Tag(@PrimaryKey val id: String = UUID.randomUUID().toString(), val name: String)
@Entity(tableName = "track_tags", primaryKeys = ["trackId", "tagId"], foreignKeys = [ForeignKey(entity = Track::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE), ForeignKey(entity = Tag::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)], indices = [Index("tagId")])
data class TrackTag(val trackId: String, val tagId: String)

package com.bilimusic.player.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Query("SELECT * FROM tracks ORDER BY addedAt DESC") fun observeTracks(): Flow<List<Track>>
    @Query("SELECT * FROM playlists ORDER BY favorite DESC, updatedAt DESC") fun observePlaylists(): Flow<List<Playlist>>
    @Query("SELECT * FROM playlists WHERE name LIKE :pattern ESCAPE '!' ORDER BY name LIMIT 100") fun searchPlaylists(pattern: String): Flow<List<Playlist>>
    @Query("SELECT * FROM playlist_tracks ORDER BY position") fun observeEntries(): Flow<List<PlaylistTrack>>
    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT 200") fun observeHistory(): Flow<List<PlayHistory>>
    @Query("SELECT * FROM tags ORDER BY name") fun observeTags(): Flow<List<Tag>>
    @Query("SELECT * FROM track_tags") fun observeTrackTags(): Flow<List<TrackTag>>
    @Query("SELECT * FROM tracks WHERE title LIKE :pattern ESCAPE '!' OR artist LIKE :pattern ESCAPE '!' OR album LIKE :pattern ESCAPE '!' OR category LIKE :pattern ESCAPE '!' OR bv LIKE :pattern ESCAPE '!' OR EXISTS (SELECT 1 FROM track_tags JOIN tags ON tags.id=track_tags.tagId WHERE track_tags.trackId=tracks.id AND tags.name LIKE :pattern ESCAPE '!') ORDER BY title LIMIT 300") fun search(pattern: String): Flow<List<Track>>
    @Query("SELECT * FROM tracks") suspend fun tracks(): List<Track>
    @Query("SELECT * FROM tracks WHERE id=:id") suspend fun track(id: String): Track?
    @Query("SELECT * FROM tracks WHERE identity=:identity") suspend fun byIdentity(identity: String): Track?
    @Query("SELECT * FROM playlists") suspend fun playlists(): List<Playlist>
    @Query("SELECT * FROM playlist_tracks ORDER BY position") suspend fun entries(): List<PlaylistTrack>
    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT 10000") suspend fun history(): List<PlayHistory>
    @Query("SELECT * FROM tags") suspend fun tags(): List<Tag>
    @Query("SELECT * FROM track_tags") suspend fun trackTags(): List<TrackTag>
    @Query("SELECT tags.* FROM tags JOIN track_tags ON tags.id=track_tags.tagId WHERE trackId=:id") suspend fun tagsFor(id: String): List<Tag>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTrack(track: Track): Long
    @Update suspend fun updateTrack(track: Track)
    @Upsert suspend fun savePlaylist(playlist: Playlist)
    @Insert suspend fun insertEntries(entries: List<PlaylistTrack>)
    @Insert suspend fun insertHistory(history: PlayHistory)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTag(tag: Tag)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTrackTags(tags: List<TrackTag>)
    @Query("DELETE FROM playlist_tracks WHERE playlistId=:id") suspend fun clearEntries(id: String)
    @Query("DELETE FROM playlists WHERE id=:id") suspend fun deletePlaylist(id: String)
    @Query("DELETE FROM tracks WHERE id=:id") suspend fun deleteTrack(id: String)
    @Query("DELETE FROM track_tags WHERE trackId=:id") suspend fun clearTrackTags(id: String)
    @Query("DELETE FROM play_history") suspend fun clearHistory()
    @Query("DELETE FROM tracks") suspend fun clearTracks()
    @Query("DELETE FROM playlists") suspend fun clearPlaylists()
    @Query("DELETE FROM tags") suspend fun clearTags()
    @Query("DELETE FROM play_history WHERE id NOT IN (SELECT id FROM play_history ORDER BY playedAt DESC LIMIT 10000)") suspend fun trimHistory()
}

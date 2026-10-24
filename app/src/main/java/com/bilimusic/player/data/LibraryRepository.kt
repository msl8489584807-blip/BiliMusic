package com.bilimusic.player.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.bilimusic.player.database.*
import com.bilimusic.player.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

data class ImportReport(val added: Int, val duplicates: Int, val failures: List<String>)
class LibraryRepository(private val context: Context, val database: MusicDatabase) {
    suspend fun saveOnline(song:com.bilimusic.player.data.bilibili.OnlineSong):Track=database.withTransaction {
        val identity=LibraryRules.biliIdentity(song.bv,song.page)
        val old=dao.byIdentity(identity)
        val value=(old?:Track(identity=identity,type=TrackType.BILIBILI_REFERENCE,title=song.title,artist=song.artist.ifBlank{"未知 UP 主"},bv=song.bv,page=song.page)).copy(durationMs=old?.durationMs?.takeIf{it>0}?:song.durationMs,artwork=old?.artwork?:song.cover,available=true,updatedAt=System.currentTimeMillis())
        if(old==null)dao.insertTrack(value) else dao.updateTrack(value)
        dao.byIdentity(identity)!!
    }
    val dao = database.library()
    val tracks = dao.observeTracks()
    val playlists = dao.observePlaylists()
    val entries = dao.observeEntries()
    val history = dao.observeHistory()
    val tags = dao.observeTags()
    val trackTags = dao.observeTrackTags()
    fun search(query: String) = dao.search(LibraryRules.searchPattern(query))

    suspend fun importAudio(uris: List<Uri>): ImportReport = withContext(Dispatchers.IO) {
        var added = 0; var duplicates = 0; val failures = mutableListOf<String>()
        for (uri in uris.distinct()) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            try {
                require(uri.scheme == "content") { "请选择系统文件提供程序中的音频" }
                val identity = LibraryRules.localIdentity(uri.toString())
                if (dao.byIdentity(identity) != null) { duplicates++; continue }
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { } ?: error("文件不可访问")
                val filename = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "未命名音频"
                val id = java.util.UUID.randomUUID().toString()
                var title = filename.substringBeforeLast('.', filename); var artist = "未知艺术家"; var album = "未知专辑"; var duration = 0L; var artwork: String? = null
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf(String::isNotBlank) ?: title
                    artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf(String::isNotBlank) ?: artist
                    album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf(String::isNotBlank) ?: album
                    duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                    retriever.embeddedPicture?.takeIf { it.size < 8_000_000 }?.let { bytes ->
                        val dir = File(context.filesDir, "artwork").apply { mkdirs() }
                        artwork = File(dir, "$id.jpg").apply { writeBytes(bytes) }.absolutePath
                    }
                } catch (e: RuntimeException) {
                    android.util.Log.w("AudioImport", "Metadata unavailable: ${e.javaClass.simpleName}")
                } finally { retriever.release() }
                val inserted = dao.insertTrack(Track(id = id, identity = identity, uri = uri.toString(), title = title, artist = artist, album = album, durationMs = duration, artwork = artwork))
                if (inserted == -1L) duplicates++ else added++
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) { failures += (e.message ?: "文件读取失败").take(160) }
        }
        ImportReport(added, duplicates, failures)
    }
    suspend fun saveReference(input: String, title: String, artist: String, category: String, tags: String, existing: Track? = null) {
        val reference = BiliParser.parse(input)
        require(title.isNotBlank()) { "请填写标题" }
        val identity = LibraryRules.biliIdentity(reference.bv, reference.page)
        database.withTransaction {
            require(dao.byIdentity(identity)?.id.let { it == null || it == existing?.id }) { "此 BV 与分 P 已收藏" }
            val track = (existing ?: Track(identity = identity, type = TrackType.BILIBILI_REFERENCE, title = title.trim())).copy(identity = identity, bv = reference.bv, page = reference.page, title = title.trim(), artist = artist.trim().ifBlank { "未知艺术家" }, category = category, updatedAt = System.currentTimeMillis())
            if (existing == null) dao.insertTrack(track) else dao.updateTrack(track)
            setTags(track.id, tags)
        }
    }
    suspend fun setTags(id: String, input: String) = database.withTransaction {
        dao.clearTrackTags(id)
        val names = input.split(',', '，', '#').map(String::trim).filter(String::isNotBlank).distinct().take(30)
        require(names.all { it.length <= 60 }) { "标签不能超过 60 字" }
        names.forEach { name -> dao.insertTag(Tag(name = name)) }
        val tagIds = dao.tags().filter { it.name in names }.map { TrackTag(id, it.id) }
        dao.insertTrackTags(tagIds)
    }
    suspend fun favorite(track: Track) { dao.updateTrack(track.copy(favorite = !track.favorite, updatedAt = System.currentTimeMillis())) }
    suspend fun available(id: String, available: Boolean) { dao.track(id)?.let { dao.updateTrack(it.copy(available = available)) } }
    suspend fun createPlaylist(name: String) { require(name.isNotBlank()) { "请填写歌单名称" }; dao.savePlaylist(Playlist(name = name.trim().take(100))) }
    suspend fun savePlaylist(value: Playlist) { require(value.name.isNotBlank() && value.name.length<=100) { "歌单名称应为 1–100 字" }; dao.savePlaylist(value.copy(updatedAt = System.currentTimeMillis())) }
    suspend fun addToPlaylist(playlistId: String, trackId: String) = database.withTransaction {
        val current = dao.entries().filter { it.playlistId == playlistId }.sortedBy { it.position }.map { it.trackId }
        reorder(playlistId, (current + trackId).distinct())
    }
    suspend fun removeFromPlaylist(playlistId: String, trackId: String) = database.withTransaction {
        reorder(playlistId, dao.entries().filter { it.playlistId == playlistId && it.trackId != trackId }.sortedBy { it.position }.map { it.trackId })
    }
    suspend fun reorder(playlistId: String, ids: List<String>) = database.withTransaction {
        require(ids.distinct().size == ids.size)
        dao.clearEntries(playlistId)
        dao.insertEntries(ids.mapIndexed { index, id -> PlaylistTrack(playlistId, id, index) })
    }
    suspend fun recordPlay(id: String) = database.withTransaction { if (dao.track(id) != null) { dao.insertHistory(PlayHistory(trackId = id)); dao.trimHistory() } }
    suspend fun removeTrack(id: String) = database.withTransaction {
        val affected = dao.entries().filter { it.trackId == id }.map { it.playlistId }.distinct()
        dao.deleteTrack(id)
        affected.forEach { playlistId -> reorder(playlistId,dao.entries().filter{it.playlistId==playlistId}.sortedBy{it.position}.map{it.trackId}) }
    }
    suspend fun associateLyrics(track: Track, uri: Uri) = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBounded(2_000_000) } ?: error("无法读取歌词")
        require(bytes.size <= 2_000_000) { "歌词文件超过 2 MB" }
        val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        require(LrcParser.parse(text).isNotEmpty()) { "未找到有效 LRC 时间戳，请选择 UTF-8 编码的歌词" }
        dao.updateTrack(track.copy(lyricsUri = uri.toString(), lyricsText = text, updatedAt = System.currentTimeMillis()))
    }
    suspend fun relink(track: Track, uri: Uri) = withContext(Dispatchers.IO) {
        require(track.isLocal && uri.scheme == "content")
        context.contentResolver.openAssetFileDescriptor(uri,"r")?.use { } ?: error("文件不可访问")
        val identity = LibraryRules.localIdentity(uri.toString())
        require(dao.byIdentity(identity)?.id.let { it == null || it == track.id }) { "此文件已关联另一首曲目" }
        dao.updateTrack(track.copy(uri = uri.toString(), identity = identity, available = true, updatedAt = System.currentTimeMillis()))
    }
}

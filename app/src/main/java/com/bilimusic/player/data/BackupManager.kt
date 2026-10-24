package com.bilimusic.player.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.bilimusic.player.database.*
import com.bilimusic.player.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class BackupManager(private val context: Context, private val db: MusicDatabase, private val settings: SettingsStore) {
    private val dao get() = db.library()
    suspend fun export(uri: Uri, includeHistory: Boolean) = withContext(Dispatchers.IO) {
        val text = exportText(includeHistory)
        context.contentResolver.openOutputStream(uri,"wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("无法写入备份文件")
    }
    suspend fun restore(uri: Uri, replace: Boolean) = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBounded(MAX_BYTES).toString(Charsets.UTF_8) } ?: error("无法读取备份")
        restoreText(text, replace)
    }
    suspend fun exportText(includeHistory: Boolean): String = db.withTransaction {
        val prefs = settings.settings.first()
        JSONObject().put("schemaVersion",1).put("app","BiliMusic").put("createdAt",System.currentTimeMillis())
            .put("settings",JSONObject().put("theme",prefs.theme).put("dynamicColor",prefs.dynamicColor).put("repeat",prefs.repeat).put("shuffle",prefs.shuffle))
            .put("tracks",jsonArray(dao.tracks().map { t -> JSONObject().put("id",t.id).put("type",t.type).put("uri",t.uri).put("title",t.title).put("artist",t.artist).put("album",t.album).put("durationMs",t.durationMs).put("bv",t.bv).put("page",t.page).put("category",t.category).put("favorite",t.favorite).put("lyricsUri",t.lyricsUri).put("lyricsText",t.lyricsText).put("addedAt",t.addedAt).put("updatedAt",t.updatedAt) }))
            .put("playlists",jsonArray(dao.playlists().map { JSONObject().put("id",it.id).put("name",it.name).put("favorite",it.favorite) }))
            .put("entries",jsonArray(dao.entries().map { JSONObject().put("playlistId",it.playlistId).put("trackId",it.trackId).put("position",it.position) }))
            .put("tags",jsonArray(dao.tags().map { JSONObject().put("id",it.id).put("name",it.name) }))
            .put("trackTags",jsonArray(dao.trackTags().map { JSONObject().put("trackId",it.trackId).put("tagId",it.tagId) }))
            .put("history",jsonArray(if (includeHistory) dao.history().map { JSONObject().put("trackId",it.trackId).put("playedAt",it.playedAt) } else emptyList()))
            .also(::validateArrayLimits).toString(2).also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "备份超过 64 MB，无法导出可恢复的文件" } }
    }
    suspend fun restoreText(text: String, replace: Boolean) = withContext(Dispatchers.IO) {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "备份超过 64 MB" }
        val root = JSONObject(text)
        validateArrayLimits(root)
        require(root.getInt("schemaVersion") == 1 && root.getString("app") == "BiliMusic") { "不支持的备份格式或版本" }
        fun array(name: String): List<JSONObject> { val a = root.getJSONArray(name); return (0 until a.length()).map { a.getJSONObject(it) } }
        fun JSONObject.str(key: String, fallback: String = ""): String = optString(key,fallback).also { require(it.length <= 2000000) { "备份字段过长" } }
        fun JSONObject.nullable(key: String): String? = if (isNull(key) || !has(key)) null else str(key)
        fun id(value: String): String = value.also { require(it.isNotBlank() && it.length <= 128) { "备份 ID 无效" } }
        val tracks = array("tracks").map { j ->
            val type = j.str("type"); require(type in setOf(TrackType.LOCAL,TrackType.BILIBILI_REFERENCE))
            val uri = j.nullable("uri")
            if (type == TrackType.LOCAL) require(uri != null && Uri.parse(uri).scheme == "content") { "本地文件 URI 无效" }
            val ref = if (type == TrackType.BILIBILI_REFERENCE) BiliParser.parse("https://www.bilibili.com/video/${j.str("bv")}?p=${j.getInt("page")}") else null
            val accessible = if (uri == null) true else try { context.contentResolver.openAssetFileDescriptor(Uri.parse(uri),"r")?.use { true } ?: false } catch (_: Exception) { false }
            val title = j.str("title"); require(title.isNotBlank()) { "曲目标题为空" }
            val duration = j.optLong("durationMs"); require(duration >= 0)
            Track(id(j.str("id")), if (ref != null) LibraryRules.biliIdentity(ref.bv,ref.page) else LibraryRules.localIdentity(uri!!), type, uri, title, j.str("artist"),j.str("album"),duration, null,ref?.bv,ref?.page ?: 1,j.str("category","其他"),j.optBoolean("favorite"),accessible,j.nullable("lyricsUri")?.takeIf { Uri.parse(it).scheme == "content" },j.nullable("lyricsText"),j.optLong("addedAt"),j.optLong("updatedAt"))
        }
        require(tracks.map { it.id }.distinct().size == tracks.size && tracks.map { it.identity }.distinct().size == tracks.size) { "备份含重复曲目" }
        val playlists = array("playlists").map { Playlist(id(it.str("id")), it.str("name").also { n -> require(n.isNotBlank() && n.length <= 100) },it.optBoolean("favorite")) }
        val tags = array("tags").map { Tag(id(it.str("id")),it.str("name").also { n -> require(n.isNotBlank() && n.length <= 60) }) }
        require(playlists.map { it.id }.distinct().size == playlists.size && tags.map { it.id }.distinct().size == tags.size && tags.map { it.name }.distinct().size == tags.size)
        val trackIds = tracks.map { it.id }.toSet(); val playlistIds = playlists.map { it.id }.toSet(); val tagIds = tags.map { it.id }.toSet()
        val entries = array("entries").map { PlaylistTrack(it.str("playlistId"),it.str("trackId"),it.getInt("position")) }
        require(entries.all { it.trackId in trackIds && it.playlistId in playlistIds && it.position >= 0 }) { "歌单引用不完整" }
        require(entries.map { it.playlistId to it.trackId }.distinct().size == entries.size && entries.map { it.playlistId to it.position }.distinct().size == entries.size) { "歌单顺序重复" }
        val trackTags = array("trackTags").map { TrackTag(it.str("trackId"),it.str("tagId")) }
        require(trackTags.all { it.trackId in trackIds && it.tagId in tagIds }) { "标签引用不完整" }
        val history = array("history").map { PlayHistory(trackId=it.str("trackId"),playedAt=it.getLong("playedAt")) }
        require(history.all { it.trackId in trackIds }) { "历史引用不完整" }
        val s = root.getJSONObject("settings")
        val newSettings = AppSettings(s.str("theme"),s.getBoolean("dynamicColor"),s.getInt("repeat"),s.getBoolean("shuffle"))
        require(newSettings.theme in setOf("dark","light","system") && newSettings.repeat in 0..2)
        val oldSettings = settings.settings.first()
        try {
            db.withTransaction {
                if (replace) { dao.clearHistory(); dao.clearPlaylists(); dao.clearTracks(); dao.clearTags() }
                val mapping = mutableMapOf<String,String>()
                tracks.forEach { incoming ->
                    val existing = dao.byIdentity(incoming.identity)
                    val value = if (existing != null) incoming.copy(id=existing.id, favorite=incoming.favorite || existing.favorite) else incoming.copy(id=java.util.UUID.randomUUID().toString())
                    if (existing == null) dao.insertTrack(value) else dao.updateTrack(value)
                    mapping[incoming.id] = value.id
                }
                val tagMapping = mutableMapOf<String,String>()
                val existingTags = dao.tags().associateBy{it.name}.toMutableMap()
                tags.forEach { tag ->
                    val value = existingTags[tag.name] ?: Tag(name=tag.name).also { dao.insertTag(it);existingTags[it.name]=it }
                    tagMapping[tag.id] = value.id
                }
                dao.insertTrackTags(trackTags.map { TrackTag(mapping.getValue(it.trackId),tagMapping.getValue(it.tagId)) })
                val existingPlaylists = dao.playlists().associateBy{it.id}
                val existingEntries = dao.entries().groupBy{it.playlistId}
                playlists.forEach { p ->
                    val same = if (!replace) existingPlaylists[p.id] else null
                    val playlist = same ?: p
                    dao.savePlaylist(playlist)
                    val old = existingEntries[playlist.id].orEmpty().sortedBy { it.position }.map { it.trackId }
                    val ids = (old + entries.filter { it.playlistId == p.id }.sortedBy { it.position }.map { mapping.getValue(it.trackId) }).distinct()
                    dao.clearEntries(playlist.id); dao.insertEntries(ids.mapIndexed { i,t -> PlaylistTrack(playlist.id,t,i) })
                }
                history.forEach { dao.insertHistory(it.copy(id=0,trackId=mapping.getValue(it.trackId))) }; dao.trimHistory()
                settings.update(newSettings)
            }
        } catch (e: Exception) { withContext(NonCancellable) { settings.update(oldSettings) }; throw e }
    }
    private fun jsonArray(items: List<JSONObject>) = JSONArray().apply { items.forEach { put(it) } }
    private fun validateArrayLimits(root: JSONObject) {
        listOf("tracks","playlists","tags","entries","trackTags","history").forEach { name ->
            val limit = if(name == "entries" || name == "trackTags") 500000 else 20000
            require(root.getJSONArray(name).length() <= limit) { "备份的 $name 记录超过 $limit 条" }
        }
    }
    companion object { const val MAX_BYTES = 64_000_000 }
}

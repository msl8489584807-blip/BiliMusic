package com.bilimusic.player

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import androidx.room.withTransaction
import com.bilimusic.player.database.*
import com.bilimusic.player.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: MusicDatabase
    @Before fun create() { db = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java).build() }
    @After fun close() { db.close() }
    @Test fun playlistOrderAndForeignKeys() = runBlocking {
        val repo = LibraryRepository(context, db)
        val a = Track(identity="local:content://test/a",uri="content://test/a",title="A")
        val b = Track(identity="local:content://test/b",uri="content://test/b",title="B")
        db.library().insertTrack(a); db.library().insertTrack(b)
        val playlist = Playlist(name="Test"); db.library().savePlaylist(playlist)
        repo.addToPlaylist(playlist.id,a.id); repo.addToPlaylist(playlist.id,b.id); repo.addToPlaylist(playlist.id,a.id)
        repo.reorder(playlist.id,listOf(b.id,a.id))
        assertEquals(listOf(b.id,a.id),db.library().entries().map { it.trackId })
        assertEquals(listOf(0,1),db.library().entries().map { it.position })
        db.library().savePlaylist(playlist.copy(name="Renamed",favorite=true))
        assertEquals("Renaming/favoriting must retain playlist entries",listOf(b.id,a.id),db.library().entries().map{it.trackId})
        db.library().deletePlaylist(playlist.id)
        assertTrue(db.library().entries().isEmpty()); assertEquals(2,db.library().tracks().size)
    }
    @Test fun backupMergeAndCorruptionRollback() = runBlocking {
        val repository = LibraryRepository(context,db)
        val backup = BackupManager(context,db,SettingsStore(context))
        repository.saveReference("BV1xx411c7mD","OST","Artist","动漫 OST","piano,动漫")
        val encoded = backup.exportText(false)
        backup.restoreText(encoded,false)
        assertEquals(1,db.library().tracks().size)
        assertEquals(2,db.library().tagsFor(db.library().tracks().first().id).size)
        assertTrue(runCatching { backup.restoreText("{bad json",true) }.isFailure)
        assertEquals(1,db.library().tracks().size)
    }
    @Test fun deletingTrackCompactsPlaylistOrder() = runBlocking {
        val repo=LibraryRepository(context,db)
        val tracks=(1..3).map{Track(identity="local:content://test/$it",uri="content://test/$it",title="Song $it")}
        tracks.forEach{db.library().insertTrack(it)}
        val playlist=Playlist(name="Order");db.library().savePlaylist(playlist)
        repo.reorder(playlist.id,tracks.map{it.id})
        repo.removeTrack(tracks[1].id)
        assertEquals(listOf(0,1),db.library().entries().map{it.position})
        assertEquals(listOf(tracks[0].id,tracks[2].id),db.library().entries().map{it.trackId})
    }
    @Test fun onlinePlaybackRefreshPreservesEditedMetadata()=runBlocking {
        val repo=LibraryRepository(context,db)
        repo.saveReference("BV1xx411c7mD","我的标题","我的作者","动漫 OST","自定义")
        val original=db.library().tracks().single()
        val refreshed=repo.saveOnline(com.bilimusic.player.data.bilibili.OnlineSong("BV1xx411c7mD","API 标题","API 作者",10000))
        assertEquals(original.id,refreshed.id);assertEquals("我的标题",refreshed.title);assertEquals("我的作者",refreshed.artist)
        assertEquals("自定义",db.library().tagsFor(refreshed.id).single().name)
    }
    @Test fun mergedBackupRetainsSongsAddedAfterBackup() = runBlocking {
        val repo=LibraryRepository(context,db)
        val backup=BackupManager(context,db,SettingsStore(context))
        repo.saveReference("BV1xx411c7mD","A","Artist","其他","")
        val a=db.library().tracks().single()
        val p=Playlist(name="Merge");db.library().savePlaylist(p);repo.addToPlaylist(p.id,a.id)
        val snapshot=backup.exportText(false)
        repo.saveReference("https://www.bilibili.com/video/BV1xx411c7mD?p=2","B","Artist","其他","")
        val b=db.library().tracks().first{it.page==2};repo.addToPlaylist(p.id,b.id)
        backup.restoreText(snapshot,false)
        assertEquals(listOf(a.id,b.id),db.library().entries().map{it.trackId})
    }
    @Test fun invalidReferencesFailBeforeReplacement() = runBlocking {
        val repo=LibraryRepository(context,db);val backup=BackupManager(context,db,SettingsStore(context))
        repo.saveReference("BV1xx411c7mD","Keep","Artist","其他","")
        val root=JSONObject(backup.exportText(false))
        root.getJSONArray("trackTags").put(JSONObject().put("trackId","missing").put("tagId","missing"))
        assertTrue(runCatching{backup.restoreText(root.toString(),true)}.isFailure)
        assertEquals("Keep",db.library().tracks().single().title)
    }
    @Test fun replacementRestoresRelationshipsAndMarksMissingFiles() = runBlocking {
        val repo=LibraryRepository(context,db);val backup=BackupManager(context,db,SettingsStore(context))
        val missing=Track(identity="local:content://missing/document/a",uri="content://missing/document/a",title="Missing",favorite=true)
        db.library().insertTrack(missing)
        val playlist=Playlist(name="Restore");db.library().savePlaylist(playlist);repo.addToPlaylist(playlist.id,missing.id)
        val snapshot=backup.exportText(false)
        repo.saveReference("BV1xx411c7mD","Remove on replace","Artist","其他","")
        backup.restoreText(snapshot,true)
        val restored=db.library().tracks().single()
        assertEquals("Missing",restored.title);assertFalse(restored.available);assertTrue(restored.favorite)
        assertEquals(restored.id,db.library().entries().single().trackId)
    }
    @Test fun transactionFailureRollsBackReplacement() = runBlocking {
        val repo=LibraryRepository(context,db);val backup=BackupManager(context,db,SettingsStore(context))
        repo.saveReference("BV1xx411c7mD","Keep","Artist","其他","")
        val snapshot=backup.exportText(false)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_restore BEFORE INSERT ON tracks BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        assertTrue(runCatching{backup.restoreText(snapshot,true)}.isFailure)
        assertEquals("Keep",db.library().tracks().single().title)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_restore")
    }
    @Test fun roomSearchFiveThousandTracksAndTags() = runBlocking {
        db.withTransaction {
            (1..5000).forEach { n->db.library().insertTrack(Track(identity="local:content://test/$n",uri="content://test/$n",title="Song $n",artist="Artist ${n%100}")) }
        }
        val last=db.library().tracks().first{it.title=="Song 4999"}
        LibraryRepository(context,db).setTags(last.id,"唯一钢琴")
        val started=android.os.SystemClock.elapsedRealtime()
        assertEquals(listOf(last.id),db.library().search("%唯一钢琴%").first().map{it.id})
        val elapsed=android.os.SystemClock.elapsedRealtime()-started
        android.util.Log.i("BiliMusicTest","5000-track Room tag search: ${elapsed}ms")
        assertTrue("Query took ${elapsed}ms",elapsed<3000)
    }
}

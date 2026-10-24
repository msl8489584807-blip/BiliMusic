package com.bilimusic.player.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.bilimusic.player.database.Track
import com.bilimusic.player.domain.BiliReference
import com.bilimusic.player.domain.OnlineRules
import java.io.File

fun Track.mediaItem(): MediaItem {
    require(if(isLocal)uri!=null else bv!=null) { "曲目来源缺失" }
    val source=if(isLocal)uri!! else OnlineRules.mediaUri(BiliReference(bv!!,page))
    return MediaItem.Builder().setMediaId(id).setUri(source).apply{if(!isLocal)setMimeType(androidx.media3.common.MimeTypes.AUDIO_MP4)}
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album)
            .setArtworkUri(artwork?.let { if(it.startsWith("https://"))Uri.parse(it) else Uri.fromFile(File(it)) }).setIsPlayable(true).build()).build()
}

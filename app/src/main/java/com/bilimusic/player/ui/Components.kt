package com.bilimusic.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.bilimusic.player.database.Track

@Composable fun ActionIcon(icon: ImageVector, label: String, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) { Icon(icon,label,tint=if(active) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
}
@Composable fun Artwork(track: Track?, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    Box(modifier.size(size).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center) {
        val gold = MaterialTheme.colorScheme.primary
        val base = MaterialTheme.colorScheme.surfaceVariant
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            drawCircle(gold.copy(alpha=.22f),radius=this.size.minDimension*.46f)
            drawCircle(gold.copy(alpha=.25f),radius=this.size.minDimension*.34f,style=androidx.compose.ui.graphics.drawscope.Stroke(width=1.dp.toPx()))
            drawCircle(gold.copy(alpha=.5f),radius=this.size.minDimension*.22f)
            drawCircle(base,radius=this.size.minDimension*.08f)
        }
        track?.artwork?.let { AsyncImage(model=it,contentDescription="${track.title} 封面",contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize()) }
    }
}
@Composable fun EmptyState(title: String, detail: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=36.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Rounded.LibraryMusic,null,modifier=Modifier.size(44.dp),tint=MaterialTheme.colorScheme.primary)
        Text(title,style=MaterialTheme.typography.titleLarge)
        Text(detail,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(action != null) Button(onClick=onAction) { Text(action) }
    }
}
@Composable fun TrackRow(track: Track, playing: Boolean = false, onClick: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clickable(onClick=onClick).padding(start=20.dp,end=8.dp,top=8.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Artwork(track,size=52.dp)
        Column(Modifier.weight(1f)) {
            Text(track.title,maxLines=1,overflow=TextOverflow.Ellipsis,color=if(playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,style=MaterialTheme.typography.titleMedium)
            Text(if(!track.isLocal) "B站 · ${track.artist}" else if(!track.available) "文件不可访问 · 请重新关联" else track.artist,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(!track.isLocal) Icon(Icons.Rounded.Cloud,"在线音乐",modifier=Modifier.size(18.dp))
        if(track.favorite) Icon(Icons.Rounded.Favorite,"已收藏",modifier=Modifier.size(16.dp),tint=MaterialTheme.colorScheme.primary)
        ActionIcon(Icons.Rounded.MoreVert,"${track.title} 更多操作",onClick=onMore)
    }
}
@Composable fun SectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start=24.dp,end=24.dp,top=24.dp,bottom=8.dp)) { Text(title,style=MaterialTheme.typography.titleLarge); subtitle?.let { Text(it,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
}
fun formatTime(ms: Long): String { val seconds=(ms.coerceAtLeast(0)/1000); return "%d:%02d".format(seconds/60,seconds%60) }

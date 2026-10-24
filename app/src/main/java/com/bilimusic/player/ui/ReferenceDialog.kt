package com.bilimusic.player.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.bilimusic.player.database.Track
import com.bilimusic.player.domain.BiliParser

@OptIn(ExperimentalLayoutApi::class)
@UnstableApi
@Composable fun ReferenceDialog(vm:MainViewModel,existing:Track?,onDismiss:()->Unit) {
    var input by rememberSaveable(existing?.id){mutableStateOf(existing?.let{"https://www.bilibili.com/video/${it.bv}?p=${it.page}"}?:"")}
    var title by rememberSaveable(existing?.id){mutableStateOf(existing?.title?:"")}
    var artist by rememberSaveable(existing?.id){mutableStateOf(existing?.artist?:"")}
    var category by rememberSaveable(existing?.id){mutableStateOf(existing?.category?:Categories.first())}
    var tags by rememberSaveable(existing?.id){mutableStateOf("")}
    var loadedTags by rememberSaveable(existing?.id){mutableStateOf(false)}
    val busy by vm.busy.collectAsStateWithLifecycle()
    val parsed=remember(input){runCatching{BiliParser.parse(input)}}
    LaunchedEffect(existing?.id){if(!loadedTags){existing?.let{tags=vm.repository.dao.tagsFor(it.id).joinToString("，"){it.name}};loadedTags=true}}
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text(if(existing==null)"收藏 Bilibili 视频" else "编辑视频收藏")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("保存 BV 后，点击歌曲即可在 BiliMusic 内播放音轨。离线可以先填写信息。",style=MaterialTheme.typography.bodyMedium)
        OutlinedTextField(input,{input=it.take(2000)},label={Text("BV 编号或完整视频链接")},isError=input.isNotBlank()&&parsed.isFailure,supportingText={Text(if(input.isNotBlank()&&parsed.isFailure)parsed.exceptionOrNull()?.message.orEmpty() else "格式通过不代表视频真实存在")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(title,{title=it.take(200)},label={Text("标题（必填）")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(artist,{artist=it.take(200)},label={Text("艺术家")},modifier=Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){Categories.forEach{c->FilterChip(selected=category==c,onClick={category=c},label={Text(c)})}}
        OutlinedTextField(tags,{tags=it.take(1800)},label={Text("标签，用逗号分隔")},modifier=Modifier.fillMaxWidth())
    }},confirmButton={TextButton(enabled=!busy&&parsed.isSuccess&&title.isNotBlank(),onClick={vm.action("视频收藏已保存"){vm.repository.saveReference(input,title,artist,category,tags,existing);onDismiss()}}){Text("保存收藏")}},dismissButton={TextButton(enabled=!busy,onClick=onDismiss){Text("取消")}})
}

package com.bilimusic.player.domain

import java.net.URI

object OnlineRules {
    fun mediaUri(reference:BiliReference)="bilimusic://bilibili/${reference.bv}?p=${reference.page}"
    fun reference(value:String):BiliReference {
        val uri=URI(value)
        require(uri.scheme=="bilimusic"&&uri.host=="bilibili") { "无效的在线曲目" }
        return BiliParser.parse("https://www.bilibili.com/video${uri.path}?${uri.rawQuery.orEmpty()}")
    }
    fun isStreamUrl(value:String):Boolean=runCatching {
        val uri=URI(value);val host=uri.host?.lowercase().orEmpty()
        uri.scheme=="https"&&uri.userInfo==null&&uri.port in setOf(-1,443)&&listOf("bilivideo.com","bilivideo.cn","akamaized.net").any{host==it||host.endsWith(".$it")}
    }.getOrDefault(false)
    fun durationMs(value:String):Long {
        val parts=value.split(':').map{it.toLongOrNull()?:return 0}
        if(parts.size !in 1..3||parts.any{it<0||it>99999})return 0
        return parts.fold(0L){acc,n->acc*60+n}*1000
    }
    fun validUntil(url:String,now:Long):Long {
        val deadline=runCatching{URI(url).rawQuery.orEmpty().split('&').firstOrNull{it.startsWith("deadline=")}?.substringAfter('=')?.toLongOrNull()?.times(1000)?.minus(30000)}.getOrNull()
        return minOf(now+300000,deadline?:Long.MAX_VALUE)
    }
}

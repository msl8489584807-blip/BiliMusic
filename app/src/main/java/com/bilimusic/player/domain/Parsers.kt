package com.bilimusic.player.domain

import java.net.URI
import java.net.URLDecoder

data class BiliReference(val bv: String, val page: Int = 1) {
    val webUrl get() = "https://www.bilibili.com/video/$bv?p=$page"
}

object BiliParser {
    private val bvFormat = Regex("BV[1-9A-HJ-NP-Za-km-z]{10}")
    fun parse(input: String): BiliReference {
        val text = input.trim()
        if (bvFormat.matches(text)) return BiliReference(text)
        val uri = try { URI(text) } catch (_: Exception) { throw IllegalArgumentException("请输入 BV 编号或完整的视频链接") }
        require(uri.scheme == "https" || uri.scheme == "http") { "仅支持 http/https 视频链接" }
        require(uri.rawUserInfo == null && (uri.port == -1 || uri.port == 443 || uri.port == 80)) { "链接包含不支持的地址信息" }
        require(uri.host?.lowercase() in setOf("bilibili.com", "www.bilibili.com", "m.bilibili.com")) { "请使用 bilibili.com 视频链接；短链接请先在浏览器展开" }
        val match = Regex("^/video/(BV[1-9A-HJ-NP-Za-km-z]{10})/?$").matchEntire(uri.path.orEmpty())
        requireNotNull(match) { "未找到合法 BV 编号" }
        val pages = uri.rawQuery.orEmpty().split('&').filter { it.isNotBlank() }.map { it.split('=', limit = 2) }
            .filter { URLDecoder.decode(it[0], "UTF-8") == "p" }
        require(pages.size <= 1) { "分 P 参数重复" }
        val page = if (pages.isEmpty()) 1 else pages.single().getOrNull(1)?.let { URLDecoder.decode(it,"UTF-8").toIntOrNull() }
        require(page != null && page in 1..10000) { "分 P 应为 1–10000 的整数" }
        return BiliReference(match.groupValues[1], page)
    }
}

data class LyricLine(val timeMs: Long, val text: String)
object LrcParser {
    private val stamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    fun parse(text: String): List<LyricLine> {
        val offset = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        return text.lineSequence().flatMap { line ->
            val matches = stamp.findAll(line).toList()
            val words = line.substringAfterLast(']', "").trim()
            matches.mapNotNull { m ->
                val second = m.groupValues[2].toInt()
                if (second > 59) null else LyricLine((m.groupValues[1].toLong() * 60000 + second * 1000 + m.groupValues[3].padEnd(3, '0').take(3).toLong() + offset).coerceAtLeast(0), words)
            }.asSequence()
        }.sortedBy { it.timeMs }.toList()
    }
}

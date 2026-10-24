package com.bilimusic.player.domain

object LibraryRules {
    fun <T> move(items: List<T>, from: Int, to: Int): List<T> {
        require(from in items.indices && to in items.indices)
        return items.toMutableList().apply { add(to, removeAt(from)) }
    }
    fun <T> normalizeOrder(items: List<T>) = items.distinct()
    fun biliIdentity(bv: String, page: Int) = "bili:$bv:$page"
    fun localIdentity(uri: String): String = "local:${java.net.URI(uri).normalize()}"
    fun searchPattern(query: String) = "%${query.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_")}%"
    fun matches(query: String, fields: List<String>) = fields.any { it.contains(query.trim(), ignoreCase = true) }
}

interface BilibiliSourceAdapter {
    suspend fun authorizationAvailable(): Boolean
    suspend fun metadata(reference: BiliReference): BilibiliMetadata?
    suspend fun authorizedPlaybackUri(reference: BiliReference): String?
}
data class BilibiliMetadata(val title: String, val artist: String)
// Online first-party transport is implemented by data/bilibili/BilibiliClient.
// This interface remains available for separately scoped future source integrations.

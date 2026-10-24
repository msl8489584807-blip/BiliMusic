package com.bilimusic.player.domain

import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    @Test fun bareBvAndMultipartUrl() {
        assertEquals(BiliReference("BV1xx411c7mD", 1), BiliParser.parse("BV1xx411c7mD"))
        assertEquals(BiliReference("BV1xx411c7mD", 3), BiliParser.parse("https://www.bilibili.com/video/BV1xx411c7mD/?p=3&share_source=copy"))
    }
    @Test fun rejectsUntrustedOrAmbiguousInput() {
        listOf("https://bilibili.com.evil.org/video/BV1xx411c7mD", "https://evil.org/BV1xx411c7mD", "https://u@bilibili.com/video/BV1xx411c7mD", "https://b23.tv/foo", "BVwrong", "https://www.bilibili.com/video/BV1xx411c7mD?p=0", "https://www.bilibili.com/video/BV1xx411c7mD?p=a", "https://www.bilibili.com/video/BV1xx411c7mD?p=1&p=2").forEach { assertTrue(it, runCatching { BiliParser.parse(it) }.isFailure) }
    }
    @Test fun lrcSupportsMultipleTimestampsFractionsAndSort() {
        val lines = LrcParser.parse("[ar:artist]\n[00:12.30][00:02.005]two\n[00:01.1]one\n[broken]x")
        assertEquals(listOf(1100L, 2005L, 12300L), lines.map { it.timeMs })
        assertEquals(listOf("one", "two", "two"), lines.map { it.text })
    }
    @Test fun lrcOffsetAndMalformedLines() {
        assertEquals(500L, LrcParser.parse("[offset:-500]\n[00:01.000]hello").single().timeMs)
        assertTrue(LrcParser.parse("[99:88]bad\nno time").isEmpty())
    }
    @Test fun stableReorderAndDeduplication() {
        assertEquals(listOf("b", "c", "a"), LibraryRules.move(listOf("a","b","c"),0,2))
        assertEquals(listOf("a","b"), LibraryRules.normalizeOrder(listOf("a","a","b")))
        assertEquals("bili:BV1xx411c7mD:2", LibraryRules.biliIdentity("BV1xx411c7mD",2))
        assertEquals(LibraryRules.localIdentity("content://provider/document/a"), LibraryRules.localIdentity("content://provider/document/a"))
        assertNotEquals(LibraryRules.localIdentity("content://provider/document/a"), LibraryRules.localIdentity("content://provider/document/b"))
    }
    @Test fun searchEscapesSqlWildcardsAndMatchesMetadata() {
        assertEquals("%50!%!_\\%", LibraryRules.searchPattern("50%_\\"))
        assertEquals("%hello!!%", LibraryRules.searchPattern("hello!"))
        assertTrue(LibraryRules.matches("  OST ", listOf("动漫 ost", "Artist")))
        assertFalse(LibraryRules.matches("piano", listOf("ost")))
    }
    @Test fun searchFiveThousandMetadataRecords() {
        val values = (1..5000).map { "Song $it" }
        assertEquals(1, values.count { LibraryRules.matches("Song 4999", listOf(it)) })
    }
}

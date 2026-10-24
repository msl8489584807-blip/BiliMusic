package com.bilimusic.player.domain

import org.junit.Assert.*
import org.junit.Test

class OnlineRulesTest {
    @Test fun internalMediaIdPreservesPageWithoutPersistingStream() {
        val ref=BiliReference("BV1xx411c7mD",3)
        assertEquals("bilimusic://bilibili/BV1xx411c7mD?p=3",OnlineRules.mediaUri(ref))
        assertEquals(ref,OnlineRules.reference(OnlineRules.mediaUri(ref)))
    }
    @Test fun rejectsNonBiliInternalIds() {
        assertTrue(runCatching{OnlineRules.reference("https://evil.test/BV1xx411c7mD")}.isFailure)
    }
    @Test fun mediaHostsRequireHttpsAndExactDomainBoundary() {
        assertTrue(OnlineRules.isStreamUrl("https://upos-sz-mirror.bilivideo.com/a.m4s?deadline=9999999999"))
        assertFalse(OnlineRules.isStreamUrl("https://bilivideo.com.evil.test/a"))
        assertFalse(OnlineRules.isStreamUrl("http://cdn.bilivideo.com/a"))
        assertFalse(OnlineRules.isStreamUrl("https://user@cdn.bilivideo.com/a"))
    }
    @Test fun durationSupportsHoursAndBadInput() {
        assertEquals(3723000L,OnlineRules.durationMs("1:02:03"))
        assertEquals(123000L,OnlineRules.durationMs("02:03"))
        assertEquals(0L,OnlineRules.durationMs("bad"))
    }
    @Test fun cacheExpiresBeforeServerDeadline() {
        assertEquals(270000L,OnlineRules.validUntil("https://cdn.bilivideo.com/a?deadline=300",0))
        assertEquals(300000L,OnlineRules.validUntil("https://cdn.bilivideo.com/a",0))
    }
}

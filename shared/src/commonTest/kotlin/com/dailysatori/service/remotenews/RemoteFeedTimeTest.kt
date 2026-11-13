package com.dailysatori.service.remotenews

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemoteFeedTimeTest {
    @Test
    fun keepsIsoTimestamps() {
        assertEquals("2026-11-12T08:00:00Z", normalizeRemoteFeedTime("2026-11-12T08:00:00Z"))
    }

    @Test
    fun convertsRfc822Timestamps() {
        assertEquals("2026-11-12T08:00:00Z", normalizeRemoteFeedTime("Tue, 12 Nov 2026 08:00:00 GMT"))
        assertEquals("2026-11-12T00:00:00Z", normalizeRemoteFeedTime("Tue, 12 Nov 2026 08:00:00 +0800"))
        assertEquals("2026-11-12T13:00:00Z", normalizeRemoteFeedTime("12 Nov 2026 08:00:00 EST"))
    }

    @Test
    fun dropsUnparseableTimestampsInsteadOfGuessing() {
        assertNull(normalizeRemoteFeedTime(null))
        assertNull(normalizeRemoteFeedTime(""))
        assertNull(normalizeRemoteFeedTime("刚刚"))
    }
}

package com.dailysatori.service.unifiednews

import kotlin.test.Test
import kotlin.test.assertEquals

class UnifiedNewsTickPolicyTest {
    private val tick = 1_000_000L

    @Test
    fun currentTickWithoutSummaryGenerates() {
        assertEquals(
            UnifiedNewsTickDecision.GENERATE,
            unifiedNewsTickDecision(tickDueAtMs = tick, latestDueAtMs = tick, latestSuccessfulAtMs = null),
        )
        assertEquals(
            UnifiedNewsTickDecision.GENERATE,
            unifiedNewsTickDecision(tickDueAtMs = tick, latestDueAtMs = tick, latestSuccessfulAtMs = tick - 1),
        )
    }

    @Test
    fun lateWorkForAnOlderTickIsStale() {
        assertEquals(
            UnifiedNewsTickDecision.SKIP_STALE,
            unifiedNewsTickDecision(tickDueAtMs = tick - 60_000, latestDueAtMs = tick, latestSuccessfulAtMs = null),
        )
    }

    @Test
    fun aTickThatAlreadyProducedASummaryIsNotRegenerated() {
        assertEquals(
            UnifiedNewsTickDecision.SKIP_GENERATED,
            unifiedNewsTickDecision(tickDueAtMs = tick, latestDueAtMs = tick, latestSuccessfulAtMs = tick),
        )
        assertEquals(
            UnifiedNewsTickDecision.SKIP_GENERATED,
            unifiedNewsTickDecision(tickDueAtMs = tick, latestDueAtMs = tick, latestSuccessfulAtMs = tick + 1),
        )
    }

    @Test
    fun manualRefreshIsNeverBlockedByAutomaticDeduplication() {
        assertEquals(
            UnifiedNewsTickDecision.GENERATE,
            unifiedNewsTickDecision(tickDueAtMs = tick, latestDueAtMs = tick, latestSuccessfulAtMs = tick, manual = true),
        )
        assertEquals(
            UnifiedNewsTickDecision.GENERATE,
            unifiedNewsTickDecision(tickDueAtMs = tick - 60_000, latestDueAtMs = tick, latestSuccessfulAtMs = tick, manual = true),
        )
        assertEquals(
            UnifiedNewsTickDecision.GENERATE,
            unifiedNewsTickDecision(tickDueAtMs = null, latestDueAtMs = tick, latestSuccessfulAtMs = tick, manual = true),
        )
    }

    @Test
    fun legacyWorkWithoutTickIdentityKeepsPreviousBehaviour() {
        assertEquals(
            UnifiedNewsTickDecision.GENERATE,
            unifiedNewsTickDecision(tickDueAtMs = null, latestDueAtMs = tick, latestSuccessfulAtMs = tick),
        )
    }
}

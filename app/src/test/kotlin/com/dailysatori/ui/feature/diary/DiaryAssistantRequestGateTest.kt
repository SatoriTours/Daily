package com.dailysatori.ui.feature.diary

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiaryAssistantRequestGateTest {
    @Test
    fun disposalInvalidationRejectsLateNonCooperativeCompletion() {
        val gate = DiaryAssistantRequestGate()
        val request = gate.begin()

        assertTrue(gate.isCurrent(request))
        gate.invalidate()

        assertFalse(gate.isCurrent(request))
    }

    @Test
    fun replacementRequestInvalidatesPreviousGeneration() {
        val gate = DiaryAssistantRequestGate()
        val first = gate.begin()
        val second = gate.begin()

        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }
}

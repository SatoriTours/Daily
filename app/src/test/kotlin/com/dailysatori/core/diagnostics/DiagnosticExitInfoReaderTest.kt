package com.dailysatori.core.diagnostics

import kotlin.test.*

class DiagnosticExitInfoReaderTest {
    @Test
    fun oldPlatformsAndPreviouslySeenExitsAreNotFabricatedAsCrashes() {
        assertNull(DiagnosticExitInfoReader.select(26, 0, listOf(DiagnosticExit(20, 6))))
        assertNull(DiagnosticExitInfoReader.select(30, 20, listOf(DiagnosticExit(20, 6))))
        assertEquals(DiagnosticExit(30, 10), DiagnosticExitInfoReader.select(30, 20,
            listOf(DiagnosticExit(20, 6), DiagnosticExit(30, 10))))
        assertFalse(DiagnosticExit(30, 10).isCrash)
        assertTrue(DiagnosticExit(30, 6).isCrash)
    }
}

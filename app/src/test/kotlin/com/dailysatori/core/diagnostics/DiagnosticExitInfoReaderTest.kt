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

    @Test
    fun newerExitFromAnotherProcessDoesNotShadowTheCurrentProcess() {
        val records = listOf(
            DiagnosticExit(30, 13, "com.dailysatori"),
            DiagnosticExit(40, 1, "com.dailysatori:diagnostics"),
        )
        assertEquals(DiagnosticExit(30, 13, "com.dailysatori"),
            DiagnosticExitInfoReader.select(30, 20, records, "com.dailysatori"))
        assertNull(DiagnosticExitInfoReader.select(30, 35, records, "com.dailysatori"))
        assertEquals(DiagnosticExit(40, 1, "com.dailysatori:diagnostics"),
            DiagnosticExitInfoReader.select(30, 20, records, "com.dailysatori:diagnostics"))
    }
}

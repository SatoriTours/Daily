package com.dailysatori.core.diagnostics

import co.touchlab.kermit.Severity
import com.dailysatori.service.diagnostics.*
import kotlin.test.*

class DiagnosticLogWriterTest {
    @Test
    fun legacyMessagesNeverBypassSafeProjection() {
        val events = mutableListOf<SafeDiagnosticEvent>()
        val writer = DiagnosticLogWriter(Diagnostics(DiagnosticSink { events.add(it) }))
        writer.log(Severity.Error, "private diary Bearer token-canary", "private-tag", IllegalStateException("private-error"))
        val text = diagnosticJson.encodeToString(SafeDiagnosticEvent.serializer(), events.single())
        assertFalse(text.contains("private"))
        assertFalse(text.contains("token-canary"))
        assertEquals(DiagnosticLevel.ERROR, events.single().level)
        assertTrue(text.contains("IllegalStateException"))
    }
}

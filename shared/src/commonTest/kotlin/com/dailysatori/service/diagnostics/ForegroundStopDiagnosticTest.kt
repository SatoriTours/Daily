package com.dailysatori.service.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ForegroundStopDiagnosticTest {
    private val components = setOf("workmanager_system_foreground", "diary_recording", "unknown")
    private val typeNames = setOf("data_sync", "microphone", "camera", "location", "media_playback",
        "connectivity", "short_service", "special_use", "unknown")

    @Test
    fun unrelatedExceptionProducesNoAttributes() {
        assertEquals(emptyMap(), ForegroundStopDiagnostic.attributes(
            "java.lang.IllegalStateException",
            "androidx.work.impl.foreground.SystemForegroundService dataSync token=secret-canary",
        ))
    }

    @Test
    fun knownComponentAndTypeMapToFixedValues() {
        val attributes = ForegroundStopDiagnostic.attributes(
            "android.app.RemoteServiceException\$ForegroundServiceDidNotStopInTimeException",
            "ServiceRecord{abc u0 androidx.work.impl.foreground.SystemForegroundService} foregroundServiceType=dataSync",
        )
        assertEquals("fgs_stop_timeout", attributes["crashKind"])
        assertEquals("workmanager_system_foreground", attributes["fgsComponent"])
        assertEquals("data_sync", attributes["fgsTypeName"])
    }

    @Test
    fun maliciousMessageOnlyLeavesWhitelistedValues() {
        val attributes = ForegroundStopDiagnostic.attributes(
            "android.app.RemoteServiceException\$ForegroundServiceDidNotStopInTimeException",
            "component=com.evil.OtherService type=dataSync token=secret-canary Authorization=Bearer abc123 /sdcard/private",
        )
        assertEquals(setOf("crashKind", "fgsComponent", "fgsTypeName"), attributes.keys)
        assertTrue(attributes.getValue("fgsComponent") in components)
        assertTrue(attributes.getValue("fgsTypeName") in typeNames)
        assertEquals("unknown", attributes["fgsComponent"])
        attributes.values.forEach { value ->
            listOf("secret-canary", "Bearer", "abc123", "evil", "/sdcard").forEach { leaked ->
                assertTrue(value.contains(leaked).not(), "不得泄露 " + leaked)
            }
        }
    }

    @Test
    fun unknownMessageStaysWithinWhitelist() {
        val attributes = ForegroundStopDiagnostic.attributes(
            "android.app.RemoteServiceException\$ForegroundServiceDidNotStopInTimeException", null,
        )
        assertEquals("unknown", attributes["fgsComponent"])
        assertEquals("unknown", attributes["fgsTypeName"])
    }
}

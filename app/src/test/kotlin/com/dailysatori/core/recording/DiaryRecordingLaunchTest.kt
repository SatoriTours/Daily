package com.dailysatori.core.recording

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiaryRecordingLaunchTest {
    @Test
    fun launchFailuresMapToStableCodesAcrossSupportedApiLevels() {
        assertEquals(
            DiaryRecordingErrorCode.FOREGROUND_START_NOT_ALLOWED,
            foregroundLaunchFailureCode(26, IllegalStateException("background start denied")),
        )
        assertEquals(
            DiaryRecordingErrorCode.FOREGROUND_START_NOT_ALLOWED,
            foregroundLaunchFailureCode(30, IllegalStateException("background start denied")),
        )
        assertEquals(
            DiaryRecordingErrorCode.FOREGROUND_START_NOT_ALLOWED,
            foregroundLaunchFailureCode(
                sdkInt = 31,
                error = IllegalStateException("foreground start denied"),
                isApi31ForegroundStartDenied = true,
            ),
        )
        assertEquals(
            DiaryRecordingErrorCode.FOREGROUND_SECURITY_DENIED,
            foregroundLaunchFailureCode(34, SecurityException("microphone denied")),
        )
        assertNull(foregroundLaunchFailureCode(25, IllegalStateException("unrelated")))
        assertNull(foregroundLaunchFailureCode(26, IllegalArgumentException("unrelated")))
    }
}

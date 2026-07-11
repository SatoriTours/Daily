package com.dailysatori.core.recording

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiaryRecordingServiceContractTest {
    @Test
    fun serviceExposesAllRecordingActionsAndStartsOnlyFromUserEntryPoint() {
        val source = source("DiaryRecordingService.kt")

        listOf("ACTION_START", "ACTION_PAUSE", "ACTION_RESUME", "ACTION_STOP", "ACTION_OPEN").forEach {
            assertTrue(source.contains("const val $it"), "$it must be stable and public")
        }
        assertTrue(source.contains("fun startFromUserAction("))
        assertTrue(source.contains("ContextCompat.startForegroundService"))
        assertFalse(source.contains("startForegroundService(") && !source.contains("ContextCompat.startForegroundService"))
    }

    @Test
    fun serviceEntersMicrophoneForegroundBeforeStartingRecorderWork() {
        val source = source("DiaryRecordingService.kt")
        val startAction = source.substringAfter("ACTION_START ->").substringBefore("ACTION_PAUSE ->")

        assertTrue(startAction.indexOf("enterForeground") in 0 until startAction.indexOf("startRecording"))
        assertTrue(source.contains("ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE"))
        assertTrue(source.contains("Build.VERSION_CODES.Q"))
    }

    @Test
    fun notificationProvidesPauseResumeStopAndOpenDiaryActions() {
        val source = source("DiaryRecordingNotification.kt")

        assertTrue(source.contains("ACTION_PAUSE"))
        assertTrue(source.contains("ACTION_RESUME"))
        assertTrue(source.contains("ACTION_STOP"))
        assertTrue(source.contains("ACTION_OPEN"))
    }

    @Test
    fun mediaRecorderIsVersionGuardedAndHiddenBehindTheRecorderInterface() {
        val abstraction = source("DiaryRecorder.kt")
        val androidRecorder = source("AndroidDiaryRecorder.kt")

        assertFalse(abstraction.contains("android.media.MediaRecorder"))
        assertTrue(androidRecorder.contains("Build.VERSION_CODES.S"))
        assertTrue(androidRecorder.contains("MediaRecorder(context)"))
        assertTrue(androidRecorder.contains("MediaRecorder()"))
    }

    @Test
    fun handledFailureCannotBePersistedAgainDuringServiceDestruction() {
        val source = source("DiaryRecordingService.kt")
        val failureHandler = source.substringAfter("private fun handleFailure(")
            .substringBefore("private fun preserveInterruptedRecording(")

        assertTrue(failureHandler.indexOf("activeAttachmentId = null") in 0 until failureHandler.indexOf("stopSelf()"))
    }

    private fun source(fileName: String): String =
        File("src/main/kotlin/com/dailysatori/core/recording/$fileName").readText()
}

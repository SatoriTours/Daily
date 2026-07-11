package com.dailysatori.core.recording

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiaryRecordingServiceContractTest {
    @Test
    fun servicePreservesPublicActionsAndUserVisibleLaunchEntryPoint() {
        val source = source("DiaryRecordingService.kt")

        listOf(
            "ACTION_START",
            "ACTION_PAUSE",
            "ACTION_RESUME",
            "ACTION_STOP",
            "ACTION_RETRY_PERSIST",
            "ACTION_OPEN",
        ).forEach {
            assertTrue(source.contains("const val $it"), "$it must remain stable and public")
        }
        assertTrue(source.contains("fun startFromUserAction("))
        assertTrue(source.contains("ContextCompat.startForegroundService"))
    }

    @Test
    fun serviceParsesIntentsAndSubmitsTypedActorCommandsOnly() {
        val source = source("DiaryRecordingService.kt")
        val startCommand = "DiaryRecordingCommand.Start(diaryId, attachmentId)"

        assertTrue(source.contains("actor.submit($startCommand)"))
        assertTrue(source.contains("actor.submit(DiaryRecordingCommand.Pause)"))
        assertTrue(source.contains("actor.submit(DiaryRecordingCommand.Resume)"))
        assertTrue(source.contains("actor.submit(DiaryRecordingCommand.Stop)"))
        assertTrue(source.contains("actor.submit(DiaryRecordingCommand.RetryPersistence)"))
        listOf(
            "Channel<",
            "Mutex()",
            "activeDiaryId",
            "activeAttachmentId",
            "activeOutputFile",
            "pendingPersistence",
            "DiaryRecordingSessionCoordinator",
            "retryDiaryRecordingPersistence",
            "recorder.start(",
            "recorder.stop(",
        ).forEach { forbidden ->
            assertFalse(source.contains(forbidden), "Service must not own $forbidden")
        }
    }

    @Test
    fun destroySubmitsShutdownWithoutBlockingTheMainThread() {
        val source = source("DiaryRecordingService.kt")
        val destroy = source.substringAfter("override fun onDestroy()")
            .substringBefore("private fun enterForeground")

        assertTrue(destroy.contains("actor.submit(DiaryRecordingCommand.Shutdown)"))
        assertFalse(destroy.contains("runBlocking"))
        assertFalse(source.contains("import kotlinx.coroutines.runBlocking"))
        assertFalse(destroy.contains("withTimeoutOrNull"))
        assertFalse(destroy.contains("cancelAndJoin"))
    }

    @Test
    fun recorderBoundaryUsesAClosableDedicatedSingleThreadExecutor() {
        val source = source("DiaryRecordingService.kt")

        assertTrue(source.contains("Executors.newSingleThreadExecutor"))
        assertTrue(source.contains("asCoroutineDispatcher()"))
        assertTrue(source.contains("recorderDispatcher = recorderDispatcher"))
    }

    @Test
    fun serviceActorHostOwnsOnlyAndroidForegroundAndNotificationEffects() {
        val source = source("DiaryRecordingService.kt")

        assertTrue(source.contains("DiaryRecordingActorHost"))
        assertTrue(source.contains("override fun enterForeground(state: DiaryRecordingState)"))
        assertTrue(source.contains("override fun stateChanged(state: DiaryRecordingState)"))
        assertTrue(source.contains("override fun stopForeground()"))
        assertTrue(source.contains("override fun stopService()"))
        assertTrue(source.contains("ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE"))
    }

    @Test
    fun persistenceAdapterKeepsRepositoryWritesBehindTheActor() {
        val source = source("DiaryRecordingService.kt")

        assertTrue(source.contains("object : DiaryRecordingPersistence"))
        assertTrue(source.contains("attachmentRepository.beginRecording("))
        assertTrue(source.contains("attachmentRepository.completeRecording("))
        assertTrue(source.contains("attachmentRepository.failRecording("))
    }

    @Test
    fun foregroundStartFailuresKeepStableCrossApiMapping() {
        val source = source("DiaryRecordingService.kt")

        assertTrue(source.contains("ForegroundServiceStartNotAllowedException"))
        assertTrue(source.contains("foregroundLaunchFailureCode("))
        assertTrue(source.contains("DiaryRecordingLaunchResult"))
        assertTrue(source.contains("Build.VERSION.SDK_INT"))
    }

    @Test
    fun persistenceFailureNotificationOffersRetryAndFunctionalDiscard() {
        val source = source("DiaryRecordingNotification.kt")
        val strings = File("src/main/res/values/strings.xml").readText()
        val persistenceFailure = source.substringAfter(
            "state is DiaryRecordingState.PersistenceFailed",
        ).substringBefore("android.R.drawable.ic_menu_view")

        assertTrue(source.contains("ACTION_RETRY_PERSIST"))
        assertTrue(source.contains("R.string.diary_recording_action_discard"))
        assertTrue(source.contains("serviceIntent(DiaryRecordingService.ACTION_STOP)"))
        assertTrue(strings.contains("name=\"diary_recording_action_discard\""))
        assertFalse(persistenceFailure.contains("diary_recording_action_stop"))
    }

    @Test
    fun mediaRecorderRemainsVersionGuardedBehindTheRecorderInterface() {
        val abstraction = source("DiaryRecorder.kt")
        val androidRecorder = source("AndroidDiaryRecorder.kt")

        assertFalse(abstraction.contains("android.media.MediaRecorder"))
        assertTrue(androidRecorder.contains("Build.VERSION_CODES.S"))
        assertTrue(androidRecorder.contains("MediaRecorder(context)"))
        assertTrue(androidRecorder.contains("MediaRecorder()"))
    }

    private fun source(fileName: String): String =
        File("src/main/kotlin/com/dailysatori/core/recording/$fileName").readText()
}

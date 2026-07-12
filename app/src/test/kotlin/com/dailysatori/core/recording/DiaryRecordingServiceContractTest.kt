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
    fun serviceParsesIntentsAndSubmitsTypedRuntimeCommandsOnly() {
        val source = source("DiaryRecordingService.kt")

        assertTrue(source.contains("runtimeManager.submit("))
        assertTrue(source.contains("runtimeLease,"))
        assertTrue(source.contains("startId,"))
        assertTrue(source.contains("DiaryRecordingCommand.Start(diaryId, attachmentId)"))
        assertTrue(source.contains("DiaryRecordingCommand.Pause"))
        assertTrue(source.contains("DiaryRecordingCommand.Resume"))
        assertTrue(source.contains("DiaryRecordingCommand.Stop"))
        assertTrue(source.contains("DiaryRecordingCommand.RetryPersistence"))
        listOf(
            "DiaryRecordingActor(",
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
    fun destroyDetachesOnlyAndDoesNotDirectlyShutdownProcessRuntime() {
        val source = source("DiaryRecordingService.kt")
        val destroy = source.substringAfter("override fun onDestroy()")
            .substringBefore("private fun enterForeground")

        assertTrue(destroy.contains("runtimeManager.detachHost(runtimeLease)"))
        assertFalse(destroy.contains("DiaryRecordingCommand.Shutdown"))
        assertFalse(destroy.contains("runtimeManager.shutdown()"))
        assertFalse(destroy.contains("runBlocking"))
        assertFalse(source.contains("import kotlinx.coroutines.runBlocking"))
        assertFalse(destroy.contains("withTimeoutOrNull"))
        assertFalse(destroy.contains("cancelAndJoin"))
    }

    @Test
    fun foregroundServiceOwnsRecordingAcrossUiBackgroundAndScreenOff() {
        val source = source("DiaryRecordingService.kt")
        val destroy = source.substringAfter("override fun onDestroy()")
            .substringBefore("private fun enterForeground")

        assertTrue(source.contains("ContextCompat.startForegroundService(context, intent)"))
        assertTrue(source.contains("ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE"))
        assertTrue(source.contains("return START_NOT_STICKY"))
        assertFalse(destroy.contains("DiaryRecordingCommand.Stop"))
        assertFalse(destroy.contains("ACTION_STOP"))
    }

    @Test
    fun recordingNotificationIsPublicOngoingStatusOnlyAndProvidesExpectedControls() {
        val source = source("DiaryRecordingNotification.kt")
        val strings = File("src/main/res/values/strings.xml").readText()

        assertTrue(source.contains(".setOngoing(state.isActive())"))
        assertTrue(source.contains(".setCategory(NotificationCompat.CATEGORY_SERVICE)"))
        assertTrue(source.contains(".setVisibility(NotificationCompat.VISIBILITY_PUBLIC)"))
        assertTrue(source.contains(".setContentText(formatElapsed(state.elapsedMs))"))
        assertTrue(source.contains("serviceIntent(DiaryRecordingService.ACTION_PAUSE)"))
        assertTrue(source.contains("serviceIntent(DiaryRecordingService.ACTION_RESUME)"))
        assertTrue(source.contains("serviceIntent(DiaryRecordingService.ACTION_STOP)"))
        assertTrue(source.contains("openDiaryIntent(diaryId)"))
        assertTrue(strings.contains(
            "<string name=\"diary_recording_active\">语音日记录音中</string>",
        ))
        assertFalse(source.contains("setStyle(NotificationCompat.BigTextStyle"))
        assertFalse(source.contains("diary.content"))
        assertFalse(source.contains("diary.text"))
    }

    @Test
    fun processSingletonManagerRecreatesClosedRuntimeAndRecorderExecutor() {
        val service = source("DiaryRecordingService.kt")
        val runtime = source("DiaryRecordingRuntime.kt")
        val manager = source("DiaryRecordingRuntimeManager.kt")
        val module = File("src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt").readText()

        assertFalse(service.contains("Executors.newSingleThreadExecutor"))
        assertFalse(service.contains("DiaryRecorder by inject"))
        assertTrue(runtime.contains("private val actor = DiaryRecordingActor("))
        assertTrue(manager.contains("current ?: createManagedRuntime()"))
        assertTrue(manager.contains("if (current === runtime) current = null"))
        assertTrue(module.contains("DiaryRecordingRuntimeManager { onClosed ->"))
        assertTrue(module.contains("Executors.newSingleThreadExecutor"))
        assertTrue(module.contains("DiaryRecordingRuntime("))
        assertTrue(module.contains("recorderDispatcher = recorderDispatcher"))
        assertTrue(module.contains("onClosed = onClosed"))
    }

    @Test
    fun serviceAndroidHostOwnsOnlyForegroundNotificationAndStartIdEffects() {
        val source = source("DiaryRecordingService.kt")

        assertTrue(source.contains("DiaryRecordingAndroidHost"))
        assertTrue(source.contains("runtimeManager.attachHost("))
        assertTrue(source.contains("override fun enterForeground(state: DiaryRecordingState)"))
        assertTrue(source.contains("override fun stateChanged(state: DiaryRecordingState)"))
        assertTrue(source.contains("override fun stopForeground()"))
        assertTrue(source.contains("override fun stopSelfResult(startId: Int)"))
        assertTrue(source.contains("this@DiaryRecordingService.stopSelfResult(startId)"))
        assertTrue(source.contains("ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE"))
    }

    @Test
    fun persistenceAdapterLivesBehindTheProcessRuntime() {
        val service = source("DiaryRecordingService.kt")
        val persistence = source("DiaryRecordingRepositoryPersistence.kt")

        assertFalse(service.contains("DiaryAttachmentRepository"))
        assertTrue(persistence.contains(": DiaryRecordingPersistence"))
        assertTrue(persistence.contains("attachmentRepository.beginRecording("))
        assertTrue(persistence.contains("attachmentRepository.completeRecording("))
        assertTrue(persistence.contains("attachmentRepository.failRecording("))
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

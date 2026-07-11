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
    fun committedTerminalPersistenceCannotBeWrittenAgainDuringServiceDestruction() {
        val source = source("DiaryRecordingService.kt")
        val destroy = source.substringAfter("override fun onDestroy()")
            .substringBefore("private fun enterForeground")
        val persistence = source.substringAfter("private fun persistPendingRecording()")
            .substringBefore("private fun markPersistenceFailed()")

        assertTrue(destroy.contains("!persistenceCommitted"))
        assertTrue(persistence.contains("persistenceCommitted = true"))
        assertTrue(persistence.contains("pendingPersistence = null"))
    }

    @Test
    fun startRequestsAreClassifiedBeforeForegroundOrRecorderWork() {
        val source = source("DiaryRecordingService.kt")
        val startAction = source.substringAfter("ACTION_START ->").substringBefore("ACTION_PAUSE ->")

        assertTrue(startAction.indexOf("requestStart") in 0 until startAction.indexOf("enterForeground"))
        assertTrue(startAction.contains("DiaryRecordingStartResult.AlreadyActive"))
        assertTrue(startAction.contains("DiaryRecordingStartResult.Busy"))
        assertTrue(startAction.contains("DiaryRecordingErrorCode.RECORDER_BUSY"))
    }

    @Test
    fun foregroundStartFailuresAreMappedAtBothServiceBoundaries() {
        val source = source("DiaryRecordingService.kt")

        assertTrue(source.contains("ForegroundServiceStartNotAllowedException"))
        assertTrue(source.contains("SecurityException"))
        assertTrue(source.contains("FOREGROUND_START_NOT_ALLOWED"))
        assertTrue(source.contains("FOREGROUND_SECURITY_DENIED"))
        assertTrue(source.contains("DiaryRecordingLaunchResult"))
    }

    @Test
    fun foregroundEntryFailureStopsCreatedServiceEvenWhenPersistenceNeedsRetry() {
        val source = source("DiaryRecordingService.kt")
        val acceptedStart = source.substringAfter("DiaryRecordingStartResult.Accepted ->")
            .substringBefore("DiaryRecordingStartResult.AlreadyActive")

        assertTrue(acceptedStart.contains("handleFailure(foregroundError, stopService = true)"))
        assertTrue(source.contains("if (stopService) stopSelf()"))
    }

    @Test
    fun destroyStopsNewActionsThenWaitsForJobsBeforeRecorderCleanup() {
        val source = source("DiaryRecordingService.kt")
        val destroy = source.substringAfter("override fun onDestroy()")
            .substringBefore("private fun enterForeground")

        val closeGate = destroy.indexOf("acceptingActions = false")
        val awaitJobs = destroy.indexOf("cancelAndJoin")
        val lockRecorder = destroy.indexOf("recorderMutex.withLock")
        assertTrue(closeGate in 0 until awaitJobs)
        assertTrue(awaitJobs in 0 until lockRecorder)
    }

    @Test
    fun persistenceFailuresKeepRetryIdentityAndAreLogged() {
        val source = source("DiaryRecordingService.kt")

        assertTrue(source.contains("Log.e("))
        assertTrue(source.contains("pendingPersistence"))
        assertTrue(source.contains("DiaryRecordingErrorCode.PERSIST_FAILED"))
        val completion = source.substringAfter("attachmentRepository.completeRecording(")
            .substringBefore("private fun startTicker")
        assertTrue(completion.indexOf("pendingPersistence = null") in 0 until completion.indexOf("activeAttachmentId = null"))
    }

    @Test
    fun failureMetadataOnlyUsesFilesThatActuallyExist() {
        val source = source("DiaryRecordingService.kt")

        assertTrue(source.contains("takeIf(File::exists)"))
        assertFalse(source.contains("activeOutputFile?.absolutePath.orEmpty()"))
    }

    @Test
    fun attachmentTargetIsValidatedBeforeRecorderStart() {
        val source = source("DiaryRecordingService.kt")
        val start = source.substringAfter("private fun startRecording(")
            .substringBefore("private fun pauseRecording()")

        assertTrue(start.indexOf("attachmentRepository.beginRecording(") in 0 until start.indexOf("recorder.start(output)"))
        assertTrue(start.contains("DiaryAttachmentRecordingTargetException"))
        assertTrue(start.contains("handleInvalidAttachment(error)"))
        assertTrue(source.contains("DiaryRecordingErrorCode.ATTACHMENT_INVALID"))
        val invalidTarget = source.substringAfter("private fun handleInvalidAttachment(")
            .substringBefore("private fun pauseRecording()")
        assertFalse(invalidTarget.contains("failRecording("))
        assertTrue(invalidTarget.contains("store.releaseFailedSession()"))
    }

    @Test
    fun pausedRecordingDoesNotKeepRefreshingTickerNotifications() {
        val source = source("DiaryRecordingService.kt")
        val pause = source.substringAfter("private fun pauseRecording()")
            .substringBefore("private fun resumeRecording()")
        val ticker = source.substringAfter("private fun startTicker(")
            .substringBefore("private fun notifyCurrent()")

        assertTrue(pause.contains("ticker?.cancel()"))
        assertTrue(ticker.contains("is DiaryRecordingState.Recording"))
    }

    @Test
    fun startingStopInvalidatesTokenBeforeRecorderCanPublishOrBeginLate() {
        val source = source("DiaryRecordingService.kt")
        val stopAction = source.substringAfter("ACTION_STOP ->")
            .substringBefore("ACTION_OPEN ->")
        val start = source.substringAfter("private fun startRecording(")
            .substringBefore("private fun pauseRecording()")

        assertTrue(stopAction.contains("startToken++"))
        assertTrue(start.indexOf("token != startToken") in 0 until start.indexOf("recorder.start(output)"))
        assertTrue(start.substringAfter("recorder.start(output)").contains("token != startToken"))
        assertTrue(source.contains("DiaryRecordingErrorCode.START_CANCELLED"))
    }

    @Test
    fun actionsUseOneFifoWorkerInsteadOfCompetingCoroutineLaunches() {
        val source = source("DiaryRecordingService.kt")
        val enqueue = source.substringAfter("private fun enqueueAction(")
            .substringBefore("private fun startRecording(")

        assertTrue(source.contains("Channel.UNLIMITED"))
        assertTrue(source.contains("for (action in actionChannel)"))
        assertTrue(enqueue.contains("actionChannel.trySend(action)"))
        assertFalse(enqueue.contains("scope.launch"))
    }

    @Test
    fun notificationCopyComesFromChineseResourcesWithChannelDescription() {
        val source = source("DiaryRecordingNotification.kt")
        val strings = File("src/main/res/values/strings.xml").readText()

        assertTrue(source.contains("R.string.diary_recording_channel_name"))
        assertTrue(source.contains("R.string.diary_recording_channel_description"))
        assertTrue(source.contains("channel.description"))
        assertTrue(source.contains("R.string.diary_recording_action_pause"))
        assertTrue(strings.contains("语音日记"))
    }

    private fun source(fileName: String): String =
        File("src/main/kotlin/com/dailysatori/core/recording/$fileName").readText()
}

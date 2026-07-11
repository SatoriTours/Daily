package com.dailysatori.core.recording

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiaryRecordingServiceContractTest {
    @Test
    fun serviceExposesAllRecordingActionsAndStartsOnlyFromUserEntryPoint() {
        val source = source("DiaryRecordingService.kt")

        listOf(
            "ACTION_START",
            "ACTION_PAUSE",
            "ACTION_RESUME",
            "ACTION_STOP",
            "ACTION_RETRY_PERSIST",
            "ACTION_OPEN",
        ).forEach {
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
    fun notificationProvidesPauseResumeStopRetryAndOpenDiaryActions() {
        val source = source("DiaryRecordingNotification.kt")

        assertTrue(source.contains("ACTION_PAUSE"))
        assertTrue(source.contains("ACTION_RESUME"))
        assertTrue(source.contains("ACTION_STOP"))
        assertTrue(source.contains("ACTION_RETRY_PERSIST"))
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
        assertTrue(source.contains("foregroundLaunchFailureCode("))
        assertTrue(source.contains("DiaryRecordingLaunchResult"))
    }

    @Test
    fun foregroundEntryFailureStopsCreatedServiceBeforeAnyPersistenceRetry() {
        val source = source("DiaryRecordingService.kt")
        val acceptedStart = source.substringAfter("DiaryRecordingStartResult.Accepted ->")
            .substringBefore("DiaryRecordingStartResult.AlreadyActive")

        assertTrue(acceptedStart.contains("pendingPersistence = PendingPersistence("))
        assertTrue(acceptedStart.contains("stopSelf()"))
        assertTrue(
            acceptedStart.indexOf("pendingPersistence = PendingPersistence(") in
                0 until acceptedStart.indexOf("stopSelf()"),
        )
        assertFalse(acceptedStart.contains("handleFailure(foregroundError"))
    }

    @Test
    fun destroyUsesABoundedCleanupAndOnlyReleasesAfterTimeout() {
        val source = source("DiaryRecordingService.kt")
        val destroy = source.substringAfter("override fun onDestroy()")
            .substringBefore("private fun enterForeground")

        assertTrue(destroy.contains("withTimeoutOrNull(DESTROY_TIMEOUT_MS)"))
        assertTrue(destroy.contains("serviceJob.cancelAndJoin()"))
        assertTrue(destroy.contains("if (!cleanupCompleted)"))
        val timedOut = destroy.substringAfter("if (!cleanupCompleted)")
        assertTrue(timedOut.contains("recorder.releasePreservingOutput()"))
        assertFalse(timedOut.contains("persistPendingRecording()"))
    }

    @Test
    fun persistenceFailuresExecuteBoundedRetryWithoutDetachingForeground() {
        val source = source("DiaryRecordingService.kt")

        assertTrue(source.contains("Log.e("))
        assertTrue(source.contains("pendingPersistence"))
        assertTrue(source.contains("DiaryRecordingErrorCode.PERSIST_FAILED"))
        assertTrue(source.contains("retryDiaryRecordingPersistence"))
        assertTrue(source.contains("ACTION_RETRY_PERSIST"))
        assertFalse(source.contains("STOP_FOREGROUND_DETACH"))
        val success = source.substringAfter("is DiaryRecordingPersistenceResult.Succeeded ->")
            .substringBefore("is DiaryRecordingPersistenceResult.Failed ->")
        assertTrue(success.indexOf("pendingPersistence = null") in 0 until success.indexOf("activeAttachmentId = null"))
    }

    @Test
    fun failureMetadataOnlyUsesUsableFilesOwnedByTheCurrentSession() {
        val source = source("DiaryRecordingService.kt")

        assertTrue(source.contains("session.currentUsableOutput"))
        assertFalse(source.contains("activeOutputFile?.absolutePath.orEmpty()"))
    }

    @Test
    fun attachmentTargetIsValidatedBeforeRecorderStart() {
        val source = source("DiaryRecordingService.kt")
        val start = source.substringAfter("private suspend fun startRecording(")
            .substringBefore("private suspend fun cancelStartingRecording")

        assertTrue(start.indexOf("attachmentRepository.beginRecording(") in 0 until start.indexOf("recorder.start(output)"))
        assertTrue(start.contains("DiaryAttachmentRecordingTargetException"))
        assertTrue(start.contains("handleInvalidAttachment(error)"))
        assertTrue(source.contains("DiaryRecordingErrorCode.ATTACHMENT_INVALID"))
        val invalidTarget = source.substringAfter("private fun handleInvalidAttachment(")
            .substringBefore("private suspend fun pauseRecording()")
        assertFalse(invalidTarget.contains("failRecording("))
        assertTrue(invalidTarget.contains("store.releaseFailedSession()"))
    }

    @Test
    fun pausedRecordingDoesNotKeepRefreshingTickerNotifications() {
        val source = source("DiaryRecordingService.kt")
        val pause = source.substringAfter("private suspend fun pauseRecording()")
            .substringBefore("private suspend fun resumeRecording()")
        val ticker = source.substringAfter("private fun startTicker(")
            .substringBefore("private fun notifyCurrent()")

        assertTrue(pause.contains("ticker?.cancel()"))
        assertTrue(ticker.contains("is DiaryRecordingState.Recording"))
    }

    @Test
    fun startingStopUsesSessionDecisionInsteadOfReportingStartCancelled() {
        val source = source("DiaryRecordingService.kt")
        val stopAction = source.substringAfter("ACTION_STOP ->")
            .substringBefore("ACTION_OPEN ->")
        val start = source.substringAfter("private suspend fun startRecording(")
            .substringBefore("private suspend fun pauseRecording()")

        assertTrue(stopAction.contains("session.requestUserStop()"))
        assertTrue(start.contains("DiaryRecordingStartingStopDecision.CancelWithoutOutput"))
        assertTrue(start.contains("DiaryRecordingStartingStopDecision.FinalizeRecording"))
        assertTrue(start.contains("completeUsableStartingOutput()"))
        assertTrue(
            start.indexOf("session.startingStopDecision()") in
                0 until start.indexOf("session.prepareOutput(output)"),
        )
        assertTrue(source.contains("DiaryRecordingErrorCode.USER_CANCELLED"))
        assertFalse(source.contains("DiaryRecordingErrorCode.START_CANCELLED"))
    }

    @Test
    fun duplicateStartReentersForegroundBeforeReturning() {
        val source = source("DiaryRecordingService.kt")
        val alreadyActive = source.substringAfter("DiaryRecordingStartResult.AlreadyActive ->")
            .substringBefore("DiaryRecordingStartResult.Busy")

        assertTrue(alreadyActive.contains("enterForeground(diaryId)"))
    }

    @Test
    fun servicePreparesSessionOutputBeforeStartingRecorder() {
        val source = source("DiaryRecordingService.kt")
        val start = source.substringAfter("private suspend fun startRecording(")
            .substringBefore("private suspend fun cancelStartingRecording")
        val recorder = source("AndroidDiaryRecorder.kt")

        assertTrue(start.indexOf("session.prepareOutput(output)") in 0 until start.indexOf("recorder.start(output)"))
        assertTrue(start.indexOf("session.markRecorderStartAttempted()") in 0 until start.indexOf("recorder.start(output)"))
        assertTrue(recorder.contains("outputFile.exists() && !outputFile.delete()"))
    }

    @Test
    fun serviceLaunchUsesStableCrossApiFailureMapping() {
        val source = source("DiaryRecordingService.kt")

        assertTrue(source.contains("foregroundLaunchFailureCode("))
        assertTrue(source.contains("Build.VERSION.SDK_INT"))
    }

    @Test
    fun actionsUseOneFifoWorkerInsteadOfCompetingCoroutineLaunches() {
        val source = source("DiaryRecordingService.kt")
        val enqueue = source.substringAfter("private fun enqueueAction(")
            .substringBefore("private suspend fun startRecording(")

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

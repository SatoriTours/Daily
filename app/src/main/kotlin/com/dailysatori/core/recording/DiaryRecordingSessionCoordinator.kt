package com.dailysatori.core.recording

import java.io.File

enum class DiaryRecordingStartingStopDecision {
    ContinueStarting,
    CancelWithoutOutput,
    FinalizeRecording,
}

class DiaryRecordingSessionCoordinator {
    private var outputFile: File? = null
    private var outputPrepared = false
    private var recorderStartAttempted = false
    private var recorderStarted = false
    private var userStopRequested = false

    @Synchronized
    fun prepareOutput(output: File): Boolean {
        outputFile = output
        outputPrepared = false
        recorderStartAttempted = false
        recorderStarted = false
        if (output.exists() && !output.delete()) return false
        outputPrepared = true
        return true
    }

    @Synchronized
    fun markRecorderStartAttempted() {
        check(outputPrepared) { "Recording output must be prepared before recorder start" }
        recorderStartAttempted = true
    }

    @Synchronized
    fun markRecorderStarted() {
        check(recorderStartAttempted) { "Recorder start must be attempted before it can complete" }
        recorderStarted = true
    }

    @Synchronized
    fun requestUserStop() {
        userStopRequested = true
    }

    @Synchronized
    fun startingStopDecision(): DiaryRecordingStartingStopDecision = when {
        !userStopRequested -> DiaryRecordingStartingStopDecision.ContinueStarting
        recorderStarted || currentUsableOutput() != null ->
            DiaryRecordingStartingStopDecision.FinalizeRecording
        else -> DiaryRecordingStartingStopDecision.CancelWithoutOutput
    }

    @Synchronized
    fun currentUsableOutput(candidate: File? = outputFile): File? {
        val expected = outputFile ?: return null
        val actual = candidate ?: return null
        return actual.takeIf {
            outputPrepared &&
                recorderStartAttempted &&
                it.absoluteFile == expected &&
                it.isFile &&
                it.length() > 0
        }
    }

    @Synchronized
    fun recorderHasStarted(): Boolean = recorderStarted
}

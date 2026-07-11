package com.dailysatori.core.recording

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DiaryRecordingSessionCoordinatorTest {
    @Test
    fun startingStopBeforeRecorderStartCancelsWithoutAnOutput() {
        val output = tempOutput()
        val session = DiaryRecordingSessionCoordinator()
        assertTrue(session.prepareOutput(output))

        session.requestUserStop()

        assertEquals(DiaryRecordingStartingStopDecision.CancelWithoutOutput, session.startingStopDecision())
        assertNull(session.currentUsableOutput())
    }

    @Test
    fun startingStopAfterRecorderStartFinalizesTheUsableOutput() {
        val output = tempOutput()
        val session = DiaryRecordingSessionCoordinator()
        assertTrue(session.prepareOutput(output))
        session.markRecorderStartAttempted()
        output.writeBytes(byteArrayOf(1, 2, 3))
        session.markRecorderStarted()

        session.requestUserStop()

        assertEquals(DiaryRecordingStartingStopDecision.FinalizeRecording, session.startingStopDecision())
        assertSame(output, session.currentUsableOutput())
    }

    @Test
    fun startingStopFinalizesUsableOutputEvenWhenRecorderStartDidNotReturn() {
        val output = tempOutput()
        val session = DiaryRecordingSessionCoordinator()
        assertTrue(session.prepareOutput(output))
        session.markRecorderStartAttempted()
        output.writeBytes(byteArrayOf(1, 2, 3))

        session.requestUserStop()

        assertEquals(DiaryRecordingStartingStopDecision.FinalizeRecording, session.startingStopDecision())
        assertFalse(session.recorderHasStarted())
        assertSame(output, session.currentUsableOutput())
    }

    @Test
    fun preparingSessionDeletesAnExistingSameNameOutput() {
        val output = tempOutput().apply { writeText("old recording") }
        val session = DiaryRecordingSessionCoordinator()

        assertTrue(session.prepareOutput(output))

        assertFalse(output.exists())
        assertNull(session.currentUsableOutput())
    }

    @Test
    fun preparingSessionRejectsAnExistingOutputThatCannotBeDeleted() {
        val output = tempOutput().apply {
            mkdirs()
            resolve("child").writeText("not empty")
        }
        val session = DiaryRecordingSessionCoordinator()

        assertFalse(session.prepareOutput(output))

        assertTrue(output.isDirectory)
        assertNull(session.currentUsableOutput())
    }

    @Test
    fun partialOutputMustBeNonEmptyRegularFileCreatedByThisSession() {
        val directory = createTempDirectory("diary-recording-test").toFile()
        val output = File(directory, "voice.m4a")
        val session = DiaryRecordingSessionCoordinator()

        assertTrue(session.prepareOutput(output))
        session.markRecorderStartAttempted()
        assertTrue(output.createNewFile())
        assertNull(session.currentUsableOutput())

        output.writeBytes(byteArrayOf(1))
        assertSame(output, session.currentUsableOutput())

        val anotherSession = DiaryRecordingSessionCoordinator()
        assertNull(anotherSession.currentUsableOutput(output))
    }

    private fun tempOutput(): File =
        File(createTempDirectory("diary-recording-test").toFile(), "voice.m4a")
}

package com.dailysatori.ui.feature.diary

import com.dailysatori.core.recording.DiaryRecordingState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiaryDockStateTest {
    @Test fun handleExpandsWithoutStartingRecordingAndDismissalDoesNotNeedAnotherTap() {
        val idle = DiaryRecordingState.Idle
        val collapsed = DiaryDockState()
        assertEquals(DiaryDockPresentation.Collapsed, collapsed.presentation(idle))
        val expanded = collapsed.reduce(DiaryDockEvent.Expand, idle)
        assertEquals(DiaryDockPresentation.Expanded, expanded.presentation(idle))
        assertTrue(expanded.expanded)
        assertFalse(expanded.reduce(DiaryDockEvent.Dismiss, idle).expanded)
        assertFalse(expanded.reduce(DiaryDockEvent.Scroll, idle).expanded)
        assertFalse(expanded.reduce(DiaryDockEvent.ActionSelected, idle).expanded)
    }

    @Test fun runtimeRecordingAlwaysWinsOverCollapseAndPreventsAnotherStart() {
        val states = listOf(
            DiaryRecordingState.Starting(1, 2),
            DiaryRecordingState.Recording(1, 2, 0),
            DiaryRecordingState.Paused(1, 2, 0),
            DiaryRecordingState.Stopping(1, 2, 0),
            DiaryRecordingState.PersistenceFailed(1, 2, 0, "/local/audio.m4a"),
        )
        for (recording in states) {
            for (event in DiaryDockEvent.entries) {
                val next = DiaryDockState(expanded = true).reduce(event, recording)
                assertEquals(DiaryDockPresentation.Recording, next.presentation(recording))
                assertFalse(next.expanded)
                assertEquals(DiaryDockPresentation.Collapsed, next.presentation(DiaryRecordingState.Idle))
            }
        }
    }

    @Test fun elapsedUpdatesDoNotReopenTheDockAfterFinishing() {
        val state = DiaryDockState(true).reduce(
            DiaryDockEvent.RecordingChanged, DiaryRecordingState.Recording(1, 2, 1000),
        )
        assertEquals(DiaryDockPresentation.Recording, state.presentation(DiaryRecordingState.Stopping(1, 2, 2000)))
        assertEquals(DiaryDockPresentation.Collapsed, state.presentation(DiaryRecordingState.Idle))
    }
}

package com.dailysatori.ui.feature.diary

import com.dailysatori.core.recording.DiaryRecordingState

internal enum class DiaryDockPresentation { Collapsed, Expanded, Recording }
internal enum class DiaryDockEvent { Expand, Dismiss, Scroll, ActionSelected, RecordingChanged }

/** Only expansion is owned here. The recording service remains the sole recording-state owner. */
internal data class DiaryDockState(val expanded: Boolean = false) {
    fun presentation(recording: DiaryRecordingState): DiaryDockPresentation = when {
        recording !is DiaryRecordingState.Idle -> DiaryDockPresentation.Recording
        expanded -> DiaryDockPresentation.Expanded
        else -> DiaryDockPresentation.Collapsed
    }

    fun reduce(event: DiaryDockEvent, recording: DiaryRecordingState): DiaryDockState =
        copy(expanded = recording is DiaryRecordingState.Idle && event == DiaryDockEvent.Expand)
}

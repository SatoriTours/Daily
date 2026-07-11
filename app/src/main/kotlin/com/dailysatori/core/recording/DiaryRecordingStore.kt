package com.dailysatori.core.recording

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DiaryRecordingStore {
    private val mutableState = MutableStateFlow<DiaryRecordingState>(DiaryRecordingState.Idle)
    val state: StateFlow<DiaryRecordingState> = mutableState.asStateFlow()

    internal fun publish(state: DiaryRecordingState) {
        mutableState.value = state
    }
}

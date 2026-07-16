package com.dailysatori.core.recording

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object DiaryRecordingOpenRequest {
    private val mutableDiaryId = MutableStateFlow<Long?>(null)
    val diaryId = mutableDiaryId.asStateFlow()

    fun open(diaryId: Long) {
        if (diaryId > 0) mutableDiaryId.value = diaryId
    }

    fun consume(diaryId: Long) {
        mutableDiaryId.compareAndSet(diaryId, null)
    }
}

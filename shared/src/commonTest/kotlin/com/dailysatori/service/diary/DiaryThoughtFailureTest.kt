package com.dailysatori.service.diary

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiaryThoughtFailureTest {
    @Test
    fun authenticationFailureStopsAutomaticRetriesWithoutExposingResponseBody() {
        val failure = diaryThoughtFailure(IllegalStateException("HTTP 401 unauthorized secret-key diary-content"))
        assertFalse(failure.retryable)
        assertTrue(failure.message.contains("鉴权"))
        assertFalse(failure.message.contains("secret-key"))
        assertFalse(failure.message.contains("diary-content"))
    }

    @Test
    fun invalidEvidenceIsExplainedAndCanRetryWithoutPublishingIt() {
        val failure = diaryThoughtFailure(DiaryThoughtResponseException("思想整理的日记依据不完整，请重试"))
        assertTrue(failure.retryable)
        assertTrue(failure.message.contains("依据不完整"))
    }
}

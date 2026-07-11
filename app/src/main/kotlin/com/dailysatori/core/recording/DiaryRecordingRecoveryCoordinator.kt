package com.dailysatori.core.recording

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

sealed interface DiaryRecordingPersistenceResult {
    val attempts: Int

    data class Succeeded(override val attempts: Int) : DiaryRecordingPersistenceResult

    data class Failed(
        override val attempts: Int,
        val cause: Exception,
    ) : DiaryRecordingPersistenceResult
}

suspend fun retryDiaryRecordingPersistence(
    retryDelaysMs: List<Long> = listOf(1_000, 2_000, 4_000),
    delayBeforeRetry: suspend (Long) -> Unit = { delay(it) },
    persist: () -> Unit,
): DiaryRecordingPersistenceResult {
    var attempts = 0
    retryDelaysMs.forEach { retryDelayMs ->
        attempts++
        try {
            persist()
            return DiaryRecordingPersistenceResult.Succeeded(attempts)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            delayBeforeRetry(retryDelayMs)
        }
    }
    attempts++
    return try {
        persist()
        DiaryRecordingPersistenceResult.Succeeded(attempts)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        DiaryRecordingPersistenceResult.Failed(attempts, error)
    }
}

fun foregroundLaunchFailureCode(
    sdkInt: Int,
    error: Throwable,
    isApi31ForegroundStartDenied: Boolean = false,
): String? = when {
    error is SecurityException -> DiaryRecordingErrorCode.FOREGROUND_SECURITY_DENIED
    sdkInt in 26..30 && error is IllegalStateException ->
        DiaryRecordingErrorCode.FOREGROUND_START_NOT_ALLOWED
    sdkInt >= 31 && isApi31ForegroundStartDenied ->
        DiaryRecordingErrorCode.FOREGROUND_START_NOT_ALLOWED
    else -> null
}

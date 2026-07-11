package com.dailysatori.core.recording

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

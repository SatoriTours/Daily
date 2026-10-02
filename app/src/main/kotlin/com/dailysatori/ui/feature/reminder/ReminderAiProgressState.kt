package com.dailysatori.ui.feature.reminder

enum class ReminderAiStage { QUEUED, ANALYZING, VALIDATING, GENERATING }

data class ReminderAiProgressUi(
    val stage: ReminderAiStage = ReminderAiStage.QUEUED,
    val isRetrying: Boolean = false,
    val retryAtMillis: Long? = null,
) {
    fun retrySecondsRemaining(nowMillis: Long): Long? = retryAtMillis?.let {
        ((it - nowMillis).coerceAtLeast(0) + 999) / 1_000
    }
}

internal fun reminderAiProgress(
    status: String?,
    current: Long = 0,
    total: Long = 0,
    message: String = "",
    retryAtMillis: Long? = null,
): ReminderAiProgressUi {
    val stage = when {
        status != "running" -> ReminderAiStage.QUEUED
        total == 4L -> ReminderAiStage.entries[current.coerceIn(0, 3).toInt()]
        message == "正在校验 AI 返回" -> ReminderAiStage.VALIDATING
        else -> ReminderAiStage.ANALYZING
    }
    return ReminderAiProgressUi(stage, status == "retrying", retryAtMillis.takeIf { status == "retrying" })
}

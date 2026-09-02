package com.dailysatori.service.reminder

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

enum class ReminderAiBatchStatus { PARSING, RUNNING, READY_FOR_CONFIRMATION, PARSE_FAILED, CONFIRMED, DISCARDED }

data class ReminderAiBatchDraft(val sourceIndex: Int, val sourceText: String, val draftJson: String)

data class ReminderAiDraftRecord(
    val sourceIndex: Int, val sourceText: String, val draftJson: String,
    val overrideJson: String = "", val selected: Boolean = true, val discarded: Boolean = false,
    val confirmationState: String = "PENDING", val reminderId: String? = null,
    val confirmed: Boolean, val confirmedAt: Instant?,
)

data class ReminderAiBatch(
    val id: String,
    val parentBatchId: String? = null,
    val originalInput: String,
    val normalizedKey: String,
    val timeZone: TimeZone,
    val localDate: LocalDate,
    val status: ReminderAiBatchStatus,
    val taskId: Long?,
    val attemptCount: Long,
    val maxAttempts: Long,
    val lastAttemptAt: Instant?,
    val errorSummary: String,
    val terminalNotificationAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val drafts: List<ReminderAiDraftRecord>,
)

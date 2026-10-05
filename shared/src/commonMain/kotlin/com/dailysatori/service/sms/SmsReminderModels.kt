package com.dailysatori.service.sms

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable

@Serializable data class SmsSource(val sender: String, val body: String)
enum class SmsSourceStatus { QUEUED, LOCAL_ONLY, PENDING, READY, CREATED, IGNORED, EXPIRED, FAILED }

data class SmsSourceRecord(
    val id: String, val source: SmsSource, val receivedAt: Instant, val zone: TimeZone,
    val status: SmsSourceStatus, val draft: SmsReminderDraft?, val reminderId: String?,
)

@Serializable data class SmsReminderDraft(
    val title: String, val reason: String = "", val deadlineMs: Long? = null,
    val estimated: Boolean = false, val category: String = "other",
)

/** Constructor is private so adapters cannot accidentally accept raw SMS text. */
class SmsAiInput private constructor(val text: String, val deadlines: List<SmsDeadline>) {
    companion object {
        fun from(source: SmsSource, receivedAt: Instant, zone: TimeZone): SmsAiInput? {
            val text = SmsPrivacy.aiText(source.body) ?: return null
            return SmsAiInput(text, SmsDeadlineExtractor.extract(source.body, receivedAt, zone))
        }
    }
}

@Serializable data class SmsAiResult(
    val actionable: Boolean, val category: String, val title: String,
    val reason: String, val evidence: String, val deadlineIndex: Int? = null,
)

fun interface SmsReminderRemote { suspend fun analyze(input: SmsAiInput): SmsAiResult }

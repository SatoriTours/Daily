package com.dailysatori.service.sms

import kotlinx.datetime.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

data class SmsDeadline(val at: Instant, val estimated: Boolean)

/** Dates and durations are computed locally; no source numbers are sent to AI. */
object SmsDeadlineExtractor {
    private val duration = Regex("(?i)(?:within\\s+(?:the\\s+next\\s+)?|在\\s*|未来\\s*|未來\\s*|接下来\\s*)(\\d{1,3})\\s*(hours?|hrs?|minutes?|mins?|days?|小时|小時|分钟|分鐘|天)(?:\\s*内)?")
    private val chineseDuration = Regex("(\\d{1,3})\\s*(小时|小時|分钟|分鐘|天)内")
    private val dated = Regex("(?i)(?:before|by|截止(?:时间)?[:：]?|请于|請於)\\s*(\\d{4})[-/年](\\d{1,2})[-/月](\\d{1,2})日?\\s+(\\d{1,2})[:：](\\d{2})")

    fun extract(text: String, receivedAt: Instant, zone: TimeZone): List<SmsDeadline> {
        if (text.isBlank() || text.length > 8_000 || SmsPrivacy.isVerification(text)) return emptyList()
        val relative = (duration.findAll(text) + chineseDuration.findAll(text)).mapNotNull { match ->
            val count = match.groupValues[1].toIntOrNull()?.takeIf { it in 1..720 } ?: return@mapNotNull null
            val unit = match.groupValues[2].lowercase()
            val delta = when {
                unit.startsWith("min") || unit.startsWith("分") -> count.minutes
                unit.startsWith("day") || unit == "天" -> (count * 24).hours
                else -> count.hours
            }
            SmsDeadline(receivedAt + delta, estimated = true)
        }
        val absolute = dated.findAll(text).mapNotNull { match ->
            runCatching {
                val g = match.groupValues.drop(1).map(String::toInt)
                SmsDeadline(LocalDateTime(g[0], g[1], g[2], g[3], g[4]).toInstant(zone), estimated = false)
            }.getOrNull()
        }
        return (relative + absolute).distinctBy { it.at }.take(8).toList()
    }
}

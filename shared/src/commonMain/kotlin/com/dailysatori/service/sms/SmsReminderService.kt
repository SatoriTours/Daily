package com.dailysatori.service.sms

import com.dailysatori.data.repository.*
import com.dailysatori.service.reminder.*
import kotlinx.datetime.*

class SmsReminderService(
    private val sources: SmsSourceRepository, private val reminders: ReminderRepository,
    private val settings: SettingRepository, private val remote: SmsReminderRemote,
    private val clock: Clock = Clock.System,
) {
    fun accept(id: String, source: SmsSource, received: Instant, zone: TimeZone, manual: Boolean = false): Long? {
        require(id.matches(Regex("[a-zA-Z0-9_-]{1,80}")))
        if (source.body.isBlank() || source.body.length > 8_000 || SmsPrivacy.isVerification(source.body) || isBlocked(source.sender)) return null
        if (!manual && !SmsPrivacy.isCandidate(source.body)) return null
        val eligible = settings.get(CLOUD_KEY) == "true" && SmsAiInput.from(source, received, zone)?.text?.let(SmsPrivacy::isCandidate) == true
        val task = sources.accept(id, source, received, zone, eligible)
        if (sources.get(id)?.status == SmsSourceStatus.LOCAL_ONLY) createLocal(id)
        return task
    }

    suspend fun process(id: String): SmsSourceRecord? {
        val row = sources.get(id) ?: return null
        if (row.status != SmsSourceStatus.QUEUED) return row
        if (settings.get(CLOUD_KEY) != "true" || isBlocked(row.source.sender)) {
            sources.update(id, SmsSourceStatus.LOCAL_ONLY)
            return sources.get(id)
        }
        val input = SmsAiInput.from(row.source, row.receivedAt, row.zone)
        if (input == null) { sources.update(id, SmsSourceStatus.LOCAL_ONLY); return sources.get(id) }
        val result = remote.analyze(input)
        // Re-check after the request: disabling/blocking/ignoring must invalidate in-flight results.
        if (settings.get(CLOUD_KEY) != "true" || isBlocked(row.source.sender) || sources.get(id)?.status != SmsSourceStatus.QUEUED) return sources.get(id)
        applyResult(row, input, result)
        return sources.get(id)
    }

    private fun applyResult(row: SmsSourceRecord, input: SmsAiInput, result: SmsAiResult) {
        if (!result.actionable) { sources.update(row.id, SmsSourceStatus.IGNORED); return }
        val draft = validatedDraft(input, result)
        sources.transaction {
            if (sources.get(row.id)?.status != SmsSourceStatus.QUEUED) return@transaction
            if (settings.get(CLOUD_KEY) != "true" || isBlocked(row.source.sender)) {
                sources.update(row.id, SmsSourceStatus.LOCAL_ONLY)
                return@transaction
            }
            sources.update(row.id, SmsSourceStatus.READY, draft)
            confirm(row.id, draft)
        }
    }

    suspend fun analyzeDraft(source: SmsSource, received: Instant, zone: TimeZone): SmsReminderDraft? {
        val input = requireNotNull(SmsAiInput.from(source, received, zone))
        val result = remote.analyze(input)
        return if (result.actionable) validatedDraft(input, result) else null
    }

    fun stageLocal(id: String, source: SmsSource, received: Instant, zone: TimeZone, draft: SmsReminderDraft) {
        sources.accept(id, source, received, zone, cloudEligible = false)
        if (sources.get(id)?.status !in setOf(SmsSourceStatus.CREATED, SmsSourceStatus.IGNORED))
            sources.update(id, SmsSourceStatus.PENDING, draft)
    }

    private fun validatedDraft(input: SmsAiInput, result: SmsAiResult): SmsReminderDraft {
        require(result.title.isNotBlank() && result.title.length <= 300 && result.reason.length <= 500)
        require(result.evidence.isNotBlank() && input.text.contains(result.evidence))
        require(result.category in categories && result.title.none(Char::isDigit) && result.reason.none(Char::isDigit))
        require(SmsPrivacy.aiText(result.title) == result.title && SmsPrivacy.aiText(result.reason.ifBlank { "reason" }) == result.reason.ifBlank { "reason" })
        val selected = result.deadlineIndex?.let { input.deadlines.getOrNull(it) ?: error("Invalid deadline index") }
        val deadline = selected?.takeIf { input.deadlines.size == 1 && it.at > clock.now() }
        return SmsReminderDraft(result.title, result.reason, deadline?.at?.toEpochMilliseconds(), deadline?.estimated ?: false, result.category)
    }

    /** Local rules produce generic titles; private text never becomes reminder content. */
    fun createLocal(id: String): String? = sources.transaction {
        val row = sources.get(id) ?: return@transaction null
        if (row.status == SmsSourceStatus.CREATED) return@transaction row.reminderId
        if (row.status == SmsSourceStatus.IGNORED || isBlocked(row.source.sender)) return@transaction null
        if (!SmsPrivacy.isCandidate(row.source.body)) { ignore(id); return@transaction null }
        val deadline = SmsDeadlineExtractor.extract(row.source.body, row.receivedAt, row.zone).singleOrNull()?.takeIf { it.at > clock.now() }
        val draft = row.draft ?: localDraft(row.source.body)
        confirm(id, draft.copy(deadlineMs = deadline?.at?.toEpochMilliseconds(), estimated = deadline?.estimated ?: false))
    }

    fun localDraft(body: String): SmsReminderDraft {
        val category = localActions.firstOrNull { it.second.containsMatchIn(body) }?.first ?: "other"
        val english = settings.get("app_language") == "en"
        val title = when (category) {
            "top_up" -> if (english) "Top up phone account" else "为手机账户充值"
            "payment" -> if (english) "Pay outstanding bill" else "完成账单缴费"
            "renewal" -> if (english) "Renew service" else "续费服务"
            "pickup" -> if (english) "Collect parcel" else "领取包裹"
            "appointment" -> if (english) "Attend appointment" else "处理预约事项"
            else -> if (english) "Handle message action" else "处理信息中的事项"
        }
        return SmsReminderDraft(title, category = category)
    }

    private fun isDuplicate(row: SmsSourceRecord, draft: SmsReminderDraft): Boolean = sources.all().any {
        it.id != row.id && it.reminderId != null && it.source.sender.equals(row.source.sender, true) &&
            (it.source.body == row.source.body || draft.deadlineMs != null && it.draft?.title == draft.title && it.draft.deadlineMs == draft.deadlineMs) &&
            reminders.get(it.reminderId)?.status !in setOf(null, ReminderStatus.COMPLETED, ReminderStatus.EXPIRED)
    }

    /** Creates an active reminder from an entry the user moved out of the ledger. */
    fun createFromLedger(entryId: String, content: String, zone: TimeZone): String = sources.transaction {
        val now = clock.now().toLocalDateTime(zone)
        val reminderId = "ledger:$entryId"
        reminders.createConfirmedOnce(ReminderDraft(reminderId, content, now.date, now.date, now.time, timeZone = zone), defaultProfile())
        reminderId
    }

    fun confirm(id: String, draft: SmsReminderDraft, checkDuplicates: Boolean = true): String? = sources.transaction {
        val row = sources.get(id) ?: return@transaction null
        if (row.status == SmsSourceStatus.CREATED) return@transaction row.reminderId
        if (row.status == SmsSourceStatus.IGNORED) return@transaction null
        if (checkDuplicates && isDuplicate(row, draft)) { ignore(id); return@transaction null }
        val deadline = draft.deadlineMs?.let(Instant::fromEpochMilliseconds)?.takeIf { it > clock.now() }
        require(draft.title.isNotBlank())
        val now = clock.now().toLocalDateTime(row.zone)
        val content = listOf(draft.title, draft.reason.takeIf(String::isNotBlank)).filterNotNull().joinToString("\n")
        val reminderId = "sms:$id"
        reminders.createConfirmedOnce(ReminderDraft(reminderId, content, now.date, deadline?.toLocalDateTime(row.zone)?.date ?: now.date,
            now.time, timeZone = row.zone, deadlineAt = deadline), defaultProfile())
        // Reminders without a deadline stay active: intake writes results in by default and the
        // user deletes or moves anything that was captured by mistake.
        val count = settings.get(CREATED_COUNT_KEY)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        settings.upsert(CREATED_COUNT_KEY, (count + 1).toString())
        sources.update(id, SmsSourceStatus.CREATED, draft.copy(deadlineMs = deadline?.toEpochMilliseconds()), reminderId)
        reminderId
    }

    fun retry(id: String): Long? = sources.transaction {
        val row = sources.get(id) ?: return@transaction null
        if (row.status !in setOf(SmsSourceStatus.FAILED, SmsSourceStatus.LOCAL_ONLY) || settings.get(CLOUD_KEY) != "true" || isBlocked(row.source.sender) || SmsAiInput.from(row.source, row.receivedAt, row.zone) == null) return@transaction null
        sources.update(id, SmsSourceStatus.QUEUED)
        sources.resetNotification(id)
        sources.enqueue(id)
    }

    fun ignore(id: String) = sources.update(id, SmsSourceStatus.IGNORED)
    fun setCloudAllowed(allowed: Boolean) = sources.transaction {
        settings.upsert(CLOUD_KEY, allowed.toString())
        if (!allowed) sources.all().filter { it.status == SmsSourceStatus.QUEUED }.forEach { sources.update(it.id, SmsSourceStatus.LOCAL_ONLY) }
    }
    fun blockSender(sender: String) {
        val blocked = blockedSenders() + sender.trim().lowercase()
        sources.saveBlockedSenders(blocked)
        sources.all().filter { it.source.sender.equals(sender, true) && it.status != SmsSourceStatus.CREATED }.forEach { ignore(it.id) }
    }
    fun clearBlockedSenders() = settings.delete(BLOCKED_KEY)
    private fun isBlocked(sender: String) = sender.trim().lowercase() in blockedSenders()
    private fun blockedSenders(): Set<String> = sources.blockedSenders()

    private fun defaultProfile(): ReminderProfileSnapshot {
        val id = settings.get("reminder.default_profile") ?: "builtin-standard"
        val profile = reminders.getProfile(id)?.snapshot ?: when (id) {
            "builtin-strong" -> ReminderProfileSnapshot.strong()
            "builtin-gentle" -> ReminderProfileSnapshot.gentle()
            else -> ReminderProfileSnapshot.standard()
        }
        fun time(key: String, fallback: LocalTime) = settings.get(key)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: fallback
        return profile.copy(sleepStart = time("reminder.sleep_start", profile.sleepStart), sleepEnd = time("reminder.sleep_end", profile.sleepEnd),
            soundEnabled = settings.get("reminder.sound")?.toBooleanStrictOrNull() ?: profile.soundEnabled,
            vibrationEnabled = settings.get("reminder.vibration")?.toBooleanStrictOrNull() ?: profile.vibrationEnabled,
            lockScreenVisibility = ReminderLockScreenVisibility.PRIVATE)
    }

    companion object {
        const val ENABLED_KEY = "sms_reminder.enabled"
        const val CLOUD_KEY = "sms_reminder.cloud_allowed"
        const val BLOCKED_KEY = "sms_reminder.blocked_senders"
        const val CREATED_COUNT_KEY = "sms_reminder.created_count"
        private val localActions = listOf(
            "top_up" to Regex("(?i)充值|top[ -]?up"), "payment" to Regex("(?i)缴费|繳費|还款|還款|付款|支付|\\bpay(?:ment)?\\b|repay"),
            "renewal" to Regex("(?i)续费|續費|renew"), "pickup" to Regex("(?i)取件|取货|取貨|提货|提貨|领取|領取|collect|pick[ -]?up"),
            "appointment" to Regex("(?i)预约|預約|appointment"),
        )
        private val categories = setOf("top_up", "payment", "renewal", "pickup", "appointment", "other")
    }
}

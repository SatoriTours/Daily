package com.dailysatori.ui.feature.phone

import com.dailysatori.bookkeeping.*
import com.dailysatori.service.phone.*
import com.dailysatori.service.reminder.Reminder
import com.dailysatori.service.sms.SmsSourceRecord

internal data class PhoneSourceInfo(val messageId: String?, val channel: PhoneChannel, val origin: String,
    val text: String, val receivedAt: Long, val zone: String, val erased: Boolean)
internal enum class PhoneTargetStatus { LOADING, READY, MISSING }
internal data class PhoneMessageActions(val retry: Boolean, val reprocess: Boolean, val aiReprocess: Boolean, val addLedger: Boolean)

private fun ledgerMatches(row: PhoneMessage, entry: LedgerEntry): Boolean {
    val source = if (row.event.channel == PhoneChannel.SMS) "sms:${row.event.origin.take(196)}" else row.event.origin
    return row.ledgerId == entry.id || (entry.source == source && row.id in entry.eventKeys)
}

internal fun phoneLinkedLedger(row: PhoneMessage, entries: List<LedgerEntry>): List<LedgerEntry> = entries
    .filter { it.status in setOf(LedgerStatus.POSTED, LedgerStatus.PENDING) && ledgerMatches(row, it) }
    .distinctBy { it.id }

internal fun phoneLinkedReminders(row: PhoneMessage, reminders: List<Reminder>): List<Reminder> {
    val ids = row.todos.filter { it.state == PhoneResultState.DONE }.map { it.reminderId }.filter(String::isNotBlank).toSet()
    return reminders.filter { it.id in ids }.distinctBy { it.id }
}

private fun PhoneMessage.sourceInfo() = PhoneSourceInfo(id, event.channel, event.origin,
    if (textErased) "" else event.text, event.receivedAt, event.zone, textErased)

internal fun phoneLedgerSources(entry: LedgerEntry, rows: List<PhoneMessage>): List<PhoneSourceInfo> = rows
    .filter { ledgerMatches(it, entry) }.distinctBy { it.id }.map { it.sourceInfo() }

internal fun phoneReminderSources(id: String, rows: List<PhoneMessage>, legacy: List<SmsSourceRecord>): List<PhoneSourceInfo> {
    val canonical = rows.filter { row -> row.todos.any { it.reminderId == id } }.distinctBy { it.id }
    if (canonical.isNotEmpty()) return canonical.map { it.sourceInfo() }
    return legacy.filter { it.reminderId == id }.distinctBy { it.id }.map {
        PhoneSourceInfo(null, PhoneChannel.SMS, it.source.sender, it.source.body,
            it.receivedAt.toEpochMilliseconds(), it.zone.id, it.source.body.isBlank())
    }
}

internal fun phoneHistoryRows(history: List<PhoneMessage>, all: List<PhoneMessage>, target: String?): List<PhoneMessage> {
    if (target == null || history.any { it.id == target }) return history
    val row = all.firstOrNull { it.id == target } ?: return history
    return listOf(row) + history
}

internal fun phoneHistoryTarget(target: String, loaded: Boolean, rows: List<PhoneMessage>): PhoneTargetStatus = when {
    !loaded -> PhoneTargetStatus.LOADING
    rows.any { it.id == target } -> PhoneTargetStatus.READY
    else -> PhoneTargetStatus.MISSING
}

internal fun phoneLinkedReminderDate(reminder: Reminder): String? = reminder.deadlineAt?.let {
    phoneDisplayTime(it.toEpochMilliseconds(), reminder.timeZone.id)
} ?: reminder.endDate.toString().drop(5).takeUnless { reminder.id.startsWith("sms:") }

internal fun phoneMessageActions(row: PhoneMessage, cloud: Boolean): PhoneMessageActions {
    val retry = row.todos.any { it.state in setOf(PhoneResultState.FAILED, PhoneResultState.QUEUED) } ||
        row.ledgerState in setOf(PhoneResultState.FAILED, PhoneResultState.QUEUED)
    val reprocess = !row.textErased && row.todos.none { it.state == PhoneResultState.DONE } &&
        row.ledgerId.isEmpty() && row.ledgerState != PhoneResultState.QUEUED
    return PhoneMessageActions(retry, reprocess, reprocess && cloud, row.ledgerId.isEmpty())
}

package com.dailysatori.ui.feature.phone

import com.dailysatori.bookkeeping.*
import com.dailysatori.service.phone.*
import com.dailysatori.service.reminder.*
import com.dailysatori.service.sms.*
import kotlinx.datetime.*
import kotlin.test.*

class PhoneLinksTest {
    @Test fun mergedLedgerIsVisibleFromEverySourceAndDeletedEntriesAreExcluded() {
        val first = message("first")
        val second = message("second").copy(ledgerId = "merged")
        val entry = ledger("merged", listOf(first.id, second.id))
        assertEquals(listOf("merged"), phoneLinkedLedger(first, listOf(entry)).map { it.id })
        assertEquals(listOf("merged"), phoneLinkedLedger(second, listOf(entry)).map { it.id })
        assertTrue(phoneLinkedLedger(first, listOf(entry.copy(status = LedgerStatus.DELETED))).isEmpty())
        assertTrue(phoneLinkedLedger(first, listOf(entry.copy(status = LedgerStatus.IGNORED))).isEmpty())
        assertTrue(phoneLinkedLedger(first, listOf(entry.copy(source = "sms:other"))).isEmpty())
        assertTrue(phoneLinkedLedger(message("unrelated"), listOf(entry)).isEmpty())
    }

    @Test fun ledgerShowsAllSourcesAndNeverRestoresErasedTextFromItsCopy() {
        val first = message("first")
        val second = message("second").copy(textErased = true)
        val entry = ledger("merged", listOf(first.id, second.id)).copy(text = "stale private text")
        val sources = phoneLedgerSources(entry, listOf(first, second, message("other")))
        assertEquals(listOf(first.id, second.id), sources.map { it.messageId })
        assertEquals("source first", sources.first().text)
        assertEquals("", sources.last().text)
        assertTrue(sources.last().erased)
    }

    @Test fun remindersUseCurrentContentAndDeletedRemindersDoNotProduceDeadLinks() {
        val row = message("todo").copy(todos = listOf(
            PhoneTodo("one", "old title", PhoneResultState.DONE, reminderId = "sms:one"),
            PhoneTodo("two", "deleted", PhoneResultState.DONE, reminderId = "sms:two"),
            PhoneTodo("duplicate", "same", PhoneResultState.DONE, reminderId = "sms:one"),
        ))
        val linked = phoneLinkedReminders(row, listOf(reminder("sms:one", "edited title")))
        assertEquals(listOf("sms:one"), linked.map { it.id })
        assertEquals("edited title", linked.single().content)
    }

    @Test fun canonicalReminderSourceOverridesLegacyTextEvenAfterErasure() {
        val row = message("todo").copy(textErased = true,
            todos = listOf(PhoneTodo("one", "stale todo text", PhoneResultState.DONE, reminderId = "sms:one")))
        val legacy = SmsSourceRecord("one", SmsSource("95533", "legacy private body"),
            Instant.fromEpochMilliseconds(1000), TimeZone.UTC, SmsSourceStatus.CREATED, null, "sms:one")
        val sources = phoneReminderSources("sms:one", listOf(row), listOf(legacy))
        assertEquals(1, sources.size)
        assertEquals(row.id, sources.single().messageId)
        assertEquals("", sources.single().text)
        assertTrue(sources.single().erased)
        val fallback = phoneReminderSources("sms:one", emptyList(), listOf(legacy))
        assertEquals("legacy private body", fallback.single().text)
        assertNull(fallback.single().messageId)
    }

    @Test fun oldHistoryTargetIsIncludedWithoutDuplicatingOrReplacingRecentHistory() {
        val recent = message("recent")
        val old = message("old")
        assertEquals(listOf(old.id, recent.id), phoneHistoryRows(listOf(recent), listOf(recent, old), old.id).map { it.id })
        assertEquals(listOf(recent.id), phoneHistoryRows(listOf(recent), listOf(recent, old), recent.id).map { it.id })
        assertEquals(listOf(recent.id), phoneHistoryRows(listOf(recent), listOf(recent), "missing").map { it.id })
    }

    @Test fun historyTargetIsNotMarkedMissingWhileSourcesAreStillLoading() {
        assertEquals(PhoneTargetStatus.LOADING, phoneHistoryTarget("old", false, emptyList()))
        assertEquals(PhoneTargetStatus.MISSING, phoneHistoryTarget("old", true, emptyList()))
        assertEquals(PhoneTargetStatus.READY, phoneHistoryTarget("sms:old", true, listOf(message("old"))))
    }

    @Test fun erasedAndPreviouslyProcessedMessagesCannotOfferReprocessing() {
        val original = message("original")
        assertTrue(phoneMessageActions(original, true).reprocess)
        assertTrue(phoneMessageActions(original, true).aiReprocess)
        assertFalse(phoneMessageActions(original, false).aiReprocess)
        assertFalse(phoneMessageActions(original.copy(textErased = true), true).reprocess)
        assertFalse(phoneMessageActions(original.copy(ledgerState = PhoneResultState.QUEUED), true).reprocess)
        assertFalse(phoneMessageActions(original.copy(ledgerId = "posted"), true).addLedger)
        assertTrue(phoneMessageActions(original.copy(ledgerState = PhoneResultState.FAILED), true).retry)
    }

    @Test fun sourceTodoWithoutDeadlineDoesNotPretendItsCreationDateIsADueDate() {
        assertNull(phoneLinkedReminderDate(reminder("sms:one", "pay")))
    }

    @Test fun linkedDeadlineUsesTheRemindersTimeZone() {
        val task = reminder("sms:one", "pay").copy(deadlineAt = Instant.parse("2026-10-25T08:30:00Z"),
            timeZone = TimeZone.of("Asia/Shanghai"))
        assertEquals("10-25 16:30", phoneLinkedReminderDate(task))
    }

    private fun message(key: String) = PhoneMessage(
        PhoneEvent(PhoneChannel.SMS, "95533", key, "", "source $key", 1000, "UTC"), generation = 0)
    private fun ledger(id: String, keys: List<String>) = LedgerEntry(id, keys, "sms:95533", "copied body", 1000,
        amountMinor = 2800, kind = LedgerKind.EXPENSE, merchant = "Shop", status = LedgerStatus.POSTED, reason = "")
    private fun reminder(id: String, title: String) = Reminder(id, title, LocalDate(2026, 10, 1), LocalDate(2026, 10, 25),
        LocalTime(9, 0), ReminderActiveDayRule.Daily, ReminderProfileSnapshot.standard(),
        ReminderStatus.ACTIVE, TimeZone.UTC, 1)
}

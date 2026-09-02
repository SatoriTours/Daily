package com.dailysatori.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.service.reminder.ReminderAiBatchDraft
import com.dailysatori.service.reminder.ReminderAiBatchStatus
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReminderAiBatchRepositoryTest {
    @Test
    fun enqueueOrReuseDeduplicatesActiveNormalizedInputForSameDateAndZone() = withRepository { repo ->
        val first = repo.enqueueOrReuse("  Call   Mom ", TimeZone.UTC, LocalDate(2026, 9, 2))
        val duplicate = repo.enqueueOrReuse("Call Mom", TimeZone.UTC, LocalDate(2026, 9, 2))
        val differentZone = repo.enqueueOrReuse("Call Mom", TimeZone.of("Asia/Hong_Kong"), LocalDate(2026, 9, 2))

        assertEquals(first.id, duplicate.id)
        assertEquals("  Call   Mom ", duplicate.originalInput)
        assertFalse(first.id == differentZone.id)
    }

    @Test
    fun markReadyReplacesDraftsInSourceOrderAndCanBeObserved() = withRepository { repo ->
        val batch = repo.enqueueOrReuse("first\nsecond", TimeZone.UTC, LocalDate(2026, 9, 2))

        repo.markReady(
            batch.id,
            listOf(
                ReminderAiBatchDraft(2, "second", "{\"content\":\"second\"}"),
                ReminderAiBatchDraft(1, "first", "{\"content\":\"first\"}"),
            ),
        )

        val stored = assertNotNull(repo.getBatch(batch.id))
        assertEquals(ReminderAiBatchStatus.READY_FOR_CONFIRMATION, stored.status)
        assertEquals(listOf(1, 2), stored.drafts.map { it.sourceIndex })
        assertEquals("first", stored.drafts.first().sourceText)
        assertEquals(stored, runBlocking { repo.observeBatch(batch.id).first() })
    }

    @Test
    fun markFailedRetainsOriginalInputAndTerminalNotificationIsClaimedOnce() = withRepository { repo ->
        val batch = repo.enqueueOrReuse("  do not lose this  ", TimeZone.UTC, LocalDate(2026, 9, 2))

        repo.markFailed(batch.id, "AI unavailable")

        val failed = assertNotNull(repo.getBatch(batch.id))
        assertEquals(ReminderAiBatchStatus.PARSE_FAILED, failed.status)
        assertEquals("  do not lose this  ", failed.originalInput)
        assertTrue(repo.claimTerminalNotification(batch.id))
        assertFalse(repo.claimTerminalNotification(batch.id))
    }

    @Test
    fun markConfirmedMarksSelectedDraftsAndBatch() = withRepository { repo ->
        val batch = repo.enqueueOrReuse("one\ntwo", TimeZone.UTC, LocalDate(2026, 9, 2))
        repo.markReady(batch.id, listOf(
            ReminderAiBatchDraft(0, "one", "{}"),
            ReminderAiBatchDraft(1, "two", "{}"),
        ))

        assertTrue(repo.markConfirmed(batch.id, setOf(1)))

        val confirmed = assertNotNull(repo.getBatch(batch.id))
        assertEquals(ReminderAiBatchStatus.CONFIRMED, confirmed.status)
        assertEquals(listOf(false, true), confirmed.drafts.map { it.confirmed })
    }

    @Test
    fun staleTerminalResultsDoNotOverwriteConfirmedBatchOrDrafts() = withRepository { repo ->
        val batch = repo.enqueueOrReuse("original", TimeZone.UTC, LocalDate(2026, 9, 2))
        repo.markReady(batch.id, listOf(ReminderAiBatchDraft(0, "original", "{\"version\":1}")))
        repo.markConfirmed(batch.id, setOf(0))

        assertFalse(repo.markReady(batch.id, listOf(ReminderAiBatchDraft(0, "stale", "{\"version\":2}"))))
        assertFalse(repo.markFailed(batch.id, "stale worker failure"))

        val stored = assertNotNull(repo.getBatch(batch.id))
        assertEquals(ReminderAiBatchStatus.CONFIRMED, stored.status)
        assertEquals(listOf("original"), stored.drafts.map { it.sourceText })
    }

    @Test
    fun staleFailureDoesNotOverwriteReadyBatch() = withRepository { repo ->
        val batch = repo.enqueueOrReuse("original", TimeZone.UTC, LocalDate(2026, 9, 2))
        repo.markReady(batch.id, listOf(ReminderAiBatchDraft(0, "original", "{}")))

        assertFalse(repo.markFailed(batch.id, "stale worker failure"))
        assertEquals(ReminderAiBatchStatus.READY_FOR_CONFIRMATION, repo.getBatch(batch.id)?.status)
    }

    @Test
    fun failedRetryCreatesImmutableSuccessorAndRetainsTerminalNotification() = withRepository { repo ->
        val failed = repo.enqueueOrReuse("keep failure", TimeZone.UTC, LocalDate(2026, 9, 2))
        repo.markFailed(failed.id, "offline")
        assertTrue(repo.claimTerminalNotification(failed.id))

        val successor = assertNotNull(repo.createRetrySuccessor(failed.id))

        assertFalse(successor.id == failed.id)
        assertEquals(ReminderAiBatchStatus.PARSE_FAILED, repo.getBatch(failed.id)?.status)
        assertEquals(failed.id, successor.parentBatchId)
        assertTrue(repo.getBatch(failed.id)?.terminalNotificationAt != null)
    }

    @Test
    fun submissionAtomicallyCreatesAndReusesAttachedTask() = withRepository { repo ->
        fun submit() = repo.submitOrReuseWithTask(
            "pay card", TimeZone.UTC, LocalDate(2026, 9, 2), "reminder_ai_parse",
            payloadForBatch = { "{\"batchId\":\"$it\"}" },
            uniqueKeyForBatch = { "reminder_ai_parse:$it" },
        )

        val first = submit()
        val duplicate = submit()

        assertEquals(first.batch.id, duplicate.batch.id)
        assertEquals(first.taskId, duplicate.taskId)
        assertEquals(first.taskId, repo.getBatch(first.batch.id)?.taskId)
    }

    @Test
    fun draftUiAndPerDraftConfirmationStateSurviveReload() = withRepository { repo ->
        val batch = repo.enqueueOrReuse("one\ntwo", TimeZone.UTC, LocalDate(2026, 9, 2))
        repo.markReady(batch.id, listOf(ReminderAiBatchDraft(0, "one", "{}"), ReminderAiBatchDraft(1, "two", "{}")))

        repo.updateDraftUiState(batch.id, 0, "edited-json", selected = false, discarded = true)
        repo.markDraftSchedulingFailed(batch.id, 1, "reminder-1")

        val reloaded = assertNotNull(repo.getBatch(batch.id))
        assertEquals("edited-json", reloaded.drafts[0].overrideJson)
        assertFalse(reloaded.drafts[0].selected)
        assertTrue(reloaded.drafts[0].discarded)
        assertEquals("SCHEDULING_FAILED", reloaded.drafts[1].confirmationState)
        assertEquals("reminder-1", reloaded.drafts[1].reminderId)
    }

    private fun withRepository(block: (ReminderAiBatchRepository) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            block(ReminderAiBatchRepository(DailySatoriDatabase(driver)))
        } finally {
            driver.close()
        }
    }
}

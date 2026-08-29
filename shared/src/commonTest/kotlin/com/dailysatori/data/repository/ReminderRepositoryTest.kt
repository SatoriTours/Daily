package com.dailysatori.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.service.reminder.ReminderActiveDayRule
import com.dailysatori.service.reminder.ReminderDraft
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import com.dailysatori.service.reminder.ReminderStatus
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderRepositoryTest {
    private val now = Instant.parse("2026-08-30T10:00:00Z")

    @Test
    fun confirmationPersistsOnlyCompleteDraftAndItsProfileSnapshot() = withRepository { repo ->
        val saved = repo.createConfirmed(draft(), strongProfile())

        assertEquals(ReminderStatus.ACTIVE, saved.status)
        assertEquals(strongProfile(), repo.get(saved.id)!!.profile)
    }

    @Test
    fun terminalRemindersAreExcludedFromActiveQuery() = withRepository { repo ->
        val active = repo.createConfirmed(draft(id = "active"), strongProfile())
        val completed = repo.createConfirmed(draft(id = "completed"), strongProfile())
        repo.complete(completed.id)

        assertEquals(listOf(active.id), repo.activeAt(now).map { it.id })
    }

    @Test
    fun dismissalBackoffResetsOnANewLocalDay() = withRepository { repo ->
        val reminder = repo.createConfirmed(draft(), strongProfile())
        repo.markDismissed(reminder.id, reminder.version, now)
        val dismissed = repo.get(reminder.id)!!
        repo.markDismissed(reminder.id, dismissed.version, Instant.parse("2026-08-31T10:00:00Z"))

        assertEquals(1, repo.state(reminder.id)!!.dismissalCount)
    }

    @Test
    fun completionWinsAgainstStaleDelivery() = withRepository { repo ->
        val reminder = repo.createConfirmed(draft(), strongProfile())
        repo.complete(reminder.id)

        assertFalse(repo.markDelivered(reminder.id, reminder.version, now))
        assertEquals(ReminderStatus.COMPLETED, repo.get(reminder.id)!!.status)
    }

    @Test
    fun staleNotificationCannotCompleteANewerGeneration() = withRepository { repo ->
        val reminder = repo.createConfirmed(draft(id = "stale-complete"), strongProfile())
        assertTrue(repo.markDelivered(reminder.id, reminder.version, now))

        assertFalse(repo.complete(reminder.id, reminder.version, now))
        assertEquals(ReminderStatus.NOTIFIED, repo.get(reminder.id)?.status)
    }

    @Test
    fun optimisticVersionRejectsStaleDismissal() = withRepository { repo ->
        val reminder = repo.createConfirmed(draft(), strongProfile())
        assertTrue(repo.markDelivered(reminder.id, reminder.version, now))

        assertFalse(repo.markDismissed(reminder.id, reminder.version, now))
    }

    @Test
    fun eventHistoryIsBoundedAndMetadataNeverStoresReminderContent() = withRepository { repo ->
        val reminder = repo.createConfirmed(draft(content = "private reminder text"), strongProfile())
        repeat(60) { index -> repo.recordEvent(reminder.id, "delivery", now, mapOf("attempt" to index.toString(), "content" to "private reminder text")) }

        val events = repo.events(reminder.id)
        assertEquals(ReminderRepository.MAX_EVENT_HISTORY, events.size)
        assertTrue(events.all { !it.metadata_json.contains("private reminder text") })
    }

    @Test
    fun eventMetadataDropsPrivateAliasesAndBodies() = withRepository { repo ->
        val reminder = repo.createConfirmed(draft(content = "private reminder text"), strongProfile())

        repo.recordEvent(reminder.id, "delivery", now, mapOf("body" to "private reminder text", "alias" to "private reminder text"))

        assertEquals("{}", repo.events(reminder.id).first().metadata_json)
    }

    @Test
    fun activeAtHonorsWeekdayAndSelectedWeekdayRules() = withRepository { repo ->
        val weekday = repo.createConfirmed(draft(id = "weekday", rule = ReminderActiveDayRule.Weekdays), strongProfile())
        val selected = repo.createConfirmed(draft(id = "selected", rule = ReminderActiveDayRule.SelectedWeekdays(setOf(DayOfWeek.SUNDAY))), strongProfile())

        assertEquals(listOf(selected.id), repo.activeAt(Instant.parse("2026-08-30T10:00:00Z")).map { it.id })
        assertEquals(listOf(weekday.id), repo.activeAt(Instant.parse("2026-08-31T10:00:00Z")).map { it.id })
    }

    @Test
    fun activeAtUsesEachRemindersTimezoneAtDateBoundary() = withDatabase { db ->
        val kiritimati = ReminderRepository(db, TimeZone.of("Pacific/Kiritimati"))
        val utcReader = ReminderRepository(db, TimeZone.UTC)
        val reminder = kiritimati.createConfirmed(
            draft(id = "kiritimati", rule = ReminderActiveDayRule.Daily).copy(
                startDate = LocalDate(2026, 8, 31),
                endDate = LocalDate(2026, 8, 31),
            ),
            strongProfile(),
        )

        assertEquals(listOf(reminder.id), utcReader.activeAt(Instant.parse("2026-08-30T12:00:00Z")).map { it.id })
    }

    @Test
    fun conditionalUpdateRejectsVersionChangedAfterRead() = withRepository { repo ->
        val reminder = repo.createConfirmed(draft(), strongProfile())
        repo.complete(reminder.id)

        assertFalse(repo.markDelivered(reminder.id, reminder.version, now))
        assertEquals(ReminderStatus.COMPLETED, repo.get(reminder.id)!!.status)
    }

    private fun draft(id: String = "reminder-1", content: String = "pay credit card", rule: ReminderActiveDayRule = ReminderActiveDayRule.ConsecutiveDateRange) = ReminderDraft(
        id = id,
        content = content,
        startDate = LocalDate(2026, 8, 30),
        endDate = LocalDate(2026, 9, 2),
        firstReminderTime = LocalTime(18, 0),
        activeDayRule = rule,
    )

    private fun strongProfile() = ReminderProfileSnapshot.strong()

    private fun withRepository(block: (ReminderRepository) -> Unit) {
        withDatabase { db -> block(ReminderRepository(db, TimeZone.UTC)) }
    }

    private fun withDatabase(block: (DailySatoriDatabase) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        block(DailySatoriDatabase(driver))
        driver.close()
    }
}

package com.dailysatori.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.service.reminder.ReminderActiveDayRule
import com.dailysatori.service.reminder.ReminderDraft
import com.dailysatori.service.reminder.ReminderDataIssue
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import com.dailysatori.service.reminder.ReminderProfileKind
import com.dailysatori.service.reminder.ReminderImportance
import com.dailysatori.service.reminder.ReminderLockScreenVisibility
import com.dailysatori.service.reminder.ReminderRecurrence
import com.dailysatori.service.reminder.ReminderStatus
import com.dailysatori.service.reminder.LeapDayPolicy
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.DayOfWeek
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
    fun confirmationPersistsTheDraftTimezoneInsteadOfRepositoryStartupTimezone() = withRepository { repo ->
        val saved = repo.createConfirmed(draft().copy(timeZone = TimeZone.of("Pacific/Auckland")), strongProfile())

        assertEquals("Pacific/Auckland", saved.timeZone.id)
    }

    @Test
    fun yearlyRecurrenceRoundTrips() = withRepository { repo ->
        val saved = repo.createConfirmed(
            draft().copy(recurrence = ReminderRecurrence.Yearly(9, 2, LeapDayPolicy.FEBRUARY_28)),
            strongProfile(),
        )

        assertEquals(ReminderRecurrence.Yearly(9, 2, LeapDayPolicy.FEBRUARY_28), repo.get(saved.id)?.recurrence)
    }

    @Test
    fun editRejectsInvalidContentAndEmptySelectedWeekdays() = withRepository { repo ->
        val reminder = repo.createConfirmed(draft(), strongProfile())

        assertFalse(repo.update(reminder.id, ReminderEdit(reminder.version, content = "   ")))
        assertFalse(repo.update(reminder.id, ReminderEdit(reminder.version, content = "x".repeat(2_001))))
        assertFalse(repo.update(reminder.id, ReminderEdit(reminder.version, activeDayRule = ReminderActiveDayRule.SelectedWeekdays(emptySet()))))
        assertEquals("pay credit card", repo.get(reminder.id)?.content)
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
    fun dailyCutoffTransitionIsDurableResetsBackoffAndRejectsStaleGeneration() = withRepository { repo ->
        val reminder = repo.createConfirmed(draft(id = "cutoff"), strongProfile())
        assertTrue(repo.markDismissed(reminder.id, reminder.version, now))
        val dismissed = repo.get(reminder.id)!!

        assertTrue(repo.advanceCutoff(dismissed.id, dismissed.version, now, LocalDate(2026, 8, 30), ReminderStatus.ACTIVE))

        val rolled = repo.get(reminder.id)!!
        assertEquals(ReminderStatus.ACTIVE, rolled.status)
        assertEquals(dismissed.version + 1, rolled.version)
        assertEquals(0, repo.state(reminder.id)?.dismissalCount)
        assertFalse(repo.advanceCutoff(rolled.id, dismissed.version, now, LocalDate(2026, 8, 30), ReminderStatus.ACTIVE))
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
                timeZone = TimeZone.of("Pacific/Kiritimati"),
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

    @Test
    fun profileCrudPersistsIndependentSnapshots() = withRepository { repo ->
        val original = ReminderProfile("custom-1", "Focus", ReminderProfileKind.CUSTOM, strongProfile().copy(kind = ReminderProfileKind.CUSTOM, importance = ReminderImportance.LOW, lockScreenVisibility = ReminderLockScreenVisibility.SECRET))
        repo.upsertProfile(original)
        val savedSnapshot = repo.getProfile(original.id)!!.snapshot

        repo.upsertProfile(original.copy(name = "Focus 2", snapshot = original.snapshot.copy(soundEnabled = false)))

        assertEquals("Focus 2", repo.getProfile(original.id)?.name)
        assertTrue(savedSnapshot.soundEnabled)
        assertEquals(ReminderImportance.LOW, savedSnapshot.importance)
        assertEquals(ReminderLockScreenVisibility.SECRET, savedSnapshot.lockScreenVisibility)
        assertEquals(listOf(original.id), repo.profiles().map { it.id })
        assertTrue(repo.deleteProfile(original.id))
        assertTrue(repo.profiles().isEmpty())
    }

    @Test
    fun corruptReminderProfileIsQuarantinedWithoutBlockingOtherReminders() =
        withDriverDatabase { db, driver ->
            val repo = ReminderRepository(db, TimeZone.UTC)
            repo.createConfirmed(draft(id = "corrupt"), strongProfile())
            val healthy = repo.createConfirmed(draft(id = "healthy"), strongProfile())
            driver.execute(null, "UPDATE reminder SET profile_json = '{broken' WHERE id = 'corrupt'", 0)

            val reminders = runBlocking { repo.observeAll().first() }
            val corrupt = reminders.first { it.id == "corrupt" }
            assertEquals(ReminderStatus.PAUSED, corrupt.status)
            assertEquals(ReminderDataIssue.CORRUPT_PROFILE, corrupt.dataIssue)
            assertFalse(corrupt.profile.soundEnabled)
            assertFalse(corrupt.profile.vibrationEnabled)
            assertEquals(ReminderLockScreenVisibility.SECRET, corrupt.profile.lockScreenVisibility)
            assertEquals(listOf(healthy.id), repo.activeAt(now).map { it.id })
            assertFalse(repo.resume(corrupt.id))
            assertFalse(repo.update(corrupt.id, ReminderEdit(corrupt.version, content = "still quarantined")))
        }

    @Test
    fun invalidProfileValuesAreQuarantinedAndApplyingValidProfileRepairsReminder() = withDriverDatabase { db, driver ->
        val repo = ReminderRepository(db, TimeZone.UTC)
        val saved = repo.createConfirmed(draft(id = "invalid-values"), strongProfile())
        val invalid = profileJson(strongProfile()).replace("\"backoff\":[120,240]", "\"backoff\":[0]")
        driver.execute(null, "UPDATE reminder SET profile_json = '${invalid.replace("'", "''")}' WHERE id = 'invalid-values'", 0)

        val quarantined = repo.get(saved.id)!!
        assertEquals(ReminderDataIssue.CORRUPT_PROFILE, quarantined.dataIssue)
        assertTrue(repo.update(saved.id, ReminderEdit(quarantined.version, profile = strongProfile())))
        val repaired = repo.get(saved.id)!!
        assertEquals(null, repaired.dataIssue)
        assertEquals(ReminderStatus.ACTIVE, repaired.status)
        assertEquals(strongProfile(), repaired.profile)
    }

    @Test
    fun corruptCustomProfilesAreSkippedWithoutDeletingTheirRows() =
        withDatabase { db ->
            val repo = ReminderRepository(db, TimeZone.UTC)
            repo.upsertProfile(ReminderProfile("healthy", "Healthy", ReminderProfileKind.CUSTOM, strongProfile().copy(kind = ReminderProfileKind.CUSTOM)))
            db.dailySatoriQueries.upsertReminderProfile("corrupt", "Corrupt", ReminderProfileKind.CUSTOM.name, "{broken", 1, 1)

            assertEquals(listOf("healthy"), repo.profiles().map { it.id })
            assertEquals(listOf("healthy"), runBlocking { repo.observeProfiles().first() }.map { it.id })
            assertEquals(null, repo.getProfile("corrupt"))
            assertTrue(db.dailySatoriQueries.selectReminderProfileById("corrupt").executeAsOneOrNull() != null)
        }

    @Test
    fun deletingReminderRemovesItAndItsEvents() = withRepository { repo ->
        val reminder = repo.createConfirmed(draft(), strongProfile())

        assertTrue(repo.delete(reminder.id))

        assertEquals(null, repo.get(reminder.id))
        assertTrue(repo.events(reminder.id).isEmpty())
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

    private fun profileJson(profile: ReminderProfileSnapshot): String {
        val repoProfile = ReminderProfile("json", "JSON", profile.kind, profile)
        var json = ""
        withDatabase { db ->
            val repo = ReminderRepository(db, TimeZone.UTC)
            repo.upsertProfile(repoProfile)
            json = db.dailySatoriQueries.selectReminderProfileById("json").executeAsOne().profile_json
        }
        return json
    }

    private fun withRepository(block: (ReminderRepository) -> Unit) {
        withDatabase { db -> block(ReminderRepository(db, TimeZone.UTC)) }
    }

    private fun withDatabase(block: (DailySatoriDatabase) -> Unit) {
        withDriverDatabase { db, _ -> block(db) }
    }

    private fun withDriverDatabase(block: (DailySatoriDatabase, JdbcSqliteDriver) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        block(DailySatoriDatabase(driver), driver)
        driver.close()
    }
}

package com.dailysatori.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.service.reminder.LeapDayPolicy
import com.dailysatori.service.reminder.ReminderActiveDayRule
import com.dailysatori.service.reminder.ReminderDraft
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import com.dailysatori.service.reminder.ReminderRecurrence
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

class ReminderRepositoryBehaviorTest {
    @Test
    fun confirmingTheSameOpportunityTwiceKeepsOneOriginalReminder() = withRepository { _, repository ->
        val draft = ReminderDraft("news-opportunity:one", "先做一次小实验", LocalDate(2026, 10, 1), LocalDate(2026, 10, 1), LocalTime(9, 0), ReminderActiveDayRule.Daily)
        val profile = ReminderProfileSnapshot.standard()
        val first = repository.createConfirmedOnce(draft, profile)
        val repeated = repository.createConfirmedOnce(draft.copy(content = "重复点击不应覆盖"), profile)
        assertEquals(first, repeated)
        assertEquals("先做一次小实验", repository.get(draft.id)?.content)
    }
    @Test
    fun updateAcceptsSheetStyleEditOnAppCreatedRow() = withRepository { _, repository ->
        val created = repository.createConfirmed(
            draft = ReminderDraft(
                id = "r-test",
                content = "提交本周工作周报",
                startDate = LocalDate(2026, 9, 16),
                endDate = LocalDate(2026, 9, 16),
                firstReminderTime = LocalTime(8, 30),
                activeDayRule = ReminderActiveDayRule.ConsecutiveDateRange,
                recurrence = ReminderRecurrence.Once,
                timeZone = TimeZone.UTC,
            ),
            profileSnapshot = ReminderProfileSnapshot.standard(),
        )
        assertEquals(0L, created.version)

        val updated = repository.update(
            created.id,
            ReminderEdit(
                expectedVersion = created.version,
                content = "Buy milk at 09:00",
                startDate = LocalDate(2026, 9, 16),
                endDate = LocalDate(2026, 9, 16),
                firstReminderTime = LocalTime(9, 0),
                activeDayRule = ReminderActiveDayRule.ConsecutiveDateRange,
                recurrence = ReminderRecurrence.Once,
            ),
        )

        assertTrue(updated, "update rejected a sheet-style edit; the save silently fails")
        val row = repository.get(created.id)!!
        assertEquals("Buy milk at 09:00", row.content)
        assertEquals(LocalTime(9, 0), row.firstReminderTime)
        assertEquals(1L, row.version)
    }

    @Test
    fun yearlyReminderSurvivesSheetStyleEdit() = withRepository { _, repository ->
        val created = repository.createConfirmed(
            draft = ReminderDraft(
                id = "r-yearly",
                content = "续订域名",
                startDate = LocalDate(2026, 1, 1),
                endDate = LocalDate(2027, 12, 31),
                firstReminderTime = LocalTime(9, 0),
                activeDayRule = ReminderActiveDayRule.ConsecutiveDateRange,
                recurrence = ReminderRecurrence.Yearly(9, 28, LeapDayPolicy.FEBRUARY_28),
                timeZone = TimeZone.UTC,
            ),
            profileSnapshot = ReminderProfileSnapshot.standard(),
        )

        val updated = repository.update(
            created.id,
            ReminderEdit(
                expectedVersion = created.version,
                content = "续订 satori.dev 域名",
                startDate = LocalDate(2026, 1, 1),
                endDate = LocalDate(2027, 12, 31),
                firstReminderTime = LocalTime(9, 0),
                activeDayRule = ReminderActiveDayRule.ConsecutiveDateRange,
                recurrence = ReminderRecurrence.Yearly(9, 28, LeapDayPolicy.FEBRUARY_28),
            ),
        )

        assertTrue(updated)
        assertEquals("续订 satori.dev 域名", repository.get(created.id)!!.content)
    }

    private fun withRepository(test: (DailySatoriDatabase, ReminderRepository) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        try {
            val db = DailySatoriDatabase(driver)
            test(db, ReminderRepository(db, timeZone = TimeZone.UTC))
        } finally {
            driver.close()
        }
    }
}

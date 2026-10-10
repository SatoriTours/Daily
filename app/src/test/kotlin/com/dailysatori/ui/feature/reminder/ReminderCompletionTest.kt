package com.dailysatori.ui.feature.reminder

import android.content.ContextWrapper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.core.reminder.ReminderCoordinator
import com.dailysatori.core.reminder.ReminderNotificationPost
import com.dailysatori.core.reminder.ReminderNotifier
import com.dailysatori.core.reminder.ReminderScheduler
import com.dailysatori.core.reminder.RepositoryReminderDeliveryStore
import com.dailysatori.core.worker.AsyncTaskScheduler
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.ReminderAiBatchRepository
import com.dailysatori.data.repository.ReminderRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.reminder.LeapDayPolicy
import com.dailysatori.service.reminder.ReminderDraft
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import com.dailysatori.service.reminder.ReminderRecurrence
import com.dailysatori.service.reminder.ReminderScheduleEngine
import com.dailysatori.service.reminder.ReminderStatus
import com.dailysatori.service.reminder.ReminderSummary
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderCompletionTest {
    private val today = LocalDate(2026, 9, 2)
    private var now = Instant.parse("2026-09-02T08:00:00Z")
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var repository: ReminderRepository
    private lateinit var coordinator: ReminderCoordinator
    private lateinit var viewModel: ReminderViewModel
    private val scheduled = mutableMapOf<String, Instant>()
    private val posted = mutableListOf<ReminderNotificationPost>()
    private val cancelled = mutableListOf<String>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        Class.forName("org.sqlite.JDBC")
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        val db = DailySatoriDatabase(driver)
        repository = ReminderRepository(db, TimeZone.UTC)
        val scheduler = object : ReminderScheduler {
            override fun schedule(id: String, expectedVersion: Long, at: Instant) { scheduled[id] = at }
            override fun cancel(id: String) { scheduled.remove(id) }
        }
        val notifier = object : ReminderNotifier {
            override fun post(post: ReminderNotificationPost) { posted += post }
            override fun cancel(id: String) { cancelled += id }
        }
        coordinator = ReminderCoordinator(
            RepositoryReminderDeliveryStore(repository), ReminderScheduleEngine(), scheduler, notifier,
            clock = object : Clock { override fun now() = now },
        )
        val context = unusedContext()
        viewModel = ReminderViewModel(
            SavedStateHandle(), repository, coordinator, SettingRepository(db),
            ReminderAiBatchRepository(db), AsyncTaskRepository(db), AsyncTaskScheduler(context), context,
        )
    }

    @After
    fun tearDown() {
        if (::viewModel.isInitialized) viewModel.viewModelScope.cancel()
        if (::driver.isInitialized) driver.close()
        Dispatchers.resetMain()
    }

    @Test
    fun manualCompletionBeforeFirstNotificationEndsOnceReminder() = runBlocking {
        val reminder = create(ReminderRecurrence.Once)
        coordinator.recompute(reminder.id)
        assertEquals(Instant.parse("2026-09-02T10:00:00Z"), scheduled[reminder.id])

        completeManually(reminder.id)

        assertEquals(ReminderStatus.COMPLETED, repository.get(reminder.id)?.status)
        assertNull(scheduled[reminder.id])
        assertEquals(listOf(reminder.id), cancelled)
        coordinator.deliver(reminder.id, reminder.version)
        assertTrue(posted.isEmpty())
        assertEquals(0, ReminderSummary.todayPendingCount(listOf(repository.get(reminder.id)!!), today))
    }

    @Test
    fun manualCompletionBeforeNotificationKeepsNextMonthlyAndYearlyCycles() = runBlocking {
        val cases = listOf(
            ReminderRecurrence.Monthly(2) to Instant.parse("2026-10-02T10:00:00Z"),
            ReminderRecurrence.Yearly(9, 2, LeapDayPolicy.FEBRUARY_28) to Instant.parse("2027-09-02T10:00:00Z"),
        )
        for ((recurrence, nextAt) in cases) {
            val reminder = create(recurrence)
            coordinator.recompute(reminder.id)

            completeManually(reminder.id)

            assertEquals(ReminderStatus.ACTIVE, repository.get(reminder.id)?.status)
            assertEquals(nextAt, scheduled[reminder.id])
            coordinator.deliver(reminder.id, reminder.version)
            assertTrue(posted.isEmpty())
        }
    }

    @Test
    fun completingPausedRecurringReminderPreservesPauseAndAdvancesItsCycle() = runBlocking {
        val reminder = create(ReminderRecurrence.Monthly(2))
        repository.pause(reminder.id, now)

        completeManually(reminder.id)

        assertEquals(ReminderStatus.PAUSED, repository.get(reminder.id)?.status)
        assertEquals(LocalDate(2026, 10, 2), repository.state(reminder.id)?.stateDate)
        assertNull(scheduled[reminder.id])
        assertTrue(repository.resume(reminder.id, now))
        coordinator.recomputeAfterStateChange(reminder.id)
        assertEquals(Instant.parse("2026-10-02T10:00:00Z"), scheduled[reminder.id])
    }

    @Test
    fun completingRecurringOccurrenceRemovesTodayBadgeAndShowsNextDate() {
        val reminder = create(ReminderRecurrence.Monthly(2))
        assertTrue(coordinator.complete(reminder.id))
        val updated = listOf(repository.get(reminder.id)!!)

        assertEquals(0, ReminderSummary.todayPendingCount(updated, today))
        val list = buildReminderListState(updated, today, ReminderListMode.RECENT, ReminderListFilter())
        val item = list.sections.flatMap { it.items }.single()
        assertEquals(LocalDate(2026, 10, 2), item.occurrenceDate)
        assertEquals(false, item.isTodayPending)
        val todayList = buildReminderListState(updated, today, ReminderListMode.RECENT, ReminderListFilter(todayOnly = true))
        assertTrue(todayList.sections.isEmpty())
        coordinator.recomputeAll()
        assertEquals(Instant.parse("2026-10-02T10:00:00Z"), scheduled[reminder.id])
    }

    @Test
    fun expiredUnfinishedReminderStaysPendingAndCanStillBeCompleted() = runBlocking {
        val reminder = repository.createConfirmed(
            ReminderDraft("yesterday", "Unfinished task", LocalDate(2026, 9, 1), LocalDate(2026, 9, 1), LocalTime(10, 0), timeZone = TimeZone.UTC),
            ReminderProfileSnapshot.strong(),
        )
        assertTrue(repository.expire(reminder.id, now))
        val expired = listOf(repository.get(reminder.id)!!)
        assertEquals(1, ReminderSummary.todayPendingCount(expired, today))
        val list = buildReminderListState(expired, today, ReminderListMode.RECENT, ReminderListFilter(todayOnly = true))
        assertEquals(listOf("overdue"), list.sections.map { it.key })
        assertEquals("yesterday", list.sections.single().items.single().id)
        assertTrue(ReminderAction.COMPLETE in reminderActions(expired.single()))

        completeManually(reminder.id)

        assertEquals(ReminderStatus.COMPLETED, repository.get(reminder.id)?.status)
        assertEquals(0, ReminderSummary.todayPendingCount(listOf(repository.get(reminder.id)!!), today))
        assertNull(scheduled[reminder.id])
    }

    @Test
    fun cutoffDoesNotHideUnfinishedRecurringReminderAndOnlyLatestCycleIsShown() {
        val reminder = create(ReminderRecurrence.Monthly(2))
        now = Instant.parse("2026-09-03T00:00:00Z")
        coordinator.cutoff(reminder.id, reminder.version)
        assertEquals(LocalDate(2026, 10, 2), repository.state(reminder.id)?.stateDate)
        val reminders = listOf(repository.get(reminder.id)!!)

        for ((date, occurrence) in listOf(
            LocalDate(2026, 9, 3) to LocalDate(2026, 9, 2),
            LocalDate(2026, 10, 3) to LocalDate(2026, 10, 2),
            LocalDate(2026, 12, 3) to LocalDate(2026, 12, 2),
        )) {
            assertEquals(1, ReminderSummary.todayPendingCount(reminders, date))
            val list = buildReminderListState(reminders, date, ReminderListMode.RECENT, ReminderListFilter(todayOnly = true))
            assertEquals(listOf("overdue"), list.sections.map { it.key })
            assertEquals(occurrence, list.sections.single().items.single().occurrenceDate)
        }
        assertTrue(posted.isEmpty())
    }

    @Test
    fun completingOverdueRecurringCycleStaysHiddenUntilTheNextCycle() = runBlocking {
        val reminder = create(ReminderRecurrence.Monthly(2))
        now = Instant.parse("2026-09-03T08:00:00Z")
        coordinator.cutoff(reminder.id, reminder.version)

        completeManually(reminder.id)

        val updated = listOf(repository.get(reminder.id)!!)
        assertEquals(0, ReminderSummary.todayPendingCount(updated, LocalDate(2026, 9, 3)))
        assertEquals(0, ReminderSummary.todayPendingCount(updated, LocalDate(2026, 9, 30)))
        assertEquals(1, ReminderSummary.todayPendingCount(updated, LocalDate(2026, 10, 2)))
        assertEquals(Instant.parse("2026-10-02T10:00:00Z"), scheduled[reminder.id])
    }

    @Test
    fun completingExpiredRecurringTaskOnlyCompletesItsLatestCycle() = runBlocking {
        val reminder = create(ReminderRecurrence.Monthly(2))
        now = Instant.parse("2026-09-03T08:00:00Z")
        assertTrue(repository.expire(reminder.id, now))

        completeManually(reminder.id)

        val updated = listOf(repository.get(reminder.id)!!)
        assertEquals(ReminderStatus.ACTIVE, updated.single().status)
        assertEquals(0, ReminderSummary.todayPendingCount(updated, LocalDate(2026, 9, 3)))
        assertEquals(1, ReminderSummary.todayPendingCount(updated, LocalDate(2026, 10, 2)))
    }

    private suspend fun completeManually(id: String) {
        val scopeJob = viewModel.viewModelScope.coroutineContext[Job]!!
        val existingJobs = scopeJob.children.toSet()
        viewModel.complete(id)
        scopeJob.children.filter { it !in existingJobs }.toList().joinAll()
    }

    // Completion does not call Android APIs; keep JDBC and ViewModel tests in one JVM classloader.
    private fun unusedContext(): ContextWrapper {
        val field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = field.get(null)
        return unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, ContextWrapper::class.java) as ContextWrapper
    }

    private fun create(recurrence: ReminderRecurrence) = repository.createConfirmed(
        ReminderDraft(
            id = recurrence.toString(), content = "Pay bill", startDate = today,
            endDate = today,
            firstReminderTime = LocalTime(10, 0), timeZone = TimeZone.UTC, recurrence = recurrence,
        ),
        ReminderProfileSnapshot.strong(),
    )
}

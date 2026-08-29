package com.dailysatori.core.reminder

import android.content.Intent
import com.dailysatori.data.repository.ReminderState
import com.dailysatori.service.reminder.Reminder
import com.dailysatori.service.reminder.ReminderActiveDayRule
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import com.dailysatori.service.reminder.ReminderScheduleEngine
import com.dailysatori.service.reminder.ReminderStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderRecoveryTest {
    @Test
    fun receiverRecognizesEverySystemRecoveryEntryPoint() {
        val actions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )

        assertTrue(actions.all(::isReminderRestoreAction))
    }

    @Test
    fun bootRecoveryReplacesOneLogicalOccurrenceForEveryActiveReminder() {
        val first = reminder("first", version = 2)
        val second = reminder("second", version = 7)
        val fixture = fixture(listOf(first, second))

        fixture.coordinator.recomputeAll()
        fixture.coordinator.recomputeAll()

        assertEquals(setOf("first", "second"), fixture.scheduler.pending.keys)
        assertEquals(2, fixture.scheduler.pending.getValue("first").expectedVersion)
        assertEquals(7, fixture.scheduler.pending.getValue("second").expectedVersion)
    }

    @Test
    fun timezoneRecoveryPreservesTenOClockLocalWallClockIntent() {
        val tokyo = TimeZone.of("Asia/Tokyo")
        val fixture = fixture(
            reminders = listOf(reminder("meeting", storedZone = TimeZone.UTC)),
            now = "2026-09-01T23:00:00Z",
            currentZone = { tokyo },
        )

        fixture.coordinator.recomputeAll()

        assertEquals(instant("2026-09-02T01:00:00Z"), fixture.scheduler.pending.getValue("meeting").at)
    }

    @Test
    fun processOwnerRunsStartupOnceAcrossActivityRecreation() = runTest {
        var snapshot = ReminderCapabilitySnapshot(notificationsAllowed = false, exactAlarmsAllowed = false, disabledChannelIds = setOf("silent"))
        var recoveries = 0
        val recovery = ReminderRecoveryController(
            capabilitySnapshot = { snapshot },
            recoveryScope = this,
            recover = { recoveries++ },
        )

        val destroyedActivityJob = Job().also { it.cancel() }
        recovery.startup()
        recovery.startup()
        runCurrent()

        assertTrue(destroyedActivityJob.isCancelled)
        assertEquals(1, recoveries)
    }

    @Test
    fun processOwnerResumesOnlyAfterRelevantCapabilityChange() = runTest {
        var snapshot = ReminderCapabilitySnapshot(notificationsAllowed = false, exactAlarmsAllowed = false, disabledChannelIds = setOf("silent"))
        var recoveries = 0
        val recovery = ReminderRecoveryController(
            capabilitySnapshot = { snapshot },
            recoveryScope = this,
            recover = { recoveries++ },
        )

        recovery.startup()
        recovery.resume()
        snapshot = snapshot.copy(exactAlarmsAllowed = true)
        recovery.resume()
        recovery.resume()
        runCurrent()

        assertEquals(2, recoveries)
    }

    @Test
    fun utcToAucklandDismissalsKeepTwoThenFourHourBackoffAcrossUtcDateBoundary() {
        val auckland = TimeZone.of("Pacific/Auckland")
        val reminder = reminder("bill").copy(
            startDate = LocalDate(2026, 9, 1),
            endDate = LocalDate(2026, 9, 2),
        )
        val store = FakeStore(listOf(reminder), false)
        val scheduler = FakeScheduler()
        val clock = MutableClock(instant("2026-08-31T22:00:00Z"))
        val coordinator = ReminderCoordinator(
            store,
            ReminderScheduleEngine(),
            scheduler,
            FakeNotifier(),
            clock,
            currentTimeZone = { auckland },
        )

        assertTrue(coordinator.dismiss("bill", 0))
        assertEquals(instant("2026-09-01T00:00:00Z"), scheduler.pending.getValue("bill").at)

        clock.value = instant("2026-09-01T00:00:00Z")
        assertTrue(coordinator.dismiss("bill", 1))

        assertEquals(instant("2026-09-01T04:00:00Z"), scheduler.pending.getValue("bill").at)
    }

    @Test
    fun permissionReturnRechecksExactAlarmCapability() {
        var exactAllowed = false
        val exact = FakeBackend()
        val work = FakeBackend()
        val scheduler = HybridReminderScheduler({ exactAllowed }, exact, work)
        val coordinator = ReminderCoordinator(
            FakeStore(listOf(reminder("bill")), false),
            ReminderScheduleEngine(),
            scheduler,
            FakeNotifier(),
            FixedClock(instant("2026-09-02T08:00:00Z")),
            currentTimeZone = { TimeZone.UTC },
        )

        coordinator.recomputeAll()
        assertNull(exact.pending["bill"])
        assertEquals(0, work.pending.getValue("bill").expectedVersion)

        exactAllowed = true
        coordinator.recomputeAll()
        assertEquals(0, exact.pending.getValue("bill").expectedVersion)
        assertEquals(0, work.pending.getValue("bill").expectedVersion)
    }

    @Test
    fun lateWorkPostsAtMostOnceWhenStillEligible() {
        val fixture = fixture(listOf(reminder("bill")), now = "2026-09-02T10:15:00Z")

        fixture.coordinator.deliver("bill", 0)
        fixture.coordinator.deliver("bill", 0)

        assertEquals(1, fixture.notifier.posts.size)
        assertEquals(1, fixture.store.get("bill")?.version)
    }

    @Test
    fun lateWorkDuringSleepSchedulesWakeWithoutPosting() {
        val fixture = fixture(
            listOf(reminder("bill", status = ReminderStatus.NOTIFIED, version = 3)),
            now = "2026-09-02T02:00:00Z",
        )

        fixture.coordinator.deliver("bill", 3)

        assertTrue(fixture.notifier.posts.isEmpty())
        assertEquals(instant("2026-09-02T09:00:00Z"), fixture.scheduler.pending.getValue("bill").at)
        assertEquals(3, fixture.scheduler.pending.getValue("bill").expectedVersion)
    }

    @Test
    fun stalePayloadAndTerminalRemindersNeverResurrect() {
        val active = reminder("active", version = 4)
        val completed = reminder("completed", status = ReminderStatus.COMPLETED, version = 5)
        val expired = reminder("expired", status = ReminderStatus.EXPIRED, version = 6)
        val fixture = fixture(listOf(active, completed, expired), exposeTerminalAsActive = true)

        fixture.coordinator.deliver("active", 3)
        fixture.coordinator.recomputeAll()

        assertTrue(fixture.notifier.posts.isEmpty())
        assertEquals(4, fixture.scheduler.pending.getValue("active").expectedVersion)
        assertNull(fixture.scheduler.pending["completed"])
        assertNull(fixture.scheduler.pending["expired"])
        assertEquals(ReminderStatus.COMPLETED, fixture.store.get("completed")?.status)
        assertEquals(ReminderStatus.EXPIRED, fixture.store.get("expired")?.status)
    }

    private fun fixture(
        reminders: List<Reminder>,
        now: String = "2026-09-02T08:00:00Z",
        currentZone: () -> TimeZone = { TimeZone.UTC },
        exposeTerminalAsActive: Boolean = false,
    ): Fixture {
        val store = FakeStore(reminders, exposeTerminalAsActive)
        val scheduler = FakeScheduler()
        val notifier = FakeNotifier()
        val clock = FixedClock(instant(now))
        return Fixture(
            ReminderCoordinator(store, ReminderScheduleEngine(), scheduler, notifier, clock, currentTimeZone = currentZone),
            store,
            scheduler,
            notifier,
        )
    }

    private data class Fixture(
        val coordinator: ReminderCoordinator,
        val store: FakeStore,
        val scheduler: FakeScheduler,
        val notifier: FakeNotifier,
    )

    private class FixedClock(private val value: Instant) : Clock {
        override fun now(): Instant = value
    }

    private class MutableClock(var value: Instant) : Clock {
        override fun now(): Instant = value
    }

    private class FakeScheduler : ReminderScheduler {
        val pending = mutableMapOf<String, ReminderScheduleRequest>()
        override fun schedule(id: String, expectedVersion: Long, at: Instant) {
            pending[id] = ReminderScheduleRequest(id, expectedVersion, at)
        }
        override fun cancel(id: String) {
            pending.remove(id)
        }
    }

    private class FakeBackend : ReminderScheduleBackend {
        val pending = mutableMapOf<String, ReminderScheduleRequest>()
        override fun schedule(request: ReminderScheduleRequest) {
            pending[request.id] = request
        }
        override fun cancel(id: String) {
            pending.remove(id)
        }
    }

    private class FakeNotifier : ReminderNotifier {
        val posts = mutableListOf<ReminderNotificationPost>()
        override fun post(post: ReminderNotificationPost) {
            posts += post
        }
        override fun cancel(id: String) = Unit
    }

    private class FakeStore(initial: List<Reminder>, private val exposeTerminalAsActive: Boolean) : ReminderDeliveryStore {
        private val reminders = initial.associateBy { it.id }.toMutableMap()
        private val states = initial.associate { it.id to ReminderState(0, null) }.toMutableMap()
        override fun get(id: String): Reminder? = reminders[id]
        override fun active(now: Instant): List<Reminder> = reminders.values.filter {
            exposeTerminalAsActive || it.status in deliverable
        }
        override fun state(id: String): ReminderState? = states[id]
        override fun markDelivered(id: String, expectedVersion: Long, at: Instant): Long? {
            val current = reminders[id] ?: return null
            if (current.version != expectedVersion || current.status !in deliverable) return null
            val next = current.copy(status = ReminderStatus.NOTIFIED, version = current.version + 1)
            reminders[id] = next
            return next.version
        }
        override fun markDismissed(id: String, expectedVersion: Long, at: Instant, timeZone: TimeZone): Boolean {
            val current = reminders[id] ?: return false
            if (current.version != expectedVersion || current.status !in deliverable) return false
            val date = at.toLocalDateTime(timeZone).date
            val previous = states.getValue(id)
            states[id] = ReminderState(if (previous.stateDate == date) previous.dismissalCount + 1 else 1, date)
            reminders[id] = current.copy(status = ReminderStatus.DISMISSED, version = current.version + 1)
            return true
        }
        override fun complete(id: String, at: Instant) = false
        override fun complete(id: String, expectedVersion: Long, at: Instant) = false
        override fun expire(id: String, at: Instant): Boolean {
            val current = reminders[id] ?: return false
            if (current.status in terminal) return false
            reminders[id] = current.copy(status = ReminderStatus.EXPIRED, version = current.version + 1)
            return true
        }

        private companion object {
            val deliverable = setOf(ReminderStatus.ACTIVE, ReminderStatus.NOTIFIED, ReminderStatus.DISMISSED)
            val terminal = setOf(ReminderStatus.COMPLETED, ReminderStatus.EXPIRED)
        }
    }

    private companion object {
        fun instant(value: String) = Instant.parse(value)

        fun reminder(
            id: String,
            status: ReminderStatus = ReminderStatus.ACTIVE,
            version: Long = 0,
            storedZone: TimeZone = TimeZone.UTC,
        ) = Reminder(
            id = id,
            content = id,
            startDate = LocalDate(2026, 9, 2),
            endDate = LocalDate(2026, 9, 3),
            firstReminderTime = LocalTime(10, 0),
            activeDayRule = ReminderActiveDayRule.Daily,
            profile = ReminderProfileSnapshot.strong(),
            status = status,
            timeZone = storedZone,
            version = version,
        )
    }
}

package com.dailysatori.core.reminder

import com.dailysatori.data.repository.ReminderState
import com.dailysatori.service.reminder.Reminder
import com.dailysatori.service.reminder.ReminderActiveDayRule
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import com.dailysatori.service.reminder.ReminderImportance
import com.dailysatori.service.reminder.ReminderLockScreenVisibility
import com.dailysatori.service.reminder.ReminderScheduleEngine
import com.dailysatori.service.reminder.ReminderStatus
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderDeliveryTest {
    @Test
    fun notificationPolicyCarriesImportanceAndLockScreenVisibility() {
        val reminder = reminder(profile = ReminderProfileSnapshot.standard().copy(importance = ReminderImportance.LOW, lockScreenVisibility = ReminderLockScreenVisibility.SECRET))

        val policy = ReminderNotificationPolicy.forDelivery(reminder, instant("2026-09-02T10:00:00Z"))

        assertEquals(ReminderImportance.LOW, policy.importance)
        assertEquals(ReminderLockScreenVisibility.SECRET, policy.lockScreenVisibility)
    }
    @Test
    fun hybridSchedulerKeepsSameGenerationWorkBackupWhenExactIsAllowed() {
        val exact = FakeBackend()
        val fallback = FakeBackend()
        val scheduler = HybridReminderScheduler({ true }, exact, fallback)

        scheduler.schedule("bill", 3, instant("2026-09-02T10:00:00Z"))

        assertEquals(3, exact.pending["bill"]?.expectedVersion)
        assertEquals(3, fallback.pending["bill"]?.expectedVersion)
        assertEquals(listOf("bill"), fallback.cancelled)

        val deniedScheduler = HybridReminderScheduler({ false }, exact, fallback)
        deniedScheduler.schedule("bill", 4, instant("2026-09-02T11:00:00Z"))
        assertNull(exact.pending["bill"])
        assertEquals(4, fallback.pending["bill"]?.expectedVersion)
    }

    @Test
    fun workBackupDeliversAtMostOnceAfterSystemDeletesExactAlarm() {
        val exact = FakeBackend()
        val fallback = FakeBackend()
        HybridReminderScheduler({ true }, exact, fallback)
            .schedule("bill", 0, instant("2026-09-02T10:00:00Z"))
        exact.pending.remove("bill")
        val backup = fallback.pending.getValue("bill")
        val fixture = fixture(now = "2026-09-02T10:00:00Z")

        fixture.coordinator.deliver(backup.id, backup.expectedVersion)
        fixture.coordinator.deliver(backup.id, backup.expectedVersion)

        assertEquals(1, fixture.notifier.posts.size)
    }

    @Test
    fun exactPermissionRaceFallsBackWithoutLosingTheOccurrence() {
        val exact = FakeBackend(failure = SecurityException("access revoked"))
        val fallback = FakeBackend()
        val scheduler = HybridReminderScheduler({ true }, exact, fallback)

        scheduler.schedule("bill", 5, instant("2026-09-02T12:00:00Z"))

        assertEquals(5, fallback.pending["bill"]?.expectedVersion)
    }

    @Test
    fun replacingScheduleLeavesOnePendingOccurrencePerReminder() {
        val scheduler = FakeScheduler()
        scheduler.schedule("bill", 1, instant("2026-09-02T10:00:00Z"))
        scheduler.schedule("bill", 2, instant("2026-09-02T11:00:00Z"))
        assertEquals(setOf("bill"), scheduler.pending.keys)
        assertEquals(2, scheduler.pending.getValue("bill").expectedVersion)
    }

    @Test
    fun pendingIntentIdentityIncludesReminderAndVersionAndIsImmutable() {
        val first = ReminderIntentIdentity.delivery("bill/one", 7)
        val next = ReminderIntentIdentity.delivery("bill/one", 8)
        val other = ReminderIntentIdentity.delivery("bill/two", 7)
        assertNotEquals(first.uri, next.uri)
        assertNotEquals(first.uri, other.uri)
        assertTrue(first.immutable)
    }

    @Test
    fun duplicateDeliveryPostsOnlyAfterSuccessfulCompareAndSet() {
        val fixture = fixture(now = "2026-09-02T10:00:00Z")
        fixture.coordinator.deliver("bill", 0)
        fixture.coordinator.deliver("bill", 0)
        assertEquals(1, fixture.notifier.posts.size)
        assertEquals(1, fixture.store.get("bill")?.version)
    }

    @Test
    fun swipeAdvancesBackoffWithoutCompleting() {
        val fixture = fixture(now = "2026-09-02T12:00:00Z")
        assertTrue(fixture.coordinator.dismiss("bill", 0))
        assertEquals(ReminderStatus.DISMISSED, fixture.store.get("bill")?.status)
        assertEquals(instant("2026-09-02T14:00:00Z"), fixture.scheduler.pending["bill"]?.at)

        fixture.clock.now = instant("2026-09-02T14:00:00Z")
        assertTrue(fixture.coordinator.dismiss("bill", 1))
        assertEquals(instant("2026-09-02T18:00:00Z"), fixture.scheduler.pending["bill"]?.at)
    }

    @Test
    fun staleDismissDoesNotCancelOrRecomputeCurrentGeneration() {
        val fixture = fixture(now = "2026-09-02T12:00:00Z", reminder = reminder(status = ReminderStatus.NOTIFIED, version = 2))
        fixture.scheduler.schedule("bill", 2, instant("2026-09-02T13:00:00Z"))

        assertFalse(fixture.coordinator.dismiss("bill", 1))

        assertEquals(2, fixture.scheduler.pending["bill"]?.expectedVersion)
        assertTrue(fixture.notifier.cancelled.isEmpty())
    }

    @Test
    fun completeIsTerminalAndCancelsScheduleAndNotification() {
        val fixture = fixture(now = "2026-09-02T12:00:00Z", reminder = reminder(status = ReminderStatus.NOTIFIED, version = 2))
        fixture.scheduler.schedule("bill", 2, fixture.clock.now)

        assertTrue(fixture.coordinator.complete("bill"))

        assertEquals(ReminderStatus.COMPLETED, fixture.store.get("bill")?.status)
        assertNull(fixture.scheduler.pending["bill"])
        assertEquals(listOf("bill"), fixture.notifier.cancelled)
    }

    @Test
    fun staleCompleteDoesNotMutateOrCancelCurrentGeneration() {
        val fixture = fixture(now = "2026-09-02T12:00:00Z", reminder = reminder(status = ReminderStatus.NOTIFIED, version = 2))
        fixture.scheduler.schedule("bill", 2, instant("2026-09-02T13:00:00Z"))

        assertFalse(fixture.coordinator.complete("bill", 1))

        assertEquals(ReminderStatus.NOTIFIED, fixture.store.get("bill")?.status)
        assertEquals(2, fixture.scheduler.pending["bill"]?.expectedVersion)
        assertTrue(fixture.notifier.cancelled.isEmpty())
    }

    @Test
    fun generationChangeAfterDeliveryCasSuppressesPost() {
        val fixture = fixture(now = "2026-09-02T10:00:00Z")
        fixture.store.afterDelivered = { fixture.store.force(ReminderStatus.PAUSED) }

        fixture.coordinator.deliver("bill", 0)

        assertTrue(fixture.notifier.posts.isEmpty())
        assertEquals(ReminderStatus.PAUSED, fixture.store.get("bill")?.status)
    }

    @Test
    fun recomputeAfterEditCancelsDisplayedOlderGeneration() {
        val fixture = fixture(now = "2026-09-02T12:00:00Z", reminder = reminder(status = ReminderStatus.NOTIFIED, version = 1))
        fixture.store.force(ReminderStatus.NOTIFIED)

        fixture.coordinator.recomputeAfterStateChange("bill")

        assertEquals(listOf("bill"), fixture.notifier.cancelled)
    }

    @Test
    fun restoreRecomputeKeepsCurrentVisibleNotification() {
        val fixture = fixture(now = "2026-09-02T12:00:00Z", reminder = reminder(status = ReminderStatus.NOTIFIED, version = 1))

        fixture.coordinator.recomputeAll()

        assertTrue(fixture.notifier.cancelled.isEmpty())
        assertEquals(1, fixture.scheduler.pending["bill"]?.expectedVersion)
    }

    @Test
    fun workHourNotificationIsVisibleButHasNoSoundOrVibration() {
        val reminder = reminder()
        val policy = ReminderNotificationPolicy.forDelivery(reminder, instant("2026-09-02T10:00:00Z"))
        assertTrue(policy.visible)
        assertFalse(policy.soundEnabled)
        assertFalse(policy.vibrationEnabled)
    }

    @Test
    fun notificationAlwaysProvidesRedactedLockScreenContent() {
        val policy = ReminderNotificationPolicy.forDelivery(reminder(), instant("2026-09-02T20:00:00Z"))
        assertEquals("You have a reminder", policy.lockScreenText)
        assertFalse(policy.lockScreenText.contains("pay", ignoreCase = true))
    }

    @Test
    fun expiredDeliveryDoesNotPostAndBecomesTerminal() {
        val fixture = fixture(now = "2026-09-03T00:00:00Z", reminder = reminder(endDate = LocalDate(2026, 9, 2)))
        fixture.coordinator.deliver("bill", 0)
        assertTrue(fixture.notifier.posts.isEmpty())
        assertEquals(ReminderStatus.EXPIRED, fixture.store.get("bill")?.status)
        assertNull(fixture.scheduler.pending["bill"])
    }

    @Test
    fun finalSlotActionsStayCurrentUntilCutoffThenExpireWithoutPosting() {
        val fixture = fixture(now = "2026-09-02T23:00:00Z", reminder = reminder(status = ReminderStatus.NOTIFIED, version = 2))

        fixture.coordinator.recompute("bill")
        assertEquals(ReminderOccurrenceKind.CUTOFF, fixture.scheduler.pending.getValue("bill").kind)
        assertTrue(fixture.coordinator.complete("bill", 2))

        val cutoff = fixture(now = "2026-09-03T00:00:00Z", reminder = reminder(status = ReminderStatus.NOTIFIED, version = 2))
        cutoff.coordinator.cutoff("bill", 2)
        assertEquals(ReminderStatus.EXPIRED, cutoff.store.get("bill")?.status)
        assertTrue(cutoff.notifier.posts.isEmpty())
    }

    @Test
    fun finalSlotDismissRemainsCurrentAndReschedulesOnlyTheCutoff() {
        val fixture = fixture(now = "2026-09-02T23:05:00Z", reminder = reminder(status = ReminderStatus.NOTIFIED, version = 2))

        assertTrue(fixture.coordinator.dismiss("bill", 2))

        assertEquals(ReminderStatus.DISMISSED, fixture.store.get("bill")?.status)
        assertEquals(3, fixture.store.get("bill")?.version)
        assertEquals(ReminderOccurrenceKind.CUTOFF, fixture.scheduler.pending.getValue("bill").kind)
        assertEquals(instant("2026-09-03T00:00:00Z"), fixture.scheduler.pending.getValue("bill").at)
    }

    @Test
    fun cutoffRollsMultiDayReminderAndResetsGenerationWithoutPosting() {
        val fixture = fixture(
            now = "2026-09-03T00:00:00Z",
            reminder = reminder(endDate = LocalDate(2026, 9, 4), status = ReminderStatus.NOTIFIED, version = 2),
        )

        fixture.coordinator.cutoff("bill", 2)

        assertEquals(ReminderStatus.ACTIVE, fixture.store.get("bill")?.status)
        assertEquals(3, fixture.store.get("bill")?.version)
        assertEquals(instant("2026-09-03T10:00:00Z"), fixture.scheduler.pending.getValue("bill").at)
        assertTrue(fixture.notifier.posts.isEmpty())
    }

    @Test
    fun wrappingQuietHoursCutoffRollsThenSchedulesNextDayWake() {
        val wrap = ReminderProfileSnapshot.strong().copy(sleepStart = LocalTime(22, 0), sleepEnd = LocalTime(9, 0))
        val fixture = fixture(
            now = "2026-09-03T00:00:00Z",
            reminder = reminder(endDate = LocalDate(2026, 9, 3), status = ReminderStatus.NOTIFIED, version = 2, profile = wrap),
        )

        fixture.coordinator.cutoff("bill", 2)

        assertEquals(instant("2026-09-03T09:00:00Z"), fixture.scheduler.pending.getValue("bill").at)
        assertEquals(ReminderOccurrenceKind.DELIVERY, fixture.scheduler.pending.getValue("bill").kind)
    }

    @Test
    fun cutoffAcrossInactiveDaysDoesNotImmediatelyRedeliverAfterNextActiveDelivery() {
        val weekdays = reminder(
            endDate = LocalDate(2026, 9, 7),
            status = ReminderStatus.NOTIFIED,
            version = 2,
            activeDayRule = ReminderActiveDayRule.Weekdays,
        )
        val fixture = fixture(now = "2026-09-05T00:00:00Z", reminder = weekdays)

        fixture.coordinator.cutoff("bill", 2)
        assertEquals(instant("2026-09-07T10:00:00Z"), fixture.scheduler.pending.getValue("bill").at)

        fixture.clock.now = instant("2026-09-07T10:00:00Z")
        fixture.coordinator.deliver("bill", 3)

        assertEquals(1, fixture.notifier.posts.size)
        assertEquals(instant("2026-09-07T11:00:00Z"), fixture.scheduler.pending.getValue("bill").at)
    }

    private fun fixture(
        now: String,
        reminder: Reminder = reminder(),
    ): Fixture {
        val store = FakeStore(reminder)
        val scheduler = FakeScheduler()
        val notifier = FakeNotifier()
        val clock = MutableClock(instant(now))
        return Fixture(
            ReminderCoordinator(store, ReminderScheduleEngine(), scheduler, notifier, clock),
            store,
            scheduler,
            notifier,
            clock,
        )
    }

    private data class Fixture(
        val coordinator: ReminderCoordinator,
        val store: FakeStore,
        val scheduler: FakeScheduler,
        val notifier: FakeNotifier,
        val clock: MutableClock,
    )

    private class MutableClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }

    private class FakeScheduler : ReminderScheduler {
        val pending = mutableMapOf<String, ReminderScheduleRequest>()
        override fun schedule(id: String, expectedVersion: Long, at: Instant) {
            pending[id] = ReminderScheduleRequest(id, expectedVersion, at)
        }
        override fun scheduleCutoff(id: String, expectedVersion: Long, at: Instant) {
            pending[id] = ReminderScheduleRequest(id, expectedVersion, at, ReminderOccurrenceKind.CUTOFF)
        }
        override fun cancel(id: String) { pending.remove(id) }
    }

    private class FakeBackend(private val failure: RuntimeException? = null) : ReminderScheduleBackend {
        val pending = mutableMapOf<String, ReminderScheduleRequest>()
        val cancelled = mutableListOf<String>()
        override fun schedule(request: ReminderScheduleRequest) {
            failure?.let { throw it }
            pending[request.id] = request
        }
        override fun cancel(id: String) { pending.remove(id); cancelled += id }
    }

    private class FakeNotifier : ReminderNotifier {
        val posts = mutableListOf<ReminderNotificationPost>()
        val cancelled = mutableListOf<String>()
        override fun post(post: ReminderNotificationPost) { posts += post }
        override fun cancel(id: String) { cancelled += id }
    }

    private class FakeStore(initial: Reminder) : ReminderDeliveryStore {
        private var reminder: Reminder? = initial
        private var state = ReminderState(0, null)
        var afterDelivered: (() -> Unit)? = null
        override fun get(id: String): Reminder? = reminder?.takeIf { it.id == id }
        override fun active(now: Instant): List<Reminder> = listOfNotNull(reminder).filter { it.status in deliverable }
        override fun state(id: String): ReminderState? = state.takeIf { reminder?.id == id }
        override fun markDelivered(id: String, expectedVersion: Long, at: Instant, timeZone: TimeZone): Long? {
            var delivered: Reminder? = null
            val changed = update(expectedVersion) {
                state = ReminderState(state.dismissalCount, at.toLocalDateTime(timeZone).date)
                it.copy(status = ReminderStatus.NOTIFIED, version = it.version + 1).also { next -> delivered = next }
            }
            if (!changed) return null
            afterDelivered?.invoke()
            return delivered?.version
        }
        override fun markDismissed(id: String, expectedVersion: Long, at: Instant, timeZone: TimeZone): Boolean = update(expectedVersion) {
            state = ReminderState(state.dismissalCount + 1, LocalDate(2026, 9, 2))
            it.copy(status = ReminderStatus.DISMISSED, version = it.version + 1)
        }
        override fun complete(id: String, at: Instant): Boolean = terminal(ReminderStatus.COMPLETED)
        override fun complete(id: String, expectedVersion: Long, at: Instant): Boolean = update(expectedVersion) {
            it.copy(status = ReminderStatus.COMPLETED, version = it.version + 1)
        }
        override fun expire(id: String, at: Instant): Boolean = terminal(ReminderStatus.EXPIRED)
        override fun expire(id: String, expectedVersion: Long, at: Instant): Boolean = update(expectedVersion) {
            it.copy(status = ReminderStatus.EXPIRED, version = it.version + 1)
        }
        override fun advanceCutoff(id: String, expectedVersion: Long, at: Instant, cycleDate: LocalDate, nextStatus: ReminderStatus): Boolean = update(expectedVersion) {
            state = ReminderState(0, cycleDate)
            it.copy(status = nextStatus, version = it.version + 1)
        }
        fun force(status: ReminderStatus) {
            reminder = reminder?.let { it.copy(status = status, version = it.version + 1) }
        }
        private fun update(expectedVersion: Long, transform: (Reminder) -> Reminder): Boolean {
            val current = reminder ?: return false
            if (current.version != expectedVersion || current.status !in deliverable) return false
            reminder = transform(current)
            return true
        }
        private fun terminal(status: ReminderStatus): Boolean {
            val current = reminder ?: return false
            if (current.status in setOf(ReminderStatus.COMPLETED, ReminderStatus.EXPIRED)) return false
            reminder = current.copy(status = status, version = current.version + 1)
            return true
        }
        private companion object {
            val deliverable = setOf(ReminderStatus.ACTIVE, ReminderStatus.NOTIFIED, ReminderStatus.DISMISSED)
        }
    }

    private companion object {
        val UTC = TimeZone.UTC
        fun instant(value: String): Instant = Instant.parse(value)
        fun reminder(
            endDate: LocalDate = LocalDate(2026, 9, 2),
            status: ReminderStatus = ReminderStatus.ACTIVE,
            version: Long = 0,
            profile: ReminderProfileSnapshot = ReminderProfileSnapshot.strong(),
            activeDayRule: ReminderActiveDayRule = ReminderActiveDayRule.Daily,
        ) = Reminder(
            id = "bill",
            content = "Pay credit card",
            startDate = LocalDate(2026, 9, 2),
            endDate = endDate,
            firstReminderTime = LocalTime(10, 0),
            activeDayRule = activeDayRule,
            profile = profile,
            status = status,
            timeZone = UTC,
            version = version,
        )
    }
}

package com.dailysatori.core.reminder

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.dailysatori.MainActivity
import com.dailysatori.data.repository.ReminderRepository
import com.dailysatori.data.repository.ReminderState
import com.dailysatori.service.reminder.Reminder
import com.dailysatori.service.reminder.ReminderActiveDayRule
import com.dailysatori.service.reminder.ReminderScheduleDecision
import com.dailysatori.service.reminder.ReminderScheduleEngine
import com.dailysatori.service.reminder.ReminderScheduleInput
import com.dailysatori.service.reminder.ReminderStatus
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

interface ReminderDeliveryStore {
    fun get(id: String): Reminder?
    fun active(now: Instant): List<Reminder>
    fun state(id: String): ReminderState?
    fun markDelivered(id: String, expectedVersion: Long, at: Instant): Long?
    fun markDismissed(id: String, expectedVersion: Long, at: Instant, timeZone: TimeZone): Boolean
    fun complete(id: String, at: Instant): Boolean
    fun complete(id: String, expectedVersion: Long, at: Instant): Boolean
    fun expire(id: String, at: Instant): Boolean
}

class RepositoryReminderDeliveryStore(
    private val repository: ReminderRepository,
) : ReminderDeliveryStore {
    override fun get(id: String) = repository.get(id)
    override fun active(now: Instant): List<Reminder> = runBlocking {
        repository.observeAll().first().filter { it.status in activeStatuses }
    }
    override fun state(id: String) = repository.state(id)
    override fun markDelivered(id: String, expectedVersion: Long, at: Instant): Long? {
        if (!repository.markDelivered(id, expectedVersion, at)) return null
        return expectedVersion + 1
    }
    override fun markDismissed(id: String, expectedVersion: Long, at: Instant, timeZone: TimeZone) =
        repository.markDismissed(id, expectedVersion, at, timeZone)
    override fun complete(id: String, at: Instant) = repository.complete(id, at)
    override fun complete(id: String, expectedVersion: Long, at: Instant) = repository.complete(id, expectedVersion, at)
    override fun expire(id: String, at: Instant) = repository.expire(id, at)

    private companion object {
        val activeStatuses = setOf(ReminderStatus.ACTIVE, ReminderStatus.NOTIFIED, ReminderStatus.DISMISSED)
    }
}

class ReminderCoordinator(
    private val store: ReminderDeliveryStore,
    private val engine: ReminderScheduleEngine,
    private val scheduler: ReminderScheduler,
    private val notifier: ReminderNotifier,
    private val clock: Clock = Clock.System,
    private val context: Context? = null,
    private val currentTimeZone: (() -> TimeZone)? = null,
) {
    @Synchronized
    fun recompute(id: String) {
        recomputeSchedule(id)
    }

    @Synchronized
    fun recomputeAfterStateChange(id: String) {
        notifier.cancel(id)
        recomputeSchedule(id)
    }

    private fun recomputeSchedule(id: String) {
        scheduler.cancel(id)
        val reminder = store.get(id)?.inCurrentTimeZone() ?: return
        val decision = engine.next(reminder.toScheduleInput(clock.now(), store.state(id)))
        when (decision) {
            is ReminderScheduleDecision.Schedule -> scheduler.schedule(id, decision.expectedVersion, decision.at)
            is ReminderScheduleDecision.None -> if (decision.status == ReminderStatus.EXPIRED) store.expire(id, clock.now())
        }
    }

    @Synchronized
    fun recomputeAll() {
        store.active(clock.now()).forEach { recompute(it.id) }
    }

    @Synchronized
    fun deliver(id: String, expectedVersion: Long) {
        val now = clock.now()
        val reminder = store.get(id)?.inCurrentTimeZone() ?: return
        if (reminder.version != expectedVersion) return
        if (!reminder.isEligibleAt(now)) {
            if (now.toLocalDateTime(reminder.timeZone).date > reminder.endDate) store.expire(id, now)
            scheduler.cancel(id)
            if (store.get(id)?.status !in terminalStatuses) recomputeSchedule(id)
            return
        }
        val deliveredVersion = store.markDelivered(id, expectedVersion, now) ?: return
        try {
            val current = store.get(id)?.inCurrentTimeZone()
            if (current?.version != deliveredVersion || current.status != ReminderStatus.NOTIFIED) {
                notifier.cancel(id)
                return
            }
            notifier.post(ReminderNotificationPost(current, ReminderNotificationPolicy.forDelivery(current, now)))
        } finally {
            recomputeSchedule(id)
        }
    }

    @Synchronized
    fun dismiss(id: String, version: Long): Boolean {
        val reminder = store.get(id) ?: return false
        val changed = store.markDismissed(id, version, clock.now(), reminder.currentTimeZone())
        if (changed) {
            notifier.cancel(id)
            recomputeSchedule(id)
        }
        return changed
    }

    @Synchronized
    fun complete(id: String): Boolean = finishCompletion(id, store.complete(id, clock.now()))

    @Synchronized
    fun complete(id: String, version: Long): Boolean {
        return finishCompletion(id, store.complete(id, version, clock.now()))
    }

    private fun finishCompletion(id: String, changed: Boolean): Boolean {
        if (changed) {
            scheduler.cancel(id)
            notifier.cancel(id)
        }
        return changed
    }

    fun viewIntent(id: String): Intent {
        val appContext = checkNotNull(context) { "Android context is required for view intents" }
        return Intent(appContext, MainActivity::class.java)
            .setAction(ACTION_VIEW_REMINDER)
            .setData(Uri.parse("dailysatori://reminder/view/${Uri.encode(id)}"))
            .putExtra(EXTRA_REMINDER_ID, id)
    }

    private fun Reminder.toScheduleInput(now: Instant, state: ReminderState?) = ReminderScheduleInput(
        now = now,
        timeZone = timeZone,
        startDate = startDate,
        endDate = endDate,
        firstReminderTime = firstReminderTime,
        activeDayRule = activeDayRule,
        profile = profile,
        status = status,
        dismissalCount = state?.dismissalCount ?: 0,
        stateDate = state?.stateDate,
        expectedVersion = version,
    )

    private fun Reminder.inCurrentTimeZone(): Reminder = copy(
        timeZone = currentTimeZone(),
    )

    private fun Reminder.currentTimeZone(): TimeZone =
        currentTimeZone?.invoke() ?: if (context != null) TimeZone.currentSystemDefault() else timeZone

    private fun Reminder.isEligibleAt(now: Instant): Boolean {
        if (status !in deliverableStatuses) return false
        val local = now.toLocalDateTime(timeZone)
        if (local.date !in startDate..endDate || !activeDayRule.includes(local.date.dayOfWeek)) return false
        if (local.date == startDate && local.time < firstReminderTime) return false
        if (profile.dailyCutoff != LocalTime(0, 0) && local.time >= profile.dailyCutoff) return false
        return !profile.isSleepTime(local.time)
    }

    private fun ReminderActiveDayRule.includes(day: kotlinx.datetime.DayOfWeek): Boolean = when (this) {
        ReminderActiveDayRule.Daily, ReminderActiveDayRule.ConsecutiveDateRange -> true
        ReminderActiveDayRule.Weekdays -> day.value <= 5
        is ReminderActiveDayRule.SelectedWeekdays -> day in days
    }

    private fun com.dailysatori.service.reminder.ReminderProfileSnapshot.isSleepTime(time: LocalTime): Boolean =
        if (sleepStart <= sleepEnd) time >= sleepStart && time < sleepEnd else time >= sleepStart || time < sleepEnd

    companion object {
        const val ACTION_VIEW_REMINDER = "com.dailysatori.reminder.VIEW"
        const val EXTRA_REMINDER_ID = "reminder_id"
        private val deliverableStatuses = setOf(ReminderStatus.ACTIVE, ReminderStatus.NOTIFIED, ReminderStatus.DISMISSED)
        private val terminalStatuses = setOf(ReminderStatus.COMPLETED, ReminderStatus.EXPIRED, ReminderStatus.PAUSED, ReminderStatus.DRAFT)
    }
}

data class ReminderCapabilitySnapshot(
    val notificationsAllowed: Boolean,
    val exactAlarmsAllowed: Boolean,
    val disabledChannelIds: Set<String> = emptySet(),
)

class ReminderRecoveryController(
    private val capabilitySnapshot: () -> ReminderCapabilitySnapshot,
    private val recoveryScope: CoroutineScope,
    private val recover: suspend () -> Unit,
) {
    private var previous: ReminderCapabilitySnapshot? = null
    private var started = false

    @Synchronized
    fun startup() {
        if (started) return
        started = true
        previous = capabilitySnapshot()
        recoveryScope.launch { recover() }
    }

    @Synchronized
    fun resume() {
        val current = capabilitySnapshot()
        val changed = previous?.let { it != current } == true
        previous = current
        if (changed) recoveryScope.launch { recover() }
    }
}

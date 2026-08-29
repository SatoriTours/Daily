package com.dailysatori.core.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class ReminderScheduleRequest(
    val id: String,
    val expectedVersion: Long,
    val at: Instant,
    val kind: ReminderOccurrenceKind = ReminderOccurrenceKind.DELIVERY,
)

enum class ReminderOccurrenceKind { DELIVERY, CUTOFF }

interface ReminderScheduler {
    fun schedule(id: String, expectedVersion: Long, at: Instant)
    fun scheduleCutoff(id: String, expectedVersion: Long, at: Instant) = schedule(id, expectedVersion, at)
    fun cancel(id: String)
}

interface ReminderScheduleBackend {
    fun schedule(request: ReminderScheduleRequest)
    fun cancel(id: String)
}

class HybridReminderScheduler(
    private val exactAllowed: () -> Boolean,
    private val exact: ReminderScheduleBackend,
    private val fallback: ReminderScheduleBackend,
) : ReminderScheduler {
    override fun schedule(id: String, expectedVersion: Long, at: Instant) {
        schedule(ReminderScheduleRequest(id, expectedVersion, at))
    }

    override fun scheduleCutoff(id: String, expectedVersion: Long, at: Instant) {
        schedule(ReminderScheduleRequest(id, expectedVersion, at, ReminderOccurrenceKind.CUTOFF))
    }

    private fun schedule(request: ReminderScheduleRequest) {
        cancel(request.id)
        if (!exactAllowed()) {
            fallback.schedule(request)
            return
        }
        fallback.schedule(request)
        try {
            exact.schedule(request)
        } catch (_: SecurityException) {
            exact.cancel(request.id)
        }
    }

    override fun cancel(id: String) {
        exact.cancel(id)
        fallback.cancel(id)
    }
}

class ExactAlarmReminderScheduler(
    private val context: Context,
    private val alarmManager: AlarmManager,
) : ReminderScheduleBackend {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun schedule(request: ReminderScheduleRequest) {
        cancel(request.id)
        val identity = request.identity()
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            request.at.toEpochMilliseconds(),
            deliveryIntent(identity, request),
        )
        preferences.edit()
            .putLong(request.id, request.expectedVersion)
            .putString("${request.id}:kind", request.kind.name)
            .apply()
    }

    override fun cancel(id: String) {
        if (!preferences.contains(id)) return
        val version = preferences.getLong(id, 0)
        val kind = preferences.getString("$id:kind", null)?.let(ReminderOccurrenceKind::valueOf) ?: ReminderOccurrenceKind.DELIVERY
        val request = ReminderScheduleRequest(id, version, Instant.DISTANT_PAST, kind)
        alarmManager.cancel(deliveryIntent(request.identity(), request))
        preferences.edit().remove(id).remove("$id:kind").apply()
    }

    private fun deliveryIntent(identity: ReminderIntentIdentity, request: ReminderScheduleRequest): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(if (request.kind == ReminderOccurrenceKind.CUTOFF) ReminderReceiver.ACTION_CUTOFF else ReminderReceiver.ACTION_DELIVER)
            .setData(Uri.parse(identity.uri))
            .putExtra(ReminderReceiver.EXTRA_REMINDER_ID, request.id)
            .putExtra(ReminderReceiver.EXTRA_EXPECTED_VERSION, request.expectedVersion)
        return PendingIntent.getBroadcast(
            context,
            identity.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val PREFERENCES = "reminder-exact-alarm-identities"
    }
}

class WorkManagerReminderScheduler(
    context: Context,
    private val clock: Clock = Clock.System,
) : ReminderScheduleBackend {
    private val workManager = WorkManager.getInstance(context)

    override fun schedule(request: ReminderScheduleRequest) {
        val data = Data.Builder()
            .putString(ReminderWorker.KEY_REMINDER_ID, request.id)
            .putLong(ReminderWorker.KEY_EXPECTED_VERSION, request.expectedVersion)
            .putString(ReminderWorker.KEY_OCCURRENCE_KIND, request.kind.name)
            .build()
        val delay = (request.at.toEpochMilliseconds() - clock.now().toEpochMilliseconds()).coerceAtLeast(0)
        val work = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(data)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(workName(request.id), ExistingWorkPolicy.REPLACE, work)
    }

    override fun cancel(id: String) {
        workManager.cancelUniqueWork(workName(id))
    }

    private fun workName(id: String) = "reminder-next:$id"
}

data class ReminderIntentIdentity(
    val uri: String,
    val requestCode: Int,
    val immutable: Boolean = true,
) {
    companion object {
        fun delivery(id: String, version: Long): ReminderIntentIdentity = create("deliver", id, version)
        fun cutoff(id: String, version: Long): ReminderIntentIdentity = create("cutoff", id, version)
        fun dismiss(id: String, version: Long): ReminderIntentIdentity = create("dismiss", id, version)
        fun complete(id: String, version: Long): ReminderIntentIdentity = create("complete", id, version)
        fun view(id: String, version: Long): ReminderIntentIdentity = create("view", id, version)

        private fun create(action: String, id: String, version: Long): ReminderIntentIdentity {
            val encodedId = URLEncoder.encode(id, StandardCharsets.UTF_8.toString())
            val uri = "dailysatori://reminder/$action/$encodedId/$version"
            return ReminderIntentIdentity(uri, uri.hashCode())
        }
    }
}

private fun ReminderScheduleRequest.identity(): ReminderIntentIdentity =
    if (kind == ReminderOccurrenceKind.CUTOFF) ReminderIntentIdentity.cutoff(id, expectedVersion)
    else ReminderIntentIdentity.delivery(id, expectedVersion)

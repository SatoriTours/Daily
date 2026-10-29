package com.dailysatori.core.sms

import android.content.Context
import androidx.work.*
import com.dailysatori.core.startup.StartupWorker
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.platform.PlatformContext
import com.dailysatori.service.security.SecretCipher
import com.dailysatori.service.sms.SmsReminderService
import com.dailysatori.service.sms.SmsSource
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.koin.core.context.GlobalContext

class SmsIntakeWorker(context: Context, params: WorkerParameters) : StartupWorker(context, params) {
    override suspend fun doReadyWork(): Result {
        val id = inputData.getString(KEY_FILE) ?: return Result.failure()
        return try {
            val store = pendingSmsStore(applicationContext)
            if (id !in store.pendingIds()) return Result.success()
            val sms = store.load(id)
            val koin = GlobalContext.get()
            if (koin.get<SettingRepository>().get(SmsReminderService.ENABLED_KEY) == "true") {
                koin.get<SmsIntake>().accept(sms.id, SmsSource(sms.sender, sms.body),
                    Instant.fromEpochMilliseconds(sms.receivedAt), TimeZone.of(sms.zone))
            }
            store.remove(id)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object { const val KEY_FILE = "pending_sms_file" }
}

internal fun pendingSmsStore(context: Context) = PendingSmsStore(
    File(context.noBackupFilesDir, "pending-sms"), SecretCipher(PlatformContext(context)),
)

internal fun enqueuePendingSms(context: Context, id: String): Operation = WorkManager.getInstance(context)
    .enqueueUniqueWork("pending-sms-$id", ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<SmsIntakeWorker>().setInputData(workDataOf(SmsIntakeWorker.KEY_FILE to id)).build())

internal fun recoverPendingSms(context: Context) {
    pendingSmsStore(context).pendingIds().forEach { enqueuePendingSms(context, it) }
}

package com.dailysatori

import android.content.Intent
import android.os.Bundle
import com.dailysatori.core.diagnostics.SafeAndroidLog as Log
import com.dailysatori.core.startup.StartupActivity
import androidx.lifecycle.Lifecycle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import com.dailysatori.core.diagnostics.DiagnosticRecoveryReader
import java.io.File
import androidx.lifecycle.lifecycleScope
import com.dailysatori.core.reminder.ReminderRecoveryController
import com.dailysatori.core.reminder.handleReminderViewIntent
import com.dailysatori.core.reminder.handleReminderAiBatchViewIntent
import com.dailysatori.core.task.AsyncTaskLogStore
import com.dailysatori.core.worker.ExternalFavoriteSyncScheduler
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import com.dailysatori.service.externalfavorites.FavoriteSyncMode
import com.dailysatori.service.externalfavorites.XOAuthCoordinator
import com.dailysatori.core.recording.DiaryRecordingOpenRequest
import com.dailysatori.core.recording.DiaryRecordingService
import com.dailysatori.ui.theme.DailySatoriTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext

class MainActivity : StartupActivity() {
    private lateinit var reminderRecovery: ReminderRecoveryController

    override fun onStartupReady(savedInstanceState: Bundle?) {
        reminderRecovery = GlobalContext.get().get()
        reminderRecovery.startup()
        handleOAuthIntent(intent)
        handleRecordingIntent(intent)
        handleReminderViewIntent(intent)
        handleReminderAiBatchViewIntent(intent)
        com.dailysatori.core.sms.SmsOpenRequest.handle(intent)
        pruneOldAsyncTasks()
        enableEdgeToEdge()
        setContent {
            DailySatoriTheme {
                DailySatoriApp()
                LaunchedEffect(Unit) {
                    withContext(Dispatchers.IO) {
                        DiagnosticRecoveryReader.startupCompleted(File(noBackupFilesDir, "diagnostics"))
                    }
                }
            }
        }
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resumeBusiness()
    }

    override fun onResume() {
        super.onResume()
        if (businessReady) resumeBusiness()
    }

    private fun resumeBusiness() {
        reminderRecovery.resume()
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                GlobalContext.get().get<com.dailysatori.core.worker.AsyncTaskScheduler>().recoverAndEnqueueRunnable()
                GlobalContext.get().get<ExternalFavoriteSyncScheduler>().recover()
            }.onFailure { Log.w(TAG, "Async task resume recovery failed", it) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!businessReady) return
        handleOAuthIntent(intent)
        handleRecordingIntent(intent)
        handleReminderViewIntent(intent)
        handleReminderAiBatchViewIntent(intent)
        com.dailysatori.core.sms.SmsOpenRequest.handle(intent)
        lifecycleScope.launch(Dispatchers.IO) {
            DiagnosticRecoveryReader.startupCompleted(File(noBackupFilesDir, "diagnostics"))
        }
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val uri = intent?.data?.toString()?.takeIf { it.startsWith("dailysatori://oauth/x") } ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val koin = GlobalContext.get()
                val sourceId = koin.get<XOAuthCoordinator>().handleCallbackUrl(uri)
                val scheduler = koin.get<ExternalFavoriteSyncScheduler>()
                koin.get<ExternalFavoriteSourceRepository>().getById(sourceId)?.let { source ->
                    if (source.enabled == 1L) {
                        scheduler.enqueue(sourceId, FavoriteSyncMode.sync.name)
                        scheduler.enqueuePeriodic(source)
                    } else {
                        scheduler.cancelPeriodic(sourceId)
                    }
                }
            }.onFailure { error ->
                Log.e(TAG, "X OAuth callback handling failed", error)
            }
        }
    }

    private fun handleRecordingIntent(intent: Intent?) {
        if (intent?.action != DiaryRecordingService.ACTION_OPEN) return
        val diaryId = intent.getLongExtra(DiaryRecordingService.EXTRA_DIARY_ID, 0)
        DiaryRecordingOpenRequest.open(diaryId)
    }

    private fun pruneOldAsyncTasks() {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val koin = GlobalContext.get()
                val deletedTaskIds = koin.get<AsyncTaskRepository>().pruneOldTasks()
                koin.get<AsyncTaskLogStore>().delete(deletedTaskIds)
            }.onFailure { error ->
                Log.w(TAG, "Async task pruning failed", error)
            }
        }
    }

    private companion object {
        const val TAG = "DailySatoriMain"
    }
}

package com.dailysatori

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.dailysatori.core.task.AsyncTaskLogStore
import com.dailysatori.core.worker.ExternalFavoriteSyncScheduler
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import com.dailysatori.service.externalfavorites.FavoriteSyncMode
import com.dailysatori.service.externalfavorites.XOAuthCoordinator
import com.dailysatori.core.recording.DiaryRecordingOpenRequest
import com.dailysatori.core.recording.DiaryRecordingService
import com.dailysatori.ui.theme.DailySatoriTheme
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleOAuthIntent(intent)
        handleRecordingIntent(intent)
        pruneOldAsyncTasks()
        enableEdgeToEdge()
        setContent {
            DailySatoriTheme {
                DailySatoriApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthIntent(intent)
        handleRecordingIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val uri = intent?.data?.toString()?.takeIf { it.startsWith("dailysatori://oauth/x") } ?: return
        lifecycleScope.launch {
            runCatching {
                val koin = GlobalContext.get()
                val sourceId = koin.get<XOAuthCoordinator>().handleCallbackUrl(uri)
                val scheduler = koin.get<ExternalFavoriteSyncScheduler>()
                scheduler.enqueue(sourceId, FavoriteSyncMode.sync.name)
                koin.get<ExternalFavoriteSourceRepository>().getById(sourceId)?.let { source ->
                    scheduler.enqueuePeriodic(source.id, source.sync_interval_minutes)
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
        lifecycleScope.launch {
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

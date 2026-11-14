package com.dailysatori.core.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import com.dailysatori.core.startup.StartupWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dailysatori.service.diary.DiaryRefreshOutcome
import com.dailysatori.service.diary.DiaryThoughtService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class DiaryThoughtScheduler(private val context: Context) {
    fun enqueue() {
        // 追加唤醒避免 KEEP 在旧任务即将完成时丢失新变更；服务会跳过已处理快照。
        WorkManager.getInstance(context).enqueueUniqueWork(
            "diary-thought-refresh",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<DiaryThoughtWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }
}

class DiaryThoughtWorker(context: Context, params: WorkerParameters) : StartupWorker(context, params) {
    override suspend fun doReadyWork(): Result = coroutineScope {
        val service = GlobalContext.get().get<DiaryThoughtService>()
        ForegroundDiagnostics.workerStarted(ForegroundDiagnostics.DIARY_THOUGHT, null, null, runAttemptCount)
        // 先确认来源快照已就绪：没有可判定的请求就不申请 dataSync 前台，
        // 避免无工作时空占前台直到系统回收。
        if (!service.awaitPendingRequest()) {
            ForegroundDiagnostics.workerFinished(
                ForegroundDiagnostics.DIARY_THOUGHT, "sources_not_ready", null, null,
            )
            return@coroutineScope Result.success()
        }
        val foreground = thoughtForegroundInfo(applicationContext, "准备整理我的思想…")
        ForegroundDiagnostics.requestStarted(
            ForegroundDiagnostics.DIARY_THOUGHT, foreground.notificationId, foreground.foregroundServiceType,
            null, null, runAttemptCount,
        )
        try {
            setForeground(foreground)
            ForegroundDiagnostics.requestSucceeded(
                ForegroundDiagnostics.DIARY_THOUGHT, foreground.notificationId, null, null,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            ForegroundDiagnostics.requestFailed(
                ForegroundDiagnostics.DIARY_THOUGHT, foreground.notificationId, null, null, error,
            )
            throw error
        }
        val progress = launch {
            service.state.map { it.progress }.distinctUntilChanged().collect {
                if (it.isNotBlank()) setForeground(thoughtForegroundInfo(applicationContext, it))
            }
        }
        try {
            val outcome = when (service.runPendingRefresh()) {
                DiaryRefreshOutcome.Completed -> "completed"
                DiaryRefreshOutcome.NothingPending -> "nothing_pending"
                DiaryRefreshOutcome.NotReady -> "sources_not_ready"
            }
            ForegroundDiagnostics.workerFinished(ForegroundDiagnostics.DIARY_THOUGHT, outcome, null, null)
            // 本轮没有可执行的工作时立即结束，不重试：来源就绪后观察者会重新调度。
            Result.success()
        } catch (error: CancellationException) {
            ForegroundDiagnostics.workerCancelled(ForegroundDiagnostics.DIARY_THOUGHT, null)
            throw error
        } finally {
            progress.cancel()
        }
    }
}

private fun thoughtForegroundInfo(context: Context, progress: String): ForegroundInfo {
    val channel = "diary_thoughts"
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(channel, "我的思想整理", NotificationManager.IMPORTANCE_LOW),
    )
    val notification = NotificationCompat.Builder(context, channel)
        .setSmallIcon(android.R.drawable.ic_popup_sync)
        .setContentTitle("正在整理我的思想")
        .setContentText(progress)
        .setOngoing(true)
        .setSilent(true)
        .build()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ForegroundInfo(3301, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
        ForegroundInfo(3301, notification)
    }
}

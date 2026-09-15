package com.dailysatori.core.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dailysatori.service.diary.DiaryThoughtService
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

class DiaryThoughtWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = coroutineScope {
        val service = GlobalContext.get().get<DiaryThoughtService>()
        setForeground(thoughtForegroundInfo(applicationContext, "准备整理我的思想…"))
        val progress = launch {
            service.state.map { it.progress }.distinctUntilChanged().collect {
                if (it.isNotBlank()) setForeground(thoughtForegroundInfo(applicationContext, it))
            }
        }
        try {
            service.runPendingRefresh()
            Result.success()
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

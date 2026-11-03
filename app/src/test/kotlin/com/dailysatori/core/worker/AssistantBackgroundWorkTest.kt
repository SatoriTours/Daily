package com.dailysatori.core.worker

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.*
import androidx.work.impl.utils.futures.SettableFuture
import androidx.work.impl.utils.taskexecutor.WorkManagerTaskExecutor
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.service.asynctask.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID
import java.util.concurrent.Executor
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE, application = android.app.Application::class)
class AssistantBackgroundWorkTest {
    @Test
    fun assistantWaitsForNetworkAndHasForegroundProtection() {
        val request = buildAsyncTaskWorkRequest(42, "assistant_turn")
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertTrue(isLongRunningAsyncTask("assistant_turn"))
        assertTrue(asyncTaskWorkTag(42) in request.tags)
    }

    @Test
    fun interruptedAssistantRetainsNetworkConstraintAndRetryDelay() {
        val request = buildAsyncTaskWorkRequest(42, "assistant_turn", initialDelayMs = 30_000)
        assertEquals(30_000L, request.workSpec.initialDelay)
        assertFalse(request.workSpec.expedited)
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun localMaintenanceDoesNotGainNetworkOrForegroundRequirements() {
        val request = buildAsyncTaskWorkRequest(43, "assistant_history_backfill")
        assertEquals(NetworkType.NOT_REQUIRED, request.workSpec.constraints.requiredNetworkType)
        assertFalse(isLongRunningAsyncTask("assistant_history_backfill"))
    }

    @Test
    fun actualWorkerPromotesBeforeExecutingAssistantAndUsesPrivateOngoingNotification() = runBlocking {
        // Use the Android driver in the Android sandbox, avoiding global JDBC driver registration.
        AndroidSqliteDriver(DailySatoriDatabase.Schema, RuntimeEnvironment.getApplication(), null).use { driver ->
            val repository = AsyncTaskRepository(DailySatoriDatabase(driver))
            val id = repository.enqueue("assistant_turn", "{}", "background-test")
            var foreground: ForegroundInfo? = null
            var foregroundBeforeExecution = false
            var executed = false
            val handler = object : AsyncTaskHandler {
                override val type = "assistant_turn"
                override suspend fun execute(taskId: Long, payloadJson: String, checkpointJson: String,
                    reporter: AsyncTaskProgressReporter): AsyncTaskExecutionResult {
                    foregroundBeforeExecution = foreground != null
                    executed = true
                    return AsyncTaskExecutionResult.PermanentFailure("test_complete", "fixture only")
                }
            }
            startKoin { modules(module {
                single { repository }
                single { AsyncTaskHandlerRegistry(listOf(handler)) }
            }) }
            try {
                val worker = GenericAsyncTaskWorker(RuntimeEnvironment.getApplication(), parameters(id) { foreground = it })
                assertEquals(ListenableWorker.Result.failure(), worker.doReadyWork())
                assertTrue(executed)
                assertTrue(foregroundBeforeExecution, "Foreground promotion must finish before the AI handler starts")
                val info = assertNotNull(foreground)
                assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, info.foregroundServiceType)
                assertEquals("个人助手正在处理，可切换到其他应用…", info.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                assertTrue(info.notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
            } finally { stopKoin() }
        }
    }

    private fun parameters(taskId: Long, promoted: (ForegroundInfo) -> Unit): WorkerParameters {
        val executor = Executor { it.run() }
        val data = workDataOf(GenericAsyncTaskWorker.KEY_TASK_ID to taskId,
            GenericAsyncTaskWorker.KEY_TASK_TYPE to "assistant_turn")
        return WorkerParameters(UUID.randomUUID(), data, emptyList(), WorkerParameters.RuntimeExtras(),
            0, 0, executor, Dispatchers.IO, WorkManagerTaskExecutor(executor),
            object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? = null
            }, ProgressUpdater { _, _, _ -> SettableFuture.create<Void>().apply { set(null) } },
            ForegroundUpdater { _, _, info ->
                promoted(info)
                SettableFuture.create<Void>().apply { set(null) }
            })
    }
}

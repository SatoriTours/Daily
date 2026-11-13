package com.dailysatori.core.worker

import android.app.Application
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskHandler
import com.dailysatori.service.asynctask.AsyncTaskHandlerRegistry
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.asynctask.AsyncTaskRunOutcome
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Favourite sync is serial too: contention must back off and resume once the owner ends. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class ExternalFavoriteQueueBlockedTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val workManager get() = WorkManager.getInstance(app)

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
    }

    @After
    fun tearDown() {
        runCatching { stopKoin() }
    }

    @Test
    fun aBlockedSerialClaimBacksOffAndNeverSpins() {
        assertTrue(favoriteClaimWasBusy(AsyncTaskRunOutcome.Blocked("serial_owner", 1L), "queued", null, 10L))
        assertTrue(favoriteClaimWasBusy(AsyncTaskRunOutcome.RetryScheduled, "retrying", 5L, 10L))
        assertFalse(favoriteClaimWasBusy(AsyncTaskRunOutcome.RetryScheduled, "queued", 5_000L, 10L))
        assertFalse(favoriteClaimWasBusy(AsyncTaskRunOutcome.Succeeded, null, null, 10L))
    }

    @Test
    fun aBlockedFavouriteTaskDelaysAndThenResumesWhenTheOwnerEnds() = runBlocking {
        AndroidSqliteDriver(DailySatoriDatabase.Schema, app, null).use { driver ->
            val repository = AsyncTaskRepository(DailySatoriDatabase(driver))
            var executed = 0
            val handler = object : AsyncTaskHandler {
                override val type = "external_favorite_sync"
                override suspend fun execute(taskId: Long, payloadJson: String, checkpointJson: String,
                    reporter: AsyncTaskProgressReporter): AsyncTaskExecutionResult {
                    executed++
                    return AsyncTaskExecutionResult.Success()
                }
            }
            startKoin { modules(module {
                single { repository }
                single { AsyncTaskHandlerRegistry(listOf(handler)) }
                single { ExternalFavoriteSyncScheduler(app) }
            }) }

            val owner = repository.enqueue("external_favorite_sync", "{}", "fav-owner")
            val queued = repository.enqueue("external_favorite_sync", "{}", "fav-queued")
            assertTrue(repository.claimForSerialRun(owner, "external_favorite_sync", "owner", Long.MAX_VALUE))

            ExternalFavoriteQueueWorker(app, params()).doReadyWork()

            assertEquals(0, executed)
            assertEquals("queued", repository.getById(queued)?.status)
            val delayed = workManager.getWorkInfosByTag("androidx.work.impl.workers.ConstraintTrackingWorker").get()
            assertTrue(
                delayed.isEmpty() || delayed.all { it.state.name != "RUNNING" },
                "a blocked claim must not immediately re-run the same task",
            )

            repository.finishSuccess(owner, "")
            ExternalFavoriteQueueWorker(app, params()).doReadyWork()
            assertEquals(1, executed, "once the owner ends the queued favourite task runs")
            assertEquals("succeeded", repository.getById(queued)?.status)
        }
    }

    private fun params() = androidx.work.WorkerParameters(
        java.util.UUID.randomUUID(),
        androidx.work.workDataOf(),
        emptyList(),
        androidx.work.WorkerParameters.RuntimeExtras(),
        0, 0,
        java.util.concurrent.Executor { it.run() },
        kotlinx.coroutines.Dispatchers.IO,
        androidx.work.impl.utils.taskexecutor.WorkManagerTaskExecutor(java.util.concurrent.Executor { it.run() }),
        object : androidx.work.WorkerFactory() {
            override fun createWorker(appContext: android.content.Context, workerClassName: String,
                workerParameters: androidx.work.WorkerParameters): androidx.work.ListenableWorker? = null
        },
        androidx.work.ProgressUpdater { _, _, _ -> com.google.common.util.concurrent.SettableFuture.create<Void>().apply { set(null) } },
        androidx.work.ForegroundUpdater { _, _, _ -> com.google.common.util.concurrent.SettableFuture.create<Void>().apply { set(null) } },
    )
}


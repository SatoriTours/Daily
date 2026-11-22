package com.dailysatori.core.worker

import android.app.Application
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Tracer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import androidx.work.workDataOf
import androidx.work.testing.WorkManagerTestInitHelper
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.dailysatori.core.task.ExternalFavoriteSyncTaskPayload
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskHandler
import com.dailysatori.service.asynctask.AsyncTaskHandlerRegistry
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.asynctask.AsyncTaskRunOutcome
import com.dailysatori.shared.db.DailySatoriDatabase
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
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
    fun schedulerEnqueuesManualSyncIntoAsyncTaskFramework() = runBlocking {
        withScheduler { repo, scheduler, _ ->
            val id = requireNotNull(scheduler.enqueue(7L, "history"))
            val queued = requireNotNull(repo.getLatestByUniqueKey("external_favorite_sync:7:history"))
            assertEquals(id, queued.id)
            assertEquals("queued", queued.status)
            assertEquals("external_favorite_sync", queued.type)
            val payload = Json.decodeFromString<ExternalFavoriteSyncTaskPayload>(queued.payload_json)
            assertEquals(7L, payload.sourceId)
            assertEquals("history", payload.mode)
            assertFalse(payload.automatic)
            assertOneFavoriteHandoffRuns(repo, id)
            assertNotEquals(id, scheduler.enqueue(7L, "history"), "a finished request must not absorb a new action")
        }
    }

    @Test
    fun resubmittingTheRunningManualFamilyDoesNotDuplicateTheRowOrAppendAnotherDrain() = runBlocking {
        withScheduler { repo, scheduler, _ ->
            val id = requireNotNull(scheduler.enqueue(7L))
            assertTrue(repo.claimForRun(id, "owner", Long.MAX_VALUE))
            val tag = ExternalFavoriteQueueWorker::class.java.name
            assertEquals(1, workManager.getWorkInfosByTag(tag).get().size)
            assertEquals(id, scheduler.enqueue(7L))
            assertEquals(id, scheduler.enqueue(7L, "history"))
            assertEquals(listOf(id), repo.activeCancellationSnapshot().map { it.id })
            assertEquals(1, workManager.getWorkInfosByTag(tag).get().size)
        }
    }

    @Test
    fun terminalHandoffSchedulesOneFavoriteDrainAndStillWakesTheCrossTypeSuccessor() = runBlocking {
        withScheduler { repo, _, _ ->
            val owner = repo.enqueue("external_favorite_sync", "{}", "owner")
            val next = repo.enqueue("external_favorite_sync", "{}", "next")
            val dependent = repo.enqueue("remote_news_fetch", "{}", "dependent")
            repo.linkSequentialTasks(listOf(owner, dependent))

            ExternalFavoriteQueueWorker(app, params()).doReadyWork()

            assertEquals("succeeded", repo.getById(owner)?.status)
            assertEquals(1, workManager.getWorkInfosByTag(ExternalFavoriteQueueWorker::class.java.name).get().size)
            assertTrue(workManager.getWorkInfosByTag(asyncTaskWorkTag(next)).get().isEmpty())
            assertEquals(1, workManager.getWorkInfosByTag(asyncTaskWorkTag(dependent)).get().size)
        }
    }

    @Test
    fun bulkCancelWhileARealWorkerIsRunningHandsOffToNewSurvivorsOnce() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var runningId = -1L
        withScheduler(beforeExecute = { id ->
            if (id == runningId) {
                started.complete(Unit)
                awaitCancellation()
            }
        }) { repo, _, _ ->
            runningId = repo.enqueue("external_favorite_sync", "{}", "running")
            val queued = repo.enqueue("external_favorite_sync", "{}", "snapshot-queued")
            coroutineScope {
                val running = async { ExternalFavoriteQueueWorker(app, params()).doReadyWork() }
                withTimeout(5_000) { started.await() }
                val snapshot = repo.activeCancellationSnapshot().mapTo(hashSetOf()) { it.id }
                val survivor = repo.enqueue("external_favorite_sync", "{}", "new-favorite")
                val dependent = repo.enqueue("remote_news_fetch", "{}", "new-dependent")
                repo.linkSequentialTasks(listOf(runningId, dependent))

                AsyncTaskScheduler(app).cancelSnapshot(snapshot)
                withTimeout(5_000) { running.await() }

                assertEquals("cancelled", repo.getById(runningId)?.status)
                assertEquals("cancelled", repo.getById(queued)?.status)
                assertEquals(1, workManager.getWorkInfosByTag(asyncTaskWorkTag(dependent)).get().size)
                assertOneFavoriteHandoffRuns(repo, survivor)
            }
        }
    }

    @Test
    fun bulkCancelWithoutAWorkerHandsOffToANewFavorite() = runBlocking {
        withScheduler { repo, _, _ ->
            val owner = repo.enqueue("external_favorite_sync", "{}", "queued-owner")
            val snapshot = setOf(owner)
            val survivor = repo.enqueue("external_favorite_sync", "{}", "new-favorite")
            AsyncTaskScheduler(app).cancelSnapshot(snapshot)
            assertEquals("cancelled", repo.getById(owner)?.status)
            assertOneFavoriteHandoffRuns(repo, survivor)
        }
    }

    @Test
    fun singleCancelWithoutAWorkerHandsOffOnceEvenIfRepeated() = runBlocking {
        withScheduler { repo, _, _ ->
            val owner = repo.enqueue("external_favorite_sync", "{}", "queued-owner")
            val survivor = repo.enqueue("external_favorite_sync", "{}", "queued-survivor")
            val scheduler = AsyncTaskScheduler(app)
            scheduler.cancel(owner)
            scheduler.cancel(owner)
            assertEquals("cancelled", repo.getById(owner)?.status)
            assertOneFavoriteHandoffRuns(repo, survivor)
        }
    }

    private suspend fun assertOneFavoriteHandoffRuns(repo: AsyncTaskRepository, survivor: Long) {
        val wake = workManager.getWorkInfosByTag(ExternalFavoriteQueueWorker::class.java.name).get().single()
        assertTrue(!wake.state.isFinished)
        assertEquals("queued", repo.getById(survivor)?.status)
        ExternalFavoriteQueueWorker(app, params(wake.id)).doReadyWork()
        assertEquals("succeeded", repo.getById(survivor)?.status)
    }

    @Test
    fun legacyOneTimeRequestIsAcceptedOnceEvenIfItsTaskIsLaterCancelled() = runBlocking {
        withScheduler { repo, scheduler, _ ->
            val sourceId = 7L
            val previouslyCancelled = requireNotNull(scheduler.enqueue(sourceId))
            AsyncTaskScheduler(app).cancel(previouslyCancelled)
            val legacy = parkedLegacyOneTime(sourceId)

            legacy.doReadyWork()
            val delivered = requireNotNull(repo.getLatestByUniqueKey(externalFavoriteSyncUniqueKey(sourceId, "sync"))).id
            assertNotEquals(previouslyCancelled, delivered, "an unrelated old cancellation can't discard a valid request")
            AsyncTaskScheduler(app).cancel(delivered)
            legacy.doReadyWork()
            assertEquals(delivered, repo.getLatestByUniqueKey(externalFavoriteSyncUniqueKey(sourceId, "sync"))?.id)
            assertEquals("cancelled", repo.getById(delivered)?.status)

            assertNotEquals(delivered, scheduler.enqueue(sourceId), "a fresh user action must still work")
        }
    }

    @Test
    fun cancellingTheSourceStopsItsUndeliveredLegacyOneTimeWork() = runBlocking {
        withScheduler { repo, scheduler, sources ->
            val id = sources.save(provider = "github", displayName = "test", accountId = "7", accountName = "", authJson = "")
            val durable = requireNotNull(scheduler.enqueue(id))
            val request = parkedLegacyOneTime(id)
            scheduler.cancelSync(requireNotNull(sources.getById(id)))

            request.doReadyWork()

            assertEquals(durable, repo.getLatestByUniqueKey(externalFavoriteSyncUniqueKey(id, "sync"))?.id)
            assertEquals("cancelled", repo.getById(durable)?.status)
            assertNotEquals(durable, scheduler.enqueue(id))
        }
    }

    @Test
    fun cancellingASourceWaitsForTheOldPeriodBeforeRegisteringANewOne() = runBlocking {
        val executor = PausableExecutor()
        var beforeOperation: (String) -> Unit = {}
        val tracer = object : Tracer {
            override fun isEnabled() = true
            override fun beginSection(label: String) = beforeOperation(label)
            override fun endSection() = Unit
            override fun beginAsyncSection(label: String, cookie: Int) = Unit
            override fun endAsyncSection(label: String, cookie: Int) = Unit
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder()
            .setExecutor { it.run() }.setTaskExecutor(executor).setTracer(tracer).build())
        withScheduler { repo, scheduler, sources ->
            val id = sources.save(provider = "github", displayName = "test", accountId = "7", accountName = "", authJson = "")
            val name = externalFavoriteSyncWorkName(id, "periodic")
            scheduler.enqueuePeriodic(id, 30)
            val original = workManager.getWorkInfosForUniqueWork(name).get().single()
            assertEquals(WorkInfo.State.ENQUEUED, original.state)
            val oldWorker = ExternalFavoriteSyncWorker(app, params(original.id, legacyData(id)))
            val autoKey = externalFavoriteSyncUniqueKey(id, "sync", automatic = true)

            val tasks = (workManager as WorkManagerImpl).workTaskExecutor.serialTaskExecutor
            var oldTick: Deferred<*>? = null
            var replacementSubmittedEarly = false
            var tickStartedLookupEarly = false
            beforeOperation = operation@{ label ->
                if (label != "CancelWorkByName_$name") return@operation
                replacementSubmittedEarly = tasks.hasPendingTasks()
                oldTick = async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                    oldWorker.doReadyWork()
                }
                tickStartedLookupEarly = tasks.hasPendingTasks() || repo.getLatestByUniqueKey(autoKey) != null
            }
            executor.paused = true
            try {
                val cancellation = async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                    scheduler.cancelSync(requireNotNull(sources.getById(id)))
                }
                // Step through unrelated WorkManager operations to the exact periodic transaction.
                repeat(30) { if (oldTick == null) executor.runNext() }
                val tick = requireNotNull(oldTick)
                executor.resume()
                withTimeout(5_000) { cancellation.await(); tick.await() }
            } finally {
                executor.resume()
            }
            assertFalse(replacementSubmittedEarly, "replacement registration must wait for the periodic cancel")
            assertFalse(tickStartedLookupEarly, "the old tick must wait for the ingress lock before WorkInfo lookup")

            val oldState = workManager.getWorkInfoById(original.id).get()?.state
            assertTrue(oldState == null || oldState == WorkInfo.State.CANCELLED)
            assertNull(repo.getLatestByUniqueKey(autoKey))
            val replacement = workManager.getWorkInfosForUniqueWork(name).get().single { !it.state.isFinished }
            assertNotEquals(original.id, replacement.id)
            val newData = Data.Builder().putAll(legacyData(id))
                .putBoolean(ExternalFavoriteSyncWorker.KEY_AUTOMATIC, true).build()
            ExternalFavoriteSyncWorker(app, params(replacement.id, newData)).doReadyWork()
            assertEquals("queued", repo.getLatestByUniqueKey(autoKey)?.status)
        }
    }

    @Test
    fun unmarkedOlderPeriodicRequestKeepsItsAutomaticIdentityAcrossRuns() = runBlocking {
        withScheduler { repo, _, _ ->
            val sourceId = 7L
            val data = legacyData(sourceId)
            val request = PeriodicWorkRequestBuilder<ExternalFavoriteSyncWorker>(30, TimeUnit.MINUTES)
                .setInputData(data).setInitialDelay(30, TimeUnit.MINUTES).build()
            workManager.enqueueUniquePeriodicWork(externalFavoriteSyncWorkName(sourceId, "periodic"),
                ExistingPeriodicWorkPolicy.KEEP, request).result.get()
            val worker = ExternalFavoriteSyncWorker(app, params(request.id, data))

            worker.doReadyWork()
            val autoKey = externalFavoriteSyncUniqueKey(sourceId, "sync", automatic = true)
            val firstTick = requireNotNull(repo.getLatestByUniqueKey(autoKey)).id
            AsyncTaskScheduler(app).cancel(firstTick)
            worker.doReadyWork()

            assertNotEquals(firstTick, repo.getLatestByUniqueKey(autoKey)?.id)
            assertNull(repo.getLatestByUniqueKey(externalFavoriteSyncUniqueKey(sourceId, "sync")))
        }
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

    private suspend fun withScheduler(
        beforeExecute: suspend (Long) -> Unit = {},
        block: suspend (AsyncTaskRepository, ExternalFavoriteSyncScheduler, ExternalFavoriteSourceRepository) -> Unit,
    ) {
        AndroidSqliteDriver(DailySatoriDatabase.Schema, app, null).use { driver ->
            val db = DailySatoriDatabase(driver)
            val repo = AsyncTaskRepository(db)
            val scheduler = ExternalFavoriteSyncScheduler(app, repo, AsyncTaskScheduler(app))
            val handler = object : AsyncTaskHandler {
                override val type = "external_favorite_sync"
                override suspend fun execute(taskId: Long, payloadJson: String, checkpointJson: String,
                    reporter: AsyncTaskProgressReporter): AsyncTaskExecutionResult {
                    beforeExecute(taskId)
                    return AsyncTaskExecutionResult.Success()
                }
            }
            startKoin { modules(module {
                single { repo }
                single { scheduler }
                single { AsyncTaskHandlerRegistry(listOf(handler)) }
            }) }
            block(repo, scheduler, ExternalFavoriteSourceRepository(db))
        }
    }

    private fun legacyData(sourceId: Long) = workDataOf(
        ExternalFavoriteSyncWorker.KEY_SOURCE_ID to sourceId,
        ExternalFavoriteSyncWorker.KEY_MODE to "sync",
    )

    private fun parkedLegacyOneTime(sourceId: Long): ExternalFavoriteSyncWorker {
        val data = legacyData(sourceId)
        val request = OneTimeWorkRequestBuilder<ExternalFavoriteSyncWorker>()
            .setInputData(data).setInitialDelay(30, TimeUnit.MINUTES).build()
        workManager.enqueueUniqueWork(externalFavoriteSyncWorkName(sourceId, "sync"),
            ExistingWorkPolicy.KEEP, request).result.get()
        return ExternalFavoriteSyncWorker(app, params(request.id, data))
    }

    private fun params(id: UUID = UUID.randomUUID(), data: Data = workDataOf()) = androidx.work.WorkerParameters(
        id,
        data,
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

    private class PausableExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()
        var paused = false

        override fun execute(command: Runnable) {
            if (paused) tasks.addLast(command) else command.run()
        }

        fun runNext() = tasks.removeFirst().run()

        fun resume() {
            paused = false
            while (tasks.isNotEmpty()) runNext()
        }
    }
}


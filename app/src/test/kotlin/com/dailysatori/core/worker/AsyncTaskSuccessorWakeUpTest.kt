package com.dailysatori.core.worker

import android.app.Application
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.ForegroundUpdater
import androidx.work.ProgressUpdater
import androidx.work.impl.utils.taskexecutor.WorkManagerTaskExecutor
import androidx.work.workDataOf
import androidx.work.testing.WorkManagerTestInitHelper
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.google.common.util.concurrent.SettableFuture
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskHandler
import com.dailysatori.service.asynctask.AsyncTaskHandlerRegistry
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.shared.db.DailySatoriDatabase
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Dependency wake-up contract: a blocked successor ends normally without consuming its business attempt,
 * and the predecessor's single terminal notification (success, failure or cancellation) runs it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class AsyncTaskSuccessorWakeUpTest {
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
    fun aBlockedSuccessorEndsNormallyAndRunsOnceItsPredecessorIsTerminal() = runBlocking {
        withRepository(failPredecessor = false) { repository, executed ->
            val (predecessor, successor) = linkedTasks(repository)
            repository.claimForRun(predecessor, "owner", Long.MAX_VALUE)

            assertEquals(ListenableWorker.Result.retry(), worker(successor).doReadyWork(),
                "a blocked task keeps WorkManager retry as its persistent fallback")
            assertEquals(0, executed.value)
            assertEquals("queued", repository.getById(successor)?.status)
            assertEquals(0L, repository.getById(successor)?.attempt_count, "blocked work consumes no business attempt")

            repository.finishSuccess(predecessor, "")
            AsyncTaskScheduler(app).notifyTerminal(predecessor)

            assertTrue(
                unfinishedStates(asyncTaskWakeUpWorkName(successor, ownerIdentity(predecessor))).isNotEmpty(),
                "the terminal transition must enqueue an independent wake-up",
            )

            assertEquals(ListenableWorker.Result.success(), worker(successor).doReadyWork())
            assertEquals(1, executed.value, "the handler runs exactly once after the dependency clears")
            assertEquals("succeeded", repository.getById(successor)?.status)
        }
    }

    @Test
    fun aFailedOrCancelledPredecessorAlsoReleasesItsSuccessor() = runBlocking {
        withRepository(failPredecessor = true) { repository, executed ->
            val (predecessor, successor) = linkedTasks(repository)
            assertEquals(ListenableWorker.Result.retry(), worker(successor).doReadyWork())

            val predecessorTaskId = predecessor
            assertEquals(ListenableWorker.Result.failure(), worker(predecessor).doReadyWork())
            repository.finishFailure(predecessorTaskId, "sync_failed", "offline")
            AsyncTaskScheduler(app).notifyTerminal(predecessorTaskId)

            assertTrue(
                unfinishedStates(asyncTaskWakeUpWorkName(successor, ownerIdentity(predecessorTaskId))).isNotEmpty(),
                "a failed predecessor releases the successor too",
            )
            assertEquals(ListenableWorker.Result.success(), worker(successor).doReadyWork())
            assertEquals(1, executed.value)
        }
    }

    @Test
    fun repeatedTerminalNotificationsAndConcurrentWakeUpsRunTheHandlerOnce() = runBlocking {
        withRepository(failPredecessor = false) { repository, executed ->
            val (predecessor, successor) = linkedTasks(repository)
            repository.claimForRun(predecessor, "owner", Long.MAX_VALUE)
            repository.finishSuccess(predecessor, "")
            val tasks = AsyncTaskScheduler(app)

            tasks.notifyTerminal(predecessor)
            tasks.notifyTerminal(predecessor)
            tasks.notifyTerminal(predecessor)

            val pending = unfinishedStates(asyncTaskWakeUpWorkName(successor, ownerIdentity(predecessor))).size
            assertEquals(1, pending, "duplicate notifications collapse into one pending request")

            val first = worker(successor).doReadyWork()
            val second = worker(successor).doReadyWork()
            assertEquals(ListenableWorker.Result.success(), first)
            assertTrue(second == ListenableWorker.Result.success(), "a duplicate run ends without executing twice")
            assertEquals(1, executed.value, "the DB lease rejects the second handler execution")
        }
    }

    @Test
    fun recoveryStillFindsASuccessorWhoseWakeUpWasLost() = runBlocking {
        withRepository(failPredecessor = false) { repository, executed ->
            val (predecessor, successor) = linkedTasks(repository)
            repository.claimForRun(predecessor, "owner", Long.MAX_VALUE)
            repository.finishSuccess(predecessor, "")
            // No wake-up was enqueued (simulating a lost event); startup recovery must still find it.
            AsyncTaskScheduler(app).recoverAndEnqueueRunnable()
            assertEquals(ListenableWorker.Result.success(), worker(successor).doReadyWork())
            assertEquals(1, executed.value)
        }
    }

    @Test
    fun aSerialOwnerTerminalWakesItsSameTypeWaiters() = runBlocking {
        withRepository(failPredecessor = false) { repository, executed ->
            val owner = repository.enqueue("remote_news_fetch", "{}", "serial-owner")
            AsyncTaskScheduler(app).enqueue(owner)
            repository.claimForRun(owner, "owner", Long.MAX_VALUE)
            val waiter = repository.enqueue("remote_news_fetch", "{}", "serial-waiter")
            AsyncTaskScheduler(app).enqueue(waiter)

            val blocked = GenericAsyncTaskWorker(app, parameters(waiter)).doReadyWork()
            assertEquals(ListenableWorker.Result.retry(), blocked, "a serial wait still keeps WorkManager alive")
            assertEquals(0, executed.value)

            AsyncTaskScheduler(app).notifyTerminal(owner)

            val pending = unfinishedStates(asyncTaskWakeUpWorkName(waiter, ownerIdentity(owner))).isNotEmpty()
            assertTrue(pending, "the serial owner's terminal state must wake its waiter independently")
        }
    }

    @Test
    fun aDelayedNormalWorkDoesNotSwallowTheIndependentWake() = runBlocking {
        withRepository(failPredecessor = false) { repository, executed ->
            val (predecessor, successor) = linkedTasks(repository)
            repository.claimForRun(predecessor, "owner", Long.MAX_VALUE)
            repository.finishSuccess(predecessor, "")

            val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            AsyncTaskScheduler(app).enqueueRetry(successor, now + 3_600_000L)
            AsyncTaskScheduler(app).notifyTerminal(predecessor)

            assertTrue(unfinishedStates(asyncTaskWakeUpWorkName(successor, ownerIdentity(predecessor))).isNotEmpty(),
                "a delayed normal retry must not swallow the terminal wake-up")
            assertEquals(ListenableWorker.Result.success(), worker(successor).doReadyWork())
            assertEquals(1, executed.value)
            assertEquals("succeeded", repository.getById(successor)?.status)
        }
    }

    @Test
    fun anOwnerTerminalStateWakesOnlyItsExactSuccessors() = runBlocking {
        withRepository(failPredecessor = false) { repository, executed ->
            val exactPredecessor = repository.enqueue("remote_article_sync", "{}", "prefix-14")
            val prefixNeighbour = repository.enqueue("remote_article_sync", "{}", "prefix-149")
            val exactSuccessor = repository.enqueue("remote_news_fetch", "{}", "prefix-exact")
            val neighbourSuccessor = repository.enqueue("remote_news_fetch", "{}", "prefix-neighbour")
            repository.linkSequentialTasks(listOf(exactPredecessor, exactSuccessor))
            repository.linkSequentialTasks(listOf(prefixNeighbour, neighbourSuccessor))
            repository.claimForRun(exactPredecessor, "owner", Long.MAX_VALUE)
            repository.finishSuccess(exactPredecessor, "")

            AsyncTaskScheduler(app).notifyTerminal(exactPredecessor)

            assertTrue(
                unfinishedStates(asyncTaskWakeUpWorkName(exactSuccessor, ownerIdentity(exactPredecessor))).isNotEmpty(),
                "the exact successor is woken",
            )
            assertTrue(
                unfinishedStates(asyncTaskWakeUpWorkName(neighbourSuccessor, ownerIdentity(prefixNeighbour))).isEmpty() &&
                    unfinishedStates(asyncTaskWakeUpWorkName(neighbourSuccessor, ownerIdentity(exactPredecessor))).isEmpty(),
                "a task waiting on 149 must not be woken by predecessor 14",
            )
        }
    }

    @Test
    fun aBlockedRunDoesNotCreateItsOwnFallbackLoop() = runBlocking {
        withRepository(failPredecessor = false) { repository, executed ->
            val (predecessor, successor) = linkedTasks(repository)
            repository.claimForRun(predecessor, "owner", Long.MAX_VALUE)
            assertEquals(ListenableWorker.Result.retry(), worker(successor).doReadyWork())

            assertTrue(
                workManager.getWorkInfosForUniqueWork("async-task-blocked-" + successor).get().isEmpty(),
                "the fallback loop must be gone: WorkManager retry is the only fallback",
            )
            assertEquals("queued", repository.getById(successor)?.status)
            assertEquals(0L, repository.getById(successor)?.attempt_count, "blocked work consumes no attempt")
        }
    }

    private suspend fun linkedTasks(repository: AsyncTaskRepository): Pair<Long, Long> {
        val predecessor = repository.enqueue("remote_article_sync", "{}", "wake-predecessor")
        val successor = repository.enqueue("remote_news_fetch", "{}", "wake-successor")
        repository.linkSequentialTasks(listOf(predecessor, successor))
        return predecessor to successor
    }

    private fun worker(taskId: Long) = GenericAsyncTaskWorker(app, parameters(taskId))

    private fun ownerIdentity(taskId: Long): String =
        taskId.toString() + "-" + (runBlocking { currentRepository?.getById(taskId)?.finished_at } ?: 0L)

    private var currentRepository: AsyncTaskRepository? = null

    private fun unfinishedStates(name: String) = workManager.getWorkInfosForUniqueWork(name).get()
        .filter { !it.state.isFinished }

    private class Counter { var value = 0 }

    private fun withRepository(
        failPredecessor: Boolean,
        block: suspend (AsyncTaskRepository, Counter) -> Unit,
    ) = runBlocking {
        AndroidSqliteDriver(DailySatoriDatabase.Schema, app, null).use { driver ->
            val repository = AsyncTaskRepository(DailySatoriDatabase(driver))
            currentRepository = repository
            val executed = Counter()
            val newsHandler = object : AsyncTaskHandler {
                override val type = "remote_news_fetch"
                override suspend fun execute(taskId: Long, payloadJson: String, checkpointJson: String,
                    reporter: AsyncTaskProgressReporter): AsyncTaskExecutionResult {
                    executed.value++
                    return AsyncTaskExecutionResult.Success()
                }
            }
            val syncHandler = object : AsyncTaskHandler {
                override val type = "remote_article_sync"
                override suspend fun execute(taskId: Long, payloadJson: String, checkpointJson: String,
                    reporter: AsyncTaskProgressReporter): AsyncTaskExecutionResult = if (failPredecessor) {
                    AsyncTaskExecutionResult.PermanentFailure("sync_failed", "offline")
                } else {
                    AsyncTaskExecutionResult.Success()
                }
            }
            startKoin { modules(module {
                single { repository }
                single { AsyncTaskHandlerRegistry(listOf(newsHandler, syncHandler)) }
            }) }
            block(repository, executed)
        }
    }

    private fun parameters(taskId: Long): WorkerParameters {
        val executor = java.util.concurrent.Executor { it.run() }
        val data = workDataOf(
            GenericAsyncTaskWorker.KEY_TASK_ID to taskId,
            GenericAsyncTaskWorker.KEY_TASK_TYPE to "remote_news_fetch",
        )
        return WorkerParameters(UUID.randomUUID(), data, emptyList(), WorkerParameters.RuntimeExtras(),
            0, 0, executor, Dispatchers.IO, WorkManagerTaskExecutor(executor),
            object : WorkerFactory() {
                override fun createWorker(appContext: android.content.Context, workerClassName: String,
                    workerParameters: WorkerParameters): ListenableWorker? = null
            }, ProgressUpdater { _, _, _ -> SettableFuture.create<Void>().apply { set(null) } },
            ForegroundUpdater { _, _, _ -> SettableFuture.create<Void>().apply { set(null) } })
    }
}


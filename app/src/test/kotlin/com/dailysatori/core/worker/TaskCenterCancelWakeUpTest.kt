package com.dailysatori.core.worker

import android.app.Application
import androidx.work.ListenableWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.ForegroundUpdater
import androidx.work.ProgressUpdater
import androidx.work.impl.utils.taskexecutor.WorkManagerTaskExecutor
import androidx.work.workDataOf
import androidx.work.testing.WorkManagerTestInitHelper
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.dailysatori.core.task.AsyncTaskLogStore
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import com.dailysatori.data.repository.RemoteNewsSourceRepository
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskHandler
import com.dailysatori.service.asynctask.AsyncTaskHandlerRegistry
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.ui.feature.settings.taskcenter.TaskCenterBulkCancelState
import com.dailysatori.ui.feature.settings.taskcenter.TaskCenterViewModel
import com.google.common.util.concurrent.SettableFuture
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
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
import kotlin.test.assertTrue

/** The task-centre cancel button is a terminal transition and must release its successors. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class TaskCenterCancelWakeUpTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val workManager get() = WorkManager.getInstance(app)

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        runCatching { stopKoin() }
    }

    @Test
    fun cancellingFromTheTaskCentreWakesTheSuccessorAndItRuns() = runTest {
        val storeRoot = File.createTempFile("task-centre-cancel", "dir").also { it.delete(); it.mkdirs() }
        try {
            AndroidSqliteDriver(DailySatoriDatabase.Schema, app, null).use { driver ->
                val db = DailySatoriDatabase(driver)
                val repository = AsyncTaskRepository(db)
                val scheduler = AsyncTaskScheduler(app)
                var executed = 0
                val newsHandler = object : AsyncTaskHandler {
                    override val type = "remote_news_fetch"
                    override suspend fun execute(taskId: Long, payloadJson: String, checkpointJson: String,
                        reporter: AsyncTaskProgressReporter): AsyncTaskExecutionResult {
                        executed++
                        return AsyncTaskExecutionResult.Success()
                    }
                }
                val syncHandler = object : AsyncTaskHandler {
                    override val type = "remote_article_sync"
                    override suspend fun execute(taskId: Long, payloadJson: String, checkpointJson: String,
                        reporter: AsyncTaskProgressReporter): AsyncTaskExecutionResult = AsyncTaskExecutionResult.Success()
                }
                startKoin { modules(module {
                    single { repository }
                    single { AsyncTaskHandlerRegistry(listOf(newsHandler, syncHandler)) }
                }) }

                val predecessor = repository.enqueue("remote_article_sync", "{}", "centre-predecessor")
                val successor = repository.enqueue("remote_news_fetch", "{}", "centre-successor")
                repository.linkSequentialTasks(listOf(predecessor, successor))
                repository.claimForRun(predecessor, "owner", Long.MAX_VALUE)

                val viewModel = TaskCenterViewModel(
                    repository = repository,
                    logStore = AsyncTaskLogStore(storeRoot),
                    scheduler = scheduler,
                    remoteSourceRepository = RemoteNewsSourceRepository(db),
                    favoriteSourceRepository = ExternalFavoriteSourceRepository(db),
                )
                viewModel.cancel(predecessor)
                // The view model cancels on Dispatchers.IO; wait (bounded) for the terminal transition.
                val cancelled = withTimeoutOrNull(3_000) {
                    while (repository.getById(predecessor)?.status != "cancelled") delay(20)
                    true
                }
                assertTrue(cancelled == true, "the task-centre cancel must reach the repository")

                val finishedAt = repository.getById(predecessor)?.finished_at ?: Clock.System.now().toEpochMilliseconds()
                val wakeName = asyncTaskWakeUpWorkName(successor, predecessor.toString() + "-" + finishedAt)
                val pending = withTimeoutOrNull(3_000) {
                    var found = workManager.getWorkInfosForUniqueWork(wakeName).get().filter { !it.state.isFinished }
                    while (found.isEmpty()) { delay(20); found = workManager.getWorkInfosForUniqueWork(wakeName).get().filter { !it.state.isFinished } }
                    found
                }
                assertTrue(pending != null, "the task-centre cancel must release an independent wake-up")

                assertEquals(ListenableWorker.Result.success(), worker(successor).doReadyWork())
                assertEquals(1, executed, "the successor handler runs after the cancel release")
                assertEquals("succeeded", repository.getById(successor)?.status)
            }
        } finally {
            storeRoot.deleteRecursively()
        }
    }

    @Test
    fun bulkConfirmationCancelsOnlyStillActiveSnapshotTasksAndReleasesSurvivors() = withBulkTaskCenter { repository, viewModel ->
        val running = repository.enqueue("external_favorite_sync", "{}")
        repository.claimForRun(running, "favorite-owner", Long.MAX_VALUE)
        val retrying = repository.enqueue("remote_news_fetch", "{}")
        repository.claimForRun(retrying, "retry-owner", Long.MAX_VALUE)
        repository.markRetry(retrying, "network", "retry later", Long.MAX_VALUE)
        val completing = repository.enqueue("save_article", "{}")
        repository.claimForRun(completing, "save-owner", Long.MAX_VALUE)
        val queued = List(300) { repository.enqueue("remote_news_fetch", "{}") }
        repository.linkSequentialTasks(queued)
        viewModel.toggleType("save_article")

        viewModel.requestBulkCancel()
        viewModel.requestBulkCancel()
        val preview = withTimeout(5_000) { viewModel.bulkCancel.first { it is TaskCenterBulkCancelState.Confirm } }
            as TaskCenterBulkCancelState.Confirm
        assertEquals(300, preview.queued)
        assertEquals(2, preview.running)
        assertEquals(1, preview.retrying)
        viewModel.dismissBulkCancel()
        viewModel.confirmBulkCancel()
        assertEquals("running", repository.getById(running)?.status)
        assertEquals("queued", repository.getById(queued.last())?.status)

        viewModel.requestBulkCancel()
        withTimeout(5_000) { viewModel.bulkCancel.first { it is TaskCenterBulkCancelState.Confirm } }
        repository.finishSuccess(completing, """{"saved":true}""")
        val newSuccessor = repository.enqueue("remote_news_fetch", "{}")
        repository.linkSequentialTasks(listOf(queued.last(), newSuccessor))
        val taggedWork = buildAsyncTaskWorkRequest(queued.last(), "remote_news_fetch", initialDelayMs = 60_000)
        workManager.enqueue(taggedWork).result.get()
        val futurePeriodic = PeriodicWorkRequestBuilder<GenericAsyncTaskWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.DAYS).build()
        workManager.enqueue(futurePeriodic).result.get()
        val statusesAtStop = CompletableDeferred<List<String?>>()
        val favoriteJob = Job()
        favoriteJob.invokeOnCompletion {
            statusesAtStop.complete(listOf(running, retrying, queued.first(), queued.last()).map {
                repository.getById(it)?.status
            })
        }
        ExternalFavoriteTaskCancellationRegistry.register(running, favoriteJob)
        try {
            viewModel.confirmBulkCancel()
            viewModel.confirmBulkCancel()
            withTimeout(5_000) { viewModel.bulkCancel.first { it == TaskCenterBulkCancelState.Idle } }
            assertTrue(favoriteJob.isCancelled)
            assertEquals(List(4) { "cancelled" }, withTimeout(5_000) { statusesAtStop.await() })
            assertEquals("cancelled", repository.getById(running)?.status)
            assertEquals("cancelled", repository.getById(retrying)?.status)
            assertTrue(queued.all { repository.getById(it)?.status == "cancelled" })
            assertEquals("succeeded", repository.getById(completing)?.status)
            assertEquals("""{"saved":true}""", repository.getById(completing)?.result_json)
            assertEquals("queued", repository.getById(newSuccessor)?.status)
            val owner = repository.getById(queued.last())!!
            val wakeName = asyncTaskWakeUpWorkName(newSuccessor, "${owner.id}-${owner.finished_at}")
            assertEquals(1, workManager.getWorkInfosForUniqueWork(wakeName).get().size)
            assertEquals(WorkInfo.State.CANCELLED, workManager.getWorkInfoById(taggedWork.id).get()?.state)
            assertEquals(WorkInfo.State.ENQUEUED, workManager.getWorkInfoById(futurePeriodic.id).get()?.state)
            val head = repository.getById(queued.first())!!
            val cancelledWake = asyncTaskWakeUpWorkName(queued[1], "${head.id}-${head.finished_at}")
            assertTrue(workManager.getWorkInfosForUniqueWork(cancelledWake).get().isEmpty())
        } finally {
            ExternalFavoriteTaskCancellationRegistry.unregister(running, favoriteJob)
        }
    }

    @Test
    fun bulkCancelWithNoActiveTasksNeverOffersConfirmation() = withBulkTaskCenter { repository, viewModel ->
        val historical = repository.enqueue("save_article", "{}").also(repository::cancel)
        viewModel.requestBulkCancel()
        assertEquals(TaskCenterBulkCancelState.Empty, withTimeout(5_000) {
            viewModel.bulkCancel.first { it != TaskCenterBulkCancelState.Loading }
        })
        viewModel.confirmBulkCancel()
        assertEquals("cancelled", repository.getById(historical)?.status)
    }

    private fun withBulkTaskCenter(
        block: suspend (AsyncTaskRepository, TaskCenterViewModel) -> Unit,
    ) = runBlocking {
        val root = kotlin.io.path.createTempDirectory("task-center-bulk-cancel").toFile()
        try {
            AndroidSqliteDriver(DailySatoriDatabase.Schema, app, null).use { driver ->
                val db = DailySatoriDatabase(driver)
                val repository = AsyncTaskRepository(db)
                startKoin { modules(module { single { repository } }) }
                block(repository, TaskCenterViewModel(
                    repository, AsyncTaskLogStore(root), AsyncTaskScheduler(app),
                    RemoteNewsSourceRepository(db), ExternalFavoriteSourceRepository(db),
                ))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private fun worker(taskId: Long) = GenericAsyncTaskWorker(app, parameters(taskId))

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

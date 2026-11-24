package com.dailysatori.ui.feature.settings.taskcenter

import android.app.Application
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.dailysatori.core.task.AsyncTaskLogStore
import com.dailysatori.core.worker.AsyncTaskScheduler
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import com.dailysatori.data.repository.RemoteNewsSourceRepository
import com.dailysatori.service.asynctask.AsyncTaskStatus
import com.dailysatori.service.asynctask.AsyncTaskType
import com.dailysatori.shared.db.Async_task
import com.dailysatori.shared.db.DailySatoriDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class TaskCenterRetryTest {
    private val app get() = RuntimeEnvironment.getApplication()

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
    fun onlyTerminalRetryableTasksThatWereNotSupersededCanRetry() {
        assertTrue(taskCenterCanRetry(task(status = AsyncTaskStatus.failed.name), false))
        assertTrue(taskCenterCanRetry(task(status = AsyncTaskStatus.cancelled.name), false))
        assertFalse(taskCenterCanRetry(task(status = AsyncTaskStatus.failed.name), true))
        assertFalse(taskCenterCanRetry(task(status = AsyncTaskStatus.succeeded.name), false))
        assertFalse(taskCenterCanRetry(task(status = AsyncTaskStatus.queued.name), false))
        assertFalse(taskCenterCanRetry(task(status = AsyncTaskStatus.running.name), false))
        assertFalse(taskCenterCanRetry(task(type = AsyncTaskType.external_favorite_sync.name), false))
        assertFalse(taskCenterCanRetry(task(type = AsyncTaskType.diary_tag_generate.name), false))
        listOf(
            AsyncTaskType.article_ai,
            AsyncTaskType.remote_news_fetch,
            AsyncTaskType.remote_article_sync,
            AsyncTaskType.news_opportunity_analysis,
            AsyncTaskType.save_article,
            AsyncTaskType.remote_article_reprocess,
            AsyncTaskType.article_cover_download,
            AsyncTaskType.book_viewpoint_generate,
        ).forEach { assertTrue(taskCenterCanRetry(task(type = it.name), false), it.name) }
    }

    @Test
    fun retryingAFailedTaskEnqueuesAFreshRowAndKeepsItsPayload() = runBlocking { withDatabase { db ->
        val repository = AsyncTaskRepository(db)
        val failed = repository.enqueue(
            type = AsyncTaskType.news_opportunity_analysis.name,
            payloadJson = """{"sourceId":3}""",
            uniqueKey = "retry-opportunity",
            maxAttempts = 3,
        )
        claimAndFail(repository, failed)
        val viewModel = viewModel(db, repository)

        viewModel.retryTask(assertNotNull(repository.getById(failed)))

        val retried = awaitTask(repository, failed)
        assertNotNull(retried, "the retry must insert a new task row")
        assertEquals(AsyncTaskStatus.queued.name, retried.status)
        assertEquals(AsyncTaskType.news_opportunity_analysis.name, retried.type)
        assertEquals("""{"sourceId":3}""", retried.payload_json)
        assertEquals("retry-opportunity", retried.unique_key)
        assertEquals(3L, retried.max_attempts)
        assertEquals(AsyncTaskStatus.failed.name, repository.getById(failed)?.status, "history stays untouched")
    } }

    @Test
    fun nonRetryableAndSucceededTasksAreNeverRequeued() = runBlocking { withDatabase { db ->
        val repository = AsyncTaskRepository(db)
        val unsupported = repository.enqueue(AsyncTaskType.external_favorite_sync.name, "{}", "retry-favorite")
        claimAndFail(repository, unsupported)
        val succeeded = repository.enqueue(AsyncTaskType.remote_news_fetch.name, "{}", "retry-news")
        assertTrue(repository.claimForRun(succeeded, "owner", Long.MAX_VALUE))
        repository.finishSuccess(succeeded, "{}")
        val viewModel = viewModel(db, repository)

        viewModel.retryTask(assertNotNull(repository.getById(unsupported)))
        viewModel.retryTask(assertNotNull(repository.getById(succeeded)))

        assertNull(awaitTask(repository, unsupported, attempts = 3), "unsupported types must not be re-enqueued")
        assertNull(awaitTask(repository, succeeded, attempts = 3), "succeeded tasks must not be re-enqueued")
    } }

    @Test
    fun cancelledTaskReplacedBySameKeySuccessIsNotRetryable() {
        assertTrue(
            taskFailureIsSuperseded(
                110,
                AsyncTaskStatus.cancelled.name,
                118,
                AsyncTaskStatus.succeeded.name,
            ),
        )
        assertFalse(
            taskFailureIsSuperseded(
                110,
                AsyncTaskStatus.cancelled.name,
                119,
                AsyncTaskStatus.failed.name,
            ),
        )
        assertFalse(taskCenterCanRetry(task(status = AsyncTaskStatus.cancelled.name), true))
    }

    @Test
    fun staleCancelledSnapshotIsRejectedAfterSameKeySucceeds() = runBlocking { withDatabase { db ->
        val repository = AsyncTaskRepository(db)
        val key = "retry-superseded-cancelled"
        val cancelledId = repository.enqueue(AsyncTaskType.remote_news_fetch.name, "{}", key)
        assertTrue(repository.claimForRun(cancelledId, "owner", Long.MAX_VALUE))
        assertEquals(
            listOf(cancelledId),
            repository.cancelSupersededRunningTasks(
                AsyncTaskType.remote_news_fetch.name,
                "superseded",
                "已被替代",
            ) { it.id == cancelledId },
        )
        val staleCancelled = assertNotNull(repository.getById(cancelledId))
        assertEquals(AsyncTaskStatus.cancelled.name, staleCancelled.status)

        val succeededId = repository.enqueue(AsyncTaskType.remote_news_fetch.name, "{}", key)
        assertTrue(repository.claimForRun(succeededId, "other-owner", Long.MAX_VALUE))
        repository.finishSuccess(succeededId, "{}")
        val viewModel = viewModel(db, repository)

        viewModel.retryTaskNow(staleCancelled)

        assertEquals(
            succeededId,
            repository.getLatestByUniqueKey(key)?.id,
            "a cancelled task already replaced by a same-key success must not be re-enqueued",
        )
        assertEquals(AsyncTaskStatus.cancelled.name, repository.getById(cancelledId)?.status, "history stays untouched")
    } }

    @Test
    fun staleFailedSnapshotIsRejectedAfterSameKeySucceeds() = runBlocking { withDatabase { db ->
        val repository = AsyncTaskRepository(db)
        val key = "retry-superseded-failed"
        val failedId = repository.enqueue(AsyncTaskType.news_opportunity_analysis.name, """{"sourceId":3}""", key)
        claimAndFail(repository, failedId)
        val staleFailed = assertNotNull(repository.getById(failedId))

        val succeededId = repository.enqueue(AsyncTaskType.news_opportunity_analysis.name, """{"sourceId":3}""", key)
        assertTrue(repository.claimForRun(succeededId, "other-owner", Long.MAX_VALUE))
        repository.finishSuccess(succeededId, "{}")
        val viewModel = viewModel(db, repository)

        viewModel.retryTaskNow(staleFailed)

        assertEquals(
            succeededId,
            repository.getLatestByUniqueKey(key)?.id,
            "a failed task already replaced by a same-key success must not be re-enqueued",
        )
        assertEquals(AsyncTaskStatus.failed.name, repository.getById(failedId)?.status, "history stays untouched")
    } }

    @Test
    fun articleAiRetryFallsBackToTheTaskQueueWithoutAQueue() = runBlocking { withDatabase { db ->
        val repository = AsyncTaskRepository(db)
        val failed = repository.enqueue(AsyncTaskType.article_ai.name, """{"articleId":9}""", "retry-article-ai")
        claimAndFail(repository, failed)
        val viewModel = viewModel(db, repository)

        viewModel.retryTask(assertNotNull(repository.getById(failed)))

        val retried = awaitTask(repository, failed)
        assertNotNull(retried)
        assertEquals(AsyncTaskType.article_ai.name, retried.type)
        assertEquals("""{"articleId":9}""", retried.payload_json)
    } }

    @Test
    fun entryFilterCanStartFromOneTaskType() = runBlocking { withDatabase { db ->
        val repository = AsyncTaskRepository(db)
        val viewModel = viewModel(db, repository)

        viewModel.applyEntryFilter(recentFailures = false, initialType = AsyncTaskType.article_ai.name)

        val state = viewModel.state.first { it.tasksLoaded }
        assertEquals(TaskCenterTab.Current, state.tab)
        assertEquals(setOf(AsyncTaskType.article_ai.name), state.types)
        assertNull(state.updatedSince)
    } }

    private suspend fun awaitTask(repository: AsyncTaskRepository, originalId: Long, attempts: Int = 100): Async_task? {
        var found: Async_task? = null
        withTimeoutOrNull(3_000) {
            repeat(attempts) {
                val latest = repository.getById(originalId)?.unique_key?.let(repository::getLatestByUniqueKey)
                if (latest != null && latest.id != originalId) {
                    found = latest
                    return@withTimeoutOrNull
                }
                delay(20)
            }
        }
        return found
    }

    private fun claimAndFail(repository: AsyncTaskRepository, taskId: Long) {
        assertTrue(repository.claimForRun(taskId, "owner", Long.MAX_VALUE))
        repository.finishFailure(taskId, "boom", "失败")
        assertEquals(AsyncTaskStatus.failed.name, repository.getById(taskId)?.status)
    }

    private fun viewModel(db: DailySatoriDatabase, repository: AsyncTaskRepository): TaskCenterViewModel {
        val storeRoot = File.createTempFile("task-centre-retry", "dir").also { it.delete(); it.mkdirs(); it.deleteOnExit() }
        startKoin { modules(module { single { repository } }) }
        return TaskCenterViewModel(
            repository = repository,
            logStore = AsyncTaskLogStore(storeRoot),
            scheduler = AsyncTaskScheduler(app),
            remoteSourceRepository = RemoteNewsSourceRepository(db),
            favoriteSourceRepository = ExternalFavoriteSourceRepository(db),
        )
    }

    private fun task(
        id: Long = 1L,
        type: String = AsyncTaskType.remote_news_fetch.name,
        status: String = AsyncTaskStatus.failed.name,
        payloadJson: String = "{}",
        uniqueKey: String? = "task-$id",
        maxAttempts: Long = 5L,
    ) = Async_task(
        id = id,
        type = type,
        status = status,
        payload_json = payloadJson,
        checkpoint_json = "",
        result_json = "",
        progress_current = 0,
        progress_total = 0,
        progress_message = "",
        attempt_count = 1,
        max_attempts = maxAttempts,
        priority = 0,
        unique_key = uniqueKey,
        batch_id = null,
        run_after_ms = null,
        lease_owner = null,
        lease_until_ms = null,
        started_at = null,
        finished_at = null,
        last_error_code = "",
        last_error_message = "",
        created_at = 1L,
        updated_at = 1L,
    )

    private suspend fun withDatabase(block: suspend (DailySatoriDatabase) -> Unit) {
        AndroidSqliteDriver(DailySatoriDatabase.Schema, app, null).use { driver -> block(DailySatoriDatabase(driver)) }
    }
}

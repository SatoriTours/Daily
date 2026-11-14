package com.dailysatori.service.diary

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.DiaryThreadRepository
import com.dailysatori.data.repository.DiaryThoughtRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiaryThoughtServiceTest {
    @Test
    fun workerCancellationResumesFromSavedEvidence() = withFixture { diaries, threads, repository, scope ->
        val id = diaries.create("先做重要的事情")
        val merging = CompletableDeferred<Unit>()
        val response = Json.encodeToString(DiaryThoughtBatch(listOf(DiaryThought(
            "做事准则", "先做重要的事情", "明确表达", listOf(DiaryThoughtEvidence(id, "先做重要的事情")),
        ))))
        var calls = 0
        val service = DiaryThoughtService(threads, repository, DiaryThoughtGenerator { _, _ ->
            calls++
            if (calls == 2) {
                merging.complete(Unit)
                awaitCancellation()
            }
            response
        })
        service.start(scope) {}
        val worker = scope.launch { service.runPendingRefresh() }
        withTimeout(8_000) { merging.await() }
        worker.cancelAndJoin()
        assertFalse(service.state.value.isUpdating)
        assertEquals(null, service.state.value.error)
        assertEquals(1, repository.load().chunks.size)
        withTimeout(8_000) { service.runPendingRefresh() }
        val completed = withTimeout(8_000) { service.state.first { it.archive.generatedAt > 0 && !it.isStale } }
        assertFalse(completed.isPaused)
        assertEquals(3, calls, "恢复只重做中断的合并请求，不重复读取日记")
    }

    @Test
    fun manualRefreshAndDuplicateWorkerDoNotRegenerateCompletedSnapshot() = withFixture { diaries, threads, repository, scope ->
        val id = diaries.create("先做重要的事情")
        var calls = 0
        val service = DiaryThoughtService(threads, repository, DiaryThoughtGenerator { _, _ ->
            calls++
            Json.encodeToString(DiaryThoughtBatch(listOf(DiaryThought(
                "做事准则", "先做重要的事情", "明确表达", listOf(DiaryThoughtEvidence(id, "先做重要的事情")),
            ))))
        })
        service.startWithWorker(scope)
        val original = withTimeout(8_000) { service.state.first { it.archive.generatedAt > 0 } }
        service.requestRefresh()
        withTimeout(8_000) { service.state.first { it.archive.generatedAt > original.archive.generatedAt } }
        assertEquals(3, calls)
        withTimeout(3_000) { service.runPendingRefresh() }
        assertEquals(3, calls, "重复调度不能再次执行已完成的手动请求")
    }

    @Test
    fun analysisRunsWithoutAnyScreenAndOnlyWhenWorkerStarts() = withFixture { diaries, threads, repository, scope ->
        diaries.create("先做重要的事情")
        val scheduled = CompletableDeferred<Unit>()
        var calls = 0
        val service = DiaryThoughtService(threads, repository, DiaryThoughtGenerator { _, _ ->
            calls++
            "{\"thoughts\":[]}"
        })
        service.start(scope) { scheduled.complete(Unit) }
        withTimeout(3_000) { scheduled.await() }
        assertEquals(0, calls, "Application 观察者不能自行请求 AI")
        withTimeout(8_000) { service.runPendingRefresh() }
        assertEquals(1, calls)
        assertFalse(service.state.value.isStale)
        assertFalse(service.state.value.isPaused)
    }

    @Test
    fun runningWorkerSwitchesToEditedDiaryWithoutPublishingOldSnapshot() = withFixture { diaries, threads, repository, scope ->
        val id = diaries.create("先做重要的事情")
        val reading = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        var calls = 0
        val service = DiaryThoughtService(threads, repository, DiaryThoughtGenerator { _, _ ->
            calls++
            if (calls == 1) {
                reading.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
            "{\"thoughts\":[]}"
        })
        service.start(scope) {}
        val worker = scope.launch { service.runPendingRefresh() }
        withTimeout(8_000) { reading.await() }
        diaries.update(id, "今天决定先休息", null, null, null)
        withTimeout(8_000) { worker.join() }
        assertTrue(cancelled.isCompleted)
        assertEquals(2, calls)
        val diary = diaries.getById(id)!!
        val source = DiaryThoughtSource(id, diary.content, diary.created_at)
        assertEquals(diaryThoughtFingerprint(listOf(source), ""), repository.load().fingerprint)
        assertEquals(1, repository.load().chunks.size)
        assertFalse(service.state.value.isStale)
    }

    @Test
    fun aiTimeoutAutomaticallyRecoversWithoutKillingTheDiaryObserver() = withFixture { diaries, threads, repository, scope ->
        diaries.create("先做重要的事情")
        var calls = 0
        val service = DiaryThoughtService(threads, repository, DiaryThoughtGenerator { _, _ ->
            calls++
            if (calls == 1) withTimeout(1) { awaitCancellation() }
            "{\"thoughts\":[]}"
        })
        service.startWithWorker(scope)
        val recovered = withTimeout(10_000) {
            service.state.first { it.archive.generatedAt > 0 && !it.isUpdating }
        }
        assertFalse(recovered.isStale)
        assertTrue(calls >= 2)
        diaries.create("今天决定先休息")
        withTimeout(8_000) { service.state.first { it.archive.diaryCount == 2 && !it.isStale } }
    }

    @Test
    fun diaryEditsInvalidateOldThoughtsAndRetryPublishesNewEvidence() = withFixture { diaries, threads, repository, scope ->
        val id = diaries.create("先做重要的事情")
        var quote = "先做重要的事情"
        var fail = false
        val service = DiaryThoughtService(threads, repository, DiaryThoughtGenerator { _, _ ->
            if (fail) error("网络失败")
            Json.encodeToString(DiaryThoughtBatch(listOf(DiaryThought(
                "做事准则", quote, "明确表达", listOf(DiaryThoughtEvidence(id, quote)),
            ))))
        })
        service.startWithWorker(scope)
        withTimeout(8_000) { service.state.first { it.archive.thoughts.isNotEmpty() } }
        fail = true
        diaries.update(id, "今天决定先休息", null, null, null)
        val failed = withTimeout(8_000) { service.state.first { it.error != null } }
        assertTrue(failed.isStale)
        assertTrue(failed.archive.thoughts.isEmpty())
        assertTrue(repository.load().thoughts.isEmpty())
        quote = "今天决定先休息"
        fail = false
        service.requestRefresh()
        val updated = withTimeout(8_000) { service.state.first { it.archive.thoughts.firstOrNull()?.statement == quote } }
        assertFalse(updated.isStale)
        assertEquals(quote, repository.load().thoughts.single().evidence.single().quote)
    }

    @Test
    fun deletingDiariesClearsPersistedProfileAndPreservesUserCorrections() = withFixture { diaries, threads, repository, scope ->
        val id = diaries.create("先做重要的事情")
        val thought = DiaryThought("做事准则", "先做重要的事情", "明确表达", listOf(DiaryThoughtEvidence(id, "先做重要的事情")))
        repository.save(DiaryThoughtArchive(thoughts = listOf(thought)))
        repository.saveCorrections("不要把临时决定当成长久原则")
        diaries.delete(id)
        val service = DiaryThoughtService(threads, repository, DiaryThoughtGenerator { _, _ -> error("空日记库无需 AI") })
        service.startWithWorker(scope)
        withTimeout(8_000) { service.state.first { it.archive.fingerprint.isNotBlank() && !it.isStale } }
        assertTrue(repository.load().thoughts.isEmpty())
        assertTrue(repository.load().chunks.isEmpty())
        assertEquals("不要把临时决定当成长久原则", repository.corrections())
    }

    @Test
    fun restartWithUnchangedDiariesLoadsArchiveWithoutAiCalls() = withFixture { diaries, threads, repository, scope ->
        val id = diaries.create("先做重要的事情")
        val source = diaries.getById(id)!!
        val fingerprint = diaryThoughtFingerprint(listOf(DiaryThoughtSource(id, source.content, source.created_at)), "")
        repository.save(DiaryThoughtArchive(fingerprint = fingerprint, diaryCount = 1, generatedAt = 123))
        val service = DiaryThoughtService(threads, repository, DiaryThoughtGenerator { _, _ -> error("不应重复生成") })
        service.startWithWorker(scope)
        val loaded = withTimeout(8_000) { service.state.first { it.archive.generatedAt == 123L } }
        assertFalse(loaded.isStale)
        assertEquals(1, loaded.archive.diaryCount)
    }

    @Test
    fun workerWithoutLoadedSourcesReturnsInsteadOfHangingForever() = withFixture { _, threads, repository, _ ->
        // 进程刚启动、Application 观察者尚未填入来源快照时，后台执行者必须有界返回，
        // 否则会一直持有 dataSync 前台等待一个可能永远不来的请求。
        val service = DiaryThoughtService(
            threads, repository,
            DiaryThoughtGenerator { _, _ -> error("无请求不应调用 AI") },
            requestWaitMs = 200,
        )

        val outcome = withTimeout(2_000) { service.runPendingRefresh() }

        assertEquals(DiaryRefreshOutcome.NotReady, outcome, "来源未加载时必须有界返回，不能无限挂起")
        assertFalse(service.awaitPendingRequest())
    }

    @Test
    fun loadedSnapshotWithoutStaleSourcesReportsNothingPending() = withFixture { diaries, threads, repository, scope ->
        val id = diaries.create("先做重要的事情")
        val source = diaries.getById(id)!!
        val fingerprint = diaryThoughtFingerprint(listOf(DiaryThoughtSource(id, source.content, source.created_at)), "")
        repository.save(DiaryThoughtArchive(fingerprint = fingerprint, diaryCount = 1, generatedAt = 123))
        val service = DiaryThoughtService(
            threads, repository,
            DiaryThoughtGenerator { _, _ -> error("未变化不应调用 AI") },
            requestWaitMs = 5_000,
        )
        service.start(scope) {}

        val outcome = withTimeout(8_000) { service.runPendingRefresh() }

        assertEquals(DiaryRefreshOutcome.NothingPending, outcome, "已加载且没有变化时必须与未加载区分")
        assertEquals(123L, repository.load().generatedAt)
    }

    @Test
    fun notReadyAttemptDoesNotDropTheLaterRequest() = withFixture { diaries, threads, repository, scope ->
        diaries.create("先做重要的事情")
        var calls = 0
        val service = DiaryThoughtService(
            threads, repository,
            DiaryThoughtGenerator { _, _ -> calls++; "{\"thoughts\":[]}" },
            requestWaitMs = 200,
        )

        assertEquals(DiaryRefreshOutcome.NotReady, withTimeout(2_000) { service.runPendingRefresh() })
        assertEquals(0, calls, "未就绪时不能提前生成")

        service.start(scope) {}
        assertEquals(DiaryRefreshOutcome.Completed, withTimeout(10_000) { service.runPendingRefresh() })
        assertEquals(1, calls, "未就绪那一次不能吞掉随后到达的请求")
    }

    @Test
    fun pendingWorkReportsCompleted() = withFixture { diaries, threads, repository, scope ->
        diaries.create("先做重要的事情")
        val service = DiaryThoughtService(
            threads, repository,
            DiaryThoughtGenerator { _, _ -> "{\"thoughts\":[]}" },
            requestWaitMs = 5_000,
        )
        service.start(scope) {}

        val outcome = withTimeout(10_000) { service.runPendingRefresh() }

        assertEquals(DiaryRefreshOutcome.Completed, outcome)
    }

    @Test
    fun sourceObservationFailureEndsTheWorkerInsteadOfHanging() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val job = SupervisorJob()
        try {
            DailySatoriDatabase.Schema.create(driver)
            val database = DailySatoriDatabase(driver)
            val threads = DiaryThreadRepository(database, driver)
            val repository = DiaryThoughtRepository(SettingRepository(database))
            // 数据源不可用：观察必然失败，后台执行者不能因此永久等待。
            driver.close()
            val service = DiaryThoughtService(
                threads, repository,
                DiaryThoughtGenerator { _, _ -> error("无请求不应调用 AI") },
                requestWaitMs = 200,
            )
            service.start(CoroutineScope(job + Dispatchers.IO)) {}

            val outcome = withTimeout(2_000) { service.runPendingRefresh() }

            assertEquals(DiaryRefreshOutcome.NotReady, outcome, "来源观察失败时必须有界返回")
            assertTrue(withTimeout(2_000) { service.state.first { it.error != null } }.error != null)
        } finally {
            job.cancelAndJoin()
            runCatching { driver.close() }
        }
    }

    private fun DiaryThoughtService.startWithWorker(scope: CoroutineScope) {
        start(scope) { scope.launch { runPendingRefresh() } }
    }

    private fun withFixture(block: suspend (DiaryRepository, DiaryThreadRepository, DiaryThoughtRepository, CoroutineScope) -> Unit) = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val job = SupervisorJob()
        try {
            DailySatoriDatabase.Schema.create(driver)
            val database = DailySatoriDatabase(driver)
            block(DiaryRepository(database, driver), DiaryThreadRepository(database, driver), DiaryThoughtRepository(SettingRepository(database)), CoroutineScope(job + Dispatchers.IO))
        } finally {
            job.cancelAndJoin()
            driver.close()
        }
    }
}

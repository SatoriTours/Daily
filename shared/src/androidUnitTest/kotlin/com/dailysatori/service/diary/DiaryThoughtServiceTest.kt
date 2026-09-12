package com.dailysatori.service.diary

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.DiaryThoughtRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
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
    fun backgroundCancellationResumesFromSavedEvidenceWhenForegroundReturns() = withFixture { diaries, repository, scope ->
        val id = diaries.create("先做重要的事情")
        val merging = CompletableDeferred<Unit>()
        val response = Json.encodeToString(DiaryThoughtBatch(listOf(DiaryThought(
            "做事准则", "先做重要的事情", "明确表达", listOf(DiaryThoughtEvidence(id, "先做重要的事情")),
        ))))
        var calls = 0
        val service = DiaryThoughtService(diaries, repository, DiaryThoughtGenerator { _, _ ->
            calls++
            if (calls == 2) {
                merging.complete(Unit)
                awaitCancellation()
            }
            response
        })
        service.start(scope)
        withTimeout(8_000) { merging.await() }
        service.setForeground(false)
        val paused = withTimeout(3_000) { service.state.first { it.isPaused } }
        assertFalse(paused.isUpdating)
        assertEquals(null, paused.error)
        assertEquals(1, repository.load().chunks.size)
        service.setForeground(true)
        val completed = withTimeout(8_000) { service.state.first { it.archive.generatedAt > 0 && !it.isStale } }
        assertFalse(completed.isPaused)
        assertEquals(3, calls, "恢复只重做中断的合并请求，不重复读取日记")
    }

    @Test
    fun manualRefreshDoesNotRegenerateAgainOnForegroundReturn() = withFixture { diaries, repository, scope ->
        val id = diaries.create("先做重要的事情")
        var calls = 0
        val service = DiaryThoughtService(diaries, repository, DiaryThoughtGenerator { _, _ ->
            calls++
            Json.encodeToString(DiaryThoughtBatch(listOf(DiaryThought(
                "做事准则", "先做重要的事情", "明确表达", listOf(DiaryThoughtEvidence(id, "先做重要的事情")),
            ))))
        })
        service.start(scope)
        val original = withTimeout(8_000) { service.state.first { it.archive.generatedAt > 0 } }
        service.requestRefresh()
        withTimeout(8_000) { service.state.first { it.archive.generatedAt > original.archive.generatedAt } }
        assertEquals(3, calls)
        service.setForeground(false)
        withTimeout(3_000) { service.state.first { it.progress == "准备更新…" } }
        service.setForeground(true)
        delay(2_500)
        assertEquals(3, calls, "已完成的手动请求不能在回前台时重复执行")
    }

    @Test
    fun aiTimeoutAutomaticallyRecoversWithoutKillingTheDiaryObserver() = withFixture { diaries, repository, scope ->
        diaries.create("先做重要的事情")
        var calls = 0
        val service = DiaryThoughtService(diaries, repository, DiaryThoughtGenerator { _, _ ->
            calls++
            if (calls == 1) withTimeout(1) { awaitCancellation() }
            "{\"thoughts\":[]}"
        })
        service.start(scope)
        val recovered = withTimeout(10_000) {
            service.state.first { it.archive.generatedAt > 0 && !it.isUpdating }
        }
        assertFalse(recovered.isStale)
        assertTrue(calls >= 2)
        diaries.create("今天决定先休息")
        withTimeout(8_000) { service.state.first { it.archive.diaryCount == 2 && !it.isStale } }
    }

    @Test
    fun diaryEditsInvalidateOldThoughtsAndRetryPublishesNewEvidence() = withFixture { diaries, repository, scope ->
        val id = diaries.create("先做重要的事情")
        var quote = "先做重要的事情"
        var fail = false
        val service = DiaryThoughtService(diaries, repository, DiaryThoughtGenerator { _, _ ->
            if (fail) error("网络失败")
            Json.encodeToString(DiaryThoughtBatch(listOf(DiaryThought(
                "做事准则", quote, "明确表达", listOf(DiaryThoughtEvidence(id, quote)),
            ))))
        })
        service.start(scope)
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
    fun deletingDiariesClearsPersistedProfileAndPreservesUserCorrections() = withFixture { diaries, repository, scope ->
        val id = diaries.create("先做重要的事情")
        val thought = DiaryThought("做事准则", "先做重要的事情", "明确表达", listOf(DiaryThoughtEvidence(id, "先做重要的事情")))
        repository.save(DiaryThoughtArchive(thoughts = listOf(thought)))
        repository.saveCorrections("不要把临时决定当成长久原则")
        diaries.delete(id)
        val service = DiaryThoughtService(diaries, repository, DiaryThoughtGenerator { _, _ -> error("空日记库无需 AI") })
        service.start(scope)
        withTimeout(8_000) { service.state.first { it.archive.fingerprint.isNotBlank() && !it.isStale } }
        assertTrue(repository.load().thoughts.isEmpty())
        assertTrue(repository.load().chunks.isEmpty())
        assertEquals("不要把临时决定当成长久原则", repository.corrections())
    }

    @Test
    fun restartWithUnchangedDiariesLoadsArchiveWithoutAiCalls() = withFixture { diaries, repository, scope ->
        val id = diaries.create("先做重要的事情")
        val source = diaries.getById(id)!!
        val fingerprint = diaryThoughtFingerprint(listOf(DiaryThoughtSource(id, source.content, source.created_at)), "")
        repository.save(DiaryThoughtArchive(fingerprint = fingerprint, diaryCount = 1, generatedAt = 123))
        val service = DiaryThoughtService(diaries, repository, DiaryThoughtGenerator { _, _ -> error("不应重复生成") })
        service.start(scope)
        val loaded = withTimeout(8_000) { service.state.first { it.archive.generatedAt == 123L } }
        assertFalse(loaded.isStale)
        assertEquals(1, loaded.archive.diaryCount)
    }

    private fun withFixture(block: suspend (DiaryRepository, DiaryThoughtRepository, CoroutineScope) -> Unit) = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val job = SupervisorJob()
        try {
            DailySatoriDatabase.Schema.create(driver)
            val database = DailySatoriDatabase(driver)
            block(DiaryRepository(database, driver), DiaryThoughtRepository(SettingRepository(database)), CoroutineScope(job + Dispatchers.IO))
        } finally {
            job.cancelAndJoin()
            driver.close()
        }
    }
}

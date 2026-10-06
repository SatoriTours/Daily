package com.dailysatori.core.task

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.opportunity.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.test.*

class NewsOpportunityTaskHandlerTest {
    @Test fun interruptedAutomaticAnalysisResumesDespiteRefreshCooldown() {
        var calls = 0
        withService(OpportunityAnalyzer {
            if (++calls == 1) throw CancellationException("Job was cancelled")
            null
        }, candidateSource = OpportunityCandidateSource { emptyList() }) { service ->
            var checkpoint = ""
            val reporter = object : AsyncTaskProgressReporter {
                override suspend fun report(current: Long, total: Long, message: String, checkpointJson: String) {
                    checkpoint = checkpointJson
                }
            }
            val handler = NewsOpportunityTaskHandler(service)
            assertFailsWith<CancellationException> { handler.execute(1, "{\"automatic\":true}", "", reporter) }
            assertTrue(checkpoint.isNotBlank())
            assertIs<AsyncTaskExecutionResult.Success>(handler.execute(1, "{\"automatic\":true}", checkpoint, reporter))
            assertEquals(2, calls)
            assertEquals(0, service.state.value.pendingCount)
        }
    }

    @Test fun stoppingInFlightAnalysisClearsBusyStateAndAllowsResuming() {
        val started = CompletableDeferred<Unit>()
        var shouldWait = true
        withService(OpportunityAnalyzer {
            if (shouldWait) { started.complete(Unit); awaitCancellation() }
            null
        }) { service ->
            coroutineScope {
                val job = launch { NewsOpportunityTaskHandler(service).execute(1, "{}", "", reporter { _, _ -> }) }
                started.await()
                assertTrue(service.state.value.isUpdating)
                job.cancelAndJoin()
            }
            service.clearError(1L)
            assertFalse(service.state.value.isUpdating)
            assertNull(service.state.value.error)
            assertEquals(1, service.state.value.pendingCount)
            shouldWait = false
            assertIs<AsyncTaskExecutionResult.Success>(NewsOpportunityTaskHandler(service).execute(2, "{}", "", reporter { _, _ -> }))
            assertEquals(0, service.state.value.pendingCount)
        }
    }

    @Test fun completionIsReportedOnlyAfterTheReadingCheckpointIsSaved() = withService(OpportunityAnalyzer { null }) { service ->
        val progress = mutableListOf<Pair<Long, Long>>()
        val result = NewsOpportunityTaskHandler(service).execute(1, "{}", "", reporter { n, total -> progress += n to total })
        assertIs<AsyncTaskExecutionResult.Success>(result)
        assertEquals(1L to 1L, progress.last())
        service.refresh()
        assertEquals(0, service.state.value.pendingCount)
    }

    @Test fun aiFailureIsVisibleToTheTaskCenterWithoutLeakingItsRawMessage() = withService(OpportunityAnalyzer { error("api_token=private") }) { service ->
        val result = NewsOpportunityTaskHandler(service).execute(1, "{}", "", reporter { _, _ -> })
        val failure = assertIs<AsyncTaskExecutionResult.PermanentFailure>(result)
        assertFalse(failure.message.contains("private"))
        assertEquals(1, service.state.value.pendingCount)
    }

    @Test fun cancellationRemainsCancellationRatherThanTaskFailure() = withService(OpportunityAnalyzer { throw CancellationException() }) { service ->
        assertFailsWith<CancellationException> { NewsOpportunityTaskHandler(service).execute(1, "{}", "", reporter { _, _ -> }) }
    }

    @Test fun invalidAiResponseIsExplainedInTaskCenterAndRemainsPending() = withService(OpportunityAnalyzer {
        throw NewsOpportunityAnalysisException(OpportunityFailureReason.INVALID_RESPONSE, IllegalArgumentException("api_token=private"))
    }) { service ->
        val result = NewsOpportunityTaskHandler(service).execute(1, "{}", "", reporter { _, _ -> })
        val failure = assertIs<AsyncTaskExecutionResult.PermanentFailure>(result)
        assertEquals("AI 返回的分析格式不完整，请重试", failure.message)
        assertEquals("opportunity_invalid_response", failure.code)
        service.refresh()
        assertEquals(failure.message, service.state.value.error)
        assertEquals(1, service.state.value.pendingCount)
    }

    private fun reporter(onProgress: (Long, Long) -> Unit) = object : AsyncTaskProgressReporter {
        override suspend fun report(current: Long, total: Long, message: String, checkpointJson: String) = onProgress(current, total)
    }

    private fun withService(
        analyzer: OpportunityAnalyzer,
        candidateSource: OpportunityCandidateSource? = null,
        block: suspend (NewsOpportunityService) -> Unit,
    ) = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val context = object : NewsOpportunityContext {
                override val enabled = false
                override fun verifiedContext(): String? = error("Private context must not be read")
            }
            val service = NewsOpportunityService(NewsOpportunityStore(SettingRepository(DailySatoriDatabase(driver))), analyzer, context, candidateSource)
            service.saveFocus("验证效率工具")
            service.markRead(ReadNewsArticle("one", "工具新闻", "可核对的正文", source = "来源", readAt = 1))
            block(service)
        } finally { driver.close() }
    }
}

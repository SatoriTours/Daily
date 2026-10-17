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
import kotlinx.coroutines.withTimeoutOrNull
import io.ktor.utils.io.ClosedByteChannelException
import java.net.SocketException
import kotlin.test.*

class NewsOpportunityTaskHandlerTest {
    @Test fun closedAiConnectionRemainsRetryableAndKeepsPendingArticle() = withService(OpportunityAnalyzer {
        throw ClosedByteChannelException(SocketException("api_token=private"))
    }) { service ->
        val result = NewsOpportunityTaskHandler(service).execute(1, "{}", "", reporter { _, _ -> })
        val failure = assertIs<AsyncTaskExecutionResult.RetryableFailure>(result)
        assertEquals("opportunity_network_error", failure.code)
        assertTrue(failure.message.contains("连接"))
        assertFalse(failure.message.contains("private"))
        assertEquals(1, service.state.value.pendingCount)
        assertFalse(service.state.value.isUpdating)
    }

    @Test fun retryAfterConnectionFailureDoesNotRepeatCompletedArticles() {
        val analyzed = mutableListOf<String>()
        var fail = true
        withService(OpportunityAnalyzer { input ->
            analyzed += input.article.key
            if (input.article.key == "two" && fail) { fail = false; throw SocketException("Connection reset") }
            null
        }) { service ->
            service.markRead(ReadNewsArticle("two", "第二篇", "第二篇正文", source = "来源", readAt = 2))
            var checkpoint = ""
            val progress = object : AsyncTaskProgressReporter {
                override suspend fun report(current: Long, total: Long, message: String, checkpointJson: String) {
                    checkpoint = checkpointJson
                }
            }
            val handler = NewsOpportunityTaskHandler(service)
            assertIs<AsyncTaskExecutionResult.RetryableFailure>(handler.execute(1, "{}", "", progress))
            assertEquals(1, service.state.value.pendingCount)
            assertIs<AsyncTaskExecutionResult.Success>(handler.execute(1, "{}", checkpoint, progress))
            assertEquals(listOf("one", "two", "two"), analyzed)
            assertEquals(0, service.state.value.pendingCount)
            assertNull(service.state.value.error)
        }
    }

    @Test fun stalledAnalysisTimesOutAsRetryableWithoutBlockingTheBatchIndefinitely() = withService(
        OpportunityAnalyzer { awaitCancellation() }, analysisTimeoutMs = 10,
    ) { service ->
        val result = withTimeoutOrNull(1_000) {
            NewsOpportunityTaskHandler(service).execute(1, "{}", "", reporter { _, _ -> })
        }
        assertIs<AsyncTaskExecutionResult.RetryableFailure>(result)
        assertEquals(1, service.state.value.pendingCount)
        assertFalse(service.state.value.isUpdating)
    }

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

    @Test fun cancellingARequestDoesNotPersistItsTransportCloseAsAnAnalysisError() {
        val started = CompletableDeferred<Unit>()
        withService(OpportunityAnalyzer {
            try { started.complete(Unit); awaitCancellation() }
            finally { throw ClosedByteChannelException(SocketException("Socket closed")) }
        }) { service ->
            coroutineScope {
                val job = launch { NewsOpportunityTaskHandler(service).execute(1, "{}", "", reporter { _, _ -> }) }
                started.await()
                job.cancelAndJoin()
            }
            assertNull(service.state.value.error)
            assertFalse(service.state.value.isUpdating)
            assertEquals(1, service.state.value.pendingCount)
        }
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
        analysisTimeoutMs: Long = 90_000L,
        block: suspend (NewsOpportunityService) -> Unit,
    ) = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val context = object : NewsOpportunityContext {
                override val enabled = false
                override fun verifiedContext(): String? = error("Private context must not be read")
            }
            val service = NewsOpportunityService(NewsOpportunityStore(SettingRepository(DailySatoriDatabase(driver))),
                analyzer, context, candidateSource, analysisTimeoutMs)
            service.saveFocus("验证效率工具")
            service.markRead(ReadNewsArticle("one", "工具新闻", "可核对的正文", source = "来源", readAt = 1))
            block(service)
        } finally { driver.close() }
    }
}

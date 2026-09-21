package com.dailysatori.core.task

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.opportunity.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class NewsOpportunityTaskHandlerTest {
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

    private fun reporter(onProgress: (Long, Long) -> Unit) = object : AsyncTaskProgressReporter {
        override suspend fun report(current: Long, total: Long, message: String, checkpointJson: String) = onProgress(current, total)
    }

    private fun withService(analyzer: OpportunityAnalyzer, block: suspend (NewsOpportunityService) -> Unit) = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val context = object : NewsOpportunityContext {
                override val enabled = false
                override fun verifiedContext(): String? = error("Private context must not be read")
            }
            val service = NewsOpportunityService(NewsOpportunityStore(SettingRepository(DailySatoriDatabase(driver))), analyzer, context)
            service.saveFocus("验证效率工具")
            service.markRead(ReadNewsArticle("one", "工具新闻", "可核对的正文", source = "来源", readAt = 1))
            block(service)
        } finally { driver.close() }
    }
}

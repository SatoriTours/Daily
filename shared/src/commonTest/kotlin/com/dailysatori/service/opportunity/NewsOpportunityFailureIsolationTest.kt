package com.dailysatori.service.opportunity

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.ai.AiQueueWaitTimeout
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A single article the AI service cannot reach must not park the whole batch: the batch keeps its durable
 * per-article checkpoints, so a later attempt resumes rather than permanently restarting from 0/N.
 */
class NewsOpportunityFailureIsolationTest {
    @Test
    fun oneUnreachableArticleDoesNotBlockTheRemainingBatch() = runBlocking {
        withService(failingKeys = setOf("a")) { service, store, attempted ->
            service.saveFocus("关注点")

            val failure = assertFailsWith<NewsOpportunityAnalysisException> { service.analyze() }

            assertEquals(OpportunityFailureReason.NETWORK_ERROR, failure.reason)
            assertEquals("a", attempted.first(), "the unreachable article is retried first")
            assertTrue(attempted.contains("b"), "later articles are still attempted: " + attempted)
            assertTrue(store.load().checkpoints.any { it.identity == "b" }, "b must be persisted")
            assertTrue(store.load().checkpoints.none { it.identity == "a" }, "a stays pending")
        }
    }

    @Test
    fun congestedQueueDefersEveryArticleWithoutFailingTheBatch() = runBlocking {
        withService(failingKeys = emptySet(), deferKeys = setOf("a", "b", "c", "d")) { service, store, attempted ->
            service.saveFocus("关注点")

            val outcome = service.analyze()

            assertEquals(0, outcome.completed, "nothing was sent, so nothing counts as done")
            assertEquals(4, outcome.deferred, "all four articles are deferred, not failed")
            assertEquals(4, attempted.size, "each article reached the queue")
            assertTrue(store.load().checkpoints.isEmpty(), "a deferral writes no article checkpoint")
            assertNull(service.state.value.error, "congestion is not a user-visible analysis failure")
        }
    }

    @Test
    fun anUnreachableServiceStopsTheBatchAfterABoundedNumberOfAttempts() = runBlocking {
        withService(failingKeys = setOf("a", "b", "c", "d")) { service, _, attempted ->
            service.saveFocus("关注点")

            assertFailsWith<NewsOpportunityAnalysisException> { service.analyze() }

            assertEquals(listOf("a", "b", "c"), attempted, "three consecutive failures stop the batch")
        }
    }

    private suspend fun withService(
        failingKeys: Set<String>,
        deferKeys: Set<String> = emptySet(),
        block: suspend (NewsOpportunityService, NewsOpportunityStore, MutableList<String>) -> Unit,
    ) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        val store = NewsOpportunityStore(SettingRepository(DailySatoriDatabase(driver)))
        val articles = listOf("a", "b", "c", "d").map { key ->
            ReadNewsArticle(
                key = key, title = "标题$key", content = "正文$key", source = "测试", readAt = 1L,
            )
        }
        val attempted = mutableListOf<String>()
        val service = NewsOpportunityService(
            store = store,
            analyzer = OpportunityAnalyzer { input ->
                attempted += input.article.key
                if (input.article.key in deferKeys) throw AiQueueWaitTimeout(200)
                if (input.article.key in failingKeys) {
                    throw NewsOpportunityAnalysisException(OpportunityFailureReason.NETWORK_ERROR)
                }
                OpportunityDraft(
                    title = input.article.title, category = "科技", fact = "事实",
                    relevance = "相关", action = "行动", caveat = "注意", quote = input.article.content,
                )
            },
            context = object : NewsOpportunityContext {
                override val enabled = false
                override fun verifiedContext(): String? = null
            },
            candidateSource = { articles },
            analysisTimeoutMs = 60_000L,
        )
        try {
            block(service, store, attempted)
        } finally {
            driver.close()
        }
    }
}

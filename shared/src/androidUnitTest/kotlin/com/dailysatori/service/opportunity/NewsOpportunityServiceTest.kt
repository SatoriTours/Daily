package com.dailysatori.service.opportunity

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NewsOpportunityServiceTest {
    @Test
    fun failedDiskWriteDoesNotAdvanceInMemoryCheckpoint() = withFixture { fixture ->
        fixture.service.markRead(article())
        fixture.driver.execute(null, "CREATE TRIGGER fail_opportunity_save BEFORE INSERT ON setting BEGIN SELECT RAISE(FAIL, 'disk failure'); END", 0)
        assertFailsWith<NewsOpportunityAnalysisException> { fixture.service.analyze() }
        assertEquals(1, fixture.service.state.value.pendingCount)
        assertTrue(fixture.service.state.value.items.isEmpty())
        fixture.driver.execute(null, "DROP TRIGGER fail_opportunity_save", 0)
        fixture.service.analyze()
        assertEquals(2, fixture.analyzer.inputs.size)
        assertEquals(0, fixture.service.state.value.pendingCount)
    }

    @Test
    fun evidenceOutsideTheTextSentToAiIsRejected() = withFixture(results = mutableListOf(draft(quote = "未发送的尾文"))) { fixture ->
        fixture.service.markRead(article(content = "a".repeat(OPPORTUNITY_BODY_LIMIT) + "未发送的尾文"))
        assertFailsWith<NewsOpportunityAnalysisException> { fixture.service.analyze() }
        assertTrue(fixture.service.state.value.items.isEmpty())
    }

    @Test
    fun savedResultIsRetainedWhenReanalysisFindsNoNewOpportunity() = withFixture { fixture ->
        fixture.service.markRead(article())
        fixture.service.analyze()
        val id = fixture.service.state.value.items.single().id
        fixture.service.setSaved(id, true)
        fixture.service.linkReminder(id, "existing-reminder")
        fixture.service.saveFocus("改变关注点")
        fixture.analyzer.results += null
        fixture.service.analyze()
        assertEquals(id, fixture.service.state.value.items.single().id)
        assertEquals("existing-reminder", fixture.service.state.value.items.single().reminderId)
        assertEquals(0, fixture.service.state.value.pendingCount)
    }

    @Test
    fun missingPersonalContextDoesNotSendNewsToAi() = withFixture(context = TrackingContext(false, null)) { fixture ->
        fixture.service.markRead(article())
        assertFailsWith<NewsOpportunityAnalysisException> { fixture.service.analyze() }
        assertTrue(fixture.analyzer.inputs.isEmpty())
        assertEquals(1, fixture.service.state.value.pendingCount)
    }

    @Test
    fun malformedAiJsonMustNotBeRecordedAsNoOpportunity() {
        // Missing a required decision is a failed analysis, not a successful empty result.
        assertFailsWith<Exception> { parseOpportunityResponse("{}") }
    }

    @Test
    fun explicitlyReadArticlesAreDeduplicatedAndChangedBodiesBecomePending() = withFixture(results = mutableListOf()) { fixture ->
        fixture.service.markRead(article(content = "正文一"))
        fixture.service.markRead(article(content = "正文一", readAt = 20))
        assertEquals(1, fixture.service.state.value.readCount)

        fixture.service.analyze()
        assertEquals(0, fixture.service.state.value.pendingCount)

        fixture.service.markRead(article(content = "正文二", readAt = 30))
        assertEquals(1, fixture.service.state.value.readCount)
        assertEquals(1, fixture.service.state.value.pendingCount)
    }

    @Test
    fun emptyRelevanceCompletesWithoutInventingAnOpportunity() = withFixture(results = mutableListOf(null)) { fixture ->
        fixture.service.markRead(article())
        fixture.service.analyze()

        assertTrue(fixture.service.state.value.items.isEmpty())
        assertEquals(0, fixture.service.state.value.pendingCount)
    }

    @Test
    fun invalidQuoteIsRejectedAndPreviousResultSurvives() = withFixture { fixture ->
        fixture.service.markRead(article())
        fixture.service.analyze()
        val previous = fixture.service.state.value.items.single()
        fixture.service.markRead(article(content = "完全变化的正文"))
        fixture.analyzer.results += draft(quote = "正文中不存在")

        assertFailsWith<NewsOpportunityAnalysisException> { fixture.service.analyze() }
        assertEquals(previous, fixture.service.state.value.items.single())
        assertEquals(1, fixture.service.state.value.pendingCount)
        assertEquals("分析失败，请稍后重试", fixture.service.state.value.error)
    }

    @Test
    fun disabledThoughtPermissionDoesNotReadPrivateContext() = withFixture(
        context = TrackingContext(enabled = false, value = "私密日记"),
    ) { fixture ->
        fixture.service.saveFocus("用户单独填写的关注点")
        fixture.service.markRead(article())
        fixture.service.analyze()

        assertEquals(0, fixture.context.reads)
        assertEquals("用户单独填写的关注点", fixture.analyzer.inputs.single().focus)
        assertNull(fixture.analyzer.inputs.single().thoughtContext)
    }

    @Test
    fun failureCheckpointsCompletedArticlesAndRetrySkipsThem() = withFixture { fixture ->
        fixture.service.markRead(article(key = "a", title = "甲"))
        fixture.service.markRead(article(key = "b", title = "乙", content = "乙正文"))
        fixture.analyzer.failTitles += "乙"

        assertFailsWith<NewsOpportunityAnalysisException> { fixture.service.analyze() }
        assertEquals(listOf("甲", "乙"), fixture.analyzer.inputs.map { it.article.title })
        assertEquals(1, fixture.service.state.value.pendingCount)
        assertEquals("已完成 1/2 篇", fixture.service.state.value.progress)

        fixture.analyzer.failTitles.clear()
        fixture.service.analyze()
        assertEquals(listOf("甲", "乙", "乙"), fixture.analyzer.inputs.map { it.article.title })
        assertEquals(0, fixture.service.state.value.pendingCount)
    }

    @Test
    fun userStateSurvivesReanalysisAndPersistsAcrossServiceInstances() = withFixture { fixture ->
        fixture.service.markRead(article())
        fixture.service.analyze()
        val id = fixture.service.state.value.items.single().id
        fixture.service.setSaved(id, true)
        fixture.service.setIgnored(id, true)
        fixture.service.linkReminder(id, "reminder-7")
        fixture.service.saveFocus("新关注")
        fixture.analyzer.results += draft(title = "新结果")
        fixture.service.analyze()

        val reopened = fixture.newService()
        reopened.refresh()
        val restored = reopened.state.value.items.single()
        assertEquals("新结果", restored.title)
        assertTrue(restored.saved)
        assertTrue(restored.ignored)
        assertEquals("reminder-7", restored.reminderId)
        assertEquals("新关注", reopened.state.value.focus)
        assertEquals("这是可核对的正文", restored.article.content)
    }

    @Test
    fun cancellationIsPropagatedWithoutUnsafeErrorText() = withFixture { fixture ->
        fixture.service.markRead(article())
        fixture.analyzer.cancellation = true

        assertFailsWith<CancellationException> { fixture.service.analyze() }
        assertFalse(fixture.service.state.value.isUpdating)
        assertNull(fixture.service.state.value.error)
    }

    private fun withFixture(
        results: MutableList<OpportunityDraft?> = mutableListOf(draft()),
        context: TrackingContext = TrackingContext(enabled = true, value = "已验证思想"),
        block: suspend (Fixture) -> Unit,
    ) = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val settings = SettingRepository(DailySatoriDatabase(driver))
            val analyzer = FakeAnalyzer(results)
            val store = NewsOpportunityStore(settings)
            val service = NewsOpportunityService(store, analyzer, context)
            block(Fixture(service, analyzer, context, driver) { NewsOpportunityService(NewsOpportunityStore(settings), analyzer, context) })
        } finally {
            driver.close()
        }
    }

    private data class Fixture(
        val service: NewsOpportunityService,
        val analyzer: FakeAnalyzer,
        val context: TrackingContext,
        val driver: JdbcSqliteDriver,
        val newService: () -> NewsOpportunityService,
    )

    private class TrackingContext(
        override val enabled: Boolean,
        private val value: String?,
    ) : NewsOpportunityContext {
        var reads = 0
        override fun verifiedContext(): String? = value.also { reads++ }
    }

    private class FakeAnalyzer(val results: MutableList<OpportunityDraft?>) : OpportunityAnalyzer {
        val inputs = mutableListOf<OpportunityAnalysisInput>()
        val failTitles = mutableSetOf<String>()
        var cancellation = false

        override suspend fun analyze(input: OpportunityAnalysisInput): OpportunityDraft? {
            inputs += input
            if (cancellation) throw CancellationException("private cancellation")
            if (input.article.title in failTitles) throw IllegalStateException("token=secret")
            return if (results.isEmpty()) draft(quote = input.article.content) else results.removeAt(0)
        }
    }

    private companion object {
        fun article(
            key: String = "article-1",
            title: String = "标题",
            content: String = "这是可核对的正文",
            readAt: Long = 10,
        ) = ReadNewsArticle(key, title, content, "https://example.test/a", "来源", "2026-09-20", readAt, 1)

        fun draft(title: String = "值得留意", quote: String = "这是可核对的正文") = OpportunityDraft(
            title = title,
            category = "值得观察",
            fact = "原文事实",
            relevance = "关联推断",
            action = "先做小范围验证",
            caveat = "仍需确认成本",
            quote = quote,
        )
    }
}

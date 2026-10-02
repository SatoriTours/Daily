package com.dailysatori.service.opportunity

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.diagnostics.DiagnosticCode
import com.dailysatori.service.diagnostics.DiagnosticLog
import com.dailysatori.service.diagnostics.DiagnosticSink
import com.dailysatori.service.diagnostics.Diagnostics
import com.dailysatori.service.diagnostics.SafeDiagnosticEvent
import com.dailysatori.service.externalfavorites.sha256Hex
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NewsOpportunityServiceTest {
    @Test
    fun savedRankingAndActionStateSurviveRefreshAndReanalysis() = withFixture(candidates = listOf(article())) { fixture ->
        fixture.service.analyze()
        val id = fixture.service.state.value.items.single().id
        fixture.service.setSaved(id, true)
        val savedAt = assertNotNull(fixture.service.state.value.items.single().savedAt)
        fixture.service.linkReminder(id, "task-1")
        fixture.service.saveFocus("新的关注方向")
        fixture.service.analyze()
        val fresh = fixture.newService()
        fresh.refresh()
        val item = fresh.state.value.items.single()
        assertTrue(item.saved)
        assertEquals(savedAt, item.savedAt)
        assertEquals("task-1", item.reminderId)
        fresh.setSaved(id, false)
        assertNull(fresh.state.value.items.single().savedAt)
    }

    @Test
    fun unreadCandidatesProduceSummariesWithoutMarkingThemRead() = withFixture(candidates = listOf(article(readAt = 0))) { fixture ->
        fixture.service.analyze()
        val result = fixture.service.state.value.items.single()
        assertEquals("标题", result.article.title)
        assertEquals("原文事实", result.fact)
        assertEquals("关联推断", result.relevance)
        assertEquals(1L, result.article.localArticleId)
        assertEquals(0, fixture.service.state.value.readCount)
        assertEquals("已验证思想", fixture.analyzer.inputs.single().thoughtContext)
        fixture.newService().analyze()
        assertEquals(1, fixture.analyzer.inputs.size)
    }

    @Test
    fun sameCandidateAndReadArticleAreAnalyzedOnlyOnce() = withFixture(candidates = listOf(article(readAt = 0))) { fixture ->
        fixture.service.markRead(article())
        fixture.service.analyze()
        assertEquals(1, fixture.analyzer.inputs.size)
        assertEquals(1, fixture.service.state.value.items.size)
    }

    @Test
    fun unrelatedUnreadCandidatesDoNotCreateFakeRecommendations() = withFixture(
        candidates = listOf(article(readAt = 0)), results = mutableListOf(null),
    ) { fixture ->
        fixture.service.analyze()
        assertEquals(1, fixture.analyzer.inputs.size)
        assertTrue(fixture.service.state.value.items.isEmpty())
        fixture.service.analyze()
        assertEquals(1, fixture.analyzer.inputs.size)
    }

    @Test
    fun automaticDiscoveryFetchesUnreadNewsOnceAcrossReopening() {
        var loads = 0
        withFixture(candidateSource = OpportunityCandidateSource { loads++; listOf(article(readAt = 0)) }) { fixture ->
            fixture.service.analyze(automatic = true)
            assertEquals(1, fixture.service.state.value.items.size)
            fixture.newService().analyze(automatic = true)
            assertEquals(1, loads)
            assertEquals(1, fixture.analyzer.inputs.size)
            fixture.service.saveFocus("新项目")
            fixture.service.analyze(automatic = true)
            assertEquals(2, loads)
            assertEquals(2, fixture.analyzer.inputs.size)
        }
    }

    @Test
    fun discoveryBoundsAiWorkAndSkipsUnreadableCandidates() = withFixture(
        candidateSource = OpportunityCandidateSource {
            listOf(article(content = "")) + (1..30).map { article(key = "news-$it", readAt = 0) }
        },
    ) { fixture ->
        fixture.service.analyze(automatic = true)
        assertEquals(20, fixture.analyzer.inputs.size)
        assertEquals(20, fixture.service.state.value.candidateCount)
        assertEquals(0, fixture.service.state.value.readCount)
    }

    @Test
    fun automaticDiscoveryWithoutPersonalContextDoesNotFetchOrAnalyze() = withFixture(
        context = TrackingContext(false, "私密思想"),
        candidateSource = OpportunityCandidateSource { error("Must not fetch without personal context") },
    ) { fixture ->
        fixture.service.analyze(automatic = true)
        assertEquals(0, fixture.context.reads)
        assertTrue(fixture.analyzer.inputs.isEmpty())
        assertNull(fixture.service.state.value.error)
    }

    @Test
    fun failedAutomaticDiscoveryPreservesResultsAndManualRetryBypassesCooldown() {
        var loads = 0
        withFixture(candidateSource = OpportunityCandidateSource {
            loads++
            if (loads == 2) error("network secret")
            listOf(article(readAt = 0))
        }) { fixture ->
            fixture.service.analyze(automatic = true)
            val previous = fixture.service.state.value.items.single()
            fixture.service.saveFocus("新关注")
            assertFailsWith<NewsOpportunityAnalysisException> { fixture.service.analyze(automatic = true) }
            assertEquals(previous, fixture.service.state.value.items.single())
            val reopened = fixture.newService()
            reopened.analyze(automatic = true)
            assertEquals(2, loads)
            assertEquals("分析失败，请稍后重试", reopened.state.value.error)
            fixture.service.analyze()
            assertEquals(3, loads)
            assertEquals(0, fixture.service.state.value.pendingCount)
            assertNull(fixture.service.state.value.error)
        }
    }

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
    fun noOpportunityWithNullUnusedFieldsIsAValidEmptyResult() {
        val response = """```json
            {"hasOpportunity":false,"productIdea":null,"targetUser":null,"userProblem":null,"category":null,"fact":null,"relevance":null,"mvp":null,"caveat":null,"quote":null,"relevanceScore":null,"actionabilityScore":null}
            ```"""
        assertNull(parseOpportunityResponse(response))
    }

    @Test
    fun invalidDecisionsAndIncompleteOpportunitiesProduceSafeFormatErrors() {
        listOf("{}", "[]", "not JSON", """{"hasOpportunity":"false"}""",
            """{"hasOpportunity":true,"productIdea":null}""").forEach { response ->
            val failure = assertFailsWith<NewsOpportunityAnalysisException> { parseOpportunityResponse(response) }
            assertEquals("AI 返回的分析格式不完整，请重试", failure.message)
            assertNotNull(failure.cause)
        }
    }

    @Test
    fun analysisFailureRetainsCauseAndRecordsOnlySafeDiagnostics() = withFixture { fixture ->
        val events = mutableListOf<SafeDiagnosticEvent>()
        val previous = DiagnosticLog.diagnostics
        DiagnosticLog.diagnostics = Diagnostics(DiagnosticSink { events.add(it) })
        try {
            fixture.service.markRead(article())
            fixture.analyzer.failTitles += "标题"
            val failure = assertFailsWith<NewsOpportunityAnalysisException> { fixture.service.analyze() }
            assertNotNull(failure.cause)
            assertEquals("分析失败，请稍后重试", fixture.service.state.value.error)
            val event = events.single { it.eventCode == DiagnosticCode.OPERATION_FAILED }
            assertTrue(event.exception.any { it.contains("IllegalStateException") })
            assertFalse(Json.encodeToString(event).contains("secret"))
            assertFalse(Json.encodeToString(event).contains("这是可核对的正文"))
        } finally {
            DiagnosticLog.diagnostics = previous
        }
    }

    @Test
    fun genericArticleRecommendationIsNotAcceptedAsAProductOpportunity() {
        val response = """{"hasOpportunity":true,"title":"值得一读","category":"观察","fact":"新闻摘要","relevance":"符合我的思想","action":"继续关注","caveat":"待确认","quote":"原文"}"""
        assertFailsWith<Exception> { parseOpportunityResponse(response) }
    }

    @Test
    fun productOpportunityNamesTheUserProblemAndSmallestBuildableVersion() {
        val response = """{"hasOpportunity":true,"productIdea":"合规提醒工具","targetUser":"独立开发者","userProblem":"难以及时跟踪新规则","category":"效率软件","fact":"新闻披露了新规则","relevance":"与用户关注的软件项目有关（推断）","mvp":"先做规则变更提醒页","caveat":"确认数据源是否可用","quote":"新规则"}"""
        val draft = assertNotNull(parseOpportunityResponse(response))
        assertEquals("合规提醒工具", draft.title)
        assertContains(draft.relevance, "独立开发者")
        assertContains(draft.relevance, "难以及时跟踪新规则")
        assertEquals("先做规则变更提醒页", draft.action)
    }

    @Test
    fun legacyAnalysisIsPendingForProductOpportunityReanalysis() {
        val article = article(readAt = 0)
        val contextVersion = sha256Hex("\n已验证思想")
        val oldFingerprint = sha256Hex("news-opportunity-analysis-v2:${article.key}:${sha256Hex(article.content)}:$contextVersion")
        val archive = OpportunityArchive(candidates = listOf(article), checkpoints = listOf(OpportunityCheckpoint(article.key, oldFingerprint)))
        withFixture(initialArchive = archive) { fixture ->
            fixture.service.refresh()
            assertEquals(1, fixture.service.state.value.pendingCount)
            fixture.service.analyze()
            assertEquals(1, fixture.analyzer.inputs.size)
        }
    }

    @Test
    fun recentLegacyAttemptDoesNotDelayProductOpportunityAnalysis() {
        val archive = OpportunityArchive(
            candidates = listOf(article(readAt = 0)),
            lastAttemptAt = Clock.System.now().toEpochMilliseconds(),
            lastAttemptContext = sha256Hex("\n已验证思想"),
        )
        withFixture(initialArchive = archive) { fixture ->
            fixture.service.analyze(automatic = true)
            assertEquals(1, fixture.analyzer.inputs.size)
        }
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
        assertEquals("AI 引用与新闻原文不一致，请重试", fixture.service.state.value.error)
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
    fun contextChangedWhileAiIsRunningDoesNotSaveStaleResult() = withFixture { fixture ->
        fixture.service.markRead(article())
        fixture.analyzer.beforeResult = { fixture.context.value = "更新后的思想" }
        val failure = assertFailsWith<NewsOpportunityAnalysisException> { fixture.service.analyze() }
        assertEquals("关注点或思想已更新，请重新分析", failure.message)
        assertTrue(fixture.service.state.value.items.isEmpty())
        assertEquals(1, fixture.service.state.value.pendingCount)
        assertFalse(fixture.service.state.value.isUpdating)
        fixture.analyzer.beforeResult = {}
        fixture.service.analyze()
        assertEquals("更新后的思想", fixture.analyzer.inputs.last().thoughtContext)
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
        candidates: List<ReadNewsArticle> = emptyList(),
        initialArchive: OpportunityArchive? = null,
        candidateSource: OpportunityCandidateSource? = null,
        block: suspend (Fixture) -> Unit,
    ) = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val settings = SettingRepository(DailySatoriDatabase(driver))
            settings.upsert("news_opportunity_archive_v1", initialArchive?.let { Json.encodeToString(it) }
                ?: "{\"candidates\":${Json.encodeToString(candidates)}}")
            val analyzer = FakeAnalyzer(results)
            val store = NewsOpportunityStore(settings)
            val service = NewsOpportunityService(store, analyzer, context, candidateSource)
            block(Fixture(service, analyzer, context, driver) { NewsOpportunityService(NewsOpportunityStore(settings), analyzer, context, candidateSource) })
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
        var value: String?,
    ) : NewsOpportunityContext {
        var reads = 0
        override fun verifiedContext(): String? = value.also { reads++ }
    }

    private class FakeAnalyzer(val results: MutableList<OpportunityDraft?>) : OpportunityAnalyzer {
        val inputs = mutableListOf<OpportunityAnalysisInput>()
        val failTitles = mutableSetOf<String>()
        var cancellation = false
        var beforeResult: () -> Unit = {}

        override suspend fun analyze(input: OpportunityAnalysisInput): OpportunityDraft? {
            inputs += input
            if (cancellation) throw CancellationException("private cancellation")
            if (input.article.title in failTitles) throw IllegalStateException("token=secret")
            beforeResult()
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

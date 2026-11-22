package com.dailysatori.service.opportunity

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.DiaryThoughtRepository
import com.dailysatori.data.repository.DiaryThreadRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.diary.DiaryThoughtChatContextProvider
import com.dailysatori.service.diagnostics.DiagnosticCode
import com.dailysatori.service.diagnostics.DiagnosticLog
import com.dailysatori.service.diagnostics.DiagnosticSink
import com.dailysatori.service.diagnostics.Diagnostics
import com.dailysatori.service.diagnostics.SafeDiagnosticEvent
import com.dailysatori.service.externalfavorites.sha256Hex
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.util.Properties
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
    fun legacyCancellationErrorIsNotDisplayed() = withFixture(
        initialArchive = OpportunityArchive(lastError = "Job was cancelled"),
    ) { fixture ->
        fixture.service.refresh()
        assertNull(fixture.service.state.value.error)
    }

    @Test
    fun dismissedErrorRemainsDismissedAcrossReopeningWithoutLosingResults() = withFixture { fixture ->
        fixture.service.markRead(article())
        fixture.service.analyze()
        fixture.service.clearError(17L)
        val reopened = fixture.newService()
        reopened.refresh()
        assertEquals(17L, reopened.state.value.dismissedErrorTaskId)
        assertEquals(1, reopened.state.value.items.size)
        assertNull(reopened.state.value.error)
    }

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
    fun markingUnreadPersistsWithoutRemovingAnalysis() = withFixture { fixture ->
        val article = article()
        fixture.service.markRead(article)
        fixture.service.analyze()
        val items = fixture.service.state.value.items
        assertTrue(items.isNotEmpty())
        assertEquals(setOf(article.key), fixture.service.state.value.readArticleKeys)

        fixture.service.markUnread(article.key)
        fixture.service.refresh()

        assertEquals(0, fixture.service.state.value.readCount)
        assertTrue(fixture.service.state.value.readArticleKeys.isEmpty())
        assertEquals(items, fixture.service.state.value.items)
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
        fixture.driver.execute(null, "CREATE TRIGGER fail_opportunity_save BEFORE INSERT ON opportunity_checkpoint BEGIN SELECT RAISE(FAIL, 'disk failure'); END", 0)
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
        val response = """{"hasOpportunity":true,"productIdea":"合规提醒工具","targetUser":"独立开发者","userProblem":"难以及时跟踪新规则","category":"效率软件","fact":"新闻披露了新规则","relevance":"与用户关注的软件项目有关（推断）","mvp":"先做规则变更提醒页","caveat":"确认数据源是否可用","quote":"新规则","preview":{"title":"规则变更提醒工具","reason":"独立开发者难以及时跟踪新规则。","opportunity":"可做规则变更提醒页，先确认数据源可用。"}}"""
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
        val oldFingerprint = sha256Hex("news-opportunity-analysis-v4:${article.key}:${sha256Hex(article.content)}:$contextVersion")
        val archive = OpportunityArchive(candidates = listOf(article), checkpoints = listOf(OpportunityCheckpoint(article.key, oldFingerprint)))
        withFixture(initialArchive = archive) { fixture ->
            fixture.service.refresh()
            assertEquals(1, fixture.service.state.value.pendingCount)
            fixture.service.analyze()
            assertEquals(1, fixture.analyzer.inputs.size)
        }
    }

    @Test
    fun compactPreviewPersistsWithoutReplacingDetailedAnalysisOrSavedState() = withFixture { fixture ->
        fixture.service.markRead(article())
        fixture.service.analyze()
        val id = fixture.service.state.value.items.single().id
        fixture.service.setSaved(id, true)
        fixture.service.linkReminder(id, "existing-reminder")
        val savedAt = fixture.service.state.value.items.single().savedAt
        fixture.service.saveFocus("新关注")
        val preview = OpportunityPreview("失败任务提醒", "手工检查费时。", "可做集中失败提醒工具。")
        fixture.analyzer.results += draft(title = "保持完整的详细产品方案").copy(preview = preview)
        fixture.service.analyze()
        val reopened = fixture.newService()
        reopened.refresh()
        val item = reopened.state.value.items.single()
        assertEquals(preview, item.preview)
        assertEquals("保持完整的详细产品方案", item.title)
        assertEquals("关联推断", item.relevance)
        assertEquals("先做小范围验证", item.action)
        assertEquals("这是可核对的正文", item.quote)
        assertEquals(id, item.id)
        assertTrue(item.saved)
        assertEquals(savedAt, item.savedAt)
        assertEquals("existing-reminder", item.reminderId)
    }

    @Test
    fun retainedLegacyItemsOutsideTheLatestCandidatesReceiveAPreview() {
        val old = legacyItem("old")
        val preview = OpportunityPreview("短标题", "关注原因。", "可做一个小工具。")
        withFixture(initialArchive = OpportunityArchive(items = listOf(old)),
            results = mutableListOf(draft().copy(preview = preview)),
            candidateSource = OpportunityCandidateSource { emptyList() }) { fixture ->
            fixture.service.refresh()
            assertEquals(1, fixture.service.state.value.pendingCount)
            fixture.service.analyze()
            val item = fixture.service.state.value.items.single()
            assertEquals(preview, item.preview)
            assertEquals(old.id, item.id)
            assertTrue(item.saved)
            assertEquals(old.savedAt, item.savedAt)
            assertEquals(0, fixture.service.state.value.pendingCount)
        }
    }

    @Test
    fun legacyPreviewBackfillIsBoundedAndLaterBatchesDoNotRetryCompletedItems() = withFixture(
        initialArchive = OpportunityArchive(items = (1..21).map { legacyItem("old-$it") } +
            listOf(legacyItem("ignored").copy(ignored = true), legacyItem("acted").copy(reminderId = "task"))),
    ) { fixture ->
        fixture.service.analyze()
        assertEquals(20, fixture.analyzer.inputs.size)
        assertEquals(1, fixture.service.state.value.pendingCount)
        fixture.service.analyze()
        assertEquals(21, fixture.analyzer.inputs.size)
        assertEquals(21, fixture.analyzer.inputs.map { it.article.key }.distinct().size)
        assertEquals(0, fixture.service.state.value.pendingCount)
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
    fun badQuoteDoesNotBlockRemainingArticlesAndRetrySkipsSuccessfulOnes() = withFixture(
        results = mutableListOf(null, draft(quote = "不存在的引用"), null),
    ) { fixture ->
        fixture.service.markRead(article(key = "a", title = "甲", readAt = 1))
        fixture.service.markRead(article(key = "b", title = "乙", readAt = 2))
        fixture.service.markRead(article(key = "c", title = "丙", readAt = 3))

        val failure = assertFailsWith<NewsOpportunityAnalysisException> { fixture.service.analyze() }
        assertEquals(OpportunityFailureReason.INVALID_QUOTE, failure.reason)
        assertEquals(listOf("甲", "乙", "丙"), fixture.analyzer.inputs.map { it.article.title })
        assertEquals(1, fixture.service.state.value.pendingCount)
        assertEquals("已完成 2/3 篇", fixture.service.state.value.progress)
        fixture.service.analyze()
        assertEquals(listOf("甲", "乙", "丙", "乙"), fixture.analyzer.inputs.map { it.article.title })
        assertEquals(0, fixture.service.state.value.pendingCount)
        assertNull(fixture.service.state.value.error)
    }

    @Test
    fun quoteWhitespaceDifferencesAreRestoredToOriginalAndPersisted() = withFixture(
        results = mutableListOf(draft(quote = "Teams spend hours checking failed jobs.")),
    ) { fixture ->
        fixture.service.markRead(article(content = "# Report\nTeams\u00a0spend  hours\nchecking failed jobs.\nEnd."))
        fixture.service.analyze()

        assertEquals("Teams\u00a0spend  hours\nchecking failed jobs.", fixture.service.state.value.items.single().quote)
        assertEquals(0, fixture.service.state.value.pendingCount)
        assertNull(fixture.service.state.value.error)
        val reopened = fixture.newService()
        reopened.refresh()
        assertEquals("Teams\u00a0spend  hours\nchecking failed jobs.", reopened.state.value.items.single().quote)
    }

    @Test
    fun translatedOrSplicedQuotesAndUnseenBodyAreRejected() {
        listOf(
            "Teams spend hours checking failed jobs." to "团队花几个小时检查失败的任务。",
            "Teams check jobs. Other work happens. Failures need retries." to "Teams check jobs. Failures need retries.",
            "Jobs did not fail." to "Jobs did fail.",
            "Jobs failed." to "Jobs failed!",
            "x".repeat(OPPORTUNITY_BODY_LIMIT) + "Hidden evidence" to "Hidden evidence",
        ).forEach { (body, quote) ->
            withFixture(results = mutableListOf(draft(quote = quote))) { fixture ->
                fixture.service.markRead(article(content = body))
                val failure = assertFailsWith<NewsOpportunityAnalysisException> { fixture.service.analyze() }
                assertEquals(OpportunityFailureReason.INVALID_QUOTE, failure.reason)
                assertTrue(fixture.service.state.value.items.isEmpty())
                assertEquals(1, fixture.service.state.value.pendingCount)
            }
        }
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
    fun userActionsMergeIntoResultWhileAiIsWaiting() = withFixture { fixture ->
        fixture.service.markRead(article())
        fixture.service.analyze()
        val id = fixture.service.state.value.items.single().id
        fixture.service.saveFocus("重新分析")
        fixture.analyzer.results += draft(title = "更新结果")
        val paused = PausedAnalysis(fixture.analyzer)
        coroutineScope {
            val analysis = async { fixture.service.analyze() }
            paused.awaitStart()
            withTimeout(2_000) {
                fixture.service.setSaved(id, true)
                fixture.service.setIgnored(id, true)
                fixture.service.linkReminder(id, "in-flight")
            }
            assertTrue(fixture.service.state.value.isUpdating)
            paused.release.complete(Unit)
            analysis.await()
        }
        val reopened = fixture.newService()
        reopened.refresh()
        val item = reopened.state.value.items.single()
        assertEquals("更新结果", item.title)
        assertTrue(item.saved && item.ignored)
        assertNotNull(item.savedAt)
        assertEquals("in-flight", item.reminderId)
    }

    @Test
    fun changedFocusDiscardsStaleResultAndQueuedAnalysesCheckpointOnlyOnce() = withFixture { fixture ->
        fixture.service.markRead(article())
        val paused = PausedAnalysis(fixture.analyzer)
        coroutineScope {
            val stale = async { assertFailsWith<NewsOpportunityAnalysisException> { fixture.service.analyze() } }
            paused.awaitStart()
            withTimeout(2_000) { fixture.service.saveFocus("新方向") }
            assertTrue(fixture.service.state.value.isUpdating)
            paused.release.complete(Unit)
            assertEquals(OpportunityFailureReason.CONTEXT_CHANGED, stale.await().reason)
            assertTrue(fixture.service.state.value.items.isEmpty())
            assertEquals(1, fixture.service.state.value.pendingCount)

            val retry = PausedAnalysis(fixture.analyzer)
            val first = async { fixture.service.analyze() }
            retry.awaitStart()
            val repeat = async(start = CoroutineStart.UNDISPATCHED) { fixture.service.analyze() }
            assertEquals(2, fixture.analyzer.inputs.size)
            assertFalse(repeat.isCompleted)
            retry.release.complete(Unit)
            first.await()
            repeat.await()
        }
        assertEquals(listOf("", "新方向"), fixture.analyzer.inputs.map { it.focus })
        assertEquals(1, fixture.service.state.value.items.size)
        assertEquals(0, fixture.service.state.value.pendingCount)
    }

    @Test
    fun editingCandidateDuringAiWaitCannotCommitItsOldBody() = withFixture { fixture ->
        fixture.service.markRead(article())
        val paused = PausedAnalysis(fixture.analyzer)
        coroutineScope {
            val stale = async { assertFailsWith<NewsOpportunityAnalysisException> { fixture.service.analyze() } }
            paused.awaitStart()
            withTimeout(2_000) { fixture.service.markRead(article(content = "新正文")) }
            paused.release.complete(Unit)
            stale.await()
        }
        assertTrue(fixture.service.state.value.items.isEmpty())
        fixture.analyzer.results += draft(quote = "新正文")
        fixture.service.analyze()
        assertEquals("新正文", fixture.service.state.value.items.single().article.content)
    }

    @Test
    fun cancellationReleasesAnalysisSlotWithoutLosingConcurrentEdit() = withFixture { fixture ->
        fixture.service.markRead(article())
        val started = CompletableDeferred<Unit>()
        fixture.analyzer.beforeResult = { started.complete(Unit); awaitCancellation() }
        coroutineScope {
            val analysis = async { fixture.service.analyze() }
            withTimeout(2_000) { started.await(); fixture.service.saveFocus("保留修改") }
            analysis.cancelAndJoin()
        }
        assertFalse(fixture.service.state.value.isUpdating)
        assertNull(fixture.service.state.value.error)
        assertEquals(1, fixture.service.state.value.pendingCount)
        fixture.analyzer.beforeResult = {}
        fixture.service.analyze()
        assertEquals("保留修改", fixture.analyzer.inputs.last().focus)
        assertEquals(0, fixture.service.state.value.pendingCount)
    }

    @Test
    fun focusChangeWhileFetchingCandidatesDiscardsTheFetchBeforeAnalysis() {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var loads = 0
        withFixture(candidateSource = OpportunityCandidateSource {
            loads++
            if (loads == 1) {
                started.complete(Unit)
                release.await()
            }
            listOf(article(key = "candidate-$loads"))
        }) { fixture ->
            coroutineScope {
                val analysis = async { assertFailsWith<NewsOpportunityAnalysisException> { fixture.service.analyze(automatic = true) } }
                withTimeout(2_000) { started.await(); fixture.service.saveFocus("新方向") }
                release.complete(Unit)
                assertEquals(OpportunityFailureReason.CONTEXT_CHANGED, analysis.await().reason)
            }
            assertEquals(0, fixture.service.state.value.candidateCount)
            assertTrue(fixture.analyzer.inputs.isEmpty())
            fixture.service.analyze(automatic = true)
            assertEquals("candidate-2", fixture.service.state.value.items.single().article.key)
        }
    }

    @Test
    fun failedResultSaveLeavesConcurrentActionDurableAndArticlePending() = withFixture { fixture ->
        fixture.service.markRead(article())
        fixture.service.analyze()
        val old = fixture.service.state.value.items.single()
        fixture.service.saveFocus("重试关注")
        val paused = PausedAnalysis(fixture.analyzer)
        coroutineScope {
            val analysis = async { assertFailsWith<NewsOpportunityAnalysisException> { fixture.service.analyze() } }
            paused.awaitStart()
            withTimeout(2_000) { fixture.service.linkReminder(old.id, "saved-before-failure") }
            fixture.driver.execute(null, "CREATE TRIGGER fail_result BEFORE INSERT ON opportunity_checkpoint BEGIN SELECT RAISE(FAIL, 'disk failure'); END", 0)
            paused.release.complete(Unit)
            analysis.await()
        }
        fixture.newService().also { it.refresh() }.state.value.let { restored ->
            assertEquals(old.copy(reminderId = "saved-before-failure"), restored.items.single())
            assertEquals(1, restored.pendingCount)
        }
        fixture.driver.execute(null, "DROP TRIGGER fail_result", 0)
        fixture.service.analyze()
        assertEquals(0, fixture.service.state.value.pendingCount)
        assertEquals("saved-before-failure", fixture.service.state.value.items.single().reminderId)
    }

    @Test
    fun disablingThoughtsAtResultCommitCannotPersistOldResultOrCheckpoint() = withThoughtCommitRace { driver, writer, store, context ->
        val service = NewsOpportunityService(store, OpportunityAnalyzer { input ->
            assertContains(assertNotNull(input.thoughtContext), OLD_CORRECTIONS)
            driver.afterReadingCorrections = { writer.setUseInChat(false) }
            draft()
        }, context)
        service.markRead(article())

        assertFailsWith<NewsOpportunityAnalysisException> { service.analyze() }
        assertFalse(writer.useInChat())
        assertTrue(store.load().items.isEmpty())
        assertTrue(store.load().checkpoints.isEmpty())
        assertTrue(service.state.value.items.isEmpty())
        assertEquals(1, service.state.value.pendingCount)
        assertFalse(service.state.value.isUpdating)
    }

    @Test
    fun savingCorrectionsAtCandidateCommitCannotReplaceTheCandidateWindow() = withThoughtCommitRace { driver, writer, store, context ->
        val old = article(key = "existing")
        store.importLegacy(OpportunityArchive(candidates = listOf(old)))
        var fetches = 0
        val service = NewsOpportunityService(store, OpportunityAnalyzer {
            assertContains(assertNotNull(it.thoughtContext), "新修正")
            draft()
        }, context, OpportunityCandidateSource {
            if (++fetches == 1) driver.afterReadingCorrections = { writer.saveCorrections("新修正") }
            listOf(article(key = "candidate-$fetches"))
        })

        assertFailsWith<NewsOpportunityAnalysisException> { service.analyze(automatic = true) }
        assertEquals("新修正", writer.corrections())
        assertEquals(listOf(old), store.load().candidates)
        assertTrue(store.load().checkpoints.isEmpty())
        assertEquals(1, service.state.value.pendingCount)
        assertFalse(service.state.value.isUpdating)
        service.analyze(automatic = true)
        assertEquals("candidate-2", service.state.value.items.single().article.key)
        assertEquals(1, store.load().checkpoints.size)
    }

    private fun withThoughtCommitRace(
        block: suspend (ThoughtCommitDriver, DiaryThoughtRepository, NewsOpportunityStore, NewsOpportunityContext) -> Unit,
    ) = runBlocking {
        val root = Files.createTempDirectory("opportunity-thought-commit").toFile()
        val url = "jdbc:sqlite:${root.resolve("race.db")}"
        val properties = Properties().apply {
            setProperty("journal_mode", "WAL")
            setProperty("transaction_mode", "DEFERRED")
        }
        try {
            JdbcSqliteDriver(url, properties).use { reader ->
                DailySatoriDatabase.Schema.create(reader)
                JdbcSqliteDriver(url, properties).use { otherConnection ->
                    val driver = ThoughtCommitDriver(reader)
                    val db = DailySatoriDatabase(driver)
                    val settings = SettingRepository(db)
                    val thoughts = DiaryThoughtRepository(settings)
                    val context = DiaryThoughtOpportunityContext(thoughts,
                        DiaryThoughtChatContextProvider(thoughts, DiaryThreadRepository(db, driver)))
                    val writer = DiaryThoughtRepository(SettingRepository(DailySatoriDatabase(otherConnection)))
                    writer.saveCorrections(OLD_CORRECTIONS)
                    block(driver, writer, NewsOpportunityStore(db), context)
                }
            }
        } finally {
            root.deleteRecursively()
        }
        Unit
    }

    private class ThoughtCommitDriver(private val delegate: SqlDriver) : SqlDriver by delegate {
        var afterReadingCorrections: (() -> Unit)? = null

        override fun <R> executeQuery(
            identifier: Int?, sql: String, mapper: (SqlCursor) -> QueryResult<R>,
            parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<R> {
            var readCorrections = false
            val result = delegate.executeQuery(identifier, sql, { cursor ->
                mapper(object : SqlCursor by cursor {
                    override fun getString(index: Int) = cursor.getString(index).also {
                        if (it == OLD_CORRECTIONS) readCorrections = true
                    }
                })
            }, parameters, binders)
            if (readCorrections) afterReadingCorrections?.let { edit ->
                afterReadingCorrections = null
                edit()
            }
            return result
        }
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
            val db = DailySatoriDatabase(driver)
            val analyzer = FakeAnalyzer(results)
            val store = NewsOpportunityStore(db)
            store.importLegacy(initialArchive ?: OpportunityArchive(candidates = candidates))
            val service = NewsOpportunityService(store, analyzer, context, candidateSource)
            block(Fixture(service, analyzer, context, driver) { NewsOpportunityService(NewsOpportunityStore(db), analyzer, context, candidateSource) })
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
        var beforeResult: suspend () -> Unit = {}

        override suspend fun analyze(input: OpportunityAnalysisInput): OpportunityDraft? {
            inputs += input
            if (cancellation) throw CancellationException("private cancellation")
            if (input.article.title in failTitles) throw IllegalStateException("token=secret")
            beforeResult()
            return if (results.isEmpty()) draft(quote = input.article.content) else results.removeAt(0)
        }
    }

    private class PausedAnalysis(analyzer: FakeAnalyzer) {
        private val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        init { analyzer.beforeResult = { started.complete(Unit); release.await() } }

        suspend fun awaitStart() = withTimeout(2_000) { started.await() }
    }

    private companion object {
        const val OLD_CORRECTIONS = "提交前的修正"

        fun legacyItem(key: String) = NewsOpportunity(key, article(key = key), "旧完整方案", "效率工具",
            "旧事实", "旧关联", "旧行动", "旧风险", "这是可核对的正文", 100,
            saved = true, savedAt = 200)

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

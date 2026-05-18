package com.dailysatori

import com.dailysatori.core.worker.UnifiedNewsWorkerMode
import com.dailysatori.core.worker.UnifiedNewsWorker
import com.dailysatori.core.worker.buildUnifiedNewsBackfillWorkRequest
import com.dailysatori.core.worker.buildUnifiedNewsNextWorkRequest
import com.dailysatori.core.worker.shouldRetryUnifiedNews
import com.dailysatori.core.worker.unifiedNewsWorkerMode
import com.dailysatori.service.crayfishnews.decodeCrayfishNewsListResponse
import com.dailysatori.service.unifiednews.UnifiedNewsGenerationResult
import com.dailysatori.service.unifiednews.UnifiedNewsSourceItem
import com.dailysatori.service.unifiednews.UnifiedNewsSourceType
import com.dailysatori.service.unifiednews.UnifiedNewsSummaryStatus
import com.dailysatori.service.unifiednews.UnifiedNewsWindowKey
import com.dailysatori.service.unifiednews.UnifiedNewsWindow
import com.dailysatori.service.unifiednews.backfillUnifiedNewsWindows
import com.dailysatori.service.unifiednews.buildUnifiedNewsPrompt
import com.dailysatori.service.unifiednews.budgetUnifiedNewsSources
import com.dailysatori.service.unifiednews.citationTokens
import com.dailysatori.service.unifiednews.dailyUnifiedNewsWindowFor
import com.dailysatori.service.unifiednews.dueUnifiedNewsWindows
import com.dailysatori.service.unifiednews.invalidCitationTokens
import com.dailysatori.service.unifiednews.nextUnifiedNewsWindow
import com.dailysatori.service.unifiednews.remoteDigestArticlesToUnifiedSources
import com.dailysatori.service.unifiednews.unifiedNewsWindowFor
import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.service.remotenews.RemoteDigest
import com.dailysatori.ui.feature.remotenews.remoteArticleDetailPageContent
import com.dailysatori.ui.feature.unifiednews.displayUnifiedNewsMarkdown
import com.dailysatori.ui.feature.unifiednews.primaryCitationInUnifiedNewsLine
import com.dailysatori.ui.feature.unifiednews.manualRefreshWindowForEnvironment
import com.dailysatori.ui.feature.unifiednews.unifiedNewsCitationUrl
import com.dailysatori.ui.feature.unifiednews.unifiedNewsMarkdownWithCitationLinks
import com.dailysatori.ui.feature.unifiednews.visibleUnifiedNewsTextWithoutCitation
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnifiedNewsBehaviorTest {
    private val zone = TimeZone.of("Asia/Shanghai")

    @Test
    fun dailyUnifiedNewsWindowUsesPhoneLocalDateAndDailyKey() {
        val window = dailyUnifiedNewsWindowFor(
            now = Instant.parse("2026-05-16T18:30:00Z"),
            timeZone = TimeZone.of("Asia/Shanghai"),
        )

        assertEquals("2026-05-17", window.summaryDate)
        assertEquals("daily", window.key.value)
        assertEquals(Instant.parse("2026-05-16T16:00:00Z").toEpochMilliseconds(), window.startMs)
        assertEquals(Instant.parse("2026-05-17T15:59:59.999Z").toEpochMilliseconds(), window.endMs)
    }

    @Test
    fun finalWindowAtMidnightTargetsPreviousNaturalDay() {
        val window = unifiedNewsWindowFor(
            key = UnifiedNewsWindowKey.FINAL,
            dueAt = Instant.parse("2026-05-16T16:00:00Z"),
            timeZone = zone,
        )

        assertEquals("2026-05-16", window.summaryDate)
        assertEquals("final", window.key.value)
        assertEquals(Instant.parse("2026-05-15T16:00:00Z").toEpochMilliseconds(), window.startMs)
        assertEquals(Instant.parse("2026-05-16T15:59:59.999Z").toEpochMilliseconds(), window.endMs)
    }

    @Test
    fun delayedRunKeepsNamedWindowEnd() {
        val window = unifiedNewsWindowFor(
            key = UnifiedNewsWindowKey.W1330,
            dueAt = Instant.parse("2026-05-16T06:10:00Z"),
            timeZone = zone,
        )

        assertEquals("2026-05-16", window.summaryDate)
        assertEquals(Instant.parse("2026-05-15T16:00:00Z").toEpochMilliseconds(), window.startMs)
        assertEquals(Instant.parse("2026-05-16T05:30:00Z").toEpochMilliseconds(), window.endMs)
    }

    @Test
    fun delayedRunAfterMidnightTargetsPreviousNamedWindow() {
        val window = unifiedNewsWindowFor(
            key = UnifiedNewsWindowKey.W2100,
            dueAt = Instant.parse("2026-05-15T16:10:00Z"),
            timeZone = zone,
        )

        assertEquals("2026-05-15", window.summaryDate)
        assertEquals(Instant.parse("2026-05-14T16:00:00Z").toEpochMilliseconds(), window.startMs)
        assertEquals(Instant.parse("2026-05-15T13:00:00Z").toEpochMilliseconds(), window.endMs)
    }

    @Test
    fun nextWindowAfterMorningIsLunchWindow() {
        val next = nextUnifiedNewsWindow(
            now = Instant.parse("2026-05-16T01:00:00Z"),
            timeZone = zone,
        )

        assertEquals(UnifiedNewsWindowKey.W1330, next.key)
        assertEquals(Instant.parse("2026-05-16T05:30:00Z"), next.dueAt)
    }

    @Test
    fun dueWindowsAtMorningIncludePreviousFinalAndMorningOnly() {
        val due = dueUnifiedNewsWindows(
            now = Instant.parse("2026-05-16T01:00:00Z"),
            timeZone = zone,
        )

        assertEquals(listOf(UnifiedNewsWindowKey.FINAL, UnifiedNewsWindowKey.W0800), due.map { it.key })
        assertEquals("2026-05-15", due.first().summaryDate)
        assertEquals("2026-05-16", due.last().summaryDate)
    }

    @Test
    fun backfillWindowsIncludeRecentMissedWindowsWithoutFutureCurrentWindows() {
        val backfill = backfillUnifiedNewsWindows(
            now = Instant.parse("2026-05-16T06:00:00Z"),
            timeZone = zone,
        )
        val currentDayKeys = backfill.filter { it.summaryDate == "2026-05-16" }.map { it.key }

        assertTrue(backfill.any { it.summaryDate == "2026-05-14" && it.key == UnifiedNewsWindowKey.FINAL })
        assertTrue(backfill.any { it.summaryDate == "2026-05-15" && it.key == UnifiedNewsWindowKey.FINAL })
        assertTrue(UnifiedNewsWindowKey.W0800 in currentDayKeys)
        assertTrue(UnifiedNewsWindowKey.W1330 in currentDayKeys)
        assertFalse(UnifiedNewsWindowKey.W1800 in currentDayKeys)
        assertFalse(UnifiedNewsWindowKey.W2100 in currentDayKeys)
    }

    @Test
    fun citationValidationRejectsUnknownTokens() {
        val sources = listOf(
            UnifiedNewsSourceItem(refKey = "R1", sourceType = UnifiedNewsSourceType.REMOTE_ARTICLE, title = "远程", summary = "摘要"),
            UnifiedNewsSourceItem(refKey = "F1", sourceType = UnifiedNewsSourceType.LOCAL_FAVORITE, title = "收藏", summary = "摘要"),
        )
        val content = "AI 相关趋势升温。[R1][F2]\n本地收藏补充了背景。[F1]"

        assertEquals(listOf("R1", "F2", "F1"), citationTokens(content))
        assertEquals(listOf("F2"), invalidCitationTokens(content, sources))
    }

    @Test
    fun citationValidationRejectsMalformedAndUnsupportedTokens() {
        val sources = listOf(
            UnifiedNewsSourceItem(refKey = "R1", sourceType = UnifiedNewsSourceType.REMOTE_ARTICLE, title = "远程", summary = "摘要"),
            UnifiedNewsSourceItem(refKey = "F1", sourceType = UnifiedNewsSourceType.LOCAL_FAVORITE, title = "收藏", summary = "摘要"),
        )
        val content = "有效来源。[R1]\n unsupported prefix。[X1]\n missing number。[R] [F]\n malformed prefix。[RC1]"

        assertEquals(listOf("R1"), citationTokens(content))
        assertEquals(listOf("X1", "R", "F", "RC1"), invalidCitationTokens(content, sources))
    }

    @Test
    fun citationValidationIgnoresMarkdownLabels() {
        val sources = listOf(
            UnifiedNewsSourceItem(refKey = "R1", sourceType = UnifiedNewsSourceType.REMOTE_ARTICLE, title = "远程", summary = "摘要"),
        )
        val content = "有效来源。[R1]\n[原文](https://example.com)\n[参考资料]"

        assertEquals(emptyList(), invalidCitationTokens(content, sources))
    }

    @Test
    fun citationValidationIgnoresCitationShapedMarkdownInlineLinkLabels() {
        val sources = listOf(
            UnifiedNewsSourceItem(refKey = "R1", sourceType = UnifiedNewsSourceType.REMOTE_ARTICLE, title = "远程", summary = "摘要"),
        )
        val content = "有效来源。[R1]\n[X1](https://example.com)\n[R](https://example.com)"

        assertEquals(emptyList(), invalidCitationTokens(content, sources))
    }

    @Test
    fun citationValidationIgnoresCitationShapedMarkdownReferenceLabels() {
        val sources = listOf(
            UnifiedNewsSourceItem(refKey = "R1", sourceType = UnifiedNewsSourceType.REMOTE_ARTICLE, title = "远程", summary = "摘要"),
        )
        val content = "有效来源。[R1]\n[X1]: https://example.com\n[F]: https://example.com"

        assertEquals(emptyList(), invalidCitationTokens(content, sources))
    }

    @Test
    fun citationValidationIgnoresCitationShapedMarkdownReferenceLinkLabels() {
        val sources = listOf(
            UnifiedNewsSourceItem(refKey = "R1", sourceType = UnifiedNewsSourceType.REMOTE_ARTICLE, title = "远程", summary = "摘要"),
        )
        val content = "有效来源。[R1]\n[X1][source]\n[RC1][source]"

        assertEquals(emptyList(), invalidCitationTokens(content, sources))
    }

    @Test
    fun citationValidationIgnoresCitationShapedMarkdownCollapsedReferenceLinkLabels() {
        val sources = listOf(
            UnifiedNewsSourceItem(refKey = "R1", sourceType = UnifiedNewsSourceType.REMOTE_ARTICLE, title = "远程", summary = "摘要"),
        )
        val content = "有效来源。[R1]\n[X1][]"

        assertEquals(emptyList(), invalidCitationTokens(content, sources))
    }

    @Test
    fun citationValidationRejectsStandaloneUnknownCitationLikeTokens() {
        val sources = listOf(
            UnifiedNewsSourceItem(refKey = "R1", sourceType = UnifiedNewsSourceType.REMOTE_ARTICLE, title = "远程", summary = "摘要"),
        )
        val content = "未知来源 [X1]"

        assertEquals(listOf("X1"), invalidCitationTokens(content, sources))
    }

    @Test
    fun sourceTypePrefixesAreStable() {
        assertTrue(UnifiedNewsSourceType.REMOTE_ARTICLE.prefix == "R")
        assertTrue(UnifiedNewsSourceType.LOCAL_FAVORITE.prefix == "F")
    }

    @Test
    fun unifiedNewsCollectsConfiguredRemoteArticlesNotRemoteDigestSources() {
        val source = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt").readText()

        assertTrue(source.contains("collectConfiguredRemoteArticles"))
        assertTrue(source.contains("fetchTopArticlesToday"))
        assertFalse(source.contains("collectRemoteArticles(window"))
        assertFalse(source.contains("REMOTE_DIGEST"))
        assertTrue(source.contains("UnifiedNewsSourceType.REMOTE_ARTICLE"))
    }

    @Test
    fun unifiedNewsWorkerWritesDailySummaryRows() {
        val worker = java.io.File("src/main/kotlin/com/dailysatori/core/worker/UnifiedNewsWorker.kt").readText()

        assertTrue(worker.contains("generateDaily"))
        assertFalse(worker.contains("summaryService.generate(window"))
    }

    @Test
    fun unifiedNewsWorkerUsesForcedDailyGenerationOnlyForDueMode() {
        val worker = java.io.File("src/main/kotlin/com/dailysatori/core/worker/UnifiedNewsWorker.kt").readText()

        assertTrue(worker.contains("UnifiedNewsWorkerMode.DUE -> generateDailySummary(summaryService, force = true)"))
        assertTrue(worker.contains("UnifiedNewsWorkerMode.BACKFILL -> generateDailySummary(summaryService, force = false)"))
        assertTrue(worker.contains("summaryService.generateDaily(force = force)"))
    }

    @Test
    fun unifiedNewsWorkerSchedulesNextOnlyAfterNonRetryResult() {
        val worker = java.io.File("src/main/kotlin/com/dailysatori/core/worker/UnifiedNewsWorker.kt").readText()
        val retryCheck = worker.indexOf("if (shouldRetryUnifiedNews(result)) return Result.retry()")
        val scheduleNext = worker.indexOf("UnifiedNewsScheduler(applicationContext).scheduleNext(Clock.System.now())")

        assertTrue(retryCheck >= 0)
        assertTrue(scheduleNext > retryCheck)
    }

    @Test
    fun unifiedNewsStartupEnsureKeepsRunningDueWork() {
        val worker = java.io.File("src/main/kotlin/com/dailysatori/core/worker/UnifiedNewsWorker.kt").readText()

        assertTrue(worker.contains("ensureNextScheduled()"))
        assertTrue(worker.contains("ExistingWorkPolicy.KEEP"))
        assertTrue(worker.contains("ExistingWorkPolicy.REPLACE"))
        assertTrue(worker.contains("fun scheduleNext(now: Instant = Clock.System.now())"))
    }

    @Test
    fun unifiedNewsSchemaAndRepositoryExposeRequiredOperations() {
        val schema = java.io.File("../shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()
        val repo = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/data/repository/UnifiedNewsSummaryRepository.kt").readText()
        val articleRepo = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/data/repository/ArticleRepository.kt").readText()
        val migration = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt").readText()
        val config = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt").readText()

        assertTrue(schema.contains("CREATE TABLE unified_news_summary"))
        assertTrue(schema.contains("CREATE TABLE unified_news_source"))
        assertTrue(schema.contains("selectUnifiedNewsSummaries"))
        assertTrue(schema.contains("selectFavoriteArticlesByDateRange"))
        assertTrue(schema.contains("insertUnifiedNewsSummary"))
        assertTrue(schema.contains("updateUnifiedNewsSummary"))
        assertTrue(!schema.contains("ON CONFLICT(summary_date, window_key) DO UPDATE"))
        assertTrue(repo.contains("class UnifiedNewsSummaryRepository"))
        assertTrue(repo.contains("fun saveSummaryWithSources"))
        assertTrue(repo.contains("q.transaction"))
        assertFalse(repo.contains("fun replaceSources"))
        assertTrue(articleRepo.contains("fun getFavoritesByDateRangeSync"))
        assertTrue(migration.contains("migrateV6ToV7"))
        assertTrue(config.contains("currentSchemaVersion = 8L"))
    }

    @Test
    fun remoteNewsSourcesHaveDedicatedTableAndMigration() {
        val schema = java.io.File("../shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()
        val config = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt").readText()
        val migration = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt").readText()

        assertTrue(schema.contains("CREATE TABLE remote_news_source"))
        assertTrue(schema.contains("id INTEGER PRIMARY KEY AUTOINCREMENT"))
        assertTrue(schema.contains("name TEXT NOT NULL"))
        assertTrue(schema.contains("base_url TEXT NOT NULL"))
        assertTrue(schema.contains("api_token TEXT NOT NULL"))
        assertTrue(schema.contains("enabled INTEGER NOT NULL DEFAULT 1"))
        assertTrue(schema.contains("created_at INTEGER NOT NULL"))
        assertTrue(schema.contains("updated_at INTEGER NOT NULL"))
        assertTrue(schema.contains("selectRemoteNewsSources"))
        assertTrue(schema.contains("SELECT * FROM remote_news_source ORDER BY created_at ASC;"))
        assertTrue(schema.contains("selectEnabledRemoteNewsSources"))
        assertTrue(schema.contains("SELECT * FROM remote_news_source WHERE enabled = 1 ORDER BY created_at ASC;"))
        assertTrue(schema.contains("selectRemoteNewsSourceById"))
        assertTrue(schema.contains("insertRemoteNewsSource"))
        assertTrue(schema.contains("updateRemoteNewsSource"))
        assertTrue(schema.contains("upsertRemoteNewsSource"))
        assertTrue(schema.contains("deleteRemoteNewsSource"))
        assertTrue(config.contains("currentSchemaVersion = 8L"))
        assertTrue(migration.contains("if (currentVersion < 8)"))
        assertTrue(migration.contains("migrateV7ToV8()"))
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS remote_news_source"))
        assertTrue(migration.contains("SettingKeys.remoteNewsBaseUrl"))
        assertTrue(migration.contains("SettingKeys.remoteNewsApiToken"))
        assertTrue(migration.contains("INSERT INTO remote_news_source (name, base_url, api_token, enabled, created_at, updated_at)"))
        assertTrue(migration.contains("VALUES ('远程新闻'"))
        assertTrue(migration.contains("normalizeTopArticlesTodayUrl(rawBaseUrl)"))
        assertTrue(migration.contains("${'$'}{baseUrl.sqlEscaped()}"))
        assertTrue(migration.contains("${'$'}{apiToken.sqlEscaped()}"))
        assertTrue(migration.contains("1,"))
    }

    @Test
    fun remoteNewsSourceRepositoryProvidesCrudMethods() {
        val repoFile = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/data/repository/RemoteNewsSourceRepository.kt")
        val diModule = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt").readText()

        assertTrue(repoFile.exists())
        val repo = repoFile.readText()
        assertTrue(repo.contains("class RemoteNewsSourceRepository"))
        assertTrue(repo.contains("fun getAll()"))
        assertTrue(repo.contains("fun getEnabled()"))
        assertTrue(repo.contains("fun save("))
        assertTrue(repo.contains("fun delete("))
        assertTrue(diModule.contains("RemoteNewsSourceRepository"))
    }

    @Test
    fun remoteNewsSettingsSupportMultipleSources() {
        val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/settings/remotenews/RemoteNewsSettingsViewModel.kt").readText()
        val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/settings/remotenews/RemoteNewsSettingsScreen.kt").readText()

        assertTrue(viewModel.contains("RemoteNewsSourceRepository"))
        assertTrue(viewModel.contains("RemoteNewsService"))
        assertTrue(viewModel.contains("sources: List<Remote_news_source>"))
        assertTrue(viewModel.contains("isEditing: Boolean"))
        assertTrue(viewModel.contains("editingId"))
        assertTrue(viewModel.contains("name: String"))
        assertTrue(viewModel.contains("baseUrl: String"))
        assertTrue(viewModel.contains("token: String"))
        assertTrue(viewModel.contains("enabled: Boolean"))
        assertTrue(viewModel.contains("isSaving: Boolean"))
        assertTrue(viewModel.contains("isTesting: Boolean"))
        assertTrue(viewModel.contains("message: String?"))
        assertTrue(viewModel.contains("updateName"))
        assertTrue(viewModel.contains("updateBaseUrl"))
        assertTrue(viewModel.contains("updateToken"))
        assertTrue(viewModel.contains("updateEnabled"))
        assertTrue(viewModel.contains("openAdd"))
        assertTrue(viewModel.contains("openEdit"))
        assertTrue(viewModel.contains("closeEditor"))
        assertTrue(viewModel.contains("fun load()"))
        assertTrue(viewModel.contains("fun save()"))
        assertTrue(viewModel.contains("deleteSource"))
        assertTrue(viewModel.contains("fun testConnection()"))
        assertTrue(viewModel.contains("sourceRepo.save"))
        assertTrue(viewModel.contains("sourceRepo.delete"))
        assertTrue(viewModel.contains("fetchTopArticlesToday(config.value, page = 1, limit = 1)"))
        assertTrue(viewModel.contains("连接成功，获取到"))
        assertTrue(viewModel.contains("if (state.value.isSaving) return"))
        assertTrue(viewModel.contains("if (state.value.isTesting) return"))
        assertTrue(viewModel.contains("if (state.value.baseUrl.isBlank() || token.isBlank())"))
        assertTrue(viewModel.contains("try"))
        assertTrue(viewModel.contains("catch"))
        assertTrue(viewModel.contains("startsWith(\"http://\")"))
        assertTrue(viewModel.contains("startsWith(\"https://\")"))
        val saveBody = viewModel.substringAfter("fun save()").substringBefore("fun deleteSource")
        val testConnectionBody = viewModel.substringAfter("fun testConnection()")
        assertTrue(saveBody.indexOf("it.copy(isSaving = true, message = null)") < saveBody.indexOf("viewModelScope.launch(Dispatchers.IO)"))
        assertTrue(testConnectionBody.indexOf("it.copy(isTesting = true, message = null)") < testConnectionBody.indexOf("viewModelScope.launch(Dispatchers.IO)"))
        assertTrue(screen.contains("名称"))
        assertTrue(screen.contains("完整 URL"))
        assertTrue(screen.contains("Token"))
        assertTrue(screen.contains("新增远程新闻"))
        assertTrue(screen.contains("onClick = viewModel::openAdd"))
        assertTrue(screen.contains("RemoteNewsSourceListPage"))
        assertTrue(screen.contains("RemoteNewsSourceEditorPage"))
        assertTrue(screen.contains("RemoteNewsSourceStatusDot"))
        assertTrue(screen.contains("RemoteNewsSourceHelperCard"))
        assertTrue(screen.contains("RemoteNewsMessageCard"))
        assertTrue(screen.contains("PasswordVisualTransformation"))
        assertTrue(screen.contains("VisualTransformation.None"))
        assertTrue(screen.contains("完整接口"))
        assertTrue(screen.contains("state.isEditing"))
        assertTrue(screen.contains("state.sources"))
        assertTrue(screen.contains("viewModel.openEdit(source)"))
        assertTrue(screen.contains("删除"))
        assertTrue(screen.contains("source.name"))
        assertTrue(screen.contains("source.base_url"))
    }

    @Test
    fun remoteArticleDetailUsesReadableArticleLayout() {
        val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt").readText()

        assertTrue(screen.contains("RemoteArticleHeroCard"))
        assertTrue(screen.contains("RemoteArticleMetaChips"))
        assertTrue(screen.contains("MarkdownTabPager"))
        assertTrue(screen.contains("MarkdownTabRow"))
        assertTrue(screen.contains("AI 摘要"))
        assertTrue(screen.contains("原文"))
        assertTrue(screen.contains("阅读详情"))
        assertFalse(screen.contains("RemoteArticleViewpointsSection"))
        assertFalse(screen.contains("RemoteArticleContentSection"))
        assertFalse(screen.contains("RemoteArticleOriginalLinkCard"))
        assertFalse(screen.contains("Text(article.url.orEmpty(), style = MaterialTheme.typography.bodySmall)"))
    }

    @Test
    fun articleDetailsUseSharedMarkdownTabPager() {
        val localDetail = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt").readText()
        val remoteDetail = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt").readText()
        val sharedPager = java.io.File("src/main/kotlin/com/dailysatori/ui/component/content/MarkdownTabPager.kt")

        assertTrue(sharedPager.exists())
        assertTrue(localDetail.contains("MarkdownTabPager("))
        assertTrue(remoteDetail.contains("MarkdownTabPager("))
        assertFalse(localDetail.contains("ArticleMarkdownTabRow"))
        assertFalse(localDetail.contains("private fun ArticleTabRow"))
        assertFalse(localDetail.contains("private fun ArticleMarkdownTabRow"))
        assertFalse(remoteDetail.contains("private fun RemoteArticleTabRow"))
        assertFalse(remoteDetail.contains("RemoteArticleMarkdownContent"))
    }

    @Test
    fun unifiedNewsCollectsAllEnabledRemoteSourcesOnly() {
        val service = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt").readText()
        val models = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsModels.kt").readText()

        assertTrue(service.contains("remoteNewsSourceRepo.getEnabled()"))
        assertTrue(service.contains("fetchTopArticlesToday"))
        assertFalse(service.contains("collectCrayfishNews"))
        assertFalse(service.contains("CrayfishNewsService"))
        assertFalse(models.contains("CRAYFISH_GENERAL"))
        assertFalse(models.contains("CRAYFISH_DJI"))
        assertTrue(models.contains("REMOTE_ARTICLE(\"remote_article\", \"R\")"))
    }

    @Test
    fun unifiedNewsRemoteArticleSourcesPersistConfiguredRemoteSourceId() {
        val service = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt").readText()

        assertTrue(service.contains("sourceFilename = remoteNewsSourceRouteKey(source.id)"))
        assertTrue(service.contains("fun remoteNewsSourceRouteKey(id: Long): String = \"remote_news_source:${'$'}id\""))
    }

    @Test
    fun unifiedNewsRemoteArticleCitationUsesPersistedRemoteSourceIdWithLegacyFallback() {
        val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()
        val di = java.io.File("src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt").readText()
        val repo = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/data/repository/RemoteNewsSourceRepository.kt").readText()

        assertTrue(viewModel.contains("data class RemoteArticle(val id: Long, val remoteSourceId: Long?)"))
        assertTrue(viewModel.contains("parseRemoteNewsSourceRouteKey(filename)"))
        assertTrue(viewModel.contains("openRemoteArticle(target.id, target.remoteSourceId, token)"))
        assertTrue(viewModel.contains("remoteNewsSourceRepo.getById(remoteSourceId)"))
        assertTrue(viewModel.contains("if (remoteSourceId != null && source == null)"))
        assertTrue(viewModel.contains("远程新闻源不存在或已删除"))
        assertTrue(viewModel.contains("remoteConfigOrSetError(token)"))
        val configBody = viewModel.substringAfter("private fun remoteConfigOrSetError").substringBefore("\n}\n")
        val missingSourceBranch = configBody.substringAfter("if (remoteSourceId != null && source == null)").substringBefore("val baseUrl")
        assertFalse(missingSourceBranch.contains("SettingKeys.remoteNewsBaseUrl"))
        assertFalse(missingSourceBranch.contains("SettingKeys.remoteNewsApiToken"))
        assertTrue(repo.contains("fun getById(id: Long)"))
        assertTrue(di.contains("UnifiedNewsViewModel(get(), get(), get(), get(), get(), com.dailysatori.BuildConfig.DEBUG)"))
    }

    @Test
    fun unifiedNewsPromptUsesOnlyRemoteAndFavoriteCitationExamples() {
        val prompt = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsPrompt.kt").readText()

        assertTrue(prompt.contains("- 新闻标题或短句 [R1]"))
        assertTrue(prompt.contains("对远程来源优先使用来源标题"))
        assertFalse(prompt.contains("[C1]"))
        assertFalse(prompt.contains("小龙虾来源"))
    }

    @Test
    fun unifiedNewsMainLoadKeepsDailyGenerationButDisplaysHistoryList() {
        val schema = java.io.File("../shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()
        val repo = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/data/repository/UnifiedNewsSummaryRepository.kt").readText()
        val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()

        assertTrue(schema.contains("selectUnifiedNewsSummaryByDate"))
        assertTrue(repo.contains("fun getByDate(summaryDate: String)"))
        assertTrue(repo.contains("fun getByDateFlow(summaryDate: String)"))
        assertTrue(viewModel.contains("dailyUnifiedNewsWindowFor"))
        assertTrue(viewModel.contains("summaryRepo.getAll().collect"))
        assertFalse(viewModel.contains("visibleSummaryLimit"))
    }

    @Test
    fun unifiedNewsMainLoadPreservesLastSuccessfulDailySummaryDuringFailedRefresh() {
        val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()

        assertTrue(viewModel.contains("lastSuccessfulSummary"))
        assertTrue(viewModel.contains("lastSuccessfulSources"))
        assertTrue(viewModel.contains("todaySummary.isSuccessfulDisplaySummary"))
        assertTrue(viewModel.contains("displaySummaries = summaries.withDisplayFallback"))
        assertTrue(viewModel.contains("lastSources = lastSuccessful?.let"))
        assertTrue(viewModel.contains("sourcesBySummaryId[summary.id] ?: summaryRepo.getSources(summary.id)"))
    }

    @Test
    fun unifiedNewsFailureAndEmptyPreserveExistingDailyContentAndSourcesForRestart() {
        val source = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt").readText()

        assertTrue(source.contains("existing.content"))
        assertTrue(source.contains("summaryRepo.upsertSummary("))
        assertTrue(source.contains("preserveExistingContent"))
    }

    @Test
    fun unifiedNewsServicePersistsEmptyWindowWithoutAiCall() {
        val source = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt").readText()

        assertTrue(source.contains("status = UnifiedNewsSummaryStatus.EMPTY.value"))
        assertTrue(source.contains("skip the AI call"))
    }

    @Test
    fun unifiedNewsServiceFailsUnknownCitations() {
        val source = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt").readText()

        assertTrue(source.contains("invalidCitationTokens"))
        assertTrue(source.contains("UnifiedNewsSummaryStatus.FAILED.value"))
    }

    @Test
    fun budgetUnifiedNewsSourcesLimitsCountAndContentLength() {
        val sources = (1..35).map { index ->
            UnifiedNewsSourceItem(
                refKey = "R$index",
                sourceType = UnifiedNewsSourceType.REMOTE_ARTICLE,
                title = "source $index",
                summary = "summary $index",
                content = "x".repeat(20),
            )
        }

        val budgeted = budgetUnifiedNewsSources(sources, maxSources = 30, maxContentChars = 12)

        assertEquals(30, budgeted.size)
        assertTrue(budgeted.all { it.content.length <= 12 })
        assertEquals("R30", budgeted.last().refKey)
    }

    @Test
    fun defaultUnifiedNewsBudgetKeepsEnoughDailyFileContent() {
        val sources = listOf(
            UnifiedNewsSourceItem(
                refKey = "R1",
                sourceType = UnifiedNewsSourceType.REMOTE_ARTICLE,
                title = "daily file",
                summary = "summary",
                content = "x".repeat(8_000),
            ),
        )

        val budgeted = budgetUnifiedNewsSources(sources)

        assertTrue(budgeted.first().content.length >= 6_000)
    }

    @Test
    fun unifiedNewsServiceHandlesSourceCollectionFailure() {
        val source = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt").readText()

        assertTrue(source.contains("collectSources(window, warnings, ignoreSourceTimeFilter)"))
        assertTrue(source.contains("catch (e: Exception)"))
        assertTrue(source.contains("saveFailure(window"))
    }

    @Test
    fun unifiedNewsServiceFetchesSingleTopArticlesPagePerConfiguredRemoteSource() {
        val source = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt").readText()

        assertTrue(source.contains("remoteNewsSourceRepo.getEnabled().forEach"))
        assertTrue(source.contains("fetchTopArticlesToday(config.value, page = 1, limit = 50)"))
        assertFalse(source.contains("MAX_DIGEST_PAGES"))
        assertFalse(source.contains("pagination.next"))
    }

    @Test
    fun remoteNewsServiceSupportsTopArticlesTodayEndpointAndDualAuth() {
        val source = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/remotenews/RemoteNewsService.kt").readText()

        assertTrue(source.contains("fun fetchTopArticlesToday"))
        assertTrue(source.contains("top_articles_today"))
        assertTrue(source.contains("normalizeTopArticlesTodayUrl"))
        assertTrue(source.contains("URLBuilder(normalizeTopArticlesTodayUrl(baseUrl))"))
        assertTrue(source.contains("externalApiRootFromRemoteNewsUrl"))
        assertTrue(source.contains("substringBefore(\"/api/v1/external/top_articles_today\")"))
        assertFalse(source.contains("${'$'}normalizedBase/api/v1/external/top_articles_today"))
        assertTrue(source.contains("bearerAuth(config.token)"))
        assertTrue(source.contains("header(\"X-Api-Token\", config.token)"))
        assertTrue(source.contains("limit"))
    }

    @Test
    fun unifiedNewsServiceCollectsConfiguredRemoteTopArticles() {
        val source = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt").readText()

        assertTrue(source.contains("collectConfiguredRemoteArticles"))
        assertTrue(source.contains("remoteNewsService.fetchTopArticlesToday"))
        assertTrue(source.contains("result.value.articles.mapNotNull"))
        assertFalse(source.contains("remoteNewsService.fetchDigests"))
        assertFalse(source.contains("remoteNewsService.fetchArticles"))
        assertTrue(source.contains("UnifiedNewsSourceType.REMOTE_ARTICLE"))
    }

    @Test
    fun remoteDigestArticleCollectionDeduplicatesDuplicateArticles() {
        val window = unifiedNewsWindowFor(
            key = UnifiedNewsWindowKey.DAILY,
            dueAt = Instant.parse("2026-05-17T16:00:00Z"),
            timeZone = zone,
        )
        val duplicate = RemoteArticle(
            id = 42,
            title = "duplicate",
            url = "https://example.com/a",
            summary = "summary",
            processedAt = "2026-05-17T10:00:00Z",
        )

        val sources = remoteDigestArticlesToUnifiedSources(
            digests = listOf(RemoteDigest(id = 1, articles = listOf(duplicate, duplicate.copy()))),
            window = window,
            ignoreSourceTimeFilter = true,
        )

        assertEquals(1, sources.size)
        assertEquals(UnifiedNewsSourceType.REMOTE_ARTICLE, sources.single().sourceType)
    }

    @Test
    fun remoteDigestArticlesUseDigestGeneratedTimeFallback() {
        val window = unifiedNewsWindowFor(
            key = UnifiedNewsWindowKey.DAILY,
            dueAt = Instant.parse("2026-05-17T16:00:00Z"),
            timeZone = zone,
        )
        val article = RemoteArticle(id = 42, title = "fallback", summary = "summary")

        val sources = remoteDigestArticlesToUnifiedSources(
            digests = listOf(RemoteDigest(id = 1, generatedAt = "2026-05-17T10:00:00Z", articles = listOf(article))),
            window = window,
            ignoreSourceTimeFilter = true,
        )

        assertEquals(Instant.parse("2026-05-17T10:00:00Z").toEpochMilliseconds(), sources.single().sourceTime)
    }

    @Test
    fun unifiedNewsServiceDoesNotFetchCrayfishCategoriesForGeneration() {
        val source = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt").readText()

        assertFalse(source.contains("category = \"general\""))
        assertFalse(source.contains("category = \"dji\""))
        assertFalse(source.contains("collectCrayfish"))
    }

    @Test
    fun crayfishNewsServiceSupportsArticleListAndDetailEndpoints() {
        val models = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/crayfishnews/CrayfishNewsModels.kt").readText()
        val service = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/crayfishnews/CrayfishNewsService.kt").readText()

        assertTrue(models.contains("data class CrayfishArticleListResponse"))
        assertTrue(models.contains("data class CrayfishArticle"))
        assertTrue(service.contains("arrayOf(\"news\", category)"))
        assertFalse(service.contains("builder.parameters.append(\"category\", category)"))
        assertTrue(service.contains("fun fetchArticleList"))
        assertTrue(service.contains("fun fetchArticle("))
        assertTrue(service.contains("buildUrl(config.baseUrl, \"news\", category, date, \"articles\")"))
        assertTrue(service.contains("buildUrl(config.baseUrl, \"news\", category, date, \"articles\", articleId)"))
    }

    @Test
    fun crayfishRequestsSendBrowserUserAgent() {
        val service = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/crayfishnews/CrayfishNewsService.kt").readText()

        assertTrue(service.contains("private const val CrayfishUserAgent"))
        assertTrue(service.contains("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"))
        assertTrue(service.contains("header(HttpHeaders.UserAgent, CrayfishUserAgent)"))
        assertTrue(service.contains("private fun HttpRequestBuilder.crayfishAuth"))
    }

    @Test
    fun unifiedNewsKeepsCrayfishArticleRouteKeyForLegacyCitationsOnly() {
        val source = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt").readText()

        assertTrue(source.contains("crayfishArticleRouteKey"))
        assertFalse(source.contains("fetchArticleList"))
        assertFalse(source.contains("sourceFilename = crayfishArticleRouteKey"))
    }

    @Test
    fun unifiedNewsWarningsPrefixConfiguredRemoteSourceName() {
        val source = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt").readText()

        assertTrue(source.contains("warnRemoteSourceFailure(warnings, source.name"))
        assertTrue(source.contains("warnSourceFailure(warnings, \"${'$'}sourceName: ${'$'}message\")"))
        assertFalse(source.contains("小龙虾 ${'$'}category 列表为空"))
    }

    @Test
    fun unifiedNewsWorkerUsesOneTimeWorkAndSchedulesStartup() {
        val worker = java.io.File("src/main/kotlin/com/dailysatori/core/worker/UnifiedNewsWorker.kt").readText()
        val app = java.io.File("src/main/kotlin/com/dailysatori/DailySatoriApplication.kt").readText()

        assertTrue(worker.contains("OneTimeWorkRequestBuilder<UnifiedNewsWorker>"))
        assertTrue(worker.contains("setInitialDelay"))
        assertTrue(worker.contains("scheduleNext"))
        assertTrue(worker.contains("inputData.getString(KEY_MODE)"))
        assertTrue(worker.contains("shouldRetryUnifiedNews"))
        assertTrue(worker.contains("scheduleNext(Clock.System.now())"))
        assertTrue(worker.contains("else -> Result.failure()"))
        assertTrue(app.contains("UnifiedNewsScheduler(this).ensureScheduled()"))
    }

    @Test
    fun unifiedNewsStartupDoesNotEnqueueSeparateImmediateBackfillWork() {
        val worker = java.io.File("src/main/kotlin/com/dailysatori/core/worker/UnifiedNewsWorker.kt").readText()

        assertTrue(worker.contains("fun ensureScheduled() {\n        ensureNextScheduled()\n    }"))
        assertFalse(worker.contains("WorkNameBackfill"))
        assertFalse(worker.contains("unified-news-backfill"))
    }

    @Test
    fun unifiedNewsNextWorkRequestSetsDueModeAndDelay() {
        val request = buildUnifiedNewsNextWorkRequest(Instant.parse("2026-05-16T01:00:00Z"))

        assertTrue(request.workSpec.initialDelay > 0L)
        assertEquals(UnifiedNewsWorker.MODE_DUE, request.workSpec.input.getString(UnifiedNewsWorker.KEY_MODE))
    }

    @Test
    fun unifiedNewsBackfillWorkRequestSetsBackfillMode() {
        val request = buildUnifiedNewsBackfillWorkRequest()

        assertEquals(UnifiedNewsWorker.MODE_BACKFILL, request.workSpec.input.getString(UnifiedNewsWorker.KEY_MODE))
    }

    @Test
    fun crayfishNewsListAppendsRequestedLimitQueryParameter() {
        val service = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/crayfishnews/CrayfishNewsService.kt").readText()

        assertTrue(service.contains("parameters.append(\"limit\", limit.toString())"))
        assertTrue(service.contains("URLBuilder"))
    }

    @Test
    fun crayfishNewsListDecodesCategoryEndpointShapes() {
        val itemArray = """[{"filename":"2026-05-17-news-summary.md","generated":"2026-05-17T08:00:00Z"}]"""
        val itemObject = """{"items":[{"filename":"2026-05-17-news-summary.md","generated":"2026-05-17T08:00:00Z"}]}"""
        val categoryObject = """{"general":[{"filename":"2026-05-17-news-summary.md","generated":"2026-05-17T08:00:00Z"}]}"""

        assertEquals("2026-05-17-news-summary.md", decodeCrayfishNewsListResponse(itemArray, "general").general.single().filename)
        assertEquals("2026-05-17-news-summary.md", decodeCrayfishNewsListResponse(itemObject, "general").general.single().filename)
        assertEquals("2026-05-17-news-summary.md", decodeCrayfishNewsListResponse(categoryObject, "general").general.single().filename)
    }

    @Test
    fun unifiedNewsWorkerRetryPolicyDoesNotRetryPermanentFailures() {
        val missingConfig = UnifiedNewsGenerationResult(false, UnifiedNewsSummaryStatus.FAILED, "请先配置 AI 服务")
        val invalidCitation = UnifiedNewsGenerationResult(false, UnifiedNewsSummaryStatus.FAILED, "无效引用: R99")
        val worker = java.io.File("src/main/kotlin/com/dailysatori/core/worker/UnifiedNewsWorker.kt").readText()

        assertFalse(shouldRetryUnifiedNews(missingConfig))
        assertFalse(shouldRetryUnifiedNews(invalidCitation))
        assertTrue(worker.contains("message.startsWith(\"AI \")"))
    }

    @Test
    fun unifiedNewsWorkerRetryPolicyRetriesTransientFailuresOnly() {
        val transient = UnifiedNewsGenerationResult(false, UnifiedNewsSummaryStatus.FAILED, "network timeout")
        val success = UnifiedNewsGenerationResult(true, UnifiedNewsSummaryStatus.SUCCESS)

        assertTrue(shouldRetryUnifiedNews(transient))
        assertFalse(shouldRetryUnifiedNews(success))
    }

    @Test
    fun openAiCompatibleSummariesUseRawChatCompletionPath() {
        val source = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/ai/AiService.kt").readText()

        assertTrue(source.contains("rawOpenAiTextCompletion"))
        assertTrue(source.contains("buildOpenAiTextCompletionMessages"))
        assertTrue(source.contains("extractOpenAiTextCompletionContent"))
    }

    @Test
    fun homeTabsAreReducedAndFirstTabIsUnifiedNews() {
        val home = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt").readText()

        assertTrue(home.contains("TabItem(\"新闻汇总\""))
        assertFalse(home.contains("TabItem(\"文章\""))
        assertFalse(home.contains("TabItem(\"远程新闻\""))
        assertFalse(home.contains("TabItem(\"设置\""))
        assertTrue(home.contains("UnifiedNewsScreen"))
        assertTrue(home.contains("selectedIndex !in tabs.indices"))
        assertTrue(home.contains("selectedIndex = 0"))
        assertTrue(home.substringAfter("when (index)").substringBefore("}").contains("else ->"))
    }

    @Test
    fun unifiedNewsUiRemovesCrayfishDistinction() {
        val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()
        val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()
        val contentFormat = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsContentFormat.kt").readText()
        val viewModelModule = java.io.File("src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt").readText()
        val settings = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt").readText()
        val citationText = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/CitationText.kt").readText()

        assertTrue(screen.contains("本地文章"))
        assertTrue(screen.contains("本地收藏"))
        assertFalse(screen.contains("CrayfishNewsScreen"))
        assertFalse(screen.contains("RemoteNewsScreen"))
        assertFalse(screen.contains("UnifiedNewsPage.REMOTE_NEWS"))
        assertFalse(screen.contains("小龙虾新闻"))
        assertFalse(screen.contains("MenuItem(\"远程新闻\""))
        assertFalse(screen.contains("crayfishTitleOverrides"))
        assertFalse(screen.contains("isCrayfish"))
        assertFalse(viewModel.contains("REMOTE_NEWS"))
        assertFalse(viewModel.contains("CrayfishArticle"))
        assertFalse(viewModel.contains("CrayfishGeneral"))
        assertFalse(viewModel.contains("CrayfishDji"))
        assertFalse(viewModel.contains("CrayfishNewsService"))
        assertFalse(viewModel.contains("crayfishConfigOrSetError"))
        assertFalse(viewModel.contains("parseCrayfishArticleRouteKey"))
        assertFalse(viewModel.contains("selectedCrayfish"))
        assertFalse(screen.contains("CrayfishNewsDetailScreen"))
        assertFalse(screen.contains("CrayfishArticleDetailScreen"))
        assertFalse(screen.contains("crayfishArticleMarkdown"))
        assertFalse(screen.contains("plainCrayfishArticleText"))
        assertFalse(contentFormat.contains("小龙虾文章"))
        assertFalse(contentFormat.contains("小龙虾新闻"))
        assertFalse(contentFormat.contains("crayfish_general"))
        assertFalse(contentFormat.contains("crayfish_dji"))
        assertFalse(viewModelModule.contains("RemoteNewsViewModel"))
        assertFalse(viewModelModule.contains("CrayfishNewsViewModel"))
        assertFalse(viewModelModule.contains("CrayfishNewsSettingsViewModel"))
        assertFalse(settings.contains("CRAYFISH_NEWS_SETTINGS"))
        assertFalse(settings.contains("小龙虾新闻设置"))
        assertTrue(settings.contains("远程新闻设置"))
        assertFalse(screen.substringAfter("private fun SkeletonLine").substringBefore("private fun UnifiedNewsMenu").contains("14.dp"))
        assertFalse(screen.substringAfter("private fun TodayUnifiedNewsCard").substringBefore("CitationText(").contains("1.dp"))
        assertFalse(citationText.substringAfter("private fun UnifiedNewsBulletItem").substringBefore("private fun unifiedNewsDisplayBlocks").contains("6.dp"))
        assertFalse(screen.substringAfter("private fun UnifiedNewsMenu").contains("设置"))
        assertTrue(screen.contains("生成/更新当日新闻"))
        assertFalse(screen.contains("刷新/重新生成"))

        val articleList = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleListScreen.kt").readText()
        val articleVm = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticlesViewModel.kt").readText()
        assertTrue(articleList.contains("showFavoritesOnly: Boolean = false"))
        assertTrue(articleVm.contains("fun setFavoritesOnly"))
    }

    @Test
    fun mainPagesExposeMyEntryFromTopBar() {
        val topBar = java.io.File("src/main/kotlin/com/dailysatori/ui/component/appbar/AppTopBar.kt").readText()
        val scaffold = java.io.File("src/main/kotlin/com/dailysatori/ui/component/scaffold/AppScaffold.kt").readText()
        val home = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt").readText()
        val unified = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()

        assertTrue(topBar.contains("Icons.Default.AccountCircle"))
        assertTrue(topBar.contains("contentDescription = myNavigationLabel"))
        assertFalse(topBar.contains("TextButton"))
        assertTrue(scaffold.contains("onMyNavigationClick"))
        assertTrue(home.contains("showMy"))
        assertTrue(home.contains("SettingsScreen(settingsViewModel, onBack = { showMy = false })"))
        assertTrue(unified.contains("myNavigationLabel = \"我的\""))
    }

    @Test
    fun unifiedNewsSummaryDoesNotExposeSourceWarningsToReaders() {
        val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()
        val cardBody = screen.substringAfter("private fun TodayUnifiedNewsCard").substringBefore("private fun UnifiedNewsSourceCard")
        val bannerBody = screen.substringAfter("private fun UnifiedNewsStatusBanner").substringBefore("@Composable\nprivate fun UnifiedNewsMenu")

        assertFalse(screen.contains("UnifiedNewsStatusBanner"))
        assertFalse(bannerBody.contains("summaryError"))
        assertFalse(bannerBody.contains("sourceWarnings"))
        assertFalse(cardBody.contains("source_warnings"))
    }

    @Test
    fun unifiedNewsSummaryDisplaysRegeneratingState() {
        val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()

        assertTrue(screen.contains("isRegenerating"))
        assertTrue(screen.contains("UnifiedNewsGeneratingSkeleton"))
        assertFalse(screen.contains("正在生成新的新闻汇总"))
        assertFalse(screen.contains("新闻汇总已更新"))
    }

    @Test
    fun unifiedNewsSummaryUsesMarkdownRendererForReadableFormatting() {
        val citationText = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/CitationText.kt").readText()
        assertTrue(citationText.contains("com.mikepenz.markdown.m3.Markdown"))
        assertTrue(citationText.contains("Markdown("))
        assertTrue(citationText.contains("UnifiedNewsBulletItem"))
        assertTrue(citationText.contains("flushMarkdownBuffer"))
        val bulletBody = citationText.substringAfter("private fun UnifiedNewsBulletItem").substringBefore("private fun unifiedNewsDisplayBlocks")
        assertFalse(bulletBody.contains("Surface("))
        assertFalse(bulletBody.contains("Card("))
        assertTrue(bulletBody.contains("Box("))
        assertTrue(bulletBody.contains("CircleShape"))
        assertTrue(citationText.contains("Column(modifier = modifier"))
        assertFalse(citationText.contains("FlowRow("))
    }

    @Test
    fun unifiedNewsContentFormatRemovesGeneratedTitle() {
        val markdown = "# 今日统一新闻总结\n\n## 重点速览\n- 新闻 [C1]"

        assertEquals("## 🗞️ 重点速览\n- 新闻 [C1]", displayUnifiedNewsMarkdown(markdown))
    }

    @Test
    fun unifiedNewsContentFormatRemovesBlankLinesBetweenListItems() {
        val markdown = "## 值得关注\n- 第四条 [C4]\n\n- 第五条 [C5]\n\n普通段落\n\n- 新列表 [C6]"

        assertEquals(
            "## 🗞️ 值得关注\n- 第四条 [C4]\n- 第五条 [C5]\n\n普通段落\n\n- 新列表 [C6]",
            displayUnifiedNewsMarkdown(markdown),
        )
    }

    @Test
    fun unifiedNewsContentFormatNormalizesCitationLinesToBullets() {
        val markdown = "## AI\nOpenAI 发布更新 [C1]\n1. Claude Code 增强 [R2]\n\n## 体育\n赛事结果 [C3]"

        assertEquals(
            "## 🤖 AI\n- OpenAI 发布更新 [C1]\n- Claude Code 增强 [R2]\n\n## 🏅 体育\n- 赛事结果 [C3]",
            displayUnifiedNewsMarkdown(markdown),
        )
    }

    @Test
    fun unifiedNewsBulletItemsUseSofterButReadableTextColor() {
        val citationText = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/CitationText.kt").readText()
        val bulletBody = citationText.substringAfter("private fun UnifiedNewsBulletItem").substringBefore("private fun unifiedNewsDisplayBlocks")

        assertTrue(bulletBody.contains("color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)"))
        assertFalse(bulletBody.contains("color = MaterialTheme.colorScheme.onSurfaceVariant"))
    }

    @Test
    fun unifiedNewsCitationsDoNotUseCrayfishSpecificTitleOverrides() {
        val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()
        val citationText = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/CitationText.kt").readText()

        assertFalse(screen.contains("crayfishTitleOverrides"))
        assertFalse(screen.contains("source.source_type.startsWith(\"crayfish_\")"))
        assertFalse(screen.contains("titleOverrides ="))
        assertTrue(citationText.contains("titleOverrides: Map<String, String> = emptyMap()"))
        assertTrue(citationText.contains("titleOverrides[block.citation] ?: block.text"))
    }

    @Test
    fun unifiedNewsTitleOverridesStripMarkdownMarkers() {
        val citationText = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/CitationText.kt").readText()

        assertTrue(citationText.contains("plainUnifiedNewsListText"))
        assertTrue(citationText.contains("plainUnifiedNewsListText(titleOverrides[block.citation] ?: block.text)"))
    }

    @Test
    fun unifiedNewsSummaryCardsUseSofterBorder() {
        val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()
        val cardBody = screen.substringAfter("private fun TodayUnifiedNewsCard").substringBefore("elevation =")

        assertTrue(cardBody.contains("outlineVariant.copy(alpha = 0.45f)"))
    }

    @Test
    fun unifiedNewsContentFormatHidesCitationTokensButKeepsLinks() {
        val markdown = "- 新闻 A [C1]\n- 新闻 B [R2]"

        assertEquals("daily-satori-citation://C1", unifiedNewsCitationUrl("C1"))
        assertEquals(
            "- [新闻 A](daily-satori-citation://C1)\n- [新闻 B](daily-satori-citation://R2)",
            unifiedNewsMarkdownWithCitationLinks(markdown),
        )
    }

    @Test
    fun unifiedNewsContentFormatRemovesStandaloneCitationTokens() {
        assertEquals("普通句子", visibleUnifiedNewsTextWithoutCitation("普通句子 [C29]"))
        assertEquals("- 列表内容", visibleUnifiedNewsTextWithoutCitation("- 列表内容 [R3]"))
        assertEquals("- OpenAI 发布 Model Spec", visibleUnifiedNewsTextWithoutCitation("- **OpenAI 发布 Model Spec** [R3]"))
    }

    @Test
    fun unifiedNewsSummaryLineExposesPrimaryCitationForWholeLineClick() {
        assertEquals("R2", primaryCitationInUnifiedNewsLine("* 重点新闻正文 [R2]"))
        assertEquals("C12", primaryCitationInUnifiedNewsLine("- 小龙虾新闻 [C12] [R2]"))
        assertNull(primaryCitationInUnifiedNewsLine("## 重点速览"))
    }

    @Test
    fun unifiedNewsPromptDoesNotAskForDuplicateTitle() {
        val prompt = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsPrompt.kt").readText()

        assertFalse(prompt.contains("# 今日统一新闻总结"))
        assertTrue(prompt.contains("不要输出总标题"))
    }

    @Test
    fun unifiedNewsPromptUsesDynamicNewsCategories() {
        val prompt = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsPrompt.kt").readText()

        assertFalse(prompt.contains("输出结构使用：## 重点速览、## 值得关注"))
        assertTrue(prompt.contains("按今天新闻内容动态合并为 3-5 个大类"))
        assertTrue(prompt.contains("AI"))
        assertTrue(prompt.contains("世界新闻"))
        assertTrue(prompt.contains("工具提效"))
        assertTrue(prompt.contains("体育"))
    }

    @Test
    fun unifiedNewsPromptRequiresMarkdownBulletItems() {
        val prompt = buildUnifiedNewsPrompt(
            window = UnifiedNewsWindow(UnifiedNewsWindowKey.DAILY, "2026-05-17", 0L, 1L),
            sources = listOf(
                UnifiedNewsSourceItem(
                    refKey = "R1",
                    sourceType = UnifiedNewsSourceType.REMOTE_ARTICLE,
                    title = "OpenAI 公布 Model Spec",
                    summary = "OpenAI 公布 Model Spec",
                ),
            ),
        )

        assertTrue(prompt.contains("每条新闻使用 Markdown 无序列表"))
        assertTrue(prompt.contains("- 新闻标题或短句 [R1]"))
    }

    @Test
    fun unifiedNewsPromptRequiresConciseDynamicCategoryNames() {
        val prompt = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsPrompt.kt").readText()

        assertTrue(prompt.contains("分类名必须简洁"))
        assertTrue(prompt.contains("2-5 个汉字"))
        assertTrue(prompt.contains("不要：全球安全威胁与基础设施事件"))
        assertTrue(prompt.contains("要：安全威胁"))
        assertTrue(prompt.contains("不要：AI前沿模型与能力突破"))
        assertTrue(prompt.contains("要：AI前沿"))
    }

    @Test
    fun unifiedNewsTopRemoteArticlesAreUsedDirectlyForPrompt() {
        val source = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt").readText()

        assertTrue(source.contains("fetchTopArticlesToday"))
        assertTrue(source.contains("result.value.articles.mapNotNull"))
        assertFalse(source.contains("fetchDigestArticleSources"))
        assertFalse(source.contains("fetchRemoteArticleDetails"))
        assertFalse(source.contains("remoteNewsService.fetchArticle(config, articleId)"))
        assertFalse(source.contains("remoteDigestArticlesToUnifiedSources(result.value.digests, window, ignoreSourceTimeFilter)"))
    }

    @Test
    fun unifiedNewsServiceDoesNotGenerateCrayfishPromptSources() {
        val source = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt").readText()

        assertFalse(source.contains("fetchArticleList"))
        assertFalse(source.contains("crayfishArticleToUnifiedSource"))
        assertFalse(source.contains("UnifiedNewsSourceType.CRAYFISH"))
    }

    @Test
    fun unifiedNewsSecondaryPagesReturnToSummaryAndLockFavorites() {
        val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()
        val articleList = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleListScreen.kt").readText()

        assertTrue(screen.contains("BackHandler"))
        assertTrue(screen.contains("UnifiedNewsPage.SUMMARY"))
        assertTrue(articleList.contains("lockFavoritesFilter: Boolean = false"))
        assertTrue(screen.contains("lockFavoritesFilter = true"))
    }

    @Test
    fun unifiedNewsViewModelGuardsLoadCollectorAndHandlesCitationClicks() {
        val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()
        val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()

        assertTrue(viewModel.contains("private var loadJob: Job?"))
        assertTrue(viewModel.contains("if (loadJob != null) return"))
        assertTrue(viewModel.contains("fun openCitation(source: Unified_news_source)"))
        assertTrue(viewModel.contains("fun openCitationSource"))
        assertTrue(screen.contains("onCitationClick = viewModel::openCitation"))
    }

    @Test
    fun unifiedNewsSummaryRendersSummaryList() {
        val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()
        val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()

        assertTrue(screen.contains("visibleSummaries.isEmpty()"))
        assertTrue(screen.contains("items(visibleSummaries"))
        assertTrue(screen.contains("summary = summary"))
        assertTrue(screen.contains("TodayUnifiedNewsCard"))
        assertTrue(screen.contains("unifiedNewsSummaryTitle"))
        assertFalse(screen.contains("Text(summary.title"))
        assertTrue(viewModel.contains("summaryRepo.getAll()"))
        assertFalse(viewModel.contains("summaries = listOfNotNull(displayed)"))
    }

    @Test
    fun unifiedNewsSummaryFeedHidesSourceCards() {
        val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()
        val cardBody = screen.substringAfter("private fun TodayUnifiedNewsCard").substringBefore("@Composable\nprivate fun CrayfishArticleDetailScreen")

        assertTrue(screen.contains("TodayUnifiedNewsCard"))
        assertFalse(cardBody.contains("UnifiedNewsSourceCard"))
        assertFalse(cardBody.contains("Text(\"来源\""))
        assertFalse(cardBody.contains("sources.forEach"))
        assertFalse(screen.contains("加载更多"))
        assertFalse(screen.contains("LoadMoreWhenAtEnd"))
    }

    @Test
    fun unifiedNewsSummaryHeaderUsesSingleLineDateAndSourceCount() {
        val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()
        val format = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsContentFormat.kt").readText()

        assertTrue(format.contains("unifiedNewsSummaryTitle"))
        assertTrue(format.contains("年"))
        assertTrue(format.contains("月"))
        assertTrue(format.contains("日总结"))
        assertTrue(screen.contains("unifiedNewsSummaryTitle(summary.summary_date)"))
        assertFalse(screen.contains("Text(\"今日总结\""))
        assertFalse(screen.contains("unifiedNewsSummaryTimeLabel(summary.summary_date, summary.window_key)"))
    }

    @Test
    fun unifiedNewsSummaryCardDoesNotRenderErrorsInsideContent() {
        val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()
        val cardBody = screen.substringAfter("private fun TodayUnifiedNewsCard").substringBefore("@Composable\nprivate fun CrayfishArticleDetailScreen")

        assertFalse(cardBody.contains("error_message"))
        assertFalse(cardBody.contains("source_warnings"))
        assertFalse(cardBody.contains("新闻来源提醒"))
    }

    @Test
    fun unifiedNewsMarkdownListSpacingIsComfortableForReading() {
        val styles = java.io.File("src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt").readText()
        val typographyBody = styles.substringAfter("fun typography(): MarkdownTypography").substringBefore("fun cardTypography")
        val paddingBody = styles.substringAfter("fun padding(): MarkdownPadding").substringBefore("}")

        assertTrue(typographyBody.contains("lineHeight = 26.sp"))
        assertFalse(typographyBody.contains("list = TextStyle(\n            fontFamily = LatoFontFamily,\n            fontWeight = FontWeight.Medium,\n            fontSize = 16.sp,\n            lineHeight = 28.sp"))
        assertTrue(paddingBody.contains("listItemBottom = 38.dp"))
        assertTrue(paddingBody.contains("list = 16.dp"))
    }

    @Test
    fun unifiedNewsSummaryLoadKeepsPreviousSummariesVisible() {
        val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()
        val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()

        assertTrue(screen.contains("sources = state.sourcesBySummaryId[summary.id].orEmpty()"))
        assertTrue(viewModel.contains("dailyUnifiedNewsWindowFor"))
        assertTrue(viewModel.contains("summaryRepo.getAll().collect"))
        assertTrue(viewModel.contains("summaries = displaySummaries"))
    }

    @Test
    fun unifiedNewsLoadFallsBackToLatestSuccessfulWhenTodayIsEmpty() {
        val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()

        assertTrue(viewModel.contains("summaryRepo.getLatestSuccessful()"))
        assertTrue(viewModel.contains("latestSuccessfulFallback"))
        assertTrue(viewModel.contains("displaySummaries = summaries.withDisplayFallback"))
    }

    @Test
    fun unifiedNewsSummaryDoesNotUseTimelinePagination() {
        val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()
        val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()

        assertFalse(viewModel.contains("visibleSummaryLimit"))
        assertFalse(viewModel.contains("fun loadMoreSummaries()"))
        assertFalse(screen.contains("take(state.visibleSummaryLimit)"))
        assertFalse(screen.contains("LoadMoreWhenAtEnd"))
        assertFalse(screen.contains("viewModel::loadMoreSummaries"))
    }

    @Test
    fun unifiedNewsSummaryCardsUseEditorialSeparation() {
        val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()

        assertTrue(screen.contains("Card("))
        assertTrue(screen.contains("CardDefaults"))
        assertTrue(screen.contains("RoundedCornerShape(Radius.l)"))
        assertTrue(screen.contains("BorderStroke"))
        assertTrue(screen.contains("${'$'}{sources.size} 个来源"))
        assertFalse(screen.contains("UnifiedNewsSourceCard"))
    }

    @Test
    fun unifiedNewsFeedAvoidsTopGapAndSingleDailySummaryHasNoLoadMore() {
        val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()
        val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()

        assertFalse(viewModel.contains("visibleSummaryLimit"))
        assertFalse(screen.contains("加载更多..."))
        assertTrue(screen.contains("UnifiedNewsGeneratingSkeleton("))
        assertFalse(screen.contains("if (isRegenerating || !error.isNullOrBlank()) {\n            item"))
    }

    @Test
    fun unifiedNewsRegenerationKeepsStatusVisibleOutsideEmptyState() {
        val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()

        assertTrue(screen.contains("UnifiedNewsGeneratingSkeleton("))
        assertTrue(screen.indexOf("UnifiedNewsGeneratingSkeleton(") < screen.indexOf("when {"))
        assertFalse(screen.contains("UnifiedNewsStatusBanner("))
    }

    @Test
    fun unifiedNewsRegenerationHidesTodaySummaryAndTitlesSkeleton() {
        val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()

        assertTrue(screen.contains("visibleSummaries"))
        assertTrue(screen.contains("state.isRegenerating"))
        assertTrue(screen.contains("summary.summary_date != state.regeneratingSummaryDate"))
        assertTrue(screen.contains("UnifiedNewsGeneratingSkeleton(summaryDate = state.regeneratingSummaryDate)"))
        assertTrue(screen.contains("unifiedNewsSummaryTitle(summaryDate)"))
    }

    @Test
    fun unifiedNewsDetailLoadingAndFailureStayOnDetailSurface() {
        val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()
        val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()

        assertTrue(screen.contains("UnifiedNewsSourceDetailLoadingScreen("))
        assertTrue(screen.contains("UnifiedNewsSourceDetailErrorScreen("))
        assertTrue(screen.indexOf("state.navigationTarget != null && state.isLoading") < screen.indexOf("when (state.page)"))
        assertTrue(screen.indexOf("state.navigationTarget != null && state.error != null") < screen.indexOf("when (state.page)"))
        assertTrue(viewModel.contains("error = null"))
        assertTrue(viewModel.contains("navigationTarget = target"))
    }

    @Test
    fun unifiedNewsRegenerationUsesSummaryListState() {
        val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()

        assertTrue(viewModel.contains("selectedSummary = displaySummaries.firstOrNull()"))
        assertTrue(viewModel.contains("sourcesBySummaryId = sourcesBySummaryId"))
        assertTrue(viewModel.contains("displaySummaries"))
    }

    @Test
    fun unifiedNewsManualRegenerationShowsEmptyResultMessage() {
        val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()
        val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()

        assertTrue(viewModel.contains("manualRefreshMessage"))
        assertTrue(viewModel.contains("UnifiedNewsSummaryStatus.EMPTY"))
        assertTrue(viewModel.contains("当前时间窗口暂无可总结新闻"))
        assertFalse(viewModel.contains("UnifiedNewsSummaryStatus.SUCCESS -> \"新闻汇总已更新\""))
        assertTrue(screen.contains("manualRefreshMessage"))
        assertTrue(screen.contains("state.manualRefreshMessage"))
        assertTrue(screen.contains("state.error"))
    }

    @Test
    fun unifiedNewsRegenerationUsesDailyWindowAndOverwritesToday() {
        val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()
        val service = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt").readText()

        assertTrue(viewModel.contains("summaryService.generateDaily"))
        assertFalse(viewModel.contains("manualRefreshWindowForEnvironment(currentWindow"))
        assertTrue(service.contains("suspend fun generateDaily"))
        assertTrue(service.contains("dailyUnifiedNewsWindowFor"))
        assertTrue(service.contains("UnifiedNewsWindowKey.DAILY"))
    }

    @Test
    fun debugManualRefreshUsesRecentTwoDaysWindowForTesting() {
        val current = unifiedNewsWindowFor(
            key = UnifiedNewsWindowKey.W2100,
            dueAt = Instant.parse("2026-05-16T13:00:00Z"),
            timeZone = zone,
        )

        val testing = manualRefreshWindowForEnvironment(current, isDebugBuild = true, timeZone = zone)

        assertEquals("2026-05-16", testing.summaryDate)
        assertEquals(current.key, testing.key)
        assertEquals(Instant.parse("2026-05-14T16:00:00Z").toEpochMilliseconds(), testing.startMs)
        assertEquals(current.endMs, testing.endMs)
    }

    @Test
    fun productionManualRefreshKeepsCurrentNaturalDayWindow() {
        val current = unifiedNewsWindowFor(
            key = UnifiedNewsWindowKey.W2100,
            dueAt = Instant.parse("2026-05-16T13:00:00Z"),
            timeZone = zone,
        )

        assertEquals(current, manualRefreshWindowForEnvironment(current, isDebugBuild = false, timeZone = zone))
    }

    @Test
    fun debugManualRefreshSkipsSourceTimeFilteringForTestability() {
        val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()
        val service = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt").readText()

        assertTrue(viewModel.contains("ignoreSourceTimeFilter = isDebugBuild"))
        assertTrue(service.contains("ignoreSourceTimeFilter: Boolean = false"))
        assertTrue(service.contains("if (!ignoreSourceTimeFilter && time !in window.startMs..window.endMs)"))
    }

    @Test
    fun unifiedNewsMarkdownUsesRememberedDisplayContent() {
        val citationText = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/CitationText.kt").readText()

        assertTrue(citationText.contains("remember(content)"))
        assertTrue(citationText.contains("displayContent"))
        assertTrue(citationText.contains("Markdown("))
    }

    @Test
    fun unifiedNewsCitationRoutingDoesNotOpenCrayfishDetails() {
        val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()
        val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()

        assertFalse(viewModel.contains("CrayfishArticle"))
        assertFalse(viewModel.contains("parseCrayfishArticleRouteKey"))
        assertFalse(viewModel.contains("fetchArticle(config, category"))
        assertFalse(viewModel.contains("selectedCrayfish"))
        assertFalse(viewModel.contains("crayfishArticleCache"))
        assertFalse(screen.contains("CrayfishArticleDetailScreen"))
        assertFalse(screen.contains("crayfishArticleMarkdown(article)"))
    }

    @Test
    fun unifiedNewsScreenDoesNotOwnCrayfishArticleDetailFormatting() {
        val models = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/crayfishnews/CrayfishNewsModels.kt").readText()
        val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()

        assertTrue(models.contains("val content: String? = null"))
        assertTrue(models.contains("val markdown: String? = null"))
        assertFalse(screen.contains("article.content?.takeIf"))
        assertFalse(screen.contains("article.markdown?.takeIf"))
        assertFalse(screen.contains("article.summary.takeIf"))
    }

    @Test
    fun crayfishArticleRequestsEncodePathSegments() {
        val service = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/crayfishnews/CrayfishNewsService.kt").readText()

        assertTrue(service.contains("appendPathSegments"))
        assertTrue(service.contains("buildUrl(config.baseUrl, \"news\", category, date, \"articles\", articleId)"))
        assertFalse(service.contains("\"news/${'$'}category/${'$'}date/articles/${'$'}articleId\""))
    }

    @Test
    fun unifiedNewsCitationRoutingHandlesAllSourceTypes() {
        val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()
        val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()

        assertTrue(viewModel.contains("sealed class UnifiedNewsNavigationTarget"))
        assertTrue(viewModel.contains("RemoteDigest"))
        assertTrue(viewModel.contains("RemoteArticle"))
        assertTrue(viewModel.contains("LocalArticle"))
        assertTrue(viewModel.contains("selectedRemoteDigest"))
        assertTrue(viewModel.contains("selectedRemoteArticle"))
        assertFalse(viewModel.contains("CrayfishGeneral"))
        assertFalse(viewModel.contains("CrayfishDji"))
        assertFalse(viewModel.contains("selectedCrayfish"))
        assertTrue(viewModel.contains("detailRequestToken") || viewModel.contains("detailLoadJob"))
        assertTrue(viewModel.substringAfter("fun closeSourceDetail()").substringBefore("fun switchPage").contains("detailRequestToken"))
        assertTrue(viewModel.contains("clearSelectedSourceDetail"))
        assertTrue(viewModel.substringAfter("fun loadInitial()").substringBefore("fun openCitation").contains("catch") || viewModel.substringAfter("fun loadInitial()").substringBefore("fun openCitation").contains("runCatching"))
        assertTrue(viewModel.substringAfter("fun regenerateCurrentWindow()").substringBefore("private fun latestDueWindow").contains("catch") || viewModel.substringAfter("fun regenerateCurrentWindow()").substringBefore("private fun latestDueWindow").contains("runCatching"))
        assertTrue(screen.contains("onCitationClick"))
        assertTrue(screen.contains("RemoteDigestDetailScreen"))
        assertTrue(screen.contains("RemoteArticleDetailScreen"))
        assertFalse(screen.contains("CrayfishNewsDetailScreen"))
    }

    @Test
    fun remoteArticleDetailSummaryTabCombinesSummaryAndViewpointsOnly() {
        val content = remoteArticleDetailPageContent(
            page = 0,
            summary = "AI 摘要内容",
            viewpoints = listOf("观点一", "观点二"),
            original = "原文内容",
        )

        assertTrue(content.contains("AI 摘要内容"))
        assertTrue(content.contains("## 关键观点"))
        assertTrue(content.contains("- 观点一"))
        assertTrue(content.contains("- 观点二"))
        assertFalse(content.contains("原文内容"))
    }

    @Test
    fun remoteArticleDetailOriginalTabShowsOriginalOnly() {
        val content = remoteArticleDetailPageContent(
            page = 1,
            summary = "AI 摘要内容",
            viewpoints = listOf("观点一"),
            original = "原文内容",
        )

        assertEquals("原文内容", content)
    }

    @Test
    fun remoteArticleDetailTabsUseFallbacksForMissingContent() {
        assertEquals(
            "暂无摘要内容",
            remoteArticleDetailPageContent(page = 0, summary = " ", viewpoints = emptyList(), original = null),
        )
        assertEquals(
            "暂无原文内容",
            remoteArticleDetailPageContent(page = 1, summary = null, viewpoints = listOf("观点"), original = ""),
        )
    }

    @Test
    fun unifiedNewsWorkerModeParsesKnownModes() {
        assertEquals(UnifiedNewsWorkerMode.DUE, unifiedNewsWorkerMode("due"))
        assertEquals(UnifiedNewsWorkerMode.BACKFILL, unifiedNewsWorkerMode("backfill"))
    }

    @Test
    fun unifiedNewsWorkerModeRejectsUnknownModes() {
        assertNull(unifiedNewsWorkerMode("unknown"))
        assertNull(unifiedNewsWorkerMode(null))
    }
}

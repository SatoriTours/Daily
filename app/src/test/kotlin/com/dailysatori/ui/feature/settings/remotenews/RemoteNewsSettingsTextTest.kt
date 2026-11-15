package com.dailysatori.ui.feature.settings.remotenews

import androidx.work.WorkInfo
import com.dailysatori.shared.db.Remote_news_source
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteNewsSettingsTextTest {
    @Test
    fun summaryMetricsUseSyncedArticleCounts() {
        val source = remoteNewsSource(id = 7)
        val state = RemoteNewsSettingsState(
            sources = listOf(source),
            syncedArticleCount = 42,
            syncedArticleCountBySourceId = mapOf(source.id to 18),
        )

        assertEquals(
            listOf(
                RemoteNewsSummaryMetric("1", "已连接来源"),
                RemoteNewsSummaryMetric("42", "一共同步"),
            ),
            remoteNewsSummaryMetrics(state),
        )
        assertEquals("一共同步 18 条", remoteNewsSourceSyncedCountText(source, state))
        assertEquals("已同步 42 篇远程文章", remoteNewsSummarySubtitle(state))
    }

    @Test
    fun missingSourceSyncCountFallsBackToZero() {
        val source = remoteNewsSource(id = 9)

        assertEquals("一共同步 0 条", remoteNewsSourceSyncedCountText(source, RemoteNewsSettingsState()))
        assertEquals("尚未连接远程新闻源", remoteNewsSummarySubtitle(RemoteNewsSettingsState()))
    }

    @Test
    fun sourceRowsExposePerSourceSyncAction() {
        val screen = File("src/main/kotlin/com/dailysatori/ui/feature/settings/remotenews/RemoteNewsSettingsScreen.kt").readText()
        val viewModel = File("src/main/kotlin/com/dailysatori/ui/feature/settings/remotenews/RemoteNewsSettingsViewModel.kt").readText()

        assertTrue(screen.contains("onSync = { viewModel.syncSource(source.id) }"))
        assertTrue(screen.contains("onCancelSync = { viewModel.cancelSync(source.id) }"))
        assertTrue(screen.contains("syncWork = state.syncWorkBySourceId[source.id]"))
        assertTrue(screen.contains("RemoteNewsSyncProgressBox(syncWork)"))
        assertTrue(screen.contains("Text(remoteNewsSyncActionLabel(syncWork))"))
        assertTrue(viewModel.contains("remoteArticleSyncTaskPayloadJson(mode = \"manual_source\", sourceId = id)"))
        assertTrue(viewModel.contains("uniqueKey = \"remote_article_sync:source:${'$'}id\""))
    }

    @Test
    fun syncWorkShowsProgressAndCancelCopy() {
        val source = remoteNewsSource(id = 7)
        val work = RemoteNewsSyncWorkUi(
            state = WorkInfo.State.RUNNING,
            current = 1,
            total = 1,
            message = "已同步 2 篇新文章，更新 1 篇",
            inserted = 2,
            updated = 1,
            skipped = 3,
        )

        assertEquals("取消同步", remoteNewsSyncActionLabel(work))
        assertTrue(remoteNewsSyncActionEnabled(source, work))
        assertEquals("同步中", remoteNewsEffectiveStatusLabel(source, work))
        assertEquals("已同步 2 篇新文章，更新 1 篇", remoteNewsSyncProgressTitle(work))
        assertEquals("1 / 1 个来源", remoteNewsSyncProgressText(work))
        assertEquals(1f, remoteNewsSyncProgressFraction(work))
        assertEquals(
            listOf(
                RemoteNewsSummaryMetric("2", "新增"),
                RemoteNewsSummaryMetric("1", "更新"),
                RemoteNewsSummaryMetric("3", "跳过"),
            ),
            remoteNewsProgressMetrics(work),
        )
        assertEquals(
            listOf(
                RemoteNewsSummaryMetric("已同步 2 篇新文章，更新 1 篇", "当前阶段"),
                RemoteNewsSummaryMetric("1 / 1 个来源", "同步进度"),
            ),
            remoteNewsRunningDetailLines(work),
        )
    }

    @Test
    fun queuedSyncWorkShowsWaitingCopy() {
        val source = remoteNewsSource(id = 7)
        val work = RemoteNewsSyncWorkUi(
            state = WorkInfo.State.ENQUEUED,
            current = 0,
            total = 1,
            message = "",
        )

        assertEquals("等待同步", remoteNewsSyncProgressTitle(work))
        assertEquals("同步中", remoteNewsEffectiveStatusLabel(source, work))
        assertEquals("取消同步", remoteNewsSyncActionLabel(work))
        assertEquals(0.04f, remoteNewsSyncProgressFraction(work))
    }

    @Test
    fun duplicateSyncShowsAlreadyRunningMessage() {
        val running = RemoteNewsSyncWorkUi(
            state = WorkInfo.State.RUNNING,
            current = 0,
            total = 1,
            message = "同步中",
        )

        assertTrue(remoteNewsHasActiveSync(RemoteNewsSettingsState(syncingSourceId = 7)))
        assertTrue(remoteNewsHasActiveSync(RemoteNewsSettingsState(syncWorkBySourceId = mapOf(7L to running))))
        assertEquals("远程新闻同步任务正在执行，请稍后再试", remoteNewsDuplicateSyncMessage())
    }

    @Test
    fun emptyObservedTaskDoesNotClearQueuedPlaceholder() {
        val queuedPlaceholder = RemoteNewsSyncWorkUi(
            taskId = null,
            createdAt = 1_000,
            state = WorkInfo.State.ENQUEUED,
            current = 0,
            total = 1,
            message = "等待同步",
        )

        val next = remoteNewsApplySyncWorkState(
            state = RemoteNewsSettingsState(
                syncingSourceId = 7,
                syncWorkBySourceId = mapOf(7L to queuedPlaceholder),
            ),
            sourceId = 7,
            workUi = null,
        )

        assertEquals(7, next.syncingSourceId)
        assertEquals(queuedPlaceholder, next.syncWorkBySourceId[7])
    }

    @Test
    fun editorAsksOnlyForTheFeedUrlAndDropsTheTokenField() {
        val screen = File("src/main/kotlin/com/dailysatori/ui/feature/settings/remotenews/RemoteNewsSettingsScreen.kt").readText()
        val viewModel = File("src/main/kotlin/com/dailysatori/ui/feature/settings/remotenews/RemoteNewsSettingsViewModel.kt").readText()

        assertTrue(screen.contains("label = { Text(\"完整 URL\") }"))
        assertTrue(screen.contains("填写 RSS 订阅地址"))
        assertFalse(screen.contains("Token（可选）"))
        assertFalse(screen.contains("PasswordVisualTransformation"))
        assertFalse(screen.contains("VisualTransformation"))
        assertFalse(screen.contains("label = { Text(\"名称\") }"))
        assertFalse(screen.contains("state.legacyApiToken"))
        assertFalse(viewModel.contains("fun updateToken"))
        assertFalse(viewModel.contains("fun updateName"))
    }

    @Test
    fun editingKeepsTheLegacyTokenAndDerivesTheNameFromTheFeedUrl() {
        val viewModel = File("src/main/kotlin/com/dailysatori/ui/feature/settings/remotenews/RemoteNewsSettingsViewModel.kt").readText()

        assertTrue(viewModel.contains("legacyApiToken = source.api_token"))
        assertTrue(viewModel.contains("sourceRepo.save(editingId, name, baseUrl, legacyApiToken, enabled)"))
        assertTrue(viewModel.contains("remoteNewsSourceName(form.name, baseUrl)"))
        assertEquals("example.com", remoteNewsSourceName("", "https://example.com/feed?token=secret"))
        assertEquals("示例来源", remoteNewsSourceName("示例来源", "https://example.com/feed"))
        assertEquals("远程新闻", remoteNewsSourceName("", ""))
    }

    @Test
    fun sourceRowsMaskTokenValuesInTheStoredUrl() {
        assertEquals(
            "https://example.com/feed?token=已脱敏&category=tech",
            remoteNewsDisplayUrl("https://example.com/feed?token=secret&category=tech"),
        )
        assertEquals("https://example.com/feed", remoteNewsDisplayUrl("https://example.com/feed"))
    }

    @Test
    fun legacyTokenStaysInsideTheSavedOrigin() {
        val saved = "https://feeds.example.com/rss?category=tech"
        val token = "saved-token"

        // Same origin, different path/query: the legacy credential still belongs to this feed.
        assertEquals(token, remoteNewsLegacyTokenFor("https://feeds.example.com/rss?category=ai", token, saved))
        assertEquals(token, remoteNewsLegacyTokenFor("https://FEEDS.example.com:443/other", token, saved))
        assertEquals(token, remoteNewsLegacyTokenFor("https://feeds.example.com:8443/rss", token, "https://feeds.example.com:8443/rss"))

        // Different host, downgraded scheme or a different port is a different origin.
        assertEquals("", remoteNewsLegacyTokenFor("https://evil.example.com/rss", token, saved))
        assertEquals("", remoteNewsLegacyTokenFor("http://feeds.example.com/rss", token, saved))
        assertEquals("", remoteNewsLegacyTokenFor("https://feeds.example.com:8443/rss", token, saved))

        // Invalid addresses and brand-new sources never inherit a credential.
        assertEquals("", remoteNewsLegacyTokenFor("not a url", token, saved))
        assertEquals("", remoteNewsLegacyTokenFor("ftp://feeds.example.com/rss", token, saved))
        assertEquals("", remoteNewsLegacyTokenFor("https://feeds.example.com/rss", token, ""))
        assertEquals("", remoteNewsLegacyTokenFor("https://feeds.example.com/rss", "  ", saved))
    }

    @Test
    fun saveAndTestBothScopeTheLegacyTokenToTheSavedUrl() {
        val viewModel = File("src/main/kotlin/com/dailysatori/ui/feature/settings/remotenews/RemoteNewsSettingsViewModel.kt").readText()

        assertTrue(
            viewModel.contains(
                "val legacyApiToken = remoteNewsLegacyTokenFor(baseUrl, form.legacyApiToken, form.savedDraft.baseUrl)",
            ),
        )
        assertTrue(
            viewModel.contains(
                "remoteNewsLegacyTokenFor(baseUrl, state.value.legacyApiToken, state.value.savedDraft.baseUrl)",
            ),
        )
    }

    private fun remoteNewsSource(id: Long): Remote_news_source =
        Remote_news_source(
            id = id,
            name = "远程新闻",
            base_url = "https://example.com/api/v1/external/top_articles_today",
            api_token = "token",
            enabled = 1,
            created_at = 0,
            updated_at = 0,
        )
}

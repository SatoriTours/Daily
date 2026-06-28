package com.dailysatori.ui.feature.settings.remotenews

import androidx.work.WorkInfo
import com.dailysatori.shared.db.Remote_news_source
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
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

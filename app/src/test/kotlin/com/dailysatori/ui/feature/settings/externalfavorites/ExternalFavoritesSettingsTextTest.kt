package com.dailysatori.ui.feature.settings.externalfavorites

import com.dailysatori.service.externalfavorites.ExternalSourceHealth
import com.dailysatori.service.externalfavorites.FavoriteSyncMode
import com.dailysatori.service.asynctask.AsyncTaskStatus
import com.dailysatori.service.asynctask.AsyncTaskType
import com.dailysatori.shared.db.Async_task
import androidx.work.WorkInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ExternalFavoritesSettingsTextTest {
    @Test
    fun settingsScreenReloadsSourcesWhenReturningFromOAuthBrowser() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsScreen.kt").readText()

        assertTrue(source.contains("Lifecycle.Event.ON_RESUME"))
        assertTrue(source.contains("viewModel.load()"))
    }

    @Test
    fun healthSubtitleDoesNotPromiseRealtimeSync() {
        val subtitle = externalFavoritePeriodicSyncSubtitle(ExternalSourceHealth.healthy)

        assertFalse(subtitle.contains("实时"))
        assertTrue(subtitle.contains("定期"))
    }

    @Test
    fun settingsRowTextDescribesLocalFavoriteSync() {
        assertEquals("外部收藏同步", externalFavoriteSettingsRowTitle())
        assertEquals("同步 X 等平台收藏到本地文章库", externalFavoriteSettingsRowSubtitle())
    }

    @Test
    fun emptyStatePromptsAddingExternalFavoriteService() {
        assertEquals("连接外部收藏", externalFavoriteEmptyStateTitle())
        assertEquals("连接 X 收藏", externalFavoriteAddServiceActionLabel())
    }

    @Test
    fun addServiceDialogPromptsForXOAuthClientId() {
        assertEquals("X OAuth Client ID", externalFavoriteXClientIdLabel())
        assertEquals("保存并打开 X 授权", externalFavoriteConnectXActionLabel())
    }

    @Test
    fun emptyStateSubtitleIncludesVisibleMessageWhenActionFails() {
        val subtitle = externalFavoriteEmptyStateSubtitle("请先填写 X OAuth Client ID")

        assertTrue(subtitle.contains("请先填写 X OAuth Client ID"))
    }

    @Test
    fun healthLabelsUseActionableChineseText() {
        assertEquals("需要授权", externalFavoriteHealthLabel(ExternalSourceHealth.needs_auth))
        assertEquals("限流中", externalFavoriteHealthLabel(ExternalSourceHealth.limited))
        assertEquals("已暂停", externalFavoriteHealthLabel(ExternalSourceHealth.paused))
        assertEquals("异常", externalFavoriteHealthLabel(ExternalSourceHealth.failing))
        assertEquals("未同步", externalFavoriteHealthLabel(ExternalSourceHealth.never_synced))
        assertEquals("正常", externalFavoriteHealthLabel(ExternalSourceHealth.healthy))
    }

    @Test
    fun reEnablePreservesActionRequiredStatus() {
        assertEquals("auth_required", externalFavoriteStatusAfterToggle("auth_required", enabled = true))
        assertEquals("auth_check_required", externalFavoriteStatusAfterToggle("auth_check_required", enabled = true))
        assertEquals("rate_limited", externalFavoriteStatusAfterToggle("rate_limited", enabled = true))
        assertEquals("failed", externalFavoriteStatusAfterToggle("failed", enabled = true))
    }

    @Test
    fun reEnableOnlyRestoresPausedSourceToIdle() {
        assertEquals("idle", externalFavoriteStatusAfterToggle("paused", enabled = true))
        assertEquals("paused", externalFavoriteStatusAfterToggle("idle", enabled = false))
    }

    @Test
    fun managementSummaryTextPrioritizesConnectionHealth() {
        assertEquals("还没有连接外部收藏来源", externalFavoriteManagementSummaryTitle(emptyList()))
        assertEquals(
            "外部收藏同步已暂停",
            externalFavoriteManagementSummaryTitle(
                listOf(
                    sourceUi(ExternalSourceHealth.paused, enabled = false),
                    sourceUi(ExternalSourceHealth.paused, enabled = false),
                ),
            ),
        )
        assertEquals(
            "2 个来源需要处理",
            externalFavoriteManagementSummaryTitle(
                listOf(
                    sourceUi(ExternalSourceHealth.healthy),
                    sourceUi(ExternalSourceHealth.needs_auth),
                    sourceUi(ExternalSourceHealth.failing),
                ),
            ),
        )
        assertEquals(
            "所有外部收藏来源同步正常",
            externalFavoriteManagementSummaryTitle(
                listOf(sourceUi(ExternalSourceHealth.healthy), sourceUi(ExternalSourceHealth.never_synced)),
            ),
        )
        assertEquals(
            "同步会先检查最新收藏，并逐步补全较早收藏。",
            externalFavoriteManagementSummarySubtitle(),
        )
    }

    @Test
    fun emptyStateGuidesFirstConnection() {
        assertEquals("连接外部收藏", externalFavoriteEmptyStateTitle())
        assertEquals("连接 X 收藏", externalFavoriteAddServiceActionLabel(hasSources = false))
        assertEquals("连接新来源", externalFavoriteAddServiceActionLabel(hasSources = true))
        assertTrue(externalFavoriteEmptyStateSubtitle().contains("当前先支持 X 收藏"))
        assertTrue(externalFavoriteEmptyStateSubtitle().contains("本地文章库"))
    }

    @Test
    fun primaryActionsFollowHealthState() {
        assertEquals("同步收藏", externalFavoritePrimaryActionLabel(ExternalSourceHealth.healthy))
        assertEquals("同步收藏", externalFavoritePrimaryActionLabel(ExternalSourceHealth.never_synced))
        assertEquals("启用同步", externalFavoritePrimaryActionLabel(ExternalSourceHealth.paused))
        assertEquals("重新连接", externalFavoritePrimaryActionLabel(ExternalSourceHealth.needs_auth))
        assertEquals("稍后自动恢复", externalFavoritePrimaryActionLabel(ExternalSourceHealth.limited))
        assertEquals("重试同步", externalFavoritePrimaryActionLabel(ExternalSourceHealth.failing))
    }

    @Test
    fun summaryMetricsMatchManagementMockup() {
        val metrics = externalFavoriteSummaryMetrics(
            listOf(
                sourceUi(ExternalSourceHealth.healthy, itemsSeen = 128, pagesSeen = 3, syncIntervalMinutes = 360),
                sourceUi(ExternalSourceHealth.needs_auth, itemsSeen = 0, pagesSeen = 0, syncIntervalMinutes = 720),
            ),
        )

        assertEquals(
            listOf(
                ExternalFavoriteSummaryMetric("2", "已连接来源"),
                ExternalFavoriteSummaryMetric("128", "上次看到收藏"),
                ExternalFavoriteSummaryMetric("6h", "定期同步间隔"),
            ),
            metrics,
        )
    }

    @Test
    fun emptySummaryMetricsExplainSupportedProvider() {
        assertEquals(
            listOf(
                ExternalFavoriteSummaryMetric("0", "已连接来源"),
                ExternalFavoriteSummaryMetric("X", "当前支持平台"),
                ExternalFavoriteSummaryMetric("12h", "默认同步间隔"),
            ),
            externalFavoriteSummaryMetrics(emptyList()),
        )
    }

    @Test
    fun sourceCardUsesProviderBadgeAndOverflowDeleteCopy() {
        assertEquals("X", externalFavoriteProviderBadge("x"))
        assertEquals("删除", externalFavoriteDeleteMenuLabel())
        assertEquals("只读", externalFavoriteReadOnlyStepLabel())
        assertTrue(externalFavoriteAddPageOrganizeNoteText().contains("本地文章库"))
        assertFalse(externalFavoriteAddPageOrganizeNoteText().contains("本地收藏"))
    }

    @Test
    fun sourceIdentityPrefersAccountName() {
        assertEquals("@jim", externalFavoriteAccountIdentity(accountName = "@jim", accountId = "123"))
        assertEquals("123", externalFavoriteAccountIdentity(accountName = "", accountId = "123"))
    }

    @Test
    fun syncActionsDisableForBlockedStates() {
        assertTrue(externalFavoriteCanRunSyncAction(ExternalSourceHealth.healthy, enabled = true))
        assertTrue(externalFavoriteCanRunSyncAction(ExternalSourceHealth.never_synced, enabled = true))
        assertTrue(externalFavoriteCanRunSyncAction(ExternalSourceHealth.failing, enabled = true))
        assertFalse(externalFavoriteCanRunSyncAction(ExternalSourceHealth.paused, enabled = false))
        assertFalse(externalFavoriteCanRunSyncAction(ExternalSourceHealth.needs_auth, enabled = true))
        assertFalse(externalFavoriteCanRunSyncAction(ExternalSourceHealth.limited, enabled = true))
    }

    @Test
    fun syncWorkStateUsesProgressCopyAndCancelAction() {
        val running = ExternalFavoriteSyncWorkUi(
            state = WorkInfo.State.RUNNING,
            pagesSeen = 2,
            maxPages = 3,
            itemsSeen = 168,
            phase = "backfill",
        )

        assertEquals("同步中", externalFavoriteEffectiveHealthLabel(ExternalSourceHealth.healthy, running))
        assertEquals("正在补全较早收藏", externalFavoriteSyncProgressTitle(running))
        assertEquals("第 2 / 3 页", externalFavoriteSyncProgressPageText(running))
        assertEquals(
            listOf(
                ExternalFavoriteProgressMetric("2 页", "本次已读取"),
                ExternalFavoriteProgressMetric("168 条", "本次看到"),
                ExternalFavoriteProgressMetric("未完成", "历史补全"),
            ),
            externalFavoriteProgressMetrics(running, historyComplete = false),
        )
        assertEquals("取消同步", externalFavoriteSyncActionLabel(ExternalSourceHealth.healthy, running))
        assertTrue(externalFavoriteSyncActionEnabled(ExternalSourceHealth.limited, enabled = true, running))
        assertEquals(
            listOf(
                ExternalFavoriteDetailLine("当前阶段", "读取 X bookmarks"),
                ExternalFavoriteDetailLine("同步策略", "每次最多 3 页 / 300 条"),
                ExternalFavoriteDetailLine("取消后", "保留已同步内容，下次继续"),
            ),
            externalFavoriteRunningDetailLines(running),
        )
    }

    @Test
    fun queuedSyncShowsWaitingCopy() {
        val queued = ExternalFavoriteSyncWorkUi(
            state = WorkInfo.State.ENQUEUED,
            pagesSeen = 0,
            maxPages = 3,
            itemsSeen = 0,
            phase = "",
        )

        assertEquals("等待同步", externalFavoriteSyncProgressTitle(queued))
        assertEquals("第 0 / 3 页", externalFavoriteSyncProgressPageText(queued))
    }

    @Test
    fun asyncTaskSyncProgressMapsToVisibleSyncWork() {
        val running = asyncTask(
            status = AsyncTaskStatus.running.name,
            progressCurrent = 2,
            progressTotal = 3,
            progressMessage = "正在补全历史收藏，已看到 168 条",
            checkpointJson = """{"phase":"backfill","pagesSeen":2,"itemsSeen":168,"historyComplete":false}""",
        )

        val work = externalFavoriteSyncWorkFromAsyncTask(running)

        assertEquals(WorkInfo.State.RUNNING, work?.state)
        assertEquals(true, work?.active)
        assertEquals("backfill", work?.phase)
        assertEquals(2, work?.pagesSeen)
        assertEquals(3, work?.maxPages)
        assertEquals(168, work?.itemsSeen)
        assertEquals(false, work?.historyComplete)
        assertEquals("正在补全较早收藏", externalFavoriteSyncProgressTitle(work!!))
    }

    @Test
    fun queuedAsyncTaskSyncShowsWaitingWork() {
        val queued = asyncTask(
            status = AsyncTaskStatus.queued.name,
            progressCurrent = 0,
            progressTotal = 3,
            progressMessage = "",
            checkpointJson = "",
        )

        val work = externalFavoriteSyncWorkFromAsyncTask(queued)

        assertEquals(WorkInfo.State.ENQUEUED, work?.state)
        assertEquals(true, work?.active)
        assertEquals("等待同步", externalFavoriteSyncProgressTitle(work!!))
        assertEquals("第 0 / 3 页", externalFavoriteSyncProgressPageText(work))
    }

    @Test
    fun manualSyncMessagesDoNotDuplicateVisibleProgress() {
        assertEquals(null, externalFavoriteSyncQueuedMessage(FavoriteSyncMode.sync))
        assertEquals(null, externalFavoriteSyncQueuedMessage(FavoriteSyncMode.history))
        assertEquals(null, externalFavoriteSyncQueuedMessage(FavoriteSyncMode.full_rescan))
        assertEquals(null, externalFavoriteSyncQueuedMessage(FavoriteSyncMode.recent))
        assertEquals("已开始重试失败项", externalFavoriteSyncQueuedMessage(FavoriteSyncMode.retry_failed))
    }

    @Test
    fun idleSourceDetailsMatchProgressMockup() {
        val source = sourceUi(
            ExternalSourceHealth.healthy,
            itemsSeen = 241,
            pagesSeen = 3,
            configJson = """{"history_complete":false}""",
        )

        assertEquals(
            listOf(
                ExternalFavoriteDetailLine("上次结果", "读取 3 页 · 看到 241 条"),
                ExternalFavoriteDetailLine("历史状态", "仍在逐步补全"),
                ExternalFavoriteDetailLine("本地收藏", "不会自动标记"),
            ),
            externalFavoriteIdleDetailLines(source),
        )
        assertEquals("@daily · 上次成功：刚刚", externalFavoriteSourceSubtitle("@daily", 1_700_000_000_000, 720, 1_700_000_030_000))
        assertEquals("@daily · 每 12 小时自动同步", externalFavoriteSourceSubtitle("@daily", null, 720, 1_700_000_030_000))
    }

    @Test
    fun deleteConfirmationExplainsLocalFavoritesAreKept() {
        assertEquals("删除外部收藏来源？", externalFavoriteDeleteDialogTitle())
        assertEquals("删除来源", externalFavoriteDeleteConfirmLabel())
        assertEquals("取消", externalFavoriteDeleteCancelLabel())
        assertTrue(externalFavoriteDeleteDialogText().contains("授权信息和同步记录"))
        assertTrue(externalFavoriteDeleteDialogText().contains("已经导入的文章不会被删除"))
    }

    @Test
    fun syncSummaryDoesNotExposeRawEpochMilliseconds() {
        assertEquals("尚未同步", externalFavoriteSyncAttemptText(null, null, nowMillis = 1_700_000_000_000))
        assertEquals(
            "上次成功：刚刚",
            externalFavoriteSyncAttemptText(
                lastAttemptAt = null,
                lastSuccessAt = 1_700_000_000_000,
                nowMillis = 1_700_000_030_000,
            ),
        )
        assertEquals(
            "上次成功：12 分钟前",
            externalFavoriteSyncAttemptText(
                lastAttemptAt = null,
                lastSuccessAt = 1_700_000_000_000,
                nowMillis = 1_700_000_720_000,
            ),
        )
        assertFalse(
            externalFavoriteSyncAttemptText(
                lastAttemptAt = null,
                lastSuccessAt = 1_700_000_000_000,
                nowMillis = 1_700_000_720_000,
            ).contains("1700000"),
        )
    }

    @Test
    fun syncCountsUseUserFacingText() {
        assertEquals(null, externalFavoriteSeenCountText(itemsSeen = 0, pagesSeen = 0))
        assertEquals("上次看到 18 条收藏", externalFavoriteSeenCountText(itemsSeen = 18, pagesSeen = 1))
        assertEquals("上次看到 18 条收藏 · 读取 3 页", externalFavoriteSeenCountText(itemsSeen = 18, pagesSeen = 3))
        assertEquals("读取 3 页", externalFavoriteSeenCountText(itemsSeen = 0, pagesSeen = 3))
    }

    @Test
    fun rateLimitTextUsesResetTimeWhenAvailable() {
        assertEquals("平台限流中，稍后自动恢复", externalFavoriteRateLimitText(null, nowMillis = 1_700_000_000_000))
        assertEquals(
            "平台限流中，预计 60 分钟后恢复",
            externalFavoriteRateLimitText(
                resetAt = 1_700_003_600_000,
                nowMillis = 1_700_000_000_000,
            ),
        )
    }

    @Test
    fun authCheckNoticeOnlyShowsForRestoredAuthState() {
        assertFalse(externalFavoriteShouldShowAuthCheckNotice(emptyList()))
        assertFalse(externalFavoriteShouldShowAuthCheckNotice(listOf(sourceUi(ExternalSourceHealth.healthy))))
        assertTrue(
            externalFavoriteShouldShowAuthCheckNotice(
                listOf(sourceUi(ExternalSourceHealth.needs_auth, status = "auth_check_required")),
            ),
        )
        assertEquals("已恢复的授权需要重新连接后才能继续同步。", externalFavoriteAuthCheckNoticeText())
    }

    @Test
    fun pendingDeleteSourceResolvesOnlyExistingSource() {
        val existingSource = sourceUi(ExternalSourceHealth.healthy, id = 42)
        val sources = listOf(existingSource)

        assertSame(existingSource, externalFavoritePendingDeleteSource(42, sources))
        assertNull(externalFavoritePendingDeleteSource(99, sources))
        assertNull(externalFavoritePendingDeleteSource(null, sources))
    }

    private fun sourceUi(
        health: ExternalSourceHealth,
        id: Long = health.name.hashCode().toLong(),
        enabled: Boolean = true,
        status: String = "idle",
        itemsSeen: Long = 0,
        pagesSeen: Long = 0,
        syncIntervalMinutes: Long = 720,
        configJson: String = "",
    ): ExternalFavoriteSourceUi =
        ExternalFavoriteSourceUi(
            source = com.dailysatori.shared.db.External_favorite_source(
                id = id,
                provider = "x",
                display_name = "X 收藏",
                account_id = "account-${health.name}",
                account_name = "",
                enabled = if (enabled) 1L else 0L,
                sync_interval_minutes = syncIntervalMinutes,
                last_sync_started_at = null,
                last_sync_completed_at = null,
                last_success_at = null,
                last_sync_window_started_at = null,
                last_items_seen_count = itemsSeen,
                last_pages_seen_count = pagesSeen,
                last_error = "",
                last_error_code = "",
                last_error_message = "",
                status = status,
                last_sync_mode = "recent",
                rate_limit_reset_at = null,
                auth_json = "",
                config_json = configJson,
                capabilities_json = "",
                created_at = 0,
                updated_at = 0,
        ),
        health = health,
    )

    private fun asyncTask(
        status: String,
        progressCurrent: Long,
        progressTotal: Long,
        progressMessage: String,
        checkpointJson: String,
    ) = Async_task(
        id = 9,
        type = AsyncTaskType.external_favorite_sync.name,
        status = status,
        payload_json = """{"sourceId":42,"mode":"sync"}""",
        checkpoint_json = checkpointJson,
        result_json = "",
        progress_current = progressCurrent,
        progress_total = progressTotal,
        progress_message = progressMessage,
        attempt_count = 0,
        max_attempts = 5,
        priority = 0,
        unique_key = "external_favorite_sync:42:sync",
        batch_id = null,
        run_after_ms = null,
        lease_owner = null,
        lease_until_ms = null,
        started_at = null,
        finished_at = null,
        last_error_code = "",
        last_error_message = "",
        created_at = 1,
        updated_at = 1,
    )
}

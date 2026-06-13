package com.dailysatori.ui.feature.settings.externalfavorites

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalFavoritesSettingsTextTest {
    @Test
    fun healthSubtitleDoesNotPromiseRealtimeSync() {
        val subtitle = externalFavoritePeriodicSyncSubtitle("healthy")

        assertFalse(subtitle.contains("实时"))
        assertTrue(subtitle.contains("定期"))
    }

    @Test
    fun settingsRowTextDescribesLocalFavoriteSync() {
        assertEquals("外部收藏同步", externalFavoriteSettingsRowTitle())
        assertEquals("同步 X 等平台收藏到本地收藏", externalFavoriteSettingsRowSubtitle())
    }

    @Test
    fun emptyStatePromptsAddingExternalFavoriteService() {
        assertEquals("连接外部收藏", externalFavoriteEmptyStateTitle())
        assertEquals("连接 X 收藏", externalFavoriteAddServiceActionLabel())
    }

    @Test
    fun addServiceDialogPromptsForXOAuthClientId() {
        assertEquals("X OAuth Client ID", externalFavoriteXClientIdLabel())
        assertEquals("保存并连接 X", externalFavoriteConnectXActionLabel())
    }

    @Test
    fun emptyStateSubtitleIncludesVisibleMessageWhenActionFails() {
        val subtitle = externalFavoriteEmptyStateSubtitle("请先配置 X OAuth Client ID")

        assertTrue(subtitle.contains("请先配置 X OAuth Client ID"))
    }

    @Test
    fun healthLabelsUseActionableChineseText() {
        assertEquals("需要授权", externalFavoriteHealthLabel("needs_auth"))
        assertEquals("限流中", externalFavoriteHealthLabel("limited"))
        assertEquals("已暂停", externalFavoriteHealthLabel("paused"))
        assertEquals("异常", externalFavoriteHealthLabel("failing"))
        assertEquals("未同步", externalFavoriteHealthLabel("never_synced"))
        assertEquals("正常", externalFavoriteHealthLabel("healthy"))
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
                listOf(sourceUi("paused", enabled = false), sourceUi("paused", enabled = false)),
            ),
        )
        assertEquals(
            "2 个来源需要处理",
            externalFavoriteManagementSummaryTitle(
                listOf(sourceUi("healthy"), sourceUi("needs_auth"), sourceUi("failing")),
            ),
        )
        assertEquals(
            "所有外部收藏来源同步正常",
            externalFavoriteManagementSummaryTitle(listOf(sourceUi("healthy"), sourceUi("never_synced"))),
        )
        assertEquals(
            "收藏会定期同步到本地收藏，可手动同步或导入历史收藏。",
            externalFavoriteManagementSummarySubtitle(),
        )
    }

    @Test
    fun emptyStateGuidesFirstConnection() {
        assertEquals("连接外部收藏", externalFavoriteEmptyStateTitle())
        assertEquals("连接 X 收藏", externalFavoriteAddServiceActionLabel(hasSources = false))
        assertEquals("连接新来源", externalFavoriteAddServiceActionLabel(hasSources = true))
        assertTrue(externalFavoriteEmptyStateSubtitle().contains("当前先支持 X 收藏"))
        assertTrue(externalFavoriteEmptyStateSubtitle().contains("本地收藏"))
    }

    @Test
    fun primaryActionsFollowHealthState() {
        assertEquals("同步", externalFavoritePrimaryActionLabel("healthy"))
        assertEquals("开始同步", externalFavoritePrimaryActionLabel("never_synced"))
        assertEquals("启用同步", externalFavoritePrimaryActionLabel("paused"))
        assertEquals("重新连接", externalFavoritePrimaryActionLabel("needs_auth"))
        assertEquals("稍后自动恢复", externalFavoritePrimaryActionLabel("limited"))
        assertEquals("重试同步", externalFavoritePrimaryActionLabel("failing"))
    }

    @Test
    fun deleteConfirmationExplainsLocalFavoritesAreKept() {
        assertEquals("删除外部收藏来源？", externalFavoriteDeleteDialogTitle())
        assertEquals("删除来源", externalFavoriteDeleteConfirmLabel())
        assertEquals("取消", externalFavoriteDeleteCancelLabel())
        assertTrue(externalFavoriteDeleteDialogText().contains("授权信息和同步记录"))
        assertTrue(externalFavoriteDeleteDialogText().contains("本地收藏的内容不会被删除"))
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
        assertFalse(externalFavoriteShouldShowAuthCheckNotice(listOf(sourceUi("healthy"))))
        assertTrue(externalFavoriteShouldShowAuthCheckNotice(listOf(sourceUi("needs_auth", status = "auth_check_required"))))
        assertEquals("已恢复的授权需要重新连接后才能继续同步。", externalFavoriteAuthCheckNoticeText())
    }

    private fun sourceUi(
        health: String,
        enabled: Boolean = true,
        status: String = "idle",
    ): ExternalFavoriteSourceUi =
        ExternalFavoriteSourceUi(
            source = com.dailysatori.shared.db.External_favorite_source(
                id = health.hashCode().toLong(),
                provider = "x",
                display_name = "X 收藏",
                account_id = "account-$health",
                account_name = "",
                enabled = if (enabled) 1L else 0L,
                sync_interval_minutes = 720,
                last_sync_started_at = null,
                last_sync_completed_at = null,
                last_success_at = null,
                last_sync_window_started_at = null,
                last_items_seen_count = 0,
                last_pages_seen_count = 0,
                last_error = "",
                last_error_code = "",
                last_error_message = "",
                status = status,
                last_sync_mode = "recent",
                rate_limit_reset_at = null,
                auth_json = "",
                config_json = "",
                capabilities_json = "",
                created_at = 0,
                updated_at = 0,
            ),
            health = health,
        )
}

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
        assertEquals("添加外部收藏服务", externalFavoriteEmptyStateTitle())
        assertEquals("添加服务", externalFavoriteAddServiceActionLabel())
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
}

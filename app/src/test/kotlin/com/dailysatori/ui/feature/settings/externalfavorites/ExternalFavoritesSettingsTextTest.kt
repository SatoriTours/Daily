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
    fun healthLabelsUseActionableChineseText() {
        assertEquals("需要授权", externalFavoriteHealthLabel("needs_auth"))
        assertEquals("限流中", externalFavoriteHealthLabel("limited"))
        assertEquals("已暂停", externalFavoriteHealthLabel("paused"))
        assertEquals("异常", externalFavoriteHealthLabel("failing"))
        assertEquals("未同步", externalFavoriteHealthLabel("never_synced"))
        assertEquals("正常", externalFavoriteHealthLabel("healthy"))
    }
}

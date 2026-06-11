package com.dailysatori.ui.feature.settings.externalfavorites

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExternalFavoritesAddPageTextTest {
    @Test
    fun addPageUsesDedicatedEditorCopy() {
        assertEquals("新增外部收藏", externalFavoriteAddPageTitle())
        assertEquals("X 收藏", externalFavoriteDefaultDisplayName())
        assertEquals("连接 X 收藏", externalFavoriteAddPageHelperTitle())
        assertEquals("保存并连接 X", externalFavoriteConnectXActionLabel())
        assertTrue(externalFavoriteAddPageHelperText().contains("浏览器"))
    }

    @Test
    fun addPageExplainsPostAuthorizationSyncWithoutPretendingToSaveIt() {
        assertEquals("授权成功后启用定期同步", externalFavoriteAddPageSyncNoteTitle())
        assertTrue(externalFavoriteAddPageSyncNoteText().contains("授权成功"))
        assertTrue(externalFavoriteAddPageSyncNoteText().contains("来源列表"))
    }
}

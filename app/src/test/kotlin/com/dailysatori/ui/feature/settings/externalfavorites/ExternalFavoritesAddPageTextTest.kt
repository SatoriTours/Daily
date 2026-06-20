package com.dailysatori.ui.feature.settings.externalfavorites

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExternalFavoritesAddPageTextTest {
    @Test
    fun addPageUsesDedicatedEditorCopy() {
        assertEquals("新增外部收藏", externalFavoriteAddPageTitle())
        assertEquals("连接 X 收藏", externalFavoriteAddPageHelperTitle())
        assertEquals("保存并打开 X 授权", externalFavoriteConnectXActionLabel())
        assertEquals("X OAuth Client ID", externalFavoriteXClientIdLabel())
        assertEquals("dailysatori://oauth/x", externalFavoriteXOAuthRedirectUri())
        assertTrue(externalFavoriteAddPageHelperText().contains("OAuth2"))
        assertTrue(externalFavoriteAddPageHelperText().contains("PKCE"))
        assertTrue(externalFavoriteAddPageHelperText().contains("回调地址"))
    }

    @Test
    fun addPageExplainsPostAuthorizationSyncWithoutPretendingToSaveIt() {
        assertEquals("授权成功后启用定期同步", externalFavoriteAddPageSyncNoteTitle())
        assertEquals(
            "授权成功后，新来源会出现在来源列表，也会作为新闻汇总页的来源筛选。",
            externalFavoriteAddPageSyncNoteText(),
        )
    }

    @Test
    fun addPageShowsRedirectUriForXDeveloperConsole() {
        assertEquals("回调地址", externalFavoriteXOAuthRedirectUriLabel())
        assertEquals("dailysatori://oauth/x", externalFavoriteXOAuthRedirectUri())
    }
}

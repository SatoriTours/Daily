package com.dailysatori.ui.feature.settings.externalfavorites

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalFavoritesAddPageSourceTest {
    private val source = java.io.File(
        "src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsScreen.kt",
    ).readText()

    @Test
    fun addFlowUsesPageInsteadOfDialog() {
        assertTrue(source.contains("ExternalFavoriteAddServicePage("))
        assertTrue(source.contains("ExternalFavoriteSourceListPage("))
        assertTrue(source.contains("FloatingActionButton(onClick = openAddPage)"))
        assertFalse(source.contains("AlertDialog("))
        assertFalse(source.contains("showAddServiceDialog"))
    }

    @Test
    fun connectKeepsAddPageOpenUntilAuthorizationUrlAndBrowserLaunchSucceed() {
        val connectBlock = source.substringAfter("onConnectX = {").substringBefore("},")

        assertTrue(connectBlock.contains("viewModel.saveXOAuthClientIdForConnect() && connectX()"))
        assertTrue(connectBlock.contains("showAddPage = false"))
        assertFalse(connectBlock.contains("showAddPage = false\n                    connectX()"))
    }

    @Test
    fun browserLaunchFailureDoesNotCloseAddPage() {
        val connectXBlock = source.substringAfter("val connectX = {").substringBefore("val openAddPage")

        assertTrue(connectXBlock.contains("context.startActivity"))
        assertTrue(connectXBlock.contains("viewModel.showMessage(\"无法打开授权页面，请确认设备已安装浏览器\")"))
        assertTrue(connectXBlock.contains(".isSuccess"))
        assertTrue(connectXBlock.contains("?: false"))
        assertFalse(connectXBlock.contains("showAddPage = false"))
    }
}

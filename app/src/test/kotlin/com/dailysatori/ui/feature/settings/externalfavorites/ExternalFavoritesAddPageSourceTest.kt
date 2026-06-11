package com.dailysatori.ui.feature.settings.externalfavorites

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalFavoritesAddPageSourceTest {
    private val source = java.io.File(
        "src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsScreen.kt",
    ).readText()

    @Test
    fun addFlowUsesDedicatedPageState() {
        assertTrue(source.contains("showAddPage"))
        assertTrue(source.contains("if (showAddPage)"))
        assertTrue(source.contains("ExternalFavoriteAddServicePage("))
        assertTrue(source.contains("ExternalFavoriteSourceListPage("))
        assertTrue(source.contains("openAddPage = openAddPage"))
    }

    @Test
    fun addPageClosesOnlyAfterClientIdIsSavedAndAuthorizationLaunches() {
        assertFalse(
            externalFavoriteShouldCloseAddPageAfterConnect(
                clientIdSaved = false,
                authorizationLaunched = false,
            ),
        )
        assertFalse(
            externalFavoriteShouldCloseAddPageAfterConnect(
                clientIdSaved = false,
                authorizationLaunched = true,
            ),
        )
        assertFalse(
            externalFavoriteShouldCloseAddPageAfterConnect(
                clientIdSaved = true,
                authorizationLaunched = false,
            ),
        )
        assertTrue(
            externalFavoriteShouldCloseAddPageAfterConnect(
                clientIdSaved = true,
                authorizationLaunched = true,
            ),
        )
    }
}

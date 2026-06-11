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

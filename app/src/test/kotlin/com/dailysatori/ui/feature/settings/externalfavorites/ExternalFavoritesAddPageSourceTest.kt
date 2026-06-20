package com.dailysatori.ui.feature.settings.externalfavorites

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalFavoritesAddPageSourceTest {
    @Test
    fun addPageRegistersBackHandlerForSystemAndGestureBack() {
        val source = File(
            "src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsScreen.kt",
        ).readText()

        assertTrue(source.contains("import androidx.activity.compose.BackHandler"))
        assertTrue(source.contains("BackHandler(enabled = showAddPage)"))
    }

    @Test
    fun overflowSyncToggleLabelMatchesSourceEnabledState() {
        assertEquals("停用同步", externalFavoriteToggleSyncMenuLabel(enabled = true))
        assertEquals("启用同步", externalFavoriteToggleSyncMenuLabel(enabled = false))
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

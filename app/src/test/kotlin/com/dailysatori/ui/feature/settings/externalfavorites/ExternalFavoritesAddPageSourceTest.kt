package com.dailysatori.ui.feature.settings.externalfavorites

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalFavoritesAddPageSourceTest {
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

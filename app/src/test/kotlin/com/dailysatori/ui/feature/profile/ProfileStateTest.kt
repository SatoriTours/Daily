package com.dailysatori.ui.feature.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ProfileStateTest {
    @Test
    fun profileContainsOnlyRealDestinations() {
        val state = ProfileUiState()

        assertEquals(
            listOf("reminders", "favorites", "external_favorites", "tasks", "settings", "privacy"),
            state.destinations.map { it.id },
        )
        assertFalse(state.destinations.any { it.id in setOf("read_later", "history") })
    }
}

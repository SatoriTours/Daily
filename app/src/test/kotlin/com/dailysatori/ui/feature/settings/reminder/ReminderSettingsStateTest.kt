package com.dailysatori.ui.feature.settings.reminder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ReminderSettingsStateTest {
    @Test
    fun settingsSummarySeparatesDefaultsFromProfileManagement() {
        val state = ReminderSettingsState.defaults()

        assertEquals("22:00–24:00 · 每小时", state.defaultRhythm.eveningSummary)
        assertFalse(state.primarySections.any { it.id == "profiles" })
    }
}

package com.dailysatori.ui.feature.settings.reminder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderSettingsStateTest {
    @Test
    fun settingsSummarySeparatesDefaultsFromProfileManagement() {
        val state = ReminderSettingsState.defaults()

        assertEquals("22:00–24:00", state.defaultRhythm.timeRange)
        assertEquals(60, state.defaultRhythm.intervalMinutes)
        assertFalse(state.primarySections.any { it.id == "profiles" })
    }

    @Test
    fun primarySettingsSectionsExposeOnlyDefaultRulesAndAdvancedAccess() {
        val ids = ReminderSettingsState.defaults().primarySections.map { it.id }

        assertEquals(
            listOf("default-rhythm", "notification-effect", "quiet-rules", "advanced"),
            ids,
        )
        assertFalse(ids.any { it == "reminders" || it == "profiles" })
    }

    @Test
    fun profileManagementBackReturnsToReminderSettings() {
        val profileManagement = ReminderSettingsNavigationState().openProfileManagement()

        assertTrue(profileManagement.managingProfiles)
        assertFalse(profileManagement.back().managingProfiles)
    }
}

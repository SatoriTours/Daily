package com.dailysatori.ui.feature.reminder

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderUiSourceTest {
    @Test
    fun reminderEditorUsesBatchPreviewAndBatchActions() {
        val editor = source("ui/feature/reminder/ReminderEditScreen.kt")
        val preview = source("ui/feature/reminder/ReminderBatchPreview.kt")

        assertTrue(editor.contains("ReminderBatchPreview("))
        assertTrue(preview.contains("onToggleItem"))
        assertTrue(preview.contains("onRemoveItem"))
        assertTrue(preview.contains("onUpdateItem"))
        assertTrue(preview.contains("selectedCount"))
    }

    @Test
    fun batchReminderResourcesStayInLocaleParity() {
        val zh = resourceNames("src/main/res/values/strings.xml").filter { it.startsWith("reminder_batch_") }.toSet()
        val en = resourceNames("src/main/res/values-en/strings.xml").filter { it.startsWith("reminder_batch_") }.toSet()

        assertEquals(zh, en)
        assertTrue("reminder_batch_save_selected" in zh)
    }

    @Test
    fun denseControlsScrollAndSettingsContentRemainsVerticallyReachable() {
        val draft = source("ui/feature/reminder/ReminderDraftCard.kt")
        val list = source("ui/feature/reminder/ReminderListScreen.kt")
        val settings = source("ui/feature/settings/reminder/ReminderSettingsScreen.kt")

        assertTrue(draft.contains("horizontalScroll(rememberScrollState())"))
        assertTrue(settings.contains("verticalScroll(rememberScrollState())"))
        assertFalse(settings.contains("Modifier.weight(1f)"))
        assertTrue(list.contains("LazyColumn"))
        assertTrue(list.contains("maxLines = 1"))
        assertTrue(list.contains("TextOverflow.Ellipsis"))
    }

    @Test
    fun deliveryAccessRefreshesOnlyWhenSettingsPageResumes() {
        val settings = source("ui/feature/settings/reminder/ReminderSettingsScreen.kt")

        assertTrue(settings.contains("Lifecycle.Event.ON_RESUME"))
        assertTrue(settings.contains("viewModel.refreshDeliveryAccess()"))
    }

    @Test
    fun reminderSourcesHaveNoHardcodedCjkAndLocaleResourcesStayInParity() {
        val sources = listOf(
            source("ui/feature/reminder/ReminderDraftCard.kt"),
            source("ui/feature/reminder/ReminderListScreen.kt"),
            source("ui/feature/reminder/ReminderViewModel.kt"),
            source("ui/feature/settings/reminder/ReminderSettingsScreen.kt"),
            source("ui/feature/settings/reminder/ReminderSettingsViewModel.kt"),
        )
        assertTrue(sources.all { !Regex("[\\u3400-\\u9fff]").containsMatchIn(it) })
        assertTrue(sources.joinToString("\n").contains("stringResource("))
        val settingsRow = source("ui/feature/settings/SettingsScreen.kt")
            .lineSequence()
            .first { it.contains("SettingsPage.REMINDERS") && it.contains("SettingsRow") }
        assertTrue(settingsRow.contains("R.string.reminder_settings_row_title"))
        assertTrue(settingsRow.contains("R.string.reminder_settings_row_subtitle"))

        val zh = resourceNames("src/main/res/values/strings.xml")
        val en = resourceNames("src/main/res/values-en/strings.xml")
        assertEquals(zh.filter { it.startsWith("reminder_") }.toSet(), en.filter { it.startsWith("reminder_") }.toSet())
    }

    private fun source(relative: String) = File("src/main/kotlin/com/dailysatori/$relative").readText()

    private fun resourceNames(path: String): List<String> = Regex("name=\"([^\"]+)\"")
        .findAll(File(path).readText())
        .map { it.groupValues[1] }
        .toList()
}

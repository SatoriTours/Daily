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
    fun recoveredReadyBatchOpensByBatchIdPostsOnceAndConfirmsOneStableReminder() {
        val handler = source("core/task/ReminderAiParseTaskHandler.kt")
        val navigation = source("core/navigation/NavHost.kt")
        val batchViewModel = source("ui/feature/reminder/ReminderAiBatchViewModel.kt")

        assertTrue(handler.contains("claimTerminalNotification(batchId)"))
        assertTrue(handler.contains("notifier.notifyReady(batchId)"))
        assertTrue(navigation.contains("composable<ReminderAiBatchRoute>"))
        assertTrue(navigation.contains("batchId = route.batchId"))

        val confirmation = batchViewModel.substringAfter("fun confirmSelected()")
            .substringBefore("fun retryBatch()")
        assertTrue(confirmation.contains("val reminder = reminderRepository.get(id)"))
        assertTrue(confirmation.contains("?: reminderRepository.createConfirmed"))
        assertTrue(confirmation.contains("batchRepository.markDraftConfirmed(batchId, id.sourceIndex(), reminder.id)"))
        assertTrue(confirmation.contains("coordinator.recompute(reminder.id)"))
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
    fun reminderEditorUsesOneConsistentControlLanguage() {
        val editor = source("ui/feature/reminder/ReminderEditScreen.kt")
        val draft = source("ui/feature/reminder/ReminderDraftCard.kt")
        val preview = source("ui/feature/reminder/ReminderBatchPreview.kt")

        assertTrue(editor.contains("ReminderSettingRow("))
        assertTrue(draft.contains("ReminderSettingRow("))
        assertTrue(draft.contains("showAdvanced"))
        assertTrue(draft.contains("AnimatedVisibility(visible = showAdvanced)"))
        assertFalse(editor.contains("TextButton(onClick = { picker = EditorPicker.START"))
        assertFalse(draft.contains("TextButton(onClick = { picker = DraftPicker.START_DATE"))
        assertFalse(Regex("(?m)^\\s+Button\\(onClick = \\{ onConfirmItem").containsMatchIn(preview))
        assertTrue(preview.contains("TextButton(onClick = { onConfirmItem"))
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

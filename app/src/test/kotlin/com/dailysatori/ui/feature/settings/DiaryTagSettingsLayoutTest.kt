package com.dailysatori.ui.feature.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiaryTagSettingsLayoutTest {
    private val source = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryTagSettingsScreen.kt").readText()

    @Test fun catalogComesBeforeMaintenanceActions() {
        assertTrue(source.contains("settings_design.tag_catalog"))
        assertTrue(source.indexOf("settings_design.tag_catalog") < source.indexOf("diary_tags.fill_missing"))
    }

    @Test fun pageScrollsFullyInsteadOfAnInnerCappedBox() {
        assertFalse(source.contains("heightIn(max = Spacing.xxl"))
        assertTrue(source.contains("verticalScroll(rememberScrollState())"))
    }

    @Test fun contentHeightIsOwnedByEachHost() {
        assertTrue(source.contains("DiaryTagSettingsContent(viewModel, modifier.fillMaxSize())"))
        assertFalse(source.contains("modifier.fillMaxSize().verticalScroll(rememberScrollState())" +
            ".padding(Spacing.m)"))
        val diary = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryScreen.kt").readText()
        val sheet = diary.substringAfter("if (showTagSettings)").substringBefore("if (showEditor)")
        assertTrue(sheet.contains("screenHeightDp"))
        assertTrue(sheet.contains("heightIn(max ="))
        assertTrue(sheet.contains("DiaryTagSettingsContent(tagViewModel"))
        assertFalse(sheet.contains("Modifier.fillMaxSize"))
    }

    @Test fun keepsMergeFillRetryAndUndoPaths() {
        listOf("confirmFill", "fillMissing", "retryFailed", "suggestMerges",
            "undoMerge", "diary_tags.merge_confirm", "diary_tags.synonyms_hint").forEach {
            assertTrue(source.contains(it), "missing $it")
        }
    }
}

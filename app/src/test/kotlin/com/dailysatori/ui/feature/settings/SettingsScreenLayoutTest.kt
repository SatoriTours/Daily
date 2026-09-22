package com.dailysatori.ui.feature.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsScreenLayoutTest {
    @Test
    fun backupRestoreReturnsOneLevelAndBothBackActionsShareTheSameHandler() {
        assertEquals(SettingsPage.BACKUP_SETTINGS, SettingsPage.BACKUP_RESTORE.parent())
        SettingsPage.entries.filter { it != SettingsPage.BACKUP_RESTORE }.forEach {
            assertEquals(SettingsPage.MAIN, it.parent())
        }
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt").readText()
        assertTrue(source.contains("BackHandler(enabled = currentPage != SettingsPage.MAIN, onBack = childBack)"))
        assertTrue(source.contains("BackupRestoreScreen(onBack = childBack)"))
        val navigation = File("src/main/kotlin/com/dailysatori/core/navigation/NavHost.kt").readText()
        val settings = navigation.substringAfter("SettingsScreen(\n                viewModel = settingsViewModel,").substringBefore(")")
        assertTrue(settings.contains("onBack = { navController.popBackStack("))
    }

    @Test
    fun sourceAndTaskManagementAreNotDuplicatedInGeneralSettings() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt").readText()
        listOf("RemoteNewsSettingsScreen", "ExternalFavoritesSettingsScreen", "TaskCenterScreen").forEach {
            assertFalse(source.contains(it))
        }
    }

    @Test
    fun settingsMainPageUsesSharedScaffoldTopBar() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt").readText()
        val settingsMainPage = source.substringAfter("private fun SettingsMainPage(")
            .substringBefore("@Composable\nprivate fun AboutDialog")

        assertTrue(settingsMainPage.contains("AppScaffold("))
        assertFalse(Regex("[^A-Za-z]Scaffold\\(").containsMatchIn(settingsMainPage))
        assertFalse(settingsMainPage.contains("AppTopBar("))
        assertFalse(settingsMainPage.contains("snackbarHost ="))
    }

    @Test
    fun settingsMainPageHandlesSystemBackWhenCallbackExists() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt").readText()
        val settingsScreen = source.substringAfter("fun SettingsScreen(")
            .substringBefore("private fun SettingsMainPage(")

        assertTrue(settingsScreen.contains("BackHandler(enabled = currentPage == SettingsPage.MAIN && rootBack != null)"))
        assertTrue(settingsScreen.contains("rootBack?.invoke()"))
    }
}

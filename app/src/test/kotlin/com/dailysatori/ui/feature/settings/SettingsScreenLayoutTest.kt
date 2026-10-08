package com.dailysatori.ui.feature.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsScreenLayoutTest {
    @Test
    fun allSettingsTabsReturnDirectlyToTheHomePage() {
        SettingsPage.entries.forEach {
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
    fun authorizationEntriesOpenThePermissionPageAndTheDashboardIsRetired() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt").readText()
        assertTrue(source.contains("SettingsPage.PHONE_ASSISTANT, SettingsPage.AUTHORIZATION ->"))
        assertTrue(source.contains("AuthorizationScreen(onBack = childBack, initialSection = section)"))
        val catalog = File("src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsCatalog.kt").readText()
        assertTrue(catalog.contains("entry(\"daily\", \"auth\", SettingsPage.AUTHORIZATION"))
        assertFalse(catalog.contains("SettingsPage.PHONE_ASSISTANT"))
        val ledger = File("src/main/kotlin/com/dailysatori/ui/feature/ledger/LedgerScreen.kt").readText()
        assertTrue(ledger.contains("fun LedgerScreen("))
        val myspace = File("src/main/kotlin/com/dailysatori/ui/feature/myspace/MySpaceScreen.kt").readText()
        assertTrue(myspace.contains("onLedger"))
    }

    @Test
    fun settingsDirectlyOwnSourceAndPrivacyConfigurationButNotTasks() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt").readText()
        listOf("RemoteNewsSettingsScreen", "ExternalFavoritesSettingsScreen", "DataPrivacyScreen").forEach {
            assertTrue(source.contains("$it(onBack = childBack)"), "$it must return directly to settings")
        }
        assertFalse(source.contains("TaskCenterScreen"))
        val navigation = File("src/main/kotlin/com/dailysatori/core/navigation/NavHost.kt").readText()
        assertTrue(navigation.contains("onProfileClick = { navController.navigate(SettingsRoute) }"))
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

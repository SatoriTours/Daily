package com.dailysatori.ui.feature.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsScreenLayoutTest {
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

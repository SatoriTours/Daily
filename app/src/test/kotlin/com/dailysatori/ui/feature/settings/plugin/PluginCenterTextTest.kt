package com.dailysatori.ui.feature.settings.plugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluginCenterTextTest {
    @Test
    fun pluginServerConfigUsesSharedTestAndSaveFlow() {
        assertEquals("插件服务器", pluginServerConfigTitle())
        assertEquals("请输入插件服务器地址", pluginServerValidationMessage(""))
        assertEquals(null, pluginServerValidationMessage("https://plugins.example.com"))

        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/settings/plugin/PluginCenterScreen.kt").readText()
        assertTrue(source.contains("SettingsEditorBottomBar("))
        assertTrue(source.contains("PluginServerEditScreen("))
    }

    @Test
    fun pluginServerQualityFixesArePresent() {
        val viewModelSource = java.io.File(
            "src/main/kotlin/com/dailysatori/ui/feature/settings/plugin/PluginCenterViewModel.kt",
        ).readText()
        val serviceSource = java.io.File(
            "../shared/src/commonMain/kotlin/com/dailysatori/service/plugin/PluginService.kt",
        ).readText()

        assertTrue(viewModelSource.contains("private var testJob"))
        assertTrue(viewModelSource.contains("private var testRequestId"))
        assertTrue(viewModelSource.contains("testJob?.cancel()"))
        assertTrue(viewModelSource.contains("isTesting = false, testMessage = null, testSucceeded = null, error = null"))
        assertTrue(viewModelSource.contains("插件更新失败：\$fileName"))
        assertTrue(viewModelSource.contains("private fun loadPluginState()"))
        assertTrue(serviceSource.contains("response.status.isSuccess()"))
        assertTrue(serviceSource.contains("Failed to update \$fileName with status"))
    }

    @Test
    fun pluginServerEditorClearsTestStateOnEnterAndBack() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/settings/plugin/PluginCenterScreen.kt").readText()

        assertTrue(source.contains("LaunchedEffect(editingServer)"))
        assertTrue(source.contains("if (editingServer) viewModel.clearTestMessage()"))
        assertTrue(source.contains("editingServer = false; viewModel.clearTestMessage(); viewModel.loadPlugins()"))
    }
}

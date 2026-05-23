package com.dailysatori.ui.feature.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsSecurityTest {
    @Test
    fun webServerTokenIsNeverHardcodedToDaily() {
        val app = File("src/main/kotlin/com/dailysatori/DailySatoriApplication.kt").readText()
        val viewModel = File("src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsViewModel.kt").readText()

        assertFalse(app.contains("web_server_token\", \"daily"))
        assertFalse(viewModel.contains("web_server_token\", \"daily"))
        assertFalse(viewModel.contains("return \"daily\""))
    }

    @Test
    fun webServerTokenUsesSecureRandomAndAtLeastThirtyTwoBytes() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsViewModel.kt").readText()
        val generateToken = source.substringAfter("private fun generateToken()")
            .substringBefore("private fun getDeviceIp()")

        assertTrue(source.contains("SecureRandom"))
        assertTrue(generateToken.contains("ByteArray(32)"))
        assertTrue(generateToken.contains("Base64.URL_SAFE"))
        assertFalse(generateToken.contains("chars.random()"))
    }
}

package com.dailysatori.ui.feature.settings.weread

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeReadSettingsTextTest {
    @Test
    fun masksSavedApiKeyInSubtitle() {
        assertEquals("未配置", weReadApiKeyStatus(""))
        assertEquals("已配置", weReadApiKeyStatus("abc"))
        assertEquals("已配置：wrk-****cdef", weReadApiKeyStatus("wrk-12345678abcdef"))
    }

    @Test
    fun exposesRequiredLabels() {
        assertEquals("微信读书", weReadSettingsTitle())
        assertEquals("保存", weReadSaveButtonText(false))
        assertEquals("保存中...", weReadSaveButtonText(true))
        assertEquals("清空", weReadClearButtonText())
        assertEquals("微信读书 API Key 已保存", weReadSavedMessage())
        assertEquals("微信读书 API Key 已清空", weReadClearedMessage())
    }

    @Test
    fun storesWeReadApiKeyEncrypted() {
        val viewModel = File("src/main/kotlin/com/dailysatori/ui/feature/settings/weread/WeReadSettingsViewModel.kt").readText()
        val service = File("../shared/src/commonMain/kotlin/com/dailysatori/service/book/WeReadSkillService.kt").readText()

        assertTrue(viewModel.contains("SecretCipher"))
        assertTrue(viewModel.contains("secretCipher.encrypt(key)"))
        assertTrue(viewModel.contains("secretCipher::decrypt"))
        assertTrue(service.contains("SecretCipher"))
        assertTrue(service.contains("secretCipher::decrypt"))
    }
}

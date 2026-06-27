package com.dailysatori.service.externalfavorites

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ExternalFavoriteAiPromptSourceTest {
    @Test
    fun promptRequiresDirectContentVoiceInsteadOfThirdPersonArticleSummary() {
        val source = File("src/commonMain/kotlin/com/dailysatori/service/externalfavorites/ExternalFavoriteAiOrganizer.kt").readText()

        assertTrue(source.contains("直接输出内容本身"))
        assertTrue(source.contains("不要用第三方视角"))
        assertTrue(source.contains("禁止使用“本文"))
        assertTrue(source.contains("不要写“谁分享了"))
    }
}

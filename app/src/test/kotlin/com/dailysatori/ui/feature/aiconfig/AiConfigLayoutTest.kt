package com.dailysatori.ui.feature.aiconfig

import kotlin.test.Test
import kotlin.test.assertEquals

class AiConfigLayoutTest {
    @Test
    fun deleteActionUsesCompactIconSizing() {
        assertEquals(32, aiConfigDeleteActionSizeDp)
        assertEquals(18, aiConfigDeleteIconSizeDp)
    }

    @Test
    fun selectedAndDestructiveColorsStaySubtle() {
        assertEquals(0.28f, aiConfigDefaultCardBorderAlpha)
        assertEquals(0.82f, aiConfigDefaultIconAlpha)
        assertEquals(0.62f, aiConfigDeleteIconAlpha)
    }

    @Test
    fun editorUsesSharedBottomTestAndSaveActions() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigEditScreen.kt").readText()

        assertEquals(true, source.contains("bottomBar ="))
        assertEquals(true, source.contains("SettingsEditorBottomBar("))
        assertEquals(true, source.contains("SettingsEditorMessage("))
    }

    @Test
    fun editorDelegatesStatefulIoWorkToViewModel() {
        val screenSource = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigEditScreen.kt").readText()
        val viewModelSource = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigEditViewModel.kt").readText()

        assertEquals(true, screenSource.contains("AiConfigEditViewModel"))
        assertEquals(false, screenSource.contains("KoinPlatform.getKoin()"))
        assertEquals(true, viewModelSource.contains("val token = snapshot.apiToken"))
        assertEquals(true, viewModelSource.contains("val defaultValue = snapshot.isDefault"))
        assertEquals(false, screenSource.contains("val token = apiToken"))
    }
}

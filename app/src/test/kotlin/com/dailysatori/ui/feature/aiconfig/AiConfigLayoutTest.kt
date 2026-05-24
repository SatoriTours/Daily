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
    fun editorSnapshotsFormStateBeforeIoWork() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigEditScreen.kt").readText()

        assertEquals(true, source.contains("val token = apiToken"))
        assertEquals(true, source.contains("val defaultValue = isDefault"))
        assertEquals(false, source.contains("apiToken = apiToken"))
        assertEquals(false, source.contains("if (isDefault) 1L else 0L"))
    }
}

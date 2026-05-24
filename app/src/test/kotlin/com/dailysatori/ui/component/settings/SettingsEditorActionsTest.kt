package com.dailysatori.ui.component.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsEditorActionsTest {
    @Test
    fun sharedSettingsEditorActionsUseTestAndSave() {
        assertEquals("测试连接", settingsEditorTestActionText(isTesting = false))
        assertEquals("测试中...", settingsEditorTestActionText(isTesting = true))
        assertEquals("保存", settingsEditorSaveActionText(isSaving = false))
        assertEquals("保存中...", settingsEditorSaveActionText(isSaving = true))
        assertEquals(true, settingsEditorActionsUseTestAndSave())
    }
}

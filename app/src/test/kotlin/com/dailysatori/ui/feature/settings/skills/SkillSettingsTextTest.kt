package com.dailysatori.ui.feature.settings.skills

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkillSettingsTextTest {
    @Test
    fun exposesSkillSettingsLabels() {
        assertEquals("Skills", skillSettingsScreenTitle())
        assertEquals("添加 Skill", skillAddButtonText())
        assertEquals("保存", skillSaveButtonText(false))
        assertEquals("保存中...", skillSaveButtonText(true))
        assertEquals("内置 Skill 不能删除", skillBuiltinDeleteBlockedMessage())
    }

    @Test
    fun validatesSkillEditInput() {
        assertEquals("请输入 Skill 名称", validateSkillInput("", "https://example.com", "{}"))
        assertEquals("请输入 Gateway URL", validateSkillInput("测试", "", "{}"))
        assertEquals("Tool Schema 必须是 JSON 对象或数组", validateSkillInput("测试", "https://example.com", "not-json"))
        assertEquals(null, validateSkillInput("测试", "https://example.com", ""))
        assertEquals(null, validateSkillInput("测试", "https://example.com", "{}"))
        assertEquals(null, validateSkillInput("测试", "https://example.com", "[]"))
    }

    @Test
    fun builtInFieldEditabilityIsRestricted() {
        assertFalse(skillCoreFieldsEditable(builtin = 1L))
        assertTrue(skillCoreFieldsEditable(builtin = 0L))
    }
}

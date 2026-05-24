package com.dailysatori.service.skill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkillConfigModelsTest {
    @Test
    fun exposesWeReadBuiltInDefaults() {
        assertEquals("weread", BuiltInSkillTemplates.weRead)
        assertEquals("微信读书", builtInWeReadSkillName())
        assertEquals("https://i.weread.qq.com/api/agent/gateway", builtInWeReadGatewayUrl())
        assertEquals("1.0.3", builtInWeReadSkillVersion())
    }

    @Test
    fun tokenStatusAndDeleteRulesAreStable() {
        assertEquals("缺少 Token", skillTokenStatus(""))
        assertEquals("已配置 Token", skillTokenStatus("abc12345"))
        assertFalse(canDeleteSkill(builtin = 1L))
        assertTrue(canDeleteSkill(builtin = 0L))
    }

    @Test
    fun uiStatusLabelsAreStable() {
        assertEquals("Skills", skillSettingsTitle())
        assertEquals("添加 Skill", skillAddActionText())
        assertEquals("已启用", skillEnabledStatus(1L))
        assertEquals("未启用", skillEnabledStatus(0L))
        assertEquals("内置", skillBuiltinBadge(1L))
        assertEquals("自定义", skillBuiltinBadge(0L))
    }
}

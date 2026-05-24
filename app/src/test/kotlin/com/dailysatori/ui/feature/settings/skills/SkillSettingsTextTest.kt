package com.dailysatori.ui.feature.settings.skills

import com.dailysatori.data.repository.SkillConfigDataSource
import com.dailysatori.shared.db.Skill_config
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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

    @Test
    fun builtInUpdatePreservesProtectedFields() {
        val existing = skillConfig(builtin = 1L)
        val input = editInput(
            name = "改名",
            description = "改描述",
            gatewayUrl = "https://changed.example.com",
            apiToken = "new-token",
            skillVersion = "9.9.9",
            enabled = true,
            provider = "changed-provider",
            templateId = "changed-template",
            toolSchemaJson = "[{\"name\":\"changed\"}]",
        )

        val protected = skillUpdateInputForExisting(input, existing)

        assertEquals(existing.name, protected.name)
        assertEquals(existing.description, protected.description)
        assertEquals(existing.gateway_url, protected.gatewayUrl)
        assertEquals("new-token", protected.apiToken)
        assertEquals(existing.skill_version, protected.skillVersion)
        assertEquals(true, protected.enabled)
        assertEquals(existing.provider, protected.provider)
        assertEquals(existing.template_id, protected.templateId)
        assertEquals(existing.tool_schema_json, protected.toolSchemaJson)
    }

    @Test
    fun customUpdateAllowsAllFields() {
        val input = editInput(
            name = "改名",
            description = "改描述",
            gatewayUrl = "https://changed.example.com",
            apiToken = "new-token",
            skillVersion = "9.9.9",
            enabled = true,
            provider = "changed-provider",
            templateId = "changed-template",
            toolSchemaJson = "[{\"name\":\"changed\"}]",
        )

        assertEquals(input, skillUpdateInputForExisting(input, skillConfig(builtin = 0L)))
    }

    @Test
    fun saveFailureMessageIsVisible() {
        assertEquals("保存 Skill 失败", skillSaveFailureMessage(null))
        assertEquals("保存 Skill 失败：database locked", skillSaveFailureMessage(IllegalStateException("database locked")))
    }

    @Test
    fun saveFailureResetsSavingAndShowsError() = runBlocking {
        val viewModel = SkillSettingsViewModel(FailingSkillRepository())

        viewModel.save(editInput(id = null))

        withTimeout(2_000) {
            while (viewModel.state.value.error == null) delay(10)
        }
        val state = viewModel.state.value
        assertFalse(state.isSaving)
        assertNotNull(state.error)
        assertTrue(state.error.startsWith("保存 Skill 失败"))
    }

    private fun editInput(
        id: Long? = 1L,
        name: String = "Skill",
        description: String = "Description",
        gatewayUrl: String = "https://example.com",
        apiToken: String = "token",
        skillVersion: String = "1.0.0",
        enabled: Boolean = false,
        provider: String = "provider",
        templateId: String = "template",
        toolSchemaJson: String = "{}",
    ) = SkillEditInput(
        id = id,
        name = name,
        description = description,
        gatewayUrl = gatewayUrl,
        apiToken = apiToken,
        skillVersion = skillVersion,
        enabled = enabled,
        provider = provider,
        templateId = templateId,
        toolSchemaJson = toolSchemaJson,
    )

    private fun skillConfig(builtin: Long) = Skill_config(
        id = 1L,
        name = "微信读书",
        description = "原描述",
        gateway_url = "https://i.weread.qq.com/api/agent/gateway",
        api_token = "old-token",
        skill_version = "1.0.3",
        enabled = 0L,
        builtin = builtin,
        provider = "weread",
        template_id = "weread",
        tool_schema_json = "{}",
        created_at = 100L,
        updated_at = 200L,
    )

    private class FailingSkillRepository : SkillConfigDataSource {
        override fun getAll(): Flow<List<Skill_config>> = emptyFlow()

        override fun getById(id: Long): Skill_config? = null

        override fun insertSkill(
            name: String,
            description: String,
            gatewayUrl: String,
            apiToken: String,
            skillVersion: String,
            enabled: Long,
            builtin: Long,
            provider: String,
            templateId: String,
            toolSchemaJson: String,
        ) {
            throw IllegalStateException("database locked")
        }

        override fun updateSkill(
            id: Long,
            name: String,
            description: String,
            gatewayUrl: String,
            apiToken: String,
            skillVersion: String,
            enabled: Long,
            provider: String,
            templateId: String,
            toolSchemaJson: String,
        ) = Unit

        override fun deleteSkill(id: Long) = Unit

        override fun ensureBuiltInWeRead() = Unit
    }
}

package com.dailysatori.ui.feature.settings.skills

import com.dailysatori.data.repository.SkillConfigDataSource
import com.dailysatori.service.skill.BuiltInSkillTemplates
import com.dailysatori.service.skill.SkillConnectionTestRequest
import com.dailysatori.service.skill.SkillConnectionTestResult
import com.dailysatori.service.skill.SkillConnectionTester
import com.dailysatori.shared.db.Skill_config
import kotlinx.coroutines.CompletableDeferred
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
        assertEquals("测试 Skill", skillTestButtonText(false))
        assertEquals("测试中...", skillTestButtonText(true))
        assertEquals("内置 Skill 不能删除", skillBuiltinDeleteBlockedMessage())
    }

    @Test
    fun skillsSettingsRowUsesGenericSkillsText() {
        assertEquals("Skills", skillSettingsRowTitle())
        assertEquals("管理 Agent 可调用的外部 Skills", skillSettingsRowSubtitle())
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
    fun builtInWeReadTokenSaveControlsEnabledValue() {
        val existing = skillConfig(builtin = 1L, templateId = BuiltInSkillTemplates.weRead)

        assertEquals(1L, skillEnabledValueForSave(editInput(apiToken = " new-token ", enabled = false), existing))
        assertEquals(0L, skillEnabledValueForSave(editInput(apiToken = " ", enabled = true), existing))
    }

    @Test
    fun customSkillSaveKeepsExplicitEnabledValue() {
        val existing = skillConfig(builtin = 0L, templateId = "custom")

        assertEquals(0L, skillEnabledValueForSave(editInput(apiToken = "token", enabled = false), existing))
        assertEquals(1L, skillEnabledValueForSave(editInput(apiToken = "", enabled = true), existing))
    }

    @Test
    fun editorClosesOnlyAfterSuccessfulSaveCompletes() {
        assertFalse(skillShouldCloseEditorAfterSave(message = null, isSaving = false))
        assertFalse(skillShouldCloseEditorAfterSave(message = skillSavedMessage(), isSaving = true))
        assertFalse(skillShouldCloseEditorAfterSave(message = skillSaveFailureMessage(null), isSaving = false))
        assertTrue(skillShouldCloseEditorAfterSave(message = skillSavedMessage(), isSaving = false))
    }

    @Test
    fun skillEditorUsesBottomTestAndSaveActions() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/settings/skills/SkillSettingsScreen.kt").readText()

        assertTrue(source.contains("bottomBar ="))
        assertTrue(source.contains("SettingsEditorBottomBar("))
        assertTrue(source.contains("SettingsEditorMessage("))
        assertTrue(source.contains("onFieldsChanged = viewModel::clearTestMessage"))
    }

    @Test
    fun clearTestMessageClearsStaleSkillTestResult() = runBlocking {
        val tester = SuspendedSkillConnectionTester(SkillConnectionTestResult.Success("真实连接成功"))
        val viewModel = SkillSettingsViewModel(
            SuccessfulSkillRepository(),
            tester,
        )

        viewModel.testSkill(editInput(id = 1L, templateId = "custom", apiToken = ""))

        withTimeout(2_000) {
            tester.started.await()
        }
        viewModel.clearTestMessage()
        tester.release.complete(Unit)
        delay(100)

        assertFalse(viewModel.state.value.isTesting)
        assertEquals(null, viewModel.state.value.testMessage)
        assertEquals(null, viewModel.state.value.error)
    }

    @Test
    fun saveFailureMessageIsVisible() {
        assertEquals("保存 Skill 失败", skillSaveFailureMessage(null))
        assertEquals("保存 Skill 失败：database locked", skillSaveFailureMessage(IllegalStateException("database locked")))
    }

    @Test
    fun validatesSkillTestInput() {
        assertEquals("请输入 Gateway URL", validateSkillTestInput(editInput(gatewayUrl = "")))
        assertEquals(
            "请输入微信读书 Token",
            validateSkillTestInput(editInput(templateId = BuiltInSkillTemplates.weRead, apiToken = "")),
        )
        assertEquals(null, validateSkillTestInput(editInput(templateId = "custom", apiToken = "")))
    }

    @Test
    fun saveFailureResetsSavingAndShowsError() = runBlocking {
        val viewModel = SkillSettingsViewModel(FailingSkillRepository(), RecordingSkillConnectionTester(SkillConnectionTestResult.Success("ok")))

        viewModel.save(editInput(id = null))

        withTimeout(2_000) {
            while (viewModel.state.value.error == null) delay(10)
        }
        val state = viewModel.state.value
        assertFalse(state.isSaving)
        assertNotNull(state.error)
        assertTrue(state.error.startsWith("保存 Skill 失败"))
    }

    @Test
    fun consumeMessageClearsSuccessfulSaveSignal() = runBlocking {
        val viewModel = SkillSettingsViewModel(SuccessfulSkillRepository(), RecordingSkillConnectionTester(SkillConnectionTestResult.Success("ok")))

        viewModel.save(editInput(id = null))

        withTimeout(2_000) {
            while (viewModel.state.value.message != skillSavedMessage()) delay(10)
        }
        viewModel.consumeMessage()

        assertEquals(null, viewModel.state.value.message)
    }

    @Test
    fun testSkillUpdatesTestStateWithoutSaving() = runBlocking {
        val repository = CountingSkillRepository()
        val tester = RecordingSkillConnectionTester(SkillConnectionTestResult.Success("真实连接成功"))
        val viewModel = SkillSettingsViewModel(repository, tester)

        viewModel.testSkill(editInput(id = 1L, templateId = "custom", apiToken = ""))

        withTimeout(2_000) {
            while (viewModel.state.value.testMessage == null) delay(10)
        }
        assertFalse(viewModel.state.value.isTesting)
        assertEquals("真实连接成功", viewModel.state.value.testMessage)
        assertEquals(1, tester.requests.size)
        assertEquals("https://example.com", tester.requests.single().gatewayUrl)
        assertEquals(0, repository.saveCount)
    }

    @Test
    fun testSkillShowsRealConnectionFailure() = runBlocking {
        val tester = RecordingSkillConnectionTester(SkillConnectionTestResult.Failure("连接失败：401"))
        val viewModel = SkillSettingsViewModel(SuccessfulSkillRepository(), tester)

        viewModel.testSkill(editInput(id = 1L, templateId = "custom", apiToken = ""))

        withTimeout(2_000) {
            while (viewModel.state.value.testMessage == null) delay(10)
        }
        assertEquals("连接失败：401", viewModel.state.value.testMessage)
        assertEquals(1, tester.requests.size)
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

    private fun skillConfig(
        builtin: Long,
        templateId: String = "weread",
    ) = Skill_config(
        id = 1L,
        name = "微信读书",
        description = "原描述",
        gateway_url = "https://i.weread.qq.com/api/agent/gateway",
        api_token = "old-token",
        skill_version = "1.0.3",
        enabled = 0L,
        builtin = builtin,
        provider = "weread",
        template_id = templateId,
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

    private open class SuccessfulSkillRepository : SkillConfigDataSource {
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
        ) = Unit

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

    private class CountingSkillRepository : SuccessfulSkillRepository() {
        var saveCount = 0

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
            saveCount++
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
        ) {
            saveCount++
        }
    }

    private class RecordingSkillConnectionTester(
        private val result: SkillConnectionTestResult,
    ) : SkillConnectionTester {
        val requests = mutableListOf<SkillConnectionTestRequest>()

        override suspend fun test(request: SkillConnectionTestRequest): SkillConnectionTestResult {
            requests += request
            return result
        }
    }

    private class SuspendedSkillConnectionTester(
        private val result: SkillConnectionTestResult,
    ) : SkillConnectionTester {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        override suspend fun test(request: SkillConnectionTestRequest): SkillConnectionTestResult {
            started.complete(Unit)
            release.await()
            return result
        }
    }

}

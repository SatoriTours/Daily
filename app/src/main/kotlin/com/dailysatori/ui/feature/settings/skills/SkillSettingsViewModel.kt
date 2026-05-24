package com.dailysatori.ui.feature.settings.skills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.SkillConfigDataSource
import com.dailysatori.service.skill.BuiltInSkillTemplates
import com.dailysatori.service.skill.SkillConnectionTestRequest
import com.dailysatori.service.skill.SkillConnectionTestResult
import com.dailysatori.service.skill.SkillConnectionTester
import com.dailysatori.service.skill.canDeleteSkill
import com.dailysatori.shared.db.Skill_config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

data class SkillSettingsState(
    val skills: List<Skill_config> = emptyList(),
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val message: String? = null,
    val testMessage: String? = null,
    val error: String? = null,
)

data class SkillEditInput(
    val id: Long?,
    val name: String,
    val description: String,
    val gatewayUrl: String,
    val apiToken: String,
    val skillVersion: String,
    val enabled: Boolean,
    val provider: String,
    val templateId: String,
    val toolSchemaJson: String,
)

class SkillSettingsViewModel(
    private val repository: SkillConfigDataSource,
    private val connectionTester: SkillConnectionTester,
) : ViewModel() {
    private val _state = MutableStateFlow(SkillSettingsState())
    val state: StateFlow<SkillSettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.ensureBuiltInWeRead()
            repository.getAll().collect { skills ->
                _state.update { it.copy(skills = skills) }
            }
        }
    }

    fun save(input: SkillEditInput) {
        viewModelScope.launch(Dispatchers.IO) { saveValidated(input) }
    }

    fun delete(skill: Skill_config) {
        if (!canDeleteSkill(skill.builtin)) {
            _state.update { it.copy(error = skillBuiltinDeleteBlockedMessage(), message = null) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) { repository.deleteSkill(skill.id) }
    }

    fun testSkill(input: SkillEditInput) {
        _state.update { it.copy(isTesting = true, testMessage = null, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val validation = validateSkillTestInput(input)
            val message = validation ?: connectionTester.test(input.toConnectionTestRequest()).toUserMessage()
            _state.update { it.copy(isTesting = false, testMessage = message) }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun saveValidated(input: SkillEditInput) {
        _state.update { it.copy(isSaving = true, error = null, message = null) }
        try {
            val existing = input.id?.let(repository::getById)
            val saveInput = skillUpdateInputForExisting(input, existing)
            val validation = validateSkillInput(saveInput.name, saveInput.gatewayUrl, saveInput.toolSchemaJson)
            if (validation != null) {
                _state.update { it.copy(error = validation) }
                return
            }
            persistSkill(saveInput, existing)
            _state.update { it.copy(message = skillSavedMessage()) }
        } catch (e: Exception) {
            _state.update { it.copy(error = skillSaveFailureMessage(e), message = null) }
        } finally {
            _state.update { it.copy(isSaving = false) }
        }
    }

    private fun persistSkill(input: SkillEditInput, existing: Skill_config?) {
        val saveInput = input.trimmed()
        val enabled = skillEnabledValueForSave(saveInput, existing)
        if (saveInput.id == null) {
            repository.insertSkill(
                saveInput.name, saveInput.description, saveInput.gatewayUrl,
                saveInput.apiToken, saveInput.skillVersion, enabled,
                0L, saveInput.provider, saveInput.templateId, saveInput.toolSchemaJson,
            )
        } else {
            repository.updateSkill(
                saveInput.id, saveInput.name, saveInput.description, saveInput.gatewayUrl,
                saveInput.apiToken, saveInput.skillVersion, enabled,
                saveInput.provider, saveInput.templateId, saveInput.toolSchemaJson,
            )
        }
    }
}

fun skillSettingsScreenTitle(): String = "Skills"

fun skillSettingsRowTitle(): String = "Skills"

fun skillSettingsRowSubtitle(): String = "管理 Agent 可调用的外部 Skills"

fun skillAddButtonText(): String = "添加 Skill"

fun skillSaveButtonText(isSaving: Boolean): String = if (isSaving) "保存中..." else "保存"

fun skillTestButtonText(isTesting: Boolean): String = if (isTesting) "测试中..." else "测试 Skill"

fun skillSavedMessage(): String = "Skill 已保存"

fun skillTestSuccessMessage(): String = "连接成功，Skill 服务可用"

fun skillSaveFailureMessage(error: Throwable?): String {
    val detail = error?.message?.trim().orEmpty()
    return if (detail.isBlank()) "保存 Skill 失败" else "保存 Skill 失败：$detail"
}

fun skillBuiltinDeleteBlockedMessage(): String = "内置 Skill 不能删除"

fun skillCoreFieldsEditable(builtin: Long): Boolean = builtin == 0L

fun skillShouldCloseEditorAfterSave(message: String?, isSaving: Boolean): Boolean =
    message == skillSavedMessage() && !isSaving

fun skillEnabledValueForSave(input: SkillEditInput, existing: Skill_config?): Long {
    val isBuiltInWeRead = existing?.builtin == 1L && existing.template_id == BuiltInSkillTemplates.weRead
    if (isBuiltInWeRead) return input.apiToken.trim().isNotBlank().asDbLong()
    return input.enabled.asDbLong()
}

fun skillUpdateInputForExisting(input: SkillEditInput, existing: Skill_config?): SkillEditInput {
    if (existing?.builtin != 1L) return input
    return input.copy(
        name = existing.name,
        description = existing.description,
        gatewayUrl = existing.gateway_url,
        skillVersion = existing.skill_version,
        provider = existing.provider,
        templateId = existing.template_id,
        toolSchemaJson = existing.tool_schema_json,
    )
}

fun validateSkillInput(name: String, gatewayUrl: String, toolSchemaJson: String): String? {
    if (name.trim().isBlank()) return "请输入 Skill 名称"
    if (gatewayUrl.trim().isBlank()) return "请输入 Gateway URL"
    val schema = toolSchemaJson.trim()
    if (schema.isBlank()) return null
    val element = runCatching { Json.parseToJsonElement(schema) }.getOrNull()
        ?: return "Tool Schema 必须是 JSON 对象或数组"
    if (element !is JsonObject && element !is JsonArray) return "Tool Schema 必须是 JSON 对象或数组"
    return null
}

fun validateSkillTestInput(input: SkillEditInput): String? {
    if (input.gatewayUrl.trim().isBlank()) return "请输入 Gateway URL"
    if (input.templateId == BuiltInSkillTemplates.weRead && input.apiToken.trim().isBlank()) return "请输入微信读书 Token"
    return validateSkillInput(input.name, input.gatewayUrl, input.toolSchemaJson)
}

fun SkillEditInput.toConnectionTestRequest(): SkillConnectionTestRequest = SkillConnectionTestRequest(
    name = name.trim(),
    gatewayUrl = gatewayUrl.trim(),
    apiToken = apiToken.trim(),
    skillVersion = skillVersion.trim(),
    provider = provider.trim(),
    templateId = templateId.trim(),
    toolSchemaJson = toolSchemaJson.trim(),
)

private fun SkillConnectionTestResult.toUserMessage(): String = when (this) {
    is SkillConnectionTestResult.Success -> message
    is SkillConnectionTestResult.Failure -> message
}

private fun Boolean.asDbLong(): Long = if (this) 1L else 0L

private fun SkillEditInput.trimmed(): SkillEditInput = copy(
    name = name.trim(),
    description = description.trim(),
    gatewayUrl = gatewayUrl.trim(),
    apiToken = apiToken.trim(),
    skillVersion = skillVersion.trim(),
    provider = provider.trim(),
    templateId = templateId.trim(),
    toolSchemaJson = toolSchemaJson.trim(),
)

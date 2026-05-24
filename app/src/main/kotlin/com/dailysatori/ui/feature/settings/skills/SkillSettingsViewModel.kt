package com.dailysatori.ui.feature.settings.skills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.SkillConfigRepository
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
    val message: String? = null,
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
    private val repository: SkillConfigRepository,
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
        val validation = validateSkillInput(input.name, input.gatewayUrl, input.toolSchemaJson)
        if (validation != null) {
            _state.update { it.copy(error = validation, message = null) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) { saveValidated(input) }
    }

    fun delete(skill: Skill_config) {
        if (!canDeleteSkill(skill.builtin)) {
            _state.update { it.copy(error = skillBuiltinDeleteBlockedMessage(), message = null) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) { repository.delete(skill.id) }
    }

    private fun saveValidated(input: SkillEditInput) {
        _state.update { it.copy(isSaving = true, error = null, message = null) }
        if (input.id == null) {
            repository.insert(
                input.name.trim(), input.description.trim(), input.gatewayUrl.trim(),
                input.apiToken.trim(), input.skillVersion.trim(), input.enabled.asDbLong(),
                0L, input.provider.trim(), input.templateId.trim(), input.toolSchemaJson.trim(),
            )
        } else {
            repository.update(
                input.id, input.name.trim(), input.description.trim(), input.gatewayUrl.trim(),
                input.apiToken.trim(), input.skillVersion.trim(), input.enabled.asDbLong(),
                input.provider.trim(), input.templateId.trim(), input.toolSchemaJson.trim(),
            )
        }
        _state.update { it.copy(isSaving = false, message = skillSavedMessage()) }
    }
}

fun skillSettingsScreenTitle(): String = "Skills"

fun skillAddButtonText(): String = "添加 Skill"

fun skillSaveButtonText(isSaving: Boolean): String = if (isSaving) "保存中..." else "保存"

fun skillSavedMessage(): String = "Skill 已保存"

fun skillBuiltinDeleteBlockedMessage(): String = "内置 Skill 不能删除"

fun skillCoreFieldsEditable(builtin: Long): Boolean = builtin == 0L

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

private fun Boolean.asDbLong(): Long = if (this) 1L else 0L

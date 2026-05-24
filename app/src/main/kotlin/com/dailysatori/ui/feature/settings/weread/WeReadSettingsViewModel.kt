package com.dailysatori.ui.feature.settings.weread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.data.repository.SkillConfigRepository
import com.dailysatori.service.book.resolveStoredWeReadApiKey
import com.dailysatori.service.security.SecretCipher
import com.dailysatori.service.skill.BuiltInSkillTemplates
import com.dailysatori.shared.db.Skill_config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WeReadSettingsState(
    val apiKey: String = "",
    val savedApiKey: String = "",
    val isSaving: Boolean = false,
    val message: String? = null,
)

class WeReadSettingsViewModel(
    private val settingRepository: SettingRepository,
    private val secretCipher: SecretCipher,
    private val skillConfigRepository: SkillConfigRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(WeReadSettingsState())
    val state: StateFlow<WeReadSettingsState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val skill = skillConfigRepository.getBuiltInByTemplateId(BuiltInSkillTemplates.weRead)
            val key = skill?.api_token?.trim() ?: readLegacyApiKey()
            _state.update { it.copy(apiKey = key, savedApiKey = key, message = null) }
        }
    }

    fun updateApiKey(value: String) {
        _state.update { it.copy(apiKey = value, message = null) }
    }

    fun save() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isSaving = true, message = null) }
            runCatching {
                val key = _state.value.apiKey.trim()
                val skill = skillConfigRepository.getBuiltInByTemplateId(BuiltInSkillTemplates.weRead)
                if (skill != null) {
                    updateBuiltInWeReadSkill(skill = skill, key = key, enabled = 1)
                } else {
                    settingRepository.upsert(SettingKeys.weReadApiKey, secretCipher.encrypt(key))
                }
                _state.update {
                    it.copy(apiKey = key, savedApiKey = key, message = weReadSavedMessage())
                }
            }
            _state.update { it.copy(isSaving = false) }
        }
    }

    fun clear() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isSaving = true, message = null) }
            runCatching {
                val skill = skillConfigRepository.getBuiltInByTemplateId(BuiltInSkillTemplates.weRead)
                if (skill != null) {
                    updateBuiltInWeReadSkill(skill = skill, key = "", enabled = 0)
                } else {
                    settingRepository.delete(SettingKeys.weReadApiKey)
                }
                _state.update { it.copy(apiKey = "", savedApiKey = "", message = weReadClearedMessage()) }
            }
            _state.update { it.copy(isSaving = false) }
        }
    }

    private fun readLegacyApiKey(): String {
        val stored = settingRepository.get(SettingKeys.weReadApiKey).orEmpty()
        return resolveStoredWeReadApiKey(
            stored = stored,
            isEncrypted = secretCipher::isEncrypted,
            decrypt = secretCipher::decrypt,
            onPlaintext = { plain -> settingRepository.upsert(SettingKeys.weReadApiKey, secretCipher.encrypt(plain)) },
        )
    }

    private fun updateBuiltInWeReadSkill(skill: Skill_config, key: String, enabled: Long) {
        skillConfigRepository.update(
            id = skill.id,
            name = skill.name,
            description = skill.description,
            gatewayUrl = skill.gateway_url,
            apiToken = key,
            skillVersion = skill.skill_version,
            enabled = enabled,
            provider = skill.provider,
            templateId = skill.template_id,
            toolSchemaJson = skill.tool_schema_json,
        )
    }

}

fun weReadSettingsTitle(): String = "微信读书"

fun weReadApiKeyStatus(apiKey: String): String {
    val trimmed = apiKey.trim()
    if (trimmed.isBlank()) return "未配置"
    if (trimmed.length < 8) return "已配置"
    val suffix = trimmed.takeLast(4)
    return "已配置：wrk-****$suffix"
}

fun weReadSaveButtonText(isSaving: Boolean): String = if (isSaving) "保存中..." else "保存"

fun weReadClearButtonText(): String = "清空"

fun weReadSavedMessage(): String = "微信读书 API Key 已保存"

fun weReadClearedMessage(): String = "微信读书 API Key 已清空"

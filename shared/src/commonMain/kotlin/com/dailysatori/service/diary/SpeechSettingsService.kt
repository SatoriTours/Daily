package com.dailysatori.service.diary

import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.security.SecretValueCipher
import io.ktor.http.Url
import io.ktor.http.URLProtocol
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SpeechConfig(val provider: String, val model: String, val apiAddress: String, val apiKey: String) {
    fun validationError(): String? {
        val service = speechProviders.firstOrNull { it.id == provider } ?: return "请选择支持语音转写的提供商"
        if (model.isBlank()) return "请选择语音转写模型"
        if (!service.customModels && model.trim() !in service.models) return "请选择列表中的语音转写模型"
        if (apiKey.isBlank()) return "请填写 API Key"
        if (apiKey.trim().any { it.isWhitespace() }) return "API Key 不能包含空白字符"
        val url = runCatching { Url(apiAddress.trim()) }.getOrNull()
        if (url == null || url.protocol != URLProtocol.HTTPS || url.host.isBlank() ||
            url.user != null || url.password != null || url.parameters.names().isNotEmpty() || url.fragment.isNotEmpty()
        ) return "请填写有效的 HTTPS 服务地址"
        return null
    }

    override fun toString(): String = "SpeechConfig(provider=$provider, model=$model, apiKey=***)"
}

class SpeechSettingsService(
    private val settings: SettingRepository,
    private val cipher: SecretValueCipher,
    private val aiConfigs: AiConfigService,
) {
    fun load(): SpeechConfig? {
        val stored = settings.get(SettingKeys.speechConfig)
        if (stored != null) return runCatching {
            Json.decodeFromString<SpeechConfig>(cipher.decrypt(stored))
        }.getOrNull()
        val legacy = aiConfigs.getSpeechConfig() ?: return null
        val isGemini = legacy.provider.lowercase() in setOf("gemini", "google", "google-gemini")
        val model = if (isGemini) legacy.model_name else settings.get(SettingKeys.speechModel)
            ?.trim()?.takeIf { it.isNotEmpty() } ?: OpenAiCompatibleSpeechTranscriptionClient.DEFAULT_MODEL
        val provider = when {
            isGemini -> "gemini"
            model in speechProviders.first { it.id == "openai" }.models -> "openai"
            else -> "compatible"
        }
        return SpeechConfig(
            provider = provider,
            model = model,
            apiAddress = legacy.api_address,
            apiKey = legacy.api_token,
        )
    }

    fun hasSavedConfig(): Boolean = settings.get(SettingKeys.speechConfig) != null

    fun save(config: SpeechConfig) {
        val normalized = config.copy(
            model = config.model.trim(), apiAddress = config.apiAddress.trim().trimEnd('/'), apiKey = config.apiKey.trim(),
        )
        require(normalized.validationError() == null) { normalized.validationError().orEmpty() }
        // One encrypted value keeps provider, model and credentials atomic, including backup/restore.
        settings.upsert(SettingKeys.speechConfig, cipher.encrypt(Json.encodeToString(normalized)))
    }
}

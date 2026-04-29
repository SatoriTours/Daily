package com.dailysatori.service.ai

import com.dailysatori.data.repository.AIConfigRepository

enum class AIFunctionType(val value: Long, val displayName: String) {
    GENERAL(0, "通用配置"),
    ARTICLE(1, "文章分析"),
    BOOK(2, "书籍解读"),
    DIARY(3, "日记总结"),
}

class AiConfigService(private val repo: AIConfigRepository) {
    fun getGeneralConfig() = repo.getGeneralConfig()

    fun getConfigForFunction(type: AIFunctionType): com.dailysatori.shared.db.Ai_config? {
        val config = repo.getDefaultByType(type.value)
        if (config == null) return getGeneralConfig()
        if (config.inherit_from_general == 1L) {
            val general = getGeneralConfig() ?: return config
            return config.copy(
                api_address = config.api_address.ifBlank { general.api_address },
                api_token = config.api_token.ifBlank { general.api_token },
                model_name = config.model_name.ifBlank { general.model_name },
            )
        }
        return config
    }

    fun getApiAddress(type: AIFunctionType): String = getConfigForFunction(type)?.api_address ?: ""
    fun getApiToken(type: AIFunctionType): String = getConfigForFunction(type)?.api_token ?: ""
    fun getModelName(type: AIFunctionType): String = getConfigForFunction(type)?.model_name ?: ""
}

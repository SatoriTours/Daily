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

    fun getConfigForFunction(type: AIFunctionType): com.dailysatori.shared.db.AiConfig? {
        val config = repo.getDefaultByType(type.value)
        if (config == null) return getGeneralConfig()
        if (config.inheritFromGeneral == 1L) {
            val general = getGeneralConfig() ?: return config
            return config.copy(
                apiAddress = config.apiAddress.ifBlank { general.apiAddress },
                apiToken = config.apiToken.ifBlank { general.apiToken },
                modelName = config.modelName.ifBlank { general.modelName },
            )
        }
        return config
    }

    fun getApiAddress(type: AIFunctionType): String = getConfigForFunction(type)?.apiAddress ?: ""
    fun getApiToken(type: AIFunctionType): String = getConfigForFunction(type)?.apiToken ?: ""
    fun getModelName(type: AIFunctionType): String = getConfigForFunction(type)?.modelName ?: ""
}

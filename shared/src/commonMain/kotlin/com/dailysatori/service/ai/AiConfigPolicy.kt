package com.dailysatori.service.ai

import com.dailysatori.config.aiProviders

fun aiConfigDisplayName(provider: String, modelName: String): String {
    val providerName = aiProviders.find { it.id == provider }?.name ?: provider
    val displayModel = aiProviders
        .flatMap { it.models }
        .find { it.id == modelName }
        ?.name
        ?: modelName
    return "$providerName / $displayModel"
}

fun canDeleteAiConfig(isDefault: Long?): Boolean = isDefault != 1L

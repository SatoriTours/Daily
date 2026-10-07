package com.dailysatori.service.ai

import kotlinx.serialization.json.JsonObject

internal expect class LangChainAiClient() {
    suspend fun complete(
        prompt: String,
        route: AiRequestRoute,
        apiToken: String,
        modelName: String,
        headers: Map<String, String>,
        systemPrompt: String?,
        temperature: Double,
    ): String

    suspend fun chatCompletion(
        messages: List<JsonObject>,
        route: AiRequestRoute,
        apiToken: String,
        modelName: String,
        headers: Map<String, String>,
        tools: List<JsonObject>,
        temperature: Double,
    ): JsonObject
}

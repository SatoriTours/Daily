package com.dailysatori.service.ai

import kotlinx.serialization.json.JsonObject

internal expect class LangChainAiClient() {
    suspend fun complete(
        prompt: String,
        apiAddress: String,
        apiToken: String,
        modelName: String,
        provider: String,
        systemPrompt: String?,
        temperature: Double,
    ): String

    suspend fun chatCompletion(
        messages: List<JsonObject>,
        apiAddress: String,
        apiToken: String,
        modelName: String,
        provider: String,
        tools: List<JsonObject>,
        temperature: Double,
    ): JsonObject
}

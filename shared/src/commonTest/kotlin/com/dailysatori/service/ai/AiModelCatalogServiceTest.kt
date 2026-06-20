package com.dailysatori.service.ai

import com.dailysatori.config.AiModelDiscoveryProtocol
import kotlin.test.Test
import kotlin.test.assertEquals

class AiModelCatalogServiceTest {
    @Test
    fun buildsDiscoveryEndpointForOpenAiCompatibleHosts() {
        assertEquals(
            "https://api.example.com/v1/models",
            buildAiModelDiscoveryUrl("https://api.example.com", AiModelDiscoveryProtocol.OpenAiCompatible),
        )
        assertEquals(
            "https://api.example.com/v1/models",
            buildAiModelDiscoveryUrl("https://api.example.com/v1", AiModelDiscoveryProtocol.OpenAiCompatible),
        )
        assertEquals(
            "https://open.bigmodel.cn/api/paas/v4/models",
            buildAiModelDiscoveryUrl("https://open.bigmodel.cn/api/paas/v4", AiModelDiscoveryProtocol.OpenAiCompatible),
        )
    }

    @Test
    fun buildsDiscoveryEndpointForAnthropicCompatibleHosts() {
        assertEquals(
            "https://api.anthropic.com/v1/models",
            buildAiModelDiscoveryUrl("https://api.anthropic.com", AiModelDiscoveryProtocol.AnthropicCompatible),
        )
        assertEquals(
            "https://proxy.example.com/v1/models",
            buildAiModelDiscoveryUrl("https://proxy.example.com/v1", AiModelDiscoveryProtocol.AnthropicCompatible),
        )
    }

    @Test
    fun parsesOpenAiCompatibleModels() {
        val body = """
            {"data":[{"id":"gpt-5.5"},{"id":"gpt-5.5"},{"id":"gpt-5.4-mini"}]}
        """.trimIndent()

        assertEquals(
            listOf(DiscoveredAiModel("gpt-5.5", "gpt-5.5"), DiscoveredAiModel("gpt-5.4-mini", "gpt-5.4-mini")),
            parseAiModelDiscoveryResponse(body, AiModelDiscoveryProtocol.OpenAiCompatible),
        )
    }

    @Test
    fun parsesAnthropicCompatibleModelsWithDisplayNames() {
        val body = """
            {"data":[
              {"id":"claude-opus-4-8","display_name":"Claude Opus 4.8"},
              {"id":"claude-sonnet-4-6","display_name":"Claude Sonnet 4.6"}
            ]}
        """.trimIndent()

        assertEquals(
            listOf(
                DiscoveredAiModel("claude-opus-4-8", "Claude Opus 4.8"),
                DiscoveredAiModel("claude-sonnet-4-6", "Claude Sonnet 4.6"),
            ),
            parseAiModelDiscoveryResponse(body, AiModelDiscoveryProtocol.AnthropicCompatible),
        )
    }

    @Test
    fun parsesGeminiModels() {
        val body = """
            {"models":[
              {"name":"models/gemini-3.1-pro-preview","displayName":"Gemini 3.1 Pro Preview"},
              {"name":"models/embedding-001","displayName":"Embedding"}
            ]}
        """.trimIndent()

        assertEquals(
            listOf(DiscoveredAiModel("gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview")),
            parseAiModelDiscoveryResponse(body, AiModelDiscoveryProtocol.Gemini),
        )
    }
}

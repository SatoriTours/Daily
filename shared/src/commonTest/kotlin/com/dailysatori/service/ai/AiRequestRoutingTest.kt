package com.dailysatori.service.ai

import com.dailysatori.config.AiModel
import com.dailysatori.config.AiRequestProtocol
import com.dailysatori.config.findProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class AiRequestRoutingTest {
    @Test
    fun goModelsSelectTheirDocumentedProtocolWithoutChangingProvider() {
        val cases = listOf(
            "glm-5.2" to AiRequestProtocol.OpenAiChatCompletions,
            "kimi-k2.6" to AiRequestProtocol.OpenAiChatCompletions,
            "deepseek-v4-flash" to AiRequestProtocol.OpenAiChatCompletions,
            "mimo-v2.5" to AiRequestProtocol.OpenAiChatCompletions,
            "minimax-m3" to AiRequestProtocol.AnthropicMessages,
            "minimax-m2.7" to AiRequestProtocol.AnthropicMessages,
            "qwen3.7-plus" to AiRequestProtocol.AnthropicMessages,
        )
        assertNotNull(findProvider("opencode-go"))
        cases.forEach { (model, protocol) ->
            val route = resolveAiRequestRoute("opencode-go", model, "https://opencode.ai/zen/go/v1")
            assertEquals(protocol, route.protocol, model)
            assertEquals(
                if (protocol == AiRequestProtocol.AnthropicMessages) "https://opencode.ai/zen/go/v1/messages"
                else "https://opencode.ai/zen/go/v1/chat/completions", route.endpoint, model,
            )
        }
    }

    @Test
    fun anthropicBaseIsNormalizedOnlyAtTheClientBoundary() {
        listOf("https://opencode.ai/zen/go", "https://opencode.ai/zen/go/v1/", "https://opencode.ai/zen/go/v1/messages")
            .forEach { address ->
                val route = resolveAiRequestRoute(" OPENCODE-GO ", " minimax-m2.7 ", address)
                assertEquals("https://opencode.ai/zen/go/v1", route.clientBaseUrl)
                assertEquals("https://opencode.ai/zen/go/v1/messages", route.endpoint)
            }
    }

    @Test
    fun unknownGoModelsAndResponsesModelsNeverFallBackToChatCompletions() {
        listOf("unknown-model", "minimax-future", "qwen-future", "gpt-6-luna", "grok-4.7", "muse-spark-1.3-contributor").forEach { model ->
            assertFailsWith<IllegalArgumentException>(model) {
                resolveAiRequestRoute("opencode-go", model, "https://opencode.ai/zen/go/v1")
            }
        }
        assertEquals(AiModelAvailability.RequiresResponses, aiModelAvailability("opencode-go", "grok-4.7"))
        assertEquals(AiModelAvailability.Unknown, aiModelAvailability("opencode-go", "qwen-future"))
    }

    @Test
    fun nativeProvidersRetainTheirExistingProtocolsAndAddresses() {
        assertEquals(AiRequestProtocol.AnthropicMessages, resolveAiRequestRoute("anthropic", "claude-test", "https://api.anthropic.com").protocol)
        assertEquals(AiRequestProtocol.Gemini, resolveAiRequestRoute("gemini", "gemini-test", "https://generativelanguage.googleapis.com").protocol)
        listOf("minimax", "dashscope", "deepseek", "openai", "custom").forEach { provider ->
            assertEquals(AiRequestProtocol.OpenAiChatCompletions, resolveAiRequestRoute(provider, "custom-model", "https://example.com/v1").protocol)
            assertEquals(AiModelAvailability.Supported, aiModelAvailability(provider, "custom-model"))
        }
    }

    @Test
    fun discoveryCannotOverwriteKnownGoModelProtocolsOrEnableUnknownModels() {
        val provider = assertNotNull(findProvider("opencode-go"))
        val merged = mergeAiModels(provider, listOf(AiModel("minimax-m2.7", "remote"), AiModel("new-model", "New")))
        assertEquals(1, merged.count { it.id == "minimax-m2.7" })
        assertEquals(AiRequestProtocol.AnthropicMessages, merged.first { it.id == "minimax-m2.7" }.requestProtocol)
        assertEquals(AiModelAvailability.Unknown, aiModelAvailability(provider.id, "new-model"))
        assertEquals(AiRequestProtocol.AnthropicMessages, mergeAiModels(provider, emptyList()).first { it.id == "qwen3.7-plus" }.requestProtocol)
        assertEquals("https://opencode.ai/zen/go/v1/models", buildAiModelDiscoveryUrl(provider.apiHost, provider.modelDiscovery.protocol))
    }
}

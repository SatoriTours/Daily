package com.dailysatori.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AiProviderModelsTest {
    @Test
    fun builtInCatalogIncludesCurrentKeyModels() {
        assertProviderHasModel("zhipu", "GLM-5.2")
        assertProviderHasModel("minimax", "MiniMax-M3")
        assertProviderHasModel("anthropic", "claude-opus-4-8")
        assertProviderHasModel("grok", "grok-4.3")
        assertProviderHasModel("deepseek", "deepseek-v4-flash")
    }

    @Test
    fun providersDeclareModelDiscoveryProtocol() {
        assertEquals(AiModelDiscoveryProtocol.OpenAiCompatible, findProvider("openai")?.modelDiscovery?.protocol)
        assertEquals(AiModelDiscoveryProtocol.AnthropicCompatible, findProvider("anthropic")?.modelDiscovery?.protocol)
        assertEquals(AiModelDiscoveryProtocol.Gemini, findProvider("gemini")?.modelDiscovery?.protocol)
        assertEquals(AiModelDiscoveryProtocol.OpenAiCompatible, findProvider("zhipu")?.modelDiscovery?.protocol)
    }

    private fun assertProviderHasModel(providerId: String, modelId: String) {
        val provider = assertNotNull(findProvider(providerId), "Missing provider $providerId")
        assertTrue(
            provider.models.any { it.id == modelId },
            "Expected ${provider.name} to include $modelId but found ${provider.models.map { it.id }}",
        )
    }
}

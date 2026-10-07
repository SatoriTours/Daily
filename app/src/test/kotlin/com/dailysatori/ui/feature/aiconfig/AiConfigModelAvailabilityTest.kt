package com.dailysatori.ui.feature.aiconfig

import com.dailysatori.config.AiModel
import com.dailysatori.config.findProvider
import com.dailysatori.service.ai.AiModelAvailability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiConfigModelAvailabilityTest {
    @Test
    fun manualGoModelNamesCannotBypassProtocolValidation() {
        val selected = findProvider("opencode-go")!!.models.first { it.id == "minimax-m2.7" }
        val state = AiConfigEditState(selectedProvider = findProvider("opencode-go"), selectedModel = selected,
            customModelName = " qwen3.7-plus ", apiToken = "key")
        assertTrue(state.canUseModel)
        assertEquals(AiModelAvailability.Supported, state.modelAvailability)
        assertFalse(state.copy(customModelName = "unknown").canUseModel)
        assertEquals(AiModelAvailability.Unknown, state.copy(customModelName = "unknown").modelAvailability)
        assertFalse(state.copy(customModelName = "grok-4.7").canUseModel)
        assertEquals(AiModelAvailability.RequiresResponses, state.copy(customModelName = "grok-4.7").modelAvailability)
    }

    @Test
    fun restoredCachedModelsUseBuiltinRoutingNotRemoteMetadata() {
        val state = AiConfigEditState(selectedProvider = findProvider("opencode-go"),
            selectedModel = AiModel("minimax-m2.7", "ID-only cached model"), apiToken = "key")
        assertTrue(state.canUseModel)
        assertFalse(state.copy(selectedModel = null).canUseModel)
    }

    @Test
    fun ordinaryProvidersStillAllowCustomModels() {
        assertTrue(AiConfigEditState(selectedProvider = findProvider("dashscope"), customModelName = "custom-model").canUseModel)
        assertFalse(AiConfigEditState(customModelName = "custom-model").canUseModel)
    }
}

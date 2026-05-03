package com.dailysatori.service.ai

import kotlin.test.Test
import kotlin.test.assertEquals

class AiConfigPolicyTest {
    @Test
    fun buildsDisplayNameFromProviderAndModelWithoutStoredName() {
        assertEquals(
            "OpenAI / GPT-5.5 (旗舰)",
            aiConfigDisplayName(provider = "openai", modelName = "gpt-5.5"),
        )
    }

    @Test
    fun fallsBackToRawProviderAndModelForCustomConfig() {
        assertEquals(
            "custom / llama3.2",
            aiConfigDisplayName(provider = "custom", modelName = "llama3.2"),
        )
    }

    @Test
    fun onlyNonDefaultAiConfigsCanBeDeleted() {
        assertEquals(false, canDeleteAiConfig(isDefault = 1L))
        assertEquals(true, canDeleteAiConfig(isDefault = 0L))
    }
}

package com.dailysatori

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class LangChainProguardRulesTest {
    @Test
    fun preservesLangChainOpenAiJacksonSerializationRules() {
        val rules = resolveProguardRules().readText()

        assertTrue(
            rules.contains("dev.langchain4j.model.openai.internal.**"),
            "Release R8 rules must keep LangChain4j OpenAI DTOs used by Jackson.",
        )
        assertTrue(
            rules.contains("com.fasterxml.jackson.annotation.**"),
            "Release R8 rules must keep Jackson annotations used by LangChain4j DTOs.",
        )
    }

    private fun resolveProguardRules(): Path {
        val candidates = listOf(
            Path.of("proguard-rules.pro"),
            Path.of("app/proguard-rules.pro"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Unable to locate app/proguard-rules.pro from ${Path.of("").toAbsolutePath()}")
    }
}

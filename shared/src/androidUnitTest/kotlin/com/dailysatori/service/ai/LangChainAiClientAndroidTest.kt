package com.dailysatori.service.ai

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LangChainAiClientAndroidTest {
    @Test
    fun createsAndroidCompatibleHttpClientBuilder() {
        val builder = langChainHttpClientBuilder()
        val builderName = builder.javaClass.name

        assertTrue(builderName.contains("okhttp", ignoreCase = true), builderName)
        assertFalse(builderName.contains("jdk", ignoreCase = true), builderName)
        builder.build()
    }
}

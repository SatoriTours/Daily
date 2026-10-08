package com.dailysatori.service.diary

import com.dailysatori.service.ai.AiPurpose
import com.dailysatori.service.ai.AiService
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiaryTagAiLiveTest {
    @Test fun configuredAiReusesVocabularyAndKeepsRelatedConceptsSeparate() = runBlocking {
        assumeTrue("Opt in to real diary-tag validation", System.getenv("DAILY_DIARY_TAG_AI_LIVE_TEST") == "1")
        val file = File("../.local/ai-test.json")
        require(file.isFile) { "Missing .local/ai-test.json" }
        val config = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }
            .getOrElse { error("Invalid AI test configuration") }
        fun required(key: String) = config[key]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
            ?: error("Missing AI test field: $key")
        HttpClient(OkHttp) {
            install(HttpTimeout) { requestTimeoutMillis = 90_000; connectTimeoutMillis = 15_000 }
        }.use { client ->
            val ai = AiService(client)
            val generator = DiaryTagGenerator { prompt, system ->
                try {
                    ai.completePrivate(prompt, required("apiAddress"), required("apiToken"),
                        required("modelName"), required("provider"), system, purpose = AiPurpose.INTERACTIVE)
                } catch (error: Exception) {
                    error("Live diary-tag request failed: ${error::class.simpleName}")
                }
            }
            val result = generator.generate(
                "今天反复比较两个工作机会的工作内容、发展空间和收入，最终决定留在现公司。" +
                    "这次职业抉择让我更明确自己想要什么，整篇只讨论职业选择。",
                listOf("职业选择", "读书"), mapOf("职业抉择" to "职业选择"),
            )
            assertEquals(listOf("职业选择"), result.tags)
            val names = listOf("职业选择", "职业抉择", "学习", "读书", "焦虑", "压力")
            val merges = generator.suggestMerges(names)
            assertTrue(merges.any { setOf(it.from, it.to) == setOf("职业选择", "职业抉择") })
            assertTrue(merges.none { setOf(it.from, it.to) in listOf(setOf("学习", "读书"), setOf("焦虑", "压力")) })
        }
    }
}

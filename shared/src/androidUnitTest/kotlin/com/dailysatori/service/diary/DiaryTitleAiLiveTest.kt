package com.dailysatori.service.diary

import com.dailysatori.service.ai.AiService
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

class DiaryTitleAiLiveTest {
    @Test fun configuredAiGeneratesRelevantTitlesForSyntheticRecordings() = runBlocking {
        assumeTrue("Opt in to real diary-title validation", System.getenv("DAILY_DIARY_TITLE_AI_LIVE_TEST") == "1")
        val file = File("../.local/ai-test.json")
        require(file.isFile) { "Missing .local/ai-test.json" }
        val config = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }
            .getOrElse { error("Invalid AI test configuration") }
        fun required(key: String) = config[key]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
            ?: error("Missing AI test field: $key")
        val cases = listOf(
            "今天晚饭后去公园散步。走在树荫下，感觉心情轻松了很多，想继续保持这个习惯。",
            "今天去书店买了三本书，花了128元。我没有买那本小说，因为预算不够。下周可能再去，还没决定。",
        )
        val titles = HttpClient(OkHttp) {
            install(HttpTimeout) { requestTimeoutMillis = 90_000; connectTimeoutMillis = 15_000 }
        }.use { client ->
            val ai = AiService(client)
            val generator = DiaryTitleGenerator { prompt, system ->
                ai.completePrivate(prompt, required("apiAddress"), required("apiToken"),
                    required("modelName"), required("provider"), system)
            }
            cases.map { generator.generate(it) }
        }
        val artifact = File("../.local/diary-title-live-results.json")
        artifact.writeText(Json.encodeToString(buildJsonObject {
            put("originals", JsonArray(cases.map(::JsonPrimitive)))
            put("titles", JsonArray(titles.map(::JsonPrimitive)))
        }))
        artifact.setReadable(false, false); artifact.setWritable(false, false)
        artifact.setReadable(true, true); artifact.setWritable(true, true)
        assertTrue(listOf("公园", "散步", "轻松", "树荫").any { it in titles[0] }, "Title must reflect the walk")
        assertTrue(listOf("书", "阅读", "预算").any { it in titles[1] }, "Title must reflect the bookshop visit")
        assertTrue(titles.all { it.length in 1..60 && '\n' !in it && !it.startsWith("#") })
    }
}

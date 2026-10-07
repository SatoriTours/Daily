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

class DiaryTranscriptPolishAiLiveTest {
    @Test fun configuredAiTidiesSyntheticDiariesWithoutLosingFactsOrUncertainty() = runBlocking {
        assumeTrue("Opt in to real transcript-polish validation", System.getenv("DAILY_DIARY_POLISH_AI_LIVE_TEST") == "1")
        val file = File("../.local/ai-test.json")
        require(file.isFile) { "Missing .local/ai-test.json" }
        val config = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }
            .getOrElse { error("Invalid AI test configuration") }
        fun required(key: String) = config[key]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
            ?: error("Missing AI test field: $key")
        val cases = listOf(
            "嗯今天今天去书店买了3本书，花了128元。哦顺序说反了，我先去咖啡馆，之后才去书店。" +
                "咖啡花了26元。我没有买那本小说，因为预算不够，不是不喜欢。就是下周二可能再去，还没决定。",
            "那个早上跑了2公里，晚上又跑了2公里，不是重复说同一次。嗯最近有点累，但不是很难过。" +
                "昨天和小李谈了换工作的事，是我想换，不是他想换。我还没决定离职，暂时也不想听建议。",
        )
        val outputs = mutableListOf<String>()
        HttpClient(OkHttp) {
            install(HttpTimeout) { requestTimeoutMillis = 90_000; connectTimeoutMillis = 15_000 }
        }.use { client ->
            val ai = AiService(client)
            val service = DiaryTranscriptPolishService { prompt, system ->
                ai.completePrivate(prompt, required("apiAddress"), required("apiToken"),
                    required("modelName"), required("provider"), system)
            }
            cases.forEach { outputs += service.polish(it) }
            // Both retries use the same raw source, with version-specific corrections overriding history.
            val old = DiaryPolishVersion(0, "旧的错误版本", "咖啡金额改为30元。")
            val first = DiaryPolishVersion(1, outputs[0], "咖啡金额应为28元，不是26元。按话题分成至少两段；保留还没决定的语气。")
            outputs += service.polish(cases.first(), first, listOf(old, first))
            val second = DiaryPolishVersion(2, outputs[2], "继续保留分段和还没决定的语气，不要写成已决定。")
            outputs += service.polish(cases.first(), second, listOf(old, first, second))
        }
        val artifact = File("../.local/diary-polish-live-results.json")
        artifact.writeText(Json { prettyPrint = true }.encodeToString(buildJsonObject {
            put("originals", JsonArray(cases.map(::JsonPrimitive)))
            put("results", JsonArray(outputs.map(::JsonPrimitive)))
        }))
        artifact.setReadable(false, false); artifact.setWritable(false, false)
        artifact.setReadable(true, true); artifact.setWritable(true, true)
        for (result in listOf(outputs[0])) {
            assertTrue(listOf("128", "26", "小说", "预算", "下周二").all { it in result })
            assertTrue(result.indexOf("咖啡") < result.indexOf("书店"))
            assertTrue(listOf("可能", "没决定", "未决定", "尚未决定").any { it in result })
        }
        for ((index, amount) in listOf(2 to "28", 3 to "28")) {
            val result = outputs[index]
            assertTrue(Regex("(?<!\\d)$amount(?!\\d)").containsMatchIn(result), "Explicit coffee correction must win and survive the next revision")
            assertTrue(!Regex("(?<!\\d)(26|30)(?!\\d)").containsMatchIn(result), "Original and conflicting historical amounts must not win")
            assertTrue(listOf("128", "小说", "预算", "下周二").all { it in result })
            assertTrue(listOf("可能", "没决定", "未决定", "尚未决定").any { it in result })
            assertTrue("\n\n" in result, "Paragraph feedback must be applied")
        }
        assertTrue(listOf("早上", "晚上", "小李", "离职").all { it in outputs[1] })
        assertTrue(outputs[1].length > 65, "Must not compress the recording into a short summary")
    }
}

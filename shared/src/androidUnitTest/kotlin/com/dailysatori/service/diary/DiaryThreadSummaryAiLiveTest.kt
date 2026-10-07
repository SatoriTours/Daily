package com.dailysatori.service.diary

import com.dailysatori.service.ai.AiService
import com.dailysatori.shared.db.Diary
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

/**
 * 日记续写专属真实接口用例：使用 .local/ai-test.json 的默认配置，只做少量基础汇总验证。
 * 不使用其他 AI 测试替代；配置缺失或请求失败必须报告未完成，不记通过。
 */
class DiaryThreadSummaryAiLiveTest {
    @Test
    fun liveSummaryReflectsFactCorrectionWithoutTouchingOriginals() = runBlocking {
        liveDiaryAi().use { live ->
            val entries = listOf(
                diary(1, "下周五出发去杭州出差，机票已经订好了。", 1_700_000_000_000),
                diary(2, "记错了，不是周五，是这周四早上出发，机票也改签了。", 1_700_100_000_000, parentId = 1),
            )

            val summary = live.summaryGenerator.generate(snapshot(entries))

            assertTrue(summary.isNotBlank(), "真实汇总不能为空")
            assertTrue(summary.contains("周四"), "汇总必须体现后续的事实纠正：$summary")
            assertEquals("下周五出发去杭州出差，机票已经订好了。", entries[0].content, "原文不得被汇总替换")
            assertEquals("记错了，不是周五，是这周四早上出发，机票也改签了。", entries[1].content)
        }
    }

    @Test
    fun liveSummaryKeepsUndecidedStateWithoutInventingConclusion() = runBlocking {
        liveDiaryAi().use { live ->
            val entries = listOf(
                diary(1, "我在考虑要不要换城市生活，列了几个优缺点但还没有结论。", 1_700_000_000_000),
                diary(2, "今天又倾向留在原地，但也只是当下感觉，两边还在比较。", 1_700_100_000_000, parentId = 1),
            )

            val summary = live.summaryGenerator.generate(snapshot(entries))

            assertTrue(summary.isNotBlank(), "真实汇总不能为空")
            assertTrue(
                listOf("没有", "还没", "尚未", "仍在", "还在", "未定", "不确定", "比较", "考虑").any { it in summary },
                "尚无结论时必须如实说明：$summary",
            )
            val decision = Regex("已经决定|最终决定")
            val negation = Regex("(?:尚未|并未|未|没有|还没|不曾|尚无)[^，。；！？\\n]{0,8}$")
            val inventedDecision = summary.split(Regex("[，。；！？\\n]")).any { clause ->
                decision.findAll(clause).any { match ->
                    !negation.containsMatchIn(clause.substring(0, match.range.first).takeLast(12))
                }
            }
            assertTrue(!inventedDecision, "不得替用户强行下结论：$summary")
        }
    }

    private fun diary(id: Long, content: String, createdAt: Long, parentId: Long? = null) =
        Diary(id, content, null, null, null, createdAt, createdAt, parentId)

    private fun snapshot(entries: List<Diary>) = DiaryThreadSnapshot(
        root = entries.first(),
        entries = entries,
        attachments = emptyList(),
        revision = 7,
        pendingAttachmentCount = 0,
        summary = null,
    )
}

/** 读取被忽略的 .local/ai-test.json 并封装真实 AI 调用；不打印、不提交任何密钥。 */
internal class LiveDiaryAi(
    private val client: HttpClient,
    private val apiAddress: String,
    private val apiToken: String,
    private val modelName: String,
    private val provider: String,
) : AutoCloseable {
    val summaryGenerator: DiaryThreadSummaryGenerator
        get() = DiaryThreadSummaryGenerator { prompt, system -> complete(prompt, system) }

    val thoughtGenerator: DiaryThoughtGenerator
        get() = DiaryThoughtGenerator { prompt, system -> complete(prompt, system) }

    private val ai = AiService(client)

    internal suspend fun complete(prompt: String, system: String): String = try {
        ai.completePrivate(prompt, apiAddress, apiToken, modelName, provider, system)
    } catch (error: Exception) {
        error("Live diary AI request failed: ${error::class.simpleName}")
    }

    override fun close() = client.close()
}

internal fun liveDiaryAi(): LiveDiaryAi {
    assumeTrue("Opt in to real diary-thread AI validation", System.getenv("DAILY_AI_LIVE_TEST") == "1")
    val file = File("../.local/ai-test.json")
    require(file.isFile) { "Missing .local/ai-test.json" }
    val config = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }
        .getOrElse { error("Invalid AI test configuration") }
    fun required(key: String) = config[key]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
        ?: error("Missing AI test field: $key")
    val client = HttpClient(OkHttp) {
        install(HttpTimeout) { requestTimeoutMillis = 120_000; connectTimeoutMillis = 15_000 }
    }
    return LiveDiaryAi(client, required("apiAddress"), required("apiToken"), required("modelName"), required("provider"))
}

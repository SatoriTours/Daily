package com.dailysatori.service.assistant

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.assistant.data.AssistantStreamRedactor
import com.dailysatori.service.diagnostics.TaskHttpRequestHandle
import com.dailysatori.service.diagnostics.TaskHttpSseCapture
import com.dailysatori.shared.db.Ai_config
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

/** Receive-side cost and first-visible-text behaviour of the streaming path. */
class AssistantStreamCostTest {
    private val model = LLModel(LLMProvider.OpenAI, "test-model", listOf(LLMCapability.Tools))
    private val config = Ai_config(1, "openai", "https://example.test/v1", "test-secret", "test-model", 1, 0, 0)

    @Test
    fun sanitizingStaysBoundedPerChunkAndStillDeliversFirstAndLastText() = runBlocking<Unit> {
        val chunks = List(600) { "公开内容第" + it + "段。" }
        val text = chunks.joinToString("")
        val stream = chunks.joinToString("") { "data: {\"choices\":[{\"delta\":{\"content\":\"$it\"}}]}\n\n" } + "data: [DONE]\n\n"
        var scrubbed = 0L
        val deltas = mutableListOf<String>()
        HttpClient(MockEngine {
            respond(stream, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
        }).use { client ->
            val executor = KoogAiPromptExecutor(AiService(client), config, streamFilter = {
                AssistantStreamRedactor({ raw -> scrubbed += raw.length; raw }, longestSecret = 16)
            })
            deltas += executor.executeStreaming(prompt("cost") { user("hi") }, model).toList()
                .filterIsInstance<StreamFrame.TextDelta>().map { it.text }
        }
        assertEquals(text, deltas.joinToString(""), "末段必须交付完整回答")
        val bound = 60L * text.length
        assertTrue(scrubbed <= bound, "收流路径仍在整篇重复脱敏：scrubbed=$scrubbed bound=$bound")
    }

    @Test
    fun theFirstSafeTextIsShownAsSoonAsTheHoldBackWindowOpens() = runBlocking<Unit> {
        // The 32-char hold-back guard forbids publishing earlier, but the first words must not then wait
        // for a further full publish step of 64 characters.
        val chunks = List(30) { "公开内容" }
        val run = driveStream(chunks, stepMs = 0)
        assertTrue(run.deltas.first().isNotEmpty(), "首段文本被吞掉")
        assertEquals(chunks.joinToString(""), run.deltas.joinToString(""), "末段必须交付完整回答")
        assertTrue(run.deltas.first().length <= 8,
            "首个安全前缀应当立即显示，实际等到 " + run.deltas.first().length + " 字")
    }

    @Test
    fun sparseSlowChunksStillRepaintWithoutWaitingForAFullStep() = runBlocking<Unit> {
        // Small, slow chunks: every new chunk arrives after the repaint interval, so the reader keeps
        // seeing text instead of one update per 64 accumulated characters.
        val chunks = List(40) { "公开" }
        val run = driveStream(chunks, stepMs = 150)
        assertEquals(chunks.joinToString(""), run.deltas.joinToString(""), "末段必须交付完整回答")
        assertTrue(run.deltas.first().length <= 6,
            "低速小块下首字仍然等到凑满步长，实际 " + run.deltas.first().length + " 字")
        assertTrue(run.deltas.size >= 10, "低速小块下重绘次数过少：" + run.deltas.size)
    }

    @Test
    fun anUnarmedSseCaptureLeavesTheReceivePathUntouched() {
        var writes = 0
        val capture = TaskHttpSseCapture(null, null) { _, _, _ -> writes++ }
        assertFalse(capture.active)
        repeat(2_000) {
            capture.accept("data: {\"choices\":[{\"delta\":{\"content\":\"公开\"}}]}")
            capture.accept("")
        }
        capture.finish()
        assertEquals(0, writes)
        // An armed call keeps capturing: the optimization never disables the approved diagnostics.
        assertTrue(TaskHttpSseCapture(1L, TaskHttpRequestHandle("r1")).active)
    }

    private class Run(val deltas: List<String>)

    /**
     * Drives the transport one chunk at a time and advances the clock only after the receive path has
     * actually attempted the next repaint, so "small sparse chunks" is reproduced instead of raced.
     */
    private suspend fun CoroutineScope.driveStream(chunks: List<String>, stepMs: Long): Run {
        val time = TestTimeSource()
        val body = ByteChannel(autoFlush = true)
        val attempted = Channel<Unit>(Channel.UNLIMITED)
        return HttpClient(MockEngine {
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
        }).use { client ->
            val executor = KoogAiPromptExecutor(AiService(client), config, streamFilter = {
                AssistantStreamRedactor({ raw -> attempted.trySend(Unit); raw }, longestSecret = 16)
            }, timeSource = time)
            val collecting = async {
                executor.executeStreaming(prompt("first") { user("hi") }, model).toList()
                    .filterIsInstance<StreamFrame.TextDelta>().map { it.text }
            }
            withTimeout(10_000) {
                chunks.forEach { chunk ->
                    body.writeStringUtf8(sse(chunk))
                    if (stepMs > 0) {
                        // A new chunk must lead to a repaint attempt; waiting on it keeps the clock honest.
                        withTimeout(2_000) { attempted.receive() }
                        time += stepMs.milliseconds
                    }
                }
                body.writeStringUtf8("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n")
                body.close()
            }
            Run(withTimeout(10_000) { collecting.await() })
        }
    }

    private fun sse(content: String) = "data: " + buildJsonObject {
        put("choices", buildJsonArray { add(buildJsonObject { put("delta", buildJsonObject { put("content", content) }) }) })
    } + "\n\n"
}

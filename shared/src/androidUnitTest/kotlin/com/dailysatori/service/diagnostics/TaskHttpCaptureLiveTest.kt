package com.dailysatori.service.diagnostics

import com.dailysatori.service.ai.AiPurpose
import com.dailysatori.service.ai.AiService
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Live check of the capture layer against a real AI endpoint. It only runs when DAILY_AI_LIVE_TEST=1
 * and .local/ai-test.json exists, arms a TEST-ONLY task id (never a user task, never on a device) and
 * removes the session afterwards.
 */
class TaskHttpCaptureLiveTest {
    private class RecordingSink : TaskHttpSink {
        val records = mutableListOf<Triple<Long, String, TaskHttpRecord>>()
        override fun write(taskId: Long, label: String, record: TaskHttpRecord) {
            records += Triple(taskId, label, record)
        }
    }

    @Test
    fun aRealAiCallCapturesItsSanitizedRequestAndResponse() = runBlocking<Unit> {
        assumeTrue("Opt in to live capture validation", System.getenv("DAILY_AI_LIVE_TEST") == "1")
        val file = File("../.local/ai-test.json")
        require(file.isFile) { "缺少 .local/ai-test.json，真实采集验证未运行" }
        val value = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }
            .getOrElse { error("AI 测试配置格式无效") }
        fun required(key: String): String = value[key]?.jsonPrimitive?.content?.trim()
            ?.takeIf(String::isNotBlank) ?: error("AI 测试配置缺少 " + key)
        val apiToken = required("apiToken")

        val sink = RecordingSink()
        TaskHttpCapture.session.bind(sink)
        TaskHttpCapture.session.arm(TEST_TASK_ID, "live-capture")
        try {
            HttpClient(OkHttp) { install(HttpTimeout) { requestTimeoutMillis = 180_000; connectTimeoutMillis = 15_000 } }.use { client ->
                val ai = AiService(client)
                withContext(DiagnosticLog.traceContext(DiagnosticTrace(DiagnosticLog.newId(), TEST_TASK_ID))) {
                    val answer = ai.complete(
                        prompt = "只回答一个词：ping",
                        apiAddress = required("apiAddress"),
                        apiToken = apiToken,
                        modelName = required("modelName"),
                        provider = required("provider"),
                        purpose = AiPurpose.EXTERNAL_CONTENT,
                    )
                    assertTrue(answer.isNotBlank(), "the live call must produce an answer")
                }
            }

            val records = sink.records.map { it.third }
            val requests = records.filter { it.direction == TaskHttpDirection.REQUEST }
            val responses = records.filter { it.direction == TaskHttpDirection.RESPONSE }
            assertTrue(requests.any { !it.url.isNullOrBlank() }, "the request keeps a debuggable sanitized url")
            val request = requests.first { it.bodyState == TASK_HTTP_CAPTURED }
            assertTrue(request.body!!.contains("\"model\""), "the built request body is captured")
            assertTrue(!request.body.contains(apiToken), "credentials never appear in the capture")
            val response = responses.first { it.bodyState == TASK_HTTP_CAPTURED }
            assertTrue(response.body!!.isNotBlank())
            assertTrue(
                requests.any { it.requestId == response.requestId },
                "request and response share the handle identity of this call",
            )
            assertTrue(records.none { it.requestId.startsWith("unpaired") })
        } finally {
            TaskHttpCapture.session.disarm(TEST_TASK_ID)
            TaskHttpCapture.session.bind(null)
        }
    }

    private companion object {
        /** Test-only identity: nothing in the app is armed for this id. */
        const val TEST_TASK_ID = 990_001L
    }
}


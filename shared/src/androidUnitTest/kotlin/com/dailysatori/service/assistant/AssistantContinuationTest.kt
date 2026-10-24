package com.dailysatori.service.assistant

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.*
import com.dailysatori.service.ai.*
import com.dailysatori.service.asynctask.*
import com.dailysatori.service.mcp.RemoteMcpClient
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import io.ktor.utils.io.*
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.*
import com.dailysatori.service.assistant.data.AssistantDataGateway
import kotlinx.serialization.json.*
import kotlin.test.*

class AssistantContinuationTest {
    @Test fun streamingExecutorEmitsSafePrefixBeforeProviderCompletes() = runBlocking<Unit> {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val configs = AIConfigRepository(DailySatoriDatabase(driver))
            configs.insert("openai", "https://example.test/v1", "configured-secret", "model", 1)
            val config = assertNotNull(AiConfigService(configs).getConfig(AiPurpose.INTERACTIVE))
            val body = ByteChannel(autoFlush = true)
            val first = CompletableDeferred<String>()
            val emitted = mutableListOf<String>()
            fun event(content: String) = "data: " + buildJsonObject { put("choices", buildJsonArray {
                add(buildJsonObject { put("delta", buildJsonObject { put("content", content) }) })
            }) } + "\n\n"
            HttpClient(MockEngine { respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream")) }).use { client ->
                val data = AssistantDataGateway(driver)
                val executor = KoogAiPromptExecutor(AiService(client), config, data::redact, streamFilter = data::streamRedactor)
                val job = async {
                    executor.executeStreaming(prompt("stream") { user("继续") }, LLModel(LLMProvider.OpenAI, "model", emptyList())).collect {
                        emitted += it
                        first.complete(it)
                    }
                }
                try {
                    body.writeStringUtf8(event("这是可以提前展示的普通回答。".repeat(40) + "configu"))
                    val early = withTimeout(5000) { first.await() }
                    assertTrue(early.isNotBlank())
                    assertFalse(job.isCompleted, "the UI must receive text while the provider request is still open")
                    assertFalse(early.contains("configu"), "an incomplete credential prefix must remain buffered")
                    body.writeStringUtf8(event("red-secret 后续普通内容。"))
                    body.writeStringUtf8("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n")
                    body.close()
                    job.await()
                    assertTrue(emitted.size >= 2)
                    assertFalse(emitted.any { it.contains("configured-secret") || it.endsWith("configu") })
                    assertTrue(emitted.joinToString("").contains("已隐藏凭据"))
                } finally {
                    job.cancelAndJoin()
                    body.cancel()
                }
            }
        }
    }

    @Test fun revokedConsentClearsSnapshotsAndInvalidatesOutstandingAuthority() = runBlocking<Unit> {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val tasks = AsyncTaskRepository(db)
            val memory = AssistantMemoryService(driver)
            val actions = AssistantActionService(driver, RepositoryAssistantBusiness(driver) { _, _ -> })
            val conversation = PersistentAssistantConversation(driver, actions, memory) {}
            conversation.setCloudConsent(true)
            val turn = conversation.send("revocation", "client", "这是尚未完成的请求")
            assertTrue(tasks.claimForRun(turn.taskId, "owner", Long.MAX_VALUE))
            tasks.updateProgress(turn.taskId, 0, 1, "", """{"messageHistory":"private unfinished content"}""")
            conversation.setCloudConsent(false)
            val held = tasks.getById(turn.taskId)!!
            assertEquals("failed", held.status)
            assertNull(held.lease_owner)
            assertEquals("", held.checkpoint_json)
            assertTrue(AssistantTurnRepository(driver).get(turn.id)!!.requiresResume)
        }
    }

    @Test fun failedRequestResumesSavedKoogNodeWithoutRepeatingCompletedModelRound() = runBlocking<Unit> {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val configs = AIConfigRepository(db)
            configs.insert("openai", "https://example.test/v1", "configured-secret", "model", 1)
            val tasks = AsyncTaskRepository(db)
            val turns = AssistantTurnRepository(driver)
            val memory = AssistantMemoryService(driver)
            val actions = AssistantActionService(driver, RepositoryAssistantBusiness(driver) { _, _ -> })
            val conversation = PersistentAssistantConversation(driver, actions, memory) {}
            conversation.setCloudConsent(true)
            val turn = conversation.send("resume", "client", "查询这条消息的原文")
            var requests = 0
            var initialRounds = 0
            HttpClient(MockEngine { request ->
                requests++
                val body = Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject
                val hasToolResult = body["messages"]!!.jsonArray.any { it.jsonObject["role"]?.jsonPrimitive?.content == "tool" }
                if (requests == 2) respond("temporary unavailable", HttpStatusCode.ServiceUnavailable)
                else {
                    val message = if (hasToolResult) buildJsonObject { put("content", "已读取保存的原文。") }
                    else {
                        initialRounds++
                        buildJsonObject { put("tool_calls", buildJsonArray { add(buildJsonObject {
                            put("id", "read-call"); put("type", "function")
                            put("function", buildJsonObject {
                                put("name", "read_record")
                                put("arguments", "{\"source\":\"chat\",\"id\":\"${turn.id}\"}")
                            })
                        }) }) }
                    }
                    respond(buildJsonObject { put("choices", buildJsonArray { add(buildJsonObject { put("message", message) }) }) }.toString(),
                        HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }).use { client ->
                val runtime = KoogAssistantRuntime(driver, AiService(client), AiConfigService(configs), actions, memory, RemoteMcpClient(client))
                val runner = AsyncTaskRunner(tasks, AsyncTaskHandlerRegistry(listOf(AssistantTurnTaskHandler(turns, tasks, runtime))))
                assertEquals(AsyncTaskRunOutcome.RetryScheduled, runner.run(turn.taskId))
                val checkpoint = tasks.getById(turn.taskId)!!.checkpoint_json
                assertTrue(checkpoint.contains("messageHistory"), "completed Koog context must survive the failed request")
                assertFalse(checkpoint.contains("configured-secret"))
                // Simulate the retry delay having elapsed, without sleeping or changing the scheduler.
                driver.execute(null, "UPDATE async_task SET run_after_ms=NULL WHERE id=?", 1) { bindLong(0, turn.taskId) }
                assertEquals(AsyncTaskRunOutcome.Succeeded, runner.run(turn.taskId))
                assertEquals(3, requests, "only the interrupted model request may repeat")
                assertEquals(1, initialRounds, "the first completed model/tool round must not restart")
                assertEquals(2, ChatConversationRepository(db).getBySession("resume").size)
            }
        }
    }

    @Test fun streamingTransportRetainsToolCallIdsArgumentsAndReasoningAcrossDeltas() = runBlocking<Unit> {
        val events = listOf(
            buildJsonObject { put("reasoning_content", "思考") },
            buildJsonObject { put("tool_calls", buildJsonArray { add(buildJsonObject {
                put("index", 0); put("id", "call-1"); put("type", "function")
                put("function", buildJsonObject { put("name", "read_record"); put("arguments", "{\"source\":") })
            }) }) },
            buildJsonObject { put("tool_calls", buildJsonArray { add(buildJsonObject {
                put("index", 0); put("function", buildJsonObject { put("arguments", "\"chat\",\"id\":\"1\"}") })
            }) }) },
        ).joinToString("") { delta -> "data: " + buildJsonObject {
            put("choices", buildJsonArray { add(buildJsonObject { put("delta", delta) }) })
        } + "\n\n" } + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}\n\ndata: [DONE]\n\n"
        HttpClient(MockEngine { respond(events, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream")) }).use { client ->
            val response = AiService(client).chatCompletionStreaming(emptyList(), "https://example.test/v1", "test-key", "model", onChunk = {})
            val message = assertNotNull(response, "a tool-only SSE round is a real response")["choices"]!!.jsonArray.single().jsonObject["message"]!!.jsonObject
            assertEquals("思考", message["reasoning_content"]!!.jsonPrimitive.content)
            val call = message["tool_calls"]!!.jsonArray.single().jsonObject
            assertEquals("call-1", call["id"]!!.jsonPrimitive.content)
            assertEquals("read_record", call["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
            assertEquals("{\"source\":\"chat\",\"id\":\"1\"}", call["function"]!!.jsonObject["arguments"]!!.jsonPrimitive.content)
        }
    }
}

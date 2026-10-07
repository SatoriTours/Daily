package com.dailysatori.service.ideatopic

import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiConversationSessionStore
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.security.SecretValueCipher
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Optional real-API smoke test for the topic chat / summary / draft path.
 * Run with: DAILY_AI_LIVE_TEST=1 ./gradlew :shared:testDebugUnitTest --tests '*IdeaTopicAiLiveTest' --rerun
 * Secrets stay local, are never printed and never stored in the artifact.
 */
class IdeaTopicAiLiveTest {

    @Test
    fun realAiChatSummaryAndDraftStayBoundedAndDoNotOverwriteContent() = runBlocking {
        assumeTrue("Opt in to real idea-topic validation", System.getenv("DAILY_AI_LIVE_TEST") == "1")
        val file = File("../.local/ai-test.json")
        require(file.isFile) { "Missing .local/ai-test.json" }
        val config = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }
            .getOrElse { error("Invalid AI test configuration") }
        fun required(key: String) = config[key]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
            ?: error("Missing AI test field: $key")

        val fixture = IdeaTopicTestFixture()
        val client = HttpClient(OkHttp) {
            install(HttpTimeout) { requestTimeoutMillis = 90_000; connectTimeoutMillis = 15_000 }
        }
        try {
            val aiConfigRepository = AIConfigRepository(fixture.db, TestCipher)
            aiConfigRepository.insert(
                provider = required("provider"),
                apiAddress = required("apiAddress"),
                apiToken = required("apiToken"),
                modelName = required("modelName"),
                isDefault = 1L,
            )
            val port = IdeaTopicAiService(
                aiService = AiService(client),
                aiConfigService = AiConfigService(aiConfigRepository),
                sessionStore = AiConversationSessionStore(SettingRepository(fixture.db)),
            )
            runScenario(fixture, port) { key -> required(key) }
        } finally {
            client.close()
            fixture.close()
        }
    }

    private suspend fun runScenario(
        fixture: IdeaTopicTestFixture,
        port: IdeaTopicAiService,
        required: (String) -> String,
    ) {
        val workflow = IdeaTopicAiWorkflow(fixture.service, port, newId = { fixture.nextId("live") })
        val diaryId = fixture.createDiary("想做一个把日记点子持续研究下去的小工具，先手动收录再和 AI 讨论。")
        val topicId = fixture.service.capture(
            IdeaCaptureInput(
                source = diarySnapshot(
                    diaryId = diaryId,
                    title = "点子闭环",
                    content = "想做一个把日记点子持续研究下去的小工具，先手动收录再和 AI 讨论。",
                ),
                content = IdeaTopicContent(title = "点子闭环小工具", description = "先跑通手动收录与 AI 沟通"),
            ),
        ).topicId
        val sessionId = fixture.service.createSession(topicId, "第一次沟通")
        val beforeContent = assertNotNull(fixture.service.getDetailSync(topicId)).topic.content

        workflow.send(sessionId, "这个点子最小的可用版本应该包含什么？请给 3 条建议。")
        val reply = fixture.service.messagesSync(sessionId)
            .last { it.role == IdeaMessageRoles.Assistant }
        assertTrue(reply.content.isNotBlank(), "real chat reply must not be empty")
        assertEquals(IdeaMessageStatus.Complete, reply.status)

        workflow.summarize(sessionId)
        val summary = fixture.service.sessionOrThrow(sessionId)
        assertTrue(summary.summary.isNotBlank(), "real summary must not be empty")
        val completedIds = fixture.service.messagesSync(sessionId)
            .filter { it.status == IdeaMessageStatus.Complete }
            .map { it.id }
        assertTrue(summary.summaryCoveredMessageIds.isNotEmpty())
        assertTrue(summary.summaryCoveredMessageIds.all { it in completedIds })
        assertEquals(summary.summaryCoveredMessageIds.last(), summary.summaryThroughMessageId)

        val draft = try {
            workflow.propose(topicId)
        } catch (failure: IdeaTopicException) {
            File("../.local/idea-topic-draft-failure.txt").apply {
                writeText(port.lastRawDraftResponse.orEmpty())
                setReadable(false, false); setWritable(false, false)
                setReadable(true, true); setWritable(true, true)
            }
            throw failure
        }
        assertEquals(IdeaDraftState.Pending, draft.state)
        assertTrue(draft.proposal.content.title.isNotBlank())
        val allowed = fixture.service.getDetailSync(topicId)!!.let { detail ->
            buildSet {
                add(detail.topic.id)
                detail.sources.forEach { add(it.id) }
                detail.events.forEach { add(it.id) }
                detail.sessions.forEach { add(it.id) }
            }
        }
        assertTrue(draft.proposal.referenceIds.all { it in allowed }, "real draft must only reference this topic")
        assertEquals(beforeContent, fixture.service.getDetailSync(topicId)!!.topic.content, "draft must not overwrite content")

        val artifact = File("../.local/idea-topic-live-results.json")
        artifact.writeText(
            Json.encodeToString(
                buildJsonObject {
                    put("provider", required("provider"))
                    put("model", required("modelName"))
                    put("replyLength", reply.content.length)
                    put("summaryLength", summary.summary.length)
                    put("coveredMessages", summary.summaryCoveredMessageIds.size)
                    put("draftTitle", draft.proposal.content.title)
                    put("draftReferenceCount", draft.proposal.referenceIds.size)
                },
            ),
        )
        artifact.setReadable(false, false)
        artifact.setWritable(false, false)
        artifact.setReadable(true, true)
        artifact.setWritable(true, true)
    }

    private object TestCipher : SecretValueCipher {
        override fun encrypt(value: String) = value
        override fun decrypt(value: String) = value
        override fun isEncrypted(value: String) = false
    }
}

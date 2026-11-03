package com.dailysatori.service.assistant

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.serialization.typeToken
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Offline compatibility gate: uses the actual released Koog runtime, not a simulated agent loop. */
class KoogCompatibilityTest {
    @Test
    fun releasedRuntimeExecutesAClassBasedToolAndReturnsItsResult() = runBlocking {
        var toolExecutions = 0
        val lookup = object : SimpleTool<Unit>(typeToken<Unit>(), "lookup", "Read a local test record") {
            override suspend fun execute(args: Unit): String {
                toolExecutions++
                return "本地记录"
            }
        }
        val executor = object : PromptExecutor() {
            override fun close() = Unit
            override suspend fun execute(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): Message.Assistant {
                val result = prompt.messages.flatMap { it.parts }.filterIsInstance<MessagePart.Tool.Result>().lastOrNull()
                return if (result == null) {
                    assertTrue(tools.any { it.name == "lookup" })
                    Message.Assistant(MessagePart.Tool.Call("tool-1", "lookup", "{}"), ResponseMetaInfo.Empty)
                } else {
                    Message.Assistant(result.output, ResponseMetaInfo.Empty)
                }
            }

            override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> = flowOf(StreamFrame.TextDelta("片段"))
            override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
                error("Moderation is not used by this agent")
        }
        val agent = AIAgent(
            promptExecutor = executor,
            strategy = ai.koog.agents.core.agent.singleRunStrategy(),
            llmModel = LLModel(LLMProvider.OpenAI, "offline-test", listOf(LLMCapability.Tools)),
            toolRegistry = ToolRegistry { tool(lookup) },
            maxIterations = 10
        )
        try {
            assertEquals("本地记录", agent.run("读取本地记录"))
            assertEquals(1, toolExecutions)
        } finally {
            agent.close()
        }
    }
}

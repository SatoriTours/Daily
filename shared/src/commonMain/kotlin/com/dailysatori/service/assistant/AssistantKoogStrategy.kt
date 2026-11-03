package com.dailysatori.service.assistant

import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import kotlinx.serialization.Serializable

/** Each tool is a Koog graph step with serializable input, not an application-owned agent loop. */
internal fun assistantKoogStrategy() = strategy<String, String>("daily_assistant_steps_v1") {
    val ask by node<String, AssistantModelRound>("ask_model") { input ->
        llm.writeSession {
            appendPrompt { user(input) }
            AssistantModelRound(requestLLM(), 1)
        }
    }
    val prepare by node<AssistantModelRound, AssistantToolBatch>("prepare_tools") { round ->
        val calls = round.response.parts.filterIsInstance<MessagePart.Tool.Call>()
        require(calls.size in 1..16) { "Tool batch exceeds the host budget" }
        AssistantToolBatch(calls, emptyList(), round.number)
    }
    val executeOne by node<AssistantToolBatch, AssistantToolBatch>("execute_one_tool") { state ->
        val result = environment.executeTool(state.remaining.first()).toMessagePart()
        state.copy(remaining = state.remaining.drop(1), results = state.results + result)
    }
    val send by node<AssistantToolBatch, AssistantModelRound>("send_results") { state ->
        llm.writeSession {
            appendPrompt { user { state.results.forEach { toolResult(it) } } }
            AssistantModelRound(requestLLM(), state.round + 1)
        }
    }
    val deliver by node<AssistantModelRound, String>("deliver_answer") { it.answer }
    val exhausted by node<AssistantModelRound, String>("budget_exhausted") { BUDGET_MESSAGE }
    edge(deliver forwardTo nodeFinish)
    edge(exhausted forwardTo nodeFinish)
    edge(nodeStart forwardTo ask)
    edge(ask forwardTo prepare onCondition { it.hasTools && it.number <= 5 })
    edge(ask forwardTo deliver onCondition { !it.hasTools })
    edge(ask forwardTo exhausted onCondition { it.hasTools && it.number > 5 })
    edge(prepare forwardTo executeOne)
    edge(executeOne forwardTo executeOne onCondition { it.remaining.isNotEmpty() })
    edge(executeOne forwardTo send onCondition { it.remaining.isEmpty() })
    edge(send forwardTo prepare onCondition { it.hasTools && it.number <= 5 })
    edge(send forwardTo deliver onCondition { !it.hasTools })
    edge(send forwardTo exhausted onCondition { it.hasTools && it.number > 5 })
}

@Serializable
internal data class AssistantModelRound(val response: Message.Assistant, val number: Int) {
    val hasTools get() = response.parts.any { it is MessagePart.Tool.Call }
    val answer get() = response.textContent()
}

@Serializable
internal data class AssistantToolBatch(
    val remaining: List<MessagePart.Tool.Call>, val results: List<MessagePart.Tool.Result>, val round: Int,
)

private const val BUDGET_MESSAGE = "已达到本轮处理预算，未继续调用工具。原文与已经执行的结果已保留，请查看关联任务；尚未完成的部分可以继续询问。"

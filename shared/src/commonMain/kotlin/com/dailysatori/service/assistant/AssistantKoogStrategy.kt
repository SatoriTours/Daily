package com.dailysatori.service.assistant

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.environment.executeTool
import ai.koog.prompt.message.Message
import kotlinx.serialization.Serializable

/** Each tool is a Koog graph step with serializable input, not an application-owned agent loop. */
internal fun assistantKoogStrategy() = strategy<String, String>("daily_assistant_steps_v1") {
    val ask by node<String, AssistantModelRound>("ask_model") { input ->
        llm.writeSession {
            updatePrompt { user(input) }
            AssistantModelRound(requestLLMMultiple(), 1)
        }
    }
    val prepare by node<AssistantModelRound, AssistantToolBatch>("prepare_tools") { round ->
        val calls = round.responses.filterIsInstance<Message.Tool.Call>()
        require(calls.size in 1..16) { "Tool batch exceeds the host budget" }
        AssistantToolBatch(calls, emptyList(), round.number)
    }
    val executeOne by node<AssistantToolBatch, AssistantToolBatch>("execute_one_tool") { state ->
        val result = environment.executeTool(state.remaining.first()).toMessage()
        state.copy(remaining = state.remaining.drop(1), results = state.results + result)
    }
    val send by node<AssistantToolBatch, AssistantModelRound>("send_results") { state ->
        llm.writeSession {
            updatePrompt { tool { state.results.forEach { result(it) } } }
            AssistantModelRound(requestLLMMultiple(), state.round + 1)
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
internal data class AssistantModelRound(val responses: List<Message.Response>, val number: Int) {
    val hasTools get() = responses.any { it is Message.Tool.Call }
    val answer get() = responses.filterIsInstance<Message.Assistant>().joinToString("\n") { it.content }
}

@Serializable
internal data class AssistantToolBatch(
    val remaining: List<Message.Tool.Call>, val results: List<Message.Tool.Result>, val round: Int,
)

private const val BUDGET_MESSAGE = "已达到本轮处理预算，未继续调用工具。原文与已经执行的结果已保留，请查看关联任务；尚未完成的部分可以继续询问。"

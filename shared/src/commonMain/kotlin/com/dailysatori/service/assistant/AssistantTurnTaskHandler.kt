package com.dailysatori.service.assistant

import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.service.asynctask.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Read-only durable slice. Business write tools must acquire stable action identities before adoption. */
class AssistantTurnTaskHandler(
    private val turns: AssistantTurnRepository,
    private val tasks: AsyncTaskRepository,
    private val generate: suspend (AssistantTurn) -> String,
) : AsyncTaskHandler {
    private var runtime: KoogAssistantRuntime? = null
    constructor(turns: AssistantTurnRepository, tasks: AsyncTaskRepository, runtime: KoogAssistantRuntime) :
        this(turns, tasks, { error("Persistent runtime required") }) { this.runtime = runtime }

    override val type = "assistant_turn"

    override suspend fun execute(
        taskId: Long,
        payloadJson: String,
        checkpointJson: String,
        reporter: AsyncTaskProgressReporter,
    ): AsyncTaskExecutionResult {
        val payload = runCatching { Json.decodeFromString<TurnPayload>(payloadJson) }.getOrNull()
        val turn = payload?.turnId?.let(turns::get)
        if (payload?.version != 1 || turn == null || turn.taskId != taskId) {
            return AsyncTaskExecutionResult.PermanentFailure("assistant_invalid_turn", "助手任务引用无效")
        }
        val owner = reporter.executionLease?.takeIf { it.taskId == taskId }?.owner
        if (owner == null || !tasks.hasRunningLease(taskId, owner) || !turns.canExecute(turn, owner)) return unavailable()
        val privacyRevision = turns.privacyRevision()
        return try {
            runtime?.resume(turn, owner)
            if (turn.responseMessageId != null) return success(turn)
            reporter.report(0, 1, "正在处理已保存的输入", checkpointJson)
            val answer = runtime?.generate(turn, owner) ?: generate(turn)
            if (turns.complete(turn.id, owner, answer, privacyRevision)) success(turn)
            else if (turns.get(turn.id)?.let { turns.canExecute(it, owner) } == true)
                AsyncTaskExecutionResult.RetryableFailure("assistant_context_changed", "记忆或隐私范围已改变，正在基于当前资料重新生成")
            else unavailable()
        } catch (cancelled: CancellationException) {
            if (turns.get(turn.id)?.let { turns.canExecute(it, owner) } != true) unavailable()
            else if (cancelled is AssistantContextChangedException || turns.privacyRevision() != privacyRevision)
                AsyncTaskExecutionResult.RetryableFailure("assistant_context_changed", "资料已改变，正在基于当前原文重新生成")
            else throw cancelled
        } catch (_: Exception) {
            // Provider errors may contain credentials or original content. Never persist the exception message.
            AsyncTaskExecutionResult.RetryableFailure("assistant_generation_failed", "助手处理失败，原始输入已保存")
        }
    }

    @Serializable
    private data class TurnPayload(val version: Int, val turnId: Long)

    private fun unavailable() = AsyncTaskExecutionResult.PermanentFailure(
        "assistant_input_unavailable", "输入已删除、排除、取消或等待恢复确认",
    )

    private fun success(turn: AssistantTurn) = AsyncTaskExecutionResult.Success("{\"turnId\":${turn.id}}")
}

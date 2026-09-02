package com.dailysatori.core.task

import com.dailysatori.data.repository.ReminderAiBatchRepository
import com.dailysatori.core.reminder.NoOpReminderAiParseNotifier
import com.dailysatori.core.reminder.ReminderAiParseNotifier
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskHandler
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.asynctask.reminderAiRetryDecision
import com.dailysatori.service.reminder.ReminderAiBatchDraft
import com.dailysatori.service.reminder.ReminderAiBatchResponseException
import com.dailysatori.service.reminder.ReminderBatchCodec
import com.dailysatori.service.reminder.ReminderInputFragment
import com.dailysatori.service.reminder.ReminderInterpretationRemote
import com.dailysatori.service.reminder.TimedReminderInterpretationRemote
import com.dailysatori.service.reminder.splitReminderInput
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ReminderAiParseTaskPayload(val batchId: String)

class ReminderAiParseTaskHandler(
    private val batchRepository: ReminderAiBatchRepository,
    private val remote: ReminderInterpretationRemote,
    private val batchCodec: ReminderBatchCodec,
    private val clock: Clock = Clock.System,
    private val notifier: ReminderAiParseNotifier = NoOpReminderAiParseNotifier,
) : AsyncTaskHandler {
    override val type: String = TYPE

    override suspend fun execute(
        taskId: Long,
        payloadJson: String,
        checkpointJson: String,
        reporter: AsyncTaskProgressReporter,
    ): AsyncTaskExecutionResult {
        val batchId = runCatching { Json.decodeFromString<ReminderAiParseTaskPayload>(payloadJson).batchId }
            .getOrNull()?.takeIf { it.isNotBlank() }
            ?: return AsyncTaskExecutionResult.PermanentFailure("invalid_payload", "提醒解析任务参数无效")
        val batch = batchRepository.getBatch(batchId)
            ?: return AsyncTaskExecutionResult.PermanentFailure("batch_missing", "提醒解析请求不存在")
        val attempt = batch.attemptCount + 1
        if (!batchRepository.markRunning(batchId, taskId, attempt)) {
            notifyTerminalIfNeeded(batchId)
            return AsyncTaskExecutionResult.Success()
        }

        val startedMs = clock.now().toEpochMilliseconds()
        reporter.report(0, 1, "准备解析提醒", checkpoint(startedMs - batch.createdAt.toEpochMilliseconds()))
        val fragments = splitReminderInput(batch.originalInput)
        if (fragments.isEmpty()) return fail(batchId, IllegalArgumentException("Reminder text is empty"), attempt, reporter, startedMs, batch.createdAt.toEpochMilliseconds())

        val timed = remoteCall(fragments, batch.timeZone)
        val queueWait = startedMs - batch.createdAt.toEpochMilliseconds()
        timed.error?.let { return fail(batchId, it, attempt, reporter, startedMs, batch.createdAt.toEpochMilliseconds(), timed.configMs, timed.requestMs) }
        val response = requireNotNull(timed.response)
        reporter.report(0, 1, "正在校验 AI 返回", checkpoint(queueWait, timed.configMs, timed.requestMs))
        if (response.isBlank()) return fail(batchId, IllegalStateException("AI returned an empty response"), attempt, reporter, startedMs, batch.createdAt.toEpochMilliseconds(), timed.configMs, timed.requestMs)

        val decoded = try {
            decodeStrict(response, fragments, batch.timeZone)
        } catch (error: Throwable) {
            return fail(batchId, error, attempt, reporter, startedMs, batch.createdAt.toEpochMilliseconds(), timed.configMs, timed.requestMs)
        }
        val decodeMs = clock.now().toEpochMilliseconds() - startedMs - timed.configMs - timed.requestMs
        val persistStarted = clock.now().toEpochMilliseconds()
        val persisted = batchRepository.markReady(batchId, decoded.map { draft ->
            val source = fragments.first { it.index == draft.sourceIndex }
            ReminderAiBatchDraft(draft.sourceIndex, source.text, batchCodec.encode(draft))
        })
        val persistMs = clock.now().toEpochMilliseconds() - persistStarted
        if (!persisted) {
            notifyTerminalIfNeeded(batchId)
            return AsyncTaskExecutionResult.Success()
        }
        notifyTerminalIfNeeded(batchId)
        reporter.report(1, 1, "提醒解析完成", checkpoint(queueWait, timed.configMs, timed.requestMs, decodeMs, persistMs))
        return AsyncTaskExecutionResult.Success("{\"batchId\":\"$batchId\"}")
    }

    private fun decodeStrict(response: String, fragments: List<ReminderInputFragment>, zone: kotlinx.datetime.TimeZone) =
        batchCodec.decode(response, zone).let { decoded ->
            val expected = fragments.map { it.index }.toSet()
            val indexes = decoded.drafts.map { it.sourceIndex }
            val duplicate = indexes.groupingBy { it }.eachCount().keys.firstOrNull { indexes.count { value -> value == it } > 1 }
            val error = when {
                decoded.failure != null -> decoded.failure
                duplicate != null -> "Batch response contains duplicate source_index $duplicate"
                indexes.any { it !in expected } -> "Batch response contains out-of-range source_index"
                indexes.toSet() != expected -> "Batch response is missing source_index"
                decoded.drafts.any { it.draft.validationErrors.isNotEmpty() } -> "Batch response contains invalid reminder fields"
                else -> null
            }
            if (error != null) throw ReminderAiBatchResponseException(error)
            decoded.drafts
        }

    override suspend fun onExecutionTimeout(taskId: Long, payloadJson: String, checkpointJson: String, reporter: AsyncTaskProgressReporter): AsyncTaskExecutionResult? {
        val batchId = runCatching { Json.decodeFromString<ReminderAiParseTaskPayload>(payloadJson).batchId }.getOrNull() ?: return null
        val batch = batchRepository.getBatch(batchId) ?: return null
        val now = clock.now().toEpochMilliseconds()
        return fail(batchId, IllegalStateException("提醒 AI 解析超时"), batch.attemptCount, reporter, now, batch.createdAt.toEpochMilliseconds())
    }

    private suspend fun fail(
        batchId: String, error: Throwable, attempt: Long, reporter: AsyncTaskProgressReporter,
        startedMs: Long, createdAtMs: Long, configMs: Long = 0, requestMs: Long = 0,
    ): AsyncTaskExecutionResult {
        reporter.report(0, 1, "提醒解析失败", checkpoint(startedMs - createdAtMs, configMs, requestMs))
        val decision = reminderAiRetryDecision(error, attempt)
        if (decision.permanent) {
            batchRepository.markFailed(batchId, decision.message)
            notifyTerminalIfNeeded(batchId)
        }
        return if (decision.permanent) {
            AsyncTaskExecutionResult.PermanentFailure(decision.code, decision.message)
        } else {
            AsyncTaskExecutionResult.RetryableFailure(
                decision.code,
                decision.message,
                decision.retryDelayMs?.let { clock.now().toEpochMilliseconds() + it },
            )
        }
    }

    private suspend fun remoteCall(fragments: List<ReminderInputFragment>, zone: kotlinx.datetime.TimeZone) =
        (remote as? TimedReminderInterpretationRemote)?.interpretBatchTimed(fragments, clock.now(), zone)
            ?: run {
                val started = clock.now().toEpochMilliseconds()
                runCatching { remote.interpretBatch(fragments, clock.now(), zone) }.fold(
                    onSuccess = { com.dailysatori.service.reminder.ReminderBatchRemoteTiming(0, clock.now().toEpochMilliseconds() - started, response = it) },
                    onFailure = { com.dailysatori.service.reminder.ReminderBatchRemoteTiming(0, clock.now().toEpochMilliseconds() - started, error = it) },
                )
            }

    private fun checkpoint(queueWaitMs: Long, configMs: Long = 0, requestMs: Long = 0, decodeMs: Long = 0, persistMs: Long = 0) =
        "{\"queue_wait_ms\":${queueWaitMs.coerceAtLeast(0)},\"config_ms\":${configMs.coerceAtLeast(0)},\"request_ms\":${requestMs.coerceAtLeast(0)},\"decode_ms\":${decodeMs.coerceAtLeast(0)},\"persist_ms\":${persistMs.coerceAtLeast(0)}}"

    private fun notifyTerminalIfNeeded(batchId: String) {
        val ready = when (batchRepository.getBatch(batchId)?.status) {
            com.dailysatori.service.reminder.ReminderAiBatchStatus.READY_FOR_CONFIRMATION -> true
            com.dailysatori.service.reminder.ReminderAiBatchStatus.PARSE_FAILED -> false
            else -> return
        }
        if (!batchRepository.claimTerminalNotification(batchId)) return
        if (ready) notifier.notifyReady(batchId) else notifier.notifyFailed(batchId)
    }

    companion object { const val TYPE = "reminder_ai_parse" }
}

fun reminderAiParseTaskPayloadJson(batchId: String): String = Json.encodeToString(ReminderAiParseTaskPayload(batchId))

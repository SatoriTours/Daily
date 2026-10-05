package com.dailysatori.core.task

import com.dailysatori.core.reminder.ReminderCoordinator
import com.dailysatori.core.sms.SmsPendingNotifier
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.SmsSourceRepository
import com.dailysatori.service.asynctask.*
import com.dailysatori.service.sms.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

class SmsReminderTaskHandler(
    private val service: SmsReminderService, private val sources: SmsSourceRepository,
    private val tasks: AsyncTaskRepository, private val coordinator: ReminderCoordinator,
    private val notifier: SmsPendingNotifier,
) : AsyncTaskHandler {
    override val type = "sms_reminder_parse"
    override suspend fun onExecutionTimeout(taskId: Long, payloadJson: String, checkpointJson: String, reporter: AsyncTaskProgressReporter): AsyncTaskExecutionResult {
        val id = runCatching { Json.parseToJsonElement(payloadJson).jsonObject["sourceId"]?.jsonPrimitive?.content }.getOrNull()
        if ((tasks.getById(taskId)?.attempt_count ?: 0) >= 3) {
            if (id != null) finishLocally(id)
            return AsyncTaskExecutionResult.Success()
        }
        return AsyncTaskExecutionResult.RetryableFailure("sms_timeout", "短信处理超时，稍后重试", 30_000)
    }
    override suspend fun execute(taskId: Long, payloadJson: String, checkpointJson: String, reporter: AsyncTaskProgressReporter): AsyncTaskExecutionResult {
        val id = runCatching { Json.parseToJsonElement(payloadJson).jsonObject["sourceId"]?.jsonPrimitive?.content }.getOrNull()
            ?: return AsyncTaskExecutionResult.PermanentFailure("invalid_sms", "短信任务参数无效")
        return try {
            reporter.report(0, 1, "正在处理已脱敏的短信")
            val row = service.process(id)
            when (row?.status) {
                SmsSourceStatus.CREATED -> { notifier.cancel(id); row.reminderId?.let(coordinator::recompute) }
                else -> Unit
            }
            AsyncTaskExecutionResult.Success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            if ((tasks.getById(taskId)?.attempt_count ?: 0) >= 3) {
                finishLocally(id)
                AsyncTaskExecutionResult.Success()
            } else AsyncTaskExecutionResult.RetryableFailure("sms_retry", "短信处理暂未完成，稍后重试", 30_000)
        }
    }

    private fun finishLocally(id: String) {
        val row = sources.get(id) ?: return
        if (row.status in setOf(SmsSourceStatus.QUEUED, SmsSourceStatus.FAILED)) service.createLocal(id)
        sources.get(id)?.reminderId?.let(coordinator::recompute)
        notifier.cancel(id)
    }
}

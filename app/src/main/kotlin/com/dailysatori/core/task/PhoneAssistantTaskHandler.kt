package com.dailysatori.core.task

import com.dailysatori.core.reminder.ReminderCoordinator
import com.dailysatori.service.asynctask.*
import com.dailysatori.service.phone.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

class PhoneAssistantTaskHandler(private val service: PhoneAssistantService, private val coordinator: ReminderCoordinator) : AsyncTaskHandler {
    override val type = "phone_assistant_process"
    override suspend fun execute(taskId: Long, payloadJson: String, checkpointJson: String,
        reporter: AsyncTaskProgressReporter): AsyncTaskExecutionResult {
        val id = messageId(payloadJson) ?: return AsyncTaskExecutionResult.PermanentFailure("invalid_phone_message", "信息参数无效")
        return try {
            reporter.report(0, 1, "正在识别手机信息")
            service.process(id)?.todos?.map { it.reminderId }?.filter(String::isNotEmpty)?.forEach(coordinator::recompute)
            AsyncTaskExecutionResult.Success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            service.markFailed(id)
            AsyncTaskExecutionResult.Success()
        }
    }
    override suspend fun onExecutionTimeout(taskId: Long, payloadJson: String, checkpointJson: String,
        reporter: AsyncTaskProgressReporter): AsyncTaskExecutionResult {
        messageId(payloadJson)?.let { service.markFailed(it) }
        return AsyncTaskExecutionResult.Success()
    }
    private fun messageId(payload: String) = runCatching {
        Json.parseToJsonElement(payload).jsonObject["messageId"]?.jsonPrimitive?.content
    }.getOrNull()
}

package com.dailysatori.service.reminder

import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.ReminderAiBatchRepository
import com.dailysatori.service.asynctask.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/** Durable retry owns the lease; platform scheduling uses the existing stable reminder identity. */
class ReminderScheduleTaskHandler(
    private val batches:ReminderAiBatchRepository,
    private val tasks:AsyncTaskRepository,
    private val schedule:(String)->Unit,
):AsyncTaskHandler {
    override val type="reminder_schedule"
    override suspend fun execute(taskId:Long,payloadJson:String,checkpointJson:String,
        reporter:AsyncTaskProgressReporter):AsyncTaskExecutionResult {
        val intent=runCatching { Json.decodeFromString<ReminderScheduleIntent>(payloadJson) }.getOrNull()
            ?: return AsyncTaskExecutionResult.PermanentFailure("invalid_payload","提醒调度任务参数无效")
        val lease=reporter.executionLease ?: return AsyncTaskExecutionResult.PermanentFailure("missing_lease","提醒调度任务缺少执行租约")
        val begin=tasks.withRunningLease(taskId,lease.owner) { batches.beginSchedule(intent.reminderId,intent.version) }
        if(begin!=true) return AsyncTaskExecutionResult.Success()
        try {
            schedule(intent.reminderId)
        } catch(cancelled:CancellationException) { throw cancelled }
        catch(failure:Exception) {
            tasks.withRunningLease(taskId,lease.owner) { batches.finishSchedule(intent.reminderId,intent.version,false) }
            return AsyncTaskExecutionResult.RetryableFailure("schedule_failed","提醒调度失败，请重试")
        }
        tasks.withRunningLease(taskId,lease.owner) { batches.finishSchedule(intent.reminderId,intent.version,true) }
        return AsyncTaskExecutionResult.Success()
    }
}

package com.dailysatori.core.task

import com.dailysatori.service.diagnostics.TaskHttpRecord
import com.dailysatori.service.diagnostics.TaskHttpRecordCodec
import com.dailysatori.service.diagnostics.TaskHttpSink

/**
 * Renders already-sanitized capture records into the per-task HTTP file. Nothing here may re-add raw
 * content: bodies arrive redacted and truncated by the capture session.
 */
class TaskHttpRecordSink(private val store: AsyncTaskLogStore) : TaskHttpSink {
    override fun write(taskId: Long, label: String, record: TaskHttpRecord) {
        store.appendHttp(taskId, TaskHttpRecordCodec.encode(record, taskId = taskId, label = label))
    }
}

package com.dailysatori.core.recording

import kotlinx.coroutines.Deferred

class DiaryRecordingRuntimeLease internal constructor(
    internal val runtime: DiaryRecordingRuntime,
    internal val attachment: DiaryRecordingHostAttachment,
)

class DiaryRecordingRuntimeManager(
    private val createRuntime: (onClosed: () -> Unit) -> DiaryRecordingRuntime,
) {
    private val lock = Any()
    private var current: DiaryRecordingRuntime? = null

    fun attachHost(host: DiaryRecordingAndroidHost): DiaryRecordingRuntimeLease = synchronized(lock) {
        val runtime = current?.takeUnless { it.isClosing() }
            ?: createManagedRuntime().also { current = it }
        DiaryRecordingRuntimeLease(runtime, runtime.attachHost(host))
    }

    fun detachHost(lease: DiaryRecordingRuntimeLease) {
        lease.runtime.detachHost(lease.attachment)
    }

    fun submit(
        lease: DiaryRecordingRuntimeLease,
        startId: Int,
        command: DiaryRecordingCommand,
    ): Deferred<DiaryRecordingCommandResult> =
        lease.runtime.submit(lease.attachment, startId, command)

    private fun createManagedRuntime(): DiaryRecordingRuntime {
        lateinit var runtime: DiaryRecordingRuntime
        runtime = createRuntime {
            synchronized(lock) {
                if (current === runtime) current = null
            }
        }
        return runtime
    }
}

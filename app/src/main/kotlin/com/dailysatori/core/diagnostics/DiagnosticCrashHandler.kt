package com.dailysatori.core.diagnostics

import com.dailysatori.service.diagnostics.*
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/** Emergency channel: no actor lock, database, coroutine drain or dependency injection. */
class DiagnosticCrashHandler(
    private val root: File,
    private val previous: Thread.UncaughtExceptionHandler,
    now: () -> Long = System::currentTimeMillis,
) : Thread.UncaughtExceptionHandler {
    private val writing = AtomicBoolean()
    private val emergency = Diagnostics(DiagnosticSink { event ->
        check(root.mkdirs() || root.isDirectory)
        val bytes = diagnosticJson.encodeToString(SafeDiagnosticEvent.serializer(), event).toByteArray()
        check(bytes.size <= 16 * 1024)
        val temp = File(root, "crash-pending.tmp")
        FileOutputStream(temp).use { it.write(bytes); it.fd.sync() }
        check(temp.renameTo(File(root, "crash-pending.json")))
        true
    }, now)

    override fun uncaughtException(thread: Thread, error: Throwable) {
        try {
            if (writing.compareAndSet(false, true)) {
                // 仅对已知的前台服务停止超时异常附加静态白名单属性；原始消息仍然只用于匹配、不入库。
                emergency.emit(
                    DiagnosticCode.CRASH, DiagnosticSource.APP, DiagnosticLevel.ERROR,
                    fields = ForegroundStopDiagnostic.attributes(error.javaClass.name, error.message),
                    error = error,
                )
            }
        } finally { previous.uncaughtException(thread, error) }
    }
}

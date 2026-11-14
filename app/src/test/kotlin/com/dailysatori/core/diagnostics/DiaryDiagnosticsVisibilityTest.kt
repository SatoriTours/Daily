package com.dailysatori.core.diagnostics

import com.dailysatori.service.diagnostics.DiagnosticCode
import com.dailysatori.service.diagnostics.DiagnosticSource
import com.dailysatori.service.diagnostics.Diagnostics
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** 思想整理阶段：必须用白名单键值真正落盘，不能用被丢弃的键冒充“已记录”。 */
class DiaryDiagnosticsVisibilityTest {
    @Test
    fun observationFailureStageIsPersistedWithWhitelistedKey() = runBlocking<Unit> {
        val root = Files.createTempDirectory("diag-diary-stage").toFile()
        var time = 2_000_000L
        DiagnosticStore(root, clock = { time }).use { store ->
            val log = Diagnostics(store, now = { time })
            log.emit(
                DiagnosticCode.OPERATION_FAILED, DiagnosticSource.DIARY,
                fields = mapOf("diaryStage" to "sources_observation_failed"),
            )
            log.emit(
                DiagnosticCode.OPERATION_PROGRESS, DiagnosticSource.DIARY,
                fields = mapOf("stage" to "sources_observation_failed"),
            )
            log.emit(
                DiagnosticCode.OPERATION_END, DiagnosticSource.TASK,
                fields = mapOf("workerOutcome" to "sources_not_ready", "foregroundWorker" to "diary_thought"),
            )
            store.snapshot(time).use { snapshot ->
                val output = java.io.ByteArrayOutputStream()
                DiagnosticExporter("5.1.66", 175117, 37).write(snapshot, output)
                val text = output.toString("UTF-8")
                assertTrue(text.contains("sources_observation_failed"), "观察失败阶段必须可见")
                assertTrue(text.contains("\"diaryStage\":\"sources_observation_failed\""))
                assertTrue(text.contains("\"workerOutcome\":\"sources_not_ready\""), "来源未就绪必须可见")
                assertTrue(text.contains("\"foregroundWorker\":\"diary_thought\""))
                assertFalse(text.contains("\"stage\":"), "未白名单键不得进入导出")
            }
        }
        root.deleteRecursively()
    }
}

package com.dailysatori.core.worker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class AsyncTaskWorkerSourceTest {
    @Test
    fun retryableHandlerExceptionsUseSharedMaxAttemptGate() {
        val source = File("src/main/kotlin/com/dailysatori/core/worker/AsyncTaskWorker.kt").readText()
        val helper = source.substringAfter("private fun handleRetryableFailure(").substringBefore("companion object")
        val cancellationBlock = source.substringAfter("catch (error: CancellationException)").substringBefore("catch (error: Exception)")
        val exceptionBlock = source.substringAfter("catch (error: Exception)").substringBefore("companion object")

        assertTrue(source.contains("private fun handleRetryableFailure("))
        assertTrue(helper.contains("attempts + 1 >= task.max_attempts"))
        assertTrue(helper.contains("repo.finishFailure(taskId, code, message)"))
        assertTrue(cancellationBlock.contains("handleRetryableFailure("))
        assertTrue(exceptionBlock.contains("handleRetryableFailure("))
    }
}

package com.dailysatori.core.worker

import androidx.work.ListenableWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class BackupWorkerTest {
    @Test
    fun failedBackupIsRetriedInsteadOfReportingSuccess() = runBlocking {
        assertEquals(ListenableWorker.Result.retry(), runBackupWork { false })
    }

    @Test
    fun completedBackupReportsSuccess() = runBlocking {
        assertEquals(ListenableWorker.Result.success(), runBackupWork { true })
    }

    @Test
    fun writeExceptionIsRetried() = runBlocking {
        assertEquals(ListenableWorker.Result.retry(), runBackupWork { error("disk full") })
    }

    @Test
    fun workerCancellationIsPropagated() = runBlocking {
        assertFailsWith<CancellationException> { runBackupWork { throw CancellationException("cancelled") } }
        Unit
    }
}

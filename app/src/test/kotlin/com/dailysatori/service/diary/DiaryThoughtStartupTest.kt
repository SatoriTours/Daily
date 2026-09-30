package com.dailysatori.service.diary

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.DiaryThoughtRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlin.test.*

class DiaryThoughtStartupTest {
    @Test
    fun startupReadFailureIsReportedWithoutCrashingOrDeletingDiaries() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        val db = DailySatoriDatabase(driver)
        val repository = DiaryRepository(db, driver)
        repository.insert("原有日记")
        driver.execute(null, "DROP TABLE setting", 0)
        val uncaught = CompletableDeferred<Throwable>()
        val applicationJob = SupervisorJob()
        val scope = CoroutineScope(applicationJob + Dispatchers.IO + CoroutineExceptionHandler { _, error -> uncaught.complete(error) })
        val service = DiaryThoughtService(repository, DiaryThoughtRepository(SettingRepository(db)),
            DiaryThoughtGenerator { _, _ -> error("不能调用 AI") })
        try {
            service.start(scope) { error("读取失败时不能调度任务") }
            val failureState = async { service.state.first { it.error != null } }
            val failed = withTimeout(5_000) {
                select {
                    uncaught.onAwait { fail("启动读取异常逃逸到应用协程：${it::class.simpleName}") }
                    failureState.onAwait { it }
                }
            }
            assertFalse(failed.isUpdating)
            assertFalse(failed.error.orEmpty().contains("SQL"))
            assertEquals(1L, repository.count())
            assertTrue(applicationJob.isActive)
        } finally {
            applicationJob.cancelAndJoin()
            driver.close()
        }
    }

    @Test
    fun schedulingFailureDoesNotEscapeApplicationScopeAndLaterRefreshStillWorks() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        val db = DailySatoriDatabase(driver)
        val repository = DiaryRepository(db, driver)
        repository.insert("保留用户原有日记")
        val uncaught = CompletableDeferred<Throwable>()
        val applicationJob = SupervisorJob()
        val scope = CoroutineScope(applicationJob + Dispatchers.IO + CoroutineExceptionHandler { _, error -> uncaught.complete(error) })
        val scheduled = Channel<Unit>(Channel.UNLIMITED)
        val service = DiaryThoughtService(repository, DiaryThoughtRepository(SettingRepository(db)),
            DiaryThoughtGenerator { _, _ -> error("启动订阅不应调用 AI") })
        try {
            var failScheduling = true
            service.start(scope) {
                if (failScheduling) throw IllegalStateException("调度不可用，private-user-content")
                scheduled.trySend(Unit)
            }
            val failureState = async { service.state.first { it.error != null } }
            val failed = withTimeout(5_000) {
                select {
                    uncaught.onAwait { fail("启动订阅异常逃逸到应用协程：${it::class.simpleName}") }
                    failureState.onAwait { it }
                }
            }
            assertFalse(failed.isUpdating)
            assertFalse(failed.error.orEmpty().contains("private-user-content"))
            assertEquals(1L, repository.count(), "失败不能删除用户记录")

            failScheduling = false
            service.requestRefresh()
            withTimeout(5_000) { scheduled.receive() }
            assertNull(service.state.value.error)
            assertTrue(service.state.value.isUpdating)
            assertFalse(uncaught.isCompleted)
        } finally {
            applicationJob.cancelAndJoin()
            scheduled.close()
            driver.close()
        }
    }
}

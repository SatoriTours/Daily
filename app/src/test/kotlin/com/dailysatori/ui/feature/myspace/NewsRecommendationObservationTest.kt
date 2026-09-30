package com.dailysatori.ui.feature.myspace

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.ReminderRepository
import com.dailysatori.core.util.withUiObservationError
import com.dailysatori.service.reminder.ReminderDraft
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.selects.select
import kotlin.test.*

class NewsRecommendationObservationTest {
    @Test
    fun taskQueryFailureDoesNotEscapeTheViewModelScopeOrEraseTheLastProgress() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        val repository = AsyncTaskRepository(DailySatoriDatabase(driver))
        val id = repository.enqueue("news_opportunity_analysis", "{}", uniqueKey = "news_opportunity_analysis")
        assertTrue(repository.claimForRun(id, "startup-test", Long.MAX_VALUE))
        repository.updateProgress(id, 3, 10, "已完成 3/10 篇", "{}")
        val uncaught = CompletableDeferred<Throwable>()
        val reported = CompletableDeferred<Unit>()
        val scopeJob = SupervisorJob()
        val scope = CoroutineScope(scopeJob + Dispatchers.IO + CoroutineExceptionHandler { _, error -> uncaught.complete(error) })
        try {
            val state = repository.observeLatestByUniqueKey("news_opportunity_analysis")
                .withUiObservationError { reported.complete(Unit) }
                .stateIn(scope, SharingStarted.Eagerly, null)
            withTimeout(5_000) { state.first { it?.id == id } }
            driver.execute(null, "ALTER TABLE async_task RENAME TO temporarily_unavailable_tasks", 0)
            driver.notifyListeners("async_task")
            withTimeout(5_000) {
                select {
                    uncaught.onAwait { fail("任务进度读取异常逃逸到 ViewModel 协程：${it::class.simpleName}") }
                    reported.onAwait { }
                }
            }
            assertEquals(id, state.value?.id)
            assertEquals(3L, state.value?.progress_current)
            assertEquals("已完成 3/10 篇", state.value?.progress_message)
            assertTrue(scopeJob.isActive)
            driver.execute(null, "ALTER TABLE temporarily_unavailable_tasks RENAME TO async_task", 0)
            assertEquals(id, repository.getById(id)?.id, "错误处理不能删除任务记录")
        } finally {
            scopeJob.cancelAndJoin()
            driver.close()
        }
    }

    @Test
    fun stoppingAnObservationStillPropagatesCancellationWithoutShowingFailure() = runBlocking {
        var reported = false
        val cancelled = CancellationException("页面离开")
        val failure = assertFailsWith<CancellationException> {
            flow<Int> { throw cancelled }.withUiObservationError { reported = true }.toList()
        }
        assertSame(cancelled, failure)
        assertFalse(reported)
    }

    @Test
    fun malformedStoredReminderCannotCrashTheHomeBadgeSubscription() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        val db = DailySatoriDatabase(driver)
        val repository = ReminderRepository(db, TimeZone.UTC)
        repository.createConfirmed(ReminderDraft("today", "原有提醒", LocalDate(2026, 9, 30),
            LocalDate(2026, 9, 30), LocalTime(9, 0), timeZone = TimeZone.UTC), ReminderProfileSnapshot.standard())
        val uncaught = CompletableDeferred<Throwable>()
        val reported = CompletableDeferred<Unit>()
        val scopeJob = SupervisorJob()
        val scope = CoroutineScope(scopeJob + Dispatchers.IO + CoroutineExceptionHandler { _, error -> uncaught.complete(error) })
        try {
            val state = repository.observeAll().withUiObservationError { reported.complete(Unit) }
                .stateIn(scope, SharingStarted.Eagerly, emptyList())
            withTimeout(5_000) { state.first { it.any { item -> item.id == "today" } } }
            driver.execute(null, "UPDATE reminder SET recurrence_rule = 'monthly:0' WHERE id = 'today'", 0)
            driver.notifyListeners("reminder")
            withTimeout(5_000) {
                select {
                    uncaught.onAwait { fail("旧提醒读取异常逃逸到首页协程：${it::class.simpleName}") }
                    reported.onAwait { }
                }
            }
            assertEquals("today", state.value.single().id)
            assertEquals("monthly:0", db.dailySatoriQueries.selectReminderById("today").executeAsOne().recurrence_rule)
            assertTrue(scopeJob.isActive)
        } finally {
            scopeJob.cancelAndJoin()
            driver.close()
        }
    }
}

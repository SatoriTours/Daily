package com.dailysatori.core.worker

import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.dailysatori.service.unifiednews.NextUnifiedNewsWindow
import com.dailysatori.service.unifiednews.UnifiedNewsWindowKey
import com.dailysatori.service.unifiednews.nextUnifiedNewsWindow
import com.dailysatori.shared.db.Unified_news_summary
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class UnifiedNewsSchedulerTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val workManager get() = WorkManager.getInstance(app)
    private val now = Instant.parse("2026-11-10T10:00:00Z")

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
    }

    @Test
    fun armingTheNextWindowKeepsTheRunningWindowWork() {
        val runningName = unifiedNewsWindowWorkName(
            NextUnifiedNewsWindow(UnifiedNewsWindowKey.W0800, Instant.parse("2026-11-10T00:00:00Z")),
        )
        workManager.enqueueUniqueWork(runningName, ExistingWorkPolicy.KEEP, buildUnifiedNewsNextWorkRequest(now))

        val nextName = unifiedNewsWindowWorkName(nextUnifiedNewsWindow(now))
        runBlocking { UnifiedNewsScheduler(app).scheduleNext(now) }

        assertNotEquals(runningName, nextName)
        assertTrue(unfinishedStates(runningName).isNotEmpty(), "the running window work must not be cancelled")
        assertNotEquals(listOf(WorkInfo.State.CANCELLED), states(runningName))
        assertEquals(1, unfinishedStates(nextName).size)

        runBlocking { UnifiedNewsScheduler(app).scheduleNext(now) }
        assertEquals(1, unfinishedStates(nextName).size, "arming the same window twice must not duplicate work")
    }

    @Test
    fun startupArmsNextWindowAndCatchesUpTheDueWindowOnce() {
        val dueName = unifiedNewsCatchUpWorkName(latestDueUnifiedNewsWindow(now))

        runBlocking { UnifiedNewsScheduler(app).ensureScheduled(now, latestSuccessful = { null }) }

        // The immediate catch-up work is eligible at once, so it may already have run here; the
        // assertion is that startup armed exactly one of it (dedupe of the delayed window work is
        // covered above).
        assertEquals(1, states(dueName).size)
        assertEquals(1, unfinishedStates(unifiedNewsWindowWorkName(nextUnifiedNewsWindow(now))).size)
    }

    @Test
    fun freshSuccessfulSummaryDoesNotArmACatchUpRun() {
        val due = latestDueUnifiedNewsWindow(now)

        runBlocking { UnifiedNewsScheduler(app).ensureScheduled(now, latestSuccessful = { summary(generatedAt = due.endMs) }) }

        assertTrue(unfinishedStates(unifiedNewsCatchUpWorkName(due)).isEmpty())
    }

    @Test
    fun legacySharedWindowNameIsDroppedSoItCannotFireTwice() {
        workManager.enqueueUniqueWork(
            LegacyUnifiedNewsWorkName, ExistingWorkPolicy.KEEP, buildUnifiedNewsNextWorkRequest(now),
        )

        runBlocking { UnifiedNewsScheduler(app).ensureScheduled(now, latestSuccessful = { null }) }

        assertEquals(listOf(WorkInfo.State.CANCELLED), states(LegacyUnifiedNewsWorkName))
    }

    @Test
    fun catchUpOnlyLooksAtTheMostRecentDueWindow() {
        val due = latestDueUnifiedNewsWindow(now)

        assertTrue(unifiedNewsNeedsCatchUp(now, null))
        assertFalse(unifiedNewsNeedsCatchUp(now, summary(generatedAt = due.endMs)))
        assertFalse(unifiedNewsNeedsCatchUp(now, summary(generatedAt = due.endMs + 60_000L)))
        assertTrue(unifiedNewsNeedsCatchUp(now, summary(generatedAt = due.endMs - 1L)))
        assertTrue(unifiedNewsNeedsCatchUp(now, summary(generatedAt = due.startMs - 24 * 60 * 60 * 1000L)))
    }

    private fun summary(generatedAt: Long) = Unified_news_summary(
        id = 1L,
        summary_date = "2026-11-10",
        window_key = UnifiedNewsWindowKey.DAILY.value,
        window_start_ms = 0L,
        window_end_ms = 0L,
        title = "旧汇总",
        content = "# 旧的中文汇总",
        status = "success",
        error_message = null,
        source_warnings = null,
        generated_at = generatedAt,
        created_at = generatedAt,
        updated_at = generatedAt,
    )

    private fun states(name: String): List<WorkInfo.State> =
        workManager.getWorkInfosForUniqueWork(name).get().map { it.state }

    private fun unfinishedStates(name: String): List<WorkInfo.State> =
        states(name).filter { !it.isFinished }
}

package com.dailysatori.core.worker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

class WeeklySummaryWorkerTest {
    private val hongKong = TimeZone.of("Asia/Hong_Kong")

    @Test
    fun nextRunIsMondayAtThree() {
        val sundayNoon = Instant.parse("2026-07-26T04:00:00Z")
        val mondayAfterDue = Instant.parse("2026-07-27T00:00:00Z")

        assertEquals(15 * 60 * 60 * 1000L, nextWeeklySummaryDelayMs(sundayNoon, hongKong))
        assertEquals(6 * 24 * 60 * 60 * 1000L + 19 * 60 * 60 * 1000L, nextWeeklySummaryDelayMs(mondayAfterDue, hongKong))
    }

    @Test
    fun applicationEnsuresWeeklySummarySchedule() {
        val application = File("src/main/kotlin/com/dailysatori/DailySatoriApplication.kt").readText()
        val worker = File("src/main/kotlin/com/dailysatori/core/worker/WeeklySummaryWorker.kt").readText()

        assertTrue(application.contains("WeeklySummaryScheduler(this).ensureScheduled()"))
        assertTrue(worker.contains("PeriodicWorkRequestBuilder<WeeklySummaryWorker>(7, TimeUnit.DAYS)"))
        assertTrue(worker.contains("NetworkType.CONNECTED"))
    }
}

package com.dailysatori.bookkeeping

import kotlinx.datetime.*
import kotlin.test.*

class LedgerPeriodTest {
    private val zone = TimeZone.of("Asia/Shanghai")
    private val engine = LedgerEngine()
    private val anchor = LocalDate(2026, 10, 6)

    private fun entry(id: String, at: LocalDateTime, amount: Long, kind: LedgerKind = LedgerKind.EXPENSE,
        currency: String = "CNY", status: LedgerStatus = LedgerStatus.POSTED): LedgerEntry =
        LedgerEntry(id, listOf(id), "bank", "text", at.toInstant(zone).toEpochMilliseconds(), amount, currency, kind,
            "便利店", status = status, reason = if (status == LedgerStatus.POSTED) "" else "incomplete")

    private fun state(vararg entries: LedgerEntry) = LedgerState(entries = entries.toList())

    @Test fun dayBucketsCoverSevenDaysNewestFirstAndExcludeUnpostedEntries() {
        val ledger = state(
            entry("posted", LocalDateTime(2026, 10, 6, 9, 0), 5600L),
            entry("income", LocalDateTime(2026, 10, 6, 12, 0), 1000L, LedgerKind.INCOME),
            entry("pending", LocalDateTime(2026, 10, 6, 13, 0), 100L, status = LedgerStatus.PENDING),
            entry("ignored", LocalDateTime(2026, 10, 6, 14, 0), 200L, status = LedgerStatus.IGNORED),
            entry("yesterday", LocalDateTime(2026, 10, 5, 8, 0), 200L),
            entry("tooOld", LocalDateTime(2026, 9, 28, 8, 0), 999L),
        )
        val buckets = engine.buckets(ledger, LedgerPeriod.DAY, anchor, zone)
        assertEquals(7, buckets.size)
        assertEquals(LocalDate(2026, 10, 6), buckets.first().start)
        assertEquals(LocalDate(2026, 10, 6), buckets.first().end)
        assertEquals(LocalDate(2026, 9, 30), buckets.last().start)
        assertEquals(listOf("income", "posted"), buckets.first().entries.map { it.id })
        assertEquals(listOf(LedgerTotal("CNY", 1000L, 5600L, 0L)), buckets.first().totals)
        assertEquals(listOf("yesterday"), buckets[1].entries.map { it.id })
        assertEquals(listOf(200L), buckets[1].totals.map { it.expense })
        assertEquals(buckets.size, buckets.map { it.start }.distinct().size)
    }

    @Test fun weekBucketsStartOnMondayAndKeepTimezoneDayBoundaries() {
        val ledger = state(
            entry("sameWeek", LocalDateTime(2026, 10, 5, 0, 30), 100L),
            entry("previousWeek", LocalDateTime(2026, 10, 4, 23, 30), 200L),
        )
        val buckets = engine.buckets(ledger, LedgerPeriod.WEEK, anchor, zone)
        assertEquals(6, buckets.size)
        assertEquals(LocalDate(2026, 10, 5), buckets.first().start)
        assertEquals(LocalDate(2026, 10, 11), buckets.first().end)
        assertEquals(listOf("sameWeek"), buckets.first().entries.map { it.id })
        assertEquals(LocalDate(2026, 9, 28), buckets[1].start)
        assertEquals(listOf("previousWeek"), buckets[1].entries.map { it.id })
    }

    @Test fun monthBucketsFollowCalendarMonths() {
        val ledger = state(
            entry("october", LocalDateTime(2026, 10, 31, 23, 0), 100L),
            entry("september", LocalDateTime(2026, 9, 30, 23, 0), 200L),
        )
        val buckets = engine.buckets(ledger, LedgerPeriod.MONTH, anchor, zone)
        assertEquals(6, buckets.size)
        assertEquals(LocalDate(2026, 10, 1), buckets.first().start)
        assertEquals(LocalDate(2026, 10, 31), buckets.first().end)
        assertEquals(listOf("october"), buckets.first().entries.map { it.id })
        assertEquals(LocalDate(2026, 5, 1), buckets.last().start)
        assertEquals(LocalDate(2026, 9, 1), buckets[1].start)
        assertEquals(listOf("september"), buckets[1].entries.map { it.id })
    }

    @Test fun bucketsUseTheGivenZoneForDayBoundaries() {
        val ledger = state(entry("lateNight", LocalDateTime(2026, 10, 6, 0, 30), 100L))
        assertEquals(listOf("lateNight"), engine.buckets(ledger, LedgerPeriod.DAY, anchor, zone).first().entries.map { it.id })
        val utc = engine.buckets(ledger, LedgerPeriod.DAY, anchor, TimeZone.UTC)
        assertEquals(emptyList(), utc.first().entries.map { it.id })
        assertEquals(listOf("lateNight"), utc[1].entries.map { it.id })
    }

    @Test fun emptyBucketsAreStillReturnedForEveryPeriod() {
        listOf(LedgerPeriod.DAY to 7, LedgerPeriod.WEEK to 6, LedgerPeriod.MONTH to 6).forEach { (period, count) ->
            val buckets = engine.buckets(LedgerState(), period, anchor, zone)
            assertEquals(count, buckets.size)
            assertTrue(buckets.all { it.entries.isEmpty() && it.totals.isEmpty() })
        }
    }

    @Test fun keepsCurrenciesSeparateInsideOneBucket() {
        val ledger = state(
            entry("cny", LocalDateTime(2026, 10, 6, 9, 0), 5600L),
            entry("usd", LocalDateTime(2026, 10, 6, 10, 0), 1250L, currency = "USD"),
            entry("refund", LocalDateTime(2026, 10, 6, 11, 0), 500L, LedgerKind.REFUND),
        )
        val totals = engine.buckets(ledger, LedgerPeriod.DAY, anchor, zone).first().totals
        assertEquals(LedgerTotal("CNY", 0L, 5600L, 500L), totals.first { it.currency == "CNY" })
        assertEquals(LedgerTotal("USD", 0L, 1250L, 0L), totals.first { it.currency == "USD" })
    }

    @Test fun shiftingTheAnchorMovesByTheSelectedPeriod() {
        assertEquals(LocalDate(2026, 10, 5), engine.shift(anchor, LedgerPeriod.DAY, -1))
        assertEquals(LocalDate(2026, 9, 29), engine.shift(anchor, LedgerPeriod.WEEK, -1))
        assertEquals(LocalDate(2026, 9, 6), engine.shift(anchor, LedgerPeriod.MONTH, -1))
        assertEquals(LocalDate(2026, 2, 28), engine.shift(LocalDate(2026, 1, 31), LedgerPeriod.MONTH, 1))
    }
}

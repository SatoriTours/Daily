package com.dailysatori.bookkeeping

import kotlinx.datetime.*
import kotlin.test.*

class LedgerAnalysisTest {
    private val zone = TimeZone.of("Asia/Shanghai")
    private val engine = LedgerEngine()
    private val anchor = LocalDate(2026, 10, 8)
    private val payment = "尾号1238消费人民币56.00元，余额1,200.00元。商户：便利店"

    private fun entry(id: String, at: LocalDateTime, amount: Long, kind: LedgerKind = LedgerKind.EXPENSE,
        merchant: String = "", category: LedgerCategory = LedgerCategory.OTHER,
        status: LedgerStatus = LedgerStatus.POSTED): LedgerEntry =
        LedgerEntry(id, listOf(id), "bank", "text", at.toInstant(zone).toEpochMilliseconds(), amount, "CNY", kind,
            merchant, category = category, status = status, reason = if (status == LedgerStatus.POSTED) "" else "incomplete")

    private fun state(vararg entries: LedgerEntry) = LedgerState(entries = entries.toList())
    private fun at(day: Int, hour: Int = 12, month: Int = 10, year: Int = 2026) =
        LocalDateTime(year, month, day, hour, 0).toInstant(zone).toEpochMilliseconds()

    @Test fun periodRangesAreHalfOpenAndCoverQuarterAndYear() {
        val month = engine.range(LedgerPeriod.MONTH, anchor, zone)
        assertEquals(at(1, 0) to at(1, 0, month = 11), month)
        val quarter = engine.range(LedgerPeriod.QUARTER, anchor, zone)
        assertEquals(at(1, 0) to at(1, 0, month = 1, year = 2027), quarter)
        val year = engine.range(LedgerPeriod.YEAR, anchor, zone)
        assertEquals(at(1, 0, month = 1) to at(1, 0, month = 1, year = 2027), year)
        assertEquals(engine.range(LedgerPeriod.QUARTER, LocalDate(2026, 1, 15), zone).first, at(1, 0, month = 1))
        assertEquals(engine.range(LedgerPeriod.QUARTER, LocalDate(2026, 12, 31), zone).first, at(1, 0))
    }

    @Test fun analysisBucketsCanWidenToTwelveMonthsOrThirteenWeeks() {
        val months = engine.buckets(LedgerState(), LedgerPeriod.MONTH, anchor, zone, count = 12)
        assertEquals(12, months.size)
        assertEquals(LocalDate(2026, 10, 1), months.first().start)
        assertEquals(LocalDate(2025, 11, 1), months.last().start)
        val weeks = engine.buckets(LedgerState(), LedgerPeriod.WEEK, anchor, zone, count = 13)
        assertEquals(13, weeks.size)
        assertEquals(LocalDate(2026, 10, 5), weeks.first().start)
        assertEquals(LocalDate(2026, 7, 13), weeks.last().start)
    }

    @Test fun defaultCategoriesFollowMerchantKeywords() {
        assertEquals(LedgerCategory.FOOD, engine.category("美团外卖", CategoryRules()))
        assertEquals(LedgerCategory.FOOD, engine.category("便利店", CategoryRules()))
        assertEquals(LedgerCategory.TRANSPORT, engine.category("地铁", CategoryRules()))
        assertEquals(LedgerCategory.TRANSPORT, engine.category("滴滴出行", CategoryRules()))
        assertEquals(LedgerCategory.SHOPPING, engine.category("永辉超市", CategoryRules()))
        assertEquals(LedgerCategory.HOME, engine.category("房租", CategoryRules()))
        assertEquals(LedgerCategory.HEALTH, engine.category("市第一医院", CategoryRules()))
        assertEquals(LedgerCategory.ENTERTAINMENT, engine.category("万达影城", CategoryRules()))
        assertEquals(LedgerCategory.BILLS, engine.category("中国移动话费", CategoryRules()))
        assertEquals(LedgerCategory.OTHER, engine.category("", CategoryRules()))
        assertEquals(LedgerCategory.OTHER, engine.category("某某科技", CategoryRules()))
    }

    @Test fun rememberedMerchantRulesOverrideKeywords() {
        val rules = CategoryRules(merchants = mapOf("便利店" to LedgerCategory.SHOPPING, "地铁" to LedgerCategory.OTHER))
        assertEquals(LedgerCategory.SHOPPING, engine.category("便利店", rules))
        assertEquals(LedgerCategory.OTHER, engine.category("地铁", rules))
        assertEquals(LedgerCategory.FOOD, engine.category("美团外卖", rules))
    }

    @Test fun ingestAssignsCategoryFromMerchantAndRules() {
        val posted = engine.ingest(LedgerState(), "bank", "e1", payment, at(8, 10))
        assertEquals(LedgerCategory.FOOD, posted.entries.single().category)
        val ruled = engine.ingest(LedgerState(), "bank", "e1", payment, at(8, 10),
            CategoryRules(merchants = mapOf("便利店" to LedgerCategory.SHOPPING)))
        assertEquals(LedgerCategory.SHOPPING, ruled.entries.single().category)
    }

    @Test fun reclassifyingKeepsEntryAndRemembersMerchantRule() {
        val posted = engine.ingest(LedgerState(), "bank", "e1", payment, at(8, 10))
        val edited = engine.categorize(posted, posted.entries.single().id, LedgerCategory.SHOPPING)
        assertEquals(LedgerCategory.SHOPPING, edited.entries.single().category)
        val same = engine.ingest(edited, "bank", "e1", payment, at(8, 10))
        assertEquals(LedgerCategory.SHOPPING, same.entries.single().category)
        assertEquals(1, same.entries.size)
    }

    @Test fun categoryTotalsRankExpensesAndIgnoreOtherKinds() {
        val ledger = state(
            entry("food", LocalDateTime(2026, 10, 8, 10, 0), 5600L, merchant = "便利店", category = LedgerCategory.FOOD),
            entry("transport", LocalDateTime(2026, 10, 7, 8, 0), 3000L, merchant = "地铁", category = LedgerCategory.TRANSPORT),
            entry("shopping", LocalDateTime(2026, 10, 6, 8, 0), 1200L, merchant = "超市", category = LedgerCategory.SHOPPING),
            entry("income", LocalDateTime(2026, 10, 5, 8, 0), 900_000L, LedgerKind.INCOME, category = LedgerCategory.OTHER),
            entry("transfer", LocalDateTime(2026, 10, 4, 8, 0), 500_000L, LedgerKind.TRANSFER),
            entry("refund", LocalDateTime(2026, 10, 3, 8, 0), 500L, LedgerKind.REFUND, category = LedgerCategory.FOOD),
            entry("pending", LocalDateTime(2026, 10, 2, 8, 0), 999L, status = LedgerStatus.PENDING),
        )
        val categories = engine.categoryTotals(ledger, LedgerPeriod.MONTH, anchor, zone)
        assertEquals(listOf(LedgerCategory.FOOD to 5600L, LedgerCategory.TRANSPORT to 3000L, LedgerCategory.SHOPPING to 1200L),
            categories.map { it.category to it.amount })
        val merchants = engine.merchantTotals(ledger, LedgerPeriod.MONTH, anchor, zone, limit = 2)
        assertEquals(listOf("便利店" to 5600L, "地铁" to 3000L), merchants.map { it.merchant to it.amount })
        assertEquals(1, engine.merchantTotals(ledger, LedgerPeriod.MONTH, anchor, zone, limit = 2).size - 1)
    }

    @Test fun merchantTotalsSkipEntriesWithoutMerchantAndCountOrders() {
        val ledger = state(
            entry("a", LocalDateTime(2026, 10, 8, 10, 0), 5600L, merchant = "便利店"),
            entry("b", LocalDateTime(2026, 10, 7, 10, 0), 2000L, merchant = "便利店"),
            entry("c", LocalDateTime(2026, 10, 6, 10, 0), 100L, merchant = ""),
        )
        val merchants = engine.merchantTotals(ledger, LedgerPeriod.MONTH, anchor, zone, limit = 10)
        assertEquals(1, merchants.size)
        assertEquals(7600L, merchants.single().amount)
        assertEquals(2, merchants.single().count)
    }

    @Test fun previousExpenseDrivesPeriodOverPeriodComparison() {
        val ledger = state(
            entry("september", LocalDateTime(2026, 9, 20, 10, 0), 1000L),
            entry("october", LocalDateTime(2026, 10, 8, 10, 0), 1500L),
            entry("lastYear", LocalDateTime(2025, 10, 8, 10, 0), 900L),
        )
        assertEquals(1000L, engine.previousExpense(ledger, LedgerPeriod.MONTH, anchor, zone))
        assertEquals(900L, engine.previousExpense(ledger, LedgerPeriod.YEAR, anchor, zone))
        assertEquals(1000L, engine.previousExpense(ledger, LedgerPeriod.QUARTER, anchor, zone))
        assertEquals(900L, engine.previousExpense(ledger, LedgerPeriod.QUARTER, LocalDate(2026, 1, 15), zone))
        assertEquals(0L, engine.previousExpense(ledger, LedgerPeriod.MONTH, LocalDate(2026, 1, 15), zone))
    }

    @Test fun lockedCategorySurvivesNewNotificationsAndRulesRoundTrip() {        val rules = CategoryRules(merchants = mapOf("便利店" to LedgerCategory.SHOPPING))
        val encoded = CategoryRules.encode(rules)
        assertEquals(rules, CategoryRules.decode(encoded))
        assertEquals(CategoryRules(), CategoryRules.decode(null))
        assertEquals(CategoryRules(), CategoryRules.decode("not json"))
    }

    @Test fun notesAndExcludedEntriesChangeStatistics() {
        val ledger = state(
            entry("kept", LocalDateTime(2026, 10, 8, 10, 0), 5600L, merchant = "便利店"),
            entry("excluded", LocalDateTime(2026, 10, 7, 10, 0), 3000L, merchant = "地铁"),
        )
        assertEquals(8600L, engine.totals(ledger, at(1, 0), at(1, 0, month = 11)).single().expense)
        val excluded = engine.exclude(ledger, "excluded", true)
        assertEquals(5600L, engine.totals(excluded, at(1, 0), at(1, 0, month = 11)).single().expense)
        assertEquals(5600L, engine.categoryTotals(excluded, LedgerPeriod.MONTH, anchor, zone).single().amount)
        val noted = engine.note(ledger, "kept", "  公司楼下的店  ")
        assertEquals("公司楼下的店", noted.entries.single { it.id == "kept" }.note)
        assertEquals("", engine.note(noted, "kept", "").entries.single { it.id == "kept" }.note)
    }
}

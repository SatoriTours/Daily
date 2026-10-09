package com.dailysatori.bookkeeping

import kotlinx.datetime.*

class LedgerEngine {
    private val parser = TransactionParser()

    fun ingest(state: LedgerState, source: String, eventKey: String, text: String, receivedAt: Long,
        rules: CategoryRules = CategoryRules()): LedgerState {
        require(state.version == 1 && source.isNotBlank() && source.length <= 200)
        require(eventKey.isNotBlank() && eventKey.length <= 200 && receivedAt > 0)
        val byEvent = state.entries.firstOrNull { it.source == source && eventKey in it.eventKeys }
        if (byEvent != null && byEvent.status != LedgerStatus.PENDING) return state
        val draft = parser.parse(text) ?: return state
        val matchingTransactions = state.entries.filter {
            draft.transactionId.isNotEmpty() && it.source == source && it.transactionId == draft.transactionId &&
                (it.accountTail.isEmpty() || draft.accountTail.isEmpty() || it.accountTail == draft.accountTail)
        }
        val byTransaction = selectTransaction(matchingTransactions, draft)
        if (byTransaction != null && shouldRetain(byTransaction, draft)) {
            val aliases = (byTransaction.eventKeys + byEvent?.eventKeys.orEmpty() + eventKey).distinct()
            return state.copy(entries = state.entries.filterNot { it.id == byEvent?.id && it.id != byTransaction.id }.map {
                if (it.id == byTransaction.id) it.copy(eventKeys = aliases,
                    accountTail = it.accountTail.ifEmpty { draft.accountTail }) else it
            })
        }
        val previous = byEvent ?: byTransaction?.takeIf { it.status == LedgerStatus.PENDING && canEnrich(it, draft) }
        val similar = state.entries.firstOrNull {
            it.id != previous?.id && it.status in setOf(LedgerStatus.POSTED, LedgerStatus.PENDING) &&
                draft.amountMinor != null && it.amountMinor == draft.amountMinor && it.currency == draft.currency &&
                it.kind == draft.kind && distance(it.receivedAt, receivedAt) <= 300_000
        }
        val conflicting = matchingTransactions.firstOrNull { it.id != previous?.id }
        val reason = if (similar != null || conflicting != null) "possible_duplicate" else draft.reason
        val entry = LedgerEntry(id = previous?.id ?: "$source:$eventKey",
            eventKeys = (previous?.eventKeys.orEmpty() + eventKey).distinct(), source = source, text = text,
            receivedAt = previous?.receivedAt ?: receivedAt, amountMinor = draft.amountMinor, currency = draft.currency,
            kind = draft.kind, merchant = draft.merchant, category = category(draft.merchant, rules),
            accountTail = draft.accountTail.ifEmpty { previous?.accountTail.orEmpty() },
            transactionId = draft.transactionId.ifEmpty { previous?.transactionId.orEmpty() },
            status = if (reason.isEmpty()) LedgerStatus.POSTED else LedgerStatus.PENDING,
            reason = reason, duplicateOf = (similar ?: conflicting)?.id.orEmpty(),
            userConfirmed = previous?.userConfirmed ?: false)
        return state.copy(entries = if (previous == null) state.entries + entry else state.entries.map {
            if (it.id == previous.id) entry else it
        })
    }

    private fun selectTransaction(entries: List<LedgerEntry>, draft: TransactionDraft): LedgerEntry? {
        if (draft.accountTail.isEmpty() && entries.map { it.accountTail }.filter { it.isNotEmpty() }.distinct().size > 1) return null
        return entries.filter { shouldRetain(it, draft) }.singleOrNull() ?: entries.singleOrNull() ?:
            entries.filter { it.status == LedgerStatus.PENDING && canEnrich(it, draft) }.singleOrNull()
    }

    private fun canEnrich(entry: LedgerEntry, draft: TransactionDraft): Boolean =
        (entry.amountMinor == null || (entry.amountMinor == draft.amountMinor && entry.currency == draft.currency)) &&
            (entry.kind == LedgerKind.UNKNOWN || entry.kind == draft.kind)

    private fun shouldRetain(entry: LedgerEntry, draft: TransactionDraft): Boolean =
        entry.userConfirmed || entry.status in setOf(LedgerStatus.IGNORED, LedgerStatus.DELETED) ||
            (entry.status == LedgerStatus.POSTED && entry.amountMinor == draft.amountMinor &&
                entry.currency == draft.currency && entry.kind == draft.kind)

    fun edit(state: LedgerState, id: String, amount: String, currency: String, kind: LedgerKind, merchant: String): LedgerState {
        val minor = requireNotNull(LedgerMoney.parse(amount, currency)) { "invalid_amount" }
        require(kind != LedgerKind.UNKNOWN && merchant.length <= 80)
        val existing = state.entries.single { it.id == id }
        require(existing.status in setOf(LedgerStatus.POSTED, LedgerStatus.PENDING))
        return state.copy(entries = state.entries.map {
            if (it.id == id) it.copy(amountMinor = minor, currency = currency, kind = kind, merchant = merchant.trim(),
                status = LedgerStatus.POSTED, reason = "", duplicateOf = "", userConfirmed = true) else it
        })
    }

    fun dismiss(state: LedgerState, id: String, status: LedgerStatus): LedgerState {
        require(status in setOf(LedgerStatus.IGNORED, LedgerStatus.DELETED))
        require(state.entries.any { it.id == id })
        // Keep identity tombstones so a notification refresh cannot recreate a deleted entry.
        return state.copy(entries = state.entries.map {
            if (it.id == id) it.copy(status = status, text = "", merchant = "", reason = "", duplicateOf = "") else it
        })
    }

    fun totals(state: LedgerState, start: Long, end: Long): List<LedgerTotal> {
        require(start < end)
        return state.entries.filter { it.status == LedgerStatus.POSTED && !it.excluded && it.receivedAt in start until end }
            .groupBy { it.currency }.map { (currency, entries) ->
                fun sum(kind: LedgerKind) = entries.filter { it.kind == kind }.fold(0L) { total, entry ->
                    val amount = requireNotNull(entry.amountMinor)
                    require(amount > 0 && total <= Long.MAX_VALUE - amount)
                    total + amount
                }
                LedgerTotal(currency, sum(LedgerKind.INCOME), sum(LedgerKind.EXPENSE), sum(LedgerKind.REFUND))
            }.sortedBy { it.currency }
    }

    /** Moves the summary anchor by [delta] days, weeks, months, quarters or years. */
    fun shift(anchor: LocalDate, period: LedgerPeriod, delta: Int): LocalDate = when (period) {
        LedgerPeriod.DAY -> anchor.plus(delta, DateTimeUnit.DAY)
        LedgerPeriod.WEEK -> anchor.plus(delta * 7, DateTimeUnit.DAY)
        LedgerPeriod.MONTH -> anchor.plus(delta, DateTimeUnit.MONTH)
        LedgerPeriod.QUARTER -> anchor.plus(delta * 3, DateTimeUnit.MONTH)
        LedgerPeriod.YEAR -> anchor.plus(delta, DateTimeUnit.YEAR)
    }

    /** Half-open epoch range (start inclusive, end exclusive) of the period containing [anchor]. */
    fun range(period: LedgerPeriod, anchor: LocalDate, zone: TimeZone): Pair<Long, Long> {
        val (start, end) = span(period, anchor)
        return start.atStartOfDayIn(zone).toEpochMilliseconds() to
            end.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds()
    }

    /** Recent periods ending at the one containing [anchor], newest first. */
    fun buckets(state: LedgerState, period: LedgerPeriod, anchor: LocalDate, zone: TimeZone,
        count: Int = bucketCount(period)): List<LedgerBucket> {
        val first = span(period, anchor).first
        return (0 until count).map { index -> bucket(state, span(period, shift(first, period, -index)), zone) }
    }

    private fun bucketCount(period: LedgerPeriod): Int = if (period == LedgerPeriod.DAY) 7 else 6

    private fun span(period: LedgerPeriod, date: LocalDate): Pair<LocalDate, LocalDate> = when (period) {
        LedgerPeriod.DAY -> date to date
        LedgerPeriod.WEEK -> weekStart(date).let { it to it.plus(6, DateTimeUnit.DAY) }
        LedgerPeriod.MONTH -> LocalDate(date.year, date.monthNumber, 1)
            .let { it to it.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY) }
        LedgerPeriod.QUARTER -> LocalDate(date.year, (date.monthNumber - 1) / 3 * 3 + 1, 1)
            .let { it to it.plus(3, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY) }
        LedgerPeriod.YEAR -> LocalDate(date.year, 1, 1) to LocalDate(date.year, 12, 31)
    }

    private fun weekStart(date: LocalDate): LocalDate = date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

    private fun bucket(state: LedgerState, span: Pair<LocalDate, LocalDate>, zone: TimeZone): LedgerBucket {
        val (start, end) = span
        val from = start.atStartOfDayIn(zone).toEpochMilliseconds()
        val to = end.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds()
        return LedgerBucket(start, end, totals(state, from, to), state.entries
            .filter { it.status == LedgerStatus.POSTED && it.receivedAt in from until to }
            .sortedByDescending { it.receivedAt })
    }

    /** Category for a merchant: remembered rules win over built-in keywords. */
    fun category(merchant: String, rules: CategoryRules = CategoryRules()): LedgerCategory {
        val name = merchant.trim()
        if (name.isEmpty()) return LedgerCategory.OTHER
        rules.merchants.entries.firstOrNull { name.contains(it.key, ignoreCase = true) }?.let { return it.value }
        val lower = name.lowercase()
        return keywords.firstOrNull { (_, words) -> words.any { lower.contains(it) } }?.first ?: LedgerCategory.OTHER
    }

    fun categorize(state: LedgerState, id: String, category: LedgerCategory): LedgerState {
        require(state.entries.any { it.id == id }) { "unknown_entry" }
        return state.copy(entries = state.entries.map { if (it.id == id) it.copy(category = category) else it })
    }

    /** Expenses of the period, grouped by category, biggest first. */
    fun categoryTotals(state: LedgerState, period: LedgerPeriod, anchor: LocalDate, zone: TimeZone,
        currency: String = "CNY"): List<LedgerCategoryTotal> {
        val (from, to) = range(period, anchor, zone)
        return expenses(state, from, to).filter { it.currency == currency }.groupBy { it.category }.map { (category, entries) ->
            LedgerCategoryTotal(category, entries.sumOf { requireNotNull(it.amountMinor) }, entries.size)
        }.sortedWith(compareByDescending<LedgerCategoryTotal> { it.amount }.thenBy { it.category.ordinal })
    }

    /** Expense ranking by merchant for the period. */
    fun merchantTotals(state: LedgerState, period: LedgerPeriod, anchor: LocalDate, zone: TimeZone,
        limit: Int = 10, currency: String = "CNY"): List<LedgerMerchantTotal> {
        require(limit > 0)
        val (from, to) = range(period, anchor, zone)
        return expenses(state, from, to).filter { it.currency == currency && it.merchant.isNotBlank() }.groupBy { it.merchant.trim() }
            .map { (merchant, entries) -> LedgerMerchantTotal(merchant, entries.sumOf { requireNotNull(it.amountMinor) }, entries.size) }
            .sortedWith(compareByDescending<LedgerMerchantTotal> { it.amount }.thenBy { it.merchant })
            .take(limit)
    }

    /** Expenses of the period right before the one containing [anchor], for period-over-period comparison. */
    fun previousExpense(state: LedgerState, period: LedgerPeriod, anchor: LocalDate, zone: TimeZone,
        currency: String = "CNY"): Long {
        val (from, to) = range(period, shift(anchor, period, -1), zone)
        return totals(state, from, to).firstOrNull { it.currency == currency }?.expense ?: 0L
    }

    private fun expenses(state: LedgerState, from: Long, to: Long): List<LedgerEntry> = state.entries.filter {
        it.status == LedgerStatus.POSTED && !it.excluded && it.kind == LedgerKind.EXPENSE && it.receivedAt in from until to
    }

    /** Sets the user note of one entry; blank clears it. */
    fun note(state: LedgerState, id: String, text: String): LedgerState {
        require(text.length <= 200)
        require(state.entries.any { it.id == id }) { "unknown_entry" }
        return state.copy(entries = state.entries.map { if (it.id == id) it.copy(note = text.trim()) else it })
    }

    /** Excludes or re-includes one entry from income/expense statistics. */
    fun exclude(state: LedgerState, id: String, excluded: Boolean): LedgerState {
        require(state.entries.any { it.id == id }) { "unknown_entry" }
        return state.copy(entries = state.entries.map { if (it.id == id) it.copy(excluded = excluded) else it })
    }

    private val keywords: List<Pair<LedgerCategory, List<String>>> = listOf(
        LedgerCategory.FOOD to listOf("餐饮", "外卖", "美团", "饿了么", "餐厅", "饭店", "便利店", "咖啡", "奶茶", "食堂", "烧烤", "火锅", "早餐", "午饭", "晚饭", "小吃", "面馆", "果切", "零食"),
        LedgerCategory.TRANSPORT to listOf("地铁", "公交", "打车", "滴滴", "出租", "高铁", "火车", "机票", "航空", "加油", "停车", "单车", "充电桩", "过路"),
        LedgerCategory.SHOPPING to listOf("超市", "商场", "淘宝", "天猫", "京东", "拼多多", "唯品会", "服饰", "数码", "家电", "宜家", "建材"),
        LedgerCategory.HOME to listOf("房租", "物业", "水费", "电费", "燃气", "取暖", "家政", "保洁", "宽带", "房贷", "维修"),
        LedgerCategory.HEALTH to listOf("医院", "药房", "药店", "诊所", "体检", "口腔", "牙科", "挂号", "医保", "疫苗"),
        LedgerCategory.ENTERTAINMENT to listOf("电影", "影城", "游戏", "健身", "ktv", "娱乐", "门票", "演出", "球馆", "剧本", "酒吧"),
        LedgerCategory.BILLS to listOf("话费", "流量", "充值", "会员", "订阅", "缴费", "服务费", "保险", "学费", "税金", "罚款"),
    )

    private fun distance(a: Long, b: Long): Long = if (a >= b) a - b else b - a
}

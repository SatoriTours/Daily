package com.dailysatori.bookkeeping

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable enum class LedgerKind { EXPENSE, INCOME, REFUND, TRANSFER, REPAYMENT, UNKNOWN }
@Serializable enum class LedgerStatus { POSTED, PENDING, IGNORED, DELETED }
@Serializable enum class LedgerCategory { FOOD, TRANSPORT, SHOPPING, HOME, HEALTH, ENTERTAINMENT, BILLS, OTHER }

@Serializable
data class LedgerEntry(
    val id: String,
    val eventKeys: List<String>,
    val source: String,
    val text: String,
    val receivedAt: Long,
    val amountMinor: Long? = null,
    val currency: String = "CNY",
    val kind: LedgerKind = LedgerKind.UNKNOWN,
    val merchant: String = "",
    val category: LedgerCategory = LedgerCategory.OTHER,
    val note: String = "",
    val excluded: Boolean = false,
    val accountTail: String = "",
    val transactionId: String = "",
    val status: LedgerStatus = LedgerStatus.PENDING,
    val reason: String = "incomplete",
    val duplicateOf: String = "",
    val userConfirmed: Boolean = false,
) {
    init {
        require(id.isNotBlank() && source.isNotBlank() && source.length <= 200 && receivedAt > 0)
        require(eventKeys.isNotEmpty() && eventKeys.all { it.isNotBlank() && it.length <= 200 })
        require(currency in LedgerMoney.currencies && (amountMinor == null || amountMinor in 1..LedgerMoney.MAX_AMOUNT))
        require(text.length <= 8000 && merchant.length <= 80 && transactionId.length <= 80 && note.length <= 200)
        require(status != LedgerStatus.POSTED || (amountMinor != null && kind != LedgerKind.UNKNOWN && reason.isEmpty()))
    }
}

@Serializable data class LedgerState(val version: Int = 1, val entries: List<LedgerEntry> = emptyList())
@Serializable data class LedgerTotal(val currency: String, val income: Long, val expense: Long, val refund: Long)

/** Aggregation window used by the ledger summary and analysis. */
enum class LedgerPeriod { DAY, WEEK, MONTH, QUARTER, YEAR }

/** Merchant→category rules remembered from user corrections. */
@Serializable data class CategoryRules(val merchants: Map<String, LedgerCategory> = emptyMap()) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun encode(rules: CategoryRules): String = json.encodeToString(rules)

        fun decode(text: String?): CategoryRules =
            text?.let { runCatching { json.decodeFromString<CategoryRules>(it) }.getOrNull() } ?: CategoryRules()
    }
}

data class LedgerCategoryTotal(val category: LedgerCategory, val amount: Long, val count: Int)
data class LedgerMerchantTotal(val merchant: String, val amount: Long, val count: Int)

/** Posted entries and per-currency totals of one day, week or month. */
data class LedgerBucket(
    val start: LocalDate,
    val end: LocalDate,
    val totals: List<LedgerTotal>,
    val entries: List<LedgerEntry>,
)

data class TransactionDraft(
    val amountMinor: Long?,
    val currency: String,
    val kind: LedgerKind,
    val merchant: String = "",
    val accountTail: String = "",
    val transactionId: String = "",
    val reason: String = "",
)

package com.dailysatori.bookkeeping

class LedgerEngine {
    private val parser = TransactionParser()

    fun ingest(state: LedgerState, source: String, eventKey: String, text: String, receivedAt: Long): LedgerState {
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
        val entry = LedgerEntry(previous?.id ?: "$source:$eventKey", (previous?.eventKeys.orEmpty() + eventKey).distinct(), source,
            text, previous?.receivedAt ?: receivedAt, draft.amountMinor, draft.currency, draft.kind, draft.merchant,
            draft.accountTail.ifEmpty { previous?.accountTail.orEmpty() }, draft.transactionId.ifEmpty { previous?.transactionId.orEmpty() },
            if (reason.isEmpty()) LedgerStatus.POSTED else LedgerStatus.PENDING,
            reason, (similar ?: conflicting)?.id.orEmpty())
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
        return state.entries.filter { it.status == LedgerStatus.POSTED && it.receivedAt in start until end }
            .groupBy { it.currency }.map { (currency, entries) ->
                fun sum(kind: LedgerKind) = entries.filter { it.kind == kind }.fold(0L) { total, entry ->
                    val amount = requireNotNull(entry.amountMinor)
                    require(amount > 0 && total <= Long.MAX_VALUE - amount)
                    total + amount
                }
                LedgerTotal(currency, sum(LedgerKind.INCOME), sum(LedgerKind.EXPENSE), sum(LedgerKind.REFUND))
            }.sortedBy { it.currency }
    }

    private fun distance(a: Long, b: Long): Long = if (a >= b) a - b else b - a
}

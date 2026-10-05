package com.dailysatori.bookkeeping

object LedgerMoney {
    val currencies = setOf("CNY", "USD", "EUR", "GBP", "JPY", "KRW", "HKD", "TWD", "SGD", "AUD", "CAD", "CHF")

    fun parse(amount: String, currency: String): Long? {
        if (currency !in currencies) return null
        val digits = if (currency in setOf("JPY", "KRW")) 0 else 2
        val normalized = amount.trim()
        if (!Regex("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?").matches(normalized)) return null
        val parts = normalized.replace(",", "").split('.')
        if (parts.getOrElse(1) { "" }.length > digits) return null
        val minor = (parts[0] + parts.getOrElse(1) { "" }.padEnd(digits, '0')).toLongOrNull() ?: return null
        return minor.takeIf { it in 1..MAX_AMOUNT }
    }

    fun format(amount: Long, currency: String): String {
        require(currency in currencies && amount in 0..Long.MAX_VALUE)
        if (currency in setOf("JPY", "KRW")) return amount.toString()
        return "${amount / 100}.${(amount % 100).toString().padStart(2, '0')}"
    }

    const val MAX_AMOUNT = 1_000_000_000_000L
}

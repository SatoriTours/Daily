package com.dailysatori.service.bookkeeping

import com.dailysatori.bookkeeping.CategoryRules
import com.dailysatori.data.repository.SettingRepository

/** Loads and stores the merchant→category rules shared by every ledger entry point. */
object LedgerRules {
    const val KEY = "bookkeeping.category_rules"

    fun of(settings: SettingRepository): CategoryRules = CategoryRules.decode(settings.get(KEY))
}

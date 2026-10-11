package com.dailysatori.core.navigation

import androidx.compose.runtime.staticCompositionLocalOf

/** Scoped callbacks also serve settings embedded in Home; no global navigation event state. */
data class PhoneNavigation(
    val openHistory: (String?) -> Unit,
    val openLedger: (String) -> Unit,
    val openTodo: (String) -> Unit,
)

val LocalPhoneNavigation = staticCompositionLocalOf<PhoneNavigation> {
    error("PhoneNavigation must be provided by DailySatoriNavHost")
}

package com.dailysatori.core.util

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/** Report recoverable read failures before stateIn launches its sharing coroutine. */
internal fun <T> Flow<T>.withUiObservationError(onFailure: () -> Unit): Flow<T> = catch { error ->
    if (error is CancellationException || error !is Exception) throw error
    Logger.withTag("UiObservation").w { "UI observation failed (${error::class.simpleName})" }
    onFailure()
}

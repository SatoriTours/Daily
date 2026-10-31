package com.dailysatori.core.startup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class StartupState { Idle, Preparing, Ready, Failed }

class StartupCoordinator(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onFailure: (Exception) -> Unit = {},
    private val initialize: suspend () -> Unit,
) {
    private val mutableState = MutableStateFlow(StartupState.Idle)
    val state = mutableState.asStateFlow()
    private val mutableDataPreparation = MutableStateFlow(false)
    val dataPreparation = mutableDataPreparation.asStateFlow()
    private val maintenanceReady = CompletableDeferred<Unit>()

    /** Only real migration/restore work opts into the data-preparation page. */
    fun requireDataPreparation() {
        mutableDataPreparation.value = true
    }

    val isReady: Boolean get() = state.value == StartupState.Ready

    /** One process-owned attempt; neither activity recreation nor worker cancellation owns it. */
    @Synchronized
    fun start() {
        if (state.value == StartupState.Preparing || isReady) return
        mutableDataPreparation.value = false
        mutableState.value = StartupState.Preparing
        scope.launch(dispatcher) {
            try {
                initialize()
                // Process-owned grace period: cancelling one Worker must not restart the clock.
                scope.launch(dispatcher) {
                    delay(10_000)
                    maintenanceReady.complete(Unit)
                }
                mutableState.value = StartupState.Ready
            } catch (cancelled: CancellationException) {
                mutableState.value = StartupState.Failed
                throw cancelled
            } catch (failure: Exception) {
                try { onFailure(failure) } finally { mutableState.value = StartupState.Failed }
            }
        }
    }

    /** Failure is not permission to access DI; only an explicit start retries preparation. */
    suspend fun awaitReady(): Boolean = state.first {
        it == StartupState.Ready || it == StartupState.Failed
    } == StartupState.Ready

    /** Automatic full-database backups must not compete with initial composition and reads. */
    suspend fun awaitMaintenanceWindow(): Boolean {
        if (!awaitReady()) return false
        maintenanceReady.await()
        return true
    }
}

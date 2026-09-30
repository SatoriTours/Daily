package com.dailysatori.core.diagnostics

import com.dailysatori.core.service.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex

enum class RecoveryUpdatePhase { IDLE, CHECKING, AVAILABLE, DOWNLOADING, READY }
enum class RecoveryUpdateFailure { CHECK, DOWNLOAD, INSTALL }
data class RecoveryUpdateState(
    val configuration: RecoveryUpdateConfiguration,
    val configurationLoaded: Boolean = false,
    val phase: RecoveryUpdatePhase = RecoveryUpdatePhase.IDLE,
    val release: AppRelease? = null,
    val progress: Float? = null,
    val download: ApkDownload? = null,
    val message: String? = null,
    val failure: RecoveryUpdateFailure? = null,
) {
    val busy get() = !configurationLoaded || phase in setOf(RecoveryUpdatePhase.CHECKING, RecoveryUpdatePhase.DOWNLOADING)
}

/** One owner for configuration/check/download; no Application or database initialization. */
class RecoveryUpdateController(
    private val installed: InstalledBuild,
    private val readConfiguration: suspend () -> RecoveryUpdateConfiguration,
    private val checkUpdate: suspend (UpdateChannel, InstalledBuild) -> UpdateCheck,
    private val downloadApk: suspend (AppRelease, (Long, Long) -> Unit) -> ApkDownload,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(RecoveryUpdateState(RecoveryUpdateConfiguration(installed.channel, installed.schemaVersion)))
    val state = mutableState.asStateFlow()

    suspend fun loadConfiguration() {
        if (!mutex.tryLock()) return
        try { refreshConfiguration() } finally { mutex.unlock() }
    }

    private suspend fun refreshConfiguration(): RecoveryUpdateConfiguration {
        val configuration = try { readConfiguration() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { RecoveryUpdateConfiguration(installed.channel, installed.schemaVersion, usedDefaults = true) }
        mutableState.update { it.copy(configuration = configuration, configurationLoaded = true) }
        return configuration
    }

    suspend fun check() {
        if (!mutex.tryLock()) return
        mutableState.update { it.copy(phase = RecoveryUpdatePhase.CHECKING, release = null,
            download = null, progress = null, failure = null, message = null) }
        try {
            val configuration = refreshConfiguration()
            val result = checkUpdate(configuration.channel, installed.copy(schemaVersion = maxOf(installed.schemaVersion, configuration.schemaVersion)))
            mutableState.update { it.copy(phase = if (result.release == null) RecoveryUpdatePhase.IDLE else RecoveryUpdatePhase.AVAILABLE,
                release = result.release, message = result.message) }
        } catch (cancelled: CancellationException) {
            mutableState.update { it.copy(phase = RecoveryUpdatePhase.IDLE) }
            throw cancelled
        } catch (_: Exception) {
            mutableState.update { it.copy(phase = RecoveryUpdatePhase.IDLE, failure = RecoveryUpdateFailure.CHECK) }
        } finally { mutex.unlock() }
    }

    suspend fun download(): ApkDownload? {
        if (!mutex.tryLock()) return null
        try {
            val release = state.value.release ?: return null
            mutableState.update { it.copy(phase = RecoveryUpdatePhase.DOWNLOADING, progress = null, download = null, failure = null) }
            val downloaded = downloadApk(release) { bytes, total ->
                mutableState.update { it.copy(progress = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else null) }
            }
            mutableState.update { it.copy(phase = RecoveryUpdatePhase.READY, progress = 1f, download = downloaded) }
            return downloaded
        } catch (cancelled: CancellationException) {
            mutableState.update { it.copy(phase = RecoveryUpdatePhase.AVAILABLE, progress = null) }
            throw cancelled
        } catch (_: Exception) {
            mutableState.update { it.copy(phase = RecoveryUpdatePhase.AVAILABLE, progress = null, failure = RecoveryUpdateFailure.DOWNLOAD) }
            return null
        } finally { mutex.unlock() }
    }

    fun installFailed() { mutableState.update { it.copy(failure = RecoveryUpdateFailure.INSTALL) } }
}

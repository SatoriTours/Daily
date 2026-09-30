package com.dailysatori.ui.feature.settings.diagnostics

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.BuildConfig
import com.dailysatori.R
import com.dailysatori.core.diagnostics.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

data class DiagnosticRecoveryState(val checking: Boolean = true, val needsRecovery: Boolean = false, val error: Int? = null)

/** Deliberately has no dependency on Koin, repositories or workers. */
class DiagnosticRecoveryViewModel(
    private val reader: DiagnosticRecoveryReader,
    private val context: Context,
    forceExport: Boolean,
) : ViewModel() {
    private val session = DiagnosticExportSession(reader::snapshot,
        DiagnosticExporter(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT))
    private val mutableState = MutableStateFlow(DiagnosticRecoveryState())
    val state = mutableState.asStateFlow()
    val exportState = session.state
    private val requestChannel = Channel<DiagnosticExportRequest>(Channel.BUFFERED)
    val requests = requestChannel.receiveAsFlow()
    private val startupChannel = Channel<Unit>(Channel.BUFFERED)
    val startup = startupChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            try {
                val needed = withContext(Dispatchers.IO) { reader.needsRecovery() }
                mutableState.value = DiagnosticRecoveryState(checking = false, needsRecovery = needed)
                if (!needed && !forceExport) startupChannel.send(Unit)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableState.value = DiagnosticRecoveryState(checking = false, needsRecovery = true, error = R.string.recovery_check_failed)
            }
        }
    }

    fun prepare(crash: Boolean) {
        viewModelScope.launch {
            mutableState.update { it.copy(error = null) }
            val request = session.prepare(crash) ?: return@launch
            requestChannel.send(request)
        }
    }

    fun save(token: String, uri: Uri?) {
        viewModelScope.launch {
            session.save(token, uri?.let { selected -> object : DiagnosticDestination {
                override fun open() = context.contentResolver.openOutputStream(selected, "wt")
                    ?: throw IOException("Document provider returned no output stream")
                override fun delete() = DocumentsContract.deleteDocument(context.contentResolver, selected)
            } })
        }
    }

    fun pickerFailed() {
        viewModelScope.launch {
            session.cancel()
            mutableState.update { it.copy(error = R.string.recovery_export_failed) }
        }
    }

    fun continueStartup() {
        if (mutableState.value.checking || exportState.value.phase in setOf(DiagnosticExportPhase.PREPARING,
                DiagnosticExportPhase.AWAITING_DESTINATION, DiagnosticExportPhase.SAVING)) return
        mutableState.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { reader.acknowledgeCrash() }
                startupChannel.send(Unit)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(checking = false, error = R.string.recovery_check_failed) } }
        }
    }

    override fun onCleared() {
        requestChannel.close()
        startupChannel.close()
        CoroutineScope(Dispatchers.IO).launch { session.cancel() }
        super.onCleared()
    }
}

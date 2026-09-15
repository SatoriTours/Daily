package com.dailysatori.ui.feature.settings.diagnostics

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.BuildConfig
import com.dailysatori.core.diagnostics.*
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

data class DiagnosticSettingsState(
    val bytes: Long? = null, val lastCrashMs: Long? = null,
    val health: DiagnosticHealth = DiagnosticHealth(), val messageKey: String? = null,
    val savedName: String? = null,
)

class DiagnosticSettingsViewModel(private val store: DiagnosticStore, private val context: Context) : ViewModel() {
    private val session = DiagnosticExportSession(store, DiagnosticExporter(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT))
    val exportState = session.state
    private val mutableState = MutableStateFlow(DiagnosticSettingsState())
    val state = mutableState.asStateFlow()
    private val requestsChannel = Channel<DiagnosticExportRequest>(Channel.BUFFERED)
    val requests = requestsChannel.receiveAsFlow()
    private val lifecycleGate = Any()
    private var generation = 0L
    private var prepareJob: Job? = null
    private var activeToken: String? = null

    fun refresh() { viewModelScope.launch { refreshInfo() } }

    private suspend fun refreshInfo() {
        try {
            val bytes = store.usageBytes()
            val lastCrash = store.latestCrashTime()
            mutableState.update { it.copy(bytes = bytes, lastCrashMs = lastCrash, health = store.health()) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutableState.update { it.copy(messageKey = "diagnostics.unavailable") } }
    }

    fun prepare(crash: Boolean) {
        if (exportState.value.phase in setOf(DiagnosticExportPhase.PREPARING,
                DiagnosticExportPhase.AWAITING_DESTINATION, DiagnosticExportPhase.SAVING)) return
        val ticket = synchronized(lifecycleGate) { generation }
        prepareJob = viewModelScope.launch {
            mutableState.update { it.copy(messageKey = null, savedName = null) }
            val request = session.prepare(crash) ?: return@launch
            val delivered = synchronized(lifecycleGate) {
                if (generation != ticket) false else {
                    activeToken = request.token
                    requestsChannel.trySend(request).isSuccess
                }
            }
            if (!delivered) session.cancel(request.token)
        }
    }

    fun onDestinationChosen(token: String, uri: Uri?) {
        viewModelScope.launch {
            val accepted = session.save(token, uri?.let(::destination))
            synchronized(lifecycleGate) { if (activeToken == token) activeToken = null }
            if (!accepted) mutableState.update { it.copy(messageKey = "diagnostics.expired") }
            if (accepted && uri != null && exportState.value.phase == DiagnosticExportPhase.SAVED) {
                val name = withContext(Dispatchers.IO) { displayName(uri) }
                mutableState.update { it.copy(savedName = name) }
            }
            refreshInfo()
        }
    }

    fun pickerFailed() {
        viewModelScope.launch {
            session.cancel()
            mutableState.update { it.copy(messageKey = "diagnostics.pickerFailed") }
        }
    }

    fun clear() {
        viewModelScope.launch {
            session.clear()
            mutableState.update { it.copy(messageKey = null, savedName = null) }
            refreshInfo()
        }
    }

    fun releaseOnLeave() {
        val abandoned = synchronized(lifecycleGate) {
            generation++
            while (requestsChannel.tryReceive().isSuccess) Unit
            activeToken.also { activeToken = null }
        }
        prepareJob?.cancel()
        // Token-bound cleanup cannot cancel a newer export after the screen is reopened.
        if (abandoned != null) CoroutineScope(Dispatchers.IO).launch { session.cancel(abandoned) }
    }

    override fun onCleared() { releaseOnLeave(); requestsChannel.close(); super.onCleared() }

    private fun destination(uri: Uri) = object : DiagnosticDestination {
        override fun open(): OutputStream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Document provider returned no output stream")
        override fun delete(): Boolean = DocumentsContract.deleteDocument(context.contentResolver, uri)
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
}

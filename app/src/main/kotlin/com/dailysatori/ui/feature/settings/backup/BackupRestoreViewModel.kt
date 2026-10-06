package com.dailysatori.ui.feature.settings.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.platform.FileManager
import com.dailysatori.service.backup.BackupEntry
import com.dailysatori.service.backup.BackupService
import com.dailysatori.service.i18n.I18nService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class BackupRestoreState(
    val backupList: List<String> = emptyList(),
    val selectedBackupIndex: Int = -1,
    val selectedFileUri: String? = null,
    val selectedFileName: String = "",
    val isLoading: Boolean = false,
    val isRestoring: Boolean = false,
    val isRestorePending: Boolean = false,
    val restoreProgress: Float = 0f,
    val statusMessage: String = "",
    val successMessage: String = "",
    val errorMessage: String = "",
) {
    val selectedName: String get() = if (selectedFileUri != null) selectedFileName else backupList.getOrNull(selectedBackupIndex).orEmpty()
}

internal interface BackupRestoreClient {
    val progress: StateFlow<Double>
    val lastMessage: StateFlow<String>
    suspend fun listBackups(): List<BackupEntry>
    fun displayName(uri: String): String
    suspend fun restore(name: String, password: String): Boolean
    suspend fun restoreFile(uri: String, password: String): Boolean
}

class BackupRestoreViewModel internal constructor(
    private val backupService: BackupRestoreClient,
    private val translate: (String) -> String,
    scope: CoroutineScope? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    constructor(backupService: BackupService, fileManager: FileManager, i18n: I18nService) : this(
        object : BackupRestoreClient {
            override val progress = backupService.progress
            override val lastMessage = backupService.lastMessage
            override suspend fun listBackups() = backupService.listBackups()
            override fun displayName(uri: String) = fileManager.displayNameForFileUri(uri)
            override suspend fun restore(name: String, password: String) = backupService.restore(name, password)
            override suspend fun restoreFile(uri: String, password: String) = backupService.restoreFile(uri, password)
        }, { i18n.t(it) },
    )

    private val workScope = scope ?: viewModelScope
    private val _state = MutableStateFlow(BackupRestoreState())
    val state: StateFlow<BackupRestoreState> = _state.asStateFlow()

    init {
        loadBackupFiles()
        workScope.launch(dispatcher) {
            backupService.progress.collect { progress ->
                if (_state.value.isRestoring) _state.update { it.copy(restoreProgress = progress.toFloat()) }
            }
        }
        workScope.launch(dispatcher) {
            backupService.lastMessage.collect { message ->
                if (_state.value.isRestoring && message.isNotBlank()) {
                    val phase = when {
                        message.startsWith("Reading") -> "reading"
                        message.startsWith("Decrypting") -> "decrypting"
                        message.startsWith("Extracting") -> "extracting"
                        message.startsWith("Validating") -> "validating"
                        message.startsWith("Preparing restore") -> "preparing"
                        message.startsWith("Staging") -> "staging"
                        message.startsWith("Restore ready") -> "restart"
                        else -> null
                    }
                    if (phase != null) _state.update {
                        it.copy(statusMessage = if (phase == "restart") readyMessage(message) else text(phase))
                    }
                }
            }
        }
    }

    fun loadBackupFiles() {
        if (_state.value.isRestoring || _state.value.isRestorePending || _state.value.isLoading) return
        _state.update { it.copy(isLoading = true) }
        workScope.launch(dispatcher) {
            try {
                val backups = backupService.listBackups().map { it.name }
                _state.update { current ->
                    val previous = current.backupList.getOrNull(current.selectedBackupIndex)
                    val index = if (current.selectedFileUri != null || backups.isEmpty()) -1 else backups.indexOf(previous).coerceAtLeast(0)
                    current.copy(backupList = backups, selectedBackupIndex = index, isLoading = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = text("load_failed")) }
            }
        }
    }

    fun selectFile(uri: String) {
        if (_state.value.isRestoring || _state.value.isRestorePending) return
        _state.update { it.copy(selectedFileUri = uri, selectedFileName = text("encrypted_file"), selectedBackupIndex = -1, errorMessage = "", successMessage = "") }
        workScope.launch(dispatcher) {
            val name = runCatching { backupService.displayName(uri) }.getOrDefault(text("encrypted_file"))
            _state.update { if (it.selectedFileUri == uri) it.copy(selectedFileName = name) else it }
        }
    }

    fun selectBackupIndex(index: Int) {
        if (_state.value.isRestoring || _state.value.isRestorePending || index !in _state.value.backupList.indices) return
        _state.update { it.copy(selectedBackupIndex = index, selectedFileUri = null, selectedFileName = "", errorMessage = "", successMessage = "") }
    }

    fun getBackupTime(path: String): String {
        val timestamp = Regex("""^daily_satori_backup_(\d{4}-\d{2}-\d{2}-\d{2}-\d{2}-\d{2})(?:_hint_[^./]{3})?\.zip\.enc$""")
            .matchEntire(path)?.groupValues?.get(1) ?: return path
        return timestamp.take(10) + " " + timestamp.substring(11).replace('-', ':')
    }

    fun restoreBackup(password: String) {
        val selection = _state.value
        if (selection.isRestoring || selection.isRestorePending) return
        val invalid = when {
            selection.selectedName.isBlank() -> "select_required"
            password.isBlank() -> "password_required"
            else -> null
        }
        if (invalid != null) { _state.update { it.copy(errorMessage = text(invalid)) }; return }
        _state.update { it.copy(isRestoring = true, restoreProgress = 0f, statusMessage = text("reading"), successMessage = "", errorMessage = "") }
        workScope.launch(dispatcher) {
            try {
                val success = selection.selectedFileUri?.let { backupService.restoreFile(it, password) }
                    ?: backupService.restore(selection.selectedName, password)
                if (success) _state.update {
                    val message = readyMessage(backupService.lastMessage.value)
                    it.copy(isRestoring = false, isRestorePending = true, successMessage = message, statusMessage = message)
                }
                else fail(backupService.lastMessage.value)
            } catch (cancelled: CancellationException) {
                _state.update { it.copy(isRestoring = false) }
                throw cancelled
            } catch (failure: Exception) {
                fail(failure.message.orEmpty())
            }
        }
    }

    private fun fail(message: String) {
        val key = when {
            message.contains("password", true) || message.contains("密码") -> "password_error"
            message.contains("space", true) || message.contains("ENOSPC") -> "space_error"
            message.contains("读取") || message.contains("permission", true) || message.contains("not found", true) || message.contains("不存在") -> "access_error"
            message.contains("版本") -> "version_error"
            message.contains("校验") || message.contains("缺失") || message.contains("不完整") || message.contains("完整性") || message.contains("非法") -> "invalid_error"
            else -> "failed"
        }
        _state.update { it.copy(isRestoring = false, statusMessage = "", successMessage = "", errorMessage = text(key)) }
    }

    private fun readyMessage(message: String): String =
        text(if (message.contains("restart manually")) "restart_manual" else "restart") +
            if (message.contains("legacy backup")) "\n" + text("legacy_life") else ""

    private fun text(key: String) = translate("backup_restore.$key")
}

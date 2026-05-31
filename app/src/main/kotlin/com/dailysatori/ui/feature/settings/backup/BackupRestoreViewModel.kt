package com.dailysatori.ui.feature.settings.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.service.backup.BackupService
import com.dailysatori.service.backup.backupPasswordHint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupRestoreState(
    val backupList: List<String> = emptyList(),
    val selectedBackupIndex: Int = -1,
    val isLoading: Boolean = false,
    val isRestoring: Boolean = false,
    val restoreProgress: Float = 0f,
    val statusMessage: String = "",
    val successMessage: String = "",
    val errorMessage: String = "",
)

class BackupRestoreViewModel(
    private val backupService: BackupService,
) : ViewModel() {
    private val _state = MutableStateFlow(BackupRestoreState())
    val state: StateFlow<BackupRestoreState> = _state.asStateFlow()

    init {
        loadBackupFiles()
        viewModelScope.launch(Dispatchers.IO) {
            backupService.progress.collect { progress ->
                _state.update { it.copy(restoreProgress = progress.toFloat()) }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            backupService.lastMessage.collect { message ->
                applyRestoreMessage(message)
            }
        }
    }

    fun loadBackupFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, errorMessage = "") }
            try {
                val backups = backupService.listBackups().map { it.name }
                val message = if (backups.isEmpty()) "暂无备份信息" else ""
                _state.update { it.copy(backupList = backups, isLoading = false, errorMessage = message) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "加载失败") }
            }
        }
    }

    fun selectBackupIndex(index: Int) {
        _state.update { it.copy(selectedBackupIndex = index) }
    }

    fun getBackupTime(path: String): String {
        return try {
            val parts = path.split("daily_satori_backup_")
            if (parts.size >= 2) {
                parts[1].substringBefore("_hint_")
            } else {
                path
            }
        } catch (_: Exception) {
            path
        }
    }

    fun getPasswordHint(path: String): String = backupPasswordHint(path) ?: "无提示"

    fun restoreBackup(password: String) {
        val backupName = selectedBackupName(password) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    isRestoring = true,
                    restoreProgress = 0f,
                    statusMessage = "准备恢复...",
                    successMessage = "",
                    errorMessage = "",
                )
            }
            try {
                val success = backupService.restore(backupName, password)
                if (success) {
                    _state.update { it.copy(successMessage = "恢复完成", errorMessage = "") }
                } else {
                    _state.update { it.copy(errorMessage = backupService.lastMessage.value.ifBlank { "恢复失败，请检查密码" }) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message ?: "恢复失败") }
            } finally {
                _state.update { it.copy(isRestoring = false) }
            }
        }
    }

    private fun selectedBackupName(password: String): String? {
        val current = _state.value
        val index = current.selectedBackupIndex
        if (index < 0 || index >= current.backupList.size) {
            _state.update { it.copy(successMessage = "", statusMessage = "", errorMessage = "未选择备份文件") }
            return null
        }
        if (password.isBlank()) {
            _state.update { it.copy(successMessage = "", statusMessage = "", errorMessage = "请输入备份密码") }
            return null
        }
        return current.backupList[index]
    }

    private fun applyRestoreMessage(message: String) {
        if (message.isBlank()) return
        if (!_state.value.isRestoring && !message.startsWith("Restore ")) return
        val localized = restoreStatusText(message)
        _state.update {
            when {
                message.startsWith("Restore completed") -> it.copy(successMessage = "恢复完成", statusMessage = localized, errorMessage = "")
                message.startsWith("Restore failed") -> it.copy(errorMessage = localized, statusMessage = "")
                else -> it.copy(statusMessage = localized)
            }
        }
    }

    private fun restoreStatusText(message: String): String {
        return when {
            message.startsWith("Decrypting") -> "正在解密备份..."
            message.startsWith("Extracting") -> "正在解压备份..."
            message.startsWith("Restore completed") -> "恢复完成"
            message.startsWith("Restore failed") -> message.replace("Restore failed:", "恢复失败：")
            else -> message
        }
    }
}

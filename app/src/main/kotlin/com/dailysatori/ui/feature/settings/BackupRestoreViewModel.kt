package com.dailysatori.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.service.backup.BackupService
import com.dailysatori.service.backup.backupPasswordHint
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
    val errorMessage: String = "",
)

class BackupRestoreViewModel(
    private val backupService: BackupService,
) : ViewModel() {
    private val _state = MutableStateFlow(BackupRestoreState())
    val state: StateFlow<BackupRestoreState> = _state.asStateFlow()

    init {
        loadBackupFiles()
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

    suspend fun restoreBackup(password: String): Boolean {
        _state.update { it.copy(isRestoring = true) }
        return try {
            val index = _state.value.selectedBackupIndex
            if (index < 0 || index >= _state.value.backupList.size) {
                _state.update { it.copy(isRestoring = false, errorMessage = "未选择备份文件") }
                return false
            }
            if (password.isBlank()) {
                _state.update { it.copy(isRestoring = false, errorMessage = "请输入备份密码") }
                return false
            }
            val backupName = _state.value.backupList[index]
            val success = backupService.restore(backupName, password)
            if (!success) _state.update { it.copy(errorMessage = "恢复失败，请检查密码") }
            _state.update { it.copy(isRestoring = false) }
            success
        } catch (e: Exception) {
            _state.update { it.copy(isRestoring = false, errorMessage = e.message ?: "恢复失败") }
            false
        }
    }
}

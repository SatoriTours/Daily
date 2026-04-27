package com.dailysatori.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.platform.FileManager
import com.dailysatori.service.setting.SettingService
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
    private val settingService: SettingService,
    private val fileManager: FileManager,
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
                val backupDirPath = settingService.getString("backup_directory")
                if (backupDirPath.isNullOrEmpty()) {
                    _state.update { it.copy(isLoading = false, errorMessage = "请先在备份设置中选择备份目录") }
                    return@launch
                }
                val dirs = fileManager.listFiles(backupDirPath)
                    .filter { it.contains("daily_satori_backup_") }
                    .sortedDescending()
                _state.update { it.copy(backupList = dirs, isLoading = false) }
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
                parts[1].replace("T", " ").take(19)
            } else {
                path.substringAfterLast("/")
            }
        } catch (_: Exception) {
            path.substringAfterLast("/")
        }
    }

    suspend fun restoreBackup(): Boolean {
        _state.update { it.copy(isRestoring = true) }
        return try {
            val index = _state.value.selectedBackupIndex
            if (index < 0 || index >= _state.value.backupList.size) {
                _state.update { it.copy(isRestoring = false, errorMessage = "未选择备份文件") }
                return false
            }
            val backupPath = _state.value.backupList[index]
            val dbSource = "$backupPath/database.db"
            if (!fileManager.exists(dbSource)) {
                _state.update { it.copy(isRestoring = false, errorMessage = "备份文件不存在") }
                return false
            }
            val dbPath = "${fileManager.getAppDataDir()}/daily_satori.db"
            fileManager.copyFile(dbSource, dbPath)
            _state.update { it.copy(isRestoring = false) }
            true
        } catch (e: Exception) {
            _state.update { it.copy(isRestoring = false, errorMessage = e.message ?: "恢复失败") }
            false
        }
    }
}

package com.dailysatori.viewmodel

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.platform.FileManager
import com.dailysatori.service.backup.BackupService
import com.dailysatori.service.setting.SettingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupSettingsState(
    val backupDirectory: String = "",
    val isBackingUp: Boolean = false,
    val backupProgress: Float = 0f,
    val error: String? = null,
)

class BackupSettingsViewModel(
    private val settingService: SettingService,
    private val backupService: BackupService,
    private val fileManager: FileManager,
) : ViewModel() {
    private val _state = MutableStateFlow(BackupSettingsState())
    val state: StateFlow<BackupSettingsState> = _state.asStateFlow()

    init {
        loadSettings()
        viewModelScope.launch(Dispatchers.IO) {
            backupService.isBackingUp.collect { backingUp ->
                _state.update { it.copy(isBackingUp = backingUp) }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            backupService.progress.collect { progress ->
                _state.update { it.copy(backupProgress = progress.toFloat()) }
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val dir = settingService.getString("backup_directory")
            _state.update { it.copy(backupDirectory = dir) }
        }
    }

    fun saveBackupDirectory(uri: Uri, activity: Activity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val path = uri.toString()
                settingService.set("backup_directory", path)
                _state.update { it.copy(backupDirectory = path) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun startBackup(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(error = null) }
            try {
                val result = backupService.backupNow()
                onComplete(result)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
                onComplete(false)
            }
        }
    }
}

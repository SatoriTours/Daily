package com.dailysatori.ui.feature.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.config.SettingKeys
import com.dailysatori.core.worker.BackupScheduler
import com.dailysatori.platform.FileManager
import com.dailysatori.service.backup.BackupPasswordStore
import com.dailysatori.service.backup.MinBackupPasswordLength
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
    val backupDirectoryDisplay: String = "",
    val passwordInput: String = "",
    val hasBackupPassword: Boolean = false,
    val isBackingUp: Boolean = false,
    val backupProgress: Float = 0f,
    val error: String? = null,
    val message: String? = null,
)

class BackupSettingsViewModel(
    private val settingService: SettingService,
    private val backupService: BackupService,
    private val fileManager: FileManager,
    private val passwordStore: BackupPasswordStore,
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
            val dir = settingService.getString(SettingKeys.backupDir)
            _state.update {
                it.copy(
                    backupDirectory = dir,
                    backupDirectoryDisplay = dir.takeIf { value -> value.isNotBlank() }
                        ?.let { value -> fileManager.displayNameForUri(value) }
                        .orEmpty(),
                    hasBackupPassword = passwordStore.hasPassword(),
                )
            }
        }
    }

    fun saveBackupDirectory(uri: Uri, activity: Activity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val path = uri.toString()
                activity.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                settingService.set(SettingKeys.backupDir, path)
                BackupScheduler(activity.applicationContext).ensureScheduled()
                _state.update {
                    it.copy(
                        backupDirectory = path,
                        backupDirectoryDisplay = fileManager.displayNameForUri(path),
                        message = "备份目录已保存",
                        error = null,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun updatePasswordInput(value: String) {
        _state.update { it.copy(passwordInput = value, error = null, message = null) }
    }

    fun saveBackupPassword() {
        viewModelScope.launch(Dispatchers.IO) {
            val password = _state.value.passwordInput
            if (password.length < MinBackupPasswordLength) {
                _state.update { it.copy(error = "备份密码至少需要 10 位") }
                return@launch
            }
            passwordStore.save(password)
            _state.update {
                it.copy(
                    passwordInput = "",
                    hasBackupPassword = true,
                    message = "备份密码已保存",
                    error = null,
                )
            }
        }
    }

    fun startBackup(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(error = null) }
            try {
                if (_state.value.backupDirectory.isBlank()) {
                    _state.update { it.copy(error = "请先选择备份目录") }
                    onComplete(false)
                    return@launch
                }
                if (!passwordStore.hasPassword()) {
                    _state.update { it.copy(error = "请先设置备份密码") }
                    onComplete(false)
                    return@launch
                }
                val result = backupService.backupNow()
                onComplete(result)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
                onComplete(false)
            }
        }
    }
}

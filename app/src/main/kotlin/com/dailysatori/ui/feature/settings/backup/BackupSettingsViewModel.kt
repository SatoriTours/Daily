package com.dailysatori.ui.feature.settings.backup

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
import com.dailysatori.service.backup.BackupVerificationResult
import com.dailysatori.service.backup.BackupVerificationStage
import com.dailysatori.service.backup.BackupVerificationStatus
import com.dailysatori.service.backup.BackupVerificationIssue
import com.dailysatori.service.setting.SettingService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.datetime.Clock
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
    val isSavingDirectory: Boolean = false,
    val backupProgress: Float = 0f,
    val error: String? = null,
    val message: String? = null,
    val isVerifying: Boolean = false,
    val verificationStage: BackupVerificationStage = BackupVerificationStage.SELECTING,
    val verificationFileName: String? = null,
    val verificationResult: BackupVerificationResult? = null,
    val verificationPasswordInput: String = "",
)

internal interface BackupSettingsClient {
    val isBackingUp: StateFlow<Boolean>
    val progress: StateFlow<Double>
    val verificationStage: StateFlow<BackupVerificationStage>
    val verificationFileName: StateFlow<String?>
    fun loadDirectory(): String
    fun saveDirectory(path: String)
    fun displayName(path: String): String
    fun hasPassword(): Boolean
    fun savePassword(password: String)
    suspend fun backupNow(): Boolean
    suspend fun verifyLatestBackup(password: String?, expectedName: String?): BackupVerificationResult
}

class BackupSettingsViewModel internal constructor(
    private val client: BackupSettingsClient,
    scope: CoroutineScope? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    constructor(
        settingService: SettingService,
        backupService: BackupService,
        fileManager: FileManager,
        passwordStore: BackupPasswordStore,
    ) : this(object : BackupSettingsClient {
        override val isBackingUp = backupService.isBackingUp
        override val progress = backupService.progress
        override val verificationStage = backupService.verificationStage
        override val verificationFileName = backupService.verificationFileName
        override fun loadDirectory() = settingService.getString(SettingKeys.backupDir)
        override fun saveDirectory(path: String) = settingService.set(SettingKeys.backupDir, path)
        override fun displayName(path: String) = fileManager.displayNameForUri(path)
        override fun hasPassword() = passwordStore.hasPassword()
        override fun savePassword(password: String) = passwordStore.save(password)
        override suspend fun backupNow() = backupService.backupNow()
        override suspend fun verifyLatestBackup(password: String?, expectedName: String?) = backupService.verifyLatestBackup(password, expectedName)
    })

    private val workScope = scope ?: viewModelScope
    private var verificationJob: Job? = null
    private val _state = MutableStateFlow(BackupSettingsState())
    val state: StateFlow<BackupSettingsState> = _state.asStateFlow()

    init {
        loadSettings()
        workScope.launch(dispatcher) {
            client.isBackingUp.collect { backingUp ->
                _state.update { it.copy(isBackingUp = backingUp) }
            }
        }
        workScope.launch(dispatcher) {
            client.progress.collect { progress ->
                _state.update { it.copy(backupProgress = progress.toFloat()) }
            }
        }
        workScope.launch(dispatcher) {
            client.verificationStage.collect { stage ->
                _state.update { if (it.isVerifying) it.copy(verificationStage = stage) else it }
            }
        }
        workScope.launch(dispatcher) {
            client.verificationFileName.collect { name ->
                _state.update { if (it.isVerifying) it.copy(verificationFileName = name) else it }
            }
        }
    }

    private fun loadSettings() {
        workScope.launch {
            val dir = client.loadDirectory()
            _state.update {
                it.copy(
                    backupDirectory = dir,
                    backupDirectoryDisplay = dir.takeIf { value -> value.isNotBlank() }
                        ?.let { value -> client.displayName(value) }
                        .orEmpty(),
                    hasBackupPassword = client.hasPassword(),
                )
            }
        }
    }

    fun saveBackupDirectory(uri: Uri, activity: Activity) = saveBackupDirectory(
        uri.toString(),
        {
            activity.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        },
        { BackupScheduler(activity.applicationContext).ensureScheduled() },
    )

    internal fun saveBackupDirectory(path: String, grantPermission: () -> Unit, schedule: () -> Unit) {
        if (_state.value.isSavingDirectory || _state.value.isVerifying || client.isBackingUp.value) return
        // Mark busy before dispatching IO so an immediate backup cannot read the old setting.
        _state.update { it.copy(isSavingDirectory = true, error = null, message = null,
            verificationResult = null, verificationPasswordInput = "") }
        workScope.launch(dispatcher) {
            try {
                grantPermission()
                val displayName = client.displayName(path)
                client.saveDirectory(path)
                _state.update {
                    it.copy(
                        backupDirectory = path,
                        backupDirectoryDisplay = displayName,
                        message = "备份目录已保存",
                        error = null,
                    )
                }
                // Scheduling is separate from persistence: failure must not leave stale UI state.
                schedule()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(isSavingDirectory = false) }
            }
        }
    }

    fun verifyLatestBackup() = startVerification(null, null)

    fun retryVerification() {
        val snapshot = _state.value
        if (snapshot.verificationPasswordInput.isBlank()) return
        startVerification(snapshot.verificationPasswordInput, snapshot.verificationResult?.fileName)
    }

    fun cancelVerification() { verificationJob?.cancel() }

    fun updateVerificationPassword(value: String) {
        if (!_state.value.isVerifying) _state.update { it.copy(verificationPasswordInput = value) }
    }

    private fun startVerification(password: String?, expectedName: String?) {
        if (_state.value.isSavingDirectory || _state.value.isVerifying || client.isBackingUp.value) return
        // Claim the UI before dispatch, including the interval before the service takes its lock.
        _state.update { it.copy(isVerifying = true, verificationResult = null, verificationPasswordInput = "",
            verificationFileName = null, verificationStage = BackupVerificationStage.SELECTING,
            backupProgress = 0f, error = null, message = null) }
        val job = workScope.launch(dispatcher) {
            try {
                val result = client.verifyLatestBackup(password, expectedName)
                _state.update { it.copy(verificationResult = result, verificationFileName = result.fileName,
                    verificationStage = result.stage) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(verificationResult = verificationFailure(it, BackupVerificationStatus.FAILED,
                    BackupVerificationIssue.CHECK_FAILED)) }
            }
        }
        verificationJob = job
        job.invokeOnCompletion { cause ->
            _state.update { snapshot -> snapshot.copy(isVerifying = false, verificationPasswordInput = "",
                verificationResult = if (cause is CancellationException) verificationFailure(snapshot,
                    BackupVerificationStatus.INCOMPLETE, BackupVerificationIssue.CANCELLED) else snapshot.verificationResult) }
        }
    }

    private fun verificationFailure(snapshot: BackupSettingsState, status: BackupVerificationStatus, issue: BackupVerificationIssue) =
        BackupVerificationResult(status, snapshot.verificationStage, Clock.System.now().toString(),
            fileName = snapshot.verificationFileName, issue = issue)

    fun updatePasswordInput(value: String) {
        _state.update { it.copy(passwordInput = value, error = null, message = null) }
    }

    fun saveBackupPassword() {
        if (_state.value.isVerifying || client.isBackingUp.value) return
        workScope.launch(dispatcher) {
            if (_state.value.isVerifying || client.isBackingUp.value) return@launch
            val password = _state.value.passwordInput
            if (password.length < MinBackupPasswordLength) {
                _state.update { it.copy(error = "备份密码至少需要 10 位") }
                return@launch
            }
            client.savePassword(password)
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
        if (_state.value.isSavingDirectory || _state.value.isVerifying) {
            onComplete(false)
            return
        }
        workScope.launch(dispatcher) {
            if (_state.value.isSavingDirectory || _state.value.isVerifying) {
                onComplete(false)
                return@launch
            }
            _state.update { it.copy(error = null, verificationResult = null, verificationPasswordInput = "") }
            try {
                if (_state.value.backupDirectory.isBlank()) {
                    _state.update { it.copy(error = "请先选择备份目录") }
                    onComplete(false)
                    return@launch
                }
                if (!client.hasPassword()) {
                    _state.update { it.copy(error = "请先设置备份密码") }
                    onComplete(false)
                    return@launch
                }
                val result = client.backupNow()
                onComplete(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
                onComplete(false)
            }
        }
    }
}

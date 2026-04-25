package com.dailysatori.service.backup

import co.touchlab.kermit.Logger
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.platform.FileManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BackupService(
    private val fileManager: FileManager,
    private val settingRepo: SettingRepository,
) {
    private val log = Logger.withTag("Backup")
    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp: StateFlow<Boolean> = _isBackingUp
    private val _progress = MutableStateFlow(0.0)
    val progress: StateFlow<Double> = _progress

    suspend fun backupNow(): Boolean {
        _isBackingUp.value = true
        _progress.value = 0.1
        return try {
            val backupDir = fileManager.getBackupDir()
            val timestamp = kotlinx.datetime.Clock.System.now().toString().replace(":", "-").take(19)
            val dir = "$backupDir/daily_satori_backup_$timestamp"
            fileManager.createDirectory(dir)
            
            val dbPath = "${fileManager.getAppDataDir()}/daily_satori.db"
            if (fileManager.exists(dbPath)) {
                fileManager.copyFile(dbPath, "$dir/database.db")
            }
            
            val imagesDir = fileManager.getImagesDir()
            if (fileManager.exists(imagesDir)) {
                val destDir = "$dir/images"
                fileManager.createDirectory(destDir)
                fileManager.listFiles(imagesDir).forEach { src ->
                    val name = src.substringAfterLast("/")
                    fileManager.copyFile(src, "$destDir/$name")
                }
            }
            
            _progress.value = 1.0
            settingRepo.upsert("last_backup_time", kotlinx.datetime.Clock.System.now().toEpochMilliseconds().toString())
            log.i { "Backup completed: $dir" }
            true
        } catch (e: Exception) {
            log.e(e) { "Backup failed" }
            false
        } finally {
            _isBackingUp.value = false
        }
    }
}

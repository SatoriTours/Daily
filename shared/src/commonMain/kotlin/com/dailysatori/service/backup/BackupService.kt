package com.dailysatori.service.backup

import co.touchlab.kermit.Logger
import com.dailysatori.config.BackupConfig
import com.dailysatori.config.DatabaseConfig
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.platform.FileManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Clock

data class BackupEntry(
    val name: String,
    val timestamp: String,
    val path: String,
    val size: Long,
)

const val MinBackupPasswordLength = 10

internal fun backupFileName(timestamp: String, password: String): String {
    val hint = password.takeLast(3)
    return "daily_satori_backup_${timestamp}_hint_${hint}.zip.enc"
}

fun backupPasswordHint(name: String): String? {
    return Regex("""_hint_([^./]{3})\.zip\.enc$""").find(name)?.groupValues?.get(1)
}

class BackupService(
    private val fileManager: FileManager,
    private val settingRepo: SettingRepository,
    private val passwordStore: BackupPasswordStore,
) {
    private val log = Logger.withTag("Backup")
    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp: StateFlow<Boolean> = _isBackingUp
    private val _progress = MutableStateFlow(0.0)
    val progress: StateFlow<Double> = _progress
    private val _lastMessage = MutableStateFlow("")
    val lastMessage: StateFlow<String> = _lastMessage

    suspend fun backupNow(): Boolean {
        if (_isBackingUp.value) return false
        _isBackingUp.value = true
        _progress.value = 0.0
        return try {
            val backupDir = selectedBackupDir() ?: return failBackup("请先选择备份目录")
            val password = currentBackupPassword() ?: return failBackup("请先设置备份密码")
            val timestamp = Clock.System.now().toString().replace(Regex("[:T]"), "-").take(19)
            val zipName = "daily_satori_backup_$timestamp.zip"
            val tempRoot = fileManager.getBackupDir()
            val zipPath = "$tempRoot/$zipName"
            val tempDir = "$tempRoot/temp_$timestamp"
            fileManager.createDirectory(tempDir)

            _progress.value = 0.05
            _lastMessage.value = "Preparing backup..."

            val filesToBackup = mutableListOf<String>()

            // Database file
            val dbPath = fileManager.getDatabasePath()
            if (fileManager.exists(dbPath)) {
                filesToBackup.add(dbPath)
            } else {
                log.w { "Database file not found: $dbPath" }
                return failBackup("数据库文件不存在，无法创建完整备份")
            }
            _progress.value = 0.1

            // Images directory
            _lastMessage.value = "Collecting images..."
            val imagesDir = fileManager.getImagesDir()
            if (fileManager.exists(imagesDir)) {
                val imageFiles = fileManager.listFiles(imagesDir)
                filesToBackup.addAll(imageFiles)
            }
            _progress.value = 0.3

            // Diary images directory
            _lastMessage.value = "Collecting diary images..."
            val diaryImagesDir = fileManager.getDiaryImagesDir()
            if (fileManager.exists(diaryImagesDir)) {
                val diaryImageFiles = fileManager.listFiles(diaryImagesDir)
                filesToBackup.addAll(diaryImageFiles)
            }
            _progress.value = 0.5

            if (filesToBackup.isEmpty()) {
                log.w { "No files to backup" }
                _isBackingUp.value = false
                return false
            }

            _lastMessage.value = "Creating ZIP archive (${filesToBackup.size} files)..."
            _progress.value = 0.6

            val appDataDir = fileManager.getAppDataDir()
            fileManager.createZip(appDataDir, zipPath, filesToBackup)

            _progress.value = 0.7
            _lastMessage.value = "Encrypting backup..."

            val finalName = backupFileName(timestamp, password)
            val encPath = "$tempRoot/$finalName"
            fileManager.encryptFile(zipPath, encPath, password)
            fileManager.deleteFile(zipPath)
            fileManager.writeFileToDirectory(backupDir, finalName, encPath)
            fileManager.deleteFile(encPath)

            _progress.value = 0.9

            // Record backup time
            settingRepo.upsert("last_backup_time", Clock.System.now().toEpochMilliseconds().toString())

            // Clean up old backups (keep last 10)
            val allBackups = listBackups()
            if (allBackups.size > 10) {
                val toDelete = allBackups.sortedByDescending { it.timestamp }.drop(10)
                toDelete.forEach { fileManager.deleteFileFromDirectory(backupDir, it.name) }
            }

            _progress.value = 1.0
            _lastMessage.value = "Backup completed: $finalName"
            log.i { "Backup completed: $finalName" }
            true
        } catch (e: Exception) {
            log.e(e) { "Backup failed" }
            _lastMessage.value = "Backup failed: ${e.message}"
            false
        } finally {
            _isBackingUp.value = false
        }
    }

    suspend fun restore(name: String, password: String): Boolean {
        _isBackingUp.value = true
        _progress.value = 0.0
        return try {
            val backupDir = selectedBackupDir() ?: return failRestore("请先选择备份目录")
            if (password.isBlank()) return failRestore("请输入备份密码")

            _progress.value = 0.1
            _lastMessage.value = "Decrypting backup..."

            val tempDir = "${fileManager.getAppDataDir()}/restore_temp"
            deleteRecursive(tempDir)
            fileManager.createDirectory(tempDir)
            val encPath = "$tempDir/$name"
            if (!fileManager.readFileFromDirectory(backupDir, name, encPath)) {
                log.w { "Backup file not found: $name" }
                return failRestore("备份文件不存在")
            }
            val tempZip = "$tempDir/backup.zip"
            fileManager.decryptFile(encPath, tempZip, password)
            fileManager.deleteFile(encPath)
            _progress.value = 0.4
            _lastMessage.value = "Extracting backup..."

            fileManager.extractZip(tempZip, tempDir)
            fileManager.deleteFile(tempZip)
            _progress.value = 0.6

            // Move database file
            val dbSrc = "$tempDir/${DatabaseConfig.name}"
            val dbDest = fileManager.getDatabasePath()
            if (fileManager.exists(dbSrc)) {
                fileManager.copyFile(dbSrc, dbDest)
            } else {
                return failRestore("备份中未找到数据库文件")
            }
            _progress.value = 0.7

            // Move images
            val tempImagesDir = "$tempDir/images"
            if (fileManager.exists(tempImagesDir)) {
                val imagesDir = fileManager.getImagesDir()
                fileManager.listFiles(tempImagesDir).forEach { src ->
                    val name = src.substringAfterLast("/")
                    fileManager.copyFile(src, "$imagesDir/$name")
                }
            }
            _progress.value = 0.85

            // Move diary images
            val tempDiaryImagesDir = "$tempDir/diary_images"
            if (fileManager.exists(tempDiaryImagesDir)) {
                val diaryImagesDir = fileManager.getDiaryImagesDir()
                fileManager.listFiles(tempDiaryImagesDir).forEach { src ->
                    val name = src.substringAfterLast("/")
                    fileManager.copyFile(src, "$diaryImagesDir/$name")
                }
            }
            _progress.value = 0.95

            // Clean up temp
            deleteRecursive(tempDir)

            _progress.value = 1.0
            _lastMessage.value = "Restore completed: $name"
            log.i { "Restore completed: $name" }
            fileManager.restartApp()
            true
        } catch (e: Exception) {
            log.e(e) { "Restore failed" }
            _lastMessage.value = "Restore failed: ${e.message}"
            false
        } finally {
            _isBackingUp.value = false
        }
    }

    fun listBackups(): List<BackupEntry> {
        return try {
            val backupDir = selectedBackupDir() ?: return emptyList()
            fileManager.listBackupFilesInDirectory(backupDir)
                .filter { it.endsWith(BackupConfig.fileExtension) }
                .map { name ->
                    BackupEntry(
                        name = name,
                        timestamp = name.removePrefix("daily_satori_backup_").removeSuffix(BackupConfig.fileExtension),
                        path = name,
                        size = 0L,
                    )
                }
                .sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            log.e(e) { "Failed to list backups" }
            emptyList()
        }
    }

    fun deleteBackup(name: String): Boolean {
        return try {
            val backupDir = selectedBackupDir() ?: return false
            if (fileManager.deleteFileFromDirectory(backupDir, name)) {
                log.i { "Deleted backup: $name" }
            }
            true
        } catch (e: Exception) {
            log.e(e) { "Failed to delete backup: $name" }
            false
        }
    }

    private fun deleteRecursive(dir: String) {
        try {
            fileManager.listFiles(dir).forEach { fileManager.deleteFile(it) }
            fileManager.deleteFile(dir)
        } catch (_: Exception) {}
    }

    private fun selectedBackupDir(): String? = settingRepo.get(SettingKeys.backupDir)?.takeIf { it.isNotBlank() }

    private fun currentBackupPassword(): String? {
        val password = passwordStore.get()?.takeIf { it.length >= MinBackupPasswordLength }
        return password
    }

    private fun failBackup(message: String): Boolean {
        _lastMessage.value = message
        _isBackingUp.value = false
        return false
    }

    private fun failRestore(message: String): Boolean {
        _lastMessage.value = message
        _isBackingUp.value = false
        return false
    }
}

package com.dailysatori.service.backup

import co.touchlab.kermit.Logger
import com.dailysatori.config.BackupConfig
import com.dailysatori.config.DatabaseConfig
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.platform.DatabaseDriverFactory
import com.dailysatori.platform.FileManager
import com.dailysatori.service.security.SecretCipher
import com.dailysatori.service.security.SecretFieldProcessor
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
    return "daily_satori_backup_${timestamp}.zip.enc"
}

fun backupPasswordHint(name: String): String? {
    return Regex("""_hint_([^./]{3})\.zip\.enc$""").find(name)?.groupValues?.get(1)
}

class BackupService internal constructor(
    private val files: BackupFiles,
    private val settings: BackupSettings,
    private val passwords: BackupPasswords,
    private val secrets: BackupSecrets,
) {
    constructor(
        fileManager: FileManager,
        settingRepo: SettingRepository,
        passwordStore: BackupPasswordStore,
        databaseDriverFactory: DatabaseDriverFactory,
        secretCipher: SecretCipher,
    ) : this(
        files = FileManagerBackupFiles(fileManager),
        settings = SettingRepositoryBackupSettings(settingRepo),
        passwords = BackupPasswordStorePasswords(passwordStore),
        secrets = DatabaseBackupSecrets(databaseDriverFactory, secretCipher),
    )

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
        var tempDirToClean: String? = null
        var zipPathToClean: String? = null
        var encryptedPathToClean: String? = null
        return try {
            val backupDir = selectedBackupDir() ?: return failBackup("请先选择备份目录")
            val password = currentBackupPassword() ?: return failBackup("请先设置备份密码")
            val timestamp = Clock.System.now().toString().replace(Regex("[:T]"), "-").take(19)
            val zipName = "daily_satori_backup_$timestamp.zip"
            val tempRoot = files.getCacheDir()
            val zipPath = "$tempRoot/$zipName"
            zipPathToClean = zipPath
            val tempDir = "$tempRoot/temp_$timestamp"
            tempDirToClean = tempDir
            files.createDirectory(tempDir)

            _progress.value = 0.05
            _lastMessage.value = "Preparing backup..."

            val filesToBackup = mutableListOf<String>()

            // Database file
            val dbPath = files.getDatabasePath()
            if (files.exists(dbPath)) {
                val tempDbPath = "$tempDir/${DatabaseConfig.name}"
                files.copyFile(dbPath, tempDbPath)
                secrets.decryptSecretsForBackup(tempDbPath)
                filesToBackup.add(tempDbPath)
            } else {
                log.w { "Database file not found: $dbPath" }
                return failBackup("数据库文件不存在，无法创建完整备份")
            }
            _progress.value = 0.1

            // Images directory
            _lastMessage.value = "Collecting images..."
            val imagesDir = files.getImagesDir()
            if (files.exists(imagesDir)) {
                val imageFiles = files.listFiles(imagesDir)
                filesToBackup.addAll(imageFiles)
            }
            _progress.value = 0.3

            // Diary images directory
            _lastMessage.value = "Collecting diary images..."
            val diaryImagesDir = files.getDiaryImagesDir()
            if (files.exists(diaryImagesDir)) {
                val diaryImageFiles = files.listFiles(diaryImagesDir)
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

            val appDataDir = files.getAppDataDir()
            files.createZip(appDataDir, zipPath, filesToBackup)

            _progress.value = 0.7
            _lastMessage.value = "Encrypting backup..."

            val finalName = backupFileName(timestamp, password)
            val encPath = "$tempRoot/$finalName"
            encryptedPathToClean = encPath
            files.encryptFile(zipPath, encPath, password)
            files.deleteFile(zipPath)
            files.writeFileToDirectory(backupDir, finalName, encPath)
            files.deleteFile(encPath)

            _progress.value = 0.9

            // Record backup time
            settings.upsert(SettingKeys.lastBackupTime, Clock.System.now().toEpochMilliseconds().toString())

            // Clean up old backups (keep last 10)
            val allBackups = listBackups()
            if (allBackups.size > 10) {
                val toDelete = allBackups.sortedByDescending { it.timestamp }.drop(10)
                toDelete.forEach { files.deleteFileFromDirectory(backupDir, it.name) }
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
            tempDirToClean?.let { deleteRecursive(it) }
            zipPathToClean?.let { files.deleteFile(it) }
            encryptedPathToClean?.let { files.deleteFile(it) }
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

            val tempDir = "${files.getAppDataDir()}/restore_temp"
            deleteRecursive(tempDir)
            files.createDirectory(tempDir)
            val encPath = "$tempDir/$name"
            if (!files.readFileFromDirectory(backupDir, name, encPath)) {
                log.w { "Backup file not found: $name" }
                return failRestore("备份文件不存在")
            }
            val tempZip = "$tempDir/backup.zip"
            files.decryptFile(encPath, tempZip, password)
            files.deleteFile(encPath)
            _progress.value = 0.4
            _lastMessage.value = "Extracting backup..."

            files.extractZip(tempZip, tempDir)
            files.deleteFile(tempZip)
            _progress.value = 0.6

            // Move database file
            val dbSrc = "$tempDir/${DatabaseConfig.name}"
            val dbDest = files.getDatabasePath()
            if (files.exists(dbSrc)) {
                files.copyFile(dbSrc, dbDest)
                secrets.prepareRestoredSecrets(dbDest)
            } else {
                return failRestore("备份中未找到数据库文件")
            }
            _progress.value = 0.7

            // Move images
            val tempImagesDir = "$tempDir/images"
            if (files.exists(tempImagesDir)) {
                val imagesDir = files.getImagesDir()
                files.listFiles(tempImagesDir).forEach { src ->
                    val name = src.substringAfterLast("/")
                    files.copyFile(src, "$imagesDir/$name")
                }
            }
            _progress.value = 0.85

            // Move diary images
            val tempDiaryImagesDir = "$tempDir/diary_images"
            if (files.exists(tempDiaryImagesDir)) {
                val diaryImagesDir = files.getDiaryImagesDir()
                files.listFiles(tempDiaryImagesDir).forEach { src ->
                    val name = src.substringAfterLast("/")
                    files.copyFile(src, "$diaryImagesDir/$name")
                }
            }
            _progress.value = 0.95

            // Clean up temp
            deleteRecursive(tempDir)

            _progress.value = 1.0
            _lastMessage.value = "Restore completed: $name"
            log.i { "Restore completed: $name" }
            files.restartApp()
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
            files.listBackupFilesInDirectory(backupDir)
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
            if (files.deleteFileFromDirectory(backupDir, name)) {
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
            files.listFiles(dir).forEach { files.deleteFile(it) }
            files.deleteFile(dir)
        } catch (_: Exception) {}
    }

    private fun selectedBackupDir(): String? = settings.get(SettingKeys.backupDir)?.takeIf { it.isNotBlank() }

    private fun currentBackupPassword(): String? {
        val password = passwords.get()?.takeIf { it.length >= MinBackupPasswordLength }
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

internal interface BackupFiles {
    fun getAppDataDir(): String
    fun getDatabasePath(): String
    fun getImagesDir(): String
    fun getDiaryImagesDir(): String
    fun getCacheDir(): String
    fun deleteFile(path: String): Boolean
    fun exists(path: String): Boolean
    fun listFiles(path: String): List<String>
    fun copyFile(src: String, dest: String)
    fun createDirectory(path: String): Boolean
    fun extractZip(zipPath: String, destDir: String)
    fun createZip(sourceDir: String, zipPath: String, files: List<String>)
    fun encryptFile(inputPath: String, outputPath: String, password: String)
    fun decryptFile(inputPath: String, outputPath: String, password: String)
    fun listBackupFilesInDirectory(uri: String): List<String>
    fun writeFileToDirectory(uri: String, name: String, sourcePath: String): String
    fun readFileFromDirectory(uri: String, name: String, destPath: String): Boolean
    fun deleteFileFromDirectory(uri: String, name: String): Boolean
    fun restartApp()
}

internal interface BackupSettings {
    fun get(key: String): String?
    fun upsert(key: String, value: String)
}

internal interface BackupPasswords {
    fun get(): String?
}

internal interface BackupSecrets {
    fun decryptSecretsForBackup(databasePath: String)
    fun prepareRestoredSecrets(databasePath: String)
}

private class FileManagerBackupFiles(private val fileManager: FileManager) : BackupFiles {
    override fun getAppDataDir(): String = fileManager.getAppDataDir()
    override fun getDatabasePath(): String = fileManager.getDatabasePath()
    override fun getImagesDir(): String = fileManager.getImagesDir()
    override fun getDiaryImagesDir(): String = fileManager.getDiaryImagesDir()
    override fun getCacheDir(): String = fileManager.getCacheDir()
    override fun deleteFile(path: String): Boolean = fileManager.deleteFile(path)
    override fun exists(path: String): Boolean = fileManager.exists(path)
    override fun listFiles(path: String): List<String> = fileManager.listFiles(path)
    override fun copyFile(src: String, dest: String) = fileManager.copyFile(src, dest)
    override fun createDirectory(path: String): Boolean = fileManager.createDirectory(path)
    override fun extractZip(zipPath: String, destDir: String) = fileManager.extractZip(zipPath, destDir)
    override fun createZip(sourceDir: String, zipPath: String, files: List<String>) =
        fileManager.createZip(sourceDir, zipPath, files)
    override fun encryptFile(inputPath: String, outputPath: String, password: String) =
        fileManager.encryptFile(inputPath, outputPath, password)
    override fun decryptFile(inputPath: String, outputPath: String, password: String) =
        fileManager.decryptFile(inputPath, outputPath, password)
    override fun listBackupFilesInDirectory(uri: String): List<String> = fileManager.listBackupFilesInDirectory(uri)
    override fun writeFileToDirectory(uri: String, name: String, sourcePath: String): String =
        fileManager.writeFileToDirectory(uri, name, sourcePath)
    override fun readFileFromDirectory(uri: String, name: String, destPath: String): Boolean =
        fileManager.readFileFromDirectory(uri, name, destPath)
    override fun deleteFileFromDirectory(uri: String, name: String): Boolean =
        fileManager.deleteFileFromDirectory(uri, name)
    override fun restartApp() = fileManager.restartApp()
}

private class SettingRepositoryBackupSettings(private val settingRepo: SettingRepository) : BackupSettings {
    override fun get(key: String): String? = settingRepo.get(key)
    override fun upsert(key: String, value: String) = settingRepo.upsert(key, value)
}

private class BackupPasswordStorePasswords(private val passwordStore: BackupPasswordStore) : BackupPasswords {
    override fun get(): String? = passwordStore.get()
}

private class DatabaseBackupSecrets(
    private val databaseDriverFactory: DatabaseDriverFactory,
    private val secretCipher: SecretCipher,
) : BackupSecrets {
    override fun decryptSecretsForBackup(databasePath: String) {
        val driver = databaseDriverFactory.createDriver(databasePath)
        try {
            SecretFieldProcessor(driver, secretCipher).decryptSecretsForBackup()
        } finally {
            driver.close()
        }
    }

    override fun prepareRestoredSecrets(databasePath: String) {
        val driver = databaseDriverFactory.createDriver(databasePath)
        try {
            SecretFieldProcessor(driver, secretCipher).prepareRestoredSecrets()
        } finally {
            driver.close()
        }
    }
}

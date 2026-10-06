package com.dailysatori.service.backup

import co.touchlab.kermit.Logger
import com.dailysatori.service.diagnostics.*
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
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json

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
    private val clock: Clock = Clock.System,
    private val lifeArchive: LifeArchiveBackup,
) {
    constructor(
        fileManager: FileManager,
        settingRepo: SettingRepository,
        passwordStore: BackupPasswordStore,
        databaseDriverFactory: DatabaseDriverFactory,
        secretCipher: SecretCipher,
        lifeArchive: LifeArchiveBackup,
    ) : this(
        files = FileManagerBackupFiles(fileManager),
        settings = SettingRepositoryBackupSettings(settingRepo),
        passwords = BackupPasswordStorePasswords(passwordStore),
        secrets = DatabaseBackupSecrets(databaseDriverFactory, secretCipher),
        lifeArchive = lifeArchive,
    )

    private val log = Logger.withTag("Backup")
    private val operationMutex = Mutex()
    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp: StateFlow<Boolean> = _isBackingUp
    private val _progress = MutableStateFlow(0.0)
    val progress: StateFlow<Double> = _progress
    private val _lastMessage = MutableStateFlow("")
    val lastMessage: StateFlow<String> = _lastMessage

    suspend fun backupNow(): Boolean = DiagnosticLog.diagnostics.operation(
        DiagnosticSource.BACKUP, isFailure = { !it },
    ) { backupRecorded() }

    private suspend fun backupRecorded(): Boolean {
        if (!operationMutex.tryLock()) return false
        _isBackingUp.value = true
        _progress.value = 0.0
        var tempDirToClean: String? = null
        return try {
            val backupDir = selectedBackupDir() ?: return failBackup("请先选择备份目录")
            val password = currentBackupPassword() ?: return failBackup("请先设置备份密码")
            if (!files.exists(files.getDatabasePath())) return failBackup("数据库文件不存在，无法创建完整备份")
            val backupTime = clock.now()
            val timestamp = backupTime.toString().replace(Regex("[:T]"), "-").take(19)
            val tempDir = "${files.getCacheDir()}/temp_$timestamp"
            tempDirToClean = tempDir
            deleteRecursive(tempDir)
            files.createDirectory(tempDir)
            val content = prepareBackupContent(tempDir)
            val finalName = backupFileName(timestamp, password)
            publishBackup(tempDir, content, backupDir, finalName, password)
            _progress.value = 0.9
            settings.upsert(SettingKeys.lastBackupTime, backupTime.toEpochMilliseconds().toString())
            cleanUpOldBackups(backupDir, finalName)
            _progress.value = 1.0
            _lastMessage.value = "Backup completed: $finalName"
            log.i { "Backup completed: $finalName" }
            true
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            log.e(e) { "Backup failed" }
            _lastMessage.value = "Backup failed: ${e.message}"
            false
        } finally {
            tempDirToClean?.let { deleteRecursive(it) }
            _isBackingUp.value = false
            operationMutex.unlock()
        }
    }

    private suspend fun prepareBackupContent(directory: String): List<String> {
        _progress.value = 0.05
        _lastMessage.value = "Preparing backup..."
        val database = "$directory/${DatabaseConfig.name}"
        files.createDatabaseSnapshot(database)
        secrets.decryptSecretsForBackup(database)
        _progress.value = 0.1
        _lastMessage.value = "Collecting user files..."
        val content = mutableListOf(database)
        val appDataDir = files.getAppDataDir()
        val userFiles = files.listFilesRecursively(appDataDir).filter { isBackupUserFile(it.removePrefix("$appDataDir/")) }
        userFiles.forEachIndexed { index, path ->
            val relative = path.removePrefix("$appDataDir/")
            if (isBackupUserFile(relative)) {
                val destination = "$directory/$relative"
                files.copyFile(path, destination)
                content.add(destination)
            }
            _progress.value = 0.1 + 0.25 * (index + 1) / userFiles.size.coerceAtLeast(1)
        }
        val archive = "$directory/$LifeArchiveBackupName"
        files.writeFile(archive, lifeArchive.exportSnapshot().encodeToByteArray())
        content.add(archive)
        _lastMessage.value = "Validating backup..."
        val entries = content.mapIndexed { index, path ->
            BackupManifestFile(path.removePrefix("$directory/"), files.fileSize(path), files.sha256(path)).also {
                _progress.value = 0.35 + 0.15 * (index + 1) / content.size
            }
        }
        val required = secrets.requiredUserFiles(database, appDataDir)
        check(entries.map { it.path }.containsAll(required)) { "存在缺失的附件，无法创建完整备份" }
        val manifest = BackupManifest(schemaVersion = DatabaseConfig.currentSchemaVersion, sourceAppDataDir = appDataDir, files = entries)
        val manifestPath = "$directory/$BackupManifestName"
        files.writeFile(manifestPath, Json.encodeToString(manifest).encodeToByteArray())
        _progress.value = 0.5
        return content + manifestPath
    }

    private fun publishBackup(directory: String, content: List<String>, backupDir: String, name: String, password: String) {
        val encrypted = "${files.getCacheDir()}/$name"
        val zip = encrypted.removeSuffix(".enc")
        try {
            _lastMessage.value = "Creating ZIP archive (${content.size} files)..."
            files.createZip(directory, zip, content) { _progress.value = 0.5 + it * 0.2 }
            _progress.value = 0.7
            _lastMessage.value = "Encrypting backup..."
            files.encryptFile(zip, encrypted, password) { _progress.value = 0.7 + it * 0.15 }
            _lastMessage.value = "Saving backup..."
            files.writeFileToDirectory(backupDir, name, encrypted)
        } finally {
            runCatching { files.deleteFile(zip) }
            runCatching { files.deleteFile(encrypted) }
        }
    }

    suspend fun restore(name: String, password: String): Boolean = DiagnosticLog.diagnostics.operation(
        DiagnosticSource.BACKUP, isFailure = { !it },
    ) { restoreRecorded(name, password) }

    suspend fun restoreFile(uri: String, password: String): Boolean = DiagnosticLog.diagnostics.operation(
        DiagnosticSource.BACKUP, isFailure = { !it },
    ) { restoreRecorded("selected-backup.enc", password, uri) }

    private suspend fun restoreRecorded(name: String, password: String, fileUri: String? = null): Boolean {
        if (!operationMutex.tryLock()) return false
        _isBackingUp.value = true
        _progress.value = 0.0
        var tempDirToClean: String? = null
        return try {
            val backupDir = selectedBackupDir() ?: if (fileUri != null) "" else return failRestore("请先选择备份目录")
            if (password.isBlank()) return failRestore("请输入备份密码")
            require('/' !in name && '\\' !in name && name != "." && name != "..") { "无效的备份文件名" }

            val tempDir = "${files.getCacheDir()}/restore_temp"
            tempDirToClean = tempDir
            deleteRecursive(tempDir)
            files.createDirectory(tempDir)
            if (!extractRestore(backupDir, name, password, tempDir, fileUri)) return false
            val prepared = prepareRestoreData(tempDir, backupDir, password)
            val preservesLifeArchive = files.exists("$prepared/preserve-life-archive")
            _lastMessage.value = "Staging restore..."
            files.stageRestore(prepared)
            _progress.value = 0.95

            // Clean up temp
            deleteRecursive(tempDir)

            _lastMessage.value = if (preservesLifeArchive) "Restore ready: legacy backup" else "Restore ready"
            log.i { "Restore staged; restarting" }
            try {
                files.restartApp()
            } catch (failure: Exception) {
                log.e(failure) { "Restore is ready; automatic restart failed" }
                _lastMessage.value += if (preservesLifeArchive) "; restart manually" else ": restart manually"
            }
            true
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            log.e(e) { "Restore failed" }
            _lastMessage.value = "Restore failed: ${e.message}"
            false
        } finally {
            tempDirToClean?.let(::deleteRecursive)
            _isBackingUp.value = false
            operationMutex.unlock()
        }
    }

    private fun extractRestore(backupDir: String, name: String, password: String, tempDir: String, fileUri: String?): Boolean {
        _lastMessage.value = "Reading backup..."
        val encrypted = "$tempDir/$name"
        val read = if (fileUri != null) files.readFileFromUri(fileUri, encrypted) else files.readFileFromDirectory(backupDir, name, encrypted)
        if (!read) return failRestore("备份文件无法读取，请重新选择文件")
        _progress.value = 0.1
        _lastMessage.value = "Decrypting backup..."
        val tempZip = "$tempDir/backup.zip"
        files.decryptFile(encrypted, tempZip, password) { _progress.value = 0.1 + it * 0.3 }
        files.deleteFile(encrypted)
        _progress.value = 0.4
        _lastMessage.value = "Extracting backup..."
        files.extractZip(tempZip, tempDir) { _progress.value = 0.4 + it * 0.2 }
        files.deleteFile(tempZip)
        _progress.value = 0.6
        return true
    }

    private suspend fun prepareRestoreData(tempDir: String, backupDir: String, password: String): String {
        val restoredFiles = files.listFilesRecursively(tempDir).map { it.removePrefix("$tempDir/") }.toSet()
        _lastMessage.value = "Validating backup..."
        validateManifest(tempDir, restoredFiles)
        _lastMessage.value = "Preparing restore..."
        val database = "$tempDir/${DatabaseConfig.name}"
        check(files.exists(database)) { "备份中未找到数据库文件" }
        secrets.prepareRestoredSecrets(database)
        secrets.relocateRestoredData(database, files.getAppDataDir(), backupDir, restoredFiles)
        _progress.value = 0.7
        val prepared = "$tempDir/_restore"
        deleteRecursive(prepared)
        files.createDirectory("$prepared/app_data")
        files.moveFile(database, "$prepared/database.db")
        restoredFiles.filter(::isBackupUserFile).forEach { relative ->
            files.moveFile("$tempDir/$relative", "$prepared/app_data/$relative")
        }
        _progress.value = 0.85
        val archive = "$tempDir/$LifeArchiveBackupName"
        if (files.exists(archive)) {
            val encrypted = lifeArchive.prepareRestore(files.readFile(archive).decodeToString())
            files.writeFile("$prepared/life_archive/archive.json.enc", encrypted)
        } else files.writeFile("$prepared/preserve-life-archive", byteArrayOf())
        files.writeFile("$prepared/backup_password.sec", passwords.encryptedPassword(password))
        if (backupDir.isBlank()) files.writeFile("$prepared/needs-backup-directory", byteArrayOf())
        return prepared
    }

    private fun validateManifest(directory: String, actualFiles: Set<String>) {
        if (BackupManifestName !in actualFiles) return // Legacy encrypted backups have no content manifest.
        val manifest = Json.decodeFromString<BackupManifest>(files.readFile("$directory/$BackupManifestName").decodeToString())
        require(manifest.formatVersion == 1 && manifest.schemaVersion <= DatabaseConfig.currentSchemaVersion) {
            "备份版本较新，请先升级应用"
        }
        val names = manifest.files.map { it.path }
        require(names.size == names.distinct().size && DatabaseConfig.name in names && LifeArchiveBackupName in names)
        require(actualFiles == names.toSet() + BackupManifestName) { "备份内容不完整或包含未登记文件" }
        manifest.files.forEachIndexed { index, entry ->
            require(entry.path == DatabaseConfig.name || entry.path == LifeArchiveBackupName || isBackupUserFile(entry.path))
            val path = "$directory/${entry.path}"
            require(files.fileSize(path) == entry.size && files.sha256(path) == entry.sha256) { "备份文件校验失败" }
            _progress.value = 0.6 + 0.1 * (index + 1) / manifest.files.size
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

    private fun cleanUpOldBackups(backupDir: String, newBackupName: String) {
        try {
            val backupTime = backupInstant(newBackupName) ?: return
            val backups = files.listBackupFilesInDirectory(backupDir).mapNotNull { name ->
                backupInstant(name)?.let { name to it }
            }.sortedByDescending { it.second }
            // Only prune after the newly written backup is visible in the directory.
            if (backups.none { it.first == newBackupName }) return
            val previousTime = backups.firstOrNull { it.first != newBackupName }?.second ?: return
            val cutoff = backupTime - BackupConfig.retentionDays.days
            if (previousTime > backupTime || previousTime < cutoff) {
                log.w { "Skipping backup cleanup: device clock changed or backups are too far apart" }
                return
            }
            val protectedNames = backups.take(BackupConfig.minimumRetainedBackups).map { it.first }.toSet()
            backups.filter { it.second < cutoff && it.first !in protectedNames && it.first != newBackupName }
                .forEach { (name, _) ->
                    if (!files.deleteFileFromDirectory(backupDir, name)) {
                        log.w { "Could not delete expired backup" }
                    }
                }
        } catch (e: Exception) {
            log.w(e) { "Backup saved, but expired backups could not be cleaned up" }
        }
    }

    private fun backupInstant(name: String): Instant? {
        val timestamp = Regex(
            """^daily_satori_backup_(\d{4}-\d{2}-\d{2}-\d{2}-\d{2}-\d{2})(?:_hint_[^./]{3})?\.zip\.enc$""",
        ).matchEntire(name)?.groupValues?.get(1) ?: return null
        return runCatching {
            Instant.parse("${timestamp.take(10)}T${timestamp.substring(11).replace('-', ':')}Z")
        }.getOrNull()
    }

    private fun deleteRecursive(dir: String) {
        try {
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
    fun createDatabaseSnapshot(destination: String)
    fun listFilesRecursively(path: String): List<String>
    fun readFile(path: String): ByteArray
    fun writeFile(path: String, bytes: ByteArray)
    fun fileSize(path: String): Long
    fun sha256(path: String): String
    fun stageRestore(directory: String)
    fun deleteFile(path: String): Boolean
    fun exists(path: String): Boolean
    fun listFiles(path: String): List<String>
    fun copyFile(src: String, dest: String)
    fun moveFile(src: String, dest: String)
    fun createDirectory(path: String): Boolean
    fun extractZip(zipPath: String, destDir: String)
    fun createZip(sourceDir: String, zipPath: String, files: List<String>)
    fun encryptFile(inputPath: String, outputPath: String, password: String)
    fun decryptFile(inputPath: String, outputPath: String, password: String)
    fun createZip(sourceDir: String, zipPath: String, files: List<String>, progress: (Double) -> Unit) { createZip(sourceDir, zipPath, files); progress(1.0) }
    fun extractZip(zipPath: String, destDir: String, progress: (Double) -> Unit) { extractZip(zipPath, destDir); progress(1.0) }
    fun encryptFile(inputPath: String, outputPath: String, password: String, progress: (Double) -> Unit) { encryptFile(inputPath, outputPath, password); progress(1.0) }
    fun decryptFile(inputPath: String, outputPath: String, password: String, progress: (Double) -> Unit) { decryptFile(inputPath, outputPath, password); progress(1.0) }
    fun readFileFromUri(uri: String, destPath: String): Boolean
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
    fun encryptedPassword(password: String): ByteArray
}

internal interface BackupSecrets {
    fun decryptSecretsForBackup(databasePath: String)
    fun prepareRestoredSecrets(databasePath: String)
    fun requiredUserFiles(databasePath: String, appDataDir: String): List<String>
    fun relocateRestoredData(databasePath: String, appDataDir: String, backupDirectory: String, availableFiles: Set<String>)
}

private class FileManagerBackupFiles(private val fileManager: FileManager) : BackupFiles {
    override fun getAppDataDir(): String = fileManager.getAppDataDir()
    override fun getDatabasePath(): String = fileManager.getDatabasePath()
    override fun getImagesDir(): String = fileManager.getImagesDir()
    override fun getDiaryImagesDir(): String = fileManager.getDiaryImagesDir()
    override fun getCacheDir(): String = fileManager.getCacheDir()
    override fun createDatabaseSnapshot(destination: String) = fileManager.createDatabaseSnapshot(destination)
    override fun listFilesRecursively(path: String): List<String> = fileManager.listFilesRecursively(path)
    override fun readFile(path: String): ByteArray = fileManager.readFile(path)
    override fun writeFile(path: String, bytes: ByteArray) = fileManager.writeFile(path, bytes)
    override fun fileSize(path: String): Long = fileManager.fileSize(path)
    override fun sha256(path: String): String = fileManager.sha256(path)
    override fun stageRestore(directory: String) = fileManager.stageRestore(directory)
    override fun deleteFile(path: String): Boolean = fileManager.deleteFile(path)
    override fun exists(path: String): Boolean = fileManager.exists(path)
    override fun listFiles(path: String): List<String> = fileManager.listFiles(path)
    override fun copyFile(src: String, dest: String) = fileManager.copyFile(src, dest)
    override fun moveFile(src: String, dest: String) = fileManager.moveFile(src, dest)
    override fun readFileFromUri(uri: String, destPath: String) = fileManager.readFileFromUri(uri, destPath)
    override fun createZip(sourceDir: String, zipPath: String, files: List<String>, progress: (Double) -> Unit) = fileManager.createZip(sourceDir, zipPath, files, progress)
    override fun extractZip(zipPath: String, destDir: String, progress: (Double) -> Unit) = fileManager.extractZip(zipPath, destDir, progress)
    override fun encryptFile(inputPath: String, outputPath: String, password: String, progress: (Double) -> Unit) = fileManager.encryptFile(inputPath, outputPath, password, progress)
    override fun decryptFile(inputPath: String, outputPath: String, password: String, progress: (Double) -> Unit) = fileManager.decryptFile(inputPath, outputPath, password, progress)
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
    override fun encryptedPassword(password: String): ByteArray = passwordStore.encryptedPassword(password)
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
            BackupDatabaseData(driver).prepareSecrets(secretCipher)
        } finally {
            driver.close()
        }
    }

    override fun requiredUserFiles(databasePath: String, appDataDir: String): List<String> =
        withDatabase(databasePath) { BackupDatabaseData(it).userFiles(appDataDir) }

    override fun relocateRestoredData(databasePath: String, appDataDir: String, backupDirectory: String, availableFiles: Set<String>) {
        withDatabase(databasePath) { BackupDatabaseData(it).prepareRestore(appDataDir, backupDirectory, availableFiles) }
    }

    private fun <T> withDatabase(path: String, block: (app.cash.sqldelight.db.SqlDriver) -> T): T {
        val driver = databaseDriverFactory.createDriver(path)
        return try { block(driver) } finally { driver.close() }
    }
}

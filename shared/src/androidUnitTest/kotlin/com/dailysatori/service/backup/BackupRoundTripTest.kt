package com.dailysatori.service.backup

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.config.DatabaseConfig
import com.dailysatori.config.SettingKeys
import com.dailysatori.platform.FileManager
import com.dailysatori.service.security.SecretFieldProcessor
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import java.io.File
import java.nio.file.Files
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class BackupRoundTripTest {
    @Test
    fun encryptedArchiveRestoresDatabaseVoicePhotosAndSecretsToAnotherDevice() = runBlocking { roundTrip(false) }

    @Test
    fun newDeviceRestoresSelectedFileWithoutConfiguringDirectoryOrPassword() = runBlocking { roundTrip(true) }

    private suspend fun roundTrip(directFile: Boolean) {
        val root = Files.createTempDirectory("backup-round-trip").toFile()
        try {
            val backups = File(root, "backups").apply { mkdirs() }
            val sourceFiles = DiskFiles(File(root, "source"))
            val sourceCipher = DeviceCipher("source")
            seedDatabase(sourceFiles, sourceCipher)
            sourceFiles.writeFile("${sourceFiles.getAppDataDir()}/diary/audio/23/voice.m4a", "voice bytes".toByteArray())
            sourceFiles.writeFile("${sourceFiles.getAppDataDir()}/diary_images/photo.jpg", "photo bytes".toByteArray())
            val archive = "{\"categories\":[{\"id\":\"insurance\",\"name\":\"保险\"}],\"records\":[{\"title\":\"policy\"}]}"
            val source = service(sourceFiles, backups, sourceCipher, archive)
            assertTrue(source.backupNow(), source.lastMessage.value)
            val name = backups.listFiles()!!.single().name
            assertFalse(backups.resolve(name).readBytes().decodeToString().contains("private-token"))

            val destinationFiles = DiskFiles(File(root, "destination"))
            val destinationCipher = DeviceCipher("destination")
            val destination = service(destinationFiles, backups, destinationCipher, "unused", configured = !directFile)
            destinationFiles.writeFile(destinationFiles.getDatabasePath(), "original data".toByteArray())
            if (directFile) {
                assertFalse(destination.restoreFile(backups.resolve(name).path, "wrong password"))
                assertEquals("original data", File(destinationFiles.getDatabasePath()).readText())
                assertTrue(destination.restoreFile(backups.resolve(name).path, "correct horse battery"), destination.lastMessage.value)
                assertEquals("恢复完成，请选择自动备份目录", destinationFiles.restoreResult)
            } else assertTrue(destination.restore(name, "correct horse battery"), destination.lastMessage.value)
            assertEquals("voice bytes", File(destinationFiles.getAppDataDir(), "diary/audio/23/voice.m4a").readText())
            assertEquals("photo bytes", File(destinationFiles.getAppDataDir(), "diary_images/photo.jpg").readText())
            assertEquals(archive, destinationCipher.decrypt(File(destinationFiles.life, "archive.json.enc").readText()))
            withDatabase(destinationFiles.getDatabasePath()) { driver ->
                val queries = DailySatoriDatabase(driver).dailySatoriQueries
                assertEquals("journal entry", queries.selectAllDiaries().executeAsOne().content)
                assertEquals("${destinationFiles.getAppDataDir()}/diary/audio/23/voice.m4a",
                    queries.selectAllDiaryAttachments().executeAsOne().local_path)
                assertEquals("private-token", destinationCipher.decrypt(queries.selectSettingByKey("weread_api_key").executeAsOne().value_!!))
                assertEquals(if (directFile) "" else backups.path, queries.selectSettingByKey("backup_directory").executeAsOne().value_)
            }
            assertEquals("secured:correct horse battery", destinationFiles.password.readText())
            assertTrue(File(destinationFiles.getCacheDir()).listFiles().orEmpty().isEmpty())
        } finally { root.deleteRecursively() }
    }

    private fun seedDatabase(files: DiskFiles, cipher: SecretValueCipher) = withDatabase(files.getDatabasePath()) { driver ->
        DailySatoriDatabase.Schema.create(driver)
        driver.execute(null, "INSERT INTO diary(id,content,images,created_at,updated_at) VALUES(23,'journal entry','diary_images/photo.jpg',1,1)", 0)
        driver.execute(null, "INSERT INTO diary_attachment(diary_id,kind,local_path,created_at,updated_at) VALUES(23,'audio',?,1,1)", 1) {
            bindString(0, "${files.getAppDataDir()}/diary/audio/23/voice.m4a")
        }
        driver.execute(null, "INSERT INTO setting(key,value,created_at,updated_at) VALUES('weread_api_key',?,1,1)", 1) {
            bindString(0, cipher.encrypt("private-token"))
        }
    }

    private fun service(files: DiskFiles, backups: File, cipher: SecretValueCipher, archive: String, configured: Boolean = true): BackupService {
        val values = if (configured) mutableMapOf(SettingKeys.backupDir to backups.path) else mutableMapOf()
        val settings = object : BackupSettings {
            override fun get(key: String) = values[key]
            override fun upsert(key: String, value: String) { values[key] = value }
        }
        val passwords = object : BackupPasswords {
            override fun get() = if (configured) "correct horse battery" else null
            override fun encryptedPassword(password: String) = "secured:$password".toByteArray()
        }
        val secrets = object : BackupSecrets {
            override fun decryptSecretsForBackup(databasePath: String) = withDatabase(databasePath) {
                SecretFieldProcessor(it, cipher).decryptSecretsForBackup(); Unit
            }
            override fun prepareRestoredSecrets(databasePath: String) = withDatabase(databasePath) {
                SecretFieldProcessor(it, cipher).prepareRestoredSecrets(strict = true); Unit
            }
            override fun requiredUserFiles(databasePath: String, appDataDir: String) = withDatabase(databasePath) {
                BackupDatabaseData(it).userFiles(appDataDir)
            }
            override fun relocateRestoredData(databasePath: String, appDataDir: String, backupDirectory: String, availableFiles: Set<String>) =
                withDatabase(databasePath) { BackupDatabaseData(it).prepareRestore(appDataDir, backupDirectory, availableFiles) }
        }
        val life = object : LifeArchiveBackup {
            override suspend fun exportSnapshot() = archive
            override suspend fun prepareRestore(snapshot: String) = cipher.encrypt(snapshot).toByteArray()
        }
        return BackupService(files, settings, passwords, secrets, lifeArchive = life)
    }

    private class DeviceCipher(private val device: String) : SecretValueCipher {
        override fun encrypt(value: String) = "enc:v1:$device:" + Base64.getEncoder().encodeToString(value.toByteArray())
        override fun decrypt(value: String) = if (value.startsWith("enc:v1:$device:"))
            String(Base64.getDecoder().decode(value.removePrefix("enc:v1:$device:"))) else value
        override fun isEncrypted(value: String) = value.startsWith("enc:v1:")
    }

    private class DiskFiles(private val root: File) : BackupFiles {
        private val manager = FileManager()
        val life = root.resolve("life_archive")
        val password = root.resolve("backup_password.sec")
        var restoreResult: String? = null
        private val installer get() = BackupRestoreTransaction(root.resolve("pending"), File(getAppDataDir()), File(getDatabasePath()), life, password)
        init { root.mkdirs() }
        override fun getAppDataDir() = root.resolve("DailySatori").apply { mkdirs() }.path
        override fun getDatabasePath() = root.resolve(DatabaseConfig.name).path
        override fun getImagesDir() = "${getAppDataDir()}/images"
        override fun getDiaryImagesDir() = "${getAppDataDir()}/diary_images"
        override fun getCacheDir() = root.resolve("cache").apply { mkdirs() }.path
        override fun createDatabaseSnapshot(destination: String) = withDatabase(getDatabasePath()) {
            createSqliteBackupSnapshot(it, getDatabasePath(), destination, manager::copyFile)
        }
        override fun listFilesRecursively(path: String) = manager.listFilesRecursively(path)
        override fun readFile(path: String) = manager.readFile(path)
        override fun writeFile(path: String, bytes: ByteArray) = manager.writeFile(path, bytes)
        override fun fileSize(path: String) = manager.fileSize(path)
        override fun sha256(path: String) = manager.sha256(path)
        override fun stageRestore(directory: String) = installer.stage(File(directory))
        override fun deleteFile(path: String) = manager.deleteFile(path)
        override fun exists(path: String) = manager.exists(path)
        override fun listFiles(path: String) = manager.listFiles(path)
        override fun copyFile(src: String, dest: String) = manager.copyFile(src, dest)
        override fun moveFile(src: String, dest: String) = manager.moveFile(src, dest)
        override fun readFileFromUri(uri: String, destPath: String): Boolean {
            manager.copyFile(uri, destPath)
            return true
        }
        override fun createDirectory(path: String) = manager.createDirectory(path)
        override fun extractZip(zipPath: String, destDir: String) = manager.extractZip(zipPath, destDir)
        override fun createZip(sourceDir: String, zipPath: String, files: List<String>) = manager.createZip(sourceDir, zipPath, files)
        override fun encryptFile(inputPath: String, outputPath: String, password: String) = manager.encryptFile(inputPath, outputPath, password)
        override fun decryptFile(inputPath: String, outputPath: String, password: String) = manager.decryptFile(inputPath, outputPath, password)
        override fun listBackupFilesInDirectory(uri: String) = File(uri).listFiles().orEmpty().map { it.name }.filter { it.endsWith(".enc") }
        override fun writeFileToDirectory(uri: String, name: String, sourcePath: String): String {
            manager.copyFile(sourcePath, File(uri, name).path)
            return name
        }
        override fun readFileFromDirectory(uri: String, name: String, destPath: String): Boolean {
            manager.copyFile(File(uri, name).path, destPath)
            return true
        }
        override fun deleteFileFromDirectory(uri: String, name: String) = File(uri, name).delete()
        override fun restartApp() { restoreResult = installer.applyPending() }
    }
}

private fun <T> withDatabase(path: String, block: (JdbcSqliteDriver) -> T): T {
    val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
    return try { block(driver) } finally { driver.close() }
}

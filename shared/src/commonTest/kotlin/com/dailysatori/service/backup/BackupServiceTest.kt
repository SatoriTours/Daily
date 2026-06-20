package com.dailysatori.service.backup

import com.dailysatori.config.DatabaseConfig
import com.dailysatori.config.SettingKeys
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupServiceTest {
    @Test
    fun backupFileNameDoesNotIncludePasswordHint() {
        val name = backupFileName("2026-05-04-10-30-00", "correct horse battery")

        assertEquals("daily_satori_backup_2026-05-04-10-30-00.zip.enc", name)
    }

    @Test
    fun legacyPasswordHintIsParsedFromBackupFileName() {
        val hint = backupPasswordHint("daily_satori_backup_2026-05-04-10-30-00_hint_abc.zip.enc")

        assertEquals("abc", hint)
    }

    @Test
    fun passwordHintReturnsNullForOldNames() {
        val hint = backupPasswordHint("daily_satori_backup_2026-05-04-10-30-00.zip.enc")

        assertNull(hint)
    }

    @Test
    fun backupRequiresSelectedDirectory() = runBlocking {
        val fixture = backupFixture()
        fixture.settings.remove(SettingKeys.backupDir)

        val result = fixture.service.backupNow()

        assertFalse(result)
        assertEquals("请先选择备份目录", fixture.service.lastMessage.value)
        assertFalse(fixture.service.isBackingUp.value)
        assertTrue(fixture.files.writtenBackups.isEmpty())
    }

    @Test
    fun backupRequiresSavedPasswordWithMinimumLength() = runBlocking {
        val fixture = backupFixture(password = "too-short")

        val result = fixture.service.backupNow()

        assertFalse(result)
        assertEquals("请先设置备份密码", fixture.service.lastMessage.value)
        assertTrue(fixture.files.writtenBackups.isEmpty())
    }

    @Test
    fun backupWritesEncryptedDatabaseAndImageArchiveToSelectedDirectory() = runBlocking {
        val fixture = backupFixture()
        fixture.files.seedFile(fixture.files.databasePathValue, "db-content")
        fixture.files.seedFile("${fixture.files.imagesDirPath}/cover.jpg", "cover")
        fixture.files.seedFile("${fixture.files.diaryImagesDirPath}/mood.png", "mood")

        val result = fixture.service.backupNow()

        assertTrue(result)
        val backup = fixture.files.writtenBackups.single()
        assertEquals("content://selected-backup-dir", backup.directory)
        assertTrue(backup.name.startsWith("daily_satori_backup_"))
        assertTrue(backup.name.endsWith(".zip.enc"))
        assertFalse(backup.name.contains("_hint_"))
        assertEquals("correct horse battery", fixture.files.encryptPasswords.single())
        assertEquals(
            listOf(
                DatabaseConfig.name,
                "images/cover.jpg",
                "diary_images/mood.png",
            ),
            fixture.files.zipEntries.single(),
        )
        val decryptedDatabase = fixture.secrets.decryptedBackupDatabases.single()
        assertTrue(decryptedDatabase.startsWith("/cache/temp_"))
        assertTrue(decryptedDatabase.endsWith("/${DatabaseConfig.name}"))
        assertNotNull(fixture.settings[SettingKeys.lastBackupTime])
        assertEquals(1.0, fixture.service.progress.value)
        assertFalse(fixture.service.isBackingUp.value)
        assertFalse(fixture.files.exists("/cache/temp"))
    }

    @Test
    fun backupDeletesOldBackupsAfterKeepingNewestTen() = runBlocking {
        val fixture = backupFixture()
        fixture.files.seedFile(fixture.files.databasePathValue, "db-content")
        (1..11).forEach { index ->
            val day = index.toString().padStart(2, '0')
            fixture.files.seedBackup("daily_satori_backup_2026-05-$day-10-30-00.zip.enc")
        }

        val result = fixture.service.backupNow()

        assertTrue(result)
        assertEquals(
            setOf("daily_satori_backup_2026-05-01-10-30-00.zip.enc", "daily_satori_backup_2026-05-02-10-30-00.zip.enc"),
            fixture.files.deletedBackups.toSet(),
        )
    }

    @Test
    fun restoreRejectsWrongPasswordWithoutOverwritingCurrentDatabase() = runBlocking {
        val fixture = backupFixture()
        fixture.files.seedFile(fixture.files.databasePathValue, "current-db")
        fixture.files.seedRestorableBackup("daily_satori_backup_2026-05-04-10-30-00.zip.enc", password = "file password")

        val result = fixture.service.restore("daily_satori_backup_2026-05-04-10-30-00.zip.enc", "wrong password")

        assertFalse(result)
        assertEquals("current-db", fixture.files.readText(fixture.files.databasePathValue))
        assertTrue(fixture.service.lastMessage.value.startsWith("Restore failed:"))
        assertFalse(fixture.files.restartCalled)
        assertTrue(fixture.secrets.preparedRestoredDatabases.isEmpty())
    }

    @Test
    fun restoreCopiesDatabaseAndImagesThenPreparesSecretsAndRestarts() = runBlocking {
        val fixture = backupFixture()
        fixture.files.seedFile(fixture.files.databasePathValue, "current-db")
        fixture.files.seedRestorableBackup(
            "daily_satori_backup_2026-05-04-10-30-00.zip.enc",
            password = "file password",
            database = "restored-db",
            images = mapOf("cover.jpg" to "restored-cover"),
            diaryImages = mapOf("mood.png" to "restored-mood"),
        )

        val result = fixture.service.restore("daily_satori_backup_2026-05-04-10-30-00.zip.enc", "file password")

        assertTrue(result)
        assertEquals("restored-db", fixture.files.readText(fixture.files.databasePathValue))
        assertEquals("restored-cover", fixture.files.readText("${fixture.files.imagesDirPath}/cover.jpg"))
        assertEquals("restored-mood", fixture.files.readText("${fixture.files.diaryImagesDirPath}/mood.png"))
        assertEquals(listOf(fixture.files.databasePathValue), fixture.secrets.preparedRestoredDatabases)
        assertTrue(fixture.files.restartCalled)
        assertEquals(1.0, fixture.service.progress.value)
        assertFalse(fixture.service.isBackingUp.value)
    }
}

private fun backupFixture(password: String? = "correct horse battery"): BackupFixture {
    val files = FakeBackupFiles()
    val settings = mutableMapOf(SettingKeys.backupDir to "content://selected-backup-dir")
    val passwords = FakeBackupPasswords(password)
    val secrets = FakeBackupSecrets()
    return BackupFixture(
        files = files,
        settings = settings,
        secrets = secrets,
        service = BackupService(files, FakeBackupSettings(settings), passwords, secrets),
    )
}

private class BackupFixture(
    val files: FakeBackupFiles,
    val settings: MutableMap<String, String>,
    val secrets: FakeBackupSecrets,
    val service: BackupService,
)

private class FakeBackupSettings(private val values: MutableMap<String, String>) : BackupSettings {
    override fun get(key: String): String? = values[key]
    override fun upsert(key: String, value: String) {
        values[key] = value
    }
}

private class FakeBackupPasswords(private val value: String?) : BackupPasswords {
    override fun get(): String? = value
}

private class FakeBackupSecrets : BackupSecrets {
    val decryptedBackupDatabases = mutableListOf<String>()
    val preparedRestoredDatabases = mutableListOf<String>()

    override fun decryptSecretsForBackup(databasePath: String) {
        decryptedBackupDatabases += databasePath
    }

    override fun prepareRestoredSecrets(databasePath: String) {
        preparedRestoredDatabases += databasePath
    }
}

private class FakeBackupFiles : BackupFiles {
    val appDataDirPath = "/app"
    val databasePathValue = "/db/${DatabaseConfig.name}"
    val imagesDirPath = "$appDataDirPath/images"
    val diaryImagesDirPath = "$appDataDirPath/diary_images"
    val writtenBackups = mutableListOf<WrittenBackup>()
    val deletedBackups = mutableListOf<String>()
    val encryptPasswords = mutableListOf<String>()
    val zipEntries = mutableListOf<List<String>>()
    var restartCalled = false

    private val files = mutableMapOf<String, String>()
    private val directories = mutableSetOf("/cache", appDataDirPath, imagesDirPath, diaryImagesDirPath)
    private val directoryBackups = mutableMapOf<String, MutableMap<String, String>>("content://selected-backup-dir" to mutableMapOf())
    private val restorableBackups = mutableMapOf<String, RestorableBackup>()
    private val encryptedPayloadPasswords = mutableMapOf<String, String>()

    fun seedFile(path: String, content: String) {
        directories += path.substringBeforeLast("/")
        files[path] = content
    }

    fun readText(path: String): String = files.getValue(path)

    fun seedBackup(name: String) {
        directoryBackups.getValue("content://selected-backup-dir")[name] = "old-backup"
    }

    fun seedRestorableBackup(
        name: String,
        password: String,
        database: String = "restored-db",
        images: Map<String, String> = emptyMap(),
        diaryImages: Map<String, String> = emptyMap(),
    ) {
        directoryBackups.getValue("content://selected-backup-dir")[name] = "encrypted:$name"
        restorableBackups[name] = RestorableBackup(password, database, images, diaryImages)
    }

    override fun getAppDataDir(): String = appDataDirPath
    override fun getDatabasePath(): String = databasePathValue
    override fun getImagesDir(): String = imagesDirPath
    override fun getDiaryImagesDir(): String = diaryImagesDirPath
    override fun getCacheDir(): String = "/cache"

    override fun deleteFile(path: String): Boolean {
        val existed = files.remove(path) != null || directories.remove(path)
        files.keys.filter { it.startsWith("$path/") }.toList().forEach { files.remove(it) }
        directories.filter { it.startsWith("$path/") }.toList().forEach { directories.remove(it) }
        return existed
    }

    override fun exists(path: String): Boolean = path in files || path in directories
    override fun listFiles(path: String): List<String> = files.keys.filter { it.substringBeforeLast("/") == path }.sorted()

    override fun copyFile(src: String, dest: String) {
        directories += dest.substringBeforeLast("/")
        files[dest] = files.getValue(src)
    }

    override fun createDirectory(path: String): Boolean {
        directories += path
        return true
    }

    override fun extractZip(zipPath: String, destDir: String) {
        val name = files.getValue(zipPath).removePrefix("zip:")
        val backup = restorableBackups.getValue(name)
        seedFile("$destDir/${DatabaseConfig.name}", backup.database)
        backup.images.forEach { (name, content) -> seedFile("$destDir/images/$name", content) }
        backup.diaryImages.forEach { (name, content) -> seedFile("$destDir/diary_images/$name", content) }
    }

    override fun createZip(sourceDir: String, zipPath: String, files: List<String>) {
        val entries = files.map { path ->
            if (path.startsWith(sourceDir)) path.removePrefix(sourceDir).removePrefix("/") else path.substringAfterLast("/")
        }
        zipEntries += entries
        seedFile(zipPath, entries.joinToString("\n"))
    }

    override fun encryptFile(inputPath: String, outputPath: String, password: String) {
        encryptPasswords += password
        encryptedPayloadPasswords[outputPath] = password
        seedFile(outputPath, files.getValue(inputPath))
    }

    override fun decryptFile(inputPath: String, outputPath: String, password: String) {
        val backupName = files.getValue(inputPath).removePrefix("encrypted:")
        val expected = restorableBackups.getValue(backupName).password
        check(password == expected) { "Invalid backup password or corrupted backup" }
        seedFile(outputPath, "zip:$backupName")
    }

    override fun listBackupFilesInDirectory(uri: String): List<String> =
        directoryBackups[uri]?.keys?.sortedDescending().orEmpty()

    override fun writeFileToDirectory(uri: String, name: String, sourcePath: String): String {
        writtenBackups += WrittenBackup(uri, name)
        directoryBackups.getOrPut(uri) { mutableMapOf() }[name] = files.getValue(sourcePath)
        return name
    }

    override fun readFileFromDirectory(uri: String, name: String, destPath: String): Boolean {
        val content = directoryBackups[uri]?.get(name) ?: return false
        seedFile(destPath, content)
        return true
    }

    override fun deleteFileFromDirectory(uri: String, name: String): Boolean {
        deletedBackups += name
        return directoryBackups[uri]?.remove(name) != null
    }

    override fun restartApp() {
        restartCalled = true
    }
}

private data class WrittenBackup(val directory: String, val name: String)

private data class RestorableBackup(
    val password: String,
    val database: String,
    val images: Map<String, String>,
    val diaryImages: Map<String, String>,
)

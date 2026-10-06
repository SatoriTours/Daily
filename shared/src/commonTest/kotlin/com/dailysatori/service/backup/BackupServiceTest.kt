package com.dailysatori.service.backup

import com.dailysatori.config.DatabaseConfig
import com.dailysatori.config.SettingKeys
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.Json

class BackupServiceTest {
    @Test
    fun legacyBackupStagesAndReportsThatLifeArchiveWasNotIncluded() = runBlocking {
        val fixture = backupFixture()
        fixture.files.seedRestorableBackup("legacy.enc", "file password")

        assertTrue(fixture.service.restore("legacy.enc", "file password"))
        assertTrue(fixture.files.exists("/pending/preserve-life-archive"))
        assertTrue(fixture.service.lastMessage.value.contains("legacy backup"))
        assertFalse(fixture.files.exists("/pending/life_archive/archive.json.enc"))
    }

    @Test
    fun selectedFileRestoreNeedsNeitherBackupDirectoryNorSavedPassword() = runBlocking {
        val fixture = backupFixture(password = null)
        fixture.settings.remove(SettingKeys.backupDir)
        fixture.files.seedRestorableBackup("chosen.enc", "file password", images = mapOf("photo.jpg" to "photo"))

        assertTrue(fixture.service.restoreFile("chosen.enc", "file password"))
        assertEquals("photo", fixture.files.readText("/app/images/photo.jpg"))
        assertTrue(fixture.files.exists("/pending/needs-backup-directory"))
        assertEquals("secured:file password", fixture.files.readText("/pending/backup_password.sec"))
    }

    @Test
    fun restartFailureKeepsPreparedRestoreAndRequestsManualRestart() = runBlocking {
        val fixture = backupFixture()
        fixture.files.seedFile(fixture.files.databasePathValue, "original database")
        fixture.files.seedRestorableBackup("chosen.enc", "file password", database = "new database")
        fixture.files.failRestart = true

        assertTrue(fixture.service.restore("chosen.enc", "file password"))
        assertEquals("Restore ready: legacy backup; restart manually", fixture.service.lastMessage.value)
        assertEquals("original database", fixture.files.readText(fixture.files.databasePathValue))
        assertEquals("new database", fixture.files.readText("/pending/database.db"))
    }

    @Test
    fun restoreDoesNotCopyAlreadyExtractedAttachmentsAgain() = runBlocking {
        val fixture = backupFixture()
        fixture.files.seedRestorableBackup("test.zip.enc", "file password", images = mapOf("photo.jpg" to "photo"))

        assertTrue(fixture.service.restore("test.zip.enc", "file password"))
        assertEquals("photo", fixture.files.readText("/app/images/photo.jpg"))
        assertFalse(fixture.files.copiedFiles.any { (source, destination) ->
            source == "/cache/restore_temp/images/photo.jpg" && destination.contains("_restore/app_data")
        })
    }
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
            setOf(
                DatabaseConfig.name,
                "images/cover.jpg",
                "diary_images/mood.png",
                "life_archive.json",
                "manifest.json",
            ),
            fixture.files.zipEntries.single().toSet(),
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
    fun backupIncludesNestedDiaryAudioAndOtherOwnedUserFiles() = runBlocking {
        val fixture = backupFixture()
        fixture.files.seedFile(fixture.files.databasePathValue, "db-content")
        fixture.files.seedFile("/app/diary/audio/23/recording.m4a", "voice")
        fixture.files.seedFile("/app/documents/policy.pdf", "policy")
        fixture.files.seedFile("/app/backups/previous.zip.enc", "old backup")

        assertTrue(fixture.service.backupNow())

        val entries = fixture.files.zipEntries.single()
        assertTrue("diary/audio/23/recording.m4a" in entries)
        assertTrue("documents/policy.pdf" in entries)
        assertFalse(entries.any { it.startsWith("backups/") })
    }

    @Test
    fun restorePreparesDatabaseAwayFromTheLiveDatabase() = runBlocking {
        val fixture = backupFixture()
        fixture.files.seedFile(fixture.files.databasePathValue, "current-db")
        fixture.files.seedRestorableBackup("test.zip.enc", "file password")

        assertTrue(fixture.service.restore("test.zip.enc", "file password"))

        assertTrue(fixture.secrets.preparedRestoredDatabases.single().startsWith("/cache/"))
    }

    @Test
    fun failedRestoreAlwaysRemovesDecryptedTemporaryData() = runBlocking {
        val fixture = backupFixture()
        fixture.files.seedRestorableBackup("test.zip.enc", "file password")

        assertFalse(fixture.service.restore("test.zip.enc", "wrong password"))

        assertFalse(fixture.files.exists("/cache/restore_temp"))
    }

    @Test
    fun damagedManifestPayloadNeverStagesRestoreOrOverwritesCurrentData() = runBlocking {
        val fixture = backupFixture()
        fixture.files.seedFile(fixture.files.databasePathValue, "current-db")
        val manifest = BackupManifest(schemaVersion = 31, sourceAppDataDir = "/old/DailySatori", files = listOf(
            BackupManifestFile("daily_satori.db", 11, "restored-db"),
            BackupManifestFile("life_archive.json", 8, "original"),
        ))
        fixture.files.seedRestorableBackup("broken.zip.enc", "file password", extraFiles = mapOf(
            "life_archive.json" to "modified", "manifest.json" to Json.encodeToString(manifest),
        ))

        assertFalse(fixture.service.restore("broken.zip.enc", "file password"))
        assertEquals("current-db", fixture.files.readText(fixture.files.databasePathValue))
        assertFalse(fixture.files.restartCalled)
        assertFalse(fixture.files.exists("/pending/database.db"))
        assertFalse(fixture.files.exists("/cache/restore_temp"))
    }

    @Test
    fun currentBackupRestoresLifeArchiveAlongsideDatabaseAndNestedAttachments() = runBlocking {
        val fixture = backupFixture()
        val life = "{\"records\":[]}"
        val voice = "voice"
        val manifest = BackupManifest(schemaVersion = 31, sourceAppDataDir = "/old/DailySatori", files = listOf(
            BackupManifestFile("daily_satori.db", 11, "restored-db"),
            BackupManifestFile("life_archive.json", life.length.toLong(), life),
            BackupManifestFile("diary/audio/23/voice.m4a", 5, voice),
        ))
        fixture.files.seedRestorableBackup("complete.zip.enc", "file password", extraFiles = mapOf(
            "life_archive.json" to life, "diary/audio/23/voice.m4a" to voice,
            "manifest.json" to Json.encodeToString(manifest),
        ))

        assertTrue(fixture.service.restore("complete.zip.enc", "file password"))
        assertEquals("voice", fixture.files.readText("/app/diary/audio/23/voice.m4a"))
        assertEquals("encrypted:$life", fixture.files.readText("/pending/life_archive/archive.json.enc"))
        assertEquals("secured:file password", fixture.files.readText("/pending/backup_password.sec"))
    }

    @Test
    fun backupKeepsEveryBackupWithinTenDaysAndDeletesOnlyOlderOnes() = runBlocking {
        val fixture = backupFixture()
        fixture.files.seedFile(fixture.files.databasePathValue, "db-content")
        (1..24).forEach { fixture.files.seedBackup(nameAt(fixture.clock.time - (it * 12).hours)) }

        val result = fixture.service.backupNow()

        assertTrue(result)
        assertEquals(
            (21..24).map { nameAt(fixture.clock.time - (it * 12).hours) }.toSet(),
            fixture.files.deletedBackups.toSet(),
        )
        assertEquals(21, fixture.service.listBackups().size)
    }

    @Test
    fun backupRetentionIncludesExactTenDayBoundary() = runBlocking {
        val fixture = backupFixture()
        fixture.files.seedFile(fixture.files.databasePathValue, "db-content")
        (1..10).forEach { fixture.files.seedBackup(nameAt(fixture.clock.time - it.hours)) }
        val boundary = fixture.clock.time - 10.days
        fixture.files.seedBackup(nameAt(boundary))
        val expired = nameAt(boundary - 1.seconds)
        fixture.files.seedBackup(expired)

        assertTrue(fixture.service.backupNow())

        assertEquals(listOf(expired), fixture.files.deletedBackups)
    }

    @Test
    fun infrequentBackupsAlwaysKeepAtLeastTenFiles() = runBlocking {
        val fixture = backupFixture()
        fixture.files.seedFile(fixture.files.databasePathValue, "db-content")
        (1..12).forEach { fixture.files.seedBackup(nameAt(fixture.clock.time - (it * 5).days)) }

        assertTrue(fixture.service.backupNow())

        assertEquals(10, fixture.service.listBackups().size)
        assertEquals(
            (10..12).map { nameAt(fixture.clock.time - (it * 5).days) }.toSet(),
            fixture.files.deletedBackups.toSet(),
        )
    }

    @Test
    fun clockMovingFarForwardSkipsCleanup() = runBlocking {
        val fixture = backupFixture()
        fixture.files.seedFile(fixture.files.databasePathValue, "db-content")
        (1..20).forEach { fixture.files.seedBackup(nameAt(fixture.clock.time - it.days)) }
        fixture.clock.time += 365.days

        assertTrue(fixture.service.backupNow())

        assertTrue(fixture.files.deletedBackups.isEmpty())
        assertEquals(21, fixture.service.listBackups().size)
    }

    @Test
    fun clockMovingBackwardSkipsCleanupAndKeepsNewBackup() = runBlocking {
        val fixture = backupFixture()
        fixture.files.seedFile(fixture.files.databasePathValue, "db-content")
        (1..20).forEach { fixture.files.seedBackup(nameAt(fixture.clock.time - it.days)) }
        fixture.clock.time -= 365.days

        assertTrue(fixture.service.backupNow())

        assertTrue(fixture.files.deletedBackups.isEmpty())
        assertEquals(21, fixture.service.listBackups().size)
    }

    @Test
    fun repeatedBackupsAfterForwardClockJumpStillKeepSafetyFloor() = runBlocking {
        val fixture = backupFixture()
        fixture.files.seedFile(fixture.files.databasePathValue, "db-content")
        (1..20).forEach { fixture.files.seedBackup(nameAt(fixture.clock.time - it.days)) }
        fixture.clock.time += 365.days
        assertTrue(fixture.service.backupNow())
        fixture.clock.time += 1.hours

        assertTrue(fixture.service.backupNow())

        assertEquals(10, fixture.service.listBackups().size)
        assertEquals(8, fixture.service.listBackups().count { it.name !in fixture.files.writtenBackups.map { it.name } })
    }

    @Test
    fun cleanupRecognizesLegacyNamesAndPreservesUnknownOrInvalidDates() = runBlocking {
        val fixture = backupFixture()
        fixture.files.seedFile(fixture.files.databasePathValue, "db-content")
        (1..10).forEach { fixture.files.seedBackup(nameAt(fixture.clock.time - it.hours)) }
        val legacy = nameAt(fixture.clock.time - 11.days).replace(".zip.enc", "_hint_abc.zip.enc")
        val unknown = listOf(
            "other_backup_2020-01-01-00-00-00.zip.enc",
            "daily_satori_backup_2020-02-30-00-00-00.zip.enc",
            "daily_satori_backup_unknown.zip.enc",
            "unrelated.enc",
        )
        (unknown + legacy).forEach { fixture.files.seedBackup(it) }

        assertTrue(fixture.service.backupNow())

        assertEquals(listOf(legacy), fixture.files.deletedBackups)
        assertTrue(fixture.service.listBackups().map { it.name }.containsAll(unknown))
    }

    @Test
    fun failedBackupWriteNeverDeletesExistingBackups() = runBlocking {
        val fixture = backupFixture()
        fixture.files.seedFile(fixture.files.databasePathValue, "db-content")
        (1..20).forEach { fixture.files.seedBackup(nameAt(fixture.clock.time - it.days)) }
        fixture.files.failWrite = true

        assertFalse(fixture.service.backupNow())

        assertTrue(fixture.files.deletedBackups.isEmpty())
        assertEquals(20, fixture.service.listBackups().size)
    }

    @Test
    fun cleanupFailureDoesNotFailSuccessfullySavedBackup() = runBlocking {
        val fixture = backupFixture()
        fixture.files.seedFile(fixture.files.databasePathValue, "db-content")
        (1..20).forEach { fixture.files.seedBackup(nameAt(fixture.clock.time - it.days)) }
        fixture.files.failDelete = true

        assertTrue(fixture.service.backupNow())

        assertEquals(21, fixture.service.listBackups().size)
        assertEquals(1.0, fixture.service.progress.value)
    }

    @Test
    fun missingNewBackupInDirectoryListingSkipsCleanup() = runBlocking {
        val fixture = backupFixture()
        fixture.files.seedFile(fixture.files.databasePathValue, "db-content")
        (1..20).forEach { fixture.files.seedBackup(nameAt(fixture.clock.time - it.days)) }
        fixture.files.hideWrittenBackups = true

        assertTrue(fixture.service.backupNow())

        assertTrue(fixture.files.deletedBackups.isEmpty())
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
        assertEquals(listOf("/cache/restore_temp/${DatabaseConfig.name}"), fixture.secrets.preparedRestoredDatabases)
        assertTrue(fixture.files.restartCalled)
        assertEquals(0.95, fixture.service.progress.value)
        assertEquals("Restore ready: legacy backup", fixture.service.lastMessage.value)
        assertFalse(fixture.service.isBackingUp.value)
    }
}

private fun backupFixture(password: String? = "correct horse battery"): BackupFixture {
    val files = FakeBackupFiles()
    val settings = mutableMapOf(SettingKeys.backupDir to "content://selected-backup-dir")
    val passwords = FakeBackupPasswords(password)
    val secrets = FakeBackupSecrets()
    val clock = FakeBackupClock(Instant.parse("2026-05-21T12:00:00Z"))
    return BackupFixture(
        files = files,
        settings = settings,
        secrets = secrets,
        clock = clock,
        service = BackupService(files, FakeBackupSettings(settings), passwords, secrets, clock,
            object : LifeArchiveBackup {
                override suspend fun exportSnapshot() = "{\"categories\":[],\"records\":[]}"
                override suspend fun prepareRestore(snapshot: String) = "encrypted:$snapshot".encodeToByteArray()
            }),
    )
}

private fun nameAt(time: Instant): String =
    "daily_satori_backup_${time.toString().replace(Regex("[:T]"), "-").take(19)}.zip.enc"

private class FakeBackupClock(var time: Instant) : Clock {
    override fun now(): Instant = time
}

private class BackupFixture(
    val files: FakeBackupFiles,
    val settings: MutableMap<String, String>,
    val secrets: FakeBackupSecrets,
    val clock: FakeBackupClock,
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
    override fun encryptedPassword(password: String) = "secured:$password".encodeToByteArray()
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
    override fun requiredUserFiles(databasePath: String, appDataDir: String) = emptyList<String>()
    override fun relocateRestoredData(databasePath: String, appDataDir: String, backupDirectory: String, availableFiles: Set<String>) = Unit
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
    val copiedFiles = mutableListOf<Pair<String, String>>()
    var restartCalled = false
    var failRestart = false
    var failWrite = false
    var failDelete = false
    var hideWrittenBackups = false

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
        extraFiles: Map<String, String> = emptyMap(),
    ) {
        directoryBackups.getValue("content://selected-backup-dir")[name] = "encrypted:$name"
        restorableBackups[name] = RestorableBackup(password, database, images, diaryImages, extraFiles)
    }

    override fun getAppDataDir(): String = appDataDirPath
    override fun getDatabasePath(): String = databasePathValue
    override fun getImagesDir(): String = imagesDirPath
    override fun getDiaryImagesDir(): String = diaryImagesDirPath
    override fun getCacheDir(): String = "/cache"
    override fun createDatabaseSnapshot(destination: String) = copyFile(databasePathValue, destination)
    override fun listFilesRecursively(path: String) = files.keys.filter { it.startsWith("$path/") }.sorted()
    override fun readFile(path: String) = files.getValue(path).encodeToByteArray()
    override fun writeFile(path: String, bytes: ByteArray) = seedFile(path, bytes.decodeToString())
    override fun fileSize(path: String) = readFile(path).size.toLong()
    override fun sha256(path: String) = files.getValue(path) // Stable fingerprint for this in-memory filesystem.
    override fun stageRestore(directory: String) {
        listFilesRecursively(directory).forEach { path -> copyFile(path, "/pending/${path.removePrefix("$directory/")}") }
    }

    override fun deleteFile(path: String): Boolean {
        val existed = files.remove(path) != null || directories.remove(path)
        files.keys.filter { it.startsWith("$path/") }.toList().forEach { files.remove(it) }
        directories.filter { it.startsWith("$path/") }.toList().forEach { directories.remove(it) }
        return existed
    }

    override fun exists(path: String): Boolean = path in files || path in directories
    override fun listFiles(path: String): List<String> = files.keys.filter { it.substringBeforeLast("/") == path }.sorted()

    override fun copyFile(src: String, dest: String) {
        copiedFiles += src to dest
        directories += dest.substringBeforeLast("/")
        files[dest] = files.getValue(src)
    }

    override fun moveFile(src: String, dest: String) {
        directories += dest.substringBeforeLast("/")
        files[dest] = files.remove(src) ?: error("Missing source")
    }

    override fun readFileFromUri(uri: String, destPath: String): Boolean = readFileFromDirectory("content://selected-backup-dir", uri, destPath)

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
        backup.extraFiles.forEach { (name, content) -> seedFile("$destDir/$name", content) }
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
            .filterNot { hideWrittenBackups && it in writtenBackups.map { backup -> backup.name } }

    override fun writeFileToDirectory(uri: String, name: String, sourcePath: String): String {
        check(!failWrite) { "Could not write backup" }
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
        check(!failDelete) { "Could not delete backup" }
        deletedBackups += name
        return directoryBackups[uri]?.remove(name) != null
    }

    override fun restartApp() {
        check(!failRestart) { "Restart denied" }
        restartCalled = true
        files["/pending/database.db"]?.let { seedFile(databasePathValue, it) }
        listFilesRecursively("/pending/app_data").forEach { path ->
            copyFile(path, "$appDataDirPath/${path.removePrefix("/pending/app_data/")}")
        }
    }
}

private data class WrittenBackup(val directory: String, val name: String)

private data class RestorableBackup(
    val password: String,
    val database: String,
    val images: Map<String, String>,
    val diaryImages: Map<String, String>,
    val extraFiles: Map<String, String>,
)

package com.dailysatori.service.backup

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.config.DatabaseConfig
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.IdeaTopicRepository
import com.dailysatori.service.ideatopic.*
import com.dailysatori.platform.FileManager
import com.dailysatori.service.security.SecretFieldProcessor
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import java.io.File
import java.nio.file.Files
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.*

class BackupRoundTripTest {
    @Test
    fun verificationReadsPublishedEncryptedBackupAndPreservesAllSourceBytes() = runBlocking {
        withVerificationFixture { files, service, backup ->
            val database = File(files.getDatabasePath()).readBytes()
            val voice = File(files.getAppDataDir(), "diary/audio/23/voice.m4a").readBytes()
            val originalArchive = backup.readBytes()
            val result = service.verifyLatestBackup()
            assertEquals(BackupVerificationStatus.PASSED, result.status)
            assertEquals(1L, result.summary["diary"])
            assertEquals(1L, result.summary["bookkeeping_entry"])
            assertEquals(1L, result.summary["images"])
            assertEquals(1L, result.summary["audio"])
            assertEquals(1L, result.summary["life_archive"])
            assertContentEquals(database, File(files.getDatabasePath()).readBytes())
            assertContentEquals(voice, File(files.getAppDataDir(), "diary/audio/23/voice.m4a").readBytes())
            assertContentEquals(originalArchive, backup.readBytes())
            assertFalse(files.pending.exists())
            assertNull(files.restoreResult)
            assertTrue(File(files.getCacheDir()).listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun verificationRejectsTruncatedPublishedCiphertext() = runBlocking {
        withVerificationFixture { files, service, backup ->
            backup.writeBytes(backup.readBytes().dropLast(20).toByteArray())
            assertVerificationFailure(files, service, BackupVerificationStage.DECRYPTING)
        }
    }

    @Test
    fun verificationRejectsAuthenticatedButInvalidZip() = runBlocking {
        withVerificationFixture { files, service, backup ->
            val invalid = checkNotNull(backup.parentFile).resolve("not-a-zip").apply { writeText("invalid zip") }
            FileManager().encryptFile(invalid.path, backup.path, "correct horse battery")
            invalid.delete()
            assertVerificationFailure(files, service, BackupVerificationStage.EXTRACTING)
        }
    }

    @Test
    fun verificationRejectsModifiedPayloadWithoutMatchingManifest() = runBlocking {
        withVerificationFixture { files, service, backup ->
            rewriteBackup(backup, updateManifest = false) { it.resolve("diary_images/photo.jpg").writeText("changed") }
            assertVerificationFailure(files, service, BackupVerificationStage.FILES)
        }
    }

    @Test
    fun verificationRejectsCorruptDatabaseEvenWhenFileHashMatches() = runBlocking {
        withVerificationFixture { files, service, backup ->
            rewriteBackup(backup) { it.resolve(DatabaseConfig.name).writeText("not sqlite") }
            assertVerificationFailure(files, service, BackupVerificationStage.DATABASE)
        }
    }

    @Test
    fun verificationRejectsMissingReferencedAudioEvenWithAConsistentManifest() = runBlocking {
        withVerificationFixture { files, service, backup ->
            rewriteBackup(backup) { it.resolve("diary/audio/23/voice.m4a").delete() }
            assertVerificationFailure(files, service, BackupVerificationStage.PREPARING)
        }
    }

    @Test
    fun verificationRejectsDatabaseMissingCurrentApplicationTables() = runBlocking {
        withVerificationFixture { files, service, backup ->
            rewriteBackup(backup) { content -> withDatabase(content.resolve(DatabaseConfig.name).path) {
                it.execute(null, "DROP TABLE idea_topic_message", 0)
            } }
            assertVerificationFailure(files, service, BackupVerificationStage.PREPARING)
        }
    }

    @Test
    fun verificationRejectsMissingRequiredDatabaseColumns() = runBlocking {
        withVerificationFixture { files, service, backup ->
            rewriteBackup(backup) { content -> withDatabase(content.resolve(DatabaseConfig.name).path) {
                it.execute(null, "ALTER TABLE diary RENAME COLUMN images TO lost_images", 0)
            } }
            assertVerificationFailure(files, service, BackupVerificationStage.PREPARING)
        }
    }

    @Test
    fun verificationRejectsFutureDatabaseVersions() = runBlocking {
        withVerificationFixture { files, service, backup ->
            rewriteBackup(backup) { content -> withDatabase(content.resolve(DatabaseConfig.name).path) {
                it.execute(null, "UPDATE setting SET value = '9999' WHERE key = 'schema_version'", 0)
            } }
            assertVerificationFailure(files, service, BackupVerificationStage.DATABASE)
        }
    }

    @Test
    fun legacyVerificationMigratesOnlyTheTemporaryCopyAndPreservesOldData() = runBlocking {
        withVerificationFixture { files, service, backup ->
            rewriteBackup(backup, updateManifest = false) { content ->
                content.resolve(BackupManifestName).delete()
                content.resolve(LifeArchiveBackupName).delete()
                withDatabase(content.resolve(DatabaseConfig.name).path) {
                    it.execute(null, "UPDATE setting SET value = '32' WHERE key = 'schema_version'", 0)
                    listOf("idea_topic_message", "idea_topic_session", "idea_topic_event", "idea_topic_source", "idea_topic").forEach { table ->
                        it.execute(null, "DROP TABLE $table", 0)
                    }
                }
            }
            val publishedBytes = backup.readBytes()
            val source = File(files.getDatabasePath()).readBytes()
            val result = service.verifyLatestBackup()
            assertEquals(BackupVerificationStatus.LIMITED, result.status)
            assertEquals(1L, result.summary["diary"])
            assertEquals(1L, result.summary["bookkeeping_entry"])
            assertContentEquals(source, File(files.getDatabasePath()).readBytes())
            assertContentEquals(publishedBytes, backup.readBytes())
            assertFalse(files.pending.exists())
            assertTrue(File(files.getCacheDir()).listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun verificationRejectsMalformedLifeArchiveEvenWithAConsistentManifest() = runBlocking {
        withVerificationFixture { files, service, backup ->
            rewriteBackup(backup) { it.resolve(LifeArchiveBackupName).writeText("not json") }
            assertVerificationFailure(files, service, BackupVerificationStage.LIFE_ARCHIVE)
        }
    }

    private suspend fun assertVerificationFailure(files: DiskFiles, service: BackupService, stage: BackupVerificationStage) {
        val source = File(files.getDatabasePath()).readBytes()
        val result = service.verifyLatestBackup()
        assertEquals(BackupVerificationStatus.FAILED, result.status)
        assertEquals(stage, result.stage)
        assertContentEquals(source, File(files.getDatabasePath()).readBytes())
        assertFalse(files.pending.exists())
        assertNull(files.restoreResult)
        assertTrue(File(files.getCacheDir()).listFiles().orEmpty().isEmpty())
    }

    private suspend fun withVerificationFixture(block: suspend (DiskFiles, BackupService, File) -> Unit) {
        val root = Files.createTempDirectory("verify-round-trip").toFile()
        try {
            val files = DiskFiles(root.resolve("source"))
            val cipher = DeviceCipher("source")
            seedDatabase(files, cipher)
            files.writeFile("${files.getAppDataDir()}/diary/audio/23/voice.m4a", "voice bytes".toByteArray())
            files.writeFile("${files.getAppDataDir()}/diary_images/photo.jpg", "photo bytes".toByteArray())
            val backups = root.resolve("backups").apply { mkdirs() }
            val service = service(files, backups, cipher, "{\"categories\":[],\"records\":[{\"title\":\"policy\"}]}")
            assertTrue(service.backupNow(), service.lastMessage.value)
            block(files, service, backups.listFiles()!!.single())
        } finally { root.deleteRecursively() }
    }

    private fun rewriteBackup(backup: File, updateManifest: Boolean = true, change: (File) -> Unit) {
        val temporary = Files.createTempDirectory("rewrite-backup").toFile()
        try {
            val manager = FileManager()
            val zip = temporary.resolve("backup.zip")
            val content = temporary.resolve("content")
            manager.decryptFile(backup.path, zip.path, "correct horse battery")
            manager.extractZip(zip.path, content.path)
            change(content)
            if (updateManifest) {
                val manifestFile = content.resolve(BackupManifestName)
                val manifest = Json.decodeFromString<BackupManifest>(manifestFile.readText())
                val entries = manager.listFilesRecursively(content.path).filter { it != manifestFile.path }.map {
                    BackupManifestFile(it.removePrefix("${content.path}/"), manager.fileSize(it), manager.sha256(it))
                }
                manifestFile.writeText(Json.encodeToString(manifest.copy(files = entries)))
            }
            manager.createZip(content.path, zip.path, manager.listFilesRecursively(content.path))
            manager.encryptFile(zip.path, backup.path, "correct horse battery")
        } finally { temporary.deleteRecursively() }
    }

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
                assertEquals("{\"amount\":12345,\"note\":\"original ledger\"}",
                    destinationCipher.decrypt(queries.selectBookkeepingEntries().executeAsOne().encrypted_payload))
                assertEquals(if (directFile) "" else backups.path, queries.selectSettingByKey("backup_directory").executeAsOne().value_)
                assertIdeaTopicData(DailySatoriDatabase(driver))
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
        driver.execute(null, "INSERT INTO setting(key,value,created_at,updated_at) VALUES('schema_version',?,1,1)", 1) {
            bindString(0, DatabaseConfig.currentSchemaVersion.toString())
        }
        driver.execute(null, "INSERT INTO bookkeeping_entry(id,encrypted_payload,created_at,updated_at) VALUES('ledger-1',?,1,1)", 1) {
            bindString(0, cipher.encrypt("{\"amount\":12345,\"note\":\"original ledger\"}"))
        }
        seedIdeaTopicData(DailySatoriDatabase(driver))
    }

    private fun seedIdeaTopicData(database: DailySatoriDatabase) {
        val q = database.dailySatoriQueries
        q.insertIdeaTopic("topic-main", "Main product", "Main description", "Main origins", "Main conclusions", "Main next action",
            "advancing", null, 7, 1, 10)
        q.insertIdeaTopic("topic-merged", "Merged product", "Merged description", "Merged origins", "Merged conclusions", "Merged next action",
            "researching", null, 2, 2, 11)
        val original = IdeaSourceSnapshot(IdeaSourceKey("diary", "23"), "Diary source", "first snapshot", originalCreatedAt = 1, originalRecordId = "23")
        val duplicate = original.copy(originalTitle = "Earlier capture", originalContent = "duplicate snapshot must survive")
        val opportunity = IdeaSourceSnapshot(IdeaSourceKey("news_opportunity", "opportunity-9"), "News source", "news snapshot",
            originalUrl = "https://example.test/news", analysisId = "analysis-9", analysisContent = "analysis snapshot", analysisVersion = "v2")
        q.insertIdeaTopicSource("source-main", "topic-main", "topic-main", "diary", "23", Json.encodeToString(original), 3, 3, 3)
        q.insertIdeaTopicSource("source-duplicate", "topic-merged", "topic-merged", "diary", "23", Json.encodeToString(duplicate), 4, 4, 4)
        q.insertIdeaTopicSource("source-news", "topic-merged", "topic-merged", "news_opportunity", "opportunity-9", Json.encodeToString(opportunity), 5, 5, 5)
        q.insertIdeaTopicEvent("progress-1", "topic-merged", "topic-merged", "progress", "{\"text\":\"prototype completed\"}", 6)
        q.insertIdeaTopicSession("session-main", "topic-main", "topic-main", "First conversation", "Retained summary",
            "main-reply", "[\"main-user\",\"main-reply\"]", "ready", 7, 10)
        q.insertIdeaTopicSession("session-child", "topic-merged", "topic-merged", "Second conversation", "Partial summary",
            "child-user", "[\"child-user\"]", "pending", 8, 11)
        q.insertIdeaTopicMessage("main-user", "session-main", "user", "main question", "complete", null, 8)
        q.insertIdeaTopicMessage("main-reply", "session-main", "assistant", "main answer", "complete", null, 9)
        q.insertIdeaTopicMessage("child-user", "session-child", "user", "child question", "complete", null, 10)
        q.insertIdeaTopicMessage("child-reply", "session-child", "assistant", "unfinished streamed answer", "pending", null, 11)
        val repository = IdeaTopicRepository(database)
        repository.merge("topic-merged", "topic-main", "merge-1", 20)
        repository.insertDraft("draft-1", "topic-main", "topic-merged", 8,
            IdeaTopicContent("Unapplied title", "Unapplied description", "Draft origins", "Draft conclusions", "Draft next action"),
            listOf("source-news", "progress-1"), "session-child", 21)
    }

    private fun assertIdeaTopicData(database: DailySatoriDatabase) {
        val q = database.dailySatoriQueries
        val main = q.selectMainIdeaTopics().executeAsOne()
        assertEquals("topic-main", main.id)
        assertEquals("Main product", main.title)
        assertEquals("Main description", main.description)
        assertEquals("Main origins", main.provenance_summary)
        assertEquals("Main conclusions", main.conclusions)
        assertEquals("Main next action", main.next_action)
        assertEquals("advancing", main.status)
        assertEquals(8L, main.context_revision)
        assertEquals(2L, main.source_count)
        assertEquals("{\"text\":\"prototype completed\"}", main.latest_progress)
        val merged = q.selectIdeaTopicById("topic-merged").executeAsOne()
        assertEquals("topic-main", merged.merged_into_topic_id)
        assertEquals("Merged product", merged.title)
        assertEquals("Merged description", merged.description)
        assertEquals("researching", merged.status)
        assertEquals(listOf("topic-merged"), q.selectIdeaTopicIdsMergedInto("topic-main").executeAsList())
        assertIdeaTopicSourcesAndEvents(database)
        assertIdeaTopicConversations(database)
        val draft = IdeaTopicRepository(database).draftsByTopicSync("topic-main").single()
        assertEquals("draft-1", draft.id)
        assertEquals("pending", draft.state)
        assertEquals(8L, draft.baseRevision)
        val draftPayload = Json.decodeFromString<IdeaDraftEventPayload>(q.selectIdeaTopicEventById("draft-1").executeAsOne().payload_json)
        assertEquals("session-child", draftPayload.originSessionId)
        assertEquals("Unapplied title", draft.proposal.content.title)
        assertEquals("Unapplied description", draft.proposal.content.description)
        assertEquals("Draft conclusions", draft.proposal.content.conclusions)
        assertEquals("Draft next action", draft.proposal.content.nextAction)
        assertEquals(listOf("source-news", "progress-1"), draft.proposal.referenceIds)
    }

    private fun assertIdeaTopicSourcesAndEvents(database: DailySatoriDatabase) {
        val q = database.dailySatoriQueries
        val sources = q.selectIdeaTopicSourcesByTopic("topic-main").executeAsList()
        assertEquals(listOf("source-main", "source-news"), sources.map { it.id })
        assertEquals("first snapshot", Json.decodeFromString<IdeaSourceSnapshot>(sources.first().snapshot_json).originalContent)
        val news = sources.last()
        assertEquals("topic-merged", news.original_topic_id)
        val snapshot = Json.decodeFromString<IdeaSourceSnapshot>(news.snapshot_json)
        assertEquals("news snapshot", snapshot.originalContent)
        assertEquals("analysis snapshot", snapshot.analysisContent)
        assertEquals("analysis-9", snapshot.analysisId)
        assertEquals("v2", snapshot.analysisVersion)
        assertEquals("https://example.test/news", snapshot.originalUrl)
        val progress = q.selectIdeaTopicEventById("progress-1").executeAsOne()
        assertEquals("topic-main", progress.topic_id)
        assertEquals("topic-merged", progress.original_topic_id)
        assertEquals("prototype completed", Json.decodeFromString<IdeaProgressEventPayload>(progress.payload_json).text)
        val merge = Json.decodeFromString<IdeaMergedEventPayload>(q.selectIdeaTopicEventById("merge-1").executeAsOne().payload_json)
        assertEquals("topic-merged", merge.fromTopicId)
        assertEquals("topic-main", merge.intoTopicId)
        assertEquals("Merged conclusions", merge.fromContent.conclusions)
        val duplicate = merge.duplicateSourceSnapshots.single()
        assertEquals(IdeaSourceKey("diary", "23"), duplicate.key)
        assertEquals("Earlier capture", duplicate.originalTitle)
        assertEquals("duplicate snapshot must survive", duplicate.originalContent)
    }

    private fun assertIdeaTopicConversations(database: DailySatoriDatabase) {
        val q = database.dailySatoriQueries
        assertEquals(setOf("session-main", "session-child"), q.selectIdeaTopicSessionsByTopic("topic-main").executeAsList().map { it.id }.toSet())
        val session = q.selectIdeaTopicSessionById("session-main").executeAsOne()
        assertEquals("Retained summary", session.summary)
        assertEquals("main-reply", session.summary_through_message_id)
        assertEquals("[\"main-user\",\"main-reply\"]", session.summary_covered_message_ids)
        assertEquals("ready", session.summary_status)
        val child = q.selectIdeaTopicSessionById("session-child").executeAsOne()
        assertEquals("topic-main", child.topic_id)
        assertEquals("topic-merged", child.original_topic_id)
        assertEquals("Second conversation", child.title)
        assertEquals("Partial summary", child.summary)
        assertEquals("pending", child.summary_status)
        assertEquals(listOf("main question", "main answer"), q.selectIdeaTopicMessagesBySession("session-main").executeAsList().map { it.content })
        val messages = q.selectIdeaTopicMessagesBySession("session-child").executeAsList()
        assertEquals(listOf("child-user", "child-reply"), messages.map { it.id })
        assertEquals(listOf("child question", "unfinished streamed answer"), messages.map { it.content })
        assertEquals(listOf("user", "assistant"), messages.map { it.role })
        assertEquals(listOf("complete", "pending"), messages.map { it.status })
        assertEquals(listOf("child-reply"), q.selectIdeaTopicMessagesByStatus("pending").executeAsList().map { it.id })
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
                BackupDatabaseData(it).prepareSecrets(cipher)
            }
            override fun checkBackupDatabase(databasePath: String) = withDatabase(databasePath) {
                BackupDatabaseData(it).checkBackupDatabase()
            }
            override fun backupDatabaseSummary(databasePath: String): Map<String, Long> {
                val reference = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
                return try {
                    DailySatoriDatabase.Schema.create(reference)
                    withDatabase(databasePath) { BackupDatabaseData(it).verifiedSummary(BackupDatabaseData(reference).schemaColumns()) }
                } finally { reference.close() }
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
        val pending get() = root.resolve("pending")
        private val installer get() = BackupRestoreTransaction(pending, File(getAppDataDir()), File(getDatabasePath()), life, password)
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

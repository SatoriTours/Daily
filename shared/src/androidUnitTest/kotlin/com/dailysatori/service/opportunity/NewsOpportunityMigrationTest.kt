package com.dailysatori.service.opportunity

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.backup.BackupDatabaseData
import com.dailysatori.service.backup.BackupValidationException
import com.dailysatori.service.backup.createSqliteBackupSnapshot
import com.dailysatori.service.externalfavorites.sha256Hex
import com.dailysatori.service.migration.DatabaseMigration
import com.dailysatori.service.migration.DatabaseSchemaInventory
import com.dailysatori.service.security.DatabaseSchemaUpgrade
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class NewsOpportunityMigrationTest {
    @Test
    fun physical54ImportsDistinctSnapshotsAndNeverRereadsLegacyArchive() = withV54 { driver, settings ->
        val expected = archive()
        val original = Json.encodeToString(expected).dropLast(1) + ""","unknownFutureField":{"keep":true}}"""
        settings.upsert(LEGACY_KEY, original)
        migrate(driver, settings)

        assertEquals("55", settings.get(SettingKeys.schemaVersion))
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { fresh ->
            DailySatoriDatabase.Schema.create(fresh)
            assertEquals(opportunityDdl(fresh), opportunityDdl(driver))
        }
        assertEquals(expected, store(driver).load())
        val service = service(driver)
        service.setSaved("item", false)
        assertEquals(original, settings.get(LEGACY_KEY))
        migrate(driver, settings)
        assertEquals(expected.items.first().copy(saved = false, savedAt = null), store(driver).load().items.first())
    }

    @Test
    fun chunkedUnicodeAndLongBodyRoundTripAndEmpty54IsValid() {
        withV54 { driver, settings ->
            val expected = archive("正文🙂𠮷".repeat(400_000))
            seedChunks(settings, Json.encodeToString(expected))
            val manifest = settings.get(LEGACY_KEY)
            migrate(driver, settings)
            assertEquals(expected, store(driver).load())
            assertEquals(manifest, settings.get(LEGACY_KEY))
        }
        withV54 { driver, settings ->
            migrate(driver, settings)
            assertEquals("55", settings.get(SettingKeys.schemaVersion))
            assertEquals(OpportunityArchive(), store(driver).load())
        }
    }

    @Test
    fun corruptLegacyArchiveLeavesVersionAndTablesUnchangedAndCanRetry() = withV54 { driver, settings ->
        val expected = archive()
        settings.upsert(LEGACY_KEY, "{broken")
        assertFails { migrate(driver, settings) }
        assertEquals("{broken", settings.get(LEGACY_KEY))
        seedChunks(settings, Json.encodeToString(expected))
        val part = assertNotNull(settings.get("$LEGACY_KEY:chunk:0"))
        settings.delete("$LEGACY_KEY:chunk:0")
        assertFails { migrate(driver, settings) }
        settings.upsert("$LEGACY_KEY:chunk:0", "$part ")
        assertFails { migrate(driver, settings) }
        assertEquals("54", settings.get(SettingKeys.schemaVersion))
        assertTrue(opportunityDdl(driver).isEmpty())

        settings.upsert("$LEGACY_KEY:chunk:0", part)
        migrate(driver, settings)
        assertEquals(expected, store(driver).load())
    }

    @Test
    fun missingManifestWithChunksRollsBackAndCanRetryAfterRestoringIt() = withV54 { driver, settings ->
        val expected = archive("旧正文🙂".repeat(10_000))
        seedChunks(settings, Json.encodeToString(expected))
        val manifest = assertNotNull(settings.get(LEGACY_KEY))
        settings.delete(LEGACY_KEY)
        val chunks = settings.getAllKeys().filter { it.startsWith("$LEGACY_KEY:chunk:") }
            .associateWith { assertNotNull(settings.get(it)) }
        assertTrue(chunks.size > 1)

        val error = assertFailsWith<IllegalStateException> { migrate(driver, settings) }
        assertContains(error.message.orEmpty(), "缺少主清单")
        assertEquals("54", settings.get(SettingKeys.schemaVersion))
        assertNull(settings.get(LEGACY_KEY))
        assertTrue(opportunityDdl(driver).isEmpty())
        chunks.forEach { (key, value) ->
            assertContentEquals(value.encodeToByteArray(), assertNotNull(settings.get(key)).encodeToByteArray())
        }

        settings.upsert(LEGACY_KEY, manifest)
        migrate(driver, settings)
        assertEquals("55", settings.get(SettingKeys.schemaVersion))
        assertEquals(expected, store(driver).load())
    }

    @Test
    fun versionlessDatabaseWithChunksIsRejectedBeforeCandidateExport() = withV54 { driver, settings ->
        DatabaseSchemaInventory.createOpportunityObjects(driver)
        settings.delete(SettingKeys.schemaVersion)
        val chunk = "$LEGACY_KEY:chunk:0"
        settings.upsert(chunk, "待恢复分块🙂")
        withBackupFile(driver) { root, file ->
            val key = File(root, "wrapped-key").apply { writeText("test key envelope") }
            val open = { path: File -> JdbcSqliteDriver("jdbc:sqlite:${path.path}") }
            val upgrade = DatabaseSchemaUpgrade(File(root, "install"), file, key, open,
                export = { _, _ -> fail("损坏归档不能进入候选导出") }, availableBytes = { Long.MAX_VALUE })
            val error = assertFailsWith<IllegalStateException> { upgrade.upgrade() }
            assertContains(error.message.orEmpty(), "缺少主清单")
            open(file).use { original ->
                val preserved = SettingRepository(DailySatoriDatabase(original))
                assertNull(preserved.get(SettingKeys.schemaVersion))
                assertEquals("待恢复分块🙂", preserved.get(chunk))
            }
        }
        assertFailsWith<IllegalStateException> { migrate(driver, settings) }
        assertNull(settings.get(SettingKeys.schemaVersion))
    }

    @Test
    fun similarlyNamedSettingsAreNotArchiveChunks() = withV54 { driver, settings ->
        val unrelated = listOf("$LEGACY_KEY:chunked:0", "newsXopportunity_archive_v1:chunk:0",
            "News_opportunity_archive_v1:chunk:0")
        unrelated.forEach { settings.upsert(it, "不相关") }
        migrate(driver, settings)
        assertEquals("55", settings.get(SettingKeys.schemaVersion))
        assertEquals(OpportunityArchive(), store(driver).load())
        unrelated.forEach { assertEquals("不相关", settings.get(it)) }
    }

    @Test
    fun favoriteUpdatesOnlyItsOwnBusinessRow() = withV54 { driver, settings ->
        val expected = archive()
        settings.upsert(LEGACY_KEY, Json.encodeToString(expected))
        migrate(driver, settings)
        val service = service(driver)
        service.refresh()
        driver.writes.clear()
        driver.reads.clear()
        service.setSaved("second", true)
        assertEquals(1, driver.writes.size)
        assertTrue(driver.writes.single().startsWith("UPDATE opportunity_item"), driver.writes.toString())
        assertTrue(driver.reads.none { "opportunity_" in it }, driver.reads.toString())
        val updated = store(driver).load()
        assertEquals(expected.items.first(), updated.items.first())
        assertTrue(updated.items.last().saved)
    }

    @Test
    fun failedCheckpointInsertRollsBackMigrationAndCanRetry() = withV54 { driver, settings ->
        val expected = archive()
        settings.upsert(LEGACY_KEY, Json.encodeToString(expected))
        DatabaseSchemaInventory.createOpportunityObjects(driver)
        driver.execute(null, """CREATE TRIGGER fail_import BEFORE INSERT ON opportunity_checkpoint
            BEGIN SELECT RAISE(ABORT, 'test import failure'); END""", 0)

        assertFails { migrate(driver, settings) }
        assertEquals("54", settings.get(SettingKeys.schemaVersion))
        assertEquals(OpportunityArchive(), store(driver).load())
        assertEquals(listOf("0"), DatabaseSchemaInventory.strings(driver, "SELECT count(*) FROM opportunity_source"))
        driver.execute(null, "DROP TRIGGER fail_import", 0)

        migrate(driver, settings)
        assertEquals(expected, store(driver).load())
    }

    @Test
    fun copiedCandidateMustPreserveConvertedDataBeforeInstallation() = withV54 { driver, settings ->
        val expected = archive()
        val encoded = Json.encodeToString(expected)
        settings.upsert(LEGACY_KEY, encoded)
        withBackupFile(driver) { root, file ->
            val key = File(root, "wrapped-key").apply { writeText("test key envelope") }
            val open = { path: File -> JdbcSqliteDriver("jdbc:sqlite:${path.path}") }
            fun upgrade(tamper: Boolean) = DatabaseSchemaUpgrade(File(root, "install"), file, key, open,
                export = { source, dest -> createSnapshot(source, dest) },
                migrate = { target ->
                    migrate(target, SettingRepository(DailySatoriDatabase(target)))
                    if (tamper) target.execute(null, "UPDATE opportunity_item SET saved = 0", 0)
                }, availableBytes = { Long.MAX_VALUE },
                checkDatabase = { assertEquals(listOf("ok"), DatabaseSchemaInventory.strings(it, "PRAGMA integrity_check")) })

            val failure = assertFailsWith<IllegalStateException> { upgrade(tamper = true).upgrade() }
            assertContains(failure.message.orEmpty(), "迁移机会归档核对失败")
            open(file).use { original ->
                val originalSettings = SettingRepository(DailySatoriDatabase(original))
                assertEquals("54", originalSettings.get(SettingKeys.schemaVersion))
                assertEquals(encoded, originalSettings.get(LEGACY_KEY))
                assertTrue(opportunityDdl(original).isEmpty())
            }
            upgrade(tamper = false).upgrade()
            open(file).use { assertEquals(expected, store(it).load()) }
        }
    }

    @Test
    fun stagedBackupUpgradeAndRestoreIncludeOpportunityRows() = withV54 { driver, settings ->
        val expected = archive()
        settings.upsert(LEGACY_KEY, Json.encodeToString(expected))
        withBackupFile(driver) { _, file ->
            JdbcSqliteDriver("jdbc:sqlite:${file.path}").use { backup ->
                val data = BackupDatabaseData(backup)
                data.prepareSecrets(unusedCipher, legacyFields = false)
                data.prepareRestore("/app", "test-backups")
                assertEquals(expected, store(backup).load())
                JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { fresh ->
                    DailySatoriDatabase.Schema.create(fresh)
                    val schema = BackupDatabaseData(fresh).schemaColumns()
                    assertEquals(2L, data.verifiedSummary(schema)["opportunity_item"])
                    backup.execute(null, "DROP TABLE opportunity_checkpoint", 0)
                    assertFailsWith<BackupValidationException> { data.verifiedSummary(schema) }
                }
            }
        }
        assertEquals("54", settings.get(SettingKeys.schemaVersion))
    }

    private fun withBackupFile(driver: SqlDriver, block: (File, File) -> Unit) {
        val root = createTempDirectory("opportunity-backup").toFile()
        try {
            val file = File(root, "copy.db")
            createSnapshot(driver, file)
            block(root, file)
        } finally { root.deleteRecursively() }
    }

    private fun createSnapshot(driver: SqlDriver, file: File) =
        createSqliteBackupSnapshot(driver, "", file.path) { _, _ -> error("Requires SQLite VACUUM INTO") }

    private val unusedCipher = object : SecretValueCipher {
        override fun encrypt(value: String): String = error("Not a legacy credential restore")
        override fun decrypt(value: String): String = error("Not a legacy credential restore")
        override fun isEncrypted(value: String): Boolean = error("Not a legacy credential restore")
    }

    private fun archive(body: String = "已读快照"): OpportunityArchive {
        val read = ReadNewsArticle("same", "已读", body, "https://example.test/read", "来源", "2026-09-20", 23, 99)
        val candidate = read.copy(title = "候选", content = "候选快照", readAt = 0)
        val item = NewsOpportunity("item", read.copy(title = "分析", content = "分析快照", readAt = 15),
            "方案", "类别", "事实", "关系", "行动", "风险", "分析", 456, true, true, "历史提醒", 789,
            relevanceScore = 91, actionabilityScore = 81, preview = OpportunityPreview("短标题", "原因", "机会"))
        val second = item.copy(id = "second", saved = false, savedAt = null, preview = null,
            article = read.copy(key = "other", content = "另一篇"))
        return OpportunityArchive(articles = listOf(read, second.article), items = listOf(item, second), focus = "关注",
            candidates = listOf(candidate, second.article),
            checkpoints = listOf(OpportunityCheckpoint("other", "a"), OpportunityCheckpoint("same", "b")),
            lastAttemptAt = 555, lastAttemptContext = "版本", lastError = "错误", dismissedErrorTaskId = 487)
    }

    private fun seedChunks(settings: SettingRepository, encoded: String) {
        var index = 0
        var offset = 0
        while (offset < encoded.length) {
            var end = (offset + 32_768).coerceAtMost(encoded.length)
            if (end < encoded.length && encoded[end - 1].isHighSurrogate()) end--
            settings.upsert("$LEGACY_KEY:chunk:${index++}", encoded.substring(offset, end))
            offset = end
        }
        settings.upsert(LEGACY_KEY, "news-opportunity-chunks-v2:$index:${sha256Hex(encoded)}")
    }

    private fun migrate(driver: SqlDriver, settings: SettingRepository) = DatabaseMigration(driver, settings).runMigrations()
    private fun store(driver: SqlDriver) = NewsOpportunityStore(DailySatoriDatabase(driver))
    private fun service(driver: SqlDriver) = NewsOpportunityService(store(driver), OpportunityAnalyzer { null }, object : NewsOpportunityContext {
        override val enabled = false
        override fun verifiedContext(): String? = null
    })

    private fun withV54(block: suspend (WriteTrace, SettingRepository) -> Unit) = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { jdbc ->
            // Schema 54 contains every current DDL object except the opportunity domain.
            DailySatoriDatabase.Schema.create(object : SqlDriver by jdbc {
                override fun execute(identifier: Int?, sql: String, parameters: Int,
                    binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<Long> =
                    if ("opportunity_" in sql) QueryResult.Value(0) else jdbc.execute(identifier, sql, parameters, binders)
            })
            val driver = WriteTrace(jdbc)
            val settings = SettingRepository(DailySatoriDatabase(driver))
            settings.upsert(SettingKeys.schemaVersion, "54")
            block(driver, settings)
        }
    }

    private class WriteTrace(private val delegate: SqlDriver) : SqlDriver by delegate {
        val writes = mutableListOf<String>()
        val reads = mutableListOf<String>()
        override fun <R> executeQuery(identifier: Int?, sql: String, mapper: (SqlCursor) -> QueryResult<R>,
            parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<R> {
            reads += sql
            return delegate.executeQuery(identifier, sql, mapper, parameters, binders)
        }

        override fun execute(identifier: Int?, sql: String, parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<Long> {
            writes += sql
            return delegate.execute(identifier, sql, parameters, binders)
        }
    }

    private fun opportunityDdl(driver: SqlDriver) = DatabaseSchemaInventory.strings(driver,
        "SELECT sql FROM sqlite_master WHERE name LIKE 'opportunity_%' OR name LIKE 'idx_opportunity_%' ORDER BY name")

    private companion object { const val LEGACY_KEY = "news_opportunity_archive_v1" }
}

package com.dailysatori.service.security

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.config.DatabaseConfig
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.ReminderAiBatchRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.backup.exportEncryptedDatabase
import com.dailysatori.service.migration.DatabaseMigration
import com.dailysatori.service.migration.DatabaseSchemaInventory
import com.dailysatori.service.migration.ReminderDraftMigration
import com.dailysatori.shared.db.DailySatoriDatabase
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.*
import org.junit.Assume.assumeTrue

/** Uses the actual pre-refactor DDL, not current generated tables relabelled as version 38. */
class HistoricalSchemaUpgradeTest {
    @Test fun emptyFrozenVersion38UpgradesAndReopensWithoutExport() = fixture(false) { root, db, key ->
        upgrade(root, db, key).upgrade()
        jdbc(db).use { driver ->
            DatabaseSchemaInventory.validate(driver)
            assertEquals(DatabaseConfig.currentSchemaVersion.toString(), settings(driver).get(SettingKeys.schemaVersion))
        }
        upgrade(root, db, key, export = { _, _ -> error("Current database must not be exported again") }).upgrade()
        assertEquals("original-wrapped-key", key.readText())
    }

    @Test fun populatedFrozenVersion38PreservesAllDraftCombinationsAndOriginalDomains() = fixture { root, db, key ->
        val original = jdbc(db).use { ReminderDraftMigration.read(it, "reminder_ai_draft", ReminderDraftMigration.legacyColumns) }
        upgrade(root, db, key).upgrade()
        jdbc(db).use { driver ->
            assertDomains(driver)
            assertDrafts(driver, original)
            assertTrue(ReminderAiBatchRepository(DailySatoriDatabase(driver)).reconcileScheduleIntents().isEmpty())
        }
        val installed = db.readBytes()
        upgrade(root, db, key).upgrade()
        assertContentEquals(installed, db.readBytes())
    }

    @Test fun legacyManuallyReaddedSuppressedTagKeepsVisibleTagAndSuppressionHistory() = fixture { root, db, key ->
        upgrade(root, db, key).upgrade()
        jdbc(db).use { driver ->
            assertDomains(driver)
            assertEquals("旧标签", DailySatoriDatabase(driver).dailySatoriQueries.selectDiaryById(1).executeAsOne().tags)
            assertEquals(listOf("active:manual", "suppressed:automatic"), sqlStrings(driver,
                "SELECT decision||':'||origin FROM diary_tag_history ORDER BY decision"))
            assertEquals(listOf("active:manual"), sqlStrings(driver,
                "SELECT state||':'||origin FROM diary_tag_assignment"))
        }
    }

    @Test fun eachTamperedDomainRejectsCandidateAndPreservesOriginalDatabaseAndKey() {
        val changes = listOf(
            "UPDATE article SET comment='tampered'",
            "UPDATE diary_attachment SET transcript='tampered' WHERE kind='audio'",
            "UPDATE reminder_policy_snapshot SET payload_json='tampered'",
            "UPDATE reminder_ai_draft_legacy SET source_text='tampered'",
            "UPDATE chat_conversation SET content='tampered'",
            "UPDATE memory_entry SET content='tampered'",
            "UPDATE idea_topic SET title='tampered'",
            "UPDATE idea_source_snapshot SET payload_json='tampered'",
        )
        for (change in changes) fixture { root, db, key ->
            val original = db.readBytes()
            assertFails("Must reject candidate after $change") {
                upgrade(root, db, key, migrate = { driver -> migrate(driver); driver.execute(null, change, 0) }).upgrade()
            }
            assertContentEquals(original, db.readBytes(), change)
            assertEquals("original-wrapped-key", key.readText())
            assertFalse(File(db.path + ".schema-candidate").exists())
            jdbc(db).use { assertEquals("38", settings(it).get(SettingKeys.schemaVersion)) }
        }
    }

    @Test fun realEncryptedFrozenVersion38SurvivesUpgradeRekeyedSnapshotAndReopen() {
        val binary = System.getenv("SQLCIPHER_BIN")
        assumeTrue("Requires real host SQLCipher", binary != null && File(binary).canExecute())
        val root = createTempDirectory().toFile()
        try {
            val db = File(root, "encrypted.db")
            val wrapped = File(root, "key.sec").apply { writeText("original-wrapped-key") }
            val key = DatabaseKey.generate()
            fun open(file: File, password: DatabaseKey = key): SqlDriver = HostSqlCipherDriver(binary!!, file).also {
                it.run("PRAGMA key = \"${password.sqlCipherPassword().decodeToString()}\"; PRAGMA foreign_keys=ON;")
            }
            val original = open(db).use { driver ->
                createHistorical(driver, true)
                ReminderDraftMigration.read(driver, "reminder_ai_draft", ReminderDraftMigration.legacyColumns)
            }
            DatabaseSchemaUpgrade(File(root, "install"), db, wrapped, { open(it) },
                { driver, target -> exportEncryptedDatabase(driver, target.path, key) }).upgrade()
            val portableKey = DatabaseKey.generate()
            val backup = File(root, "portable.db")
            val snapshot = open(db).use { driver ->
                assertDomains(driver)
                assertDrafts(driver, original)
                MigrationContentSnapshot.capture(driver).also { exportEncryptedDatabase(driver, backup.path, portableKey) }
            }
            open(backup, portableKey).use { driver ->
                DatabaseSchemaInventory.validate(driver)
                snapshot.requirePreserved(driver)
                assertDomains(driver)
                assertDrafts(driver, original)
            }
            assertFails { open(backup).use { DatabaseSchemaInventory.validate(it) } }
            assertEquals("original-wrapped-key", wrapped.readText())
            listOf(db, backup).forEach { assertFalse(it.readBytes().toString(Charsets.UTF_8).contains("historicalcanary")) }
            DatabaseSchemaUpgrade(File(root, "install"), db, wrapped, { open(it) },
                { _, _ -> error("Must not migrate twice") }).upgrade()
        } finally { root.deleteRecursively() }
    }

    private fun assertDrafts(driver: SqlDriver, original: List<Map<String, ReminderDraftMigration.Cell>>) {
        val archived = ReminderDraftMigration.read(driver, "reminder_ai_draft_legacy", ReminderDraftMigration.legacyColumns)
        val active = ReminderDraftMigration.read(driver, "reminder_ai_draft", commonDraftFields)
        val archivedIds = archived.map { it.getValue("id") }.toSet()
        assertEquals(original.filter { it.getValue("id") in archivedIds }, archived)
        val allPayloads = (active + archived.map { it.filterKeys { field -> field in commonDraftFields } })
            .sortedBy { it.getValue("id").long() }
        assertEquals(original.map { it.filterKeys { field -> field in commonDraftFields } }, allPayloads)
        assertEquals(96, allPayloads.size)
        assertEquals(listOf("1"), sqlStrings(driver, "SELECT COUNT(*) FROM reminder"))
        assertEquals(listOf("0"), sqlStrings(driver, "SELECT COUNT(*) FROM async_task WHERE type='reminder_schedule'"))
    }

    private fun assertDomains(driver: SqlDriver) {
        DatabaseSchemaInventory.validate(driver)
        assertEquals(listOf("historicalcanary\u0000diary tail"), sqlStrings(driver, "SELECT content FROM diary WHERE id=1"))
        assertEquals(listOf("1"), sqlStrings(driver, "SELECT rowid FROM diary_fts WHERE diary_fts MATCH 'historicalcanary'"))
        assertEquals(listOf("audio\u0000tail"), sqlStrings(driver, "SELECT transcript FROM diary_attachment WHERE id=7"))
        assertEquals(listOf("comment\u0000tail"), sqlStrings(driver, "SELECT comment FROM article WHERE id=3"))
        assertEquals(listOf("{\"unknown\":true}"), sqlStrings(driver, "SELECT payload_json FROM reminder_policy_snapshot"))
        assertEquals(listOf("chat\u0000tail"), sqlStrings(driver, "SELECT content FROM chat_conversation WHERE id=11"))
        assertEquals(listOf("3"), sqlStrings(driver, "SELECT created_at FROM assistant_session WHERE id='missing-session'"))
        assertEquals(listOf("memory\u0000tail"), sqlStrings(driver, "SELECT content FROM memory_entry"))
        assertEquals(listOf("future\u0000tail"), sqlStrings(driver, "SELECT payload_json FROM idea_topic_event WHERE id='event'"))
        assertEquals(listOf("100"), sqlStrings(driver, "SELECT seq FROM sqlite_sequence WHERE name='article'"))
        assertEquals(listOf("9"), sqlStrings(driver, "PRAGMA user_version"))
    }

    private fun createHistorical(driver: SqlDriver, populated: Boolean) {
        val ddl = checkNotNull(javaClass.getResourceAsStream("/schema-v38.sql")).bufferedReader().use { it.readText() }
        ddl.split("\n-- statement\n").drop(1).forEach { driver.execute(null, it, 0) }
        settings(driver).upsert(SettingKeys.schemaVersion, "38")
        driver.execute(null, "PRAGMA user_version=9", 0)
        if (populated) { seedDomains(driver); seedReaddedTagState(driver); seedDraftCombinations(driver) }
    }

    private fun seedReaddedTagState(driver: SqlDriver) {
        val content = sqlStrings(driver, "SELECT content FROM diary WHERE id=1").single()
        val previous = com.dailysatori.service.diary.DiaryTagState(suppressed = listOf("旧标签"),
            contentFingerprint = com.dailysatori.service.diary.diaryTagFingerprint(content))
        val readded = com.dailysatori.service.diary.DiaryTagDraft.from(null, previous, content,
            com.dailysatori.service.diary.DiaryTagVocabulary()).add("旧标签")
        // The actual v38 saveDraft() unioned the previously persisted suppression back into the edited draft.
        val saved = previous.copy(automatic = readded.automatic,
            suppressed = (previous.suppressed + readded.suppressed + readded.removed).distinct())
        assertEquals(listOf("旧标签"), readded.tags)
        assertEquals(listOf("旧标签"), saved.suppressed)
        settings(driver).upsert("diary_tag_state_v1_1", kotlinx.serialization.json.Json.encodeToString(saved))
    }

    private fun seedDomains(driver: SqlDriver) {
        fun insert(sql: String) = driver.execute(null, sql, 0)
        insert("INSERT INTO diary(id,content,tags,images,created_at,updated_at) VALUES(1,'historicalcanary', '旧标签','missing.png',1,2)")
        driver.execute(null, "UPDATE diary SET content=? WHERE id=1", 1) { bindString(0, "historicalcanary\u0000diary tail") }
        insert("INSERT INTO diary_attachment(id,diary_id,kind,local_path,transcript,created_at,updated_at) VALUES(7,1,'audio','audio/kept.m4a','audio',3,4)")
        driver.execute(null, "UPDATE diary_attachment SET transcript=? WHERE id=7", 1) { bindString(0, "audio\u0000tail") }
        insert("INSERT INTO article(id,title,comment,created_at,updated_at) VALUES(3,'historicalcanary','comment',1,2)")
        driver.execute(null, "UPDATE article SET comment=? WHERE id=3", 1) { bindString(0, "comment\u0000tail") }
        insert("INSERT INTO article(id,created_at,updated_at) VALUES(100,1,2)"); insert("DELETE FROM article WHERE id=100")
        insert("""INSERT INTO reminder(id,content,status,start_date,end_date,first_reminder_time,active_day_rule,time_zone_id,profile_json,created_at,updated_at)
            VALUES('kept-reminder','kept','ACTIVE','2026-01-01','2027-01-01','10:00','daily','UTC','{"unknown":true}',1,2)""")
        insert("INSERT INTO chat_conversation(id,session_id,role,content,created_at) VALUES(11,'missing-session','user','chat',3)")
        driver.execute(null, "UPDATE chat_conversation SET content=? WHERE id=11", 1) { bindString(0, "chat\u0000tail") }
        insert("INSERT INTO memory_entry(type,title,content,created_at,updated_at) VALUES('content','memory','memory',1,2)")
        driver.execute(null, "UPDATE memory_entry SET content=?", 1) { bindString(0, "memory\u0000tail") }
        insert("INSERT INTO idea_topic(id,title,status,created_at,updated_at) VALUES('topic','original topic','advancing',1,2)")
        insert("""INSERT INTO idea_topic_source VALUES('source','topic','deleted-origin','diary','1','{"key":{"type":"diary","recordId":"1"},"originalTitle":"retained","originalContent":"original body","unknown":true}',1,2,3)""")
        driver.execute(null, "INSERT INTO idea_topic_event VALUES('event','topic','deleted-origin','future',?,4)", 1) { bindString(0, "future\u0000tail") }
    }

    private fun seedDraftCombinations(driver: SqlDriver) {
        driver.execute(null, """INSERT INTO reminder_ai_batch(id,original_input,normalized_key,time_zone_id,local_date,status,created_at,updated_at)
            VALUES('batch','original input','original input','UTC','2026-01-01','READY_FOR_CONFIRMATION',1,2)""", 0)
        var index = 0L
        for (confirmed in 0L..1L) for (discarded in 0L..1L)
            for (state in listOf("PENDING", "CONFIRMED", "SCHEDULING_FAILED", "FUTURE"))
                for (id in listOf(null, "kept-reminder", "deleted-reminder")) for (at in listOf(null, 99L)) {
                    driver.execute(null, """INSERT INTO reminder_ai_draft(batch_id,source_index,source_text,draft_json,override_json,
                        confirmed,discarded,confirmation_state,reminder_id,confirmed_at,created_at,updated_at)
                        VALUES('batch',?,?,?, ?,?,?,?,?,?,1,2)""", 9) {
                        bindLong(0, index++); bindString(1, "source\u0000tail"); bindString(2, "opaque draft\u0000tail")
                        bindString(3, "user override\u0000tail"); bindLong(4, confirmed); bindLong(5, discarded)
                        bindString(6, state); bindString(7, id); bindLong(8, at)
                    }
                }
    }

    private fun fixture(populated: Boolean = true, block: (File, File, File) -> Unit) {
        val root = createTempDirectory().toFile()
        try {
            val db = File(root, "source.db")
            jdbc(db).use { createHistorical(it, populated) }
            block(root, db, File(root, "key.sec").apply { writeText("original-wrapped-key") })
        } finally { root.deleteRecursively() }
    }

    private fun upgrade(root: File, db: File, key: File, migrate: (SqlDriver) -> Unit = ::migrate,
        export: (SqlDriver, File) -> Unit = { driver, target -> driver.execute(null, "VACUUM INTO '${target.path.replace("'", "''")}'", 0) },
    ) = DatabaseSchemaUpgrade(File(root, "install"), db, key, ::jdbc, export, migrate, checkDatabase = {
        // JDBC has no cipher_integrity_check pragma. The encrypted test above uses production validate().
        assertEquals(listOf("ok"), sqlStrings(it, "PRAGMA integrity_check"))
        assertTrue(sqlStrings(it, "PRAGMA foreign_key_check").isEmpty())
    })

    private fun jdbc(file: File): SqlDriver = JdbcSqliteDriver("jdbc:sqlite:${file.path}").also { it.execute(null, "PRAGMA foreign_keys=ON", 0) }
    private fun settings(driver: SqlDriver) = SettingRepository(DailySatoriDatabase(driver))
    private fun migrate(driver: SqlDriver) = DatabaseMigration(driver, settings(driver)).runMigrations()
    private val commonDraftFields = listOf("id", "batch_id", "source_index", "source_text", "draft_json", "override_json", "selected", "confirmed_at", "created_at", "updated_at")
}

package com.dailysatori.service.security

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.config.DatabaseConfig
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.ReminderAiBatchRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.backup.exportEncryptedDatabase
import com.dailysatori.service.migration.*
import com.dailysatori.shared.db.DailySatoriDatabase
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.*
import org.junit.Assume.assumeTrue

/** Uses the actual pre-refactor DDL, not current generated tables relabelled as version 38. */
class HistoricalSchemaUpgradeTest {
    @Test fun authenticVersion35RunsTheOlderMigrationChainWithoutLosingDomains() {
        for (missing in listOf(false, true)) fixture(historicalVersion = 35) { root, db, key ->
            val drafts = jdbc(db).use { driver ->
                if (missing) removeSecondaryObjects(driver)
                ReminderDraftMigration.read(driver, "reminder_ai_draft", ReminderDraftMigration.legacyColumns)
            }
            upgrade(root, db, key).upgrade()
            jdbc(db).use { assertDomains(it); assertDrafts(it, drafts) }
            assertEquals("original-wrapped-key", key.readText())
        }
    }

    @Test fun eachVersionSecondaryObjectRecoversIndividuallyAndMaintainsRuntimeSearch() {
        var cases = 0
        for (version in 38L..DatabaseConfig.currentSchemaVersion) fixture { _, db, _ ->
            jdbc(db).use { driver ->
                advanceHistoricalTo(driver, version)
                cases += verifyIndividualObjects(driver, version)
            }
        }
        println("Individual secondary-object recovery and live SQL mutations validated: $cases cases")
    }

    @Test fun partialMissingViewsAndTriggersPreserveCustomObjectsAndUnknownColumns() {
        for (version in listOf(41L, DatabaseConfig.currentSchemaVersion)) {
            val missing = listOf(listOf("VIEW diary_active_tags"), listOf("TRIGGER diary_tag_fts_update"),
                listOf("INDEX idx_article_source_created"), listOf("VIEW diary_active_tags", "TRIGGER diary_tag_fts_update"),
                listOf("INDEX idx_article_source_created", "TRIGGER article_fts_insert"))
            for (objects in missing) fixture { root, db, key ->
                jdbc(db).use { driver ->
                    advanceHistoricalTo(driver, version)
                    driver.execute(null, "ALTER TABLE article ADD COLUMN custom_note TEXT", 0)
                    driver.execute(null, "UPDATE article SET custom_note='unknown-column-kept' WHERE id=3", 0)
                    driver.execute(null, "CREATE INDEX custom_article_index ON article(custom_note)", 0)
                    driver.execute(null, "CREATE VIEW custom_article_view AS SELECT id,custom_note FROM article", 0)
                    driver.execute(null, "CREATE TRIGGER custom_article_trigger AFTER UPDATE OF comment ON article BEGIN SELECT 1; END", 0)
                    objects.forEach { driver.execute(null, "DROP $it", 0) }
                }
                upgrade(root, db, key).upgrade()
                jdbc(db).use { driver ->
                    assertDomains(driver)
                    assertEquals(listOf("unknown-column-kept"), sqlStrings(driver, "SELECT custom_note FROM custom_article_view WHERE id=3"))
                    assertEquals(listOf("3"), sqlStrings(driver, "SELECT COUNT(*) FROM sqlite_master WHERE name IN ('custom_article_index','custom_article_view','custom_article_trigger')"))
                }
                assertEquals("original-wrapped-key", key.readText())
            }
        }
    }

    @Test fun unrecoverableColumnsRelationsJsonAndFtsTablesPreserveOriginalAndKey() {
        for (damage in listOf("column", "relation", "json", "fts")) fixture { root, db, key ->
            jdbc(db).use { driver ->
                when (damage) {
                    "column" -> driver.execute(null, "ALTER TABLE reminder DROP COLUMN notes", 0)
                    "relation" -> {
                        driver.execute(null, "PRAGMA foreign_keys=OFF", 0)
                        driver.execute(null, "INSERT INTO reminder_event(reminder_id,event_type,actual_at) VALUES('missing','dismissed',1)", 0)
                        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
                    }
                    "json" -> settings(driver).upsert(DiaryTagMigration.vocabularyKey, "{invalid")
                    else -> driver.execute(null, "DROP TABLE article_fts", 0)
                }
            }
            val original = db.readBytes()
            assertFails(damage) { upgrade(root, db, key).upgrade() }
            assertContentEquals(original, db.readBytes(), damage)
            assertEquals("original-wrapped-key", key.readText())
            assertFalse(File(db.path + ".schema-candidate").exists())
        }
    }

    @Test fun interruptedRepairAtDdlAndSearchBoundariesPreservesSourceAndCanRetry() {
        for (phase in listOf("ddl_1", "ddl_20", "article_deleted", "diary_filled")) fixture { root, db, key ->
            jdbc(db).use { advanceHistoricalTo(it, DatabaseConfig.currentSchemaVersion); removeSecondaryObjects(it) }
            val original = db.readBytes()
            var interrupted = false
            var ddl = 0
            assertFailsWith<RepairInterrupted>(phase) {
                upgrade(root, db, key, migrate = { driver ->
                    val faulty = object : SqlDriver by driver {
                        override fun execute(identifier: Int?, sql: String, parameters: Int,
                            binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<Long> {
                            val result = driver.execute(identifier, sql, parameters, binders)
                            if (sql.startsWith("CREATE ")) ddl++
                            val stop = when (phase) {
                                "ddl_1" -> ddl == 1
                                "ddl_20" -> ddl == 20
                                "article_deleted" -> sql == "DELETE FROM article_fts"
                                else -> sql.startsWith("INSERT INTO diary_fts(")
                            }
                            if (stop) { interrupted = true; throw RepairInterrupted() }
                            return result
                        }
                    }
                    migrate(faulty)
                }).upgrade()
            }
            assertTrue(interrupted, phase)
            assertContentEquals(original, db.readBytes(), phase)
            assertEquals("original-wrapped-key", key.readText())
            assertFalse(File(db.path + ".schema-candidate").exists())
            upgrade(root, db, key).upgrade()
            jdbc(db).use(::assertDomains)
        }
    }

    @Test fun interruptedRepairedInstallationRollsBackThenSafelyRetriesOnNextStartup() = fixture { root, db, key ->
        jdbc(db).use { advanceHistoricalTo(it, DatabaseConfig.currentSchemaVersion); removeSecondaryObjects(it) }
        val original = db.readBytes()
        val install = File(root, "install")
        val interrupted = DatabaseSchemaUpgrade(install, db, key, ::jdbc,
            { source, target -> source.execute(null, "VACUUM INTO '${target.path.replace("'", "''")}'", 0) },
            checkDatabase = { driver ->
                assertEquals(listOf("ok"), sqlStrings(driver, "PRAGMA integrity_check"))
                if (File(install, "transaction.json").exists()) throw RepairInterrupted()
            })
        assertFailsWith<RepairInterrupted> { interrupted.upgrade() }
        assertTrue(File(install, "transaction.json").isFile)
        assertContentEquals(original, File(install, "rollback/database").readBytes())
        assertEquals("original-wrapped-key", File(install, "rollback/key").readText())
        upgrade(root, db, key).upgrade()
        jdbc(db).use(::assertDomains)
        assertEquals("original-wrapped-key", key.readText())
        assertFalse(install.exists())
        val installed = db.readBytes()
        upgrade(root, db, key, export = { _, _ -> error("Already recovered") }).upgrade()
        assertContentEquals(installed, db.readBytes())
    }

    @Test fun legacyMissingSecondaryObjectsAreRebuiltOnVerifiedCandidate() = fixture { root, db, key ->
        jdbc(db).use { driver ->
            // Old migrations caught secondary DDL failures and still recorded version 38.
            removeSecondaryObjects(driver)
            assertFailsWith<IllegalStateException> { DatabaseSchemaInventory.validate(driver, 38) }
        }
        upgrade(root, db, key).upgrade()
        jdbc(db).use(::assertDomains)
        assertEquals("original-wrapped-key", key.readText())
        val installed = db.readBytes()
        upgrade(root, db, key).upgrade()
        assertContentEquals(installed, db.readBytes())
    }

    @Test fun everyVersion38ThroughCurrentRepairsMissingProjectionsAndKeepsNormalizedTags() {
        for (version in 38L..DatabaseConfig.currentSchemaVersion) fixture { root, db, key ->
            jdbc(db).use { driver ->
                advanceHistoricalTo(driver, version)
                removeSecondaryObjects(driver)
                if (version in 41L..44L) driver.execute(null, "UPDATE diary SET tags='stalelegacytag'", 0)
                driver.execute(null, "DELETE FROM article_fts", 0)
                driver.execute(null, "DELETE FROM diary_fts", 0)
            }
            upgrade(root, db, key).upgrade()
            jdbc(db).use { driver ->
                assertDomains(driver)
                assertEquals(listOf("1"), sqlStrings(driver, "SELECT rowid FROM diary_fts WHERE diary_fts MATCH '旧标签'"))
                assertTrue(sqlStrings(driver, "SELECT rowid FROM diary_fts WHERE diary_fts MATCH 'stalelegacytag'").isEmpty())
            }
            val installed = db.readBytes()
            upgrade(root, db, key, export = { _, _ -> error("Repaired database must not be copied again") }).upgrade()
            assertContentEquals(installed, db.readBytes(), "version=$version")
            assertEquals("original-wrapped-key", key.readText())
        }
    }

    @Test fun missingBusinessTableAndConflictingUniqueIndexCannotBeRepairedOrInstalled() {
        for (damage in listOf("table", "unique")) fixture { root, db, key ->
            jdbc(db).use { driver ->
                if (damage == "table") driver.execute(null, "DROP TABLE assistant_reference", 0)
                else {
                    driver.execute(null, "DROP INDEX idx_reminder_ai_batch_active_key", 0)
                    for (id in listOf("duplicate-a", "duplicate-b")) driver.execute(null, """INSERT INTO reminder_ai_batch
                        (id,original_input,normalized_key,time_zone_id,local_date,status,created_at,updated_at)
                        VALUES('$id','kept input','duplicate-key','UTC','2026-01-01','RUNNING',1,2)""", 0)
                }
            }
            val original = db.readBytes()
            assertFails { upgrade(root, db, key).upgrade() }
            assertContentEquals(original, db.readBytes(), damage)
            assertEquals("original-wrapped-key", key.readText())
            assertFalse(File(db.path + ".schema-candidate").exists())
        }
    }

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

    @Test fun realEncryptedFrozenVersion38SurvivesUpgradeRekeyedSnapshotAndReopen() = encryptedRoundTrip(false)

    @Test fun realEncryptedMissingSecondaryObjectsAreRebuiltBeforeVerifiedInstallation() = encryptedRoundTrip(true)

    @Test fun realEncryptedCurrentDatabaseRepairsOnlyMissingReferencedView() = encryptedRoundTrip(false, currentView = true)

    private fun encryptedRoundTrip(missingSecondary: Boolean, currentView: Boolean = false) {
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
                val drafts = ReminderDraftMigration.read(driver, "reminder_ai_draft", ReminderDraftMigration.legacyColumns)
                if (currentView) {
                    advanceHistoricalTo(driver, DatabaseConfig.currentSchemaVersion)
                    driver.execute(null, "DROP VIEW diary_active_tags", 0)
                }
                if (missingSecondary) {
                    removeSecondaryObjects(driver)
                    driver.execute(null, "DELETE FROM article_fts", 0)
                }
                drafts
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
        for ((table, id) in listOf("article_fts" to "3", "book_fts" to "1", "book_viewpoint_fts" to "1",
            "memory_entry_fts" to "1")) {
            assertEquals(listOf(id), sqlStrings(driver, "SELECT rowid FROM $table WHERE $table MATCH 'historicalcanary'"), table)
        }
    }

    private fun createHistorical(driver: SqlDriver, populated: Boolean, version: Long = 38) {
        val ddl = checkNotNull(javaClass.getResourceAsStream("/schema-v$version.sql")).bufferedReader().use { it.readText() }
        ddl.split("\n-- statement\n").drop(1).forEach { driver.execute(null, it, 0) }
        settings(driver).upsert(SettingKeys.schemaVersion, version.toString())
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
        insert("INSERT INTO book(id,title,author,category,cover_image,introduction,created_at,updated_at) VALUES(1,'historicalcanary','author','kept category','kept cover','kept introduction',1,2)")
        insert("INSERT INTO book_viewpoint(id,book_id,title,content,example,created_at,updated_at) VALUES(1,1,'historicalcanary','kept content','kept example',1,2)")
        driver.execute(null, "UPDATE article SET comment=? WHERE id=3", 1) { bindString(0, "comment\u0000tail") }
        insert("INSERT INTO article(id,created_at,updated_at) VALUES(100,1,2)"); insert("DELETE FROM article WHERE id=100")
        insert("""INSERT INTO reminder(id,content,status,start_date,end_date,first_reminder_time,active_day_rule,time_zone_id,profile_json,created_at,updated_at)
            VALUES('kept-reminder','kept','ACTIVE','2026-01-01','2027-01-01','10:00','daily','UTC','{"unknown":true}',1,2)""")
        insert("INSERT INTO chat_conversation(id,session_id,role,content,created_at) VALUES(11,'missing-session','user','chat',3)")
        driver.execute(null, "UPDATE chat_conversation SET content=? WHERE id=11", 1) { bindString(0, "chat\u0000tail") }
        insert("INSERT INTO memory_entry(type,title,content,created_at,updated_at) VALUES('content','historicalcanary','memory',1,2)")
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

    private fun secondaryObjects(driver: SqlDriver) = sqlStrings(driver,
        "SELECT type||' '||name FROM sqlite_master WHERE type IN ('index','trigger','view') AND sql IS NOT NULL ORDER BY type,name")

    private fun removeSecondaryObjects(driver: SqlDriver) = secondaryObjects(driver).forEach { driver.execute(null, "DROP $it", 0) }

    private fun verifyIndividualObjects(driver: SqlDriver, version: Long): Int {
        val before = MigrationContentSnapshot.capture(driver)
        val objects = secondaryObjects(driver)
        assertTrue(objects.isNotEmpty())
        var tested = 0
        for (obj in objects) DailySatoriDatabase(driver).dailySatoriQueries.transaction {
            driver.execute(null, "DROP $obj", 0)
            val missing = DatabaseSchemaInventory.missingObjects(driver, version)
            // Obsolete historical objects are not part of the versioned repair contract.
            if (missing.isEmpty()) rollback()
            assertEquals(setOf(obj.replaceFirst(' ', ':')), missing.keys)
            DatabaseSchemaRepair.repair(driver, version)
            assertTrue(DatabaseSchemaInventory.missingObjects(driver, version).isEmpty())
            before.requirePreserved(driver)
            assertSearchMutations(driver)
            tested++
            // SQLDelight pins the JDBC connection and rolls back both DDL and probe mutations.
            rollback()
        }
        before.requirePreserved(driver)
        assertTrue(tested > 0)
        return tested
    }

    private fun assertSearchMutations(driver: SqlDriver) {
        for ((table, id) in listOf("article" to 3L, "book" to 1L, "book_viewpoint" to 1L, "memory_entry" to 1L)) {
            val fts = "${table}_fts"
            driver.execute(null, "UPDATE $table SET title='metadataprobe' WHERE id=$id", 0)
            assertEquals(listOf(id.toString()), sqlStrings(driver, "SELECT rowid FROM $fts WHERE $fts MATCH 'metadataprobe'"), table)
            val columns = sqlStrings(driver, "SELECT name FROM pragma_table_info('$table')")
            val copy = columns.joinToString { if (it == "id") "id+1000" else "\"$it\"" }
            driver.execute(null, "INSERT INTO $table SELECT $copy FROM $table WHERE id=$id", 0)
            assertEquals(listOf(id.toString(), (id+1000).toString()), sqlStrings(driver,
                "SELECT rowid FROM $fts WHERE $fts MATCH 'metadataprobe' ORDER BY rowid"), table)
            driver.execute(null, "DELETE FROM $table WHERE id=${id+1000}", 0)
            assertEquals(listOf(id.toString()), sqlStrings(driver, "SELECT rowid FROM $fts WHERE $fts MATCH 'metadataprobe'"), table)
        }
        val revision = sqlStrings(driver, "SELECT revision FROM diary_thread_revision WHERE root_diary_id=1").single().toLong()
        driver.execute(null, "UPDATE diary SET content='newdiaryprobe' WHERE id=1", 0)
        assertEquals(listOf("1"), sqlStrings(driver, "SELECT rowid FROM diary_fts WHERE diary_fts MATCH 'newdiaryprobe'"))
        assertEquals(listOf((revision+1).toString()), sqlStrings(driver, "SELECT revision FROM diary_thread_revision WHERE root_diary_id=1"))
    }

    private class RepairInterrupted : Error("simulated process interruption")

    private fun advanceHistoricalTo(driver: SqlDriver, version: Long) {
        if (version >= 40) {
            driver.execute(null, "ALTER TABLE diary_attachment ADD COLUMN position INTEGER NOT NULL DEFAULT 0 CHECK(position >= 0)", 0)
            driver.execute(null, "ALTER TABLE diary_attachment ADD COLUMN remote_url TEXT", 0)
            DiaryImageMigration.migrate(driver)
        }
        if (version >= 41) DiaryTagMigration.migrate(driver)
        if (version >= 42) { ReminderPolicyMigration.migrate(driver); ReminderDraftMigration.migrate(driver) }
        if (version >= 43) IdeaSnapshotMigration.migrate(driver)
        if (version >= 44) AssistantRelationsMigration.migrate(driver)
        if (version >= 45) SchemaRetirementMigration.migrate(driver)
        if (version >= 46) ReminderDraftLegacyArchive.create(driver)
        settings(driver).upsert(SettingKeys.schemaVersion, version.toString())
        DatabaseSchemaInventory.validate(driver, version)
    }

    private fun fixture(populated: Boolean = true, historicalVersion: Long = 38, block: (File, File, File) -> Unit) {
        val root = createTempDirectory().toFile()
        try {
            val db = File(root, "source.db")
            jdbc(db).use { createHistorical(it, populated, historicalVersion) }
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

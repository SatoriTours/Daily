package com.dailysatori.service.security

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.config.DatabaseConfig
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.backup.exportEncryptedDatabase
import com.dailysatori.service.migration.DatabaseMigration
import com.dailysatori.shared.db.DailySatoriDatabase
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.*
import org.junit.Assume.assumeTrue

class DatabaseSchemaUpgradeTest {
    @Test fun version38IsUpgradedOnCopyAndOriginalInputsAndWrappedKeySurvive() = fixture { root, db, key ->
        val envelope = key.readBytes()
        upgrade(root, db, key).upgrade()
        jdbc(db).use { driver ->
            assertEquals(DatabaseConfig.currentSchemaVersion.toString(), settings(driver).get(SettingKeys.schemaVersion))
            assertOriginal(driver)
        }
        assertContentEquals(envelope, key.readBytes())
        assertFalse(File(db.path + ".schema-candidate").exists())
        upgrade(root, db, key, export = { _, _ -> error("must not export current schema") }).upgrade()
        jdbc(db).use(::assertOriginal)
    }

    @Test fun migratedLegacyImageOccurrencesAreVerifiedBeforeInstalling() = fixture { root, db, key ->
        for(change in listOf("path","missing","extra")) {
            val before=db.readBytes()
            assertFails {
                upgrade(root,db,key,migrate={ driver ->
                    migrate(driver)
                    when(change) {
                        "path" -> driver.execute(null,"UPDATE diary_attachment SET local_path='wrong.png' WHERE kind='image'",0)
                        "missing" -> driver.execute(null,"DELETE FROM diary_attachment WHERE kind='image'",0)
                        else -> driver.execute(null,"INSERT INTO diary_attachment(diary_id,kind,local_path,position,created_at,updated_at) VALUES(1,'image','extra.png',1,1,2)",0)
                    }
                }).upgrade()
            }
            assertContentEquals(before,db.readBytes())
        }
    }

    @Test fun failedMigrationNeverTouchesActiveDatabaseOrWrappedKey() = fixture { root, db, key ->
        val before = db.readBytes()
        assertFails {
            upgrade(root, db, key, migrate = { driver ->
                driver.execute(null, "UPDATE diary SET content='wrong text'", 0)
                error("injected migration failure")
            }).upgrade()
        }
        assertContentEquals(before, db.readBytes())
        assertEquals("wrapped-original-key", key.readText())
        assertFalse(File(db.path + ".schema-candidate").exists())
    }

    @Test fun migrationThatSilentlyChangesOriginalInputCannotBeInstalled() = fixture { root, db, key ->
        val before = db.readBytes()
        assertFails {
            upgrade(root, db, key, migrate = { driver ->
                migrate(driver)
                driver.execute(null, "UPDATE diary SET content=content || 'unexpected suffix'", 0)
            }).upgrade()
        }
        assertContentEquals(before, db.readBytes())
        jdbc(db).use { assertEquals("38", settings(it).get(SettingKeys.schemaVersion)); assertOriginal(it) }
    }

    @Test fun insufficientSpaceStopsBeforeExportAndKeepsOriginal() = fixture { root, db, key ->
        val before = db.readBytes()
        var exports = 0
        assertFails {
            upgrade(root, db, key, export = { _, _ -> exports++ }, availableBytes = { 0 }).upgrade()
        }
        assertEquals(0, exports)
        assertContentEquals(before, db.readBytes())
    }

    @Test fun missingOriginalDoesNotEraseRecoverableCandidateOrKey() = fixture { root, db, key ->
        assertTrue(db.delete())
        val candidate = File(db.path + ".schema-candidate").apply { writeText("recoverable") }
        assertFails { upgrade(root, db, key).upgrade() }
        assertEquals("recoverable", candidate.readText())
        assertEquals("wrapped-original-key", key.readText())
        assertFalse(db.exists())
    }

    @Test fun incompleteUnversionedDatabaseCannotBePromotedToLatest() = fixture { root, db, key ->
        jdbc(db).use {
            settings(it).delete(SettingKeys.schemaVersion)
            it.execute(null, "DROP TABLE assistant_action", 0)
        }
        val before = db.readBytes()
        assertFails { upgrade(root, db, key).upgrade() }
        assertContentEquals(before, db.readBytes())
    }

    @Test fun interveningWriteToOriginalIsNotOverwrittenByOlderCandidate() = fixture { root, db, key ->
        assertFails {
            upgrade(root, db, key, export = { source, target ->
                source.execute(null, "VACUUM INTO '${target.path.replace("'", "''")}'", 0)
                source.execute(null, "UPDATE diary SET content='new live input'", 0)
            }).upgrade()
        }
        jdbc(db).use { driver ->
            assertEquals("new live input", com.dailysatori.service.migration.DiaryLegacyFieldsFixture.readDiary(driver,1).content)
            assertEquals("38", settings(driver).get(SettingKeys.schemaVersion))
        }
    }

    @Test fun originalTextAfterEmbeddedNullAndNullVersusEmptyRemainProtected() {
        for (change in listOf("nul", "empty")) fixture { root, db, key ->
            val original = "prefix\u0000original-suffix"
            jdbc(db).use { driver ->
                driver.execute(null, "UPDATE diary SET content=?,mood=NULL", 1) { bindString(0, original) }
            }
            assertFails {
                upgrade(root, db, key, migrate = { driver ->
                    migrate(driver)
                    if (change == "nul") driver.execute(null, "UPDATE diary SET content=?", 1) {
                        bindString(0, "prefix\u0000changed-suffix")
                    } else driver.execute(null, "UPDATE diary SET mood=''", 0)
                }).upgrade()
            }
            jdbc(db).use { driver ->
                val row = com.dailysatori.service.migration.DiaryLegacyFieldsFixture.readDiary(driver,1)
                assertEquals(original, row.content)
                assertNull(row.mood)
                assertEquals("38", settings(driver).get(SettingKeys.schemaVersion))
            }
        }
    }

    @Test fun hostSqlCipherCursorDistinguishesNullFromEmptyString() {
        val binary=System.getenv("SQLCIPHER_BIN")
        assumeTrue("Requires real host SQLCipher",binary!=null && File(binary).canExecute())
        val root=createTempDirectory().toFile()
        try {
            HostSqlCipherDriver(binary!!,File(root,"nulls.db")).use { driver ->
                driver.executeQuery(null,"SELECT NULL,''", { c ->
                    c.next()
                    assertNull(c.getString(0))
                    assertEquals("",c.getString(1))
                    app.cash.sqldelight.db.QueryResult.Value(Unit)
                },0)
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun realSqlCipherUpgradeUsesSameKeyAndNeverWritesAPlaintextCandidate() {
        val binary = System.getenv("SQLCIPHER_BIN")
        assumeTrue("Requires real host SQLCipher", binary != null && File(binary).canExecute())
        val root = createTempDirectory().toFile()
        try {
            val db = File(root, "encrypted.db")
            val keyFile = File(root, "key.sec").apply { writeText("same-wrapped-key") }
            val key = DatabaseKey.generate()
            val open: (File) -> SqlDriver = { file ->
                HostSqlCipherDriver(binary!!, file).also {
                    it.run("PRAGMA key = \"${key.sqlCipherPassword().decodeToString()}\"; PRAGMA foreign_keys=ON;")
                }
            }
            open(db).use { driver ->
                DailySatoriDatabase.Schema.create(driver)
                com.dailysatori.service.migration.AssistantLegacyFixture.seed(driver)
                seed(driver)
                driver.execute(null,"INSERT INTO diary_attachment(id,diary_id,kind,local_path,transcript,created_at,updated_at) VALUES(7,1,'audio','diary/audio/kept.m4a','原始转写',3,4)",0)
                com.dailysatori.service.migration.ReminderLegacyFixture.seed(driver)
                com.dailysatori.service.migration.IdeaLegacyFixture.seed(driver)
                // A physical pre-40 attachment table, not only a historical version marker.
                driver.execute(null,"DROP INDEX idx_diary_attachment_position",0)
                driver.execute(null,"ALTER TABLE diary_attachment DROP COLUMN position",0)
                driver.execute(null,"ALTER TABLE diary_attachment DROP COLUMN remote_url",0)
                driver.execute(null, """INSERT INTO chat_conversation(id,session_id,role,content,created_at)
                    VALUES(100,'deleted-session','user','deleted input',1)""", 0)
                driver.execute(null, "DELETE FROM chat_conversation WHERE id=100", 0)
                driver.execute(null, "PRAGMA journal_mode=WAL", 0)
            }
            DatabaseSchemaUpgrade(File(root, "schema-install"), db, keyFile, open,
                export = { driver, target ->
                    exportEncryptedDatabase(driver, target.path, key)
                    assertFalse(com.dailysatori.platform.isPlaintextDatabase(target))
                }, migrate = ::migrate).upgrade()
            open(db).use { driver ->
                assertEquals(DatabaseConfig.currentSchemaVersion.toString(), settings(driver).get(SettingKeys.schemaVersion))
                assertOriginal(driver)
                validate(driver)
                val retained=DailySatoriDatabase(driver).dailySatoriQueries.selectDiaryAttachmentById(7).executeAsOne()
                assertEquals("diary/audio/kept.m4a",retained.local_path)
                assertEquals("原始转写",retained.transcript)
                com.dailysatori.service.migration.ReminderLegacyFixture.assertMigrated(driver)
                assertEquals(com.dailysatori.service.migration.IdeaLegacyFixture.raw,
                    DailySatoriDatabase(driver).dailySatoriQueries.selectIdeaTopicSourceById("legacy-source").executeAsOne().snapshot_json)
                assertEquals(listOf("unknown\u0000tail"),sqlStrings(driver,"SELECT payload_json FROM idea_topic_event WHERE id='legacy-opaque'"))
                val idea=com.dailysatori.data.repository.IdeaTopicRepository(DailySatoriDatabase(driver)).getDetailSync("legacy-idea")!!
                assertEquals(2,idea.events.size)
                assertTrue(idea.events.single { it.id=="legacy-captured" }.payload.contains("retained"))
                assertEquals(listOf("3"),sqlStrings(driver,"SELECT created_at FROM assistant_session WHERE id='missing-session'"))
                assertEquals(listOf("legacy_unknown"),sqlStrings(driver,"SELECT content_origin FROM memory_entry"))
                assertEquals("原话\u0000tail",DailySatoriDatabase(driver).dailySatoriQueries.selectChatById(71).executeAsOne().content)
                assertEquals(4L,retained.updated_at)
                assertEquals(listOf("100"), sqlStrings(driver,
                    "SELECT seq FROM sqlite_sequence WHERE name='chat_conversation'"))
            }
            assertEquals("same-wrapped-key", keyFile.readText())
            assertFalse(db.readBytes().toString(Charsets.UTF_8).contains("private-original-893771"))
        } finally { root.deleteRecursively() }
    }

    private fun upgrade(
        root: File, db: File, key: File,
        export: (SqlDriver, File) -> Unit = { source, target ->
            source.execute(null, "VACUUM INTO '${target.path.replace("'", "''")}'", 0)
        },
        migrate: (SqlDriver) -> Unit = ::migrate,
        availableBytes: () -> Long = { Long.MAX_VALUE },
    ) = DatabaseSchemaUpgrade(File(root, "schema-install"), db, key, ::jdbc, export, migrate, availableBytes,
        checkDatabase = { assertEquals(listOf("ok"), sqlStrings(it, "PRAGMA integrity_check")) })

    private fun fixture(block: (File, File, File) -> Unit) {
        val root = createTempDirectory().toFile()
        try {
            val db = File(root, "source.db")
            jdbc(db).use { DailySatoriDatabase.Schema.create(it); seed(it) }
            val key = File(root, "key.sec").apply { writeText("wrapped-original-key") }
            block(root, db, key)
        } finally { root.deleteRecursively() }
    }

    private fun seed(driver: SqlDriver) {
        com.dailysatori.service.migration.DiaryLegacyFieldsFixture.install(driver)
        settings(driver).upsert(SettingKeys.schemaVersion, "38")
        DailySatoriDatabase(driver).dailySatoriQueries.insertDiary("private-original-893771", "平静", 1, 2)
        driver.execute(null,"UPDATE diary SET tags='标签',images='diary_images/missing.png' WHERE id=1",0)
        driver.execute(null,"UPDATE diary SET updated_at=2 WHERE id=1",0)
        settings(driver).upsert("diary_tag_state_v1_1",kotlinx.serialization.json.Json.encodeToString(
            com.dailysatori.service.diary.DiaryTagState(automatic=listOf("标签"),suppressed=listOf("旧抑制"),
                contentFingerprint="legacy-fingerprint-\u0000-tail",revision=3)))
    }

    private fun assertOriginal(driver: SqlDriver) {
        val row = com.dailysatori.service.migration.DiaryLegacyFieldsFixture.readDiary(driver,1)
        assertEquals("private-original-893771", row.content)
        assertEquals("标签", row.tags)
        assertEquals("diary_images/missing.png", row.images)
        assertEquals(1, row.created_at)
        assertEquals(2, row.updated_at)
    }

    private fun jdbc(file: File): SqlDriver = JdbcSqliteDriver("jdbc:sqlite:${file.path}").also {
        it.execute(null, "PRAGMA foreign_keys=ON", 0)
    }
    private fun settings(driver: SqlDriver) = SettingRepository(DailySatoriDatabase(driver))
    private fun migrate(driver: SqlDriver) = DatabaseMigration(driver, settings(driver)).runMigrations()
}

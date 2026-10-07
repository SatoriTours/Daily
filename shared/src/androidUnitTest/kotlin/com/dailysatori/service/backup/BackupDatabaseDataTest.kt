package com.dailysatori.service.backup

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.config.DatabaseConfig
import com.dailysatori.shared.db.DailySatoriDatabase
import java.nio.file.Files
import kotlin.test.*

class BackupDatabaseDataTest {
    @Test
    fun orphanedAttachmentPreventsAcceptingAnOtherwiseReadableDatabase() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            driver.execute(null, "PRAGMA foreign_keys = OFF", 0)
            driver.execute(null, "INSERT INTO diary_attachment(diary_id,kind,local_path,created_at,updated_at) VALUES(99,'audio','diary/audio/voice.m4a',1,1)", 0)

            val error = assertFailsWith<IllegalStateException> { BackupDatabaseData(driver).userFiles("/app") }
            assertTrue(error.message!!.contains("关联完整性"))
        } finally { driver.close() }
    }

    @Test
    fun historicalDatabaseIsMigratedBeforeSecretFieldsArePrepared() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            driver.execute(null, "DROP TABLE sms_reminder_source", 0)
            driver.execute(null, "DROP TABLE bookkeeping_entry", 0)
            driver.execute(null, "DROP TABLE phone_message", 0)
            driver.execute(null, "INSERT INTO setting(key,value,created_at,updated_at) VALUES('schema_version','28',1,1)", 0)
            driver.execute(null, "INSERT INTO diary(id,content,created_at,updated_at) VALUES(1,'keep this diary',1,1)", 0)
            val cipher = object : com.dailysatori.service.security.SecretValueCipher {
                override fun encrypt(value: String) = "enc:$value"
                override fun decrypt(value: String) = value.removePrefix("enc:")
                override fun isEncrypted(value: String) = value.startsWith("enc:")
            }
            BackupDatabaseData(driver).prepareSecrets(cipher)
            val q = DailySatoriDatabase(driver).dailySatoriQueries
            assertEquals("keep this diary", q.selectAllDiaries().executeAsOne().content)
            assertEquals(DatabaseConfig.currentSchemaVersion.toString(), q.selectSettingByKey("schema_version").executeAsOne().value_)
            listOf("sms_reminder_source", "bookkeeping_entry", "phone_message").forEach { table ->
                driver.execute(null, "SELECT * FROM $table", 0)
            }
        } finally { driver.close() }
    }

    @Test
    fun restoreRelocatesAudioAndKeepsTheNewDevicesSelectedDirectory() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            driver.execute(null, "INSERT INTO diary(id,content,created_at,updated_at) VALUES(23,'diary',1,1)", 0)
            driver.execute(null, "INSERT INTO diary_attachment(diary_id,kind,local_path,created_at,updated_at) VALUES(23,'audio','/data/user/0/old/files/DailySatori/diary/audio/23/voice.m4a',1,1)", 0)
            driver.execute(null, "INSERT INTO setting(key,value,created_at,updated_at) VALUES('backup_directory','content://old-device',1,1)", 0)
            val data = BackupDatabaseData(driver)
            assertEquals(listOf("diary/audio/23/voice.m4a"), data.userFiles("/new/DailySatori"))
            data.prepareRestore("/new/DailySatori", "content://new-device", setOf("diary/audio/23/voice.m4a"))
            val queries = DailySatoriDatabase(driver).dailySatoriQueries
            assertEquals("/new/DailySatori/diary/audio/23/voice.m4a", queries.selectAllDiaryAttachments().executeAsOne().local_path)
            assertEquals("content://new-device", queries.selectSettingByKey("backup_directory").executeAsOne().value_)
        } finally { driver.close() }
    }

    @Test
    fun missingReferencedAudioPreventsRestore() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            driver.execute(null, "INSERT INTO diary(id,content,created_at,updated_at) VALUES(1,'diary',1,1)", 0)
            driver.execute(null, "INSERT INTO diary_attachment(diary_id,kind,local_path,created_at,updated_at) VALUES(1,'audio','diary/audio/lost.m4a',1,1)", 0)
            assertFailsWith<IllegalStateException> { BackupDatabaseData(driver).prepareRestore("/new/DailySatori", "new", emptySet()) }
        } finally { driver.close() }
    }

    @Test
    fun snapshotIncludesCommittedWalDataWithoutCopyingWalSidecars() {
        val root = Files.createTempDirectory("snapshot-test").toFile()
        val source = root.resolve("source.db")
        val driver = JdbcSqliteDriver("jdbc:sqlite:${source.path}")
        try {
            driver.execute(null, "PRAGMA journal_mode=WAL", 0)
            driver.execute(null, "PRAGMA wal_autocheckpoint=0", 0)
            driver.execute(null, "CREATE TABLE sample(value TEXT)", 0)
            driver.execute(null, "INSERT INTO sample VALUES('latest')", 0)
            val destination = root.resolve("snapshot.db")
            createSqliteBackupSnapshot(driver, source.path, destination.path) { _, _ -> error("must use a SQLite snapshot") }
            val restored = JdbcSqliteDriver("jdbc:sqlite:${destination.path}")
            try {
                val value = restored.executeQuery(null, "SELECT value FROM sample", { cursor ->
                    cursor.next(); QueryResult.Value(cursor.getString(0))
                }, 0).value
                assertEquals("latest", value)
                assertFalse(root.resolve("snapshot.db-wal").exists())
            } finally { restored.close() }
        } finally { driver.close(); root.deleteRecursively() }
    }
}

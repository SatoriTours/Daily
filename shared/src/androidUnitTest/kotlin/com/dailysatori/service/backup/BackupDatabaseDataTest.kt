package com.dailysatori.service.backup

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.config.DatabaseConfig
import com.dailysatori.shared.db.DailySatoriDatabase
import java.nio.file.Files
import kotlin.test.*

class BackupDatabaseDataTest {
    @Test
    fun attachmentInventoryPreservesEveryReferenceAndItsOwnerWithoutIncludingRemoteImages() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            com.dailysatori.service.migration.DiaryLegacyFieldsFixture.install(driver)
            driver.execute(null, "INSERT INTO diary(id,content,images,created_at,updated_at) VALUES(23,'diary','diary_images/photo.jpg,https://example.com/remote.jpg',1,1)", 0)
            driver.execute(null, "INSERT INTO diary_attachment(id,diary_id,kind,local_path,created_at,updated_at) VALUES(7,23,'audio','/old/app_flutter/diary/audio/23/voice.m4a',1,1)", 0)
            driver.execute(null, "INSERT INTO article(id,cover_image,created_at,updated_at) VALUES(31,'images/shared.jpg',1,1)", 0)
            driver.execute(null, "INSERT INTO image(id,path,article_id,created_at,updated_at) VALUES(8,'images/shared.jpg',31,1,1)", 0)
            driver.execute(null, "INSERT INTO book(id,title,author,category,cover_image,introduction,created_at,updated_at) VALUES(51,'book','','','images/book.jpg','',1,1)", 0)

            val references = BackupDatabaseData(driver).attachmentReferences("/app")

            assertEquals(5, references.size)
            assertEquals(BackupAttachmentSource.entries.toSet(), references.map { it.source }.toSet())
            val audio = references.single { it.source == BackupAttachmentSource.DIARY_ATTACHMENT }
            assertEquals(7L, audio.rowId)
            assertEquals(23L, audio.recordId)
            assertEquals("/old/app_flutter/diary/audio/23/voice.m4a", audio.originalPath)
            assertEquals("diary/audio/23/voice.m4a", audio.relativePath)
            assertEquals(31L, references.single { it.source == BackupAttachmentSource.ARTICLE_IMAGE }.recordId)
            assertEquals(2, references.count { it.relativePath == "images/shared.jpg" })
            assertEquals(4, BackupDatabaseData(driver).userFiles("/app").size)
        } finally { driver.close() }
    }

    @Test
    fun legacyNullImagePlaceholdersDoNotRequireNonexistentAttachments() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            com.dailysatori.service.migration.DiaryLegacyFieldsFixture.install(driver)
            driver.execute(null, "INSERT INTO article(id,cover_image,created_at,updated_at) VALUES(1,'null',1,1)", 0)
            driver.execute(null, "INSERT INTO image(path,article_id,created_at,updated_at) VALUES('images/null',1,1,1)", 0)
            driver.execute(null, "INSERT INTO book(title,author,category,cover_image,introduction,created_at,updated_at) VALUES('book','','','/old/app_flutter/images/null','',1,1)", 0)
            driver.execute(null, "INSERT INTO diary(id,content,images,created_at,updated_at) VALUES(1,'keep text','diary_images/null,diary_images/photo.jpg',1,1)", 0)
            val data = BackupDatabaseData(driver)

            assertEquals(listOf("diary_images/photo.jpg"), data.userFiles("/app"))
            data.prepareRestore("/app", "backup-dir")

            val queries = DailySatoriDatabase(driver).dailySatoriQueries
            assertEquals("keep text", com.dailysatori.service.migration.DiaryLegacyFieldsFixture.readDiary(driver,1).content)
            assertEquals("diary_images/photo.jpg", com.dailysatori.service.migration.DiaryLegacyFieldsFixture.readDiary(driver,1).images)
        } finally { driver.close() }
    }

    @Test
    fun legacyNullImageHandlingDoesNotHideMissingAudioOrImageFiles() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            com.dailysatori.service.migration.DiaryLegacyFieldsFixture.install(driver)
            driver.execute(null, "INSERT INTO diary(id,content,images,created_at,updated_at) VALUES(1,'diary','diary_images/null.jpg',1,1)", 0)
            driver.execute(null, "INSERT INTO diary_attachment(diary_id,kind,local_path,created_at,updated_at) VALUES(1,'audio','diary/audio/null',1,1)", 0)
            val data = BackupDatabaseData(driver)

            assertEquals(setOf("diary_images/null.jpg", "diary/audio/null"), data.userFiles("/app").toSet())
            data.prepareRestore("/app", "backup-dir")
            val queries = DailySatoriDatabase(driver).dailySatoriQueries
            assertEquals("diary_images/null.jpg", com.dailysatori.service.migration.DiaryLegacyFieldsFixture.readDiary(driver,1).images)
            assertEquals("/app/diary/audio/null", queries.selectAllDiaryAttachments().executeAsOne().local_path)
        } finally { driver.close() }
    }

    @Test
    fun orphanedAttachmentPreventsAcceptingAnOtherwiseReadableDatabase() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            com.dailysatori.service.migration.DiaryLegacyFieldsFixture.install(driver)
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
            com.dailysatori.service.migration.DiaryLegacyFieldsFixture.install(driver)
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
            com.dailysatori.service.migration.DiaryLegacyFieldsFixture.install(driver)
            driver.execute(null, "INSERT INTO diary(id,content,created_at,updated_at) VALUES(23,'diary',1,1)", 0)
            driver.execute(null, "INSERT INTO diary_attachment(diary_id,kind,local_path,created_at,updated_at) VALUES(23,'audio','/data/user/0/old/files/DailySatori/diary/audio/23/voice.m4a',1,1)", 0)
            driver.execute(null, "INSERT INTO setting(key,value,created_at,updated_at) VALUES('backup_directory','content://old-device',1,1)", 0)
            val data = BackupDatabaseData(driver)
            assertEquals(listOf("diary/audio/23/voice.m4a"), data.userFiles("/new/DailySatori"))
            data.prepareRestore("/new/DailySatori", "content://new-device")
            val queries = DailySatoriDatabase(driver).dailySatoriQueries
            assertEquals("/new/DailySatori/diary/audio/23/voice.m4a", queries.selectAllDiaryAttachments().executeAsOne().local_path)
            assertEquals("content://new-device", queries.selectSettingByKey("backup_directory").executeAsOne().value_)
        } finally { driver.close() }
    }

    @Test
    fun missingReferencedAudioDoesNotBlockRestoreOrDeleteItsReference() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            com.dailysatori.service.migration.DiaryLegacyFieldsFixture.install(driver)
            driver.execute(null, "INSERT INTO diary(id,content,created_at,updated_at) VALUES(1,'diary',1,1)", 0)
            driver.execute(null, "INSERT INTO diary_attachment(diary_id,kind,local_path,created_at,updated_at) VALUES(1,'audio','diary/audio/lost.m4a',1,1)", 0)
            BackupDatabaseData(driver).prepareRestore("/new/DailySatori", "new")
            assertEquals("/new/DailySatori/diary/audio/lost.m4a",
                DailySatoriDatabase(driver).dailySatoriQueries.selectAllDiaryAttachments().executeAsOne().local_path)
        } finally { driver.close() }
    }

    @Test
    fun restoreKeepsMissingImageReferencesAndRelocatesAudioWithoutCheckingEveryAttachment() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            com.dailysatori.service.migration.DiaryLegacyFieldsFixture.install(driver)
            driver.execute(null, "INSERT INTO diary(id,content,images,created_at,updated_at) VALUES(1,'private diary','diary_images/present.jpg,diary_images/private-photo.jpg',1,1)", 0)
            driver.execute(null, "INSERT INTO diary_attachment(diary_id,kind,local_path,created_at,updated_at) VALUES(1,'audio','/old/DailySatori/diary/audio/present.m4a',1,1)", 0)
            driver.execute(null, "INSERT INTO diary_attachment(diary_id,kind,local_path,created_at,updated_at) VALUES(1,'audio','/old/DailySatori/diary/audio/private-voice.m4a',1,1)", 0)
            driver.execute(null, "INSERT INTO article(id,title,url,cover_image,created_at,updated_at) VALUES(1,'private title','https://private.example','images/private-cover.jpg',1,1)", 0)
            driver.execute(null, "INSERT INTO image(path,article_id,created_at,updated_at) VALUES('images/private-cover.jpg',1,1,1)", 0)
            driver.execute(null, "INSERT INTO setting(key,value,created_at,updated_at) VALUES('backup_directory','content://old-device',1,1)", 0)

            BackupDatabaseData(driver).prepareRestore("/new/DailySatori", "content://new-device")
            val queries = DailySatoriDatabase(driver).dailySatoriQueries
            assertEquals(setOf("/new/DailySatori/diary/audio/present.m4a", "/new/DailySatori/diary/audio/private-voice.m4a"),
                queries.selectAllDiaryAttachments().executeAsList().map { it.local_path }.toSet())
            assertEquals("diary_images/present.jpg,diary_images/private-photo.jpg", com.dailysatori.service.migration.DiaryLegacyFieldsFixture.readDiary(driver,1).images)
            assertEquals("images/private-cover.jpg", queries.selectArticleById(1).executeAsOne().cover_image)
            assertEquals("content://new-device", queries.selectSettingByKey("backup_directory").executeAsOne().value_)
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

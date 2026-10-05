package com.dailysatori.service.sms

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.config.*
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.migration.DatabaseMigration
import com.dailysatori.service.security.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.*

class SmsMigrationTest {
    @Test fun version28DatabaseKeepsOldReminderAndAddsSmsStorage() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            driver.execute(null, "DROP TABLE sms_reminder_source", 0)
            driver.execute(null, "ALTER TABLE reminder DROP COLUMN deadline_at", 0)
            driver.execute(null, "INSERT INTO reminder(id,content,status,start_date,end_date,first_reminder_time,active_day_rule,time_zone_id,profile_json,created_at,updated_at) VALUES('old','保留旧待办','ACTIVE','2026-10-05','2026-10-06','15:00','daily','UTC','{}',1,1)", 0)
            val db = DailySatoriDatabase(driver)
            val settings = SettingRepository(db)
            settings.upsert(SettingKeys.schemaVersion, "28")
            DatabaseMigration(driver, settings, Cipher).runMigrations()
            val old = db.dailySatoriQueries.selectReminderById("old").executeAsOne()
            assertEquals("保留旧待办", old.content)
            assertNull(old.deadline_at)
            db.dailySatoriQueries.insertSmsSource("source", "encrypted", 1, "UTC", "LOCAL_ONLY")
            assertEquals("encrypted", db.dailySatoriQueries.selectSmsSource("source").executeAsOne().encrypted_source)
            assertEquals(DatabaseConfig.currentSchemaVersion.toString(), settings.get(SettingKeys.schemaVersion))
        } finally { driver.close() }
    }

    @Test fun smsSourceParticipatesInEncryptedBackupAndRestore() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            db.dailySatoriQueries.insertSmsSource("source", "enc:v1:source-json", 1, "UTC", "LOCAL_ONLY")
            val processor = SecretFieldProcessor(driver, Cipher)
            processor.decryptSecretsForBackup()
            assertEquals("source-json", db.dailySatoriQueries.selectSmsSource("source").executeAsOne().encrypted_source)
            processor.prepareRestoredSecrets()
            assertEquals("enc:v1:source-json", db.dailySatoriQueries.selectSmsSource("source").executeAsOne().encrypted_source)
        } finally { driver.close() }
    }

    private object Cipher : SecretValueCipher {
        override fun encrypt(value: String) = "enc:v1:$value"
        override fun decrypt(value: String) = value.removePrefix("enc:v1:")
        override fun isEncrypted(value: String) = value.startsWith("enc:v1:")
    }
}

package com.dailysatori.service.phone

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.config.*
import com.dailysatori.data.repository.*
import com.dailysatori.service.migration.DatabaseMigration
import com.dailysatori.service.security.*
import com.dailysatori.service.sms.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlin.test.*

class PhoneStorageTest {
    private val message = PhoneMessage(PhoneEvent(PhoneChannel.SMS, "bank", "one", "", "消费56元", 1000, "UTC"), generation = 0)

    @Test fun version30UpgradePreservesLedgerAndSettings() = withDatabase { db, driver ->
        driver.execute(null, "DROP TABLE phone_message", 0)
        val settings = SettingRepository(db)
        settings.upsert(SettingKeys.schemaVersion, "30")
        settings.upsert("existing.setting", "retained")
        val ledger = BookkeepingRepository(db)
        val before = ledger.ingest("bank", "one", "消费56元", 1000)
        DatabaseMigration(driver, settings).runMigrations()
        val records = PhoneMessageRepository(db)
        records.save(message)
        assertEquals(message, records.get(message.id))
        assertEquals(before, ledger.snapshot())
        assertEquals("retained", settings.get("existing.setting"))
        assertEquals(DatabaseConfig.currentSchemaVersion.toString(), settings.get(SettingKeys.schemaVersion))
    }

    @Test fun sourceTextAndTaskDetailsAreOrdinaryJsonInsideProtectedDatabase() = withDatabase { db, _ ->
        val records = PhoneMessageRepository(db)
        records.save(message)
        val stored = db.dailySatoriQueries.selectPhoneMessage(message.id).executeAsOne().encrypted_payload
        assertFalse(Cipher.isEncrypted(stored))
        assertTrue(stored.contains("消费56元"))
        assertEquals(message, PhoneMessageRepository(db).get(message.id))
    }

    @Test fun oldBackupCanPrepareSecretsBeforeSourceMigration() = withDatabase { db, driver ->
        driver.execute(null, "DROP TABLE phone_message", 0)
        SettingRepository(db).upsert(SettingKeys.schemaVersion, "30")
        SecretFieldProcessor(driver, Cipher).prepareRestoredSecrets()
        DatabaseMigration(driver, SettingRepository(db)).runMigrations()
        assertTrue(PhoneMessageRepository(db).all().isEmpty())
    }

    @Test fun failedLegacySettingWriteRollsBackPhonePreferences() = withDatabase { db, driver ->
        val settings = SettingRepository(db)
        val sms = SmsSourceRepository(db)
        val service = PhoneAssistantService(PhoneMessageRepository(db), sms,
            SmsReminderService(sms, ReminderRepository(db), settings, SmsReminderRemote { error("No cloud request expected") }),
            BookkeepingRepository(db), settings)
        driver.execute(null, """
            CREATE TRIGGER reject_sms_settings BEFORE INSERT ON setting
            WHEN NEW.key = 'sms_reminder.enabled'
            BEGIN SELECT RAISE(ABORT, 'Simulated write failure'); END
        """.trimIndent(), 0)
        runBlocking {
            assertFails { service.configure(PhoneChannel.SMS, PhoneOptions(enabled = true)) }
        }
        assertFalse(service.preferences().sms.enabled)
        assertNull(settings.get("phone_assistant.sms"))
    }

    @Test fun olderTodoRemainsObservableBeyondTwoHundredNewerMessages() = withDatabase { db, _ ->
        val records = PhoneMessageRepository(db)
        val old = message.copy(todos = listOf(PhoneTodo("old_todo", "请取件", PhoneResultState.PENDING)))
        records.save(old)
        repeat(205) { index -> records.save(message.copy(event = message.event.copy(key = "new_$index", receivedAt = 2000L + index))) }
        val visible = runBlocking { records.observe().first() }
        assertTrue(visible.any { it.id == old.id })
        assertEquals(206, records.all().size)
    }

    @Test fun historyPagesKeepStableOrderingWithoutHidingResultRecords() = withDatabase { db, _ ->
        val records = PhoneMessageRepository(db)
        repeat(205) { index -> records.save(message.copy(event = message.event.copy(key = "history_$index", receivedAt = 2000L + index))) }
        val pages = (0..4).flatMap { records.history(50, it * 50) }
        assertEquals(205, pages.size)
        assertEquals(205, pages.map { it.id }.toSet().size)
        assertEquals("sms:history_204", pages.first().id)
        assertEquals("sms:history_0", pages.last().id)
        assertEquals(pages.take(50), runBlocking { records.observeHistory(50).first() })
        assertEquals(pages, records.all())
    }

    private fun withDatabase(block: (DailySatoriDatabase, JdbcSqliteDriver) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try { DailySatoriDatabase.Schema.create(driver); block(DailySatoriDatabase(driver), driver) }
        finally { driver.close() }
    }
    private object Cipher : SecretValueCipher {
        override fun encrypt(value: String) = "enc:" + value.reversed()
        override fun decrypt(value: String) = if (isEncrypted(value)) value.removePrefix("enc:").reversed() else value
        override fun isEncrypted(value: String) = value.startsWith("enc:")
    }
}

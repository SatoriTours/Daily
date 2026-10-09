package com.dailysatori.service.bookkeeping

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.bookkeeping.*
import com.dailysatori.config.*
import com.dailysatori.data.repository.*
import com.dailysatori.service.migration.DatabaseMigration
import com.dailysatori.service.security.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlin.test.*

class BookkeepingStorageTest {
    @Test fun optInAndSelectedSourcesControlIntake() = runBlocking {
        withDatabase { db, _ ->
            val repo = BookkeepingRepository(db, Cipher)
            val service = BookkeepingService(repo, SettingRepository(db))
            assertFalse(service.accept("bank", "a", "消费56元", 1000))
            service.setEnabled(true)
            assertFalse(service.accept("bank", "a", "消费56元", 1000))
            service.selectSource("bank", true)
            assertTrue(service.accept("bank", "a", "消费56元", 1000))
            assertEquals(1, repo.snapshot().entries.size)
            service.setEnabled(false)
            assertFalse(service.accept("bank", "b", "消费57元", 2000))
            assertEquals(1, repo.snapshot().entries.size)
        }
    }

    @Test fun backfillReclassifiesLegacyEntriesOnceByMerchant() = runBlocking {
        withDatabase { db, _ ->
            val repo = BookkeepingRepository(db, Cipher)
            val entry = repo.ingest("bank", "one", "消费56元，商户：美团外卖", 1000).entries.single()
            assertEquals(LedgerCategory.FOOD, entry.category)
            // An entry stored before categories existed looks like an uncategorised merchant entry.
            repo.setCategory(entry.id, LedgerCategory.OTHER)
            assertEquals(LedgerCategory.OTHER, repo.snapshot().entries.single().category)
            assertTrue(repo.backfillCategories())
            assertEquals(LedgerCategory.FOOD, repo.snapshot().entries.single().category)
            assertFalse(repo.backfillCategories())
            // A merchant the built-in keywords do not know stays uncategorised.
            val unknown = repo.ingest("bank", "two", "消费12元，商户：某某科技", 2000).entries.last()
            repo.setCategory(unknown.id, LedgerCategory.OTHER)
            assertFalse(repo.backfillCategories())
        }
    }

    @Test fun encryptsWholeEntryAndRetainsDeletionIdentity() {
        withDatabase { db, _ ->
            val repo = BookkeepingRepository(db, Cipher)
            val entry = repo.ingest("bank", "one", "消费56元，商户：便利店", 1000).entries.single()
            val stored = db.dailySatoriQueries.selectBookkeepingEntries().executeAsOne().encrypted_payload
            assertTrue(Cipher.isEncrypted(stored))
            assertFalse(stored.contains("便利店"))
            repo.edit(entry.id, "57.20", "CNY", LedgerKind.EXPENSE, "超市")
            assertEquals(5720L, BookkeepingRepository(db, Cipher).snapshot().entries.single().amountMinor)
            repo.dismiss(entry.id, LedgerStatus.DELETED)
            repo.ingest("bank", "one", "消费56元", 1001)
            val deleted = repo.snapshot().entries.single()
            assertEquals(LedgerStatus.DELETED, deleted.status)
            assertEquals("", deleted.text)
        }
    }

    @Test fun version29DatabaseKeepsExistingDataAndAddsLedger() {
        withDatabase { db, driver ->
            driver.execute(null, "DROP TABLE bookkeeping_entry", 0)
            val settings = SettingRepository(db)
            settings.upsert("existing.user.setting", "keep-me")
            settings.upsert(SettingKeys.schemaVersion, "29")
            DatabaseMigration(driver, settings, Cipher).runMigrations()
            BookkeepingRepository(db, Cipher).ingest("bank", "one", "消费56元", 1000)
            assertEquals(1, db.dailySatoriQueries.selectBookkeepingEntries().executeAsList().size)
            assertEquals("keep-me", settings.get("existing.user.setting"))
            assertEquals(DatabaseConfig.currentSchemaVersion.toString(), settings.get(SettingKeys.schemaVersion))
        }
    }

    @Test fun ledgerSecretsParticipateInPortableEncryptedBackup() {
        withDatabase { db, driver ->
            BookkeepingRepository(db, Cipher).ingest("bank", "one", "消费56元", 1000)
            val processor = SecretFieldProcessor(driver, Cipher)
            processor.decryptSecretsForBackup()
            assertFalse(Cipher.isEncrypted(db.dailySatoriQueries.selectBookkeepingEntries().executeAsOne().encrypted_payload))
            processor.prepareRestoredSecrets()
            assertTrue(Cipher.isEncrypted(db.dailySatoriQueries.selectBookkeepingEntries().executeAsOne().encrypted_payload))
            assertEquals(5600L, BookkeepingRepository(db, Cipher).snapshot().entries.single().amountMinor)
        }
    }

    @Test fun version29BackupCanPrepareSecretsBeforeLedgerMigration() {
        withDatabase { db, driver ->
            driver.execute(null, "DROP TABLE bookkeeping_entry", 0)
            SettingRepository(db).upsert(SettingKeys.schemaVersion, "29")
            SecretFieldProcessor(driver, Cipher).prepareRestoredSecrets()
            DatabaseMigration(driver, SettingRepository(db), Cipher).runMigrations()
            assertTrue(BookkeepingRepository(db, Cipher).snapshot().entries.isEmpty())
        }
    }

    @Test fun concurrentRepeatedNotificationsProduceOneEntry() = runBlocking {
        withDatabase { db, _ ->
            val repo = BookkeepingRepository(db, Cipher)
            val service = BookkeepingService(repo, SettingRepository(db))
            service.setEnabled(true)
            service.selectSource("bank", true)
            coroutineScope { repeat(20) { launch(Dispatchers.Default) { service.accept("bank", "one", "消费56元", 1000) } } }
            assertEquals(1, repo.snapshot().entries.size)
            service.selectSource("bank", false)
            assertFalse(service.accept("bank", "two", "消费57元", 2000))
        }
    }

    @Test fun damagedCiphertextCannotOverwriteExistingLedger() {
        withDatabase { db, _ ->
            db.dailySatoriQueries.upsertBookkeepingEntry("bad", "enc:v1:invalid", 1, 1)
            val repo = BookkeepingRepository(db, Cipher)
            assertFails { repo.ingest("bank", "one", "消费56元", 1000) }
            assertEquals(1, db.dailySatoriQueries.selectBookkeepingEntries().executeAsList().size)
            assertEquals("enc:v1:invalid", db.dailySatoriQueries.selectBookkeepingEntries().executeAsOne().encrypted_payload)
        }
    }

    @Test fun encryptionFailurePreservesExistingEntry() {
        withDatabase { db, _ ->
            val repo = BookkeepingRepository(db, Cipher)
            val original = repo.ingest("bank", "one", "消费56元", 1000)
            val failingCipher = object : SecretValueCipher {
                override fun encrypt(value: String): String = error("encryption_unavailable")
                override fun decrypt(value: String) = Cipher.decrypt(value)
                override fun isEncrypted(value: String) = Cipher.isEncrypted(value)
            }
            assertFails { BookkeepingRepository(db, failingCipher).edit(original.entries.single().id, "99", "CNY", LedgerKind.EXPENSE, "") }
            assertEquals(original, repo.snapshot())
        }
    }

    @Test fun malformedEntryCannotReachUiOrBeOverwritten() {
        withDatabase { db, _ ->
            val malformed = """{"id":"bad","eventKeys":["one"],"source":"bank","text":"","receivedAt":1000,"amountMinor":-1,"currency":"CNY","kind":"EXPENSE","status":"POSTED","reason":""}"""
            db.dailySatoriQueries.upsertBookkeepingEntry("bad", Cipher.encrypt(malformed), 1, 1)
            assertFails { BookkeepingRepository(db, Cipher).snapshot() }
            assertEquals(Cipher.encrypt(malformed), db.dailySatoriQueries.selectBookkeepingEntries().executeAsOne().encrypted_payload)
        }
    }

    @Test fun notificationIntakeDecryptsEachExistingEntryOnlyOnce() = runBlocking {
        withDatabase { db, _ ->
            var decryptions = 0
            val countingCipher = object : SecretValueCipher {
                override fun encrypt(value: String) = Cipher.encrypt(value)
                override fun decrypt(value: String): String { decryptions++; return Cipher.decrypt(value) }
                override fun isEncrypted(value: String) = Cipher.isEncrypted(value)
            }
            val repo = BookkeepingRepository(db, countingCipher)
            repo.ingest("bank", "one", "消费56元", 1000)
            val service = BookkeepingService(repo, SettingRepository(db))
            service.setEnabled(true)
            service.selectSource("bank", true)
            decryptions = 0
            assertTrue(service.accept("bank", "two", "消费57元", 400000))
            assertEquals(1, decryptions)
            decryptions = 0
            assertFalse(service.accept("bank", "two", "消费57元", 400000))
            assertEquals(2, decryptions)
        }
    }

    @Test fun manualConfirmationAndItsIdentitySurviveReload() {
        withDatabase { db, _ ->
            val repo = BookkeepingRepository(db, Cipher)
            val original = repo.ingest("bank", "one", "尾号1238消费56元，交易单号：AB123456", 1000).entries.single()
            repo.edit(original.id, "58", "USD", LedgerKind.INCOME, "修正")
            val reloaded = BookkeepingRepository(db, Cipher)
            val repeated = reloaded.ingest("bank", "two", "消费56元，交易单号：AB123456", 2000)
            assertEquals(5800L, repeated.entries.single().amountMinor)
            assertEquals("USD", repeated.entries.single().currency)
            assertEquals(LedgerKind.INCOME, repeated.entries.single().kind)
            assertEquals(listOf("one", "two"), repeated.entries.single().eventKeys)
        }
    }

    @Test fun entriesWrittenBeforeConfirmationFlagRemainReadable() {
        withDatabase { db, _ ->
            val legacy = """{"id":"bank:one","eventKeys":["one"],"source":"bank","text":"消费56元","receivedAt":1000,"amountMinor":5600,"currency":"CNY","kind":"EXPENSE","status":"POSTED","reason":""}"""
            db.dailySatoriQueries.upsertBookkeepingEntry("bank:one", Cipher.encrypt(legacy), 1, 1)
            val entry = BookkeepingRepository(db, Cipher).snapshot().entries.single()
            assertFalse(entry.userConfirmed)
            assertEquals(5600L, entry.amountMinor)
            assertEquals(LedgerStatus.POSTED, entry.status)
        }
    }

    private inline fun withDatabase(block: (DailySatoriDatabase, JdbcSqliteDriver) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try { DailySatoriDatabase.Schema.create(driver); block(DailySatoriDatabase(driver), driver) }
        finally { driver.close() }
    }

    private object Cipher : SecretValueCipher {
        override fun encrypt(value: String) = "enc:v1:" + value.reversed()
        override fun decrypt(value: String) = if (isEncrypted(value)) value.removePrefix("enc:v1:").reversed() else value
        override fun isEncrypted(value: String) = value.startsWith("enc:v1:")
    }
}

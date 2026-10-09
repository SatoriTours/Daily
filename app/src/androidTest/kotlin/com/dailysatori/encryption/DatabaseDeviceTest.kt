@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.dailysatori.encryption

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.db.SqlDriver
import com.dailysatori.core.lifearchive.EncryptedLifeArchiveRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.platform.*
import com.dailysatori.service.backup.*
import com.dailysatori.service.security.*
import com.dailysatori.shared.db.DailySatoriDatabase
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.*

internal fun labContext(): Context {
    check(InstrumentationRegistry.getArguments().getString("disposable") == "yes") { "Disposable emulator authorization is required" }
    check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish")) { "Only disposable emulators may run destructive fixtures" }
    return InstrumentationRegistry.getInstrumentation().targetContext
}
internal fun archive(context: Context) = EncryptedLifeArchiveRepository(File(context.noBackupFilesDir, "life_archive"), SecretCipher(PlatformContext(context)))
internal fun service(context: Context, driver: SqlDriver): BackupService {
    val platform = PlatformContext(context)
    val backup = BackupService(FileManager().apply { init(context) }, SettingRepository(DailySatoriDatabase(driver)), BackupPasswordStore(platform),
        DatabaseDriverFactory(platform), SecretCipher(platform), archive(context))
    // Keep every production storage/crypto/SAF adapter; only defer process exit to the cold-launch test driver.
    val field = BackupService::class.java.getDeclaredField("files").apply { isAccessible = true }
    val realFiles = field.get(backup) as BackupFiles
    field.set(backup, object : BackupFiles by realFiles {
        override fun restartApp() { error("Device test performs an explicit cold restart after durable staging") }
    })
    return backup
}
internal fun treeUri(): String {
    val context = labContext()
    val uri = DocumentsContract.buildTreeDocumentUri("com.dailysatori.test.encryption.documents", "root")
    val command = "am start -n com.dailysatori.test/com.dailysatori.encryption.FixtureGrantActivity"
    InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { descriptor ->
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { reader ->
            check(reader.readText().contains("Starting:")) { "Cannot grant the test SAF tree" }
        }
    }
    repeat(20) {
        try {
            context.contentResolver.takePersistableUriPermission(uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            return uri.toString()
        } catch (failure: SecurityException) { if(it == 19) throw failure; Thread.sleep(200) }
    }
    error("SAF grant unavailable")
}
internal fun newestBackup(context: Context): File {
    val files = FileManager().apply { init(context) }
    val name = files.listBackupFilesInDirectory(treeUri()).first()
    return File(context.cacheDir, "device-transfer.enc").also { check(files.readFileFromDirectory(treeUri(), name, it.path)) }
}

@RunWith(AndroidJUnit4::class)
class DatabaseDeviceTest {
    @Test fun fullAndroidSchemaExportsWithReadwriteOnlySource() = sandbox { context ->
        DeviceFixture.seedLegacy(context, 10)
        val driver = DatabaseDriverFactory(PlatformContext(context)).createLegacyDriver("daily_satori.db")
        val key = DatabaseKey.generate()
        val target = File(context.cacheDir, "export.db")
        try { exportEncryptedDatabase(driver, target.path, key) } finally { driver.close() }
        DatabaseDriverFactory(PlatformContext(context)).createBackupDriver(target.path, key).useDriver {
            assertEquals("10", DeviceFixture.scalar(it, "SELECT count(*) FROM diary"))
        }
        val original = target.readBytes()
        assertFails { prepareEncryptedSnapshotFile(target.path) }
        assertContentEquals(original, target.readBytes())
    }
    private fun sandbox(block: (SandboxContext) -> Unit) {
        val base = labContext()
        val root = File(base.cacheDir, "encryption-lab-${java.util.UUID.randomUUID()}")
        try { block(SandboxContext(base, root)) } finally { root.deleteRecursively() }
    }
    @Test fun freshInstallCreatesEncryptedDatabaseAndHardwareWrappedKey() = sandbox { context ->
        val p = PlatformContext(context)
        DatabaseBootstrap.prepare(p)
        val keys = DatabaseKeyStore(p)
        val key = requireNotNull(keys.readExisting())
        assertEquals(93L, File(keys.storagePath()).length())
        val factory = DatabaseDriverFactory(p)
        factory.createDriver().useDriver { driver ->
            driver.execute(null, "INSERT INTO setting(key,value,created_at,updated_at) VALUES('test','private-value',1,1)", 0)
        }
        factory.createDriver().useDriver { assertEquals("private-value", DeviceFixture.scalar(it, "SELECT value FROM setting WHERE key='test'")) }
        assertFalse(isPlaintextDatabase(context.getDatabasePath("daily_satori.db")))
        assertFails { factory.createBackupDriver(context.getDatabasePath("daily_satori.db").path, DatabaseKey.generate()).close() }
        assertFails { android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath("daily_satori.db").path, null, 1).use { it.rawQuery("SELECT * FROM setting", null).use { c -> c.moveToFirst() } } }
        assertFalse(File(keys.storagePath()).readBytes().toString(Charsets.ISO_8859_1).contains(key.portableJson()))
    }
    @Test fun genuineKeystoreLegacyMigrationPreservesCorpusFtsHierarchyAndAttachments() = sandbox { context ->
        DeviceFixture.seedLegacy(context)
        val files = FileManager().apply { init(context) }
        val image = files.sha256("${files.getAppDataDir()}/diary_images/fixture.png")
        val audio = files.sha256("${files.getAppDataDir()}/diary/audio/1/fixture.m4a")
        assertTrue(isPlaintextDatabase(context.getDatabasePath("daily_satori.db")))
        DatabaseBootstrap.prepare(PlatformContext(context))
        DatabaseDriverFactory(PlatformContext(context)).createDriver().useDriver { driver ->
            DeviceFixture.assertData(driver)
            assertEquals("512", DeviceFixture.scalar(driver, "SELECT count(*) FROM diary_fts WHERE diary_fts MATCH 'sample'"))
        }
        assertEquals(image, files.sha256("${files.getAppDataDir()}/diary_images/fixture.png"))
        assertEquals(audio, files.sha256("${files.getAppDataDir()}/diary/audio/1/fixture.m4a"))
        DatabaseBootstrap.prepare(PlatformContext(context))
        assertFalse(context.getDatabasePath("daily_satori.db").readBytes().toString(Charsets.UTF_8).contains(MARKER))
    }
    @Test fun malformedLegacyFieldAbortsWithoutChangingSourceOrAttachments() = sandbox { context ->
        DeviceFixture.seedLegacy(context, 10)
        DatabaseDriverFactory(PlatformContext(context)).createLegacyDriver("daily_satori.db").useDriver {
            it.execute(null, "UPDATE mcp_server SET api_key='enc:v1:invalid'", 0)
        }
        val original = context.getDatabasePath("daily_satori.db").readBytes()
        val image = File(context.filesDir, "DailySatori/diary_images/fixture.png").readBytes()
        assertFails { DatabaseBootstrap.prepare(PlatformContext(context)) }
        assertContentEquals(original, context.getDatabasePath("daily_satori.db").readBytes())
        assertContentEquals(image, File(context.filesDir, "DailySatori/diary_images/fixture.png").readBytes())
        assertFalse(File(context.noBackupFilesDir, "database_key.sec").exists())
    }
    @Test fun missingCorruptAndWrongWrappedKeyNeverCreateAnEmptyReplacement() = sandbox { context ->
        val p = PlatformContext(context)
        DatabaseBootstrap.prepare(p)
        val keys = DatabaseKeyStore(p)
        val file = File(keys.storagePath())
        val originalKey = file.readBytes()
        val originalDb = context.getDatabasePath("daily_satori.db").readBytes()
        file.delete()
        assertFails { DatabaseBootstrap.prepare(p) }
        assertFalse(file.exists())
        assertContentEquals(originalDb, context.getDatabasePath("daily_satori.db").readBytes())
        for (bytes in listOf(originalKey.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }, keys.wrap(DatabaseKey.generate()))) {
            file.writeBytes(bytes)
            assertFails { DatabaseBootstrap.prepare(p) }
            assertContentEquals(bytes, file.readBytes())
            assertContentEquals(originalDb, context.getDatabasePath("daily_satori.db").readBytes())
        }
        file.writeBytes(originalKey)
    }
    @Test fun readonlyRecoveryHasNoDatabaseKeyOrAliasWrites() = sandbox { context ->
        DeviceFixture.seedLegacy(context, 10)
        val p = PlatformContext(context)
        val keystore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val aliases = keystore.aliases().toList().sorted()
        assertEquals("stable", readRecoverySettings(p)["update_channel"])
        assertEquals(aliases, keystore.aliases().toList().sorted())
        assertFalse(File(context.noBackupFilesDir, "database_key.sec").exists())
        DatabaseBootstrap.prepare(p)
        val db = context.getDatabasePath("daily_satori.db")
        val before = db.readBytes()
        val keyFile = File(DatabaseKeyStore(p).storagePath())
        val beforeKey = keyFile.readBytes()
        assertEquals("stable", readRecoverySettings(p)["update_channel"])
        assertContentEquals(before, db.readBytes())
        assertContentEquals(beforeKey, keyFile.readBytes())
    }
    @Test fun realSafBackupVerificationAndRestorePreserveTheSameDatabaseKey() = sandbox { context -> runBlocking {
        DeviceFixture.seedLegacy(context, 10)
        val p = PlatformContext(context)
        DatabaseBootstrap.prepare(p)
        val factory = DatabaseDriverFactory(p)
        val key = factory.readDatabaseKey()
        val driver = factory.createDriver()
        val settings = SettingRepository(DailySatoriDatabase(driver))
        settings.upsert("backup_directory", treeUri())
        BackupPasswordStore(p).save(PASSWORD)
        val backup = service(context, driver)
        assertTrue(backup.backupNow(), backup.lastMessage.value)
        val encrypted = newestBackup(context)
        assertTrue(backup.verifyLatestBackup(PASSWORD).status == BackupVerificationStatus.PASSED, backup.lastMessage.value)
        val before = File(DatabaseKeyStore(p).storagePath()).readBytes()
        assertFalse(backup.restoreFile(Uri.fromFile(encrypted).toString(), "wrong-password"))
        assertContentEquals(before, File(DatabaseKeyStore(p).storagePath()).readBytes())
        assertTrue(backup.restoreFile(Uri.fromFile(encrypted).toString(), PASSWORD), backup.lastMessage.value)
        driver.close()
        DatabaseBootstrap.prepare(p)
        assertEquals(fingerprint(key.portableJson().encodeToByteArray()), fingerprint(factory.readDatabaseKey().portableJson().encodeToByteArray()))
        factory.createDriver().useDriver { DeviceFixture.assertData(it, 10, context) }
    } }
    @Test fun everyInstallMoveBoundaryRecoversOneCompleteRealDatabaseAndKeyPair() {
        for (point in 1..12) sandbox { context ->
            val p = PlatformContext(context)
            DatabaseBootstrap.prepare(p)
            val factory = DatabaseDriverFactory(p)
            val oldKey = factory.readDatabaseKey()
            val nextKey = DatabaseKey.generate()
            factory.createDriver().useDriver { it.execute(null, "INSERT INTO setting(key,value,created_at,updated_at) VALUES('pair','old',1,1)", 0) }
            val candidate = File(context.cacheDir, "candidate.db")
            factory.createEncryptedDriver(candidate.path, nextKey).useDriver { it.execute(null, "INSERT INTO setting(key,value,created_at,updated_at) VALUES('pair','new',1,1)", 0) }
            val root = File(context.noBackupFilesDir, "database-install")
            val db = context.getDatabasePath("daily_satori.db")
            val keyFile = File(DatabaseKeyStore(p).storagePath())
            var moves = 0
            val txn = DatabaseInstallTransaction(root, db, keyFile) { from, to ->
                to.parentFile!!.mkdirs()
                if (++moves == point) throw DeviceDeath()
                Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
            }
            try { txn.stage(candidate, factory.wrapDatabaseKey(nextKey)); txn.applyPending() } catch (_: DeviceDeath) { }
            DatabaseInstallTransaction(root, db, keyFile).applyPending()
            val recovered = factory.readDatabaseKey()
            factory.createDriver().useDriver { driver ->
                val value = DeviceFixture.scalar(driver, "SELECT value FROM setting WHERE key='pair'")
                assertTrue((value == "old" && fingerprint(recovered.portableJson().encodeToByteArray()) == fingerprint(oldKey.portableJson().encodeToByteArray())) || (value == "new" && fingerprint(recovered.portableJson().encodeToByteArray()) == fingerprint(nextKey.portableJson().encodeToByteArray())), "move boundary $point")
            }
        }
    }
    @Test fun restoreMoveBoundariesKeepDatabaseKeyAndAllExternalPayloadsTogether() {
        for (point in 1..16) sandbox { context ->
            val p = PlatformContext(context)
            DatabaseBootstrap.prepare(p)
            val factory = DatabaseDriverFactory(p)
            val key = factory.readDatabaseKey()
            factory.createDriver().useDriver { it.execute(null, "INSERT INTO setting(key,value,created_at,updated_at) VALUES('pair','old',1,1)", 0) }
            val data = File(context.filesDir, "DailySatori").apply { mkdirs() }
            File(data, "fixture.bin").writeText("old")
            val life = File(context.noBackupFilesDir, "life_archive").apply { mkdirs() }
            File(life, "opaque-protected-fixture").writeText("old")
            val prepared = preparedPair(context, factory, "new")
            val nextFingerprint = fingerprint(DatabaseKeyStore(p).unwrap(File(prepared, "database_key.sec").readBytes()).portableJson().encodeToByteArray())
            BackupPasswordStore(p).save("old")
            var moves = 0
            val root = File(context.noBackupFilesDir, "pending-restore")
            val txn = BackupRestoreTransaction(root, data, context.getDatabasePath("daily_satori.db"), life,
                File(context.filesDir, "backup_password.sec"), File(DatabaseKeyStore(p).storagePath()), move = { from, to ->
                    to.parentFile!!.mkdirs()
                    if (++moves == point) throw DeviceDeath()
                    Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
                })
            try { txn.stage(prepared); txn.applyPending() } catch (_: DeviceDeath) { }
            DatabaseBootstrap.prepare(p)
            val value = factory.createDriver().useDriver { DeviceFixture.scalar(it, "SELECT value FROM setting WHERE key='pair'") }
            val expectedKey = if(value == "old") fingerprint(key.portableJson().encodeToByteArray()) else nextFingerprint
            assertTrue(value == "old" || value == "new", "restore boundary $point")
            assertEquals(expectedKey, fingerprint(factory.readDatabaseKey().portableJson().encodeToByteArray()))
            assertEquals(value, File(data, "fixture.bin").readText())
            assertEquals(value, File(life, "opaque-protected-fixture").readText())
            assertEquals(value, BackupPasswordStore(p).get())
            DatabaseBootstrap.prepare(p)
            assertFalse(File(root, "ready").exists())
        }
    }
    @Test fun invalidPreparedRestoreCannotReplaceActiveDataOrReplayOnNextBootstrap() = sandbox { context ->
        val p = PlatformContext(context)
        DeviceFixture.seedLegacy(context, 10)
        DatabaseBootstrap.prepare(p)
        val factory = DatabaseDriverFactory(p)
        val prepared = preparedPair(context, factory, "new")
        File(prepared, "database.db").writeBytes(byteArrayOf(1, 2, 3, 4))
        BackupPasswordStore(p).save("old")
        val db = context.getDatabasePath("daily_satori.db")
        val before = db.readBytes()
        val key = File(DatabaseKeyStore(p).storagePath()).readBytes()
        val image = File(context.filesDir, "DailySatori/diary_images/fixture.png").readBytes()
        FileManager().apply { init(context) }.stageRestore(prepared.path)
        repeat(2) { DatabaseBootstrap.prepare(p) }
        assertContentEquals(before, db.readBytes())
        assertContentEquals(key, File(DatabaseKeyStore(p).storagePath()).readBytes())
        assertContentEquals(image, File(context.filesDir, "DailySatori/diary_images/fixture.png").readBytes())
        assertEquals("old", BackupPasswordStore(p).get())
        factory.createDriver().useDriver { DeviceFixture.assertData(it, 10, context) }
    }
    @Test fun bootstrapResolvesBothPendingTransactionsBeforeOpeningBusinessDatabase() = sandbox { context ->
        val p = PlatformContext(context)
        DatabaseBootstrap.prepare(p)
        val factory = DatabaseDriverFactory(p)
        val middleKey = DatabaseKey.generate()
        val candidate = File(context.cacheDir, "middle.db")
        factory.createEncryptedDriver(candidate.path, middleKey).useDriver {
            it.execute(null, "INSERT INTO setting(key,value,created_at,updated_at) VALUES('pair','middle',1,1)", 0)
        }
        DatabaseInstallTransaction(File(context.noBackupFilesDir, "database-install"), context.getDatabasePath("daily_satori.db"),
            File(DatabaseKeyStore(p).storagePath())).stage(candidate, factory.wrapDatabaseKey(middleKey))
        val prepared = preparedPair(context, factory, "final")
        val expected = fingerprint(DatabaseKeyStore(p).unwrap(File(prepared, "database_key.sec").readBytes()).portableJson().encodeToByteArray())
        FileManager().apply { init(context) }.stageRestore(prepared.path)
        repeat(2) { DatabaseBootstrap.prepare(p) }
        factory.createDriver().useDriver { assertEquals("final", DeviceFixture.scalar(it, "SELECT value FROM setting WHERE key='pair'")) }
        assertEquals(expected, fingerprint(factory.readDatabaseKey().portableJson().encodeToByteArray()))
        assertEquals("final", File(context.filesDir, "DailySatori/fixture.bin").readText())
        assertEquals("final", BackupPasswordStore(p).get())
    }
    private fun preparedPair(context: Context, factory: DatabaseDriverFactory, value: String): File {
        val prepared = File(context.cacheDir, "prepared-pair").apply { mkdirs() }
        val key = DatabaseKey.generate()
        factory.createEncryptedDriver(File(prepared, "database.db").path, key).useDriver {
            it.execute(null, "INSERT INTO setting(key,value,created_at,updated_at) VALUES('pair',?,1,1)", 1) { bindString(0, value) }
        }
        File(prepared, "database_key.sec").writeBytes(factory.wrapDatabaseKey(key))
        File(prepared, "app_data").mkdirs()
        File(prepared, "app_data/fixture.bin").writeText(value)
        File(prepared, "life_archive").mkdirs()
        File(prepared, "life_archive/opaque-protected-fixture").writeText(value)
        BackupPasswordStore(PlatformContext(context)).save(value)
        File(context.filesDir, "backup_password.sec").copyTo(File(prepared, "backup_password.sec"))
        return prepared
    }
    private class DeviceDeath : Error()
}

internal inline fun <T> SqlDriver.useDriver(block: (SqlDriver) -> T): T = try { block(this) } finally { close() }

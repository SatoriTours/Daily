package com.dailysatori.encryption

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dailysatori.platform.*
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.backup.*
import com.dailysatori.service.security.*
import com.dailysatori.service.lifearchive.LifeArchiveRecord
import com.dailysatori.shared.db.DailySatoriDatabase
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.*

/** This class also runs against the genuinely old APK, before installing the new APK. */
@RunWith(AndroidJUnit4::class)
class LegacySeedDeviceTest {
    @Test fun seedOldAppWithRealLegacyCipherAndAttachments() {
        val context = labContext()
        assertFalse(context.getDatabasePath("daily_satori.db").exists(), "Only a cleared disposable emulator may be seeded")
        DeviceFixture.seedLegacy(context)
        assertFalse(File(context.noBackupFilesDir, "database_key.sec").exists())
        android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath("daily_satori.db").path, null, 1).use { db ->
            db.rawQuery("SELECT api_token FROM ai_config", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.getString(0).startsWith("enc:v1:"))
            }
        }
    }
}

@RunWith(AndroidJUnit4::class)
class SourceUpgradeDeviceTest {
    @Test fun realUpgradePreservesOldAppDataAndExportsPortableBackup() = runBlocking {
        val context = labContext()
        val p = PlatformContext(context)
        DatabaseBootstrap.prepare(p)
        val factory = DatabaseDriverFactory(p)
        factory.createDriver().useDriver { driver ->
            DeviceFixture.assertData(driver)
            val archive = archive(context)
            val category = archive.addCategory("模拟器升级验收")
            archive.save(LifeArchiveRecord("device-life-fixture", "原始生活资料", category.id, MARKER), null)
            val files = FileManager().apply { init(context) }
            val settings = SettingRepository(DailySatoriDatabase(driver))
            settings.upsert("backup_directory", treeUri())
            BackupPasswordStore(p).save(PASSWORD)
            val backup = service(context, driver)
            assertTrue(backup.backupNow(), backup.lastMessage.value)
            assertEquals(BackupVerificationStatus.PASSED, backup.verifyLatestBackup(PASSWORD).status)
            val transferred = newestBackup(context)
            val evidence = buildJsonObject {
                put("databaseKeyFingerprint", fingerprint(factory.readDatabaseKey().portableJson().encodeToByteArray()))
                put("imageSha256", files.sha256("${files.getAppDataDir()}/diary_images/fixture.png"))
                put("audioSha256", files.sha256("${files.getAppDataDir()}/diary/audio/1/fixture.m4a"))
                put("backupSha256", files.sha256(transferred.path))
            }
            File(context.filesDir, "source-evidence.json").writeText(evidence.toString())
        }
    }
}

@RunWith(AndroidJUnit4::class)
class ReceiverRestoreDeviceTest {
    @Test fun differentDeviceCannotUseSourceEnvelopeButCanRecoverWithBackupPassword() = runBlocking<Unit> {
        val context = labContext()
        val p = PlatformContext(context)
        val factory = DatabaseDriverFactory(p)
        val sourceEnvelope = File(context.filesDir, "source-device-key.sec").readBytes()
        val db = context.getDatabasePath("daily_satori.db")
        val keyFile = File(context.noBackupFilesDir, "database_key.sec")
        db.parentFile!!.mkdirs()
        File(context.filesDir, "source-database.db").copyTo(db, overwrite = true)
        keyFile.writeBytes(sourceEnvelope)
        val originalDb = db.readBytes()
        assertFails { DatabaseBootstrap.prepare(p) }
        assertContentEquals(originalDb, db.readBytes())
        assertContentEquals(sourceEnvelope, keyFile.readBytes())
        // Recover without opening the locked active database or starting business DI.
        val transientSettings = factory.createInMemoryDriver()
        try {
            val backup = service(context, transientSettings)
            val transfer = File(context.filesDir, "transferred.enc")
            assertFalse(backup.restoreFile(Uri.fromFile(transfer).toString(), "wrong-password"))
            assertContentEquals(originalDb, db.readBytes())
            assertContentEquals(sourceEnvelope, keyFile.readBytes())
            assertTrue(backup.restoreFile(Uri.fromFile(transfer).toString(), PASSWORD), backup.lastMessage.value)
        } finally { transientSettings.close() }
        DatabaseBootstrap.prepare(p)
        assertFalse(sourceEnvelope.contentEquals(keyFile.readBytes()), "Receiver must wrap the source DB key under its own Keystore")
        val evidence = Json.parseToJsonElement(File(context.filesDir, "source-evidence.json").readText()).jsonObject
        assertEquals(evidence.getValue("databaseKeyFingerprint").jsonPrimitive.content, fingerprint(factory.readDatabaseKey().portableJson().encodeToByteArray()))
        factory.createDriver().useDriver { DeviceFixture.assertData(it, context = context) }
        val files = FileManager().apply { init(context) }
        assertEquals(evidence.getValue("imageSha256").jsonPrimitive.content, files.sha256("${files.getAppDataDir()}/diary_images/fixture.png"))
        assertEquals(evidence.getValue("audioSha256").jsonPrimitive.content, files.sha256("${files.getAppDataDir()}/diary/audio/1/fixture.m4a"))
        assertEquals(MARKER, archive(context).records().single { it.id == "device-life-fixture" }.body)
        assertEquals(PASSWORD, BackupPasswordStore(p).get())
        // Keystore rewrapping must not rewrite the whole DB: the exported snapshot salt is retained.
        val zip = File(context.cacheDir, "restored-snapshot.zip")
        val content = File(context.cacheDir, "snapshot-check").apply { mkdirs() }
        files.decryptFile(File(context.filesDir, "transferred.enc").path, zip.path, PASSWORD)
        files.extractZip(zip.path, content.path)
        assertContentEquals(File(content, "daily_satori.db").inputStream().use { it.readNBytes(16) }, db.inputStream().use { it.readNBytes(16) })
        zip.delete(); content.deleteRecursively()
    }
}

class ReceiverColdStartDeviceTest {
    @Test fun realApplicationColdStartKeepsRestoredDataReadable() = runBlocking {
        val context = labContext()
        val factory = DatabaseDriverFactory(PlatformContext(context))
        factory.createDriver().useDriver { DeviceFixture.assertData(it, context = context) }
        val evidence = Json.parseToJsonElement(File(context.filesDir, "source-evidence.json").readText()).jsonObject
        val files = FileManager().apply { init(context) }
        assertEquals(evidence.getValue("databaseKeyFingerprint").jsonPrimitive.content, fingerprint(factory.readDatabaseKey().portableJson().encodeToByteArray()))
        assertEquals(evidence.getValue("imageSha256").jsonPrimitive.content, files.sha256("${files.getAppDataDir()}/diary_images/fixture.png"))
        assertEquals(evidence.getValue("audioSha256").jsonPrimitive.content, files.sha256("${files.getAppDataDir()}/diary/audio/1/fixture.m4a"))
        assertEquals(MARKER, archive(context).records().single { it.id == "device-life-fixture" }.body)
        assertEquals(PASSWORD, BackupPasswordStore(PlatformContext(context)).get())
    }
}

internal fun fingerprint(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

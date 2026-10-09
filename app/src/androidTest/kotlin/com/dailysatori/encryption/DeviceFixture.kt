package com.dailysatori.encryption

import android.content.Context
import android.content.ContextWrapper
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.dailysatori.config.DatabaseConfig
import com.dailysatori.config.SettingKeys
import com.dailysatori.platform.FileManager
import com.dailysatori.platform.PlatformContext
import com.dailysatori.service.security.SecretCipher
import com.dailysatori.service.security.SecretFieldRegistry
import com.dailysatori.shared.db.DailySatoriDatabase
import java.io.File

internal const val PASSWORD = "device-fixture-backup-password"
internal const val MARKER = "设备加密验收-原始资料-839173"

/** No real user credentials; this corpus is seeded into disposable emulators only. */
internal object DeviceFixture {
    fun seedLegacy(context: Context, count: Int = 512) {
        val driver = AndroidSqliteDriver(DailySatoriDatabase.Schema, context, "daily_satori.db")
        try { populate(driver, context, true, count) } finally { driver.close() }
    }

    fun populate(driver: SqlDriver, context: Context, legacy: Boolean, count: Int = 512) {
        val cipher = SecretCipher(PlatformContext(context))
        fun secret(text: String) = if (legacy) cipher.encrypt(text) else text
        val db = DailySatoriDatabase(driver)
        db.transaction {
            repeat(count) { index ->
                driver.execute(null, "INSERT INTO diary(id,content,tags,images,created_at,updated_at) VALUES(?,?,'[]',?,1,1)", 3) {
                    bindLong(0, (index + 1).toLong())
                    bindString(1, "$MARKER-$index " + "sample ".repeat(256))
                    bindString(2, if (index == 0) "diary_images/fixture.png" else "")
                }
            }
            driver.execute(null, "UPDATE diary SET parent_diary_id=1 WHERE id=2", 0)
            driver.execute(null, "INSERT INTO diary_attachment(diary_id,kind,local_path,created_at,updated_at) VALUES(1,'audio','diary/audio/1/fixture.m4a',1,1)", 0)
            driver.execute(null, "INSERT INTO ai_config(api_address,api_token,model_name,created_at,updated_at) VALUES('https://example.invalid',?,'fixture',1,1)", 1) { bindString(0, secret(value(0))) }
            driver.execute(null, "INSERT INTO sms_reminder_source(id,encrypted_source,received_at,time_zone_id,status) VALUES('sms-fixture',?,1,'Asia/Shanghai','pending')", 1) { bindString(0, secret(value(1))) }
            driver.execute(null, "INSERT INTO bookkeeping_entry(id,encrypted_payload,created_at,updated_at) VALUES('ledger-fixture',?,1,1)", 1) { bindString(0, secret(value(2))) }
            driver.execute(null, "INSERT INTO phone_message(id,encrypted_payload,received_at) VALUES('phone-fixture',?,1)", 1) { bindString(0, secret(value(3))) }
            db.dailySatoriQueries.upsertSetting("sms_reminder.blocked_senders", secret(value(4)), 1, 1)
            driver.execute(null, "INSERT INTO mcp_server(name,server_url,api_key,enabled,created_at,updated_at) VALUES('fixture','https://example.invalid',?,0,1,1)", 1) { bindString(0, secret(value(5))) }
            driver.execute(null, "INSERT INTO remote_news_source(name,base_url,api_token,enabled,created_at,updated_at) VALUES('fixture','https://example.invalid',?,0,1,1)", 1) { bindString(0, secret(value(6))) }
            driver.execute(null, "INSERT INTO external_favorite_source(provider,display_name,account_id,auth_json,enabled,created_at,updated_at) VALUES('fixture','fixture','fixture',?,0,1,1)", 1) { bindString(0, secret(value(7))) }
            driver.execute(null, "INSERT INTO skill_config(name,gateway_url,api_token,created_at,updated_at) VALUES('fixture','https://example.invalid',?,1,1)", 1) { bindString(0, secret(value(8))) }
            db.dailySatoriQueries.upsertSetting(SettingKeys.weReadApiKey, secret(value(9)), 1, 1)
            db.dailySatoriQueries.upsertSetting(SettingKeys.speechConfig, secret(value(10)), 1, 1)
            db.dailySatoriQueries.upsertSetting("schema_version", DatabaseConfig.currentSchemaVersion.toString(), 1, 1)
            db.dailySatoriQueries.upsertSetting("update_channel", "stable", 1, 1)
        }
        val files = FileManager().apply { init(context) }
        File(files.getAppDataDir(), "diary_images/fixture.png").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(32768) { (it % 251).toByte() }) }
        File(files.getAppDataDir(), "diary/audio/1/fixture.m4a").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(65536) { (it % 193).toByte() }) }
    }

    fun value(index: Int): String = when(index) {
        1 -> "{\"id\":\"sms-fixture\",\"body\":\"$MARKER\"}"
        2 -> "{\"amount\":12345,\"note\":\"$MARKER\"}"
        3 -> "{\"id\":\"phone-fixture\",\"body\":\"$MARKER\"}"
        4 -> "[\"fixture-sender\"]"
        7 -> "{\"token\":\"fixture-auth-839173\"}"
        10 -> "{\"apiKey\":\"fixture-speech-839173\"}"
        else -> "fixture-credential-$index-839173"
    }

    fun assertData(driver: SqlDriver, count: Int = 512, context: Context? = null) {
        kotlin.test.assertEquals(count.toString(), scalar(driver, "SELECT count(*) FROM diary"))
        rows(driver, "SELECT content FROM diary ORDER BY id").forEachIndexed { index, content ->
            kotlin.test.assertEquals("$MARKER-$index " + "sample ".repeat(256), content, "Diary content #$index")
        }
        kotlin.test.assertEquals(count.toString(), scalar(driver, "SELECT count(*) FROM diary_fts WHERE diary_fts MATCH 'sample'"))
        assertAttachmentPath(scalar(driver, "SELECT local_path FROM diary_attachment WHERE diary_id=1"), "diary/audio/1/fixture.m4a", context)
        assertAttachmentPath(scalar(driver, "SELECT images FROM diary WHERE id=1"), "diary_images/fixture.png", context)
        kotlin.test.assertEquals("1", scalar(driver, "SELECT parent_diary_id FROM diary WHERE id=2"))
        SecretFieldRegistry.fields.forEachIndexed { index, field ->
            val where = field.whereClause?.let { " WHERE $it" } ?: ""
            kotlin.test.assertEquals(value(index), scalar(driver, "SELECT ${field.column} FROM ${field.table}$where ORDER BY rowid LIMIT 1"), "Legacy field #$index")
        }
        kotlin.test.assertEquals("ok", scalar(driver, "PRAGMA integrity_check"))
        kotlin.test.assertTrue(rows(driver, "PRAGMA foreign_key_check").isEmpty())
        kotlin.test.assertTrue(rows(driver, "PRAGMA cipher_integrity_check").isEmpty())
    }

    private fun assertAttachmentPath(stored: String, relative: String, context: Context?) {
        if (context == null) kotlin.test.assertTrue(stored.endsWith(relative))
        else {
            val root = File(context.filesDir, "DailySatori")
            val actual = if (File(stored).isAbsolute) File(stored) else File(root, stored)
            kotlin.test.assertEquals(File(root, relative).canonicalPath, actual.canonicalPath)
        }
    }
    fun scalar(driver: SqlDriver, sql: String) = rows(driver, sql).single()
    fun rows(driver: SqlDriver, sql: String): List<String> = driver.executeQuery(null, sql, { cursor ->
        val values = mutableListOf<String>()
        while (cursor.next().value) values += cursor.getString(0).orEmpty()
        QueryResult.Value(values)
    }, 0).value
}

internal class SandboxContext(base: Context, val root: File) : ContextWrapper(base) {
    init { filesDir.mkdirs(); noBackupFilesDir.mkdirs(); cacheDir.mkdirs() }
    override fun getApplicationContext(): Context = this
    override fun getFilesDir() = File(root, "files")
    override fun getNoBackupFilesDir() = File(root, "no_backup")
    override fun getCacheDir() = File(root, "cache")
    override fun getDatabasePath(name: String) = if (File(name).isAbsolute) File(name) else File(root, "databases/$name").apply { parentFile!!.mkdirs() }
}

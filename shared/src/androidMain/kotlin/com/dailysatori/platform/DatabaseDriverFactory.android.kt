package com.dailysatori.platform

import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.service.security.*
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

internal object SqlCipherRuntime {
    @Synchronized fun load() {
        if (!loaded) { System.loadLibrary("sqlcipher"); loaded = true }
    }
    private var loaded = false
}

actual class DatabaseDriverFactory(private val context: PlatformContext) {
    actual fun createDriver(): SqlDriver = createDriver("daily_satori.db")
    actual fun createDriver(name: String): SqlDriver = createEncryptedDriver(name,
        DatabaseKeyStore(context).readExisting() ?: throw DatabaseSecurityException())

    actual fun createEncryptedDriver(name: String, key: DatabaseKey): SqlDriver {
        SqlCipherRuntime.load()
        val path = databasePath(name)
        if (File(path).exists()) return openExisting(path, key)
        val password = key.sqlCipherPassword()
        return try {
            retainDatabasePassword(AndroidSqliteDriver(DailySatoriDatabase.Schema, context.context, name,
                factory = SupportOpenHelperFactory(password),
                callback = object : AndroidSqliteDriver.Callback(DailySatoriDatabase.Schema) {
                    override fun onOpen(db: SupportSQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
                }), password)
        } catch (failure: Throwable) { password.fill(0); throw failure }
    }

    actual fun createBackupDriver(name: String): SqlDriver = createLegacyDriver(name)
    actual fun createBackupDriver(name: String, key: DatabaseKey?): SqlDriver =
        if (key == null) createLegacyDriver(name) else openExisting(databasePath(name), key)

    actual fun createLegacyDriver(name: String): SqlDriver {
        val path = databasePath(name)
        check(isPlaintextDatabase(File(path))) { "不是可恢复的历史数据库" }
        return openExisting(path, null)
    }

    actual fun readDatabaseKey(): DatabaseKey = DatabaseKeyStore(context).readExisting() ?: throw DatabaseSecurityException()
    actual fun wrapDatabaseKey(key: DatabaseKey): ByteArray = DatabaseKeyStore(context).wrap(key)
    actual fun encryptLegacyDatabase(name: String, key: DatabaseKey) {
        val source = File(databasePath(name))
        check(source.canonicalFile != context.context.getDatabasePath("daily_satori.db").canonicalFile)
        check(isPlaintextDatabase(source)) { "历史备份不是明文数据库" }
        val candidate = File(source.path + ".encrypted")
        listOf(candidate, File(candidate.path + "-wal"), File(candidate.path + "-shm"), File(candidate.path + "-journal")).forEach {
            if (it.exists()) check(it.delete())
        }
        try {
            DatabaseEncryptionMigration(context).convertLegacy(source.path, candidate.path, key)
            listOf("-wal", "-shm", "-journal").forEach { suffix ->
                val sidecar = File(source.path + suffix)
                if (sidecar.exists()) check(sidecar.delete())
            }
            java.nio.file.Files.move(candidate.toPath(), source.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally {
            listOf(candidate, File(candidate.path + "-wal"), File(candidate.path + "-shm"), File(candidate.path + "-journal")).forEach { it.delete() }
        }
    }

    actual fun createInMemoryDriver(): SqlDriver = AndroidSqliteDriver(
        DailySatoriDatabase.Schema, context.context, name = null,
    )

    private fun databasePath(name: String): String =
        if (File(name).isAbsolute) name else context.context.getDatabasePath(name).absolutePath

    private fun openExisting(path: String, key: DatabaseKey?): SqlDriver {
        check(File(path).isFile) { "数据库文件不存在" }
        SqlCipherRuntime.load()
        val password = key?.sqlCipherPassword() ?: byteArrayOf()
        try {
            val database = try { SQLiteDatabase.openDatabase(path, password, null, SQLiteDatabase.OPEN_READWRITE, null as SQLiteDatabaseHook?) }
            catch (_: Exception) { throw DatabaseSecurityException("数据库无法解锁或已损坏") }
            return try {
                database.rawQuery("SELECT count(*) FROM sqlite_master", emptyArray<String>()).use { check(it.moveToFirst()) }
                database.setForeignKeyConstraintsEnabled(true)
                retainDatabasePassword(AndroidSqliteDriver(database), password)
            } catch (_: Exception) { database.close(); throw DatabaseSecurityException("数据库无法解锁或已损坏") }
        } catch (failure: Throwable) { password.fill(0); throw failure }
    }
}

/** SQLCipher 4.19.1 retains this exact array in its connection pool; wipe only after pool closure. */
internal fun retainDatabasePassword(driver: SqlDriver, password: ByteArray): SqlDriver = object : SqlDriver by driver {
    override fun close() {
        try { driver.close() } finally { password.fill(0) }
    }
}

/** No DI, schema callbacks, alias generation or migration in the independent recovery process. */
fun readRecoverySettings(context: PlatformContext): Map<String, String?> {
    val file = context.context.getDatabasePath("daily_satori.db")
    if (!file.isFile) throw DatabaseSecurityException("数据库文件不存在")
    SqlCipherRuntime.load()
    val password = if (isPlaintextDatabase(file)) byteArrayOf() else
        (DatabaseKeyStore(context).readExisting() ?: throw DatabaseSecurityException()).sqlCipherPassword()
    try {
        val database = SQLiteDatabase.openDatabase(file.path, password, null, SQLiteDatabase.OPEN_READONLY, null as SQLiteDatabaseHook?)
        try {
            return database.rawQuery("SELECT key, value FROM setting WHERE key IN (?, ?)",
                arrayOf("update_channel", "schema_version")).use { cursor ->
                buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1)) }
            }
        } finally { database.close() }
    } finally { password.fill(0) }
}

internal fun isPlaintextDatabase(file: File): Boolean = file.isFile && file.inputStream().use {
    input -> ByteArray(16) { input.read().toByte() }.contentEquals("SQLite format 3\u0000".toByteArray())
}

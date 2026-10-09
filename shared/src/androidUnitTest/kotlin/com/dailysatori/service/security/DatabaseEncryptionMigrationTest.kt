package com.dailysatori.service.security

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.*
import com.dailysatori.service.backup.exportEncryptedDatabase
import java.io.File
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.test.*
import org.junit.Assume.assumeTrue

/** Runs the production export/validation SQL against the real host SQLCipher, not JDBC SQLite. */
class DatabaseEncryptionMigrationTest {
    @Test fun everyRegisteredLegacyFieldIsConvertedInsideARealEncryptedDatabase() {
        val binary = System.getenv("SQLCIPHER_BIN")
        assumeTrue("Requires real host SQLCipher", binary != null && File(binary).canExecute())
        val root = createTempDirectory().toFile()
        try {
            val source = File(root, "legacy.db")
            val target = File(root, "converted.db")
            val key = DatabaseKey.generate()
            val fields = SecretFieldRegistry.fields
            HostSqlCipherDriver(binary!!, source).use { driver ->
                fields.groupBy { it.table }.forEach { (table, specs) ->
                    val columns = (specs.map { it.column } + if (table == "setting") listOf("key") else emptyList()).distinct()
                    driver.run("CREATE TABLE $table (${columns.joinToString { "$it TEXT" }})")
                }
                fields.forEachIndexed { index, field ->
                    val plain = "native-legacy-private-token-$index"
                    val settingKey = field.whereClause?.substringAfter("key = '")?.substringBeforeLast("'")
                    val columns = field.column + if (settingKey != null) ",key" else ""
                    driver.execute(null, "INSERT INTO ${field.table}($columns) VALUES(?${if (settingKey != null) ",?" else ""})", if (settingKey != null) 2 else 1) {
                        bindString(0, LegacyProbeCipher.encrypt(plain))
                        if (settingKey != null) bindString(1, settingKey)
                    }
                }
                exportEncryptedDatabase(driver, target.path, key)
                HostSqlCipherDriver(binary!!, target).use { converted ->
                    converted.run("PRAGMA key = \"${key.sqlCipherPassword().decodeToString()}\"")
                    assertEquals(fields.size, SecretFieldProcessor(converted, LegacyProbeCipher).decryptLegacyFields().updated)
                    fields.forEachIndexed { index, field ->
                        val query = "SELECT ${field.column} FROM ${field.table}" + (field.whereClause?.let { " WHERE $it" } ?: "")
                        assertEquals(listOf(listOf("native-legacy-private-token-$index")), converted.run(query))
                        assertTrue(LegacyProbeCipher.isEncrypted(driver.run(query).single().single()))
                    }
                    validate(converted)
                }
            }
            fields.indices.forEach { assertFalse(target.readBytes().toString(Charsets.ISO_8859_1).contains("native-legacy-private-token-$it")) }
        } finally { root.deleteRecursively() }
    }

    @Test fun encryptedExportKeepsCommittedWalDataSchemaAndVersionWithoutPlaintextOutput() {
        val binary = System.getenv("SQLCIPHER_BIN")
        assumeTrue("Requires real host SQLCipher", binary != null && File(binary).canExecute())
        val root = createTempDirectory().toFile()
        try {
            val source = File(root, "old.db")
            val target = File(root, "encrypted.db")
            val key = DatabaseKey.fromHex("10".repeat(32))
            HostSqlCipherDriver(binary!!, source).use { driver ->
                driver.run("PRAGMA journal_mode=WAL; CREATE TABLE setting(key TEXT PRIMARY KEY, value TEXT); CREATE INDEX value_idx ON setting(value); CREATE TRIGGER ignore_empty BEFORE INSERT ON setting WHEN NEW.key='' BEGIN SELECT RAISE(ABORT,'empty'); END; INSERT INTO setting VALUES('private','encryption-marker-893771'); PRAGMA user_version=0;")
                assertTrue(File(source.path + "-wal").isFile)
                exportEncryptedDatabase(driver, target.path, key)
                assertEquals(listOf(listOf("encryption-marker-893771")), driver.run("SELECT value FROM setting"))
            }
            assertFalse(target.readBytes().toString(Charsets.ISO_8859_1).contains("encryption-marker-893771"))
            assertFalse(com.dailysatori.platform.isPlaintextDatabase(target))
            HostSqlCipherDriver(binary!!, target).use { driver ->
                driver.run("PRAGMA key = \"${key.sqlCipherPassword().decodeToString()}\"")
                validate(driver)
                assertEquals(listOf(listOf("0")), driver.run("PRAGMA user_version"))
                assertEquals(listOf(listOf("encryption-marker-893771")), driver.run("SELECT value FROM setting"))
                assertEquals(listOf(listOf("2")), driver.run("SELECT count(*) FROM sqlite_master WHERE name IN ('value_idx','ignore_empty')"))
                assertFails { driver.run("INSERT INTO setting VALUES('','bad')") }
            }
            HostSqlCipherDriver(binary!!, target).use { driver -> assertFails { driver.run("SELECT * FROM setting") } }
            HostSqlCipherDriver(binary!!, target).use { driver ->
                driver.run("PRAGMA key = \"x'${"22".repeat(32)}'\"")
                assertFails { driver.run("SELECT * FROM setting") }
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun portableBackupUsesTheSameKeyAndPreservesNonzeroVersion() {
        val binary = System.getenv("SQLCIPHER_BIN")
        assumeTrue("Requires real host SQLCipher", binary != null && File(binary).canExecute())
        val root = createTempDirectory().toFile()
        try {
            val key = DatabaseKey.generate()
            val source = File(root, "source.db")
            val backup = File(root, "backup.db")
            HostSqlCipherDriver(binary!!, source).use { driver ->
                driver.run("PRAGMA key = \"${key.sqlCipherPassword().decodeToString()}\"; CREATE TABLE data(value TEXT); INSERT INTO data VALUES('portable-value'); PRAGMA user_version=7;")
                exportEncryptedDatabase(driver, backup.path, key)
            }
            val portable = DatabaseKey.fromPortableJson(key.portableJson())
            HostSqlCipherDriver(binary!!, backup).use { driver ->
                driver.run("PRAGMA key = \"${portable.sqlCipherPassword().decodeToString()}\"")
                validate(driver)
                assertEquals(listOf(listOf("7")), driver.run("PRAGMA user_version"))
                assertEquals(listOf(listOf("portable-value")), driver.run("SELECT value FROM data"))
            }
        } finally { root.deleteRecursively() }
    }
}

/** Host-only AES-GCM field fixture; the Android Keystore/legacy envelope still needs device verification. */
private object LegacyProbeCipher : SecretValueCipher {
    private val key = javax.crypto.spec.SecretKeySpec(ByteArray(32) { 17 }, "AES")
    override fun isEncrypted(value: String) = value.startsWith(SecretCipherPrefix)
    override fun encrypt(value: String): String {
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
        return SecretCipherPrefix + java.util.Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(value.encodeToByteArray()))
    }
    override fun decrypt(value: String): String {
        val bytes = java.util.Base64.getDecoder().decode(value.removePrefix(SecretCipherPrefix))
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).decodeToString()
    }
}

private class HostSqlCipherDriver(binary: String, database: File) : SqlDriver {
    private val process = ProcessBuilder(binary, "-batch", database.path).redirectErrorStream(true).start()
    private val input = process.outputStream.bufferedWriter()
    private val output = process.inputStream.bufferedReader()
    private var transaction: Transacter.Transaction? = null
    init { input.write(".bail on\n.mode list\n.separator \"\t\"\n.headers off\n"); input.flush() }

    fun run(sql: String): List<List<String>> {
        val end = "END_" + UUID.randomUUID().toString().replace("-", "")
        input.write(sql.trimEnd(';') + ";\n.print $end\n"); input.flush()
        val result = mutableListOf<List<String>>()
        while (true) {
            val line = output.readLine() ?: error("Host SQLCipher rejected the operation")
            if (line == end) return result
            if (line.startsWith("Parse error") || line.startsWith("Runtime error")) error("Host SQLCipher rejected the operation")
            result += line.split('\t')
        }
    }
    override fun execute(identifier: Int?, sql: String, parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<Long> {
        run(bind(sql, parameters, binders)); return QueryResult.Value(0L)
    }
    override fun <R> executeQuery(identifier: Int?, sql: String, mapper: (SqlCursor) -> QueryResult<R>, parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<R> {
        val rows = run(bind(sql, parameters, binders))
        val cursor = object : SqlCursor {
            var position = -1
            override fun next() = QueryResult.Value(++position < rows.size)
            override fun getString(index: Int) = rows[position][index]
            override fun getLong(index: Int) = getString(index).toLongOrNull()
            override fun getDouble(index: Int) = getString(index).toDoubleOrNull()
            override fun getBoolean(index: Int) = getLong(index)?.let { it != 0L }
            override fun getBytes(index: Int) = error("Unused blob query")
        }
        return mapper(cursor)
    }
    override fun newTransaction(): QueryResult<Transacter.Transaction> {
        val parent = transaction
        if (parent == null) run("BEGIN")
        val next = object : Transacter.Transaction() {
            override val enclosingTransaction = parent
            override fun endTransaction(successful: Boolean): QueryResult<Unit> {
                if (parent == null) run(if (successful) "COMMIT" else "ROLLBACK")
                transaction = parent
                return QueryResult.Value(Unit)
            }
        }
        transaction = next
        return QueryResult.Value(next)
    }
    override fun currentTransaction() = transaction
    override fun addListener(vararg queryKeys: String, listener: Query.Listener) = Unit
    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) = Unit
    override fun notifyListeners(vararg queryKeys: String) = Unit
    override fun close() { input.close(); process.destroy(); output.close() }

    private fun bind(sql: String, size: Int, binders: (SqlPreparedStatement.() -> Unit)?): String {
        val values = Array(size) { "NULL" }
        val statement = object : SqlPreparedStatement {
            override fun bindString(index: Int, string: String?) { values[index] = string?.let { "'${it.replace("'", "''")}'" } ?: "NULL" }
            override fun bindLong(index: Int, long: Long?) { values[index] = long?.toString() ?: "NULL" }
            override fun bindDouble(index: Int, double: Double?) { values[index] = double?.toString() ?: "NULL" }
            override fun bindBoolean(index: Int, boolean: Boolean?) = bindLong(index, boolean?.let { if (it) 1 else 0 })
            override fun bindBytes(index: Int, bytes: ByteArray?) = error("Unused blob binding")
        }
        binders?.invoke(statement)
        var index = 0
        return sql.replace(Regex("\\?")) { values[index++] }
    }
}

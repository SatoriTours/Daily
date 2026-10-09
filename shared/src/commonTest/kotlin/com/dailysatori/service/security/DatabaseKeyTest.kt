package com.dailysatori.service.security

import kotlin.test.*

class DatabaseKeyTest {
    private val hex = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"

    @Test fun rawKeyAndPortableRestorePreserveTheSameDatabasePassword() {
        val key = DatabaseKey.fromHex(hex.uppercase())
        assertEquals("x'$hex'", key.sqlCipherPassword().decodeToString())
        assertContentEquals(key.sqlCipherPassword(), DatabaseKey.fromPortableJson(key.portableJson()).sqlCipherPassword())
        assertFalse(key.toString().contains(hex))
    }

    @Test fun malformedKeysAndUnsupportedPortableFormatsAreRejected() {
        listOf("", hex.drop(1), hex + "0", "z".repeat(64)).forEach {
            assertFailsWith<IllegalArgumentException> { DatabaseKey.fromHex(it) }
        }
        val json = """{"version":1,"encoding":"raw-256-hex","keyHex":"$hex","cipherCompatibility":4}"""
        assertEquals("x'$hex'", DatabaseKey.fromPortableJson(json).sqlCipherPassword().decodeToString())
        listOf(json.replace("\"version\":1", "\"version\":2"),
            json.replace("raw-256-hex", "passphrase"), json.replace("Compatibility\":4", "Compatibility\":3"),
            json.replace(hex, "0"), json.dropLast(1) + ",\"extra\":1}", " ".repeat(1025)).forEach {
            assertFails { DatabaseKey.fromPortableJson(it) }
        }
    }

    @Test fun failedNativeExportCannotExposeAKeyInItsException() {
        val jdbc = app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver(app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver.IN_MEMORY)
        val folder = java.nio.file.Files.createTempDirectory("export-privacy").toFile()
        var invoked = false
        try {
            val key = DatabaseKey.fromHex(hex)
            val faulty = object : app.cash.sqldelight.db.SqlDriver by jdbc {
                override fun execute(identifier: Int?, sql: String, parameters: Int,
                    binders: (app.cash.sqldelight.db.SqlPreparedStatement.() -> Unit)?): app.cash.sqldelight.db.QueryResult<Long> {
                    invoked = true
                    error("native binding error ${key.sqlCipherPassword().decodeToString()}")
                }
            }
            val failure = assertFails { com.dailysatori.service.backup.exportEncryptedDatabase(faulty, java.io.File(folder, "output.db").path, key) }
            assertTrue(invoked)
            assertFalse(failure.toString().contains(hex))
            assertNull(failure.cause)
        } finally { jdbc.close(); folder.deleteRecursively() }
    }

    @Test fun generatedKeysAreDifferentAndExactly256Bits() {
        val first = DatabaseKey.generate()
        assertEquals(67, first.sqlCipherPassword().size)
        assertFalse(first.sqlCipherPassword().contentEquals(DatabaseKey.generate().sqlCipherPassword()))
    }
}

package com.dailysatori.service.security

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.platform.retainDatabasePassword
import net.zetetic.database.sqlcipher.SQLiteDatabaseConfiguration
import kotlin.test.*

class DatabasePasswordLifetimeTest {
    @Test fun sqlCipherPoolBorrowsPasswordUntilDriverCloses() {
        val password = DatabaseKey.generate().sqlCipherPassword()
        val expected = password.copyOf()
        val config = SQLiteDatabaseConfiguration("test.db", 0, password, null)
        val pooledConfig = SQLiteDatabaseConfiguration(config)
        // Actual SQLCipher 4.19.1 configuration copies retain the same array, not a defensive copy.
        assertSame(password, pooledConfig.password)
        val jdbc = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val underlying = object : SqlDriver by jdbc {
            override fun close() {
                assertContentEquals(expected, pooledConfig.password, "The pool needs the key until closure")
                jdbc.close()
            }
        }
        val driver = retainDatabasePassword(underlying, password)
        assertContentEquals(expected, pooledConfig.password, "Reopened/WAL connections must still unlock")
        driver.close()
        assertTrue(password.all { it == 0.toByte() })
    }

    @Test fun closeFailureStillClearsTheBorrowedPassword() {
        val password = byteArrayOf(1, 2, 3)
        val jdbc = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val underlying = object : SqlDriver by jdbc {
            override fun close() { jdbc.close(); error("close failed") }
        }
        val driver = retainDatabasePassword(underlying, password)
        assertFails { driver.close() }
        assertTrue(password.all { it == 0.toByte() })
    }
}

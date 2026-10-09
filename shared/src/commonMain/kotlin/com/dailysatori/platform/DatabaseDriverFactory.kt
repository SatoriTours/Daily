package com.dailysatori.platform

import app.cash.sqldelight.db.SqlDriver
import com.dailysatori.service.security.DatabaseKey

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
    fun createDriver(name: String): SqlDriver
    fun createBackupDriver(name: String): SqlDriver
    fun createBackupDriver(name: String, key: DatabaseKey?): SqlDriver
    fun createEncryptedDriver(name: String, key: DatabaseKey): SqlDriver
    fun createLegacyDriver(name: String): SqlDriver
    fun readDatabaseKey(): DatabaseKey
    fun wrapDatabaseKey(key: DatabaseKey): ByteArray
    fun encryptLegacyDatabase(name: String, key: DatabaseKey)
    fun createInMemoryDriver(): SqlDriver
}

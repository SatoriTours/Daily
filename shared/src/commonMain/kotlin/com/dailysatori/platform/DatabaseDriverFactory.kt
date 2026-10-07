package com.dailysatori.platform

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
    fun createDriver(name: String): SqlDriver
    fun createBackupDriver(name: String): SqlDriver
    fun createInMemoryDriver(): SqlDriver
}

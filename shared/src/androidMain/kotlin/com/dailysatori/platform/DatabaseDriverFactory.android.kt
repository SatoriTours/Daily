package com.dailysatori.platform

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.dailysatori.shared.db.DailySatoriDatabase

actual class DatabaseDriverFactory(private val context: PlatformContext) {
    actual fun createDriver(): SqlDriver =
        createDriver("daily_satori.db")

    actual fun createDriver(name: String): SqlDriver =
        AndroidSqliteDriver(DailySatoriDatabase.Schema, context.context, name)
}

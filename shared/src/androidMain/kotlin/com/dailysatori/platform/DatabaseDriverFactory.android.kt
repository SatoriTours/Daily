package com.dailysatori.platform

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.dailysatori.shared.db.DailySatoriDatabase

actual class DatabaseDriverFactory(private val context: PlatformContext) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(DailySatoriDatabase.Schema, context.context, "daily_satori.db")
}

package com.dailysatori.platform

import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.dailysatori.shared.db.DailySatoriDatabase

actual class DatabaseDriverFactory(private val context: PlatformContext) {
    actual fun createBackupDriver(name: String): SqlDriver = AndroidSqliteDriver(
        schema = DailySatoriDatabase.Schema,
        context = context.context,
        name = name,
        callback = object : AndroidSqliteDriver.Callback(DailySatoriDatabase.Schema) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                // Legacy databases may have user_version=0; inspect them without creating any tables.
                check(db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='setting'").use { it.moveToFirst() }) {
                    "备份数据库未初始化"
                }
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                error("备份数据库版本不兼容")
            }
        },
    )

    actual fun createInMemoryDriver(): SqlDriver = AndroidSqliteDriver(
        schema = DailySatoriDatabase.Schema, context = context.context, name = null,
    )

    actual fun createDriver(): SqlDriver =
        createDriver("daily_satori.db")

    actual fun createDriver(name: String): SqlDriver =
        AndroidSqliteDriver(
            schema = DailySatoriDatabase.Schema,
            context = context.context,
            name = name,
            callback = object : AndroidSqliteDriver.Callback(DailySatoriDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    db.setForeignKeyConstraintsEnabled(true)
                }
            },
        )
}

package com.dailysatori.service.security

import app.cash.sqldelight.db.SqlDriver
import com.dailysatori.config.DatabaseConfig
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.migration.DatabaseMigration
import com.dailysatori.service.migration.DatabaseSchemaInventory
import com.dailysatori.service.migration.OpportunityArchiveMigration
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.service.diagnostics.PerformancePoint.*
import com.dailysatori.service.diagnostics.databasePhase
import java.io.File
import com.dailysatori.platform.DatabaseDriverFactory
import com.dailysatori.platform.PlatformContext
import com.dailysatori.service.backup.exportEncryptedDatabase

/** Sole owner: the bootstrap initialization lock, before any business readers or writers. */
internal class DatabaseSchemaUpgrade(
    private val root: File,
    private val database: File,
    private val keyFile: File,
    private val open: (File) -> SqlDriver,
    private val export: (SqlDriver, File) -> Unit,
    private val migrate: (SqlDriver) -> Unit = { driver ->
        DatabaseMigration(driver, SettingRepository(DailySatoriDatabase(driver))).runMigrations()
    },
    private val availableBytes: () -> Long = { database.parentFile!!.usableSpace },
    private val checkDatabase: (SqlDriver) -> Unit = ::validate,
) {
    private val candidate get() = File(database.path + ".schema-candidate")
    private fun transaction() = DatabaseInstallTransaction(root, database, keyFile,
        validateInstalled = { open(database).useDriver(::verifyInstalled) })

    fun recoverPending() = transaction().applyPending()

    fun upgrade(onPreparationRequired: () -> Unit = {}) {
        recoverPending()
        check(database.isFile && keyFile.isFile) { "数据库或密钥缺失，待升级数据已保留" }
        var before: MigrationContentSnapshot? = null
        var sourceVersion: String? = null
        var migratingOpportunities = false
        try {
            open(database).useDriver { source ->
                sourceVersion = version(source)
                if (sourceVersion?.toLongOrNull() == DatabaseConfig.currentSchemaVersion &&
                    DatabaseSchemaInventory.missingObjects(source).isEmpty()) {
                    databasePhase(DB_VALIDATE) { DatabaseSchemaInventory.validate(source) }
                    return
                }
                requireSupportedVersion(sourceVersion)
                onPreparationRequired()
                if (sourceVersion == null) databasePhase(DB_VALIDATE) { DatabaseSchemaInventory.validate(source) }
                requireSpace()
                cleanCandidate()
                before = databasePhase(DB_SNAPSHOT) {
                    val settings = SettingRepository(DailySatoriDatabase(source))
                    migratingOpportunities = sourceVersion?.toLongOrNull()?.let { it < 55 }
                        ?: OpportunityArchiveMigration.hasLegacyData(settings)
                    val opportunities = if (migratingOpportunities) OpportunityArchiveMigration.read(settings) else null
                    MigrationContentSnapshot.capture(source, opportunities)
                }
                databasePhase(DB_COPY) { export(source, candidate) }
            }
            val expected = checkNotNull(before)
            open(candidate).useDriver { target ->
                databasePhase(DB_MIGRATE) { migrate(target) }
                verifyInstalled(target)
                databasePhase(DB_VALIDATE) {
                    expected.requirePreserved(target, legacy = (sourceVersion?.toLongOrNull() ?: 38) < 38,
                        migratedImages = sourceVersion?.toLongOrNull()?.let { it < 40 } == true,
                        migratedTags = sourceVersion?.toLongOrNull()?.let { it < 41 } == true,
                        migratedReminders = sourceVersion?.toLongOrNull()?.let { it < 42 } == true,
                        migratedIdeas = sourceVersion?.toLongOrNull()?.let { it < 43 } == true,
                        migratedAssistant = sourceVersion?.toLongOrNull()?.let { it < 44 } == true,
                        migratedRetirement = sourceVersion?.toLongOrNull()?.let { it < 45 } == true,
                        migratedOpportunities = migratingOpportunities)
                }
            }
            open(database).useDriver { source ->
                check(version(source) == sourceVersion) { "升级期间原数据库版本发生变化" }
                databasePhase(DB_VALIDATE) { expected.requirePreserved(source) }
            }
            transaction().stage(candidate, keyFile.readBytes())
            transaction().applyPending()
        } finally {
            // Missing source can mean this is the only recoverable copy: do not erase it.
            if (before != null && database.isFile) cleanCandidate()
        }
    }

    private fun verifyInstalled(driver: SqlDriver) = databasePhase(DB_VALIDATE) {
        checkDatabase(driver)
        DatabaseSchemaInventory.validate(driver)
        check(version(driver) == DatabaseConfig.currentSchemaVersion.toString()) { "数据库升级未完成" }
    }

    private fun version(driver: SqlDriver): String? =
        SettingRepository(DailySatoriDatabase(driver)).get(SettingKeys.schemaVersion)

    private fun requireSupportedVersion(value: String?) {
        check(value == null || value.toLongOrNull()?.let { it in 0..DatabaseConfig.currentSchemaVersion } == true) {
            "数据库版本无效或较新，原有数据已保留"
        }
    }

    private fun requireSpace() {
        val size = database.length() + File(database.path + "-wal").length()
        check(size <= (Long.MAX_VALUE - SPACE_RESERVE) / 3 && availableBytes() >= size * 3 + SPACE_RESERVE) {
            "数据库升级空间不足，原有数据已保留"
        }
    }

    private fun cleanCandidate() {
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            val file = File(candidate.path + suffix)
            if (file.exists()) check(file.delete()) { "数据库升级临时文件清理失败" }
        }
    }

    companion object {
        private const val SPACE_RESERVE = 16L * 1024 * 1024

        fun forContext(context: PlatformContext): DatabaseSchemaUpgrade {
            val factory = DatabaseDriverFactory(context)
            val keys = DatabaseKeyStore(context)
            fun key() = databasePhase(DB_KEY) { keys.readExisting() ?: throw DatabaseSecurityException() }
            return DatabaseSchemaUpgrade(
                File(context.context.noBackupFilesDir, "database-schema-install"),
                context.context.getDatabasePath("daily_satori.db"), File(keys.storagePath()),
                open = { factory.createBackupDriver(it.path, key()) },
                export = { driver, target -> exportEncryptedDatabase(driver, target.path, key()) },
            )
        }
    }
}

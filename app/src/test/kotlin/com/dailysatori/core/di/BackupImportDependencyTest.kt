package com.dailysatori.core.di

import android.content.Context
import android.content.ContextWrapper
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.di.sharedModule
import com.dailysatori.core.lifearchive.EncryptedLifeArchiveRepository
import com.dailysatori.platform.FileManager
import com.dailysatori.service.backup.BackupService
import com.dailysatori.service.backup.LifeArchiveBackup
import com.dailysatori.service.import.ImportService
import com.dailysatori.service.lifearchive.LifeArchiveRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import java.io.File
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.core.definition.indexKey
import kotlin.test.Test
import kotlin.test.assertSame

@OptIn(org.koin.core.annotation.KoinInternalApi::class)
class BackupImportDependencyTest {
    @Test
    fun backupAndImportResolveThroughProductionModulesWithoutRecursion() = withApplication(mergeArchiveTypes = false)

    @Test
    fun mergedArchiveTypesResolveWithoutAnAliasRequestingItself() = withApplication(mergeArchiveTypes = true)

    private fun withApplication(mergeArchiveTypes: Boolean) {
        // Only Android's stub constructor is bypassed; all production DI factories are exercised.
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val context = unsafeClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, TestContext::class.java) as TestContext
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val application = koinApplication {
                modules(platformModule, sharedModule, appModule, module {
                    single<Context> { context }
                    single<SqlDriver> { driver }
                    if (mergeArchiveTypes) {
                        // R8 rewrites all three class literals to the same class: the last mapping wins.
                        val factory = appModule.mappings.values.last { it.beanDefinition.hasType(LifeArchiveBackup::class) }
                        listOf(LifeArchiveRepository::class, LifeArchiveBackup::class, EncryptedLifeArchiveRepository::class)
                            .forEach { type -> mappings[indexKey(type, null, factory.beanDefinition.scopeQualifier)] = factory }
                    }
                })
            }
            try {
                val koin = application.koin
                assertSame(koin.get<FileManager>(), koin.get<FileManager>())
                assertSame(koin.get<BackupService>(), koin.get<BackupService>())
                assertSame(koin.get<ImportService>(), koin.get<ImportService>())
                assertSame<Any>(koin.get<LifeArchiveRepository>(), koin.get<LifeArchiveBackup>())
            } finally { application.close() }
        }
    }

    private class TestContext : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir() = File(System.getProperty("java.io.tmpdir"), "daily-backup-di/files")
        override fun getNoBackupFilesDir() = File(System.getProperty("java.io.tmpdir"), "daily-backup-di/no-backup")
    }
}

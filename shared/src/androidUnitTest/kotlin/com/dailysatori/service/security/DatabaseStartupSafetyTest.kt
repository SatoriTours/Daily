package com.dailysatori.service.security

import android.content.Context
import android.content.ContextWrapper
import com.dailysatori.platform.PlatformContext
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class DatabaseStartupSafetyTest {
    @Test fun missingKeyCannotReplaceExistingDatabaseAndInterruptedBackupPlaintextIsCleaned() = withContext { context ->
        val database = context.getDatabasePath("daily_satori.db").apply { parentFile.mkdirs(); writeText("existing encrypted database") }
        val cache = context.cacheDir.apply { mkdirs() }
        val temporary = File(cache, "temp_2026-10-09-12-00-00").apply { mkdirs() }
        File(temporary, "database_key.json").writeText("temporary portable key")
        val zip = File(cache, "daily_satori_backup_2026-10-09-12-00-00.zip").apply { writeText("private plaintext archive") }
        val user = File(cache, "unrelated-user-file").apply { writeText("keep") }
        assertFailsWith<DatabaseSecurityException> { DatabaseBootstrap.prepare(PlatformContext(context)) }
        assertEquals("existing encrypted database", database.readText())
        assertFalse(File(context.noBackupFilesDir, "database_key.sec").exists())
        assertFalse(temporary.exists())
        assertFalse(zip.exists())
        assertEquals("keep", user.readText())
    }

    @Test fun missingActiveDatabaseCannotEraseAnExistingCandidateOrWrappedKey() = withContext { context ->
        val candidate = context.getDatabasePath("daily_satori.encrypted-candidate").apply { parentFile.mkdirs(); writeText("possibly recoverable data") }
        assertFailsWith<DatabaseSecurityException> { DatabaseBootstrap.prepare(PlatformContext(context)) }
        assertEquals("possibly recoverable data", candidate.readText())
        assertFalse(context.getDatabasePath("daily_satori.db").exists())
    }

    private fun withContext(block: (TestContext) -> Unit) {
        val root = createTempDirectory().toFile()
        try {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
            val context = unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, TestContext::class.java) as TestContext
            context.root = root
            block(context)
        } finally { root.deleteRecursively() }
    }
    private class TestContext : ContextWrapper(null) {
        lateinit var root: File
        override fun getApplicationContext(): Context = this
        override fun getFilesDir() = File(root, "files")
        override fun getNoBackupFilesDir() = File(root, "private")
        override fun getCacheDir() = File(root, "cache")
        override fun getDatabasePath(name: String) = File(root, "databases/$name")
    }
}

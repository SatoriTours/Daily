package com.dailysatori.core.diagnostics

import com.dailysatori.core.service.*
import com.dailysatori.service.security.DatabaseSecurityException
import kotlinx.coroutines.CancellationException
import kotlin.test.*

class RecoveryEncryptedDatabaseTest {
    private val installed = InstalledBuild(100, UpdateChannel.COMMIT, 27)

    @Test fun absentDatabaseUsesDefaultsWithoutGeneratingAReplacementKey() {
        val root = kotlin.io.path.createTempDirectory().toFile()
        try {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
            val context = unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, MissingDatabaseContext::class.java) as MissingDatabaseContext
            context.root = root
            val config = readRecoveryUpdateConfiguration(context, installed)
            assertTrue(config.usedDefaults)
            assertEquals(UpdateChannel.COMMIT, config.channel)
            assertTrue(root.listFiles().orEmpty().isEmpty())
        } finally { root.deleteRecursively() }
    }
    private class MissingDatabaseContext : android.content.ContextWrapper(null) {
        lateinit var root: java.io.File
        override fun getDatabasePath(name: String) = java.io.File(root, name)
    }

    @Test fun keyAndNativeFailuresNeverPreventIndependentRecoveryUpdates() {
        for (failure in listOf(DatabaseSecurityException(), UnsatisfiedLinkError("sqlcipher missing"), IllegalStateException("invalid db"))) {
            val config = loadRecoveryUpdateConfiguration(installed) { throw failure }
            assertEquals(UpdateChannel.COMMIT, config.channel)
            assertEquals(27L, config.schemaVersion)
            assertTrue(config.usedDefaults)
        }
    }
    @Test fun readOnlySettingsPreserveHigherSchemaAndCancellationIsNotSwallowed() {
        val config = loadRecoveryUpdateConfiguration(installed) { mapOf("update_channel" to "stable", "schema_version" to "99") }
        assertEquals(UpdateChannel.STABLE, config.channel)
        assertEquals(99L, config.schemaVersion)
        assertFalse(config.usedDefaults)
        assertFailsWith<CancellationException> { loadRecoveryUpdateConfiguration(installed) { throw CancellationException() } }
    }
}

package com.dailysatori.service.security

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class DatabaseInstallTransactionTest {
    @Test fun databaseAndKeyRollbackTogetherAtEveryReplacementBoundary() {
        for (failureAt in 1..8) {
            val root = createTempDirectory().toFile()
            try {
                val db = File(root, "db").apply { writeText("old-db") }
                val key = File(root, "key").apply { writeText("old-key") }
                File("${db.path}-wal").writeText("old-wal")
                val candidate = File(root, "candidate").apply { writeText("new-db") }
                val pending = File(root, "pending")
                var moves = 0
                val txn = DatabaseInstallTransaction(pending, db, key) { source, target ->
                    if (++moves == failureAt) throw Interrupted()
                    target.parentFile?.mkdirs()
                    Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                }
                try { txn.stage(candidate, "new-key".toByteArray()); txn.applyPending() } catch (_: Interrupted) {}
                DatabaseInstallTransaction(pending, db, key).applyPending()
                assertTrue((db.readText() == "old-db" && key.readText() == "old-key") ||
                    (db.readText() == "new-db" && key.readText() == "new-key"), "boundary=$failureAt")
                if (db.readText() == "old-db") assertEquals("old-wal", File("${db.path}-wal").readText())
            } finally { root.deleteRecursively() }
        }
    }
    @Test fun installationDoesNotTouchAttachmentsAndIsIdempotent() {
        val root = createTempDirectory().toFile()
        try {
            val db = File(root, "db")
            val key = File(root, "key")
            val media = File(root, "audio").apply { writeText("media") }
            val candidate = File(root, "candidate").apply { writeText("encrypted") }
            val transaction = DatabaseInstallTransaction(File(root, "pending"), db, key)
            transaction.stage(candidate, "wrapped-key".toByteArray())
            transaction.applyPending(); transaction.applyPending()
            assertEquals("encrypted", db.readText())
            assertEquals("wrapped-key", key.readText())
            assertEquals("media", media.readText())
        } finally { root.deleteRecursively() }
    }
    @Test fun rejectedInstalledDatabaseRollsBackKeyAndSidecarsBeforeCommit() {
        val root = createTempDirectory().toFile()
        try {
            val db = File(root, "db").apply { writeText("old-db") }
            val key = File(root, "key").apply { writeText("old-key") }
            File("${db.path}-wal").writeText("old-wal")
            val pending = File(root, "pending")
            val candidate = File(root, "candidate").apply { writeText("bad-db") }
            val transaction = DatabaseInstallTransaction(pending, db, key, validateInstalled = {
                assertEquals("bad-db", db.readText())
                assertEquals("new-key", key.readText())
                File("${db.path}-shm").writeText("new-shm")
                error("candidate validation failed")
            })
            transaction.stage(candidate, "new-key".toByteArray())
            assertFailsWith<DatabaseSecurityException> { transaction.applyPending() }
            assertEquals("old-db", db.readText())
            assertEquals("old-key", key.readText())
            assertEquals("old-wal", File("${db.path}-wal").readText())
            assertFalse(File("${db.path}-shm").exists())
            DatabaseInstallTransaction(pending, db, key).applyPending()
            assertEquals("old-db", db.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun interruptedInstalledValidationIsRolledBackOnNextStartup() {
        val root = createTempDirectory().toFile()
        try {
            val db = File(root, "db").apply { writeText("old-db") }
            val key = File(root, "key").apply { writeText("old-key") }
            val pending = File(root, "pending")
            val candidate = File(root, "candidate").apply { writeText("new-db") }
            val transaction = DatabaseInstallTransaction(pending, db, key,
                validateInstalled = { throw Interrupted() })
            transaction.stage(candidate, "new-key".toByteArray())
            assertFailsWith<Interrupted> { transaction.applyPending() }
            DatabaseInstallTransaction(pending, db, key).applyPending()
            assertEquals("old-db", db.readText())
            assertEquals("old-key", key.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun committedValidationIsNotRepeatedWhenCleanupFailedAfterNewWrites() {
        val root = createTempDirectory().toFile()
        try {
            val db = File(root, "db").apply { writeText("old-db") }
            val key = File(root, "key").apply { writeText("old-key") }
            val pending = File(root, "pending")
            val candidate = File(root, "candidate").apply { writeText("new-db") }
            var checks = 0
            val transaction = DatabaseInstallTransaction(pending, db, key,
                validateInstalled = { checks++ },
                move = { source, target ->
                    if (source == pending) error("cleanup unavailable")
                    target.parentFile?.mkdirs()
                    Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                })
            transaction.stage(candidate, "new-key".toByteArray())
            transaction.applyPending()
            assertEquals(1, checks)
            assertTrue(File(pending, "committed").exists())
            db.writeText("new-user-data")
            transaction.applyPending()
            assertEquals(1, checks)
            assertEquals("new-user-data", db.readText())
            DatabaseInstallTransaction(pending, db, key).applyPending()
            assertEquals("new-user-data", db.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun missingStagedPayloadCannotCommitAnEmptyDatabase() {
        val root = createTempDirectory().toFile()
        try {
            val db = File(root, "db").apply { writeText("old-db") }
            val key = File(root, "key").apply { writeText("old-key") }
            val pending = File(root, "pending")
            val candidate = File(root, "candidate").apply { writeText("new-db") }
            val transaction = DatabaseInstallTransaction(pending, db, key)
            transaction.stage(candidate, "new-key".toByteArray())
            assertTrue(File(pending, "incoming/database").delete())
            assertFails { transaction.applyPending() }
            assertEquals("old-db", db.readText())
            assertEquals("old-key", key.readText())
        } finally { root.deleteRecursively() }
    }

    private class Interrupted : Error()
}

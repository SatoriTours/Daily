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
    private class Interrupted : Error()
}

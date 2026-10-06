package com.dailysatori.service.backup

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.test.*

class BackupRestoreTransactionTest {
    @Test
    fun cleanupFailureAfterRollbackNeverReplaysRollbackAgainstNewUserData() = withFiles { root ->
        val pending = File(root, "pending")
        val app = File(root, "app").apply { mkdirs() }
        val database = File(root, "db").apply { writeText("original") }
        val life = File(root, "life")
        val password = File(root, "password")
        val transaction = BackupRestoreTransaction(pending, app, database, life, password) { source, target ->
            if (source.path.endsWith("incoming/backup_password.sec")) error("installation failed")
            if (source == pending) error("cleanup failed")
            target.parentFile?.mkdirs()
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        }
        transaction.stage(prepare(root))
        assertEquals("恢复失败，已还原原有数据", transaction.applyPending())
        assertEquals("original", database.readText())
        val newVoice = File(app, "diary/new-recording.m4a").apply { parentFile?.mkdirs(); writeText("recorded after rollback") }

        val restarted = BackupRestoreTransaction(pending, app, database, life, password)
        assertEquals("恢复失败，已还原原有数据", restarted.applyPending())
        assertEquals("recorded after rollback", newVoice.readText())
        assertEquals("original", database.readText())
    }

    @Test
    fun legacyRestoreReportsMissingLifeArchiveAndPreservesCurrentArchive() = withFiles { root ->
        val prepared = prepare(root)
        prepared.resolve("life_archive").deleteRecursively()
        prepared.resolve("preserve-life-archive").writeText("")
        val life = File(root, "life").apply { mkdirs() }
        life.resolve("archive.json.enc").writeText("original life archive")
        val transaction = BackupRestoreTransaction(File(root, "pending"), File(root, "app"), File(root, "db"), life, File(root, "password"))

        transaction.stage(prepared)
        assertTrue(transaction.applyPending()!!.contains("旧备份不含生活资料"))
        assertEquals("original life archive", life.resolve("archive.json.enc").readText())
    }

    @Test
    fun stagingMovesPreparedMediaWithoutMakingAnotherCopy() = withFiles { root ->
        val prepared = prepare(root)
        val voice = File(prepared, "app_data/diary/audio/23/voice.m4a")
        val originalFileKey = Files.readAttributes(voice.toPath(), java.nio.file.attribute.BasicFileAttributes::class.java).fileKey()
        val pending = File(root, "pending")
        val transaction = BackupRestoreTransaction(pending, File(root, "app"), File(root, "db"), File(root, "life"), File(root, "password"))

        transaction.stage(prepared)

        assertFalse(prepared.exists())
        val staged = File(pending, "incoming/app_data/diary/audio/23/voice.m4a")
        assertEquals("voice", staged.readText())
        assertEquals(originalFileKey, Files.readAttributes(staged.toPath(), java.nio.file.attribute.BasicFileAttributes::class.java).fileKey())
    }
    @Test
    fun processDeathAfterCommitNeverRollsBackTheCompletedRestore() = withFiles { root ->
        val app = File(root, "app").apply { mkdirs() }
        val db = File(root, "db.sqlite").apply { writeText("old db") }
        val pending = File(root, "pending")
        val life = File(root, "life")
        val password = File(root, "password")
        val interrupted = BackupRestoreTransaction(pending, app, db, life, password) { src, dest ->
            if (src == pending) throw SimulatedProcessDeath()
            dest.parentFile?.mkdirs()
            Files.move(src.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE)
        }
        interrupted.stage(prepare(root))
        assertFailsWith<SimulatedProcessDeath> { interrupted.applyPending() }
        assertEquals("new db", db.readText())

        assertEquals("恢复完成", BackupRestoreTransaction(pending, app, db, life, password).applyPending())
        assertEquals("new db", db.readText())
        assertEquals("new encrypted life", File(life, "archive.json.enc").readText())
        assertFalse(pending.exists())
    }
    @Test
    fun installsWholeSnapshotAndReplacesStaleFilesWhileKeepingBackupHistory() = withFiles { root ->
        val app = File(root, "app").apply { mkdirs() }
        val db = File(root, "db.sqlite").apply { writeText("old db") }
        File("${db.path}-wal").writeText("old WAL")
        File(app, "images/stale.jpg").apply { parentFile?.mkdirs(); writeText("stale") }
        File(app, "backups/history.enc").apply { parentFile?.mkdirs(); writeText("history") }
        val life = File(root, "life").apply { mkdirs() }
        File(life, "archive.json.enc").writeText("old life")
        val password = File(root, "password").apply { writeText("old password") }
        val prepared = prepare(root)
        val transaction = BackupRestoreTransaction(File(root, "pending"), app, db, life, password)

        transaction.stage(prepared)
        assertEquals("old db", db.readText())
        assertEquals("恢复完成", transaction.applyPending())
        assertEquals("new db", db.readText())
        assertFalse(File("${db.path}-wal").exists())
        assertEquals("voice", File(app, "diary/audio/23/voice.m4a").readText())
        assertFalse(File(app, "images/stale.jpg").exists())
        assertEquals("history", File(app, "backups/history.enc").readText())
        assertEquals("new encrypted life", File(life, "archive.json.enc").readText())
        assertEquals("new encrypted password", password.readText())
        assertNull(transaction.applyPending())
    }

    @Test
    fun installationFailureRestoresDatabaseMediaLifeArchiveAndPassword() = withFiles { root ->
        val app = File(root, "app").apply { mkdirs() }
        val db = File(root, "db.sqlite").apply { writeText("old db") }
        File(app, "images/old.jpg").apply { parentFile?.mkdirs(); writeText("old photo") }
        val life = File(root, "life").apply { mkdirs() }
        File(life, "archive.json.enc").writeText("old life")
        val password = File(root, "password").apply { writeText("old password") }
        val transaction = BackupRestoreTransaction(File(root, "pending"), app, db, life, password) { src, dest ->
            if (src.path.endsWith("incoming/backup_password.sec")) error("disk failed")
            dest.parentFile?.mkdirs()
            Files.move(src.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE)
        }

        transaction.stage(prepare(root))
        assertEquals("恢复失败，已还原原有数据", transaction.applyPending())
        assertEquals("old db", db.readText())
        assertEquals("old photo", File(app, "images/old.jpg").readText())
        assertFalse(File(app, "diary").exists())
        assertEquals("old life", File(life, "archive.json.enc").readText())
        assertEquals("old password", password.readText())
    }

    @Test
    fun processDeathDuringReplacementRollsBackOnNextStartup() = withFiles { root ->
        val app = File(root, "app").apply { mkdirs() }
        val db = File(root, "db.sqlite").apply { writeText("old db") }
        val pending = File(root, "pending")
        val life = File(root, "life")
        val password = File(root, "password")
        val interrupted = BackupRestoreTransaction(pending, app, db, life, password) { src, dest ->
            if (src.path.endsWith("incoming/life_archive")) throw SimulatedProcessDeath()
            dest.parentFile?.mkdirs()
            Files.move(src.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE)
        }
        interrupted.stage(prepare(root))
        assertFailsWith<SimulatedProcessDeath> { interrupted.applyPending() }
        assertEquals("new db", db.readText())

        val restarted = BackupRestoreTransaction(pending, app, db, life, password)
        assertEquals("恢复中断，已还原原有数据", restarted.applyPending())
        assertEquals("old db", db.readText())
        assertFalse(File(app, "diary").exists())
        assertFalse(pending.exists())
    }

    private fun prepare(root: File): File = File(root, "prepared").apply {
        mkdirs()
        resolve("database.db").writeText("new db")
        resolve("app_data/diary/audio/23/voice.m4a").apply { parentFile?.mkdirs(); writeText("voice") }
        resolve("life_archive/archive.json.enc").apply { parentFile?.mkdirs(); writeText("new encrypted life") }
        resolve("backup_password.sec").writeText("new encrypted password")
    }

    private fun withFiles(block: (File) -> Unit) {
        val root = Files.createTempDirectory("restore-test").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }

    private class SimulatedProcessDeath : Error()
}

package com.dailysatori.platform

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertFailsWith

class FileManagerPathSafetyTest {
    @Test
    fun extractionRejectsParentTraversalWithoutWritingOutsideDestination() {
        val root = createTempDirectory().toFile()
        try {
            val archive = File(root, "backup.zip")
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("../escaped.txt"))
                zip.write("private".toByteArray())
                zip.closeEntry()
            }
            assertFailsWith<IllegalArgumentException> { FileManager().extractZip(archive.path, File(root, "restore").path) }
            assertFalse(File(root, "escaped.txt").exists())
        } finally { root.deleteRecursively() }
    }
    @Test
    fun appDataContainmentRejectsSiblingPrefixesAndParentEscapes() {
        val parent = createTempDirectory().toFile()
        val root = parent.resolve("DailySatori").apply { mkdirs() }

        assertTrue(isPathWithinDirectory(root.path, root.resolve("diary_images/recording.m4a").path))
        assertFalse(isPathWithinDirectory(root.path, parent.resolve("DailySatori-backup/recording.m4a").path))
        assertFalse(isPathWithinDirectory(root.path, root.resolve("../outside/recording.m4a").path))
    }

    @Test
    fun appOwnedFileCleanupDeletesMultipleInBoundsFilesButLeavesEscapes() {
        val parent = createTempDirectory().toFile()
        val root = parent.resolve("DailySatori").apply { mkdirs() }
        val first = root.resolve("diary_images/first.m4a").apply { parentFile?.mkdirs(); writeText("first") }
        val second = root.resolve("diary_images/second.jpg").apply { writeText("second") }
        val escape = parent.resolve("outside.txt").apply { writeText("keep") }

        assertTrue(deleteAppOwnedFileIfAllowed(first.path, { isPathWithinDirectory(root.path, it) }, { File(it).delete() }))
        assertTrue(deleteAppOwnedFileIfAllowed(second.path, { isPathWithinDirectory(root.path, it) }, { File(it).delete() }))
        assertFalse(deleteAppOwnedFileIfAllowed(escape.path, { isPathWithinDirectory(root.path, it) }, { File(it).delete() }))

        assertFalse(first.exists())
        assertFalse(second.exists())
        assertTrue(escape.exists())
    }
}

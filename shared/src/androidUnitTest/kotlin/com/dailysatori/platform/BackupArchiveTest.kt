package com.dailysatori.platform

import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.*

class BackupArchiveTest {
    @Test
    fun extractionRejectsPayloadWhoseStoredCrcNoLongerMatches() {
        val root = Files.createTempDirectory("backup-crc").toFile()
        try {
            val zip = root.resolve("damaged.zip")
            val payload = "unique stored payload".toByteArray()
            java.util.zip.ZipOutputStream(zip.outputStream()).use { output ->
                val entry = java.util.zip.ZipEntry("payload.txt").apply {
                    method = java.util.zip.ZipEntry.STORED
                    size = payload.size.toLong()
                    compressedSize = size
                    crc = java.util.zip.CRC32().apply { update(payload) }.value
                }
                output.putNextEntry(entry); output.write(payload); output.closeEntry()
            }
            val bytes = zip.readBytes()
            val offset = bytes.indices.first { i -> i + payload.size <= bytes.size && bytes.copyOfRange(i, i + payload.size).contentEquals(payload) }
            bytes[offset] = (bytes[offset].toInt() xor 1).toByte()
            zip.writeBytes(bytes)
            assertFails { FileManager().extractZip(zip.path, root.resolve("output").path) }
        } finally { root.deleteRecursively() }
    }

    @Test
    fun extractionRejectsAliasedPathsInsteadOfOverwritingAnEarlierEntry() {
        val root = Files.createTempDirectory("backup-duplicate").toFile()
        try {
            val zip = root.resolve("duplicate.zip")
            java.util.zip.ZipOutputStream(zip.outputStream()).use { output ->
                listOf("payload.txt", "folder/../payload.txt").forEach { name ->
                    output.putNextEntry(java.util.zip.ZipEntry(name)); output.write(name.toByteArray()); output.closeEntry()
                }
            }
            assertFails { FileManager().extractZip(zip.path, root.resolve("output").path) }
        } finally { root.deleteRecursively() }
    }

    @Test
    fun largeFilesReportIntermediateProgressThroughoutArchiveAndEncryption() {
        val root = Files.createTempDirectory("backup-progress").toFile()
        try {
            val source = root.resolve("source").apply { mkdirs() }
            val media = source.resolve("voice.m4a").apply { writeBytes(ByteArray(3 * 1024 * 1024) { (it % 251).toByte() }) }
            val manager = FileManager()
            fun checked(operation: ((Double) -> Unit) -> Unit) {
                val fractions = mutableListOf<Double>()
                operation { fractions += it }
                assertTrue(fractions.any { it > 0.0 && it < 1.0 }, "Large files should report intermediate progress")
                assertEquals(1.0, fractions.last())
                assertTrue(fractions.zipWithNext().all { (a, b) -> a <= b })
            }
            val zip = root.resolve("backup.zip")
            val encrypted = root.resolve("backup.enc")
            val decrypted = root.resolve("decrypted.zip")
            checked { manager.createZip(source.path, zip.path, listOf(media.path), it) }
            checked { manager.encryptFile(zip.path, encrypted.path, "file password", it) }
            checked { manager.decryptFile(encrypted.path, decrypted.path, "file password", it) }
            checked { manager.extractZip(decrypted.path, root.resolve("restored").path, it) }
            assertContentEquals(media.readBytes(), root.resolve("restored/voice.m4a").readBytes())
        } finally { root.deleteRecursively() }
    }

    @Test
    fun mediaAvoidsRecompressionWhileDatabaseStillCompressesAndAllFilesRoundTrip() {
        val root = Files.createTempDirectory("backup-archive").toFile()
        try {
            val source = root.resolve("source").apply { mkdirs() }
            val voice = source.resolve("diary/audio/voice.m4a").apply { parentFile?.mkdirs(); writeBytes(ByteArray(256 * 1024) { 7 }) }
            val database = source.resolve("daily_satori.db").apply { writeText("database record\n".repeat(20000)) }
            val zip = root.resolve("backup.zip")
            val manager = FileManager()
            manager.createZip(source.path, zip.path, listOf(voice.path, database.path))
            ZipFile(zip).use { archive ->
                assertTrue(archive.getEntry("diary/audio/voice.m4a").compressedSize >= voice.length())
                assertTrue(archive.getEntry("daily_satori.db").compressedSize < database.length() / 10)
            }
            val restored = root.resolve("restored")
            manager.extractZip(zip.path, restored.path)
            assertContentEquals(voice.readBytes(), restored.resolve("diary/audio/voice.m4a").readBytes())
            assertEquals(database.readText(), restored.resolve("daily_satori.db").readText())
        } finally { root.deleteRecursively() }
    }
}

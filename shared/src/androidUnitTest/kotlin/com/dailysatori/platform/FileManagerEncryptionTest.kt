package com.dailysatori.platform

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FileManagerEncryptionTest {
    @Test
    fun encryptFileUsesVersionedStreamingFormat() {
        val tempDir = createTempDirectory().toFile()
        val input = tempDir.resolve("input.txt")
        val encrypted = tempDir.resolve("backup.enc")
        val decrypted = tempDir.resolve("decrypted.txt")
        input.writeText("daily satori backup data")

        val fileManager = FileManager()
        fileManager.encryptFile(input.absolutePath, encrypted.absolutePath, "correct horse battery")
        fileManager.decryptFile(encrypted.absolutePath, decrypted.absolutePath, "correct horse battery")

        assertEquals("DSB2", encrypted.inputStream().use { stream -> String(stream.readNBytes(4)) })
        assertContentEquals(input.readBytes(), decrypted.readBytes())
    }

    @Test
    fun decryptFileRejectsNonStreamingFormat() {
        val tempDir = createTempDirectory().toFile()
        val legacyEncrypted = tempDir.resolve("legacy.enc")
        val decrypted = tempDir.resolve("decrypted.txt")
        legacyEncrypted.writeBytes(ByteArray(64) { index -> index.toByte() })

        val fileManager = FileManager()
        val error = assertFailsWith<IllegalStateException> {
            fileManager.decryptFile(legacyEncrypted.absolutePath, decrypted.absolutePath, "correct horse battery")
        }

        assertEquals("Unsupported or corrupted backup format", error.message)
    }
}

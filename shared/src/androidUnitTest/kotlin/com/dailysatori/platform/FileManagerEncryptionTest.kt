package com.dailysatori.platform

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FileManagerEncryptionTest {
    @Test
    fun restoresHistoricalAesGcmBackupsAndDoesNotExposePlaintextOnWrongPassword() {
        val root = createTempDirectory().toFile()
        try {
            val salt = ByteArray(16) { it.toByte() }
            val iv = ByteArray(12) { (it + 16).toByte() }
            val keyBytes = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(javax.crypto.spec.PBEKeySpec("legacy password".toCharArray(), salt, 10000, 256)).encoded
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(keyBytes, "AES"), javax.crypto.spec.GCMParameterSpec(128, iv))
            val archive = root.resolve("legacy.zip.enc")
            archive.writeBytes(salt + iv + cipher.doFinal("historical data".toByteArray()))
            val restored = root.resolve("restored.zip")
            FileManager().decryptFile(archive.path, restored.path, "legacy password")
            assertEquals("historical data", restored.readText())

            val rejected = root.resolve("rejected.zip")
            assertFailsWith<IllegalStateException> { FileManager().decryptFile(archive.path, rejected.path, "wrong password") }
            kotlin.test.assertFalse(rejected.exists())
            kotlin.test.assertFalse(root.resolve("rejected.zip.tmp").exists())
        } finally { root.deleteRecursively() }
    }
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

        assertEquals("Invalid backup password or corrupted backup", error.message)
    }
}

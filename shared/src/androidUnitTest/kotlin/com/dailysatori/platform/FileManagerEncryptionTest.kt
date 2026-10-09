package com.dailysatori.platform

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class FileManagerEncryptionTest {
    @Test fun fixedDsb2ArchiveRemainsReadableAndAuthenticated() {
        val root = createTempDirectory().toFile()
        try {
            // Fixed read-only vector from an independent Node/OpenSSL implementation of the historical format.
            val bytes = java.util.Base64.getDecoder().decode("RFNCMgABAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZGhscHR4f9oEcVhdYCx75Bh/yAro7ZQ+S82uK7JVE+Z5DEOW7xCB5vzEm4vqloHA+/S1FCKLXmZ0Sjs4TDg==")
            val source = root.resolve("legacy.enc").apply { writeBytes(bytes) }
            val output = root.resolve("output")
            FileManager().decryptFile(source.path, output.path, "fixture-password")
            assertEquals("historic ZIP payload v2", output.readText())
            output.writeText("keep existing output")
            assertFailsWith<IllegalStateException> { FileManager().decryptFile(source.path, output.path, "wrong password") }
            assertEquals("keep existing output", output.readText())
            for (index in listOf(0, 7, 23, 36, bytes.lastIndex)) {
                source.writeBytes(bytes.copyOf().apply { this[index] = (this[index].toInt() xor 1).toByte() })
                assertFailsWith<IllegalStateException> { FileManager().decryptFile(source.path, output.path, "fixture-password") }
                assertEquals("keep existing output", output.readText())
            }
            source.writeBytes(bytes.copyOf(bytes.size - 1))
            assertFailsWith<IllegalStateException> { FileManager().decryptFile(source.path, output.path, "fixture-password") }
            assertEquals("keep existing output", output.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun dsb3AuthenticatesEachHeaderSectionCiphertextAndTag() {
        val root = createTempDirectory().toFile()
        try {
            val input = root.resolve("input").apply { writeText("protected content") }
            val encrypted = root.resolve("encrypted")
            val output = root.resolve("output").apply { writeText("existing content") }
            FileManager().encryptFile(input.path, encrypted.path, "password")
            val original = encrypted.readBytes()
            for (index in listOf(0, 4, 5, 9, 25, 41, original.lastIndex)) {
                encrypted.writeBytes(original.copyOf().apply { this[index] = (this[index].toInt() xor 1).toByte() })
                assertFailsWith<IllegalStateException> { FileManager().decryptFile(encrypted.path, output.path, "password") }
                assertEquals("existing content", output.readText())
            }
            encrypted.writeBytes(original.copyOf(original.size - 1))
            assertFailsWith<IllegalStateException> { FileManager().decryptFile(encrypted.path, output.path, "password") }
            assertEquals("existing content", output.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun emptyFileRoundTripsWithoutPublishingAnUnauthenticatedOutput() {
        val root = createTempDirectory().toFile()
        try {
            val input = root.resolve("empty").apply { writeBytes(byteArrayOf()) }
            val encrypted = root.resolve("encrypted")
            val output = root.resolve("output")
            FileManager().encryptFile(input.path, encrypted.path, "password")
            FileManager().decryptFile(encrypted.path, output.path, "password")
            assertEquals(0L, output.length())
            assertEquals(73L, encrypted.length())
        } finally { root.deleteRecursively() }
    }

    @Test fun failedEncryptionAndUnauthenticatedDecryptionNeverReplaceExistingOutput() {
        val root = createTempDirectory().toFile()
        try {
            val input = root.resolve("input").apply { writeText("private content") }
            val output = root.resolve("output").apply { writeText("keep me") }
            assertFailsWith<IllegalStateException> {
                FileManager().encryptFile(input.path, output.path, "password") { error("cancelled") }
            }
            assertEquals("keep me", output.readText())
            val encrypted = root.resolve("backup")
            FileManager().encryptFile(input.path, encrypted.path, "password")
            val bytes = encrypted.readBytes().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
            encrypted.writeBytes(bytes)
            assertFailsWith<IllegalStateException> { FileManager().decryptFile(encrypted.path, output.path, "password") }
            assertEquals("keep me", output.readText())
            assertFalse(root.resolve("output.tmp").exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun untrustedKdfParametersAreRejectedBeforeDerivation() {
        val root = createTempDirectory().toFile()
        try {
            val encrypted = root.resolve("backup")
            encrypted.writeBytes("DSB3".toByteArray() + byteArrayOf(1, 127, -1, -1, -1) + ByteArray(64))
            val output = root.resolve("plain")
            assertFailsWith<IllegalStateException> { FileManager().decryptFile(encrypted.path, output.path, "password") }
            assertFalse(output.exists())
        } finally { root.deleteRecursively() }
    }
    @Test
    fun restoresHistoricalAesGcmBackupsAndDoesNotExposePlaintextOnWrongPassword() {
        val root = createTempDirectory().toFile()
        try {
            // Fixed historical GCM vector, not output from the new DSB3 writer.
            val archive = root.resolve("legacy.zip.enc")
            archive.writeBytes(java.util.Base64.getDecoder().decode("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaG5lUYTUWFNtLCsH9hY3n8tAh+9XF6rettSSzdpddUmU="))
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

        assertEquals("DSB3", encrypted.inputStream().use { stream -> String(stream.readNBytes(4)) })
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

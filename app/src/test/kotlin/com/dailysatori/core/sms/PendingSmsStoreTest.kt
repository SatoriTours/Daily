package com.dailysatori.core.sms

import com.dailysatori.service.security.SecretValueCipher
import java.nio.file.Files
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import kotlin.test.*

class PendingSmsStoreTest {
    private val root = Files.createTempDirectory("pending-sms-test").toFile()
    private val cipher = TestSmsCipher()
    private val store = PendingSmsStore(root, cipher)

    @AfterTest
    fun cleanup() { root.deleteRecursively() }

    @Test
    fun largeMessageSurvivesReopenWithoutPlaintextOrWorkManagerSizeLimit() {
        val sms = PendingSms("original-fingerprint", "private-sender", "private-body\u0000中文".repeat(10_000), 123L, "Asia/Shanghai")
        val id = store.save(sms)
        val files = root.listFiles().orEmpty()
        assertEquals(1, files.size)
        assertFalse(files.single().readText().contains("private-body"))
        assertFalse(files.single().readText().contains("private-sender"))
        assertEquals(listOf(id), PendingSmsStore(root, cipher).pendingIds())
        assertEquals(sms, PendingSmsStore(root, cipher).load(id))
        store.remove(id)
        assertTrue(store.pendingIds().isEmpty())
    }

    @Test
    fun unreadableKeyOrCorruptPayloadIsRetainedRatherThanSilentlyDeleted() {
        val id = store.save(PendingSms("id", "sender", "body", 1L, "UTC"))
        val original = root.listFiles()!!.single().readBytes()
        assertFailsWith<javax.crypto.AEADBadTagException> { PendingSmsStore(root, TestSmsCipher()).load(id) }
        assertContentEquals(original, root.listFiles()!!.single().readBytes())
        assertEquals(listOf(id), store.pendingIds())
    }

    @Test
    fun unexpectedNamesCannotReadOrDeleteFilesOutsideInbox() {
        root.resolve("uncommitted.tmp").writeText("incomplete encrypted write")
        assertTrue(store.pendingIds().isEmpty())
        assertFailsWith<IllegalArgumentException> { store.load("../other") }
        assertFailsWith<IllegalArgumentException> { store.remove("../other") }
        assertTrue(root.resolve("uncommitted.tmp").exists())
    }
}

private class TestSmsCipher : SecretValueCipher {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    override fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return "test:" + Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()))
    }
    override fun decrypt(value: String): String {
        val bytes = Base64.getDecoder().decode(value.removePrefix("test:"))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }
    override fun isEncrypted(value: String) = value.startsWith("test:")
}

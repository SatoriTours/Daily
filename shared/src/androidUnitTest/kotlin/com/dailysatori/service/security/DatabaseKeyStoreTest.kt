package com.dailysatori.service.security

import java.io.File
import javax.crypto.KeyGenerator
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class DatabaseKeyStoreTest {
    @Test fun wrappingForAnotherDeviceDoesNotRequireTheOriginalKeystore() {
        val root = createTempDirectory().toFile()
        try {
            val generator = KeyGenerator.getInstance("AES").apply { init(256) }
            val first = generator.generateKey()
            val second = generator.generateKey()
            val key = DatabaseKey.fromHex("ab".repeat(32))
            val source = DatabaseKeyStore(File(root, "source")) { first }
            val target = DatabaseKeyStore(File(root, "target")) { second }
            File(source.storagePath()).writeBytes(source.wrap(key))
            val portable = DatabaseKey.fromPortableJson(source.readExisting()!!.portableJson())
            File(target.storagePath()).writeBytes(target.wrap(portable))
            assertContentEquals(key.sqlCipherPassword(), target.readExisting()!!.sqlCipherPassword())
            assertFalse(File(source.storagePath()).readBytes().contentEquals(File(target.storagePath()).readBytes()))
            assertFailsWith<DatabaseSecurityException> { DatabaseKeyStore(File(source.storagePath())) { second }.readExisting() }
        } finally { root.deleteRecursively() }
    }

    @Test fun missingOrBrokenKeyNeverCreatesAReplacementOnRead() {
        val root = createTempDirectory().toFile()
        try {
            val file = File(root, "key")
            var creates = 0
            val store = DatabaseKeyStore(file) { create ->
                if (create) creates++
                error("Keystore unavailable")
            }
            assertNull(store.readExisting())
            file.writeBytes(byteArrayOf(1, 2, 3))
            assertFailsWith<DatabaseSecurityException> { store.readExisting() }
            assertFailsWith<DatabaseSecurityException> { store.wrap(DatabaseKey.fromHex("ab".repeat(32))) }
            assertContentEquals(byteArrayOf(1, 2, 3), file.readBytes())
            assertEquals(1, creates)
        } finally { root.deleteRecursively() }
    }
}

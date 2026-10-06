package com.dailysatori.core.lifearchive

import com.dailysatori.service.lifearchive.*
import com.dailysatori.service.security.SecretValueCipher
import java.nio.file.Files
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class EncryptedLifeArchiveRepositoryTest {
    @Test
    fun backupRestoresCustomCategoriesAndWholeRecordsUsingTheNewDevicesKey() = runBlocking {
        withDirectory { source -> withDirectory { destination ->
            val oldCipher = DeviceCipher("old-device")
            val newCipher = DeviceCipher("new-device")
            val original = EncryptedLifeArchiveRepository(source, oldCipher, { 10L })
            val category = original.addCategory("家庭保险")
            val saved = original.save(record().copy(categoryId = category.id, body = "续费资料",
                sourceReminderId = "reminder1", sourceReminderVersion = 3), null)
            val restored = EncryptedLifeArchiveRepository(destination, newCipher)
            val encrypted = restored.prepareRestore(original.exportSnapshot())
            assertFalse(encrypted.decodeToString().contains("secret@example.com"))
            java.io.File(destination, "archive.json.enc").writeBytes(encrypted)

            assertEquals(original.categories(), restored.categories())
            assertEquals(listOf(saved), restored.records())
            assertFailsWith<LifeArchiveStorageFailure> { EncryptedLifeArchiveRepository(destination, oldCipher).records() }
        } }
    }

    @Test
    fun invalidBackupSnapshotCannotOverwriteExistingLifeArchive() = runBlocking {
        withDirectory { directory ->
            val repository = EncryptedLifeArchiveRepository(directory, TestCipher)
            val saved = repository.save(record(), null)
            assertFailsWith<LifeArchiveStorageFailure> {
                repository.prepareRestore("{\"schemaVersion\":2,\"categories\":[],\"records\":[]}")
            }
            assertEquals(listOf(saved), repository.records())
        }
    }

    @Test
    fun persistsOnlyCiphertextAndLoadsWholeRecord() = runBlocking {
        withDirectory { dir ->
            val repository = EncryptedLifeArchiveRepository(dir, TestCipher, { 10L })
            val saved = repository.save(record(), null)
            assertEquals(saved, EncryptedLifeArchiveRepository(dir, TestCipher).records().single())
            assertFalse(dir.listFiles()!!.any { it.readText().contains("secret@example.com") })
            assertEquals(10L, saved.createdAt)
        }
    }

    @Test
    fun versionsIncreaseWithinSameMillisecondAndStaleSavesFail() = runBlocking {
        withDirectory { dir ->
            val repository = EncryptedLifeArchiveRepository(dir, TestCipher, { 10L })
            val first = repository.save(record(), null)
            val second = repository.save(first.copy(title = "changed"), first.updatedAt)
            assertEquals(11L, second.updatedAt)
            assertEquals(first.createdAt, second.createdAt)
            assertFailsWith<LifeArchiveConflict> { repository.save(first, first.updatedAt) }
            assertEquals(second, repository.records().single())
        }
    }

    @Test
    fun corruptCiphertextFailsInsteadOfAppearingAsEmptyArchive() = runBlocking {
        withDirectory { dir ->
            val repository = EncryptedLifeArchiveRepository(dir, TestCipher)
            repository.save(record(), null)
            dir.listFiles()!!.single().writeText("broken ciphertext")
            assertFailsWith<LifeArchiveStorageFailure> { repository.records() }
            assertFailsWith<LifeArchiveStorageFailure> { repository.save(record("another"), null) }
            assertEquals("broken ciphertext", dir.listFiles()!!.single().readText())
        }
    }

    @Test
    fun failedAtomicCommitRetainsPreviousArchive() = runBlocking {
        withDirectory { dir ->
            val original = EncryptedLifeArchiveRepository(dir, TestCipher).save(record(), null)
            val failing = EncryptedLifeArchiveRepository(dir, TestCipher, commit = { _, _ -> error("disk failed") })
            assertFailsWith<LifeArchiveStorageFailure> { failing.save(original.copy(title = "lost"), original.updatedAt) }
            assertEquals(original, EncryptedLifeArchiveRepository(dir, TestCipher).records().single())
            assertEquals(1, dir.listFiles()!!.size)
        }
    }

    @Test
    fun categoriesCanBeRenamedAndDeletedWithoutLosingRecords() = runBlocking {
        withDirectory { dir ->
            val repository = EncryptedLifeArchiveRepository(dir, TestCipher)
            val category = repository.addCategory("保险")
            repository.save(record().copy(categoryId = category.id), null)
            repository.renameCategory(category.id, "家庭保险")
            assertEquals("家庭保险", repository.categories().single { it.id == category.id }.name)
            repository.deleteCategory(category.id, "other")
            assertEquals("other", repository.records().single().categoryId)
            assertFalse(repository.categories().any { it.id == category.id })
        }
    }

    @Test
    fun duplicateSourceReminderCannotCreateAnotherRecord() = runBlocking {
        withDirectory { dir ->
            val repository = EncryptedLifeArchiveRepository(dir, TestCipher)
            repository.save(record().copy(sourceReminderId = "reminder1", sourceReminderVersion = 0), null)
            assertFailsWith<LifeArchiveConflict> {
                repository.save(record("second").copy(sourceReminderId = "reminder1", sourceReminderVersion = 0), null)
            }
            assertEquals(1, repository.records().size)
        }
    }

    private fun record(id: String = "record1") = LifeArchiveRecord(
        id, "域名", "domain", fields = listOf(LifeArchiveField("邮箱", "secret@example.com")),
    )

    private suspend fun withDirectory(block: suspend (java.io.File) -> Unit) {
        val directory = Files.createTempDirectory("life-archive-test").toFile()
        try { block(directory) } finally { directory.deleteRecursively() }
    }

    private object TestCipher : SecretValueCipher {
        override fun encrypt(value: String) = "test:" + Base64.getEncoder().encodeToString(value.toByteArray())
        override fun decrypt(value: String) = String(Base64.getDecoder().decode(value.removePrefix("test:")))
        override fun isEncrypted(value: String) = value.startsWith("test:")
    }

    private class DeviceCipher(private val device: String) : SecretValueCipher {
        override fun encrypt(value: String) = "encrypted:$device:" + Base64.getEncoder().encodeToString(value.toByteArray())
        override fun decrypt(value: String) = if (value.startsWith("encrypted:$device:"))
            String(Base64.getDecoder().decode(value.removePrefix("encrypted:$device:"))) else value
        override fun isEncrypted(value: String) = value.startsWith("encrypted:")
    }
}

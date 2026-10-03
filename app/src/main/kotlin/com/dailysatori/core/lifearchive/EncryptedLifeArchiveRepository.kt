package com.dailysatori.core.lifearchive

import com.dailysatori.service.lifearchive.*
import com.dailysatori.service.security.SecretValueCipher
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class LifeArchiveSnapshot(
    val schemaVersion: Int = 1,
    val categories: List<LifeArchiveCategory> = defaultLifeArchiveCategories(),
    val records: List<LifeArchiveRecord> = emptyList(),
)

/** A single DI-owned writer commits categories and records as one encrypted snapshot. */
class EncryptedLifeArchiveRepository(
    private val directory: File,
    private val cipher: SecretValueCipher,
    private val now: () -> Long = System::currentTimeMillis,
    private val commit: (File, File) -> Unit = { temporary, destination ->
        Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    },
) : LifeArchiveRepository {
    private val mutex = Mutex()
    private val file get() = File(directory, "archive.json.enc")

    override suspend fun records(): List<LifeArchiveRecord> = withContext(Dispatchers.IO) {
        mutex.withLock { read().records.sortedByDescending { it.updatedAt } }
    }

    override suspend fun categories(): List<LifeArchiveCategory> = withContext(Dispatchers.IO) {
        mutex.withLock { read().categories }
    }

    override suspend fun save(record: LifeArchiveRecord, expectedUpdatedAt: Long?): LifeArchiveRecord = mutate { snapshot ->
        validateLifeArchiveRecord(record, snapshot.categories)
        val previous = snapshot.records.find { it.id == record.id }
        if (previous?.updatedAt != expectedUpdatedAt) throw LifeArchiveConflict()
        if (record.sourceReminderId != null && snapshot.records.any { it.id != record.id && it.sourceReminderId == record.sourceReminderId }) {
            throw LifeArchiveConflict()
        }
        val timestamp = maxOf(now(), (previous?.updatedAt ?: -1) + 1)
        val saved = record.copy(createdAt = previous?.createdAt ?: timestamp, updatedAt = timestamp)
        snapshot.copy(records = snapshot.records.filterNot { it.id == record.id } + saved) to saved
    }

    override suspend fun delete(id: String) = mutate { snapshot ->
        snapshot.copy(records = snapshot.records.filterNot { it.id == id }) to Unit
    }

    override suspend fun addCategory(name: String): LifeArchiveCategory = mutate { snapshot ->
        val clean = categoryName(name, snapshot.categories)
        val category = LifeArchiveCategory(newLifeArchiveId(), clean)
        snapshot.copy(categories = snapshot.categories + category) to category
    }

    override suspend fun renameCategory(id: String, name: String) = mutate { snapshot ->
        require(snapshot.categories.any { it.id == id })
        val clean = categoryName(name, snapshot.categories.filterNot { it.id == id })
        snapshot.copy(categories = snapshot.categories.map { if (it.id == id) it.copy(name = clean) else it }) to Unit
    }

    override suspend fun deleteCategory(id: String, replacementId: String) = mutate { snapshot ->
        require(id != replacementId && snapshot.categories.any { it.id == id } && snapshot.categories.any { it.id == replacementId })
        val records = snapshot.records.map { record ->
            if (record.categoryId == id) record.copy(categoryId = replacementId, updatedAt = maxOf(now(), record.updatedAt + 1)) else record
        }
        snapshot.copy(categories = snapshot.categories.filterNot { it.id == id }, records = records) to Unit
    }

    private suspend fun <T> mutate(change: (LifeArchiveSnapshot) -> Pair<LifeArchiveSnapshot, T>): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            val (snapshot, result) = change(read())
            validate(snapshot)
            write(snapshot)
            result
        }
    }

    private fun categoryName(name: String, others: List<LifeArchiveCategory>): String = name.trim().also { clean ->
        require(clean.isNotEmpty() && clean.length <= 80 && others.none { it.name.equals(clean, ignoreCase = true) })
    }

    private fun read(): LifeArchiveSnapshot {
        if (!file.exists()) return LifeArchiveSnapshot()
        return try {
            val encrypted = file.readText()
            require(cipher.isEncrypted(encrypted))
            val decrypted = cipher.decrypt(encrypted)
            require(!cipher.isEncrypted(decrypted))
            Json.decodeFromString<LifeArchiveSnapshot>(decrypted).also(::validate)
        } catch (_: Exception) {
            throw LifeArchiveStorageFailure()
        }
    }

    private fun validate(snapshot: LifeArchiveSnapshot) {
        require(snapshot.schemaVersion == 1 && snapshot.categories.isNotEmpty())
        require(snapshot.categories.map { it.id }.distinct().size == snapshot.categories.size)
        require(snapshot.categories.all { it.id.isNotBlank() && it.name.isNotBlank() && it.name.length <= 80 })
        require(snapshot.records.map { it.id }.distinct().size == snapshot.records.size)
        val sources = snapshot.records.mapNotNull { it.sourceReminderId }
        require(sources.distinct().size == sources.size)
        snapshot.records.forEach { validateLifeArchiveRecord(it, snapshot.categories) }
    }

    private fun write(snapshot: LifeArchiveSnapshot) {
        var temporary: File? = null
        try {
            require(directory.isDirectory || directory.mkdirs())
            val encrypted = cipher.encrypt(Json.encodeToString(snapshot))
            require(cipher.isEncrypted(encrypted))
            temporary = File.createTempFile("archive-", ".enc", directory)
            FileOutputStream(temporary).use { stream -> stream.write(encrypted.toByteArray()); stream.fd.sync() }
            commit(temporary, file)
        } catch (_: Exception) {
            throw LifeArchiveStorageFailure()
        } finally {
            temporary?.delete()
        }
    }
}

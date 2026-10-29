package com.dailysatori.core.sms

import com.dailysatori.service.security.SecretValueCipher
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class PendingSms(
    val id: String, val sender: String, val body: String, val receivedAt: Long, val zone: String,
)

/** Independent of business SQLite: a slow upgrade must not lose the receiver's transient payload. */
internal class PendingSmsStore(private val directory: File, private val cipher: SecretValueCipher) {
    fun save(sms: PendingSms): String {
        check(directory.isDirectory || directory.mkdirs())
        val encrypted = cipher.encrypt(Json.encodeToString(sms))
        check(cipher.isEncrypted(encrypted))
        val id = UUID.randomUUID().toString()
        val temporary = File.createTempFile("pending-", ".tmp", directory)
        try {
            check(temporary.setReadable(false, false) && temporary.setReadable(true, true))
            check(temporary.setWritable(false, false) && temporary.setWritable(true, true))
            FileOutputStream(temporary).use { output ->
                output.write(encrypted.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            Files.move(temporary.toPath(), file(id).toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally { temporary.delete() }
        return id
    }

    fun load(id: String): PendingSms {
        val encrypted = file(id).readText()
        check(cipher.isEncrypted(encrypted))
        return Json.decodeFromString(cipher.decrypt(encrypted))
    }

    fun remove(id: String) { check(file(id).delete() || !file(id).exists()) }

    fun pendingIds(): List<String> = directory.listFiles().orEmpty()
        .filter { it.isFile && it.name.endsWith(".enc") }
        .map { it.name.removeSuffix(".enc") }.filter { ID.matches(it) }

    private fun file(id: String): File {
        require(ID.matches(id))
        return File(directory, "$id.enc")
    }

    private companion object {
        val ID = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
    }
}

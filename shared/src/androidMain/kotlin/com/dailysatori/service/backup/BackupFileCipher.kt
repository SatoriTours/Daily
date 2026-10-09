package com.dailysatori.service.backup

import java.io.*
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.*
import javax.crypto.spec.*
import kotlinx.coroutines.CancellationException

/** Versioned encrypt-then-MAC container. A failed operation never publishes partial output. */
internal object BackupFileCipher {
    private const val BufferSize = 256 * 1024
    private const val Iterations = 600_000
    private val magic = "DSB3".toByteArray()
    private val legacyMagic = "DSB2".toByteArray()

    fun encrypt(input: File, output: File, password: String, progress: (Double) -> Unit) = publish(output) { temp ->
        val salt = random(16)
        val iv = random(16)
        val header = magic + byteArrayOf(1) + ByteBuffer.allocate(4).putInt(Iterations).array() + salt + iv
        val (key, macKey) = streamingKeys(password, salt, Iterations)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv)) }
        val mac = Mac.getInstance("HmacSHA256").apply { init(macKey); update(header) }
        FileOutputStream(temp).use { stream ->
            stream.write(header)
            consume(input, progress) { bytes, count ->
                cipher.update(bytes, 0, count)?.let { stream.write(it); mac.update(it) }
            }
            val final = cipher.doFinal()
            stream.write(final); mac.update(final); stream.write(mac.doFinal()); stream.fd.sync()
        }
    }

    fun decrypt(input: File, output: File, password: String, progress: (Double) -> Unit) = publish(output) { temp ->
        try {
            FileInputStream(input).buffered(BufferSize).use { stream ->
                val prefix = stream.exact(4)
                when {
                    prefix.contentEquals(magic) -> {
                        val parameters = stream.exact(5)
                        check(parameters[0] == 1.toByte() && ByteBuffer.wrap(parameters, 1, 4).int == Iterations)
                        check(input.length() >= 73)
                        decryptStreaming(stream, temp, password, magic + parameters, Iterations, input.length(), progress)
                    }
                    prefix.contentEquals(legacyMagic) -> {
                        check(input.length() >= 68)
                        decryptStreaming(stream, temp, password, legacyMagic, 10_000, input.length(), progress)
                    }
                    else -> decryptGcm(input, temp, password, progress)
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: IOException) { throw failure }
        catch (_: Exception) { error("Invalid backup password or corrupted backup") }
    }

    private fun decryptStreaming(input: InputStream, output: File, password: String, prefix: ByteArray,
        iterations: Int, total: Long, progress: (Double) -> Unit) {
        val salt = input.exact(16)
        val iv = input.exact(16)
        val (key, macKey) = streamingKeys(password, salt, iterations)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv)) }
        val mac = Mac.getInstance("HmacSHA256").apply { init(macKey); update(prefix); update(salt); update(iv) }
        FileOutputStream(output).use { stream ->
            var tag = input.exact(32)
            val buffer = ByteArray(BufferSize)
            var processed = (prefix.size + 64).toLong()
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                val combined = tag + buffer.copyOf(count)
                mac.update(combined, 0, count)
                cipher.update(combined, 0, count)?.let(stream::write)
                tag = combined.copyOfRange(count, combined.size)
                processed += count; progress((processed.toDouble() / total).coerceIn(0.0, 1.0))
            }
            check(MessageDigest.isEqual(mac.doFinal(), tag))
            stream.write(cipher.doFinal()); stream.fd.sync()
            progress(1.0)
        }
    }

    private fun decryptGcm(input: File, output: File, password: String, progress: (Double) -> Unit) {
        FileInputStream(input).use { stream ->
            val salt = stream.exact(16)
            val iv = stream.exact(12)
            val bytes = derive(password, salt, 10_000, 256)
            val cipher = try { Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(bytes, "AES"), GCMParameterSpec(128, iv))
            } } finally { bytes.fill(0) }
            FileOutputStream(output).use { out ->
                val buffer = ByteArray(BufferSize)
                var processed = 28L
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    cipher.update(buffer, 0, count)?.let(out::write)
                    processed += count; progress((processed.toDouble() / input.length()).coerceIn(0.0, 1.0))
                }
                out.write(cipher.doFinal()); out.fd.sync()
            }
        }
    }

    private fun streamingKeys(password: String, salt: ByteArray, iterations: Int): Pair<SecretKeySpec, SecretKeySpec> {
        val bytes = derive(password, salt, iterations, 512)
        return try { SecretKeySpec(bytes, 0, 32, "AES") to SecretKeySpec(bytes, 32, 32, "HmacSHA256") }
        finally { bytes.fill(0) }
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int, bits: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, bits)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }

    private fun consume(file: File, progress: (Double) -> Unit, write: (ByteArray, Int) -> Unit) {
        file.inputStream().use { input ->
            val buffer = ByteArray(BufferSize)
            var processed = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                write(buffer, count); processed += count
                progress((processed.toDouble() / file.length().coerceAtLeast(1)).coerceIn(0.0, 1.0))
            }
            progress(1.0)
        }
    }

    private fun publish(output: File, write: (File) -> Unit) {
        val parent = output.absoluteFile.parentFile
        check(parent.isDirectory || parent.mkdirs())
        val temp = File.createTempFile("backup-cipher-", ".tmp", parent)
        try { write(temp); Files.move(temp.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        finally { temp.delete() }
    }

    private fun random(size: Int) = ByteArray(size).also { SecureRandom().nextBytes(it) }
    private fun InputStream.exact(size: Int): ByteArray {
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val count = read(bytes, offset, size - offset)
            check(count > 0) { "Invalid encrypted backup" }
            offset += count
        }
        return bytes
    }
}

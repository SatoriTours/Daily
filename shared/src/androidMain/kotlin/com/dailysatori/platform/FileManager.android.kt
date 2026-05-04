package com.dailysatori.platform

import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.system.exitProcess

actual class FileManager actual constructor() {
    private lateinit var appContext: android.content.Context

    fun init(context: android.content.Context) {
        this.appContext = context
    }

    private fun appDir() = File(appContext.filesDir, "DailySatori")

    actual fun getAppDataDir(): String = appDir().absolutePath
    actual fun getDatabasePath(): String = appContext.getDatabasePath("daily_satori.db").absolutePath
    actual fun getImagesDir(): String = File(appDir(), "images").apply { mkdirs() }.absolutePath
    actual fun getDiaryImagesDir(): String = File(appDir(), "diary_images").apply { mkdirs() }.absolutePath
    actual fun getBackupDir(): String = File(appDir(), "backups").apply { mkdirs() }.absolutePath
    actual fun getCacheDir(): String = appContext.cacheDir.absolutePath
    actual fun getLegacyFlutterDir(): String? {
        val dir = File(appContext.filesDir.parentFile, "app_flutter")
        return if (dir.exists()) dir.absolutePath else null
    }

    actual fun writeFile(path: String, data: ByteArray) {
        File(path).apply { parentFile?.mkdirs() }.writeBytes(data)
    }

    actual fun readFile(path: String): ByteArray = File(path).readBytes()
    actual fun deleteFile(path: String): Boolean = File(path).delete()
    actual fun exists(path: String): Boolean = File(path).exists()
    actual fun listFiles(path: String): List<String> =
        File(path).listFiles()?.map { it.absolutePath } ?: emptyList()
    actual fun copyFile(src: String, dest: String) { File(src).copyTo(File(dest), overwrite = true) }
    actual fun fileSize(path: String): Long = File(path).length()
    actual fun createDirectory(path: String): Boolean = File(path).mkdirs()

    actual fun extractZip(zipPath: String, destDir: String) {
        val dest = File(destDir)
        if (!dest.exists()) dest.mkdirs()
        java.util.zip.ZipFile(zipPath).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val file = File(dest, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    actual fun createZip(sourceDir: String, zipPath: String, files: List<String>) {
        java.util.zip.ZipOutputStream(File(zipPath).outputStream()).use { zos ->
            files.forEach { filePath ->
                val file = File(filePath)
                if (file.exists()) {
                    val entryName = if (filePath.startsWith(sourceDir)) {
                        filePath.removePrefix(sourceDir).removePrefix("/")
                    } else {
                        file.name
                    }
                    zos.putNextEntry(java.util.zip.ZipEntry(entryName))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }

    actual fun readAssetText(filename: String): String {
        return appContext.assets.open(filename).bufferedReader().readText()
    }

    actual fun encryptFile(inputPath: String, outputPath: String, password: String) {
        val salt = ByteArray(SaltSize).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(CtrIvSize).also { SecureRandom().nextBytes(it) }
        val (cipherKey, macKey) = deriveStreamingKeys(password, salt)

        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, cipherKey, IvParameterSpec(iv))
        val mac = hmac(macKey)

        File(outputPath).parentFile?.mkdirs()
        FileOutputStream(outputPath).use { output ->
            output.write(StreamingMagic)
            output.write(salt)
            output.write(iv)
            mac.update(StreamingMagic)
            mac.update(salt)
            mac.update(iv)

            FileInputStream(File(inputPath)).use { input ->
                val buffer = ByteArray(DefaultBufferSize)
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    val encrypted = cipher.update(buffer, 0, count)
                    if (encrypted.isNotEmpty()) {
                        output.write(encrypted)
                        mac.update(encrypted)
                    }
                }
            }
            val finalBytes = cipher.doFinal()
            if (finalBytes.isNotEmpty()) {
                output.write(finalBytes)
                mac.update(finalBytes)
            }
            output.write(mac.doFinal())
        }
    }

    actual fun decryptFile(inputPath: String, outputPath: String, password: String) {
        FileInputStream(inputPath).use { input ->
            val magic = input.readExact(StreamingMagic.size)
            if (!magic.contentEquals(StreamingMagic)) error("Unsupported or corrupted backup format")
            decryptStreaming(input, outputPath, password, magic)
        }
    }

    private fun decryptStreaming(input: InputStream, outputPath: String, password: String, magic: ByteArray) {
        val salt = input.readExact(SaltSize)
        val iv = input.readExact(CtrIvSize)
        val (cipherKey, macKey) = deriveStreamingKeys(password, salt)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, cipherKey, IvParameterSpec(iv))
        val mac = hmac(macKey)
        mac.update(magic)
        mac.update(salt)
        mac.update(iv)

        val output = File(outputPath).apply { parentFile?.mkdirs() }
        val tempOutput = File("$outputPath.tmp")
        try {
            FileOutputStream(tempOutput).use { outputStream ->
                val expectedTag = decryptCipherTextWithTrailingTag(input, outputStream, cipher, mac)
                if (!MessageDigest.isEqual(mac.doFinal(), expectedTag)) error("Invalid backup password or corrupted backup")
                val finalBytes = cipher.doFinal()
                if (finalBytes.isNotEmpty()) outputStream.write(finalBytes)
            }
            tempOutput.copyTo(output, overwrite = true)
        } finally {
            tempOutput.delete()
        }
    }

    private fun decryptCipherTextWithTrailingTag(
        input: InputStream,
        output: FileOutputStream,
        cipher: Cipher,
        mac: Mac,
    ): ByteArray {
        var pendingTag = input.readExact(HmacSize)
        val buffer = ByteArray(DefaultBufferSize)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) return pendingTag
            val combined = pendingTag + buffer.copyOf(count)
            val cipherTextSize = combined.size - HmacSize
            writeDecryptedChunk(combined, cipherTextSize, output, cipher, mac)
            pendingTag = combined.copyOfRange(cipherTextSize, combined.size)
        }
    }

    private fun writeDecryptedChunk(
        bytes: ByteArray,
        count: Int,
        output: FileOutputStream,
        cipher: Cipher,
        mac: Mac,
    ) {
        if (count <= 0) return
        mac.update(bytes, 0, count)
        val decrypted = cipher.update(bytes, 0, count)
        if (decrypted.isNotEmpty()) output.write(decrypted)
    }

    actual fun displayNameForUri(uri: String): String {
        return directory(uri)?.name ?: uri
    }

    actual fun listBackupFilesInDirectory(uri: String): List<String> {
        return directory(uri)?.listFiles()
            ?.mapNotNull { it.name }
            ?.filter { it.endsWith(".enc") }
            ?.sortedDescending()
            ?: emptyList()
    }

    actual fun writeFileToDirectory(uri: String, name: String, sourcePath: String): String {
        val dir = directory(uri) ?: error("Backup directory unavailable")
        dir.findFile(name)?.delete()
        val target = dir.createFile("application/octet-stream", name)
            ?: error("Unable to create backup file")
        val output = appContext.contentResolver.openOutputStream(target.uri)
            ?: error("Unable to open backup file")
        FileInputStream(sourcePath).use { input -> output.use { input.copyTo(it) } }
        return target.name ?: name
    }

    actual fun readFileFromDirectory(uri: String, name: String, destPath: String): Boolean {
        val file = directory(uri)?.findFile(name) ?: return false
        val input = appContext.contentResolver.openInputStream(file.uri) ?: return false
        File(destPath).parentFile?.mkdirs()
        input.use { source -> FileOutputStream(destPath).use { source.copyTo(it) } }
        return true
    }

    actual fun deleteFileFromDirectory(uri: String, name: String): Boolean {
        return directory(uri)?.findFile(name)?.delete() ?: false
    }

    actual fun restartApp() {
        val intent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        appContext.startActivity(intent)
        exitProcess(0)
    }

    private fun deriveStreamingKeys(password: String, salt: ByteArray): Pair<SecretKeySpec, SecretKeySpec> {
        val keys = deriveKeyBytes(password, salt, 512)
        return SecretKeySpec(keys.copyOfRange(0, 32), "AES") to SecretKeySpec(keys.copyOfRange(32, 64), "HmacSHA256")
    }

    private fun deriveKeyBytes(password: String, salt: ByteArray, bits: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 10000, bits)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun hmac(key: SecretKeySpec): Mac {
        return Mac.getInstance("HmacSHA256").apply { init(key) }
    }

    private fun InputStream.readExact(size: Int): ByteArray {
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val count = read(bytes, offset, size - offset)
            if (count == -1) error("Invalid encrypted backup")
            offset += count
        }
        return bytes
    }

    private fun directory(uri: String): DocumentFile? {
        return DocumentFile.fromTreeUri(appContext, Uri.parse(uri))?.takeIf { it.isDirectory }
    }

    private companion object {
        val StreamingMagic = byteArrayOf('D'.code.toByte(), 'S'.code.toByte(), 'B'.code.toByte(), '2'.code.toByte())
        const val SaltSize = 16
        const val CtrIvSize = 16
        const val HmacSize = 32
        const val DefaultBufferSize = 64 * 1024
    }
}

package com.dailysatori.platform

import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.FilterInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.GCMParameterSpec
import com.dailysatori.service.backup.createSqliteBackupSnapshot
import com.dailysatori.service.backup.BackupRestoreTransaction
import kotlin.system.exitProcess

actual class FileManager actual constructor() {
    private lateinit var appContext: android.content.Context

    fun init(context: android.content.Context) {
        this.appContext = context
    }

    private fun appDir() = File(appContext.filesDir, "DailySatori")

    actual fun getAppDataDir(): String = appDir().absolutePath
    actual fun isAppDataPath(path: String): Boolean = isPathWithinDirectory(getAppDataDir(), path)
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
    actual fun deleteFile(path: String): Boolean = File(path).deleteRecursively()
    actual fun deleteAppOwnedFile(path: String?): Boolean =
        deleteAppOwnedFileIfAllowed(path, ::isAppDataPath, ::deleteFile)
    actual fun exists(path: String): Boolean = File(path).exists()
    actual fun listFiles(path: String): List<String> =
        File(path).listFiles()?.map { it.absolutePath } ?: emptyList()
    actual fun copyFile(src: String, dest: String) { File(src).copyTo(File(dest).apply { parentFile?.mkdirs() }, overwrite = true) }
    actual fun moveFile(src: String, dest: String) {
        File(dest).parentFile?.mkdirs()
        Files.move(File(src).toPath(), File(dest).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
    actual fun fileSize(path: String): Long = File(path).length()
    actual fun createDirectory(path: String): Boolean = File(path).mkdirs()

    actual fun createDatabaseSnapshot(destination: String) {
        val target = File(destination).apply { parentFile?.mkdirs() }
        check(!target.exists()) { "备份临时数据库已存在" }
        val driver = DatabaseDriverFactory(PlatformContext(appContext)).createDriver()
        try { createSqliteBackupSnapshot(driver, getDatabasePath(), destination, ::copyFile) }
        finally { driver.close() }
    }

    actual fun listFilesRecursively(path: String): List<String> {
        val root = File(path)
        if (!root.exists()) return emptyList()
        return java.nio.file.Files.walk(root.toPath()).use { paths ->
            paths.filter { java.nio.file.Files.isRegularFile(it, java.nio.file.LinkOption.NOFOLLOW_LINKS) }
                .map { it.toFile().absolutePath }.iterator().asSequence().toList()
        }
    }

    actual fun sha256(path: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        File(path).inputStream().use { stream ->
            val buffer = ByteArray(DefaultBufferSize)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun restoreTransaction() = BackupRestoreTransaction(
        File(appContext.noBackupFilesDir, "pending-restore"), appDir(), File(getDatabasePath()),
        File(appContext.noBackupFilesDir, "life_archive"), File(appContext.filesDir, "backup_password.sec"),
    )

    actual fun stageRestore(directory: String) = restoreTransaction().stage(File(directory))
    actual fun applyPendingRestore(): String? = restoreTransaction().applyPending()

    actual fun extractZip(zipPath: String, destDir: String, progress: (Double) -> Unit) {
        val dest = File(destDir)
        if (!dest.exists()) dest.mkdirs()
        java.util.zip.ZipFile(zipPath).use { zip ->
            val total = zip.entries().asSequence().filter { !it.isDirectory }.sumOf { it.size.coerceAtLeast(0) }.coerceAtLeast(1)
            var processed = 0L
            zip.entries().asSequence().forEach { entry ->
                val file = File(dest, entry.name)
                require('\\' !in entry.name && !File(entry.name).isAbsolute && isPathWithinDirectory(dest.path, file.path) && file.canonicalFile != dest.canonicalFile) {
                    "备份包含非法文件路径"
                }
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    ProgressInputStream(zip.getInputStream(entry), entry.size.coerceAtLeast(1)) { fraction ->
                        progress(((processed + fraction * entry.size.coerceAtLeast(0)) / total).coerceIn(0.0, 1.0))
                    }.use { input ->
                        file.outputStream().buffered(DefaultBufferSize).use { output ->
                            input.copyTo(output, DefaultBufferSize)
                        }
                    }
                    processed += entry.size.coerceAtLeast(0)
                }
            }
        }
        progress(1.0)
    }

    actual fun createZip(sourceDir: String, zipPath: String, files: List<String>, progress: (Double) -> Unit) {
        val total = files.sumOf { File(it).length() }.coerceAtLeast(1)
        var processed = 0L
        java.util.zip.ZipOutputStream(File(zipPath).outputStream().buffered(DefaultBufferSize)).use { zos ->
            files.forEach { filePath ->
                val file = File(filePath)
                check(file.isFile) { "备份源文件不存在" }
                run {
                    val entryName = if (filePath.startsWith(sourceDir)) {
                        filePath.removePrefix(sourceDir).removePrefix("/")
                    } else {
                        file.name
                    }
                    zos.setLevel(if (file.extension.lowercase() in CompressedExtensions) java.util.zip.Deflater.NO_COMPRESSION else java.util.zip.Deflater.BEST_SPEED)
                    zos.putNextEntry(java.util.zip.ZipEntry(entryName))
                    ProgressInputStream(file.inputStream(), file.length().coerceAtLeast(1)) { fraction ->
                        progress(((processed + fraction * file.length()) / total).coerceIn(0.0, 1.0))
                    }.use { it.copyTo(zos, DefaultBufferSize) }
                    zos.closeEntry()
                    processed += file.length()
                }
            }
        }
        progress(1.0)
    }

    actual fun readAssetText(filename: String): String {
        return appContext.assets.open(filename).bufferedReader().readText()
    }

    actual fun encryptFile(inputPath: String, outputPath: String, password: String, progress: (Double) -> Unit) {
        val salt = ByteArray(SaltSize).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(CtrIvSize).also { SecureRandom().nextBytes(it) }
        val (cipherKey, macKey) = deriveStreamingKeys(password, salt)

        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, cipherKey, IvParameterSpec(iv))
        val mac = hmac(macKey)

        File(outputPath).parentFile?.mkdirs()
        FileOutputStream(outputPath).buffered(DefaultBufferSize).use { output ->
            output.write(StreamingMagic)
            output.write(salt)
            output.write(iv)
            mac.update(StreamingMagic)
            mac.update(salt)
            mac.update(iv)

            ProgressInputStream(FileInputStream(inputPath), File(inputPath).length(), progress).use { input ->
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

    actual fun decryptFile(inputPath: String, outputPath: String, password: String, progress: (Double) -> Unit) {
        ProgressInputStream(FileInputStream(inputPath), File(inputPath).length(), progress).use { input ->
            val magic = input.readExact(StreamingMagic.size)
            if (magic.contentEquals(StreamingMagic)) decryptStreaming(input, outputPath, password, magic)
            else decryptLegacy(inputPath, outputPath, password, progress)
        }
    }

    private fun decryptLegacy(inputPath: String, outputPath: String, password: String, progress: (Double) -> Unit) {
        val output = File(outputPath).apply { parentFile?.mkdirs() }
        val temporary = File("$outputPath.tmp")
        try {
            ProgressInputStream(FileInputStream(inputPath), File(inputPath).length(), progress).use { input ->
                val salt = input.readExact(SaltSize)
                val iv = input.readExact(12)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(deriveKeyBytes(password, salt, 256), "AES"), GCMParameterSpec(128, iv))
                temporary.outputStream().use { stream ->
                    val buffer = ByteArray(DefaultBufferSize)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        cipher.update(buffer, 0, count)?.let(stream::write)
                    }
                    stream.write(cipher.doFinal()) // Authenticate before exposing the plaintext ZIP.
                }
            }
            moveFile(temporary.path, output.path)
        } catch (failure: java.io.IOException) {
            throw failure
        } catch (_: Exception) {
            error("Invalid backup password or corrupted backup")
        } finally { temporary.delete() }
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
            moveFile(tempOutput.path, output.path)
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
        check(dir.findFile(name) == null) { "同名备份已存在，请稍后重试" }
        val target = dir.createFile("application/octet-stream", "$name.partial")
            ?: error("Unable to create backup file")
        try {
            val output = appContext.contentResolver.openOutputStream(target.uri)
                ?: error("Unable to open backup file")
            val copied = FileInputStream(sourcePath).use { input -> output.use { input.copyTo(it) } }
            check(copied == File(sourcePath).length()) { "备份文件未写入完整" }
            check(target.renameTo(name)) { "无法保存备份文件" }
            return target.name ?: name
        } catch (failure: Exception) {
            target.delete()
            throw failure
        }
    }

    actual fun readFileFromDirectory(uri: String, name: String, destPath: String): Boolean {
        val file = directory(uri)?.findFile(name) ?: return false
        return readFileFromUri(file.uri.toString(), destPath)
    }

    actual fun displayNameForFileUri(uri: String): String =
        DocumentFile.fromSingleUri(appContext, Uri.parse(uri))?.name ?: "备份文件"

    actual fun readFileFromUri(uri: String, destPath: String): Boolean {
        val input = appContext.contentResolver.openInputStream(Uri.parse(uri)) ?: return false
        File(destPath).parentFile?.mkdirs()
        input.use { source -> FileOutputStream(destPath).buffered(DefaultBufferSize).use { source.copyTo(it, DefaultBufferSize) } }
        return true
    }

    actual fun deleteFileFromDirectory(uri: String, name: String): Boolean {
        return directory(uri)?.findFile(name)?.delete() ?: false
    }

    actual fun restartApp() {
        val intent = checkNotNull(appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)) { "Unable to restart app" }
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
        const val DefaultBufferSize = 256 * 1024
        val CompressedExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "heic", "m4a", "mp3", "mp4", "aac", "ogg", "flac", "zip", "gz", "enc", "pdf")
    }
}

private class ProgressInputStream(input: InputStream, private val total: Long, private val progress: (Double) -> Unit) : FilterInputStream(input) {
    private var count = 0L
    private var reported = 0L
    override fun read(): Int = `in`.read().also { record(if (it < 0) -1 else 1) }
    override fun read(bytes: ByteArray, offset: Int, length: Int): Int = `in`.read(bytes, offset, length).also(::record)
    private fun record(size: Int) {
        if (size > 0) count += size
        if (size < 0 || count - reported >= 1024 * 1024 || count >= total) {
            progress((count.toDouble() / total.coerceAtLeast(1)).coerceIn(0.0, 1.0))
            reported = count
        }
    }
}

internal fun isPathWithinDirectory(root: String, candidate: String): Boolean = runCatching {
    val rootPath = File(root).canonicalFile.path
    val candidatePath = File(candidate).canonicalFile.path
    val separator = File.separator
    val rootPrefix = if (rootPath.endsWith(separator)) rootPath else "$rootPath$separator"
    candidatePath.startsWith(rootPrefix)
}.getOrDefault(false)

internal fun deleteAppOwnedFileIfAllowed(
    path: String?,
    isAppDataPath: (String) -> Boolean,
    deleteFile: (String) -> Boolean,
): Boolean {
    return path != null && isAppDataPath(path) && deleteFile(path)
}

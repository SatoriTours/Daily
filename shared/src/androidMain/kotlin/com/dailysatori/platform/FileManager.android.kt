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
        try {
            val key = com.dailysatori.service.security.DatabaseKeyStore(PlatformContext(appContext)).readExisting()
                ?: throw com.dailysatori.service.security.DatabaseSecurityException()
            com.dailysatori.service.backup.exportEncryptedDatabase(driver, destination, key)
        }
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
        databaseKey = File(com.dailysatori.service.security.DatabaseKeyStore(PlatformContext(appContext)).storagePath()),
        prepareIncoming = ::preparePendingDatabase,
    )

    actual fun stageRestore(directory: String) = restoreTransaction().stage(File(directory))
    actual fun applyPendingRestore(): String? = restoreTransaction().applyPending()

    private fun preparePendingDatabase(incoming: File) {
        val database = File(incoming, "database.db")
        val envelope = File(incoming, "database_key.sec")
        val keys = com.dailysatori.service.security.DatabaseKeyStore(PlatformContext(appContext))
        if (!envelope.exists()) {
            check(isPlaintextDatabase(database)) { "待恢复数据库缺少密钥" }
            val key = com.dailysatori.service.security.DatabaseKey.generate()
            val temporary = File(incoming, "database_key.sec.tmp")
            FileOutputStream(temporary).use { it.write(keys.wrap(key)); it.fd.sync() }
            moveFile(temporary.path, envelope.path)
        }
        check(envelope.length() == 93L)
        val key = keys.unwrap(envelope.readBytes())
        val factory = DatabaseDriverFactory(PlatformContext(appContext))
        if (isPlaintextDatabase(database)) factory.encryptLegacyDatabase(database.path, key)
        factory.createBackupDriver(database.path, key).let { driver ->
            try { com.dailysatori.service.security.validate(driver) } finally { driver.close() }
        }
    }

    actual fun extractZip(zipPath: String, destDir: String, progress: (Double) -> Unit) {
        val dest = File(destDir)
        check(dest.isDirectory || dest.mkdirs()) { "无法创建解压目录" }
        java.util.zip.ZipFile(zipPath).use { zip ->
            require(zip.size() <= 100_000) { "备份包含过多文件" }
            val targets = mutableSetOf<String>()
            val entries = zip.entries().asSequence().map { entry ->
                val file = File(dest, entry.name)
                val parts = entry.name.removeSuffix("/").split('/')
                require('\\' !in entry.name && !File(entry.name).isAbsolute &&
                    parts.none { it.isBlank() || it == "." || it == ".." } &&
                    isPathWithinDirectory(dest.path, file.path) && targets.add(file.canonicalPath)) {
                    "备份包含非法或重复文件路径"
                }
                require(if (entry.isDirectory) entry.size == 0L && entry.crc == 0L else entry.size >= 0 && entry.crc >= 0) {
                    "备份文件长度或校验码无效"
                }
                require(entry.isDirectory || entry.size / entry.compressedSize.coerceAtLeast(1) <= 2_000) { "备份解压体积异常" }
                entry to file
            }.toList()
            val total = entries.filterNot { it.first.isDirectory }.fold(0L) { size, pair -> Math.addExact(size, pair.first.size) }
            check(total <= 32L * 1024 * 1024 * 1024 && total <= dest.usableSpace - 64L * 1024 * 1024) {
                "空间不足或备份解压体积异常"
            }
            var processed = 0L
            entries.forEach { (entry, file) ->
                if (entry.isDirectory) check(file.isDirectory || file.mkdirs())
                else {
                    extractArchiveEntry(zip, entry, file) { bytes ->
                        progress(((processed + bytes).toDouble() / total.coerceAtLeast(1)).coerceIn(0.0, 1.0))
                    }
                    processed += entry.size
                }
            }
        }
        progress(1.0)
    }

    private fun extractArchiveEntry(zip: java.util.zip.ZipFile, entry: java.util.zip.ZipEntry, file: File, progress: (Long) -> Unit) {
        file.parentFile?.let { check(it.isDirectory || it.mkdirs()) }
        val crc = java.util.zip.CRC32()
        var size = 0L
        zip.getInputStream(entry).use { input ->
            file.outputStream().buffered(DefaultBufferSize).use { output ->
                val buffer = ByteArray(DefaultBufferSize)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(count <= entry.size - size) { "备份文件解压长度异常" }
                    output.write(buffer, 0, count)
                    crc.update(buffer, 0, count)
                    size += count
                    progress(size)
                }
            }
        }
        check(size == entry.size && crc.value == entry.crc) { "备份 ZIP 文件校验失败" }
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
                    val alreadyCompressed = entryName == com.dailysatori.config.DatabaseConfig.name || file.extension.lowercase() in CompressedExtensions
                    zos.setLevel(if (alreadyCompressed) java.util.zip.Deflater.NO_COMPRESSION else java.util.zip.Deflater.BEST_SPEED)
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

    actual fun encryptFile(inputPath: String, outputPath: String, password: String, progress: (Double) -> Unit) =
        com.dailysatori.service.backup.BackupFileCipher.encrypt(File(inputPath), File(outputPath), password, progress)

    actual fun decryptFile(inputPath: String, outputPath: String, password: String, progress: (Double) -> Unit) =
        com.dailysatori.service.backup.BackupFileCipher.decrypt(File(inputPath), File(outputPath), password, progress)

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

    private fun directory(uri: String): DocumentFile? {
        return DocumentFile.fromTreeUri(appContext, Uri.parse(uri))?.takeIf { it.isDirectory }
    }

    private companion object {
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

package com.dailysatori.platform

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

actual class FileManager actual constructor() {
    private lateinit var appContext: android.content.Context

    fun init(context: android.content.Context) {
        this.appContext = context
    }

    private fun appDir() = File(appContext.filesDir, "DailySatori")

    actual fun getAppDataDir(): String = appDir().absolutePath
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
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))

        val output = FileOutputStream(outputPath)
        output.write(salt)  // header: salt
        output.write(iv)     // header: iv
        val cos = CipherOutputStream(output, cipher)
        FileInputStream(File(inputPath)).use { it.copyTo(cos) }
        cos.close()
    }

    actual fun decryptFile(inputPath: String, outputPath: String, password: String) {
        val input = FileInputStream(inputPath)
        val salt = ByteArray(16); input.read(salt)
        val iv = ByteArray(12); input.read(iv)
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))

        val cos = FileOutputStream(outputPath)
        val cis = CipherInputStream(input, cipher)
        cis.copyTo(cos)
        cis.close()
        cos.close()
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

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, 10000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun directory(uri: String): DocumentFile? {
        return DocumentFile.fromTreeUri(appContext, Uri.parse(uri))?.takeIf { it.isDirectory }
    }
}

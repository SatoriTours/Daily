package com.dailysatori.platform

import java.io.File

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
}

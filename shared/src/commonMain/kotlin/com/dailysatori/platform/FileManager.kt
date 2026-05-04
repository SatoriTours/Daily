package com.dailysatori.platform

expect class FileManager() {
    fun getAppDataDir(): String
    fun getImagesDir(): String
    fun getDiaryImagesDir(): String
    fun getBackupDir(): String
    fun getCacheDir(): String
    fun getLegacyFlutterDir(): String?
    fun writeFile(path: String, data: ByteArray)
    fun readFile(path: String): ByteArray
    fun deleteFile(path: String): Boolean
    fun exists(path: String): Boolean
    fun listFiles(path: String): List<String>
    fun copyFile(src: String, dest: String)
    fun fileSize(path: String): Long
    fun createDirectory(path: String): Boolean
    fun extractZip(zipPath: String, destDir: String)
    fun createZip(sourceDir: String, zipPath: String, files: List<String>)
    fun readAssetText(filename: String): String
    fun encryptFile(inputPath: String, outputPath: String, password: String)
    fun decryptFile(inputPath: String, outputPath: String, password: String)
    fun displayNameForUri(uri: String): String
    fun listBackupFilesInDirectory(uri: String): List<String>
    fun writeFileToDirectory(uri: String, name: String, sourcePath: String): String
    fun readFileFromDirectory(uri: String, name: String, destPath: String): Boolean
    fun deleteFileFromDirectory(uri: String, name: String): Boolean
}

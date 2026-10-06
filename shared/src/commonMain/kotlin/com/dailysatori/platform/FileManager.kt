package com.dailysatori.platform

expect class FileManager() {
    fun getAppDataDir(): String
    fun isAppDataPath(path: String): Boolean
    fun getDatabasePath(): String
    fun getImagesDir(): String
    fun getDiaryImagesDir(): String
    fun getBackupDir(): String
    fun getCacheDir(): String
    fun createDatabaseSnapshot(destination: String)
    fun listFilesRecursively(path: String): List<String>
    fun sha256(path: String): String
    fun stageRestore(directory: String)
    fun applyPendingRestore(): String?
    fun getLegacyFlutterDir(): String?
    fun writeFile(path: String, data: ByteArray)
    fun readFile(path: String): ByteArray
    fun deleteFile(path: String): Boolean
    fun deleteAppOwnedFile(path: String?): Boolean
    fun exists(path: String): Boolean
    fun listFiles(path: String): List<String>
    fun copyFile(src: String, dest: String)
    fun moveFile(src: String, dest: String)
    fun fileSize(path: String): Long
    fun createDirectory(path: String): Boolean
    fun extractZip(zipPath: String, destDir: String, progress: (Double) -> Unit = {})
    fun createZip(sourceDir: String, zipPath: String, files: List<String>, progress: (Double) -> Unit = {})
    fun readAssetText(filename: String): String
    fun encryptFile(inputPath: String, outputPath: String, password: String, progress: (Double) -> Unit = {})
    fun decryptFile(inputPath: String, outputPath: String, password: String, progress: (Double) -> Unit = {})
    fun readFileFromUri(uri: String, destPath: String): Boolean
    fun displayNameForFileUri(uri: String): String
    fun displayNameForUri(uri: String): String
    fun listBackupFilesInDirectory(uri: String): List<String>
    fun writeFileToDirectory(uri: String, name: String, sourcePath: String): String
    fun readFileFromDirectory(uri: String, name: String, destPath: String): Boolean
    fun deleteFileFromDirectory(uri: String, name: String): Boolean
    fun restartApp()
}

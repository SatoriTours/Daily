package com.dailysatori.platform

expect class FileManager {
    fun getAppDataDir(): String
    fun getImagesDir(): String
    fun getDiaryImagesDir(): String
    fun getBackupDir(): String
    fun getCacheDir(): String
    fun writeFile(path: String, data: ByteArray)
    fun readFile(path: String): ByteArray
    fun deleteFile(path: String): Boolean
    fun exists(path: String): Boolean
    fun listFiles(path: String): List<String>
    fun copyFile(src: String, dest: String)
    fun fileSize(path: String): Long
    fun createDirectory(path: String): Boolean
}

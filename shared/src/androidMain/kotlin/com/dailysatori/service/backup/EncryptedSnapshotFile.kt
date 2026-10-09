package com.dailysatori.service.backup

import java.io.File

/** An OPEN_READWRITE (no CREATE) source also prevents ATTACH from creating missing files. */
internal actual fun prepareEncryptedSnapshotFile(path: String) {
    val file = File(path)
    check(listOf(file, File("$path-wal"), File("$path-shm"), File("$path-journal")).none { it.exists() }) {
        "导出目标已存在，不能覆盖"
    }
    file.parentFile?.let { check(it.isDirectory) { "导出目录不存在" } }
    check(file.createNewFile()) { "不能创建加密导出目标" }
}

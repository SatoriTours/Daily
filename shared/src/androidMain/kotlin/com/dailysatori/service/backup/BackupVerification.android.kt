package com.dailysatori.service.backup

/** Retain the full cause/suppressed chain and frames, but never arbitrary exception messages or SQL. */
internal actual fun backupVerificationSafeStack(error: Throwable): List<String> = buildList {
    val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
    fun appendFailure(failure: Throwable, relation: String) {
        if (!visited.add(failure)) {
            add("$relation [cycle omitted]")
            return
        }
        val detail = failure.message?.takeIf { failure is BackupValidationException || it in safeBackupMessages }
            ?: "[message omitted for privacy]"
        add("$relation ${failure.javaClass.name}: $detail")
        if (failure is java.sql.SQLException || failure.javaClass.name.startsWith("android.database.sqlite.")) {
            databaseFailureCategory(failure.message.orEmpty())?.let { add("databaseFailure=$it") }
        }
        failure.stackTrace.forEach { frame ->
            add("  at ${frame.className}.${frame.methodName}(${frame.fileName}:${frame.lineNumber})")
        }
        failure.suppressed.forEach { appendFailure(it, "Suppressed:") }
        failure.cause?.let { appendFailure(it, "Caused by:") }
    }
    appendFailure(error, "Exception:")
}

private fun databaseFailureCategory(message: String): String? = listOf(
    "no such table" to "missing_table", "no such column" to "missing_column",
    "foreign key constraint failed" to "foreign_key_constraint", "not null constraint failed" to "not_null_constraint",
    "unique constraint failed" to "unique_constraint", "database is locked" to "database_locked",
    "database or disk is full" to "storage_full", "database disk image is malformed" to "database_corrupt",
    "file is not a database" to "not_sqlite", "no such module" to "missing_sqlite_module",
    "syntax error" to "sql_syntax_error",
).firstOrNull { (pattern, _) -> message.contains(pattern, ignoreCase = true) }?.second

private val safeBackupMessages = setOf(
    "数据库完整性检查失败", "数据库关联完整性检查失败", "备份不是完整的应用数据库",
    "备份数据库版本无效或较新", "备份版本较新，请先升级应用", "备份数据库未初始化",
    "备份数据库版本不兼容", "备份数据库缺少应用所需的表或字段",
    "正在录音，请结束录音后再备份或恢复", "备份缺少附件，已取消恢复",
    "附件路径不属于应用数据，无法完整迁移", "备份中未找到数据库文件",
    "备份内容不完整或包含未登记文件", "备份文件校验失败",
    "无法解密敏感数据，已取消备份以避免数据丢失",
    "Cannot create private verification directory", "Private verification directory was not removed",
)

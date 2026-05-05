package com.dailysatori.service.mcp

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

private val allowedLocalSqlTables = setOf(
    "diary",
    "article",
    "book",
    "book_viewpoint",
    "memory_entry",
)

private val forbiddenSqlTokens = listOf(
    "insert", "update", "delete", "drop", "alter", "pragma", "attach", "detach", "replace", "create",
)

data class LocalSqlValidationResult(
    val isValid: Boolean,
    val sql: String,
    val error: String? = null,
)

data class LocalSqlQueryResult(
    val success: Boolean,
    val sql: String,
    val rows: List<Map<String, String?>> = emptyList(),
    val error: String? = null,
)

fun localSqlToolSchemaText(): String = """
可查询表：
- diary(id, content, tags, mood, images, created_at, updated_at)
- article(id, title, ai_title, ai_content, url, is_favorite, comment, status, created_at, updated_at)
- book(id, title, author, category, introduction, created_at, updated_at)
- book_viewpoint(id, book_id, title, content, example, created_at, updated_at)
- memory_entry(id, type, source_type, source_id, title, content, tags, created_at, updated_at)

规则：只生成 SELECT 查询；统计类问题优先使用 COUNT、MIN、MAX、GROUP BY；必须避免查询密钥、配置和会话表。
""".trimIndent()

fun validateLocalSql(sql: String): LocalSqlValidationResult {
    val cleaned = sql.trim().trimEnd(';').trim()
    val lower = cleaned.lowercase()
    if (!lower.startsWith("select ")) {
        return LocalSqlValidationResult(false, cleaned, "只允许 SELECT 查询")
    }
    if (forbiddenSqlTokens.any { Regex("\\b$it\\b").containsMatchIn(lower) }) {
        return LocalSqlValidationResult(false, cleaned, "只允许 SELECT 查询")
    }
    val tables = Regex("\\b(from|join)\\s+([a-zA-Z_][a-zA-Z0-9_]*)")
        .findAll(lower)
        .map { it.groupValues[2] }
        .toSet()
    val blocked = tables.filterNot { it in allowedLocalSqlTables }
    if (blocked.isNotEmpty()) {
        return LocalSqlValidationResult(false, cleaned, "不允许查询表: ${blocked.joinToString(", ")}")
    }
    val limited = if (Regex("\\blimit\\s+\\d+").containsMatchIn(lower)) {
        cleaned
    } else {
        "SELECT * FROM ($cleaned) LIMIT 100"
    }
    return LocalSqlValidationResult(true, limited)
}

class LocalSqlQueryService(private val driver: SqlDriver) {
    fun query(sql: String, columns: List<String>): LocalSqlQueryResult {
        val validation = validateLocalSql(sql)
        if (!validation.isValid) {
            return LocalSqlQueryResult(false, validation.sql, error = validation.error)
        }
        if (columns.isEmpty()) {
            return LocalSqlQueryResult(false, validation.sql, error = "缺少 columns 参数")
        }
        return try {
            driver.executeQuery(0, validation.sql, { cursor ->
                val rows = mutableListOf<Map<String, String?>>()
                while (cursor.next().value) {
                    rows.add(columns.mapIndexed { index, name -> name to cursor.getString(index) }.toMap())
                }
                QueryResult.Value(LocalSqlQueryResult(true, validation.sql, rows))
            }, 0).value
        } catch (error: Exception) {
            LocalSqlQueryResult(false, validation.sql, error = error.message ?: "SQL 查询失败")
        }
    }
}

fun LocalSqlQueryResult.toJson(): JsonObject = buildJsonObject {
    put("success", JsonPrimitive(success))
    put("sql", JsonPrimitive(sql))
    error?.let { put("error", JsonPrimitive(it)) }
    put("rows", JsonArray(rows.map { row ->
        buildJsonObject {
            row.forEach { (key, value) -> put(key, value?.let { JsonPrimitive(it) } ?: JsonPrimitive("")) }
        }
    }))
    put("count", JsonPrimitive(rows.size))
}

package com.dailysatori.service.security

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.dailysatori.config.SettingKeys

data class SecretFieldSpec(
    val table: String,
    val column: String,
    val whereClause: String? = null,
)

object SecretFieldRegistry {
    val fields: List<SecretFieldSpec> = listOf(
        SecretFieldSpec(table = "ai_config", column = "api_token"),
        SecretFieldSpec(table = "mcp_server", column = "api_key"),
        SecretFieldSpec(table = "remote_news_source", column = "api_token"),
        SecretFieldSpec(table = "external_favorite_source", column = "auth_json"),
        SecretFieldSpec(table = "skill_config", column = "api_token"),
        SecretFieldSpec(table = "setting", column = "value", whereClause = "key = '${SettingKeys.weReadApiKey}'"),
    )
}

data class SecretProcessingResult(
    val scanned: Int = 0,
    val updated: Int = 0,
    val cleared: Int = 0,
    val encrypted: Int = 0,
) {
    operator fun plus(other: SecretProcessingResult): SecretProcessingResult =
        SecretProcessingResult(
            scanned = scanned + other.scanned,
            updated = updated + other.updated,
            cleared = cleared + other.cleared,
            encrypted = encrypted + other.encrypted,
        )
}

class SecretFieldProcessor(
    private val driver: SqlDriver,
    private val cipher: SecretValueCipher,
    private val fields: List<SecretFieldSpec> = SecretFieldRegistry.fields,
) {
    fun encryptPlaintextSecrets(): SecretProcessingResult =
        process { value ->
            when {
                value.isBlank() || cipher.isEncrypted(value) -> SecretTransform.Unchanged
                else -> SecretTransform.Updated(cipher.encrypt(value), encrypted = true)
            }
        }

    fun decryptSecretsForBackup(): SecretProcessingResult =
        process { value ->
            when {
                value.isBlank() || !cipher.isEncrypted(value) -> SecretTransform.Unchanged
                else -> {
                    val decrypted = cipher.decrypt(value)
                    if (decrypted != value) SecretTransform.Updated(decrypted) else SecretTransform.Unchanged
                }
            }
        }

    fun clearUnrecoverableEncryptedSecrets(): SecretProcessingResult =
        process { value ->
            when {
                value.isBlank() || !cipher.isEncrypted(value) -> SecretTransform.Unchanged
                cipher.decrypt(value) == value -> SecretTransform.Updated("", cleared = true)
                else -> SecretTransform.Unchanged
            }
        }

    fun prepareRestoredSecrets(): SecretProcessingResult {
        val cleared = clearUnrecoverableEncryptedSecrets()
        val encrypted = encryptPlaintextSecrets()
        return SecretProcessingResult(
            scanned = cleared.scanned + encrypted.scanned,
            updated = cleared.updated + encrypted.updated,
            cleared = cleared.cleared,
            encrypted = encrypted.encrypted,
        )
    }

    private fun process(transform: (String) -> SecretTransform): SecretProcessingResult {
        var result = SecretProcessingResult()
        fields.forEach { field ->
            readRows(field).forEach { row ->
                result = result.copy(scanned = result.scanned + 1)
                when (val update = transform(row.value)) {
                    SecretTransform.Unchanged -> Unit
                    is SecretTransform.Updated -> {
                        updateRow(field, row.rowId, update.value)
                        result = result.copy(
                            updated = result.updated + 1,
                            cleared = result.cleared + if (update.cleared) 1 else 0,
                            encrypted = result.encrypted + if (update.encrypted) 1 else 0,
                        )
                    }
                }
            }
        }
        return result
    }

    private fun readRows(field: SecretFieldSpec): List<SecretRow> {
        val where = listOfNotNull(
            field.whereClause,
            "${field.quotedColumn()} IS NOT NULL",
            "${field.quotedColumn()} <> ''",
        ).joinToString(" AND ")
        val sql = "SELECT rowid, ${field.quotedColumn()} FROM ${field.quotedTable()} WHERE $where"
        return driver.executeQuery(0, sql, { cursor ->
            val rows = mutableListOf<SecretRow>()
            while (cursor.next().value) {
                rows.add(SecretRow(cursor.getLong(0) ?: 0L, cursor.getString(1).orEmpty()))
            }
            QueryResult.Value(rows)
        }, 0).value
    }

    private fun updateRow(field: SecretFieldSpec, rowId: Long, value: String) {
        val sql = "UPDATE ${field.quotedTable()} SET ${field.quotedColumn()} = '${value.sqlEscaped()}' WHERE rowid = $rowId"
        driver.execute(null, sql, 0, null)
    }

    private fun SecretFieldSpec.quotedTable(): String = table.sqlIdentifier()
    private fun SecretFieldSpec.quotedColumn(): String = column.sqlIdentifier()
    private fun String.sqlIdentifier(): String = "\"${replace("\"", "\"\"")}\""
    private fun String.sqlEscaped(): String = replace("'", "''")

    private data class SecretRow(val rowId: Long, val value: String)

    private sealed interface SecretTransform {
        data object Unchanged : SecretTransform
        data class Updated(
            val value: String,
            val cleared: Boolean = false,
            val encrypted: Boolean = false,
        ) : SecretTransform
    }
}

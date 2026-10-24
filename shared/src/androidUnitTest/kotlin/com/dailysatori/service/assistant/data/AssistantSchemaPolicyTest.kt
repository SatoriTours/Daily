package com.dailysatori.service.assistant.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.*

class AssistantSchemaPolicyTest {
    @Test fun everyCurrentTableAndFieldIsReviewedAndNewFieldsAreClosed() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            fun strings(sql: String) = driver.executeQuery(null, sql, { c ->
                val rows = mutableListOf<String>()
                while(c.next().value) rows += c.getString(0)!!
                QueryResult.Value(rows)
            }, 0).value
            val actual = strings("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")
                .associateWith { table -> strings("SELECT name FROM pragma_table_info('$table')").toSet() }
            assertEquals(AssistantSchemaPolicy.columns, actual, "Schema changed: review every added/changed field")
            for ((table, fields) in actual) for (field in fields) assertNotNull(AssistantSchemaPolicy.classify(table, field))
            assertEquals(AssistantFieldPolicy.DENIED, AssistantSchemaPolicy.classify("article", "new_unreviewed_secret"))
            assertEquals(AssistantFieldPolicy.DENIED, AssistantSchemaPolicy.classify("new_table", "content"))
            listOf("session", "phone_message", "bookkeeping_entry", "sms_reminder_source").forEach { table ->
                actual[table]!!.forEach { assertEquals(AssistantFieldPolicy.DENIED, AssistantSchemaPolicy.classify(table, it)) }
            }
            for ((table, field) in listOf("ai_config" to "api_token", "mcp_server" to "api_key", "external_favorite_source" to "auth_json"))
                assertEquals(AssistantFieldPolicy.DENIED, AssistantSchemaPolicy.classify(table, field))
        }
    }
}

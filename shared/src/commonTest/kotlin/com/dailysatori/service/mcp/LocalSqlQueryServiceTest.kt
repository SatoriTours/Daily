package com.dailysatori.service.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalSqlQueryServiceTest {
    @Test
    fun acceptsSafeSelectFromAllowedTable() {
        val result = validateLocalSql("SELECT id, content FROM diary LIMIT 10")

        assertEquals(true, result.isValid)
        assertEquals("SELECT id, content FROM diary LIMIT 10", result.sql)
    }

    @Test
    fun rejectsMutatingStatements() {
        val result = validateLocalSql("DELETE FROM diary")

        assertFalse(result.isValid)
        assertTrue(result.error.orEmpty().contains("只允许 SELECT"))
    }

    @Test
    fun rejectsUnknownTables() {
        val result = validateLocalSql("SELECT token FROM ai_config LIMIT 5")

        assertFalse(result.isValid)
        assertTrue(result.error.orEmpty().contains("不允许查询表"))
    }

    @Test
    fun wrapsSelectWithoutLimit() {
        val result = validateLocalSql("SELECT id, content FROM diary")

        assertEquals(true, result.isValid)
        assertEquals("SELECT * FROM (SELECT id, content FROM diary) LIMIT 100", result.sql)
    }

    @Test
    fun exposesSchemaWithoutSensitiveAiConfig() {
        val schema = localSqlToolSchemaText()

        assertTrue(schema.contains("diary"))
        assertTrue(schema.contains("article"))
        assertFalse(schema.contains("ai_config"))
        assertFalse(schema.contains("api_token"))
    }
}

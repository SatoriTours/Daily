package com.dailysatori.service.skill

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.SkillConfigRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkillRegistryTest {
    @Test
    fun exposesWeReadToolNamesForBuiltInSkill() {
        val tools = builtInWeReadToolNames()

        assertEquals(
            listOf("weread_search_books", "weread_get_book_info", "weread_get_chapters", "weread_get_reviews"),
            tools,
        )
    }

    @Test
    fun genericCustomSkillToolSchemaMentionsRequiredArguments() {
        val schema = buildCallExternalSkillToolDefinition().toString()

        assertTrue(schema.contains("call_external_skill"))
        assertTrue(schema.contains("skill_id"))
        assertTrue(schema.contains("api_name"))
        assertTrue(schema.contains("params_json"))
    }

    @Test
    fun enabledSkillCountReturnsEnabledRepositoryRows() = withRegistry { registry, repository ->
        repository.insert("Enabled One", "", "https://example.com/one", "", "1.0.0", enabled = 1)
        repository.insert("Disabled", "", "https://example.com/two", "", "1.0.0", enabled = 0)
        repository.insert("Enabled Two", "", "https://example.com/three", "", "1.0.0", enabled = 1)

        assertEquals(2, registry.enabledSkillCount())
    }

    @Test
    fun buildToolDefinitionsContainsWeReadAndExternalSkillTools() = withRegistry { registry, _ ->
        val schema = registry.buildToolDefinitions().toString()

        listOf(
            "weread_search_books",
            "weread_get_book_info",
            "weread_get_chapters",
            "weread_get_reviews",
            "call_external_skill",
        ).forEach { toolName -> assertTrue(schema.contains(toolName), "Missing tool: $toolName") }
    }

    private fun withRegistry(test: (SkillRegistry, SkillConfigRepository) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        try {
            val db = DailySatoriDatabase(driver)
            val repository = SkillConfigRepository(
                db = db,
                encryptSecret = { value -> if (value.isBlank()) value else "test-encrypted:$value" },
                decryptSecret = { value -> value.removePrefix("test-encrypted:") },
            )
            test(SkillRegistry(repository), repository)
        } finally {
            driver.close()
        }
    }
}

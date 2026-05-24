package com.dailysatori.service.skill

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
}

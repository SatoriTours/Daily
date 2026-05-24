package com.dailysatori.service.skill

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.SkillConfigRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
    fun genericCustomSkillToolSchemaDefinesRequiredArguments() {
        val function = buildCallExternalSkillToolDefinition().functionObject()
        val parameters = function.parametersObject()
        val properties = parameters.propertiesObject()

        assertEquals("call_external_skill", function.stringValue("name"))
        assertEquals("object", parameters.stringValue("type"))
        assertEquals(listOf("skill_id", "api_name", "params_json"), parameters.requiredNames())
        assertEquals("integer", properties.propertyType("skill_id"))
        assertEquals("string", properties.propertyType("api_name"))
        assertEquals("string", properties.propertyType("params_json"))
    }

    @Test
    fun enabledSkillCountReturnsEnabledRepositoryRows() = withRegistry { registry, repository ->
        repository.insert("Enabled One", "", "https://example.com/one", "", "1.0.0", enabled = 1)
        repository.insert("Disabled", "", "https://example.com/two", "", "1.0.0", enabled = 0)
        repository.insert("Enabled Two", "", "https://example.com/three", "", "1.0.0", enabled = 1)

        assertEquals(2, registry.enabledSkillCount())
    }

    @Test
    fun buildToolDefinitionsReturnsNoToolsWhenNoSkillsAreEnabled() = withRegistry { registry, repository ->
        repository.insert(
            name = "Disabled WeRead",
            description = "",
            gatewayUrl = "https://example.com/weread",
            apiToken = "",
            skillVersion = "1.0.0",
            enabled = 0,
            builtin = 1,
            templateId = BuiltInSkillTemplates.weRead,
        )
        repository.insert("Disabled Custom", "", "https://example.com/custom", "", "1.0.0", enabled = 0)

        assertEquals(emptyList(), registry.buildToolDefinitions())
    }

    @Test
    fun buildToolDefinitionsReturnsWeReadToolsOnlyForEnabledBuiltInWeRead() = withRegistry { registry, repository ->
        repository.insert(
            name = "WeRead",
            description = "",
            gatewayUrl = "https://example.com/weread",
            apiToken = "",
            skillVersion = "1.0.0",
            enabled = 1,
            builtin = 1,
            templateId = BuiltInSkillTemplates.weRead,
        )
        val toolsByName = registry.toolsByName()

        assertEquals(
            builtInWeReadToolNames().toSet(),
            toolsByName.keys,
        )

        builtInWeReadToolNames().forEach { toolName ->
            val parameters = toolsByName.getValue(toolName).functionObject().parametersObject()
            val properties = parameters.propertiesObject()
            assertEquals("object", parameters.stringValue("type"))
            assertFalse(properties.containsKey("skill_id"), "$toolName should not expose skill_id")
            assertFalse(properties.containsKey("api_name"), "$toolName should not expose api_name")
            assertFalse(properties.containsKey("params_json"), "$toolName should not expose params_json")
        }
    }

    @Test
    fun buildToolDefinitionsIncludesExternalToolForEnabledCustomSkill() = withRegistry { registry, repository ->
        repository.insert("Enabled Custom", "", "https://example.com/custom", "", "1.0.0", enabled = 1)
        val externalTool = registry.toolsByName().getValue("call_external_skill")
        val parameters = externalTool.functionObject().parametersObject()
        val properties = parameters.propertiesObject()

        assertEquals(setOf("call_external_skill"), registry.toolsByName().keys)
        assertEquals(listOf("skill_id", "api_name", "params_json"), parameters.requiredNames())
        assertEquals(
            setOf("skill_id", "api_name", "params_json"),
            properties.keys,
        )
        assertEquals("integer", properties.propertyType("skill_id"))
        assertEquals("string", properties.propertyType("api_name"))
        assertEquals("string", properties.propertyType("params_json"))
    }

    @Test
    fun buildToolDefinitionsDoesNotIncludeExternalToolForDisabledCustomSkill() = withRegistry { registry, repository ->
        repository.insert("Disabled Custom", "", "https://example.com/custom", "", "1.0.0", enabled = 0)

        assertEquals(emptyList(), registry.buildToolDefinitions())
    }

    private fun JsonObject.functionObject(): JsonObject = getValue("function").jsonObject

    private fun JsonObject.parametersObject(): JsonObject = getValue("parameters").jsonObject

    private fun JsonObject.propertiesObject(): JsonObject = getValue("properties").jsonObject

    private fun JsonObject.stringValue(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.requiredNames(): List<String> = getValue("required").jsonArray.map { it.jsonPrimitive.content }

    private fun JsonObject.propertyType(name: String): String = getValue(name).jsonObject.stringValue("type")

    private fun JsonObject.getValue(key: String) = get(key) ?: error("Missing JSON key: $key")

    private fun SkillRegistry.toolsByName(): Map<String, JsonObject> =
        buildToolDefinitions().associateBy { it.functionObject().stringValue("name") }

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

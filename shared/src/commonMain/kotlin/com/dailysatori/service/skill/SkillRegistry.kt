package com.dailysatori.service.skill

import com.dailysatori.data.repository.SkillConfigRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject

class SkillRegistry(
    private val skillConfigRepository: SkillConfigRepository,
) {
    fun enabledSkillCount(): Int = skillConfigRepository.getEnabled().size

    fun buildToolDefinitions(): List<JsonObject> =
        builtInWeReadToolNames().map(::buildBuiltInWeReadToolDefinition) + buildCallExternalSkillToolDefinition()
}

fun builtInWeReadToolNames(): List<String> = listOf(
    "weread_search_books",
    "weread_get_book_info",
    "weread_get_chapters",
    "weread_get_reviews",
)

fun buildCallExternalSkillToolDefinition(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("function"))
    putJsonObject("function") {
        put("name", JsonPrimitive("call_external_skill"))
        put("description", JsonPrimitive("调用用户配置的外部 Skill。参数必须包含 skill_id、api_name 和 params_json。"))
        putJsonObject("parameters") {
            put("type", JsonPrimitive("object"))
            put(
                "required",
                JsonArray(listOf(JsonPrimitive("skill_id"), JsonPrimitive("api_name"), JsonPrimitive("params_json"))),
            )
            putJsonObject("properties") {
                putJsonObject("skill_id") { put("type", JsonPrimitive("integer")) }
                putJsonObject("api_name") { put("type", JsonPrimitive("string")) }
                putJsonObject("params_json") { put("type", JsonPrimitive("string")) }
            }
        }
    }
}

private fun buildBuiltInWeReadToolDefinition(name: String): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("function"))
    putJsonObject("function") {
        put("name", JsonPrimitive(name))
        put("description", JsonPrimitive("微信读书内置 Skill 工具。"))
        putJsonObject("parameters") {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {}
        }
    }
}

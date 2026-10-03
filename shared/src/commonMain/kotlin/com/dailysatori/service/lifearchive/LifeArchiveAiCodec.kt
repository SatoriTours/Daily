package com.dailysatori.service.lifearchive

import com.dailysatori.service.ai.unwrapAiJsonResponse
import kotlinx.serialization.json.*

class LifeArchiveAiCodec {
    fun decode(
        response: String,
        categories: List<LifeArchiveCategory>,
        editing: Boolean,
        importing: Boolean = false,
    ): List<LifeArchiveRecord> = try {
        require(response.length <= 100_000)
        val root = Json.parseToJsonElement(unwrapAiJsonResponse(response)) as JsonObject
        require(root.keys == setOf("records"))
        val records = root["records"] as JsonArray
        require(records.size <= 20 && (records.isNotEmpty() || importing))
        require(!editing || records.size == 1)
        records.map { parseRecord(it as JsonObject, categories.map { category -> category.id }.toSet(), importing) }
    } catch (_: Exception) {
        throw LifeArchiveInvalidResponse()
    }

    private fun parseRecord(value: JsonObject, categoryIds: Set<String>, importing: Boolean): LifeArchiveRecord {
        val allowed = setOf("title", "categoryId", "body", "fields") + if (importing) setOf("sourceReminderId") else emptySet()
        require((value.keys - allowed).isEmpty())
        val title = value.string("title").trim().also { require(it.isNotEmpty() && it.length <= 120) }
        val category = value.string("categoryId").also { require(it in categoryIds) }
        val body = value.string("body").also { require(it.length <= 20_000) }
        val rawFields = value["fields"] as JsonArray
        require(rawFields.size <= 50)
        val fields = rawFields.map { raw ->
            val field = raw as JsonObject
            require(field.keys == setOf("name", "value"))
            val name = field.string("name").trim().also { require(it.isNotBlank() && it.length <= 80) }
            LifeArchiveField(name, field.string("value").also { require(it.length <= 20_000) })
        }
        require(fields.map { it.name.lowercase() }.distinct().size == fields.size)
        val source = if (importing) value.string("sourceReminderId").also { require(it.isNotBlank()) } else null
        return LifeArchiveRecord(newLifeArchiveId(), title, category, body, fields, sourceReminderId = source)
    }

    private fun JsonObject.string(name: String): String =
        (get(name) as JsonPrimitive).also { require(it.isString) }.content
}

package com.dailysatori.service.lifearchive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LifeArchiveAiCodecTest {
    private val categories = listOf(LifeArchiveCategory("domain", "域名"), LifeArchiveCategory("insurance", "保险"))
    private val codec = LifeArchiveAiCodec()
    private val record = """{"title":"example.com","categoryId":"domain","body":"","fields":[{"name":"管理平台","value":"Spaceship"}]}"""

    @Test
    fun decodesDynamicFieldsAndCustomCategoriesFromFencedJson() {
        val response = "```json\n{\"records\":[$record]}\n```"
        val draft = codec.decode(response, categories, editing = false).single()
        assertEquals("Spaceship", draft.fields.single().value)
        assertEquals("insurance", codec.decode(response.replace("domain", "insurance"), categories, false).single().categoryId)
    }

    @Test
    fun rejectsUnknownCategoryDuplicateFieldsAndNonStringValues() {
        listOf(
            record.replace("domain", "unknown"),
            record.replace("\"value\":\"Spaceship\"", "\"value\":123"),
            record.replace("\"fields\":[", "\"fields\":[{\"name\":\"管理平台\",\"value\":\"other\"},"),
        ).forEach { invalid -> assertFailsWith<LifeArchiveInvalidResponse> { codec.decode("{\"records\":[$invalid]}", categories, false) } }
    }

    @Test
    fun editingAcceptsExactlyOneRecordAndRejectsForgedMetadata() {
        listOf("{\"records\":[]}", "{\"records\":[$record,$record]}",
            "{\"records\":[${record.replaceFirst("{", "{\"id\":\"forged\",")}]}").forEach {
            assertFailsWith<LifeArchiveInvalidResponse> { codec.decode(it, categories, true) }
        }
    }

    @Test
    fun rejectsPlainTextAndResponseAndRecordLimits() {
        listOf("no JSON", " ".repeat(100_001), "{\"records\":[${List(21) { record }.joinToString()}]}").forEach {
            assertFailsWith<LifeArchiveInvalidResponse> { codec.decode(it, categories, false) }
        }
    }

    @Test
    fun rejectsEmptyFieldNamesAndTooManyFields() {
        val fields = List(51) { """{"name":"field$it","value":"value"}""" }.joinToString()
        listOf(record.replace("管理平台", " "), record.replace("[{\"name\":\"管理平台\",\"value\":\"Spaceship\"}]", "[$fields]")).forEach {
            assertFailsWith<LifeArchiveInvalidResponse> { codec.decode("{\"records\":[$it]}", categories, false) }
        }
    }
}

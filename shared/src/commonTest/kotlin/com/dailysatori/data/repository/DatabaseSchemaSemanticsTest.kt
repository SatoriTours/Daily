package com.dailysatori.data.repository

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class DatabaseSchemaSemanticsTest {
    @Test
    fun repeatedLookingFieldsHaveDocumentedSemantics() {
        val doc = File("../docs/database-schema-semantics.md").readText()

        assertTrue(doc.contains("article.ai_markdown_content"))
        assertTrue(doc.contains("article.original_markdown_content"))
        assertTrue(doc.contains("article.cover_image"))
        assertTrue(doc.contains("article.cover_image_url"))
        assertTrue(doc.contains("external_favorite_source.last_error"))
        assertTrue(doc.contains("external_favorite_source.last_error_code"))
        assertTrue(doc.contains("external_favorite_source.last_error_message"))
        assertTrue(doc.contains("external_favorite_item.provider"))
    }
}

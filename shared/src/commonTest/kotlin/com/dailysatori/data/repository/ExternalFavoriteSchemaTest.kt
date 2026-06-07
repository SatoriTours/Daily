package com.dailysatori.data.repository

import kotlin.test.Test
import kotlin.test.assertTrue

class ExternalFavoriteSchemaTest {
    @Test
    fun externalFavoriteSchemaDefinesSourceScopedIdentity() {
        val schema = this::class.java.classLoader
            ?.getResource("sqldelight/com/dailysatori/shared/db/DailySatori.sq")
            ?.readText()
            ?: java.io.File("src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()

        assertTrue("CREATE TABLE external_favorite_source" in schema)
        assertTrue("CREATE TABLE external_favorite_item" in schema)
        assertTrue("UNIQUE(source_id, external_id)" in schema)
        assertTrue("normalized_json TEXT NOT NULL DEFAULT ''" in schema)
        assertTrue("debug_json TEXT NOT NULL DEFAULT ''" in schema)
    }
}

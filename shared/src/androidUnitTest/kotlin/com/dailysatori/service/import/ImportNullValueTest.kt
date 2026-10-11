package com.dailysatori.service.import

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.platform.FileManager
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.*

class ImportNullValueTest {
    @Test
    fun jsonNullDoesNotBecomeATextValueButLiteralStringsArePreserved() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            val service = ImportService(DailySatoriDatabase(driver), driver, FileManager())
            val source = Json.parseToJsonElement("""{"path":null,"title":"null","cover_image":"images/photo.jpg"}""").jsonObject

            service.run {
                assertNull(source.getString("path"))
                assertNull(source.getString("missing"))
                assertEquals("null", source.getString("title"))
                assertEquals("images/photo.jpg", source.getString("cover_image"))
            }
        } finally { driver.close() }
    }
}

package com.dailysatori.service.assistant.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.service.assistant.AssistantPrivacyRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class AssistantBusinessCatalogTest {
    @Test fun representativeBusinessSourcesSearchReadAndCountWithoutOpeningProtectedPayloads() = runBlocking<Unit> {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val data = AssistantDataGateway(driver)
            // Fixture construction is host-only; production tools never expose schema discovery.
            fun insert(table: String, extra: Map<String, Any> = emptyMap()) {
                val required = driver.executeQuery(null, "PRAGMA table_info($table)", { c ->
                    val values = linkedMapOf<String, Any>()
                    while (c.next().value) {
                        val name = c.getString(1)!!; val type = c.getString(2).orEmpty()
                        if (c.getLong(3) == 1L && c.getString(4) == null && !(c.getLong(5) == 1L && type == "INTEGER"))
                            values[name] = if (type.contains("INT")) 1L else if (name == "id") "fixture" else "检索哨兵"
                    }
                    QueryResult.Value(values)
                }, 0).value
                required.putAll(extra)
                driver.execute(null, "INSERT INTO $table(${required.keys.joinToString()}) VALUES(${required.keys.joinToString { "?" }})", required.size) {
                    required.values.forEachIndexed { i, v -> if (v is Number) bindLong(i, v.toLong()) else bindString(i, v.toString()) }
                }
            }
            insert("article", mapOf("ai_markdown_content" to "检索哨兵文章"))
            insert("diary", mapOf("content" to "检索哨兵日记"))
            insert("book", mapOf("introduction" to "检索哨兵书籍"))
            insert("book_viewpoint", mapOf("content" to "检索哨兵笔记"))
            insert("chat_conversation", mapOf("content" to "检索哨兵历史", "role" to "user"))
            insert("reminder", mapOf("content" to "检索哨兵提醒"))
            insert("weekly_summary", mapOf("content" to "检索哨兵总结"))
            insert("external_favorite_item", mapOf("text" to "检索哨兵收藏"))
            insert("idea_topic", mapOf("description" to "检索哨兵点子"))
            val sources = listOf("article", "diary", "book", "book_viewpoint", "chat", "reminder", "weekly_summary", "external_favorite_item", "idea_topic")
            for (source in sources) {
                val rows = data.search("检索哨兵", listOf(source))["rows"]!!.jsonArray
                assertEquals(1, rows.size, source)
                val id = rows.single().jsonObject["id"]!!.jsonPrimitive.content
                assertEquals(JsonPrimitive(true), data.read(source, id)["ok"], source)
                assertEquals(1, data.aggregate(AssistantAggregate(source))["rows"]!!.jsonArray.single().jsonObject["count"]!!.jsonPrimitive.int)
            }
            insert("idea_topic_source", mapOf("topic_id" to "fixture", "original_topic_id" to "fixture", "source_type" to "diary", "source_record_id" to "1", "snapshot_id" to com.dailysatori.data.repository.IdeaSnapshots(com.dailysatori.shared.db.DailySatoriDatabase(driver)).intern("{}")))
            AssistantPrivacyRepository(driver).exclude("diary", "1")
            assertTrue(data.search("检索哨兵", listOf("idea_topic"))["rows"]!!.jsonArray.isEmpty())
            for (source in listOf("session", "phone_message", "sms_reminder_source", "bookkeeping_entry", "sqlite_master"))
                assertEquals(JsonPrimitive(false), data.query(AssistantQuery(source))["ok"], source)
        }
    }
}

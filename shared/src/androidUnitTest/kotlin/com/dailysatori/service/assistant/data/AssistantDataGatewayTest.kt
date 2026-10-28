package com.dailysatori.service.assistant.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.service.assistant.AssistantPrivacyRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class AssistantDataGatewayTest {
    @Test fun diaryTagProjectionUsesCurrentRelationsNotRetiredCsv() = runBlocking<Unit> {
        withGateway { data,driver ->
            val db=DailySatoriDatabase(driver)
            val diaries=com.dailysatori.data.repository.DiaryRepository(db,driver)
            diaries.insert("body","current")
            assertFails { driver.execute(null,"UPDATE diary SET tags='retired'",0) }
            assertEquals("current",data.query(AssistantQuery("diary",fields=listOf("tags")))["rows"]!!
                .jsonArray.single().jsonObject["tags"]!!.jsonPrimitive.content)
            assertTrue(data.search("retired",sources=listOf("diary"))["rows"]!!.jsonArray.isEmpty())
            assertEquals(1,data.search("current",sources=listOf("diary"))["rows"]!!.jsonArray.size)
            assertFalse(data.query(AssistantQuery("diary_tag_history"))["ok"]!!.jsonPrimitive.boolean)
        }
    }

    @Test
    fun everyCatalogProjectionCanActuallyBeQueriedAndNewFieldsRemainClosed() = runBlocking<Unit> {
        withGateway { data, driver ->
            val sources = data.catalog()["sources"]!!.jsonArray.map { it.jsonObject["source"]!!.jsonPrimitive.content }
            sources.forEach { assertTrue(data.query(AssistantQuery(it))["ok"]!!.jsonPrimitive.boolean, it) }
            driver.execute(null, "ALTER TABLE diary ADD COLUMN future_private_value TEXT", 0)
            assertFalse(data.query(AssistantQuery("diary", fields = listOf("future_private_value")))["ok"]!!.jsonPrimitive.boolean)
        }
    }

    @Test
    fun unknownSourcesFieldsAndOperatorsFailClosed() = runBlocking<Unit> {
        withGateway { data, _ ->
            assertFalse(data.query(AssistantQuery("sqlite_master"))["ok"]!!.jsonPrimitive.boolean)
            assertFalse(data.query(AssistantQuery("ai_config", fields = listOf("api_token")))["ok"]!!.jsonPrimitive.boolean)
            assertFalse(data.query(AssistantQuery("diary", filters = listOf(AssistantFilter("content", "raw_sql", JsonPrimitive("1=1")))))["ok"]!!.jsonPrimitive.boolean)
            assertFalse(data.aggregate(AssistantAggregate("diary", measures = listOf("count; DROP TABLE diary")))["ok"]!!.jsonPrimitive.boolean)
            assertFalse(data.catalog().toString().contains("api_token"))
        }
    }

    @Test
    fun queryValuesAreBoundLiterallyIncludingSqlAndLikeMetacharacters() = runBlocking<Unit> {
        withGateway { data, driver ->
            driver.execute(null, "INSERT INTO diary(content,created_at,updated_at) VALUES ('普通记录',1,1),('100%完成',2,2)", 0)
            assertEquals(0, data.query(AssistantQuery("diary", filters = listOf(AssistantFilter("content", "eq", JsonPrimitive("x' OR 1=1 --")))))["rows"]!!.jsonArray.size)
            assertEquals(1, data.query(AssistantQuery("diary", filters = listOf(AssistantFilter("content", "contains", JsonPrimitive("%")))))["rows"]!!.jsonArray.size)
            assertEquals(2, data.query(AssistantQuery("diary"))["rows"]!!.jsonArray.size)
        }
    }

    @Test
    fun exclusionsApplyToEveryReadPathAndDerivedMemory() = runBlocking<Unit> {
        withGateway { data, driver ->
            driver.execute(null, "INSERT INTO diary(id,content,created_at,updated_at) VALUES (1,'去香山爬山',1,1)", 0)
            driver.execute(null, "INSERT INTO memory_entry(type,source_type,source_id,title,content,created_at,updated_at) VALUES ('content','diary',1,'归纳','香山',1,1)", 0)
            AssistantPrivacyRepository(driver).exclude("diary", "1")
            assertEquals(0, data.query(AssistantQuery("diary"))["rows"]!!.jsonArray.size)
            assertEquals(0, data.search("香山")["rows"]!!.jsonArray.size)
            assertEquals(0, data.aggregate(AssistantAggregate("diary"))["rows"]!!.jsonArray.single().jsonObject["count"]!!.jsonPrimitive.int)
            assertFalse(data.read("diary", "1")["ok"]!!.jsonPrimitive.boolean)
            // A stale worker must not make the forgotten source visible by recreating its derived row.
            driver.execute(null, "INSERT INTO memory_entry(type,source_type,source_id,title,content,created_at,updated_at) VALUES ('content','diary',1,'迟到的归纳','香山',2,2)", 0)
            assertEquals(0, data.query(AssistantQuery("memory_entry"))["rows"]!!.jsonArray.size)
        }
    }

    @Test
    fun chineseSearchPaginationTextIdsAndReadBoundsWork() = runBlocking<Unit> {
        withGateway { data, driver ->
            repeat(25) { n ->
                driver.execute(null, "INSERT INTO diary(content,created_at,updated_at) VALUES (?, ?, ?)", 3) {
                    bindString(0, "香山" + "长文".repeat(4000)); bindLong(1, n.toLong()); bindLong(2, n.toLong())
                }
            }
            val first = data.query(AssistantQuery("diary", limit = 10))
            assertEquals(10, first["rows"]!!.jsonArray.size)
            assertEquals(10, first["next_offset"]!!.jsonPrimitive.int)
            assertEquals(5, data.search("香山", sources = listOf("diary"), limit = 10, offset = 20)["rows"]!!.jsonArray.size)
            assertTrue(data.read("diary", "1")["content"]!!.jsonPrimitive.content.length <= 6000)
            assertTrue(data.read("diary", "1")["has_more"]!!.jsonPrimitive.boolean)
            driver.execute(null,"INSERT INTO diary(content,created_at,updated_at) VALUES (?,1,1)",1) { bindString(0,"普通记录".repeat(4000)+"尾部独特喜好") }
            val tail = data.search("尾部独特喜好",sources=listOf("diary"))["rows"]!!.jsonArray.single().jsonObject
            assertTrue(tail["snippet"]!!.jsonPrimitive.content.contains("尾部独特喜好"))
            assertTrue(tail["offset"]!!.jsonPrimitive.int > 6000)
            assertEquals(26, data.query(AssistantQuery("diary", limit = Int.MAX_VALUE))["rows"]!!.jsonArray.size)
            driver.execute(null, "INSERT INTO reminder(id,content,status,start_date,end_date,first_reminder_time,active_day_rule,time_zone_id,policy_snapshot_id,created_at,updated_at) VALUES ('todo-uuid','去香山','ACTIVE','2026-10-24','2026-10-24','10:00','daily','Asia/Shanghai',?,1,1)", 1) {
                bindString(0,com.dailysatori.data.repository.ReminderPolicySnapshots(com.dailysatori.shared.db.DailySatoriDatabase(driver)).intern("{}"))
            }
            assertTrue(data.read("reminder", "todo-uuid")["ok"]!!.jsonPrimitive.boolean)
            AssistantPrivacyRepository(driver).exclude("reminder", "todo-uuid")
            assertFalse(data.read("reminder", "todo-uuid")["ok"]!!.jsonPrimitive.boolean)
        }
    }

    @Test
    fun configuredCredentialsAreAbsentFromCatalogQueriesSearchAndPastedText() = runBlocking<Unit> {
        withGateway { data, driver ->
            val secret = "sk-SENTINEL-credential-12345"
            driver.execute(null, "INSERT INTO ai_config(api_address,api_token,model_name,created_at,updated_at) VALUES ('https://example.test',?,'safe-model',1,1)", 1) { bindString(0, secret) }
            driver.execute(null, "INSERT INTO setting(key,value,created_at,updated_at) VALUES ('speech_config',?,1,1)", 1) { bindString(0, "{\"apiKey\":\"nested-speech-secret\"}") }
            driver.execute(null, "INSERT INTO diary(content,created_at,updated_at) VALUES (?,1,1)", 1) { bindString(0, secret) }
            assertFalse(data.query(AssistantQuery("ai_config")).toString().contains(secret))
            assertFalse(data.read("diary", "1").toString().contains(secret))
            assertEquals(0, data.search(secret)["rows"]!!.jsonArray.size)
            val redacted = data.redact("$secret nested-speech-secret https://user:password@example.test/a?token=unknown#secret")
            assertFalse(redacted.contains(secret))
            assertFalse(redacted.contains("nested-speech-secret"))
            assertFalse(redacted.contains("password"))
            assertFalse(redacted.contains("unknown"))
            val raw = driver.executeQuery(null, "SELECT content FROM diary WHERE id=1",
                { app.cash.sqldelight.db.QueryResult.Value(it.let { cursor -> cursor.next().value; cursor.getString(0) }) }, 0).value
            assertEquals(secret, raw) // Model projections must not mutate the user's original input.
            assertTrue(data.read("diary", "1")["ok"]!!.jsonPrimitive.boolean)
        }
    }

    private suspend fun withGateway(block: suspend (AssistantDataGateway, JdbcSqliteDriver) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use {
            DailySatoriDatabase.Schema.create(it)
            block(AssistantDataGateway(it), it)
        }
    }
}

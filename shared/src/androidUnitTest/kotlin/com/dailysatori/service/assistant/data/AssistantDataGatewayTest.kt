package com.dailysatori.service.assistant.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.service.assistant.AssistantPrivacyRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
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

    @Test
    fun articleContentPrefersLatestTranslationThenSummaryThenLegacyColumns() = runBlocking<Unit> {
        withGateway { data, driver ->
            driver.execute(null, "INSERT INTO article(id,title,ai_content,ai_markdown_content,original_markdown_content,created_at,updated_at) " +
                "VALUES (1,'标题','旧 AI 摘要','旧 AI 正文','原始正文',1,1)", 0)
            assertEquals("旧 AI 正文", articleContent(data, 1))
            // A source row without any result must not hide the legacy columns.
            driver.execute(null, "INSERT INTO article_ai_source(article_id,body_hash,body_kind,cache_hits) VALUES (1,'hash','remote',0)", 0)
            assertEquals("旧 AI 正文", articleContent(data, 1))
            insertResult(driver, "summary", 1, "摘要一版")
            assertEquals("摘要一版", articleContent(data, 1))
            insertResult(driver, "translation", 1, "译文一版")
            assertEquals("译文一版", articleContent(data, 1))
            insertResult(driver, "translation", 3, "译文三版")
            assertEquals("译文三版", articleContent(data, 1))
            // An empty newer row never wins over a real older one.
            insertResult(driver, "summary", 9, "")
            assertEquals("译文三版", articleContent(data, 1))
            assertEquals(1, data.search("译文三版", sources = listOf("article"))["rows"]!!.jsonArray.size)
            // The article source keeps its safe field boundary.
            assertFalse(data.query(AssistantQuery("article", fields = listOf("url")))["ok"]!!.jsonPrimitive.boolean)
        }
    }

    @Test
    fun opportunityItemsExposeAnalysisTextOnlyAndHonourDefaultPrivacy() = runBlocking<Unit> {
        withGateway { data, driver ->
            val analysis = Json.encodeToString(
                com.dailysatori.service.opportunity.OpportunityDraft(
                    title = "机会标题", category = "产品", fact = "事实正文", relevance = "相关性",
                    action = "建议行动", caveat = "风险提示", quote = "原文引用",
                    preview = com.dailysatori.service.opportunity.OpportunityPreview("短标题", "值得关注", "可尝试开发"),
                ),
            )
            driver.execute(null, "INSERT INTO opportunity_source(id,payload_json) " +
                "VALUES ('src-1','{\"title\":\"原始标题\",\"url\":\"https://secret.example.test/a\"}')", 0)
            driver.execute(null, "INSERT INTO opportunity_item(id,identity,source_id,position,created_at,saved,ignored,saved_at,analysis_json) " +
                "VALUES ('opp-1','identity-1','src-1',0,123,1,0,NULL,?)", 1) { bindString(0, analysis) }

            val sources = data.catalog()["sources"]!!.jsonArray.map { it.jsonObject["source"]!!.jsonPrimitive.content }
            assertTrue(sources.contains("opportunity_item"))

            val row = data.query(AssistantQuery("opportunity_item"))["rows"]!!.jsonArray.single().jsonObject
            assertEquals("机会标题", row["title"]!!.jsonPrimitive.content)
            assertEquals("事实正文", row["fact"]!!.jsonPrimitive.content)
            assertEquals("可尝试开发", row["opportunity"]!!.jsonPrimitive.content)
            assertEquals("值得关注", row["reason"]!!.jsonPrimitive.content)
            assertEquals(1, row["saved"]!!.jsonPrimitive.int)

            val read = data.read("opportunity_item", "opp-1")
            assertTrue(read["ok"]!!.jsonPrimitive.boolean)
            val content = read["content"]!!.jsonPrimitive.content
            assertTrue(content.contains("机会标题") && content.contains("风险提示") && content.contains("原文引用"))
            assertFalse(read.toString().contains("secret.example.test"), "raw opportunity payload stays closed")

            assertEquals(1, data.search("机会标题", sources = listOf("opportunity_item"))["rows"]!!.jsonArray.size)
            assertEquals(1, data.aggregate(AssistantAggregate("opportunity_item"))["rows"]!!.jsonArray
                .single().jsonObject["count"]!!.jsonPrimitive.int)

            AssistantPrivacyRepository(driver).exclude("opportunity_item", "opp-1")
            assertEquals(0, data.query(AssistantQuery("opportunity_item"))["rows"]!!.jsonArray.size)
            assertEquals(0, data.search("机会标题", sources = listOf("opportunity_item"))["rows"]!!.jsonArray.size)
            assertFalse(data.read("opportunity_item", "opp-1")["ok"]!!.jsonPrimitive.boolean)
        }
    }

    @Test
    fun articleBackedIdeaTopicsFollowPrivacyAndArticleDeletion() = runBlocking<Unit> {
        withGateway { data, driver ->
            driver.execute(null, "INSERT INTO article(id,title,created_at,updated_at) VALUES (5,'来源文章',1,1)", 0)
            insertIdeaTopic(driver, "topic-article", "article", "5")
            insertIdeaTopic(driver, "topic-opportunity", "news_opportunity", "opp-unrelated")
            assertEquals(2, data.query(AssistantQuery("idea_topic"))["rows"]!!.jsonArray.size)

            AssistantPrivacyRepository(driver).exclude("article", "5")
            val afterExclusion = data.query(AssistantQuery("idea_topic"))["rows"]!!.jsonArray
                .map { it.jsonObject["id"]!!.jsonPrimitive.content }
            assertEquals(listOf("topic-opportunity"), afterExclusion)
            assertFalse(data.read("idea_topic", "topic-article")["ok"]!!.jsonPrimitive.boolean)
        }
        withGateway { data, driver ->
            driver.execute(null, "INSERT INTO article(id,title,created_at,updated_at) VALUES (6,'将被删除',1,1)", 0)
            insertIdeaTopic(driver, "topic-deleted", "article", "6")
            assertEquals(1, data.query(AssistantQuery("idea_topic"))["rows"]!!.jsonArray.size)
            driver.execute(null, "DELETE FROM article WHERE id=6", 0)
            assertEquals(0, data.query(AssistantQuery("idea_topic"))["rows"]!!.jsonArray.size)
            assertEquals(0, data.search("主题", sources = listOf("idea_topic"))["rows"]!!.jsonArray.size)
        }
    }

    @Test
    fun remoteOnlyIdeaTopicsStayVisibleWhateverHappensToLocalArticleIds() = runBlocking<Unit> {
        withGateway { data, driver ->
            insertIdeaTopic(driver, "topic-remote", "article", "remote:42")
            assertEquals(listOf("topic-remote"), topicIds(data))

            // A local article reusing the same number must never decide a remote snapshot's visibility.
            driver.execute(null, "INSERT INTO article(id,title,created_at,updated_at) VALUES (42,'本地文章',1,1)", 0)
            assertEquals(listOf("topic-remote"), topicIds(data))
            AssistantPrivacyRepository(driver).exclude("article", "42")
            assertEquals(listOf("topic-remote"), topicIds(data))
            driver.execute(null, "DELETE FROM article WHERE id=42", 0)
            assertEquals(listOf("topic-remote"), topicIds(data))

            assertTrue(data.read("idea_topic", "topic-remote")["ok"]!!.jsonPrimitive.boolean)
            assertEquals(1, data.search("主题", sources = listOf("idea_topic"))["rows"]!!.jsonArray.size)
        }
    }

    @Test
    fun ideaTopicWithBlankRecordIdIsNotMatchedAgainstLocalArticles() = runBlocking<Unit> {
        withGateway { data, driver ->
            insertIdeaTopic(driver, "topic-blank", "article", "")
            assertEquals(listOf("topic-blank"), topicIds(data))

            // A blank record id can never mean "local article", so local rows never decide its fate.
            driver.execute(null, "INSERT INTO article(id,title,created_at,updated_at) VALUES (42,'本地文章',1,1)", 0)
            AssistantPrivacyRepository(driver).exclude("article", "42")
            driver.execute(null, "DELETE FROM article WHERE id=42", 0)
            assertEquals(listOf("topic-blank"), topicIds(data))
        }
    }

    private suspend fun articleContent(data: AssistantDataGateway, articleId: Long): String =
        data.read("article", articleId.toString())["content"]!!.jsonPrimitive.content

    private fun insertResult(driver: JdbcSqliteDriver, kind: String, version: Long, markdown: String) {
        driver.execute(null, "INSERT INTO article_ai_result(body_hash,kind,version,target,markdown,partial) VALUES ('hash',?,?,'zh',?,0)", 3) {
            bindString(0, kind); bindLong(1, version); bindString(2, markdown)
        }
    }

    private suspend fun topicIds(data: AssistantDataGateway): List<String> =
        data.query(AssistantQuery("idea_topic"))["rows"]!!.jsonArray
            .map { it.jsonObject["id"]!!.jsonPrimitive.content }

    private fun insertIdeaTopic(driver: JdbcSqliteDriver, topicId: String, sourceType: String, recordId: String) {
        driver.execute(null, "INSERT INTO idea_source_snapshot(id,format_version,payload_json,sha256,created_at) VALUES (?,1,'{}','sha',1)", 1) {
            bindString(0, "snap-" + topicId)
        }
        driver.execute(null, "INSERT INTO idea_topic(id,title,status,created_at,updated_at) VALUES (?,'主题','pending_research',1,1)", 1) {
            bindString(0, topicId)
        }
        driver.execute(null, "INSERT INTO idea_topic_source(id,topic_id,original_topic_id,source_type,source_record_id,snapshot_id,captured_at,created_at,updated_at) " +
            "VALUES (?,?,?,?,?,?,1,1,1)", 6) {
            bindString(0, "src-" + topicId); bindString(1, topicId); bindString(2, topicId)
            bindString(3, sourceType); bindString(4, recordId); bindString(5, "snap-" + topicId)
        }
    }

    private suspend fun withGateway(block: suspend (AssistantDataGateway, JdbcSqliteDriver) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use {
            DailySatoriDatabase.Schema.create(it)
            block(AssistantDataGateway(it), it)
        }
    }
}

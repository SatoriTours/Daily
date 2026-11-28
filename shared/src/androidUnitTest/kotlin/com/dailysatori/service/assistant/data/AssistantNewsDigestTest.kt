package com.dailysatori.service.assistant.data

import ai.koog.agents.core.tools.Tool
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.service.assistant.AssistantPrivacyRepository
import com.dailysatori.service.assistant.ReadNewsArgs
import com.dailysatori.service.assistant.koogDataTools
import com.dailysatori.service.externalfavorites.sha256Hex
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.serialization.json.*
import kotlin.test.*

class AssistantNewsDigestTest {
    private val today = "2026-11-28"
    private val yesterday = "2026-11-27"
    private val zone = TimeZone.UTC
    private val secret = "SECRET-TOKEN-9f2c"
    private val privateTitle = "私有标题哨兵-绝不可见"
    private val privateBody = "私有正文哨兵-绝不可见"
    private val summaryTitle = "汇总标题哨兵-绝不可见"
    private val summaryBody = "汇总正文哨兵-绝不可见"

    private fun digest(driver: JdbcSqliteDriver) = AssistantNewsDigest(driver, today, zone)

    @Test
    fun todayPicksTheNewestReadableWindowOfThatSameDate() = withDb { driver ->
        summary(driver, today, "daily", 1_000, "failed", "")
        val morning = summary(driver, today, "0800", 2_000, "success", "上午要闻")
        val afternoon = summary(driver, today, "1330", 3_000, "success", "下午要闻")
        source(driver, morning, "R0", "remote_article", null, "上午标题", "上午摘要", null)
        source(driver, afternoon, "R1", "remote_article", null, "下午标题", "下午摘要", null)
        val result = digest(driver).read("today")
        assertEquals("1330", result["window"]!!.jsonPrimitive.content)
        assertEquals(today, result["summary_date"]!!.jsonPrimitive.content)
        assertTrue(result["is_today"]!!.jsonPrimitive.boolean)
        assertEquals("0800", digest(driver).read("", "0800")["window"]!!.jsonPrimitive.content)
        assertFalse(digest(driver).read("", "9900")["ok"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun datesAreParsedStrictlyAndInvalidOnesFailClosed() = withDb { driver ->
        val id = summary(driver, today, "1330", 3_000, "success", "公开正文")
        source(driver, id, "R1", "remote_article", null, "公开标题", "公开摘要", null)
        assertEquals(today, digest(driver).read(today)["summary_date"]!!.jsonPrimitive.content)
        listOf("2026-13-01", "2026-02-30", "d2026-11-28", "2026-11-28T00:00", "2026-1-8", "todayy").forEach { bad ->
            val result = digest(driver).read(bad)
            assertFalse(result["ok"]!!.jsonPrimitive.boolean, "非法日期被接受：$bad")
            assertEquals("invalid_date", result["error"]!!.jsonPrimitive.content)
        }
    }

    @Test
    fun aMissingTodayIsNeverBackfilledWithAnOlderBriefing() = withDb { driver ->
        val old = summary(driver, yesterday, "2100", 500, "success", "昨天要闻：历史内容")
        source(driver, old, "R1", "remote_article", null, "公开标题", "公开摘要", null)
        val result = digest(driver).read("")
        assertEquals("no_summary_for_date", result["state"]!!.jsonPrimitive.content)
        assertFalse(result["found"]!!.jsonPrimitive.boolean)
        assertEquals(yesterday, result["latest_available_date"]!!.jsonPrimitive.content)
        assertFalse(result.toString().contains("历史内容"))
        val latest = digest(driver).read("latest")
        assertEquals(yesterday, latest["summary_date"]!!.jsonPrimitive.content)
        assertFalse(latest["is_today"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun observedGenerationStatesUseTheRealTaskStatusesAndAreTimeScoped() = withDb { driver ->
        summary(driver, today, "1330", 3_000, "pending", "")
        val pending = digest(driver).read("today")
        assertEquals("generating", pending["state"]!!.jsonPrimitive.content)
        assertFalse(pending["found"]!!.jsonPrimitive.boolean)

        val startOfToday = LocalDate.parse(today).atStartOfDayIn(zone).toEpochMilliseconds()
        listOf("queued", "running", "retrying").forEach { status ->
            driver.execute(null, "DELETE FROM async_task", 0)
            task(driver, status, null)
            val global = digest(driver).read("today")
            assertEquals(status, global["upstream_refresh_task"]!!.jsonPrimitive.content)
            assertEquals("global", global["upstream_refresh_scope"]!!.jsonPrimitive.content)
            driver.execute(null, "DELETE FROM async_task", 0)
            task(driver, status, startOfToday + 8 * 3_600_000L)
            assertEquals("requested_date", digest(driver).read("today")["upstream_refresh_scope"]!!.jsonPrimitive.content)
            driver.execute(null, "DELETE FROM async_task", 0)
            task(driver, status, startOfToday - 26 * 3_600_000L)
            assertEquals("global", digest(driver).read("today")["upstream_refresh_scope"]!!.jsonPrimitive.content)
        }
        driver.execute(null, "DELETE FROM async_task", 0)
        task(driver, "succeeded", startOfToday)
        assertNull(digest(driver).read("today")["upstream_refresh_task"])

        driver.execute(null, "UPDATE unified_news_summary SET status='failed'", 0)
        assertEquals("failed", digest(driver).read("today")["state"]!!.jsonPrimitive.content)
        driver.execute(null, "UPDATE unified_news_summary SET status='empty', content=''", 0)
        assertEquals("empty", digest(driver).read("today")["state"]!!.jsonPrimitive.content)
    }

    @Test
    fun anAuthenticatedRemoteSourceNeverReachesTheCloud() = withDb { driver ->
        val id = summary(driver, today, "1330", 3_000, "success", summaryBody, summaryTitle)
        // Remote channel whose feed URL carries its own token, as the remote-news standard allows.
        driver.execute(null, "INSERT INTO unified_news_source(summary_id,ref_key,source_type,source_id,source_filename,source_url,title,summary,source_time) " +
            "VALUES (?,?,?,?,?,?,?,?,?)", 9) {
            bindLong(0, id); bindString(1, "R1"); bindString(2, "remote_article"); bindLong(3, 5_001)
            bindString(4, "remote_news_source:3")
            bindString(5, "https://feed.test/rss?token=" + secret + "&category=tech")
            bindString(6, privateTitle); bindString(7, privateBody); bindLong(8, 2_000)
        }
        val result = digest(driver).read("today")
        assertEquals("restricted", result["state"]!!.jsonPrimitive.content)
        assertFalse(result["prose_allowed"]!!.jsonPrimitive.boolean)
        assertEquals("", result["content"]!!.jsonPrimitive.content)
        assertEquals("", result["title"]!!.jsonPrimitive.content)
        assertTrue(result["sources"]!!.jsonArray.isEmpty())
        assertEquals(1, result["source_count"]!!.jsonPrimitive.int)
        assertTrue(result["read_in_app"]!!.jsonPrimitive.boolean)
        assertFalse(result.toString().contains("哨兵"), "受限时仍把私有引用送进上下文")
        assertFalse(result.toString().contains(secret), "受限时仍把鉴权地址送进上下文")
        assertFalse(result.toString().contains("feed.test/rss"))
    }

    @Test
    fun anIndependentTokenSubscriptionWithoutUrlTokenIsStillRestricted() = withDb { driver ->
        val id = summary(driver, today, "1330", 3_000, "success", summaryBody, summaryTitle)
        // No token in the URL and a public-looking article link: absence of a token is not proof of public.
        driver.execute(null, "INSERT INTO unified_news_source(summary_id,ref_key,source_type,source_id,source_filename,source_url,title,summary,source_time) " +
            "VALUES (?,?,?,?,?,?,?,?,?)", 9) {
            bindLong(0, id); bindString(1, "R1"); bindString(2, "remote_article"); bindLong(3, 5_002)
            bindString(4, "remote_news_source:7")
            bindString(5, "https://news.example.com/story/1")
            bindString(6, privateTitle); bindString(7, privateBody); bindLong(8, 2_000)
        }
        val result = digest(driver).read("today")
        assertEquals("restricted", result["state"]!!.jsonPrimitive.content)
        assertTrue(result["sources"]!!.jsonArray.isEmpty())
        assertFalse(result.toString().contains("哨兵"))
    }

    @Test
    fun everyStoredSourceTypeStaysRestrictedIncludingTheBuiltInHotBoards() = withDb { driver ->
        val id = summary(driver, today, "1330", 3_000, "success", summaryBody, summaryTitle)
        repeat(6) { source(driver, id, "R$it", "remote_article", null, "远程标题$it", "远程摘要$it", "https://news.test/r$it") }
        // Identity format of a built-in hot board item; the per-platform feedUrl is user-editable, so even
        // this is not verifiable public provenance for cloud release.
        repeat(6) { source(driver, id, "H$it", "hot_news", null, "榜单标题$it", "榜单摘要$it", null, filename = "zhihu:$it") }
        val result = digest(driver).read("today")
        assertEquals("restricted", result["state"]!!.jsonPrimitive.content)
        assertEquals("", result["content"]!!.jsonPrimitive.content)
        assertEquals("", result["title"]!!.jsonPrimitive.content)
        assertEquals(12, result["source_count"]!!.jsonPrimitive.int)
        assertEquals(12, result["sources_omitted"]!!.jsonPrimitive.int)
        assertTrue(result["sources"]!!.jsonArray.isEmpty())
        assertFalse(result.toString().contains("哨兵"))
        // It must not be reported as "no news": the briefing exists but is not releasable.
        assertEquals(true, result["found"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun aMixedBriefingReleasesNoEntryAndKeepsTheRealTotal() = withDb { driver ->
        val id = summary(driver, today, "1330", 3_000, "success", summaryBody, summaryTitle)
        repeat(9) { source(driver, id, "H$it", "hot_news", null, "榜单标题$it", "榜单摘要$it", null, filename = "github:$it") }
        source(driver, id, "F9", "local_favorite", 77L, privateTitle, privateBody, null)
        val result = digest(driver).read("today")
        assertEquals("restricted", result["state"]!!.jsonPrimitive.content)
        assertEquals(10, result["source_count"]!!.jsonPrimitive.int)
        assertTrue(result["sources"]!!.jsonArray.isEmpty())
        assertFalse(result.toString().contains("哨兵"))
    }

    @Test
    fun aBriefingWithoutAnyAttributableSourceIsNotReleased() = withDb { driver ->
        summary(driver, today, "1330", 3_000, "success", summaryBody, summaryTitle)
        val result = digest(driver).read("today")
        assertEquals("restricted", result["state"]!!.jsonPrimitive.content)
        assertEquals(0, result["source_count"]!!.jsonPrimitive.int)
        assertFalse(result["prose_allowed"]!!.jsonPrimitive.boolean)
        assertEquals("", result["content"]!!.jsonPrimitive.content)
        assertFalse(result.toString().contains("哨兵"))
    }

    @Test
    fun anExclusionCannotBypassTheRestriction() = withDb { driver ->
        val id = summary(driver, today, "1330", 3_000, "success", summaryBody)
        source(driver, id, "R1", "remote_article", 42L, privateTitle, privateBody, null)
        AssistantPrivacyRepository(driver).exclude("article", "42")
        val result = digest(driver).read("today")
        assertEquals("restricted", result["state"]!!.jsonPrimitive.content)
        assertFalse(result.toString().contains("哨兵"))
    }

    @Test
    fun theModelFacingRegistryOffersOneBoundedNewsReadInsteadOfArticleCrawling() = withDb { driver ->
        val id = summary(driver, today, "0800", 2_000, "success", "今日重点：" + summaryBody)
        source(driver, id, "R1", "remote_article", null, privateTitle, "公开摘要", "https://example.test/a?token=" + secret)
        val registry = koogDataTools(AssistantDataGateway(driver), news = digest(driver))
        assertTrue(registry.tools.any { it.descriptor.name == "read_news_digest" }, registry.tools.map { it.descriptor.name }.toString())
        assertFalse(registry.tools.any { it.descriptor.name == "unified_news_summary" })
        @Suppress("UNCHECKED_CAST")
        val tool = registry.getTool("read_news_digest") as Tool<ReadNewsArgs, String>
        val output = runBlocking { tool.execute(ReadNewsArgs("today", "")) }
        assertTrue(output.contains("\"state\":\"restricted\""), output)
        assertTrue(output.contains("\"read_in_app\":true"))
        assertFalse(output.contains("哨兵"))
        assertFalse(output.contains(secret))
        assertEquals(output, runBlocking { tool.execute(ReadNewsArgs("today", "")) })
        assertFalse(koogDataTools(AssistantDataGateway(driver)).tools.any { it.descriptor.name == "read_news_digest" })
    }

    @Test
    fun aRecordedReadStampIsInvalidatedWhenTheSameDayBriefingChanges() = withDb { driver ->
        val id = summary(driver, today, "1330", 3_000, "success", "今日重点：公开甲")
        source(driver, id, "R1", "remote_article", 11L, "公开标题", "公开摘要", null)
        val stamp = sha256Hex(digest(driver).read("today").toString())
        assertEquals(stamp, sha256Hex(digest(driver).read("today").toString()))
        assertEquals(stamp, sha256Hex(AssistantNewsDigest(driver, today, zone).read("today").toString()))
        // The released projection is what a checkpoint depends on; adding a source changes it.
        source(driver, id, "R2", "remote_article", 12L, "公开标题二", "公开摘要二", null)
        assertNotEquals(stamp, sha256Hex(digest(driver).read("today").toString()))
        assertNotEquals(stamp, sha256Hex(AssistantNewsDigest(driver, yesterday, zone).read("today").toString()))
    }

    private fun task(driver: JdbcSqliteDriver, status: String, tickDueAtMs: Long?) {
        val payload = if (tickDueAtMs == null) """{"force":false,"mode":"manual"}"""
        else """{"force":false,"mode":"scheduled","tickDueAtMs":$tickDueAtMs}"""
        driver.execute(null, "INSERT INTO async_task(type,status,payload_json,progress_current,progress_total," +
            "attempt_count,max_attempts,created_at,updated_at) VALUES ('remote_news_fetch',?,?,0,1,0,3,1,1)", 2) {
            bindString(0, status); bindString(1, payload)
        }
    }

    private fun summary(
        driver: JdbcSqliteDriver, date: String, window: String, endMs: Long, status: String,
        content: String, title: String = "简报",
    ): Long {
        driver.execute(null, "INSERT INTO unified_news_summary(summary_date,window_key,window_start_ms,window_end_ms,title,content,status," +
            "error_message,source_warnings,generated_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", 12) {
            bindString(0, date); bindString(1, window); bindLong(2, endMs - 1000); bindLong(3, endMs)
            bindString(4, title); bindString(5, content); bindString(6, status)
            bindString(7, "upstream failure text"); bindString(8, """{"private":"x"}""")
            bindLong(9, endMs); bindLong(10, endMs); bindLong(11, endMs)
        }
        return summaryId(driver, date, window)
    }

    private fun summaryId(driver: JdbcSqliteDriver, date: String, window: String): Long =
        driver.executeQuery(null, "SELECT id FROM unified_news_summary WHERE summary_date=? AND window_key=?", {
            it.next(); QueryResult.Value(it.getLong(0)!!)
        }, 2) { bindString(0, date); bindString(1, window) }.value

    private fun source(
        driver: JdbcSqliteDriver, summaryId: Long, ref: String, type: String, sourceId: Long?,
        title: String, summary: String, url: String?, filename: String? = null,
    ) {
        driver.execute(null, "INSERT INTO unified_news_source(summary_id,ref_key,source_type,source_id,source_filename,source_url,title,summary,source_time) " +
            "VALUES (?,?,?,?,?,?,?,?,?)", 9) {
            bindLong(0, summaryId); bindString(1, ref); bindString(2, type); bindLong(3, sourceId)
            bindString(4, filename); bindString(5, url); bindString(6, title); bindString(7, summary); bindLong(8, 1)
        }
    }

    private fun withDb(block: (JdbcSqliteDriver) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use {
            DailySatoriDatabase.Schema.create(it)
            block(it)
        }
    }
}

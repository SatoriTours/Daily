package com.dailysatori.service.localnews

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.UnifiedNewsSummaryRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalNewsBriefingTest {
    private val today = "2026-11-28"
    private val yesterday = "2026-11-27"
    private val token = "SECRET-TOKEN-4a71"
    private val secretUrl = "https://private.example/feed?token=" + token

    @Test
    fun onlyCompleteOverviewQuestionsAreRoutedLocally() {
        listOf(
            "今天有什么重点的新闻吗？", "今天有什么重要的新闻", "今日要闻", "今天的新闻有哪些",
            "今天 有什么 新闻 吗", "本日简报", "今天新闻", "What's the news today?",
        ).forEach { assertEquals(LocalNewsIntent.Today, localNewsIntentOf(it), "未识别今日问法：$it") }
        listOf(
            "最近一次简报", "最新简报", "最近一期新闻简报", "最近的新闻", "Latest briefing?",
        ).forEach { assertEquals(LocalNewsIntent.Latest, localNewsIntentOf(it), "未识别最新问法：$it") }

        listOf(
            "帮我写日报", "今天有什么新闻，顺便帮我写个日报", "搜索今天的新闻", "今天新闻里关于 AI 的部分",
            "引用今天新闻的第三条", "今天的新闻和昨天比怎么样", "最近一次简报的来源是什么",
            "新闻", "今天", "帮我总结最近一次简报", "今天的新闻吗？再说说那篇文章",
        ).forEach { assertNull(localNewsIntentOf(it), "不应本机拦截：$it") }
    }

    @Test
    fun todayAnswersWithTheNewestReadableWindowAndRealSources() = withReader { reader, driver ->
        summary(driver, today, "0800", 2_000L, "success", "上午要点", "上午标题")
        val afternoon = summary(driver, today, "1330", 3_000L, "success", "下午要点", "下午标题")
        summary(driver, today, "1800", 4_000L, "failed", "")
        source(driver, afternoon, "H1", "hot_news", "榜单标题", null, "zhihu:1")
        source(driver, afternoon, "R1", "remote_article", "远程标题", secretUrl, "remote_news_source:2")

        val result = reader.read(LocalNewsIntent.Today, instantOf("2026-11-28T06:00:00Z"), "UTC")

        assertEquals(LocalNewsBriefingState.Ready, result.state)
        assertEquals("1330", result.window)
        assertEquals(today, result.summaryDate)
        assertTrue(result.isToday)
        assertEquals("下午标题", result.title)
        assertEquals("下午要点", result.content)
        assertEquals(listOf("榜单标题" to "知乎", "远程标题" to ""), result.sources.map { it.title to it.platformName })
        assertFalse(result.sources.any { it.sourceType.isBlank() })
        assertFalse(result.toString().contains(token), "来源地址中的凭据不得进入展示数据")
    }

    @Test
    fun todayWithoutItsOwnBriefingNeverBorrowsYesterday() = withReader { reader, driver ->
        summary(driver, yesterday, "2100", 1_000L, "success", "昨天要点", "昨天标题")

        val result = reader.read(LocalNewsIntent.Today, instantOf("2026-11-28T06:00:00Z"), "UTC")

        assertEquals(LocalNewsBriefingState.NotPrepared, result.state)
        assertTrue(result.isToday)
        assertEquals(today, result.summaryDate)
        assertEquals("", result.content)
        assertEquals("", result.title)
        assertTrue(result.sources.isEmpty())
        assertEquals(yesterday, result.latestDate, "可以报告最近日期，但不得展示旧正文")
    }

    @Test
    fun todayDistinguishesGeneratingFailedAndEmpty() = withReader { reader, driver ->
        val id = summary(driver, today, "1330", 3_000L, "pending", "")
        assertEquals(LocalNewsBriefingState.Generating, todayState(reader))
        driver.execute(null, "UPDATE unified_news_summary SET status='failed', error_message='上游失败' WHERE id=?", 1) {
            bindLong(0, id)
        }
        assertEquals(LocalNewsBriefingState.Failed, todayState(reader))
        driver.execute(null, "UPDATE unified_news_summary SET status='empty' WHERE id=?", 1) { bindLong(0, id) }
        assertEquals(LocalNewsBriefingState.Empty, todayState(reader))
        driver.execute(null, "UPDATE unified_news_summary SET status='success', content='  ' WHERE id=?", 1) {
            bindLong(0, id)
        }
        assertEquals(LocalNewsBriefingState.Empty, todayState(reader))
    }

    @Test
    fun latestReadsTheLastAvailableBriefingWithItsRealDate() = withReader { reader, driver ->
        summary(driver, yesterday, "1800", 1_000L, "success", "昨天要点", "昨天标题")
        summary(driver, today, "0800", 2_000L, "success", "今天要点", "今天标题")
        summary(driver, "2026-11-26", "2100", 500L, "success", "前天要点", "前天标题")

        val result = reader.read(LocalNewsIntent.Latest, instantOf("2026-11-28T23:30:00Z"), "UTC")

        assertEquals(LocalNewsBriefingState.Ready, result.state)
        assertEquals(today, result.summaryDate)
        assertTrue(result.isToday)
        assertEquals("今天要点", result.content)
    }

    @Test
    fun latestMarksAnOlderBriefingAsNotTodayAndReportsNoBriefingWhenNoneExist() = withReader { reader, driver ->
        assertEquals(
            LocalNewsBriefingState.NotPrepared,
            reader.read(LocalNewsIntent.Latest, instantOf("2026-11-28T06:00:00Z"), "UTC").state,
        )
        summary(driver, yesterday, "2100", 1_000L, "success", "昨天要点", "昨天标题")

        val result = reader.read(LocalNewsIntent.Latest, instantOf("2026-11-28T06:00:00Z"), "UTC")

        assertEquals(yesterday, result.summaryDate)
        assertFalse(result.isToday)
        assertEquals("昨天要点", result.content)
    }

    @Test
    fun latestSkipsAWhitespaceOnlySuccessAndReportsTheOlderReadableRealDate() = withReader { reader, driver ->
        summary(driver, today, "1330", 3_000L, "success", " \n\t ", "空白最新标题")
        summary(driver, yesterday, "1800", 1_000L, "success", "昨天可读要点", "昨天标题")

        val result = reader.read(LocalNewsIntent.Latest, instantOf("2026-11-28T06:00:00Z"), "UTC")

        assertEquals(LocalNewsBriefingState.Ready, result.state, "最新 success 只有空白时不得报无简报")
        assertEquals(yesterday, result.summaryDate, "应答更早的那份真实日期")
        assertFalse(result.isToday)
        assertEquals("昨天可读要点", result.content)
    }

    @Test
    fun latestStillFallsBackWhenTheNewestBodyIsExoticWhitespace() = withReader { reader, driver ->
        // U+3000 is whitespace for Kotlin but not for SQL char(32/9/10/13): the Kotlin guard must fall back.
        summary(driver, today, "1330", 3_000L, "success", "\u3000", "全角空白标题")
        summary(driver, yesterday, "2100", 1_000L, "success", "昨天可读要点", "昨天标题")

        val result = reader.read(LocalNewsIntent.Latest, instantOf("2026-11-28T06:00:00Z"), "UTC")

        assertEquals(LocalNewsBriefingState.Ready, result.state)
        assertEquals(yesterday, result.summaryDate)
        assertEquals("昨天可读要点", result.content)
    }

    @Test
    fun todayWithoutItsOwnBriefingReportsTheLatestReadableDateEvenWhenTheNewestSuccessIsBlank() = withReader { reader, driver ->
        summary(driver, yesterday, "1800", 1_000L, "success", "   ", "空白最新标题")
        summary(driver, "2026-11-26", "2100", 500L, "success", "前天可读要点", "前天标题")

        val missingToday = reader.read(LocalNewsIntent.Today, instantOf("2026-11-28T06:00:00Z"), "UTC")

        assertEquals(LocalNewsBriefingState.NotPrepared, missingToday.state)
        assertEquals("", missingToday.content)
        assertEquals("2026-11-26", missingToday.latestDate, "最近可用日期取真正可读的那份，不是空白 success")

        summary(driver, today, "1330", 3_000L, "success", "   ", "今天的空白标题")
        val blankToday = reader.read(LocalNewsIntent.Today, instantOf("2026-11-28T06:00:00Z"), "UTC")

        assertEquals(LocalNewsBriefingState.Empty, blankToday.state, "今天有生成但无正文即为确认空")
        assertEquals("", blankToday.content)
        assertEquals("2026-11-26", blankToday.latestDate)
    }

    @Test
    fun todayIsFixedByTheQuestionInstantAndZone() = withReader { reader, driver ->
        summary(driver, today, "0800", 2_000L, "success", "今天要点", "今天标题")

        // 2026-11-27T17:30Z is already 2026-11-28 01:30 in +08:00.
        val inShanghai = reader.read(LocalNewsIntent.Today, instantOf("2026-11-27T17:30:00Z"), "Asia/Shanghai")
        val inUtc = reader.read(LocalNewsIntent.Today, instantOf("2026-11-27T17:30:00Z"), "UTC")

        assertEquals(LocalNewsBriefingState.Ready, inShanghai.state)
        assertEquals(today, inShanghai.summaryDate)
        assertEquals(LocalNewsBriefingState.NotPrepared, inUtc.state)
        assertEquals(yesterday, inUtc.summaryDate)
    }

    private fun todayState(reader: LocalNewsBriefingReader): LocalNewsBriefingState = runBlocking {
        reader.read(LocalNewsIntent.Today, instantOf("2026-11-28T06:00:00Z"), "UTC").state
    }

    private fun instantOf(text: String): Long = kotlinx.datetime.Instant.parse(text).toEpochMilliseconds()

    private fun withReader(block: suspend (LocalNewsBriefingReader, JdbcSqliteDriver) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val repository = UnifiedNewsSummaryRepository(DailySatoriDatabase(driver))
            runBlocking { block(RepositoryLocalNewsBriefingReader(repository, Dispatchers.Unconfined), driver) }
        }
    }

    private fun summary(
        driver: JdbcSqliteDriver, date: String, window: String, endMs: Long, status: String,
        content: String, title: String = "简报",
    ): Long {
        driver.execute(null, "INSERT INTO unified_news_summary(summary_date,window_key,window_start_ms,window_end_ms,title,content,status," +
            "generated_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)", 10) {
            bindString(0, date); bindString(1, window); bindLong(2, endMs - 1000); bindLong(3, endMs)
            bindString(4, title); bindString(5, content); bindString(6, status)
            bindLong(7, endMs); bindLong(8, endMs); bindLong(9, endMs)
        }
        return summaryId(driver, date, window)
    }

    private fun summaryId(driver: JdbcSqliteDriver, date: String, window: String): Long =
        driver.executeQuery(null, "SELECT id FROM unified_news_summary WHERE summary_date=? AND window_key=?", {
            it.next(); QueryResult.Value(it.getLong(0)!!)
        }, 2) { bindString(0, date); bindString(1, window) }.value

    private fun source(
        driver: JdbcSqliteDriver, summaryId: Long, ref: String, type: String, title: String,
        url: String?, filename: String,
    ) {
        driver.execute(null, "INSERT INTO unified_news_source(summary_id,ref_key,source_type,source_id,source_filename,source_url,title,summary,source_time) " +
            "VALUES (?,?,?,?,?,?,?,?,?)", 9) {
            bindLong(0, summaryId); bindString(1, ref); bindString(2, type); bindLong(3, null)
            bindString(4, filename); bindString(5, url); bindString(6, title); bindString(7, "来源摘要"); bindLong(8, 1)
        }
    }
}

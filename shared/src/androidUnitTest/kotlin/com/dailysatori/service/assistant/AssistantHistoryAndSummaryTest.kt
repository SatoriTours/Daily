package com.dailysatori.service.assistant

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.*
import com.dailysatori.service.asynctask.*
import com.dailysatori.service.memory.ConversationSummaryDraft
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class AssistantHistoryAndSummaryTest {
    @Test fun historyPagesHaveFixedUpperBoundAndSkipUnfinishedDeletedAndExcludedInputs() = runBlocking<Unit> {
        Fixture().use { f ->
            repeat(7) { f.chat("历史$it") }
            AssistantPrivacyRepository(f.driver).exclude("chat", "2")
            f.store.exec("DELETE FROM chat_conversation WHERE id=3")
            val pending = AssistantTurnRepository(f.driver).accept("s", "pending", "尚未完成的当前轮", 1, "UTC")
            val root = assertNotNull(f.backfill.enqueue())
            val afterUpper = f.chat("排队后新到的原文")
            val runner = f.runner(AssistantHistoryBackfillTaskHandler(f.backfill))
            f.finish(runner, root)
            val indexed = f.store.rows("SELECT payload_json FROM async_task WHERE type='assistant_index'") {
                Json.parseToJsonElement(it.getString(0)!!).jsonObject["sourceId"]!!.jsonPrimitive.long
            }
            assertEquals(listOf(1L, 4L, 5L, 6L, 7L), indexed)
            assertFalse(pending.id in indexed)
            assertFalse(afterUpper in indexed)
            assertEquals(0L, f.tasks.getById(root)!!.attempt_count, "productive page yields are not failures")
            assertTrue(f.tasks.getById(root)!!.checkpoint_json.contains("upperBound"))
        }
    }

    @Test fun restoredBackfillStaysParkedUntilExplicitAuthorizedResume() = runBlocking<Unit> {
        Fixture().use { f ->
            f.chat("旧历史")
            f.settings.upsert(ASSISTANT_HISTORY_RESTORE_HOLD, "true")
            assertNull(f.backfill.enqueue())
            val held = f.tasks.getLatestByUniqueKey(ASSISTANT_HISTORY_BACKFILL_TYPE)!!
            assertEquals("failed", held.status)
            assertNull(f.backfill.enqueue())
            assertEquals("true", f.settings.get(ASSISTANT_HISTORY_RESTORE_HOLD))
            assertTrue(f.tasks.resumeAssistantBackground(held.id))
            f.finish(f.runner(AssistantHistoryBackfillTaskHandler(f.backfill)), held.id)
            assertEquals("false", f.settings.get(ASSISTANT_HISTORY_RESTORE_HOLD))
        }
    }

    @Test fun unauthorizedBackfillAndResumeNeverQueueCloudWork() = runBlocking<Unit> {
        Fixture().use { f ->
            f.chat("旧历史")
            f.settings.upsert(PersistentAssistantConversation.CONSENT_KEY, "false")
            assertNull(f.backfill.enqueue())
            val id = f.tasks.enqueue("assistant_summary", "{}")
            assertTrue(f.tasks.claimForRun(id, "summary", Long.MAX_VALUE))
            f.tasks.finishFailure(id, "cloud_consent_required", "等待授权")
            assertFalse(f.tasks.resumeAssistantBackground(id))
            val other = f.tasks.enqueue("save_article", "{}")
            assertTrue(f.tasks.claimForRun(other, "article", Long.MAX_VALUE))
            f.tasks.finishFailure(other, "failed", "失败")
            f.settings.upsert(PersistentAssistantConversation.CONSENT_KEY, "true")
            assertFalse(f.tasks.resumeAssistantBackground(other), "this API cannot retry arbitrary business writes")
            f.store.exec("UPDATE async_task SET attempt_count=5 WHERE id=?", id)
            assertTrue(f.tasks.resumeAssistantBackground(id))
            assertEquals("queued", f.tasks.getById(id)!!.status)
            assertEquals(0L, f.tasks.getById(id)!!.attempt_count)
            assertEquals("failed", f.tasks.getById(other)!!.status)
        }
    }

    @Test fun summaryReallyRollsAcrossEverySegmentIncludingTheTailWithoutRepeatingCompletedWork() = runBlocking<Unit> {
        Fixture().use { f ->
            val original = "历史项目：" + "我在规划长跑训练。".repeat(290) + "尾部长跑证据"
            val old = f.oldSource(original)
            val seen = mutableListOf<String>()
            val previous = mutableListOf<String>()
            val summarizer = AssistantConversationSummarizer(f.driver, segmentSize = 1000) { prior, text ->
                previous += prior; seen += text
                ConversationSummaryDraft("AI归纳第${seen.size}版", listOf(text.takeLast(12)))
            }
            val id = assertNotNull(f.backfill.enqueueSummary("s", Long.MAX_VALUE))
            val runner = f.runner(AssistantSummaryTaskHandler(summarizer))
            f.finish(runner, id)
            assertTrue(seen.size >= 3)
            assertTrue(seen.last().contains("尾部长跑证据"))
            assertEquals("", previous.first())
            assertEquals("AI归纳第1版", previous[1])
            val coverage = Json.parseToJsonElement(f.store.text("SELECT summary_sources_json FROM assistant_session WHERE id='s'")!!).jsonArray.single().jsonObject
            assertEquals(old, coverage["id"]!!.jsonPrimitive.long)
            assertEquals(original.length, coverage["offset"]!!.jsonPrimitive.int)
            assertEquals("redacted-utf16", coverage["coordinate"]!!.jsonPrimitive.content)
            assertEquals("", AssistantMemoryService(f.driver).summary("s", old), "later material never enters an earlier queued turn")
            val count = seen.size
            f.finish(runner, assertNotNull(f.backfill.enqueueSummary("s", Long.MAX_VALUE)))
            assertEquals(count, seen.size, "completed coverage is reused without another model request")
        }
    }

    @Test fun summaryProviderFailureResumesAfterLastCommittedSegment() = runBlocking<Unit> {
        Fixture().use { f ->
            f.oldSource("首段" + "普通项目资料。".repeat(400) + "尾段")
            var calls = 0
            val fragments = mutableListOf<String>()
            val summarizer = AssistantConversationSummarizer(f.driver, segmentSize = 1000) { _, text ->
                calls++; fragments += text
                if (calls == 2) error("simulated network interruption")
                ConversationSummaryDraft("第${calls}版", listOf(text.takeLast(8)))
            }
            val id = f.backfill.enqueueSummary("s", Long.MAX_VALUE)!!
            val runner = f.runner(AssistantSummaryTaskHandler(summarizer))
            assertEquals(AsyncTaskRunOutcome.RetryScheduled, runner.run(id))
            f.unblock(id)
            assertEquals(AsyncTaskRunOutcome.RetryScheduled, runner.run(id))
            f.unblock(id)
            f.finish(runner, id)
            assertEquals(1, fragments.count { it == fragments.first() }, "the first committed segment must not be uploaded again")
            assertEquals(fragments[1], fragments[2], "only the failed request repeats")
        }
    }

    @Test fun exclusionDuringSummaryCallCannotSaveLateDerivedText() = runBlocking<Unit> {
        Fixture().use { f ->
            val old = f.oldSource("用户的旧项目原文")
            val summarizer = AssistantConversationSummarizer(f.driver) { _, text ->
                AssistantPrivacyRepository(f.driver).exclude("chat", old.toString())
                ConversationSummaryDraft("迟到的旧摘要", listOf(text))
            }
            val id = f.backfill.enqueueSummary("s", Long.MAX_VALUE)!!
            f.finish(f.runner(AssistantSummaryTaskHandler(summarizer)), id)
            assertEquals("", f.store.text("SELECT summary FROM assistant_session WHERE id='s'").orEmpty())
        }
    }

    @Test fun staleSummaryOwnerAndConcurrentVersionCannotOverwriteNewerState() = runBlocking<Unit> {
        Fixture().use { f ->
            f.oldSource("项目原文")
            val summarizer = AssistantConversationSummarizer(f.driver) { _, text ->
                f.store.exec("UPDATE assistant_session SET summary='并发较新的摘要',summary_version=summary_version+1 WHERE id='s'")
                ConversationSummaryDraft("旧摘要", listOf(text))
            }
            val id = f.backfill.enqueueSummary("s", Long.MAX_VALUE)!!
            assertEquals(AsyncTaskRunOutcome.RetryScheduled, f.runner(AssistantSummaryTaskHandler(summarizer)).run(id))
            assertEquals("并发较新的摘要", f.store.text("SELECT summary FROM assistant_session WHERE id='s'"))
        }
    }

    @Test fun invalidModelQuoteDoesNotAdvanceSummaryCoverageAndIndexingCannotReplaceAiSummary() = runBlocking<Unit> {
        Fixture().use { f ->
            val old = f.oldSource("真实的项目原文")
            val id = f.backfill.enqueueSummary("s", Long.MAX_VALUE)!!
            val bad = AssistantConversationSummarizer(f.driver) { _, _ -> ConversationSummaryDraft("没有原文根据的结论", listOf("原文不存在的证据")) }
            assertEquals(AsyncTaskRunOutcome.RetryScheduled, f.runner(AssistantSummaryTaskHandler(bad)).run(id))
            assertEquals("[]", f.store.text("SELECT summary_sources_json FROM assistant_session WHERE id='s'"))
            f.unblock(id)
            val good = AssistantConversationSummarizer(f.driver) { _, text -> ConversationSummaryDraft("有证据的 AI 项目归纳", listOf(text)) }
            f.finish(f.runner(AssistantSummaryTaskHandler(good)), id)
            val version = f.store.rows("SELECT summary_version FROM assistant_session WHERE id='s'") { it.getLong(0)!! }.single()
            AssistantMemoryService(f.driver).index(old)
            assertTrue(AssistantMemoryService(f.driver).summary("s", Long.MAX_VALUE).contains("有证据的 AI 项目归纳"))
            assertEquals(version, f.store.rows("SELECT summary_version FROM assistant_session WHERE id='s'") { it.getLong(0)!! }.single())
            f.store.exec("UPDATE chat_conversation SET content='内容已被更改' WHERE id=?", old)
            assertEquals("", AssistantMemoryService(f.driver).summary("s", Long.MAX_VALUE))
        }
    }

    private class Fixture : AutoCloseable {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { DailySatoriDatabase.Schema.create(it) }
        val db = DailySatoriDatabase(driver)
        val store = AssistantStore(driver)
        val tasks = AsyncTaskRepository(db)
        val settings = SettingRepository(db)
        val chats = ChatConversationRepository(db)
        val backfill = AssistantHistoryBackfill(driver, pageSize = 2)
        init { settings.upsert(PersistentAssistantConversation.CONSENT_KEY, "true") }
        fun chat(text: String): Long { chats.insert("s", "user", text); return store.rows("SELECT last_insert_rowid()") { it.getLong(0)!! }.single() }
        fun oldSource(text: String): Long = chat(text).also { repeat(12) { chat("近期$it") } }
        fun runner(handler: AsyncTaskHandler) = AsyncTaskRunner(tasks, AsyncTaskHandlerRegistry(listOf(handler)))
        fun unblock(id: Long) = store.exec("UPDATE async_task SET run_after_ms=NULL WHERE id=?", id)
        suspend fun finish(runner: AsyncTaskRunner, id: Long) {
            repeat(30) { unblock(id); if (runner.run(id) == AsyncTaskRunOutcome.Succeeded) return }
            fail("bounded work did not reach completion")
        }
        override fun close() = driver.close()
    }
}

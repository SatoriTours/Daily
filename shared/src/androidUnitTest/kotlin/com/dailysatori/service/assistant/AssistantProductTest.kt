package com.dailysatori.service.assistant

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.*
import com.dailysatori.service.assistant.data.AssistantDataGateway
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class AssistantProductTest {
    @Test fun v37PhysicalSchemaMigratesProductColumnsWithoutLosingExistingBusinessData() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val chats = ChatConversationRepository(db)
            val original = chats.acceptUserInput("old-session","迁移前用户原文")
            driver.execute(null,"INSERT INTO memory_entry(type,source_type,source_id,title,content,created_at,updated_at) VALUES ('content','diary',7,'旧摘要','旧记忆正文',1,1)",0)
            driver.execute(null,"INSERT INTO reminder(id,content,status,start_date,end_date,first_reminder_time,active_day_rule,time_zone_id,profile_json,created_at,updated_at) VALUES ('old-reminder','旧提醒','PAUSED','2030-01-01','2030-01-01','10:00','daily','UTC','{}',1,1)",0)
            // Remove every product addition: this is the actual affected v37 table shape,
            // not merely a version marker on an already-current database.
            listOf("assistant_action","assistant_reference","assistant_index_segment").forEach { driver.execute(null,"DROP TABLE $it",0) }
            mapOf("assistant_turn" to listOf("action_plan_json","context_json"),
                "assistant_session" to listOf("summary","summary_sources_json","summary_version"),
                "memory_entry" to listOf("assistant_kind","assistant_state","evidence","source_version")
            ).forEach { (table,columns) -> columns.forEach { driver.execute(null,"ALTER TABLE $table DROP COLUMN $it",0) } }
            val settings = SettingRepository(db)
            settings.upsert("assistant-migration-sentinel","保留原设置")
            settings.upsert(com.dailysatori.config.SettingKeys.schemaVersion,"37")
            com.dailysatori.service.migration.DatabaseMigration(driver,settings).runMigrations()
            assertEquals("38",settings.get(com.dailysatori.config.SettingKeys.schemaVersion))
            assertEquals("保留原设置",settings.get("assistant-migration-sentinel"))
            assertEquals("迁移前用户原文",chats.getById(original)?.content)
            assertEquals("旧记忆正文",AssistantStore(driver).text("SELECT content FROM memory_entry WHERE source_id=7"))
            assertEquals("active",AssistantStore(driver).text("SELECT assistant_state FROM memory_entry WHERE source_id=7"))
            val memory = AssistantMemoryService(driver)
            memory.index(original)
            assertEquals(1,memory.pendingSegments(original).size)
            val turns = AssistantTurnRepository(driver)
            val turn = turns.accept("new","new-client","2030-01-02 15:00提醒我读书",1L,"UTC")
            AssistantInputContext(driver).capture(turn.id,null)
            AsyncTaskRepository(db).claimForRun(turn.taskId,"owner",Long.MAX_VALUE)
            val actions = AssistantActionService(driver,RepositoryAssistantBusiness(driver) { _, _ -> })
            actions.submit(turn,"owner",listOf(AssistantActionRequest("reminder","2030-01-02 15:00提醒我读书",content="读书",startDate="2030-01-02",time="15:00")))
            assertEquals(2,db.dailySatoriQueries.selectAllReminders().executeAsList().size)
            assertEquals("旧提醒",AssistantStore(driver).text("SELECT content FROM reminder WHERE id='old-reminder'"))
        }
    }
    @Test fun privacyChangesFenceLateRepliesAndOlderSummaryIsSourceVersioned() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val turns = AssistantTurnRepository(driver)
            val tasks = AsyncTaskRepository(db)
            val memory = AssistantMemoryService(driver)
            val chats = ChatConversationRepository(db)
            val ids = (1..15).map { chats.acceptUserInput("s", "第${it}次原话") }
            // These compatibility messages are still original sources, not invented summaries.
            val turn = turns.accept("s", "c", "不要保存 https://example.com/no-save", 1L, "UTC")
            tasks.claimForRun(turn.taskId, "owner", Long.MAX_VALUE)
            memory.index(turn.id)
            assertTrue(memory.summary("s", turn.id).contains("chat/"))
            val actions = AssistantActionService(driver, RepositoryAssistantBusiness(driver) { _, _ -> })
            assertFails { actions.submit(turn, "owner", listOf(AssistantActionRequest("article", "[input_link_0]", urlRef="input_link_0"))) }
            assertTrue(ArticleRepository(db).getAllSync().isEmpty())
            val version = turns.privacyRevision()
            AssistantPrivacyRepository(driver).exclude("chat", ids.first().toString())
            assertEquals("", memory.summary("s", turn.id))
            assertFalse(turns.complete(turn.id, "owner", "包含已遗忘事实的迟到回复", version))
            assertNull(turns.get(turn.id)?.responseMessageId)
        }
    }

    @Test fun quotedReminderIsNotAWriteInstructionOrCertainUserFact() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val turns = AssistantTurnRepository(driver)
            val turn = turns.accept("s","quote","解释引文：“明天15:00提醒我读书；我喜欢徒步。”",1L,"UTC")
            AsyncTaskRepository(db).claimForRun(turn.taskId,"owner",Long.MAX_VALUE)
            val actions = AssistantActionService(driver,RepositoryAssistantBusiness(driver) { _, _ -> })
            assertFails { actions.submit(turn,"owner",listOf(AssistantActionRequest("reminder","明天15:00提醒我读书",content="读书",time="15:00"))) }
            assertTrue(db.dailySatoriQueries.selectAllReminders().executeAsList().isEmpty())
            assertFails { AssistantMemoryService(driver).remember(turn,"owner","fact","用户喜欢徒步","我喜欢徒步") }
            val denied = turns.accept("s","negative","不要明天15:00提醒我读书",2L,"UTC")
            AsyncTaskRepository(db).claimForRun(denied.taskId,"owner",Long.MAX_VALUE)
            assertFails { actions.submit(denied,"owner",listOf(AssistantActionRequest("reminder","明天15:00提醒我读书",content="读书",time="15:00"))) }
        }
    }

    @Test fun modelCannotInventDefaultClockForDateOnlyInstruction() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val turn = AssistantTurnRepository(driver).accept("s","c","明天提醒我缴费",1L,"UTC")
            AsyncTaskRepository(db).claimForRun(turn.taskId,"owner",Long.MAX_VALUE)
            val actions = AssistantActionService(driver,RepositoryAssistantBusiness(driver) { _, _ -> })
            actions.submit(turn,"owner",listOf(AssistantActionRequest("reminder","明天提醒我缴费",content="缴费",startDate="2030-01-02",time="09:00")))
            val reminder = db.dailySatoriQueries.selectAllReminders().executeAsOne()
            assertEquals("PAUSED",reminder.status)
            assertEquals("00:00",reminder.first_reminder_time)
        }
    }

    @Test fun durablePlanReplaysOnlyUnfinishedWorkAndChangedPlanCannotCreateDuplicates() = runBlocking<Unit> {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val tasks = AsyncTaskRepository(db)
            val turns = AssistantTurnRepository(driver)
            val input = "保存 https://example.com/article ，明天提醒我看"
            val turn = turns.accept("s", "client", input, 1792800000000L, "Asia/Shanghai")
            tasks.claimForRun(turn.taskId, "owner", Long.MAX_VALUE)
            var dispatches = 0
            val business = RepositoryAssistantBusiness(driver) { _, _ -> dispatches++; if (dispatches == 2) error("permission/secret") }
            val actions = AssistantActionService(driver, business)
            val plan = listOf(
                AssistantActionRequest("article", "保存 [input_link_0]", urlRef = "input_link_0"),
                AssistantActionRequest("reminder", "明天提醒我看", content = "看文章", startDate = "2026-10-25", time = "15:00"),
            )
            actions.submit(turn, "owner", plan)
            assertEquals(1, ArticleRepository(db).getAllSync().size)
            assertEquals(1, db.dailySatoriQueries.selectAllReminders().executeAsList().size)
            actions.resume(turn, "owner")
            actions.submit(turn, "owner", plan)
            assertEquals(1, ArticleRepository(db).getAllSync().size)
            assertEquals(1, db.dailySatoriQueries.selectAllReminders().executeAsList().size)
            assertFails { actions.submit(turn, "owner", plan.take(1)) }
            assertEquals(2, actions.list(turn.id).size)
            assertTrue(actions.list(turn.id).all { it.targetId != null })
            val child = actions.list(turn.id).first { it.taskId != null }.taskId!!
            tasks.claimForRun(child, "child-owner", Long.MAX_VALUE)
            tasks.finishSuccess(child, "")
            val newer = tasks.enqueue("retention_fixture", "{}")
            tasks.claimForRun(newer, "newer-owner", Long.MAX_VALUE)
            tasks.finishSuccess(newer, "")
            tasks.pruneOldTasks(1)
            assertNotNull(tasks.getById(child), "Referenced business child task must survive pruning")
        }
    }

    @Test fun missingTimeCreatesPausedTodoAndExpiredOwnerCannotCreateAnything() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val tasks = AsyncTaskRepository(db)
            val turns = AssistantTurnRepository(driver)
            val turn = turns.accept("s", "c", "待办：读书", 1792800000000L, "UTC")
            tasks.claimForRun(turn.taskId, "old", Long.MAX_VALUE)
            val actions = AssistantActionService(driver, RepositoryAssistantBusiness(driver) { _, _ -> })
            actions.submit(turn, "old", listOf(AssistantActionRequest("reminder", "待办：读书", content = "读书")))
            val reminder = db.dailySatoriQueries.selectAllReminders().executeAsOne()
            assertEquals("PAUSED", reminder.status)
            val clarify = turns.accept("s", "clarify", "明天下午三点", 1792800000001L, "UTC")
            tasks.claimForRun(clarify.taskId, "clarify-owner", Long.MAX_VALUE)
            val actionId = actions.list(turn.id).single().id
            actions.clarify(clarify, "clarify-owner", actionId, AssistantActionRequest("reminder", "明天下午三点", startDate="2026-10-25", time="15:00"))
            val refined = db.dailySatoriQueries.selectAllReminders().executeAsOne()
            assertEquals(reminder.id, refined.id)
            assertEquals("15:00", refined.first_reminder_time)
            assertEquals("ACTIVE", refined.status)
            val next = turns.accept("s", "next", "保存 https://example.com/next", 1792800000001L, "UTC")
            tasks.claimForRun(next.taskId, "old", Long.MAX_VALUE)
            tasks.markRunningForRetryAfterProcessRestart(1L)
            tasks.claimForRun(next.taskId, "new", Long.MAX_VALUE)
            assertFails { actions.submit(next, "old", listOf(AssistantActionRequest("article", "保存 [input_link_0]", urlRef = "input_link_0"))) }
            assertTrue(ArticleRepository(db).getAllSync().isEmpty())
        }
    }

    @Test fun sourcedMemoryCorrectionForgettingAndLongInputIndexNeverResurrectExcludedText() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val turns = AssistantTurnRepository(driver)
            val tasks = AsyncTaskRepository(db)
            val text = "我喜欢徒步。" + "长内容".repeat(7000) + "尾部特殊证据"
            val turn = turns.accept("s", "c", text, 1L, "UTC")
            tasks.claimForRun(turn.taskId, "owner", Long.MAX_VALUE)
            val memory = AssistantMemoryService(driver)
            val id = memory.remember(turn, "owner", "preference", "用户喜欢徒步", "我喜欢徒步。")
            assertFails { memory.remember(turn, "owner", "fact", "虚构观点", "不存在的依据") }
            memory.index(turn.id)
            assertTrue(memory.segments(turn.id).last().contains("尾部特殊证据"))
            memory.correct(id, "我现在喜欢骑车")
            assertEquals(listOf("我现在喜欢骑车"), memory.list().filter { it.active }.map { it.content })
            memory.forget(memory.list().first { it.active }.id)
            memory.index(turn.id)
            assertTrue(memory.list().none { it.active })
            AssistantPrivacyRepository(driver).exclude("chat", turn.id.toString())
            memory.index(turn.id)
            assertTrue(memory.segments(turn.id).isEmpty())
            assertFalse(AssistantDataGateway(driver).search("徒步").toString().contains("徒步"))
        }
    }
}

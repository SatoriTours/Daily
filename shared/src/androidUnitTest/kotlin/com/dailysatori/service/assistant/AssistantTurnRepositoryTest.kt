package com.dailysatori.service.assistant

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.*
import com.dailysatori.service.asynctask.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.*
import kotlin.test.*

class AssistantTurnRepositoryTest {
    private fun fixture(block: (JdbcSqliteDriver, DailySatoriDatabase, AssistantTurnRepository, AsyncTaskRepository) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            block(driver, db, AssistantTurnRepository(driver), AsyncTaskRepository(db))
        }
    }

    @Test fun acceptsOriginalAndTaskAtomicallyAndReusesClientIdentityAfterRestart() = fixture { driver, db, turns, tasks ->
        val first = turns.accept("s", "client-1", "明天提醒我", 123L, "Asia/Shanghai")
        val replay = AssistantTurnRepository(driver).accept("s", "client-1", "明天提醒我", 999L, "UTC")
        assertEquals(first, replay)
        assertEquals(123L, replay.receivedAtMs)
        assertEquals("Asia/Shanghai", replay.timeZoneId)
        assertEquals(listOf("明天提醒我"), ChatConversationRepository(db).getBySession("s").map { it.content })
        assertEquals("assistant_turn", tasks.getById(first.taskId)!!.type)
        assertFalse(tasks.getById(first.taskId)!!.payload_json.contains("明天"))
        assertFailsWith<IllegalArgumentException> { turns.accept("s", "client-1", "改写的原文", 123L, "UTC") }
        assertNotEquals(first.id, turns.accept("s", "client-2", "明天提醒我", 124L, "UTC").id)
    }

    @Test fun failedEnqueueRollsBackOriginalAndSession() = fixture { driver, db, turns, _ ->
        driver.execute(null, "CREATE TRIGGER reject_turn_task BEFORE INSERT ON async_task BEGIN SELECT RAISE(ABORT, 'test'); END", 0)
        assertFails { turns.accept("s", "client", "不能丢", 123L, "UTC") }
        assertTrue(ChatConversationRepository(db).getBySession("s").isEmpty())
        driver.execute(null, "DROP TRIGGER reject_turn_task", 0)
        assertEquals("不能丢", turns.input(turns.accept("s", "client", "不能丢", 123L, "UTC")))
    }

    @Test fun onlyCurrentLeaseCanCommitAndCompletedReplySurvivesTaskPruning() = fixture { _, db, turns, tasks ->
        val turn = turns.accept("s", "client", "原文", 123L, "UTC")
        assertTrue(tasks.claimForRun(turn.taskId, "old", Long.MAX_VALUE))
        tasks.markRunningForRetryAfterProcessRestart(1L)
        assertTrue(tasks.claimForRun(turn.taskId, "new", Long.MAX_VALUE))
        assertFalse(turns.complete(turn.id, "old", "过期回答"))
        assertTrue(turns.complete(turn.id, "new", "正确回答"))
        assertTrue(turns.complete(turn.id, "new", "重复回答"))
        tasks.finishSuccess(turn.taskId, "")
        repeat(3) {
            val id = tasks.enqueue("other", "{}")
            tasks.claimForRun(id, "owner", Long.MAX_VALUE)
            tasks.finishSuccess(id, "")
        }
        tasks.pruneOldTasks(1)
        assertNotNull(tasks.getById(turn.taskId))
        assertEquals(listOf("原文", "正确回答"), ChatConversationRepository(db).getBySession("s").map { it.content })
    }

    @Test fun cancellationAndDeletionPreventLateReplies() = fixture { driver, db, turns, tasks ->
        val turn = turns.accept("s", "a", "稍后取消", 123L, "UTC")
        tasks.claimForRun(turn.taskId, "owner", Long.MAX_VALUE)
        turns.cancel(turn.id)
        assertFalse(turns.complete(turn.id, "owner", "不应保存"))
        val deleted = turns.accept("s", "b", "稍后删除", 124L, "UTC")
        tasks.claimForRun(deleted.taskId, "owner", Long.MAX_VALUE)
        AssistantPrivacyRepository(driver).deleteChatMessage(deleted.id.toString())
        assertFalse(turns.complete(deleted.id, "owner", "不应保存"))
        assertEquals(listOf("稍后取消"), ChatConversationRepository(db).getBySession("s").map { it.content })
    }

    @Test fun runnerRecoversInterruptedTurnWithoutDuplicatingOriginalOrCompletedAnswer() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val turns = AssistantTurnRepository(driver)
            val tasks = AsyncTaskRepository(db)
            val turn = turns.accept("s", "client", "持久输入", 123L, "UTC")
            var interrupted = true
            val handler = AssistantTurnTaskHandler(turns, tasks) { accepted ->
                assertEquals("持久输入", turns.input(accepted))
                if (interrupted) throw CancellationException("system interruption")
                "恢复后的回答"
            }
            val runner = AsyncTaskRunner(tasks, AsyncTaskHandlerRegistry(listOf(handler)))
            assertFailsWith<CancellationException> { runner.run(turn.taskId) }
            assertEquals("retrying", tasks.getById(turn.taskId)!!.status)
            driver.execute(null, "UPDATE async_task SET run_after_ms = 0 WHERE id = ${turn.taskId}", 0)
            interrupted = false
            assertEquals(AsyncTaskRunOutcome.Succeeded, runner.run(turn.taskId))
            // Model completion was durable before the runner's finish; replay must not re-run it.
            driver.execute(null, "UPDATE async_task SET status = 'retrying', run_after_ms = 0, lease_owner = NULL WHERE id = ${turn.taskId}", 0)
            val replayHandler = AssistantTurnTaskHandler(turns, tasks) { error("Must reuse completed reply") }
            assertEquals(AsyncTaskRunOutcome.Succeeded, AsyncTaskRunner(tasks, AsyncTaskHandlerRegistry(listOf(replayHandler))).run(turn.taskId))
            assertEquals(listOf("持久输入", "恢复后的回答"), ChatConversationRepository(db).getBySession("s").map { it.content })
        }
    }

    @Test fun acceptedInputsDoNotWaitForNetworkAndQueuedTurnsSeePriorReplyButNotFutureInputs() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val turns = AssistantTurnRepository(driver)
            val tasks = AsyncTaskRepository(db)
            val first = turns.accept("s", "a", "第一个问题", 1L, "UTC")
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val handler = AssistantTurnTaskHandler(turns, tasks) { accepted ->
                if (accepted.id == first.id) {
                    entered.complete(Unit)
                    release.await()
                    "第一个回答"
                } else {
                    val context = AssistantPrivacyRepository(driver).history("s", beforeInputId = accepted.id)
                    assertEquals(listOf("第一个问题", "第一个回答"), context.map { it.content })
                    "第二个回答"
                }
            }
            val runner = AsyncTaskRunner(tasks, AsyncTaskHandlerRegistry(listOf(handler)))
            val running = async { runner.run(first.taskId) }
            entered.await()
            val second = turns.accept("s", "b", "第二个问题", 2L, "UTC")
            turns.accept("s", "c", "未来的问题", 3L, "UTC")
            assertEquals(AsyncTaskRunOutcome.RetryScheduled, runner.run(second.taskId))
            release.complete(Unit)
            assertEquals(AsyncTaskRunOutcome.Succeeded, running.await())
            assertEquals(AsyncTaskRunOutcome.Succeeded, runner.run(second.taskId))
        }
    }

    @Test fun migrationFromV36KeepsOriginalsAndExclusions() = fixture { driver, db, _, _ ->
        val chats = ChatConversationRepository(db)
        val id = chats.acceptUserInput("legacy", "已有原文")
        AssistantPrivacyRepository(driver).exclude("chat", id.toString())
        driver.execute(null, "DROP TABLE assistant_turn", 0)
        driver.execute(null, "DROP TABLE assistant_session", 0)
        val settings = SettingRepository(db)
        settings.upsert(com.dailysatori.config.SettingKeys.schemaVersion, "36")
        com.dailysatori.service.migration.DatabaseMigration(driver, settings).runMigrations()
        assertEquals(listOf("已有原文"), chats.getBySession("legacy").map { it.content })
        assertTrue(AssistantPrivacyRepository(driver).isExcluded("chat", id.toString()))
        val accepted = AssistantTurnRepository(driver).accept("new", "client", "新原文", 10L, "UTC")
        assertNotNull(AssistantTurnRepository(driver).get(accepted.id))
    }

    @Test fun handlerRejectsMismatchedTaskAndDoesNotLeakProviderErrors() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val tasks = AsyncTaskRepository(DailySatoriDatabase(driver))
            val turns = AssistantTurnRepository(driver)
            val turn = turns.accept("s", "client", "input", 123L, "UTC")
            val handler = AssistantTurnTaskHandler(turns, tasks) { error("secret-token https://private.test/?key=secret") }
            val runner = AsyncTaskRunner(tasks, AsyncTaskHandlerRegistry(listOf(handler)))
            assertEquals(AsyncTaskRunOutcome.RetryScheduled, runner.run(turn.taskId))
            assertFalse(tasks.getById(turn.taskId)!!.last_error_message.contains("secret"))
            val other = tasks.enqueue("assistant_turn", "{\"version\":1,\"turnId\":${turn.id}}")
            assertEquals(AsyncTaskRunOutcome.Failed, runner.run(other))
            val malformed = tasks.enqueue("assistant_turn", "{\"version\":{},\"turnId\":${turn.id}}")
            assertEquals(AsyncTaskRunOutcome.Failed, runner.run(malformed))
            assertEquals("assistant_invalid_turn", tasks.getById(malformed)!!.last_error_code)
        }
    }
}

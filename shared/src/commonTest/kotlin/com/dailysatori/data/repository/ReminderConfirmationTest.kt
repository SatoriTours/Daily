package com.dailysatori.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.service.reminder.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.datetime.*
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach

class ReminderConfirmationTest {
    @Test fun confirmationAndDoubleClickCreateOneReminderPolicyAndDurableIntent() = fixture { driver,db,batches,id ->
        val first=assertNotNull(batches.confirmDraft(id,0,0,draft(),ReminderProfileSnapshot.gentle()))
        assertEquals(first,batches.confirmDraft(id,0,0,draft(),ReminderProfileSnapshot.strong()))
        assertEquals(listOf("1"),strings(driver,"SELECT COUNT(*) FROM reminder"))
        assertEquals(listOf("1"),strings(driver,"SELECT COUNT(*) FROM reminder_policy_snapshot"))
        assertEquals(listOf("1"),strings(driver,"SELECT COUNT(*) FROM async_task WHERE type='reminder_schedule'"))
        val row=batches.getBatch(id)!!.drafts.single()
        assertEquals("SCHEDULE_PENDING",row.confirmationState)
        assertEquals("$id:0",row.reminderId)
        assertFalse(row.confirmed)
        assertEquals(ReminderProfileSnapshot.gentle(),ReminderRepository(db).get("$id:0")!!.profile)
    }

    @Test fun intentInsertFailureRollsBackReminderPolicyAndDraftAssociation() = fixture { driver,_,batches,id ->
        driver.execute(null,"""CREATE TRIGGER reject_schedule BEFORE INSERT ON async_task
            WHEN new.type='reminder_schedule' BEGIN SELECT RAISE(ABORT,'injected'); END""",0)
        assertFails { batches.confirmDraft(id,0,0,draft(),ReminderProfileSnapshot.gentle()) }
        assertEquals(listOf("0"),strings(driver,"SELECT COUNT(*) FROM reminder"))
        assertEquals(listOf("0"),strings(driver,"SELECT COUNT(*) FROM reminder_policy_snapshot"))
        assertNull(batches.getBatch(id)!!.drafts.single().reminderId)
        assertEquals("PENDING",batches.getBatch(id)!!.drafts.single().confirmationState)
    }

    @Test fun staleDraftCannotConfirmAndCreatedReminderCannotBeDiscardedImplicitly() = fixture { _,_,batches,id ->
        assertTrue(batches.updateDraftUiState(id,0,"user override",true,false,expectedRevision=0))
        assertNull(batches.confirmDraft(id,0,0,draft(),ReminderProfileSnapshot.gentle()))
        assertNotNull(batches.confirmDraft(id,0,1,draft(),ReminderProfileSnapshot.gentle()))
        assertFalse(batches.updateDraftUiState(id,0,"discard",false,true))
        assertFalse(batches.discardReady(id))
    }

    @Test fun externalSuccessBeforeInterruptionCanRetryWithoutDuplicatingReminder() = fixture { _,db,batches,id ->
        val taskId=assertNotNull(batches.confirmDraft(id,0,0,draft(),ReminderProfileSnapshot.gentle()))
        val tasks=AsyncTaskRepository(db)
        val active=mutableSetOf<String>()
        var interrupt=true
        val handler=ReminderScheduleTaskHandler(batches,tasks) { reminder ->
            active+=reminder
            if(interrupt) throw kotlinx.coroutines.CancellationException("injected after platform success")
        }
        fun execute(owner:String)=kotlinx.coroutines.runBlocking {
            assertTrue(tasks.claimForRun(taskId,owner,kotlinx.datetime.Clock.System.now().toEpochMilliseconds()+60000))
            val reporter=object:com.dailysatori.service.asynctask.AsyncTaskProgressReporter {
                override val executionLease=com.dailysatori.service.asynctask.AsyncTaskExecutionLease(taskId,owner)
                override suspend fun report(current:Long,total:Long,message:String,checkpointJson:String) {}
            }
            handler.execute(taskId,tasks.getById(taskId)!!.payload_json,"",reporter)
        }
        assertFailsWith<kotlinx.coroutines.CancellationException> { execute("first") }
        assertFalse(batches.getBatch(id)!!.drafts.single().confirmed)
        interrupt=false
        tasks.markRunningForRetryAfterProcessRestart(kotlinx.datetime.Clock.System.now().toEpochMilliseconds())
        execute("restarted")
        assertEquals(setOf("$id:0"),active)
        assertTrue(batches.getBatch(id)!!.drafts.single().confirmed)
        assertEquals(1,db.dailySatoriQueries.countReminderPolicies().executeAsOne())
    }

    @Test fun oldVersionCannotConfirmANewerDefinitionAndDeletionDoesNotResurrectIt() = fixture { driver,db,batches,id ->
        assertNotNull(batches.confirmDraft(id,0,0,draft(),ReminderProfileSnapshot.gentle()))
        val reminders=ReminderRepository(db)
        assertTrue(reminders.update("$id:0",ReminderEdit(0,content="new definition")))
        assertFalse(batches.finishSchedule("$id:0",0,true))
        assertFalse(batches.getBatch(id)!!.drafts.single().confirmed)
        assertTrue(batches.beginSchedule("$id:0",1))
        assertTrue(batches.finishSchedule("$id:0",1,true))
        assertTrue(batches.getBatch(id)!!.drafts.single().confirmed)
        reminders.delete("$id:0")
        assertFalse(batches.beginSchedule("$id:0",1))
        assertEquals(listOf("0"),strings(driver,"SELECT COUNT(*) FROM reminder_ai_draft"))
        assertEquals(listOf("0"),strings(driver,"SELECT COUNT(*) FROM reminder"))
    }

    @Test fun observesWorkerFailureWithoutAnyBatchParentChange() = fixture { _,_,batches,id ->
        batches.confirmDraft(id,0,0,draft(),ReminderProfileSnapshot.gentle())
        runBlocking {
            val started=CompletableDeferred<Unit>()
            val observed=async {
                withTimeout(3000) { batches.observeBatch(id).onEach { started.complete(Unit) }
                    .first { it?.drafts?.single()?.confirmationState=="SCHEDULING_FAILED" } }
            }
            started.await()
            batches.finishSchedule("$id:0",0,false)
            assertEquals("SCHEDULING_FAILED",observed.await()!!.drafts.single().confirmationState)
        }
    }

    private fun draft()=ReminderDraft("caller-id-is-not-authority","original",
        LocalDate(2026,10,27),LocalDate(2026,10,28),LocalTime(10,0),timeZone=TimeZone.UTC)
    private fun strings(driver:JdbcSqliteDriver,sql:String)=com.dailysatori.service.migration.DatabaseSchemaInventory.strings(driver,sql)
    private fun fixture(block:(JdbcSqliteDriver,DailySatoriDatabase,ReminderAiBatchRepository,String)->Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver); driver.execute(null,"PRAGMA foreign_keys=ON",0)
            val db=DailySatoriDatabase(driver); val batches=ReminderAiBatchRepository(db)
            val batch=batches.enqueueOrReuse("original",TimeZone.UTC,LocalDate(2026,10,27))
            batches.markReady(batch.id,listOf(ReminderAiBatchDraft(0,"original","opaque AI suggestion")))
            block(driver,db,batches,batch.id)
        }
    }
}

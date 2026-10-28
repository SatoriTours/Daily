package com.dailysatori.service.migration

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.*
import com.dailysatori.service.reminder.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.datetime.*
import kotlin.test.*

class ReminderDraftMigrationTest {
    @Test fun migratedUnselectedFailureRetriesStoredCurrentReminderWithoutOldDraftOrDuplicateCreation()=fixture { driver,db,batch ->
        val reminders=ReminderRepository(db,TimeZone.UTC)
        reminders.createConfirmed(draft("$batch:0"),ReminderProfileSnapshot.gentle())
        insert(driver,batch,0,0,0,"SCHEDULING_FAILED","$batch:0",null)
        driver.execute(null,"UPDATE reminder_ai_draft SET selected=0",0)
        driver.execute(null,"UPDATE reminder_ai_batch SET status='CONFIRMED'",0)
        ReminderDraftMigration.migrate(driver)
        val batches=ReminderAiBatchRepository(db)
        val original=batches.getBatch(batch)!!.drafts.single()
        assertFalse(original.selected)
        assertTrue(batches.reconcileScheduleIntents().isEmpty())
        assertTrue(reminders.update("$batch:0",ReminderEdit(0,content="current reminder, not opaque old draft")))
        val task=assertNotNull(batches.retryDraftSchedule(batch,0))
        assertEquals(task,batches.retryDraftSchedule(batch,0))
        val tasks=AsyncTaskRepository(db)
        val intent=kotlinx.serialization.json.Json.decodeFromString<ReminderScheduleIntent>(tasks.getById(task)!!.payload_json)
        assertEquals(ReminderScheduleIntent("$batch:0",1),intent)
        val pending=batches.getBatch(batch)!!.drafts.single()
        assertFalse(pending.selected)
        assertEquals(original.draftJson,pending.draftJson)
        assertEquals(original.overrideJson,pending.overrideJson)
        assertEquals(listOf("1"),strings(driver,"SELECT COUNT(*) FROM reminder"))
        assertEquals(listOf("1"),strings(driver,"SELECT COUNT(*) FROM async_task WHERE type='reminder_schedule'"))
        val scheduled=mutableSetOf<String>()
        val handler=ReminderScheduleTaskHandler(batches,tasks) { scheduled+=it }
        val runner=com.dailysatori.service.asynctask.AsyncTaskRunner(tasks,
            com.dailysatori.service.asynctask.AsyncTaskHandlerRegistry(listOf(handler)))
        assertEquals(com.dailysatori.service.asynctask.AsyncTaskRunOutcome.Succeeded,
            kotlinx.coroutines.runBlocking { runner.run(task) })
        assertEquals(setOf("$batch:0"),scheduled)
        assertTrue(batches.getBatch(batch)!!.drafts.single().confirmed)
        assertNull(batches.retryDraftSchedule(batch,0))
    }

    @Test fun retryOutboxFailureRollsBackMigratedUnselectedFailedState()=fixture { driver,db,batch ->
        ReminderRepository(db,TimeZone.UTC).createConfirmed(draft("$batch:0"),ReminderProfileSnapshot.gentle())
        insert(driver,batch,0,0,0,"SCHEDULING_FAILED","$batch:0",null)
        driver.execute(null,"UPDATE reminder_ai_draft SET selected=0",0)
        ReminderDraftMigration.migrate(driver)
        val batches=ReminderAiBatchRepository(db)
        val before=batches.getBatch(batch)!!.drafts.single()
        driver.execute(null,"""CREATE TRIGGER reject_retry BEFORE INSERT ON async_task
            WHEN new.type='reminder_schedule' BEGIN SELECT RAISE(ABORT,'injected'); END""",0)
        assertFails { batches.retryDraftSchedule(batch,0) }
        assertEquals(before,batches.getBatch(batch)!!.drafts.single())
        assertEquals(listOf("0"),strings(driver,"SELECT COUNT(*) FROM async_task"))
    }

    @Test fun provenStatesAndInterruptedCreationPreservePayloadsIdsAndSequence() = fixture { driver,db,batch ->
        val repo=ReminderRepository(db,TimeZone.UTC)
        for(index in 2..4) repo.createConfirmed(draft("$batch:$index"),ReminderProfileSnapshot.gentle())
        insert(driver,batch,0,0,0,"PENDING",null,null)
        insert(driver,batch,1,0,1,"PENDING",null,null)
        insert(driver,batch,2,0,0,"SCHEDULING_FAILED","$batch:2",null)
        insert(driver,batch,3,1,0,"CONFIRMED","$batch:3",99)
        insert(driver,batch,4,0,0,"PENDING",null,null)
        driver.execute(null,"UPDATE sqlite_sequence SET seq=90 WHERE name='reminder_ai_draft'",0)
        val original=strings(driver,"SELECT hex(draft_json)||'|'||hex(override_json)||'|'||hex(source_text) FROM reminder_ai_draft ORDER BY id")
        val plan=assertNotNull(ReminderDraftMigration.plan(driver))
        ReminderDraftMigration.migrate(driver)
        plan.requireApplied(driver)
        assertEquals(listOf("PENDING","DISCARDED","SCHEDULING_FAILED","CONFIRMED","SCHEDULE_PENDING"),
            strings(driver,"SELECT state FROM reminder_ai_draft ORDER BY id"))
        assertEquals(original,strings(driver,"SELECT hex(draft_json)||'|'||hex(override_json)||'|'||hex(source_text) FROM reminder_ai_draft ORDER BY id"))
        assertEquals(listOf("90"),strings(driver,"SELECT seq FROM sqlite_sequence WHERE name='reminder_ai_draft'"))
        assertEquals(1,ReminderAiBatchRepository(db).reconcileScheduleIntents().size)
        assertEquals(listOf("1"),strings(driver,"SELECT COUNT(*) FROM async_task WHERE type='reminder_schedule'"))
        assertFails { driver.execute(null,"DELETE FROM reminder WHERE id=?",1) { bindString(0,"$batch:3") } }
        assertFails { driver.execute(null,"UPDATE reminder_ai_draft SET reminder_id=NULL WHERE state='CONFIRMED'",0) }
        ReminderDraftMigration.migrate(driver)
        assertTrue(ReminderAiBatchRepository(db).reconcileScheduleIntents().isEmpty())
        assertEquals(listOf("1"),strings(driver,"SELECT COUNT(*) FROM async_task WHERE type='reminder_schedule'"))
    }

    @Test fun unprovableConfirmationIsRejectedWithoutChangingOriginalRow() = fixture { driver,_,batch ->
        insert(driver,batch,0,1,0,"CONFIRMED",null,99)
        assertFails { ReminderDraftMigration.migrate(driver) }
        assertEquals(listOf("1|CONFIRMED"),strings(driver,"SELECT confirmed||'|'||confirmation_state FROM reminder_ai_draft"))
        assertTrue("confirmed" in strings(driver,"SELECT name FROM pragma_table_info('reminder_ai_draft')"))
    }

    private fun insert(driver:JdbcSqliteDriver,batch:String,index:Int,confirmed:Long,discarded:Long,state:String,id:String?,at:Long?) {
        driver.execute(null,"""INSERT INTO reminder_ai_draft(id,batch_id,source_index,source_text,draft_json,override_json,
            confirmed,discarded,confirmation_state,reminder_id,confirmed_at,created_at,updated_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,10,20)""",11) {
            bindLong(0,60L+index); bindString(1,batch); bindLong(2,index.toLong()); bindString(3,"original\u0000tail")
            bindString(4,"opaque AI $index"); bindString(5,"user override $index")
            bindLong(6,confirmed); bindLong(7,discarded); bindString(8,state); bindString(9,id); bindLong(10,at)
        }
    }
    private fun draft(id:String)=ReminderDraft(id,"body",LocalDate(2026,10,27),LocalDate(2026,10,28),LocalTime(10,0),timeZone=TimeZone.UTC)
    private fun strings(driver:JdbcSqliteDriver,sql:String)=DatabaseSchemaInventory.strings(driver,sql)
    private fun fixture(block:(JdbcSqliteDriver,DailySatoriDatabase,String)->Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db=DailySatoriDatabase(driver)
            val batch=ReminderAiBatchRepository(db).enqueueOrReuse("input",TimeZone.UTC,LocalDate(2026,10,27)).id
            driver.execute(null,"DROP TABLE reminder_ai_draft",0)
            driver.execute(null,legacyTable,0)
            driver.execute(null,"PRAGMA foreign_keys=ON",0)
            block(driver,db,batch)
        }
    }
    private val legacyTable="""CREATE TABLE reminder_ai_draft(id INTEGER PRIMARY KEY AUTOINCREMENT,
        batch_id TEXT NOT NULL REFERENCES reminder_ai_batch(id) ON DELETE CASCADE,source_index INTEGER NOT NULL,
        source_text TEXT NOT NULL,draft_json TEXT NOT NULL,override_json TEXT NOT NULL DEFAULT '',selected INTEGER NOT NULL DEFAULT 1,
        discarded INTEGER NOT NULL DEFAULT 0,confirmation_state TEXT NOT NULL DEFAULT 'PENDING',reminder_id TEXT,
        confirmed INTEGER NOT NULL DEFAULT 0,confirmed_at INTEGER,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,
        UNIQUE(batch_id,source_index))"""
}

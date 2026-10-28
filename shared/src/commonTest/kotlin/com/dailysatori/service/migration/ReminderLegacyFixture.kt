package com.dailysatori.service.migration

import app.cash.sqldelight.db.SqlDriver
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.*

/** Physical pre-42 structures; never derive old rows through current generated insert APIs. */
internal object ReminderLegacyFixture {
    const val id="policy-batch:0"
    val payload="""{"kind":"GENTLE","unknown":"original"}"""+"\u0000tail\t\n"
    fun installLegacyTables(driver:SqlDriver) {
        DiaryLegacyFieldsFixture.install(driver)
        val indices=DatabaseSchemaInventory.strings(driver,
            "SELECT sql FROM sqlite_master WHERE type='index' AND tbl_name IN ('reminder','reminder_event','reminder_ai_draft') AND sql IS NOT NULL")
        val events=DatabaseSchemaInventory.strings(driver,"SELECT sql FROM sqlite_master WHERE name='reminder_event'").single()
        driver.execute(null,"DROP VIEW reminder_record",0)
        driver.execute(null,"DROP TRIGGER reminder_policy_referenced",0)
        listOf("reminder_ai_draft","reminder_event","reminder").forEach { driver.execute(null,"DROP TABLE $it",0) }
        driver.execute(null,ReminderPolicyMigration.reminderTable.replace(
            "policy_snapshot_id TEXT NOT NULL REFERENCES reminder_policy_snapshot(id) ON DELETE RESTRICT",
            "profile_json TEXT NOT NULL"),0)
        driver.execute(null,events,0)
        driver.execute(null,legacyDraft,0)
        indices.filterNot { "idx_reminder_ai_draft_reminder" in it }.forEach { driver.execute(null,it,0) }
    }
    fun seed(driver:SqlDriver) {
        installLegacyTables(driver)
        listOf(id,"independent").forEach { reminder ->
            driver.execute(null,"""INSERT INTO reminder(id,content,notes,status,start_date,end_date,first_reminder_time,
                active_day_rule,recurrence_rule,time_zone_id,profile_json,version,state_date,dismissal_count,
                next_occurrence_at,last_notified_at,last_dismissed_at,deadline_at,created_at,updated_at)
                VALUES(?,?,'original notes','ACTIVE','2026-09-01','2027-09-01','10:00','daily','once','UTC',
                ?,7,'2026-09-01',2,123,124,125,126,10,20)""",3) {
                bindString(0,reminder);bindString(1,"original body\u0000tail");bindString(2,payload)
            }
        }
        driver.execute(null,"""INSERT INTO reminder_event(id,reminder_id,event_type,scheduled_at,actual_at,metadata_json)
            VALUES(8,'$id','confirmed',123,124,'{"original":true}')""",0)
        DailySatoriDatabase(driver).dailySatoriQueries.insertReminderAiBatch("policy-batch",null,
            "original input","original input","UTC","2026-09-01","READY_FOR_CONFIRMATION",null,0,3,null,"",null,10,20)
        driver.execute(null,"""INSERT INTO reminder_ai_draft(id,batch_id,source_index,source_text,draft_json,override_json,
            selected,created_at,updated_at) VALUES(7,'policy-batch',0,?,?,?,0,10,20)""",3) {
            bindString(0,"original source\u0000tail");bindString(1,"AI original\u0000tail");bindString(2,"edited original\u0000tail")
        }
        driver.execute(null,"UPDATE sqlite_sequence SET seq=99 WHERE name='reminder_ai_draft'",0)
        driver.execute(null,"UPDATE sqlite_sequence SET seq=100 WHERE name='reminder_event'",0)
    }
    fun assertMigrated(driver:SqlDriver) {
        val q=DailySatoriDatabase(driver).dailySatoriQueries
        val reminder=q.selectReminderById(id).executeAsOne()
        assertEquals(payload,reminder.profile_json)
        assertEquals(7L,reminder.version)
        assertEquals(2L,reminder.dismissal_count)
        assertEquals("original body\u0000tail",reminder.content)
        assertEquals(126L,reminder.deadline_at)
        assertEquals(1,DatabaseSchemaInventory.strings(driver,"SELECT COUNT(*) FROM reminder_policy_snapshot").single().toInt())
        val draft=q.selectReminderAiDraftsByBatchId("policy-batch").executeAsOne()
        assertEquals("SCHEDULE_PENDING",draft.state)
        assertEquals(0L,draft.selected)
        assertEquals(id,draft.reminder_id)
        assertEquals("AI original\u0000tail",draft.draft_json)
        assertEquals("edited original\u0000tail",draft.override_json)
        assertEquals(listOf("99"),DatabaseSchemaInventory.strings(driver,"SELECT seq FROM sqlite_sequence WHERE name='reminder_ai_draft'"))
        assertEquals(listOf("100"),DatabaseSchemaInventory.strings(driver,"SELECT seq FROM sqlite_sequence WHERE name='reminder_event'"))
    }
    private val legacyDraft="""CREATE TABLE reminder_ai_draft(
        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
        batch_id TEXT NOT NULL REFERENCES reminder_ai_batch(id) ON DELETE CASCADE,
        source_index INTEGER NOT NULL,source_text TEXT NOT NULL,draft_json TEXT NOT NULL,
        override_json TEXT NOT NULL DEFAULT '',selected INTEGER NOT NULL DEFAULT 1,discarded INTEGER NOT NULL DEFAULT 0,
        confirmation_state TEXT NOT NULL DEFAULT 'PENDING',reminder_id TEXT,
        confirmed INTEGER NOT NULL DEFAULT 0,confirmed_at INTEGER,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,
        UNIQUE(batch_id,source_index))"""
}

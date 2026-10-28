package com.dailysatori.service.migration

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.*

class ReminderPolicyMigrationTest {
    @Test fun sharesOnlyCompleteEqualPayloadsAndPreservesReminderStateAndHistory() = fixture { driver ->
        val payload="""{"kind":"GENTLE","unknown":{"future":"keep\\u0000tail"}}"""
        insert(driver,"one",payload); insert(driver,"two",payload); insert(driver,"three"," $payload")
        driver.execute(null,"INSERT INTO reminder_event(id,reminder_id,event_type,actual_at) VALUES(9,'one','dismissed',99)",0)
        driver.execute(null,"UPDATE sqlite_sequence SET seq=90 WHERE name='reminder_event'",0)
        ReminderPolicyMigration.migrate(driver)
        assertEquals(listOf("2"),strings(driver,"SELECT COUNT(*) FROM reminder_policy_snapshot"))
        assertEquals(listOf(payload," $payload").sorted(),strings(driver,"SELECT payload_json FROM reminder_policy_snapshot").sorted())
        assertEquals(listOf("1"),strings(driver,"SELECT COUNT(DISTINCT policy_snapshot_id) FROM reminder WHERE id IN ('one','two')"))
        assertEquals(listOf("7|4|42|43|44|45|46|47|48"),strings(driver,
            "SELECT version||'|'||dismissal_count||'|'||last_notified_at||'|'||last_dismissed_at||'|'||completed_at||'|'||next_occurrence_at||'|'||deadline_at||'|'||created_at||'|'||updated_at FROM reminder WHERE id='one'"))
        assertEquals(listOf("9|one|dismissed|99"),strings(driver,"SELECT id||'|'||reminder_id||'|'||event_type||'|'||actual_at FROM reminder_event"))
        assertEquals(listOf("90"),strings(driver,"SELECT seq FROM sqlite_sequence WHERE name='reminder_event'"))
        assertFalse("profile_json" in strings(driver,"SELECT name FROM pragma_table_info('reminder')"))
        assertFails { driver.execute(null,"UPDATE reminder_policy_snapshot SET payload_json='changed'",0) }
        assertFails { driver.execute(null,"DELETE FROM reminder_policy_snapshot",0) }
        ReminderPolicyMigration.migrate(driver)
        assertEquals(listOf("2"),strings(driver,"SELECT COUNT(*) FROM reminder_policy_snapshot"))
    }

    @Test fun hashCollisionsAndDifferentFormatsNeverMergeDifferentPayloads() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db=DailySatoriDatabase(driver)
            val policies=com.dailysatori.data.repository.ReminderPolicySnapshots(db) { "0".repeat(64) }
            val first=policies.intern("opaque first")
            val second=policies.intern("opaque second")
            assertNotEquals(first,second)
            assertEquals(first,policies.intern("opaque first"))
            assertNotEquals(first,policies.intern("opaque first",format=2))
            assertEquals(3,db.dailySatoriQueries.countReminderPolicies().executeAsOne())
        }
    }

    private fun insert(driver:JdbcSqliteDriver,id:String,payload:String) {
        driver.execute(null,"""INSERT INTO reminder(id,content,notes,status,start_date,end_date,first_reminder_time,
            active_day_rule,recurrence_rule,time_zone_id,profile_json,version,state_date,dismissal_count,
            last_notified_at,last_dismissed_at,completed_at,next_occurrence_at,deadline_at,created_at,updated_at)
            VALUES(?,'original','notes','DISMISSED','2026-10-01','2027-10-01','09:01','daily','once',
            'Pacific/Auckland',?,7,'2026-10-02',4,42,43,44,45,46,47,48)""",2) { bindString(0,id); bindString(1,payload) }
    }
    private fun strings(driver:JdbcSqliteDriver,sql:String)=DatabaseSchemaInventory.strings(driver,sql)
    private fun fixture(block:(JdbcSqliteDriver)->Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val events=strings(driver,"SELECT sql FROM sqlite_master WHERE name='reminder_event'").single()
            driver.execute(null,"DROP TABLE reminder_event",0)
            driver.execute(null,"DROP VIEW reminder_record",0)
            driver.execute(null,"DROP TABLE reminder",0)
            driver.execute(null,ReminderPolicyMigration.reminderTable.replace(
                "policy_snapshot_id TEXT NOT NULL REFERENCES reminder_policy_snapshot(id) ON DELETE RESTRICT",
                "profile_json TEXT NOT NULL"),0)
            driver.execute(null,events,0)
            driver.execute(null,"PRAGMA foreign_keys=ON",0)
            block(driver)
        }
    }
}

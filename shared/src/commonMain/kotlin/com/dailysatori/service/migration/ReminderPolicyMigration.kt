package com.dailysatori.service.migration

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.*
import com.dailysatori.data.repository.ReminderPolicySnapshots
import com.dailysatori.shared.db.DailySatoriDatabase

/** Frozen schema42 transformation; preserves raw payloads, parent IDs and every event. */
internal object ReminderPolicyMigration {
    const val reminderTable="""CREATE TABLE reminder (
        id TEXT PRIMARY KEY NOT NULL, content TEXT NOT NULL, notes TEXT NOT NULL DEFAULT '',
        status TEXT NOT NULL,start_date TEXT NOT NULL,end_date TEXT NOT NULL,first_reminder_time TEXT NOT NULL,
        active_day_rule TEXT NOT NULL,recurrence_rule TEXT NOT NULL DEFAULT 'once',time_zone_id TEXT NOT NULL,
        policy_snapshot_id TEXT NOT NULL REFERENCES reminder_policy_snapshot(id) ON DELETE RESTRICT,
        version INTEGER NOT NULL DEFAULT 0,state_date TEXT,dismissal_count INTEGER NOT NULL DEFAULT 0,
        last_notified_at INTEGER,last_dismissed_at INTEGER,completed_at INTEGER,next_occurrence_at INTEGER,
        deadline_at INTEGER,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)"""
    const val view="""CREATE VIEW reminder_record AS
        SELECT r.*,p.payload_json AS profile_json FROM reminder r JOIN reminder_policy_snapshot p ON p.id=r.policy_snapshot_id"""
    val policySchema=listOf(
        """CREATE TABLE IF NOT EXISTS reminder_policy_snapshot(id TEXT PRIMARY KEY NOT NULL,format_version INTEGER NOT NULL,
            payload_json TEXT NOT NULL,sha256 TEXT NOT NULL,created_at INTEGER NOT NULL)""",
        "CREATE INDEX IF NOT EXISTS idx_reminder_policy_hash ON reminder_policy_snapshot(format_version,sha256)",
        """CREATE TRIGGER IF NOT EXISTS reminder_policy_immutable BEFORE UPDATE ON reminder_policy_snapshot
            BEGIN SELECT RAISE(ABORT,'Immutable reminder policy'); END""",
        """CREATE TRIGGER IF NOT EXISTS reminder_policy_referenced BEFORE DELETE ON reminder_policy_snapshot
            WHEN EXISTS(SELECT 1 FROM reminder WHERE policy_snapshot_id=old.id)
            BEGIN SELECT RAISE(ABORT,'Referenced reminder policy'); END""",
    )

    fun migrate(driver:SqlDriver)=object:TransacterImpl(driver) {}.transaction {
        val expected=plan(driver) ?: return@transaction
        policySchema.forEach { driver.execute(null,it,0) }
        val rows=driver.executeQuery(null,"SELECT profile_json,created_at FROM reminder ORDER BY created_at,id",{ c ->
            val result=mutableListOf<Pair<String,Long>>()
            while(c.next().value) result+=checkNotNull(c.getString(0)) to checkNotNull(c.getLong(1))
            QueryResult.Value(result)
        },0).value
        val snapshots=ReminderPolicySnapshots(DailySatoriDatabase(driver))
        rows.forEach { (payload,created) -> snapshots.intern(payload,createdAt=created) }
        rebuildReminder(driver)
        expected.requireApplied(driver)
    }

    class Plan(private val policies:Set<Map<String,ReminderDraftMigration.Cell>>) {
        fun requireApplied(driver:SqlDriver) {
            val actual=ReminderDraftMigration.read(driver,"reminder_policy_snapshot",
                listOf("format_version","payload_json","sha256","created_at"))
            check(actual.size==policies.size && actual.toSet()==policies) { "提醒配置快照迁移核对失败" }
        }
    }

    fun plan(driver:SqlDriver):Plan? {
        if("profile_json" !in strings(driver,"SELECT name FROM pragma_table_info('reminder')")) return null
        if("reminder_policy_snapshot" in strings(driver,"SELECT name FROM sqlite_master WHERE type='table'"))
            check(strings(driver,"SELECT COUNT(*) FROM reminder_policy_snapshot").single()=="0") { "提醒快照结构版本冲突" }
        val rows=ReminderDraftMigration.read(driver,"reminder",listOf("profile_json","created_at"))
        return Plan(rows.groupBy { it.getValue("profile_json") }.map { (payload,group) ->
            mapOf("format_version" to ReminderDraftMigration.Cell.number(1),"payload_json" to payload,
                "sha256" to ReminderDraftMigration.Cell.of(com.dailysatori.service.externalfavorites.sha256Hex(payload.text())),
                "created_at" to ReminderDraftMigration.Cell.number(group.minOf { it.getValue("created_at").long() }))
        }.toSet())
    }

    private fun rebuildReminder(driver:SqlDriver) {
        val objects=strings(driver,"""SELECT sql FROM sqlite_master WHERE tbl_name IN ('reminder','reminder_event')
            AND type IN ('index','trigger') AND sql IS NOT NULL ORDER BY type,name""")
        val events=checkNotNull(strings(driver,"SELECT sql FROM sqlite_master WHERE name='reminder_event'").singleOrNull())
        val sequence=strings(driver,"SELECT seq FROM sqlite_sequence WHERE name='reminder_event'").singleOrNull()
        fun exec(sql:String) { driver.execute(null,sql,0) }
        check(strings(driver,"SELECT name FROM sqlite_master WHERE name IN ('reminder_policy42','reminder_events42')").isEmpty())
        exec("CREATE TABLE reminder_events42 AS SELECT * FROM reminder_event")
        exec("DROP TABLE reminder_event")
        exec("DROP VIEW IF EXISTS reminder_record")
        exec(reminderTable.replace("CREATE TABLE reminder (","CREATE TABLE reminder_policy42 ("))
        val oldColumns=strings(driver,"SELECT name FROM pragma_table_info('reminder')")
        val columns=oldColumns.joinToString { if(it=="profile_json") "policy_snapshot_id" else quote(it) }
        val projection=oldColumns.joinToString { if(it=="profile_json")
            "(SELECT id FROM reminder_policy_snapshot p WHERE p.format_version=1 AND p.payload_json=r.profile_json ORDER BY p.id LIMIT 1)"
            else "r."+quote(it) }
        exec("INSERT INTO reminder_policy42($columns) SELECT $projection FROM reminder r")
        exec("DROP TRIGGER IF EXISTS reminder_policy_referenced")
        exec("DROP TABLE reminder")
        exec("ALTER TABLE reminder_policy42 RENAME TO reminder")
        exec(events)
        exec("INSERT INTO reminder_event SELECT * FROM reminder_events42")
        if(sequence!=null) ReminderDraftMigration.restoreSequence(driver,"reminder_event",sequence.toLong())
        exec("DROP TABLE reminder_events42")
        objects.forEach(::exec)
        exec(policySchema.last())
        exec(view)
    }

    private fun quote(s:String)="\""+s.replace("\"","\"\"")+"\""
    private fun strings(driver:SqlDriver,sql:String)=DatabaseSchemaInventory.strings(driver,sql)
}

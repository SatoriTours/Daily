package com.dailysatori.service.migration

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.*
import com.dailysatori.service.reminder.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Never infer confirmation from flags alone or discard opaque AI/user payload bytes. */
internal object ReminderDraftMigration {
    val columns=listOf("id","batch_id","source_index","source_text","draft_json","override_json","selected",
        "state","reminder_id","confirmed_at","revision","created_at","updated_at")
    val legacyColumns=(columns-setOf("state","revision"))+listOf("discarded","confirmation_state","confirmed")
    const val table="""CREATE TABLE reminder_ai_draft(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        batch_id TEXT NOT NULL REFERENCES reminder_ai_batch(id) ON DELETE CASCADE,
        source_index INTEGER NOT NULL,source_text TEXT NOT NULL,draft_json TEXT NOT NULL,
        override_json TEXT NOT NULL DEFAULT '',selected INTEGER NOT NULL DEFAULT 1,
        state TEXT NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','DISCARDED','SCHEDULE_PENDING','SCHEDULING_FAILED','CONFIRMED')),
        reminder_id TEXT REFERENCES reminder(id) ON DELETE RESTRICT,confirmed_at INTEGER,
        revision INTEGER NOT NULL DEFAULT 0 CHECK(revision >= 0),created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,
        CHECK((state IN ('PENDING','DISCARDED') AND reminder_id IS NULL AND confirmed_at IS NULL)
            OR(state IN ('SCHEDULE_PENDING','SCHEDULING_FAILED') AND reminder_id IS NOT NULL AND confirmed_at IS NULL)
            OR(state='CONFIRMED' AND reminder_id IS NOT NULL AND confirmed_at IS NOT NULL)),
        UNIQUE(batch_id,source_index))"""
    val indexes=listOf(
        "CREATE INDEX IF NOT EXISTS idx_reminder_ai_draft_batch_source ON reminder_ai_draft(batch_id,source_index ASC)",
        "CREATE UNIQUE INDEX IF NOT EXISTS idx_reminder_ai_draft_reminder ON reminder_ai_draft(reminder_id) WHERE reminder_id IS NOT NULL",
    )
    data class Cell(val type:String,val hex:String) {
        fun text():String {
            check(type=="text")
            val bytes=hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            return bytes.decodeToString().also { check(it.encodeToByteArray().contentEquals(bytes)) }
        }
        fun long()=also { check(type=="integer") }.hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray().decodeToString().toLong()
        fun sql()=when(type) { "null"->"NULL"; "integer"->long().toString(); "text"->"CAST(X'$hex' AS TEXT)"; else->error("Unsupported migration value") }
        companion object {
            fun of(value:String?)=if(value==null) Cell("null","") else Cell("text",value.encodeToByteArray().joinToString("") {
                (it.toInt() and 255).toString(16).padStart(2,'0').uppercase() })
            fun number(value:Long)=Cell("integer",of(value.toString()).hex)
        }
    }
    class Plan(val rows:List<Map<String,Cell>>,val intents:List<ReminderScheduleIntent>,
        val retainedRows:List<Map<String,Cell>>,private val previousLegacyRows:List<Map<String,Cell>>) {
        fun requireApplied(driver:SqlDriver) {
            check(read(driver,"reminder_ai_draft",columns)==rows) { "提醒草稿迁移核对失败" }
            val expected=(previousLegacyRows+retainedRows).sortedBy { it.getValue("id").long() }
            check(ReminderDraftLegacyArchive.read(driver)==expected) { "历史提醒草稿保留核对失败" }
        }
    }

    fun plan(driver:SqlDriver):Plan? {
        val found=DatabaseSchemaInventory.strings(driver,"SELECT name FROM pragma_table_info('reminder_ai_draft')")
        if(found.isEmpty() || "state" in found) return null
        val defaults=ReminderDraftLegacyArchive.defaults
        check((found-legacyColumns.toSet()).isEmpty() && (legacyColumns-found.toSet()).all { it in defaults }) { "提醒草稿结构不完整" }
        val reminders=driver.executeQuery(null,"SELECT id,version FROM reminder",{ c ->
            val result=mutableMapOf<String,Long>()
            while(c.next().value) result[checkNotNull(c.getString(0))]=checkNotNull(c.getLong(1))
            QueryResult.Value(result)
        },0).value
        val candidates=read(driver,"reminder_ai_draft",legacyColumns,defaults.filterKeys { it !in found })
            .map { it to convert(driver,it,reminders) }
        val duplicateIds=candidates.mapNotNull { it.second?.getValue("reminder_id")?.takeIf { c -> c.type!="null" } }
            .groupingBy { it }.eachCount().filterValues { it>1 }.keys
        val (valid,retained)=candidates.partition { (_,row) -> row!=null && row.getValue("reminder_id") !in duplicateIds }
        val rows=valid.map { checkNotNull(it.second) }
        val intents=rows.filter { it.getValue("state").text() in setOf("SCHEDULE_PENDING","SCHEDULING_FAILED") }.map {
            val id=it.getValue("reminder_id").text()
            ReminderScheduleIntent(id,reminders.getValue(id))
        }
        return Plan(rows,intents,retained.map { it.first },ReminderDraftLegacyArchive.read(driver))
    }

    private fun convert(driver:SqlDriver,row:Map<String,Cell>,reminders:Map<String,Long>):Map<String,Cell>? {
        val confirmed=row.getValue("confirmed").long()
        val discarded=row.getValue("discarded").long()
        val old=row.getValue("confirmation_state").text()
        val oldId=row.getValue("reminder_id").takeIf { it.type!="null" }?.text()
        val at=row.getValue("confirmed_at").takeIf { it.type!="null" }?.long()
        val stable=row.getValue("batch_id").text()+":"+row.getValue("source_index").long()
        val hasReminder=oldId!=null && oldId in reminders
        val recovered=oldId==null && stable in reminders && DatabaseSchemaInventory.strings(driver,
            "SELECT COUNT(*) FROM reminder_event WHERE reminder_id=${Cell.of(stable).sql()} AND event_type='confirmed'").single().toLong()>0
        val state=when {
            confirmed==1L && discarded==0L && old=="CONFIRMED" && hasReminder && at!=null -> "CONFIRMED"
            confirmed==0L && discarded==0L && old=="SCHEDULING_FAILED" && hasReminder && at==null -> "SCHEDULING_FAILED"
            confirmed==0L && discarded==0L && old=="PENDING" && oldId==null && at==null && stable !in reminders -> "PENDING"
            confirmed==0L && discarded==1L && old=="PENDING" && oldId==null && at==null && stable !in reminders -> "DISCARDED"
            confirmed==0L && discarded==0L && old=="PENDING" && at==null && recovered -> "SCHEDULE_PENDING"
            else -> return null
        }
        val id=if(recovered) stable else oldId
        return columns.associateWith { when(it) {
            "state"->Cell.of(state); "revision"->Cell.number(0); "reminder_id"->Cell.of(id); else->row.getValue(it)
        } }
    }

    fun migrate(driver:SqlDriver)=object:TransacterImpl(driver) {}.transaction {
        val plan=plan(driver) ?: return@transaction
        ReminderDraftLegacyArchive.retain(driver,plan.retainedRows)
        val sequence=DatabaseSchemaInventory.strings(driver,"SELECT seq FROM sqlite_sequence WHERE name='reminder_ai_draft'").singleOrNull()
        val objects=DatabaseSchemaInventory.strings(driver,"""SELECT sql FROM sqlite_master WHERE tbl_name='reminder_ai_draft'
            AND type IN ('index','trigger') AND sql IS NOT NULL ORDER BY type,name""")
        fun exec(sql:String) { driver.execute(null,sql,0) }
        check(DatabaseSchemaInventory.strings(driver,"SELECT name FROM sqlite_master WHERE name='reminder_ai_draft42'").isEmpty())
        exec(table.replace("CREATE TABLE reminder_ai_draft(","CREATE TABLE reminder_ai_draft42("))
        plan.rows.forEach { row ->
            val values=columns.joinToString { if(it in setOf("state","revision","reminder_id")) row.getValue(it).sql() else "\"$it\"" }
            exec("INSERT INTO reminder_ai_draft42(${columns.joinToString()}) SELECT $values FROM reminder_ai_draft WHERE id=${row.getValue("id").long()}")
        }
        exec("DROP TABLE reminder_ai_draft")
        exec("ALTER TABLE reminder_ai_draft42 RENAME TO reminder_ai_draft")
        objects.forEach(::exec); indexes.forEach(::exec)
        if(sequence!=null) restoreSequence(driver,"reminder_ai_draft",sequence.toLong())
        plan.requireApplied(driver)
    }

    fun restoreSequence(driver:SqlDriver,table:String,sequence:Long) {
        driver.execute(null,"INSERT INTO sqlite_sequence(name,seq) SELECT '$table',$sequence WHERE NOT EXISTS(SELECT 1 FROM sqlite_sequence WHERE name='$table')",0)
        driver.execute(null,"UPDATE sqlite_sequence SET seq=MAX(seq,$sequence) WHERE name='$table'",0)
    }

    fun read(driver:SqlDriver,table:String,columns:List<String>,defaults:Map<String,Cell> = emptyMap(),orderBy:String="id"):List<Map<String,Cell>> {
        val projection=columns.joinToString {
            val expression=defaults[it]?.sql() ?: "\"$it\""
            "typeof($expression),hex(CAST($expression AS BLOB))"
        }
        return driver.executeQuery(null,"SELECT $projection FROM $table ORDER BY $orderBy",{ c ->
            val rows=mutableListOf<Map<String,Cell>>()
            while(c.next().value) rows+=columns.mapIndexed { index,name ->
                name to Cell(checkNotNull(c.getString(index*2)),checkNotNull(c.getString(index*2+1)))
            }.toMap()
            QueryResult.Value(rows)
        },0).value
    }
}

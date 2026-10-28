package com.dailysatori.service.migration

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.SqlDriver
import com.dailysatori.data.repository.IdeaSnapshots
import com.dailysatori.service.ideatopic.IdeaSnapshotJson
import com.dailysatori.service.externalfavorites.sha256Hex
import com.dailysatori.shared.db.DailySatoriDatabase

/** Exact, transactional schema42 conversion. Unknown event kinds remain opaque v1. */
internal object IdeaSnapshotMigration {
    private val legacy=linkedMapOf(
        "idea_topic" to "id,title,description,provenance_summary,conclusions,next_action,status,merged_into_topic_id,context_revision,created_at,updated_at",
        "idea_topic_source" to "id,topic_id,original_topic_id,source_type,source_record_id,snapshot_json,captured_at,created_at,updated_at",
        "idea_topic_event" to "id,topic_id,original_topic_id,kind,payload_json,created_at",
        "idea_topic_session" to "id,topic_id,original_topic_id,title,summary,summary_through_message_id,summary_covered_message_ids,summary_status,created_at,updated_at",
        "idea_topic_message" to "id,session_id,role,content,status,error,created_at",
    ).mapValues { it.value.split(',')+listOf("rowid") }
    private fun strings(d:SqlDriver,sql:String)=DatabaseSchemaInventory.strings(d,sql)
    private fun read(d:SqlDriver,t:String,c:List<String>)=ReminderDraftMigration.read(d,t,c)
    private fun text(r:Map<String,ReminderDraftMigration.Cell>,key:String)=r.getValue(key).text()

    class Plan internal constructor(internal val rows:Map<String,List<Map<String,ReminderDraftMigration.Cell>>>) {
        internal val events=rows.getValue("idea_topic_event").associate { row ->
            text(row,"id") to IdeaSnapshotJson.split(text(row,"kind"),text(row,"payload_json"))
        }
        internal val snapshots=buildMap<String,Long> {
            rows.getValue("idea_topic_source").forEach {
                val raw=text(it,"snapshot_json");val at=it.getValue("created_at").long()
                put(raw,minOf(get(raw) ?: at,at))
            }
            rows.getValue("idea_topic_event").forEach { row ->
                events.getValue(text(row,"id")).snapshots.forEach { raw ->
                    val at=row.getValue("created_at").long();put(raw,minOf(get(raw) ?: at,at))
                }
            }
        }
        fun requireApplied(d:SqlDriver) {
            val store=IdeaSnapshots(DailySatoriDatabase(d))
            rows.forEach { (table,expected) ->
                val cols=when(table) {
                    "idea_topic_source"->legacy.getValue(table)-"snapshot_json"+"snapshot_id"
                    "idea_topic_event"->legacy.getValue(table)+"payload_version"
                    else->legacy.getValue(table)
                }
                val actual=read(d,table,cols).map { row ->
                    if(table=="idea_topic_source") row-"snapshot_id"+("snapshot_json" to ReminderDraftMigration.Cell.of(store.payload(text(row,"snapshot_id"))))
                    else row
                }
                val wanted=if(table=="idea_topic_event") expected.map { row ->
                    val split=events.getValue(text(row,"id"))
                    row+mapOf("payload_json" to ReminderDraftMigration.Cell.of(split.payload),
                        "payload_version" to ReminderDraftMigration.Cell.number(if(split.role==null) 1 else 2))
                } else expected
                check(actual==wanted) { "点子迁移原始数据核对失败: $table" }
            }
            requireSnapshots(d)
            requireReferences(d,store)
        }
        private fun requireSnapshots(d:SqlDriver) {
            val expected=snapshots.map { (raw,at) -> mapOf("format_version" to ReminderDraftMigration.Cell.number(1),
                "payload_json" to ReminderDraftMigration.Cell.of(raw),"sha256" to ReminderDraftMigration.Cell.of(sha256Hex(raw)),
                "created_at" to ReminderDraftMigration.Cell.number(at)) }.toSet()
            val actual=read(d,"idea_source_snapshot",listOf("format_version","payload_json","sha256","created_at"))
            check(actual.size==expected.size && actual.toSet()==expected) { "点子快照迁移核对失败" }
        }
        private fun requireReferences(d:SqlDriver,store:IdeaSnapshots) {
            val q=DailySatoriDatabase(d).dailySatoriQueries
            rows.getValue("idea_topic_event").forEach { row ->
                val id=text(row,"id");val split=events.getValue(id)
                val refs=q.selectIdeaEventSnapshots(id).executeAsList()
                check(refs.map { it.role }==split.snapshots.map { split.role } &&
                    refs.map { it.ordinal }==split.snapshots.indices.map { it.toLong() } &&
                    refs.map { store.payload(it.snapshot_id) }==split.snapshots) { "点子事件快照引用核对失败" }
                if(split.role!=null) {
                    val reconstructed=store.eventPayload(id)
                    val original=text(row,"payload_json")
                    check(kotlinx.serialization.json.Json.parseToJsonElement(reconstructed)==
                        kotlinx.serialization.json.Json.parseToJsonElement(original)) { "点子事件重建核对失败" }
                }
            }
            check(strings(d,"SELECT COUNT(*) FROM idea_event_snapshot").single().toInt()==events.values.sumOf { it.snapshots.size })
        }
    }

    fun plan(d:SqlDriver):Plan? {
        val found=strings(d,"SELECT name FROM pragma_table_info('idea_topic_source')")
        if("snapshot_id" in found) return null
        val present=strings(d,"SELECT name FROM sqlite_master WHERE type='table'").toSet().intersect(legacy.keys)
        check(present.isEmpty() || present==legacy.keys) { "点子旧结构不完整" }
        listOf("idea_source_snapshot","idea_event_snapshot").forEach { table ->
            if(strings(d,"SELECT name FROM sqlite_master WHERE name='$table'").isNotEmpty())
                check(strings(d,"SELECT COUNT(*) FROM $table").single()=="0") { "点子快照结构版本冲突" }
        }
        val rows=legacy.mapValues { (table,cols) ->
            val actual=strings(d,"SELECT name FROM pragma_table_info('$table')")
            if(actual.isEmpty()) emptyList() else {
                check(actual.toSet()==(cols-"rowid").toSet()) { "点子旧结构不完整: $table" }
                read(d,table,cols)
            }
        }
        validateRelations(rows)
        rows.getValue("idea_topic_source").forEach {
            val key=IdeaSnapshotJson.key(text(it,"snapshot_json"))
            check(key.type==text(it,"source_type") && key.recordId==text(it,"source_record_id")) { "点子来源key冲突" }
        }
        return Plan(rows)
    }

    private fun validateRelations(rows:Map<String,List<Map<String,ReminderDraftMigration.Cell>>>) {
        val topics=rows.getValue("idea_topic").associate { text(it,"id") to it.getValue("merged_into_topic_id") }
        listOf("idea_topic_source","idea_topic_event","idea_topic_session").forEach { table ->
            check(rows.getValue(table).all { text(it,"topic_id") in topics }) { "点子当前归属缺失: $table" }
        }
        val sessions=rows.getValue("idea_topic_session").map { text(it,"id") }.toSet()
        check(rows.getValue("idea_topic_message").all { text(it,"session_id") in sessions }) { "点子会话归属缺失" }
        topics.keys.forEach { start ->
            val visited=mutableSetOf<String>();var id:String?=start
            while(id!=null) {
                check(visited.add(id)) { "点子合并环" }
                val next=checkNotNull(topics[id]) { "点子合并目标缺失" }
                id=if(next.type=="null") null else next.text()
            }
        }
    }

    fun migrate(d:SqlDriver)=object:TransacterImpl(d) {}.transaction {
        val plan=plan(d) ?: return@transaction
        val objects=strings(d,"""SELECT sql FROM sqlite_master WHERE tbl_name IN
            ('idea_topic','idea_topic_source','idea_topic_event','idea_topic_session','idea_topic_message')
            AND type IN ('index','trigger') AND sql IS NOT NULL
            AND name NOT IN ('idea_topic_merge_cycle_insert','idea_topic_merge_cycle_update') ORDER BY type,name""")
        fun exec(sql:String) { d.execute(null,sql,0) }
        if(strings(d,"SELECT name FROM sqlite_master WHERE name='idea_source_snapshot'").isEmpty()) exec(IdeaSnapshotSchema.snapshot)
        val store=IdeaSnapshots(DailySatoriDatabase(d))
        val ids=plan.snapshots.entries.sortedBy { it.value }.associate { it.key to store.intern(it.key,createdAt=it.value) }
        listOf("idea_topic_merge_cycle_insert","idea_topic_merge_cycle_update","idea_snapshot_immutable","idea_snapshot_referenced").forEach {
            exec("DROP TRIGGER IF EXISTS $it")
        }
        exec("DROP VIEW IF EXISTS idea_source_record")
        exec("DROP TABLE IF EXISTS idea_event_snapshot")
        legacy.keys.reversed().forEach { exec("DROP TABLE IF EXISTS $it") }
        IdeaSnapshotSchema.tables.values.forEach(::exec)
        exec(IdeaSnapshotSchema.references)
        plan.rows.forEach { (table,rows) -> rows.forEach { old ->
            var row=old
            if(table=="idea_topic_source") row=row-"snapshot_json"+("snapshot_id" to ReminderDraftMigration.Cell.of(ids.getValue(text(row,"snapshot_json"))))
            if(table=="idea_topic_event") {
                val split=plan.events.getValue(text(row,"id"))
                row=row+mapOf("payload_json" to ReminderDraftMigration.Cell.of(split.payload),
                    "payload_version" to ReminderDraftMigration.Cell.number(if(split.role==null) 1 else 2))
            }
            exec("INSERT INTO $table(${row.keys.joinToString()}) VALUES(${row.values.joinToString { it.sql() }})")
        } }
        plan.events.forEach { (event,split) -> split.snapshots.forEachIndexed { ordinal,raw ->
            DailySatoriDatabase(d).dailySatoriQueries.insertIdeaEventSnapshot(event,checkNotNull(split.role),ordinal.toLong(),ids.getValue(raw))
        } }
        objects.forEach(::exec)
        IdeaSnapshotSchema.indices.forEach(::exec)
        IdeaSnapshotSchema.triggers.forEach(::exec)
        exec(IdeaSnapshotSchema.view)
        plan.requireApplied(d)
    }
}

package com.dailysatori.service.migration

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.SqlDriver

internal object AssistantRelationsMigration {
    private val columns=linkedMapOf(
        "assistant_session" to "id,created_at,summary,summary_sources_json,summary_version",
        "chat_conversation" to "id,session_id,role,content,search_results,steps,created_at",
        "assistant_turn" to "id,session_id,client_message_id,task_id,received_at,time_zone_id,response_message_id,requires_resume,action_plan_json,context_json",
        "assistant_action" to "id,turn_id,kind,parameters_json,fingerprint,status,target_id,task_id,error_code",
        "assistant_reference" to "turn_id,source_type,source_id,title",
        "memory_entry" to "id,assistant_kind,assistant_state,evidence,source_version,type,source_type,source_id,title,content,tags,created_at,updated_at"
    ).mapValues { it.value.split(',')+"rowid" }
    private val defaults=mapOf("summary" to Cell.of(""),"summary_sources_json" to Cell.of("[]"),"summary_version" to Cell.number(0),
        "assistant_kind" to Cell.of("legacy"),"assistant_state" to Cell.of("active"),"evidence" to Cell.of(""),"source_version" to Cell.of(""))
    private fun s(d:SqlDriver,sql:String)=DatabaseSchemaInventory.strings(d,sql)
    private fun read(d:SqlDriver,t:String,cols:List<String>)=ReminderDraftMigration.read(d,t,cols,orderBy="rowid")
    private fun text(r:Map<String,Cell>,k:String)=r.getValue(k).text()

    class Plan internal constructor(internal val rows:Map<String,List<Map<String,Cell>>>,internal val sessions:List<Map<String,Cell>>) {
        fun requireApplied(d:SqlDriver) {
            rows.forEach { (table,old) ->
                val extra=if(table=="memory_entry") listOf("content_origin") else emptyList()
                val wanted=when(table) {
                    "assistant_session"->old+sessions
                    "memory_entry"->old.map { it+("content_origin" to Cell.of("legacy_unknown")) }
                    else->old
                }
                val actual=read(d,table,columns.getValue(table)+extra)
                check(actual.associateBy { it.getValue("rowid") }==wanted.associateBy { it.getValue("rowid") }) { "助手迁移数据核对失败: $table" }
            }
            validateRelations(d)
        }
    }
    fun plan(d:SqlDriver):Plan? {
        if("content_origin" in s(d,"SELECT name FROM pragma_table_info('memory_entry')")) return null
        val rows=columns.mapValues { (table,cols) ->
            val found=s(d,"SELECT name FROM pragma_table_info('$table')")
            if(found.isEmpty()) emptyList() else {
                check(found.toSet()==(cols-"rowid").filterNot { it in defaults && it !in found }.toSet()) { "助手旧结构不完整: $table" }
                ReminderDraftMigration.read(d,table,cols,defaults.filterKeys { it !in found },orderBy="rowid")
            }
        }
        val existing=rows.getValue("assistant_session").map { text(it,"id") }.toSet()
        check(existing.none { it.isBlank() }) { "助手会话标识无效" }
        var rowid=rows.getValue("assistant_session").maxOfOrNull { it.getValue("rowid").long() } ?: 0
        val added=rows.getValue("chat_conversation").groupBy { text(it,"session_id") }.toSortedMap().mapNotNull { (id,messages) ->
            check(id.isNotBlank()) { "助手消息会话标识无效" }
            if(id in existing) null else mapOf("id" to Cell.of(id),"created_at" to Cell.number(messages.minOf { it.getValue("created_at").long() }),
                "summary" to Cell.of(""),"summary_sources_json" to Cell.of("[]"),"summary_version" to Cell.number(0),"rowid" to Cell.number(++rowid))
        }
        validateRows(d,rows,existing+added.map { text(it,"id") })
        return Plan(rows,added)
    }
    fun validateRelations(d:SqlDriver) {
        // Startup checks identities in SQLite, without loading full conversation bodies into memory.
        check(s(d,"""SELECT 'invalid' FROM assistant_turn t
            LEFT JOIN chat_conversation input ON input.id=t.id
            LEFT JOIN chat_conversation response ON response.id=t.response_message_id
            WHERE (input.id IS NOT NULL AND (input.role!='user' OR input.session_id!=t.session_id))
                OR (input.id IS NULL AND NOT EXISTS(SELECT 1 FROM assistant_memory_exclusion x
                    WHERE x.source_type='chat' AND x.source_id=CAST(t.id AS TEXT)))
                OR (t.response_message_id IS NOT NULL AND (
                    (response.id IS NOT NULL AND (response.role!='assistant' OR response.session_id!=t.session_id))
                    OR (response.id IS NULL AND NOT EXISTS(SELECT 1 FROM assistant_memory_exclusion x
                        WHERE x.source_type='chat' AND x.source_id=CAST(t.response_message_id AS TEXT)))))
            LIMIT 1""").isEmpty()) { "助手消息关联冲突，保留原库" }
    }
    private fun validateRows(d:SqlDriver,rows:Map<String,List<Map<String,Cell>>>,sessions:Set<String>) {
        val chats=rows.getValue("chat_conversation").associateBy { it.getValue("id").long() }
        val tasks=if(s(d,"SELECT name FROM sqlite_master WHERE name='async_task'").isEmpty()) emptySet()
            else s(d,"SELECT CAST(id AS TEXT) FROM async_task").map { it.toLong() }.toSet()
        val turns=rows.getValue("assistant_turn")
        val tombstones=if(s(d,"SELECT name FROM sqlite_master WHERE name='assistant_memory_exclusion'").isEmpty()) emptySet()
            else s(d,"SELECT source_id FROM assistant_memory_exclusion WHERE source_type='chat'").toSet()
        check(chats.values.all { text(it,"session_id") in sessions }) { "助手消息会话缺失" }
        turns.forEach { turn ->
            val session=text(turn,"session_id")
            check(session in sessions && turn.getValue("task_id").long() in tasks) { "助手执行归属缺失" }
            fun message(id:Long,role:String) {
                val row=chats[id]
                check(if(row==null) id.toString() in tombstones else text(row,"role")==role && text(row,"session_id")==session) { "助手消息关联冲突，保留原库" }
            }
            message(turn.getValue("id").long(),"user")
            turn.getValue("response_message_id").takeIf { it.type!="null" }?.let { message(it.long(),"assistant") }
        }
        val ids=turns.map { it.getValue("id").long() }.toSet()
        listOf("assistant_action","assistant_reference").forEach { table ->
            check(rows.getValue(table).all { it.getValue("turn_id").long() in ids }) { "助手执行关联缺失: $table" }
        }
        check(rows.getValue("assistant_action").all { it.getValue("task_id").let { id -> id.type=="null" || id.long() in tasks } }) { "助手动作任务缺失" }
    }
    fun migrate(d:SqlDriver)=object:TransacterImpl(d) {}.transaction {
        val p=plan(d) ?: return@transaction
        val objects=s(d,"""SELECT sql FROM sqlite_master WHERE tbl_name IN ('chat_conversation','assistant_turn','assistant_action','assistant_reference')
            AND type IN ('index','trigger') AND sql IS NOT NULL ORDER BY type,name""")
        val sequence=s(d,"SELECT seq FROM sqlite_sequence WHERE name='chat_conversation'").singleOrNull()
        p.sessions.forEach { insert(d,"assistant_session",it) }
        AssistantRelationsSchema.tables.keys.reversed().forEach { d.execute(null,"DROP TABLE IF EXISTS $it",0) }
        AssistantRelationsSchema.tables.values.forEach { d.execute(null,it,0) }
        AssistantRelationsSchema.tables.keys.forEach { t -> p.rows.getValue(t).forEach { insert(d,t,it) } }
        objects.forEach { d.execute(null,it,0) }
        // Replace the old two-column index even when the original DDL omitted IF NOT EXISTS.
        d.execute(null,"DROP INDEX IF EXISTS idx_chat_conversation_session_created",0)
        AssistantRelationsSchema.indexes.forEach { d.execute(null,it,0) }
        AssistantRelationsSchema.triggers.forEach { d.execute(null,it,0) }
        if(sequence!=null) ReminderDraftMigration.restoreSequence(d,"chat_conversation",sequence.toLong())
        d.execute(null,"ALTER TABLE memory_entry ADD COLUMN content_origin TEXT NOT NULL DEFAULT 'legacy_unknown' CHECK(content_origin IN ('automatic','manual','accepted','legacy_unknown'))",0)
        p.requireApplied(d)
    }
    private fun insert(d:SqlDriver,table:String,row:Map<String,Cell>) {
        d.execute(null,"INSERT INTO $table(${row.keys.joinToString()}) VALUES(${row.values.joinToString { it.sql() }})",0)
    }
}
private typealias Cell=ReminderDraftMigration.Cell

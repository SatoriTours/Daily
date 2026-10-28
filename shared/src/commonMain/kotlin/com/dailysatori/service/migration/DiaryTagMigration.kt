package com.dailysatori.service.migration

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.dailysatori.service.diary.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** Strict, deterministic conversion. The caller's candidate transaction owns the commit. */
internal object DiaryTagMigration {
    const val vocabularyKey = "diary_tag_vocabulary_v1"
    const val undoKey = "diary_tag_merge_undo_v1"
    const val baselineKey = "diary_tag_content_baseline_v1"
    private val stateKey = Regex("diary_tag_state_v1_(-?[0-9]+)")
    val columns = linkedMapOf(
        "diary_tag_definition" to listOf("id","name","vocabulary_position"),
        "diary_tag_alias" to listOf("alias","tag_id"),
        "diary_tag_assignment" to listOf("diary_id","tag_id","origin","state","position","source_revision"),
        "diary_tag_history" to listOf("id","diary_id","tag_id","operation_id","decision","origin","position",
            "source_revision","source_fingerprint","edit_revision","occurred_at"),
        "diary_tag_processing" to listOf("root_diary_id","processed_revision","edit_revision"),
        "diary_tag_catalog_state" to listOf("id","format_version","pending_names_json","suggestions_json","merge_undo_json","baseline_json"),
    )
    val schema = listOf(
        """CREATE TABLE IF NOT EXISTS diary_tag_definition (
            id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE, vocabulary_position INTEGER)""",
        """CREATE TABLE IF NOT EXISTS diary_tag_alias (
            alias TEXT PRIMARY KEY NOT NULL, tag_id INTEGER NOT NULL REFERENCES diary_tag_definition(id) ON DELETE RESTRICT)""",
        """CREATE TABLE IF NOT EXISTS diary_tag_assignment (
            diary_id INTEGER NOT NULL REFERENCES diary(id) ON DELETE CASCADE,
            tag_id INTEGER NOT NULL REFERENCES diary_tag_definition(id) ON DELETE RESTRICT,
            origin TEXT NOT NULL CHECK(origin IN ('manual','automatic')),
            state TEXT NOT NULL CHECK(state IN ('active','suppressed')),
            position INTEGER NOT NULL CHECK(position >= 0), source_revision INTEGER,
            PRIMARY KEY(diary_id,tag_id))""",
        """CREATE TABLE IF NOT EXISTS diary_tag_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT, diary_id INTEGER NOT NULL REFERENCES diary(id) ON DELETE CASCADE,
            tag_id INTEGER NOT NULL REFERENCES diary_tag_definition(id) ON DELETE RESTRICT,
            operation_id TEXT NOT NULL, decision TEXT NOT NULL CHECK(decision IN ('active','suppressed','removed')),
            origin TEXT NOT NULL CHECK(origin IN ('manual','automatic')), position INTEGER NOT NULL CHECK(position >= 0),
            source_revision INTEGER, source_fingerprint TEXT, edit_revision INTEGER NOT NULL CHECK(edit_revision >= 0),
            occurred_at INTEGER, UNIQUE(operation_id,diary_id,tag_id))""",
        """CREATE INDEX IF NOT EXISTS idx_diary_tag_history_revision ON diary_tag_history(diary_id,edit_revision,id)""",
        """CREATE TABLE IF NOT EXISTS diary_tag_processing (
            root_diary_id INTEGER PRIMARY KEY REFERENCES diary(id) ON DELETE CASCADE,
            processed_revision INTEGER, edit_revision INTEGER NOT NULL CHECK(edit_revision >= 0))""",
        """CREATE TABLE IF NOT EXISTS diary_tag_catalog_state (
            id INTEGER PRIMARY KEY CHECK(id=1), format_version INTEGER NOT NULL CHECK(format_version=1),
            pending_names_json TEXT NOT NULL, suggestions_json TEXT NOT NULL, merge_undo_json TEXT, baseline_json TEXT)""",
    )

    val runtimeSchema = listOf(
        """CREATE VIEW diary_active_tags AS
SELECT diary_id,group_concat(name,',') AS tags
FROM (SELECT a.diary_id,t.name FROM diary_tag_assignment a
JOIN diary_tag_definition t ON t.id=a.tag_id WHERE a.state='active'
ORDER BY a.diary_id,a.position,a.tag_id)
GROUP BY diary_id""",
        """CREATE TRIGGER diary_tag_fts_insert AFTER INSERT ON diary_tag_assignment BEGIN
DELETE FROM diary_fts WHERE rowid IN (SELECT id FROM diary WHERE id=new.diary_id);
INSERT INTO diary_fts(rowid,content,tags)
SELECT d.id,d.content,p.tags FROM diary d LEFT JOIN diary_active_tags p ON p.diary_id=d.id WHERE d.id=new.diary_id;

END""",
        """CREATE TRIGGER diary_tag_fts_update AFTER UPDATE ON diary_tag_assignment BEGIN
DELETE FROM diary_fts WHERE rowid IN (SELECT id FROM diary WHERE id=new.diary_id);
INSERT INTO diary_fts(rowid,content,tags)
SELECT d.id,d.content,p.tags FROM diary d LEFT JOIN diary_active_tags p ON p.diary_id=d.id WHERE d.id=new.diary_id;
DELETE FROM diary_fts WHERE rowid IN (SELECT id FROM diary WHERE id=old.diary_id);
INSERT INTO diary_fts(rowid,content,tags)
SELECT d.id,d.content,p.tags FROM diary d LEFT JOIN diary_active_tags p ON p.diary_id=d.id WHERE d.id=old.diary_id;
END""",
        """CREATE TRIGGER diary_tag_fts_delete AFTER DELETE ON diary_tag_assignment BEGIN
DELETE FROM diary_fts WHERE rowid IN (SELECT id FROM diary WHERE id=old.diary_id);
INSERT INTO diary_fts(rowid,content,tags)
SELECT d.id,d.content,p.tags FROM diary d LEFT JOIN diary_active_tags p ON p.diary_id=d.id WHERE d.id=old.diary_id;

END""",
        """CREATE TRIGGER diary_tag_fts_definition AFTER UPDATE OF name ON diary_tag_definition BEGIN
DELETE FROM diary_fts WHERE rowid IN (SELECT diary_id FROM diary_tag_assignment WHERE tag_id=new.id);
INSERT INTO diary_fts(rowid,content,tags)
SELECT d.id,d.content,p.tags FROM diary d LEFT JOIN diary_active_tags p ON p.diary_id=d.id
WHERE d.id IN (SELECT diary_id FROM diary_tag_assignment WHERE tag_id=new.id);
END""",
        """CREATE TRIGGER diary_tag_expire_after_revision AFTER UPDATE OF revision ON diary_thread_revision
WHEN new.revision IS NOT old.revision BEGIN
UPDATE diary_tag_processing SET edit_revision=edit_revision+1 WHERE root_diary_id=new.root_diary_id AND EXISTS(
SELECT 1 FROM diary_tag_assignment WHERE diary_id=new.root_diary_id AND state='suppressed' AND source_revision IS NOT new.revision);
INSERT INTO diary_tag_history(diary_id,tag_id,operation_id,decision,origin,position,source_revision,edit_revision,occurred_at)
SELECT a.diary_id,a.tag_id,'body:'||new.root_diary_id||':'||new.revision,'removed',a.origin,a.position,a.source_revision,
p.edit_revision,CAST(strftime('%s','now') AS INTEGER)*1000 FROM diary_tag_assignment a JOIN diary_tag_processing p ON p.root_diary_id=a.diary_id
WHERE a.diary_id=new.root_diary_id AND a.state='suppressed' AND a.source_revision IS NOT new.revision;
DELETE FROM diary_tag_assignment WHERE diary_id=new.root_diary_id AND state='suppressed' AND source_revision IS NOT new.revision;
END""",
        """CREATE TRIGGER diary_fts_insert AFTER INSERT ON diary BEGIN
    INSERT INTO diary_fts(rowid, content, tags)
    VALUES (new.id, new.content, (SELECT tags FROM diary_active_tags WHERE diary_id=new.id));
END""",
        """CREATE TRIGGER diary_fts_update AFTER UPDATE OF content ON diary BEGIN
    DELETE FROM diary_fts WHERE rowid = old.id;
    INSERT INTO diary_fts(rowid, content, tags)
    VALUES (new.id, new.content, (SELECT tags FROM diary_active_tags WHERE diary_id=new.id));
END"""
    )

    data class Plan(val rows:Map<String,List<List<Any?>>>,val consumed:Map<String,String?>) {
        fun requireApplied(driver:SqlDriver) {
            rows.forEach { (table,expected) ->
                check(encoded(expected)==actualRows(driver,table)) { "日记标签迁移核对失败: $table" }
            }
            check(strings(driver,"""SELECT d.id FROM diary d LEFT JOIN diary_fts f ON f.rowid=d.id
                LEFT JOIN diary_active_tags p ON p.diary_id=d.id WHERE f.rowid IS NULL OR
                typeof(f.content)<>typeof(d.content) OR hex(f.content)<>hex(d.content) OR
                typeof(f.tags)<>typeof(p.tags) OR hex(f.tags)<>hex(p.tags)
                UNION ALL SELECT f.rowid FROM diary_fts f WHERE NOT EXISTS(SELECT 1 FROM diary d WHERE d.id=f.rowid)""").isEmpty()) {
                "日记标签搜索投影核对失败"
            }
            consumed.forEach { (key,_) ->
                check(strings(driver,"SELECT key FROM setting WHERE key=?",key).isEmpty()) { "日记标签旧状态未退休" }
            }
        }
    }

    fun migrate(driver:SqlDriver) {
        if (isApplied(driver)) return
        val plan=plan(driver)
        DailySatoriDatabase(driver).dailySatoriQueries.transaction {
            schema.forEach { driver.execute(null,it,0) }
            columns.keys.forEach { table ->
                check(strings(driver,"SELECT 1 FROM $table LIMIT 1").isEmpty()) { "日记标签候选存在未识别数据" }
            }
            plan.rows.forEach { (table,rows) -> rows.forEach { row ->
                driver.execute(null,"INSERT INTO $table (${columns.getValue(table).joinToString()}) VALUES (${row.joinToString { "?" }})",row.size) {
                    row.forEachIndexed { i,value -> when(value) {
                        is Long -> bindLong(i,value)
                        is String -> bindString(i,value)
                        null -> bindString(i,null)
                        else -> error("Unsupported migration value")
                    } }
                }
            } }
            plan.consumed.keys.forEach { key ->
                driver.execute(null,"DELETE FROM setting WHERE key=?",1) { bindString(0,key) }
            }
            listOf("diary_fts_insert","diary_fts_update").forEach { driver.execute(null,"DROP TRIGGER IF EXISTS $it",0) }
            runtimeSchema.forEach { sql ->
                val name=Regex("CREATE (?:VIEW|TRIGGER) ([a-zA-Z0-9_]+)").find(sql)!!.groupValues[1]
                val kind=if(sql.startsWith("CREATE VIEW")) "VIEW" else "TRIGGER"
                driver.execute(null,"DROP $kind IF EXISTS $name",0)
                driver.execute(null,sql,0)
            }
            driver.execute(null,"DELETE FROM diary_fts",0)
            driver.execute(null,"INSERT INTO diary_fts(rowid,content,tags) SELECT d.id,d.content,p.tags FROM diary d LEFT JOIN diary_active_tags p ON p.diary_id=d.id",0)
            plan.requireApplied(driver)
        }
    }

    fun isApplied(driver:SqlDriver):Boolean =
        strings(driver,"SELECT name FROM sqlite_master WHERE type='table' AND name='diary_tag_catalog_state'").isNotEmpty() &&
            strings(driver,"SELECT id FROM diary_tag_catalog_state WHERE id=1").isNotEmpty()

    /** The same plan is captured BEFORE migration and independently checked against the candidate. */
    fun plan(driver:SqlDriver):Plan {
        columns.keys.filter { table -> strings(driver,
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",table).isNotEmpty() }.forEach { table ->
            check(strings(driver,"SELECT 1 FROM $table LIMIT 1").isEmpty()) { "日记标签候选存在未识别数据" }
        }
        val db=DailySatoriDatabase(driver)
        val q=db.dailySatoriQueries
        val settings=q.selectAllSettings().executeAsList().associate { it.key to it.value_ }
        val vocabulary=settings[vocabularyKey]?.let { Json.decodeFromString<DiaryTagVocabulary>(it) } ?: DiaryTagVocabulary()
        val aliases=flattenAliases(vocabulary.aliases)
        settings[undoKey]?.let { validateUndo(it) }
        settings[baselineKey]?.let { Json.decodeFromString<Map<Long,String>>(it) }
        val diaries=legacyDiaries(driver)
        val revisions=legacyRevisions(driver,diaries)
        val ids=diaries.map { it.id }.toSet()
        val consumed=linkedMapOf<String,String?>()
        listOf(vocabularyKey,undoKey,baselineKey).filter { it in settings }.forEach { consumed[it]=settings[it] }
        val states=linkedMapOf<Long,DiaryTagState>()
        settings.forEach { (nullableKey,value) ->
            val key=nullableKey ?: return@forEach
            val id=stateKey.matchEntire(key)?.groupValues?.get(1)?.toLongOrNull() ?: return@forEach
            if(key!="diary_tag_state_v1_$id") return@forEach
            // Orphan/unknown keys remain untouched, not guessed or prefix-deleted.
            if(id in ids) {
                val state=Json.decodeFromString<DiaryTagState>(checkNotNull(value))
                check(state.revision>=0) { "日记标签版本非法" }
                states[id]=state; consumed[key]=value
            }
        }
        val names=(vocabulary.names+aliases.values+diaries.flatMap { parseDiaryTags(it.tags) }+
            states.values.flatMap { it.automatic+it.suppressed }).distinct().sorted()
        val tagIds=names.mapIndexed { index,name -> name to index.toLong()+1 }.toMap()
        val rows=columns.keys.associateWith { mutableListOf<List<Any?>>() }
        names.forEach { name -> rows.getValue("diary_tag_definition")+=listOf(tagIds.getValue(name),name,
            vocabulary.names.indexOf(name).takeIf { it>=0 }?.toLong()) }
        aliases.forEach { (alias,target) -> rows.getValue("diary_tag_alias")+=listOf(alias,tagIds.getValue(target)) }
        diaries.forEach { diary -> appendDiary(diary,diaries,states[diary.id] ?: DiaryTagState(),revisions[diary.id],tagIds,rows) }
        rows.getValue("diary_tag_catalog_state")+=listOf(1L,1L,Json.encodeToString(vocabulary.pendingNames),
            Json.encodeToString(vocabulary.suggestions),settings[undoKey],settings[baselineKey])
        return Plan(rows,consumed)
    }

    private fun appendDiary(diary:com.dailysatori.shared.db.Diary_record,diaries:List<com.dailysatori.shared.db.Diary_record>,
        state:DiaryTagState,revision:Long?,tagIds:Map<String,Long>,rows:Map<String,MutableList<List<Any?>>>) {
        val content=if(diary.parent_diary_id==null) renderDiaryThreadContent(listOf(diary)+diaries.filter { it.parent_diary_id==diary.id }.sortedWith(compareBy({ it.created_at },{ it.id }))) else ""
        val fingerprint=diaryTagFingerprint(content)
        val mapped=revision.takeIf { state.contentFingerprint==fingerprint }
        val active=parseDiaryTags(diary.tags)
        check(active.none { it in state.suppressed && mapped!=null }) {
            "日记标签激活与抑制冲突"
        }
        rows.getValue("diary_tag_processing")+=listOf(diary.id,
            revision.takeIf { state.processedFingerprint==fingerprint },state.revision)
        active.forEachIndexed { position,name ->
            val origin=if(name in state.automatic) "automatic" else "manual"
            rows.getValue("diary_tag_assignment")+=listOf(diary.id,tagIds.getValue(name),origin,"active",
                position.toLong(),mapped.takeIf { origin=="automatic" })
            history(rows,diary.id,tagIds.getValue(name),"active",origin,position.toLong(),mapped,state)
        }
        state.suppressed.distinct().forEachIndexed { position,name ->
            history(rows,diary.id,tagIds.getValue(name),"suppressed","automatic",position.toLong(),mapped,state)
            if(mapped!=null && name !in active) rows.getValue("diary_tag_assignment")+=listOf(
                diary.id,tagIds.getValue(name),"automatic","suppressed",position.toLong(),mapped)
        }
        (state.automatic-active.toSet()-state.suppressed.toSet()).distinct().forEachIndexed { position,name ->
            history(rows,diary.id,tagIds.getValue(name),"removed","automatic",position.toLong(),mapped,state)
        }

    }

    private fun legacyDiaries(driver:SqlDriver):List<com.dailysatori.shared.db.Diary_record> {
        val fields=strings(driver,"SELECT name FROM pragma_table_info('diary')")
        if(fields.isEmpty()) return emptyList()
        val optional=listOf("tags","mood","images").map { if(it in fields) it else "NULL" }
        val parent=if("parent_diary_id" in fields) "parent_diary_id" else "NULL"
        return driver.executeQuery(null,"SELECT id,content,${optional.joinToString()},created_at,updated_at,$parent FROM diary ORDER BY id",{ c ->
            val result=mutableListOf<com.dailysatori.shared.db.Diary_record>()
            while(c.next().value) result+=com.dailysatori.shared.db.Diary_record(c.getLong(0)!!,c.getString(1)!!,
                c.getString(2),c.getString(3),c.getString(4),c.getLong(5)!!,c.getLong(6)!!,c.getLong(7))
            QueryResult.Value(result)
        },0).value
    }

    private fun legacyRevisions(driver:SqlDriver,diaries:List<com.dailysatori.shared.db.Diary_record>):Map<Long,Long> {
        if(strings(driver,"SELECT name FROM sqlite_master WHERE name='diary_thread_revision'").isEmpty())
            // The historical thread migration backfills each root with exactly revision 1.
            return diaries.filter { it.parent_diary_id==null }.associate { it.id to 1L }
        return driver.executeQuery(null,"SELECT root_diary_id,revision FROM diary_thread_revision",{ c ->
            val result=mutableMapOf<Long,Long>()
            while(c.next().value) result[c.getLong(0)!!]=c.getLong(1)!!
            QueryResult.Value(result)
        },0).value
    }

    private fun history(rows:Map<String,MutableList<List<Any?>>>,id:Long,tag:Long,decision:String,
        origin:String,position:Long,revision:Long?,state:DiaryTagState) {
        val history=rows.getValue("diary_tag_history")
        history+=listOf(history.size.toLong()+1,id,tag,"schema41:$decision:$id",decision,origin,position,
            revision,state.contentFingerprint.takeIf { it.isNotEmpty() },state.revision,null)
    }

    fun flattenAliases(aliases:Map<String,String>):Map<String,String> = aliases.mapValues { (alias,target) ->
        val seen=mutableSetOf(alias)
        var name=target
        while(name in aliases) {
            check(seen.add(name)) { "日记标签别名循环" }
            name=aliases.getValue(name)
        }
        check(seen.add(name)) { "日记标签别名循环" }
        name
    }

    private fun validateUndo(raw:String) {
        val obj=Json.parseToJsonElement(raw).jsonObject
        check(obj.keys==setOf("before","after","entries")) { "日记标签撤销载荷无法识别" }
        Json.decodeFromString<DiaryTagVocabulary>(obj.getValue("before").toString())
        Json.decodeFromString<DiaryTagVocabulary>(obj.getValue("after").toString())
        obj.getValue("entries").let { Json.decodeFromString<List<LegacyMergeEntry>>(it.toString()) }
    }

    private fun encoded(rows:List<List<Any?>>):List<String> = rows.map { row -> row.joinToString("!") { value ->
        val type=if(value==null) "null" else if(value is Long) "integer" else "text"
        type+":"+value?.toString().orEmpty().encodeToByteArray().joinToString("") {
            (it.toInt() and 255).toString(16).padStart(2,'0').uppercase()
        }
    } }.sorted()

    private fun actualRows(driver:SqlDriver,table:String):List<String> {
        val fields=columns.getValue(table).joinToString { "typeof($it),hex(CAST($it AS BLOB))" }
        return driver.executeQuery(null,"SELECT $fields FROM $table", { c ->
            val rows=mutableListOf<String>()
            while(c.next().value) rows+=(columns.getValue(table).indices).joinToString("!") {
                c.getString(it*2)+":"+c.getString(it*2+1)
            }
            QueryResult.Value(rows.sorted())
        },0).value
    }

    private fun strings(driver:SqlDriver,sql:String,arg:Any?=null):List<String> =
        driver.executeQuery(null,sql,{ c ->
            val found=mutableListOf<String>()
            while(c.next().value) found+=c.getString(0)!!
            QueryResult.Value(found)
        },if(arg==null) 0 else 1) {
            when(arg) { is Long -> bindLong(0,arg); is String -> bindString(0,arg) }
        }.value
}

@kotlinx.serialization.Serializable
private data class LegacyMergeEntry(val id:Long,val beforeTags:String?,val beforeState:DiaryTagState,
    val afterTags:String?,val afterState:DiaryTagState)

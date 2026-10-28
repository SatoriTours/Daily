package com.dailysatori.service.migration

import app.cash.sqldelight.db.SqlDriver

/** Freeze the retired physical diary fields and content/image triggers of schema44. */
internal object DiaryLegacyFieldsFixture {
    fun install(d:SqlDriver) {
        val fields=DatabaseSchemaInventory.strings(d,"SELECT name FROM pragma_table_info('diary')")
        if("tags" in fields && "images" in fields) return
        d.execute(null,"DROP VIEW IF EXISTS diary_record",0)
        for(name in listOf("tags","images")) if(name !in fields)
            d.execute(null,"ALTER TABLE diary ADD COLUMN $name TEXT",0)
        triggers.forEach { (name,sql) ->
            d.execute(null,"DROP TRIGGER IF EXISTS $name",0)
            d.execute(null,sql,0)
        }
    }
    fun readDiary(d:SqlDriver,id:Long):com.dailysatori.shared.db.Diary_record {
        val db=com.dailysatori.shared.db.DailySatoriDatabase(d)
        if("images" !in DatabaseSchemaInventory.strings(d,"SELECT name FROM pragma_table_info('diary')"))
            return com.dailysatori.data.repository.DiaryImages(db).project(db.dailySatoriQueries.selectDiaryById(id).executeAsOne())
        return d.executeQuery(null,"SELECT id,content,tags,mood,images,created_at,updated_at,parent_diary_id FROM diary WHERE id=?", { c ->
            check(c.next().value)
            app.cash.sqldelight.db.QueryResult.Value(com.dailysatori.shared.db.Diary_record(c.getLong(0)!!,c.getString(1)!!,c.getString(2),
                c.getString(3),c.getString(4),c.getLong(5)!!,c.getLong(6)!!,c.getLong(7)))
        },1) { bindLong(0,id) }.value
    }
    fun readProjection(d:SqlDriver) {
        d.execute(null,SchemaRetirementMigration.recordView.replace("CREATE VIEW","CREATE VIEW IF NOT EXISTS"),0)
    }
    private val triggers=mapOf(
        "diary_thread_revision_after_content_update" to """CREATE TRIGGER diary_thread_revision_after_content_update
            AFTER UPDATE OF content,images ON diary WHEN new.content IS NOT old.content OR new.images IS NOT old.images BEGIN
            UPDATE diary_thread_revision SET revision=revision+1,updated_at=MAX(new.updated_at,updated_at+1)
            WHERE root_diary_id=COALESCE(new.parent_diary_id,new.id); END""",
        "diary_thread_root_updated_after_content_update" to """CREATE TRIGGER diary_thread_root_updated_after_content_update
            AFTER UPDATE OF content,images ON diary WHEN new.parent_diary_id IS NULL AND (new.content IS NOT old.content OR new.images IS NOT old.images) BEGIN
            UPDATE diary SET updated_at=MAX(new.updated_at,old.updated_at+1) WHERE id=new.id; END""",
        "diary_thread_child_updated_after_content_update" to """CREATE TRIGGER diary_thread_child_updated_after_content_update
            AFTER UPDATE OF content,images ON diary WHEN new.parent_diary_id IS NOT NULL AND (new.content IS NOT old.content OR new.images IS NOT old.images) BEGIN
            UPDATE diary SET updated_at=MAX(new.updated_at,updated_at+1) WHERE id=new.parent_diary_id; END""",
    )
}

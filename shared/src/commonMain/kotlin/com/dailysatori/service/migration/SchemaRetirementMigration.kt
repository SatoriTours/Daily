package com.dailysatori.service.migration

import app.cash.sqldelight.db.SqlDriver

/** Retire only the two representations proven and replaced by schemas40/41. */
internal object SchemaRetirementMigration {
    private val triggerNames=listOf("diary_thread_revision_after_content_update",
        "diary_thread_root_updated_after_content_update","diary_thread_child_updated_after_content_update")
    const val recordView="""CREATE VIEW diary_record AS
        SELECT d.id,d.content,p.tags,d.mood,CAST(NULL AS TEXT) AS images,d.created_at,d.updated_at,d.parent_diary_id
        FROM diary d LEFT JOIN diary_active_tags p ON p.diary_id=d.id"""
    fun migrate(d:SqlDriver) {
        val fields=DatabaseSchemaInventory.strings(d,"SELECT name FROM pragma_table_info('diary')")
        val retired=listOf("tags","images")
        check(retired.all { it in fields } || retired.none { it in fields }) { "日记退役字段结构冲突" }
        if(retired.none { it in fields }) {
            check(DatabaseSchemaInventory.strings(d,"SELECT name FROM pragma_table_info('diary_record')").size==8) { "日记投影结构不完整" }
            return
        }
        d.execute(null,"DROP VIEW IF EXISTS diary_record",0)
        triggerNames.forEach { d.execute(null,"DROP TRIGGER $it",0) }
        retired.forEach { d.execute(null,"ALTER TABLE diary DROP COLUMN $it",0) }
        d.execute(null,recordView,0)
        d.execute(null,"""CREATE TRIGGER diary_thread_revision_after_content_update AFTER UPDATE OF content ON diary
            WHEN new.content IS NOT old.content BEGIN UPDATE diary_thread_revision SET revision=revision+1,
            updated_at=MAX(new.updated_at,updated_at+1) WHERE root_diary_id=COALESCE(new.parent_diary_id,new.id); END""",0)
        d.execute(null,"""CREATE TRIGGER diary_thread_root_updated_after_content_update AFTER UPDATE OF content ON diary
            WHEN new.parent_diary_id IS NULL AND new.content IS NOT old.content BEGIN UPDATE diary
            SET updated_at=MAX(new.updated_at,old.updated_at+1) WHERE id=new.id; END""",0)
        d.execute(null,"""CREATE TRIGGER diary_thread_child_updated_after_content_update AFTER UPDATE OF content ON diary
            WHEN new.parent_diary_id IS NOT NULL AND new.content IS NOT old.content BEGIN UPDATE diary
            SET updated_at=MAX(new.updated_at,updated_at+1) WHERE id=new.parent_diary_id; END""",0)
    }
}

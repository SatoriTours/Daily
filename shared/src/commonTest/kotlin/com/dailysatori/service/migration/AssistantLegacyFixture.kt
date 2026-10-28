package com.dailysatori.service.migration

import app.cash.sqldelight.db.SqlDriver

/** Physical schema43 contract, independent of the generated schema44 API. */
internal object AssistantLegacyFixture {
    fun install(d:SqlDriver) {
        DiaryLegacyFieldsFixture.install(d)
        listOf("assistant_turn_links_insert","assistant_turn_links_update","assistant_message_links_insert","assistant_message_links_update","assistant_task_history_restrict")
            .forEach { d.execute(null,"DROP TRIGGER IF EXISTS $it",0) }
        listOf("assistant_reference","assistant_action","assistant_turn","chat_conversation").forEach { d.execute(null,"DROP TABLE IF EXISTS $it",0) }
        tables.forEach { d.execute(null,it,0) }
        if("content_origin" in DatabaseSchemaInventory.strings(d,"SELECT name FROM pragma_table_info('memory_entry')"))
            d.execute(null,"ALTER TABLE memory_entry DROP COLUMN content_origin",0)
    }
    private val tables=listOf(
        """CREATE TABLE chat_conversation(id INTEGER PRIMARY KEY AUTOINCREMENT,session_id TEXT NOT NULL,role TEXT NOT NULL,
            content TEXT NOT NULL,search_results TEXT,steps TEXT,created_at INTEGER NOT NULL)""",
        """CREATE INDEX idx_chat_conversation_session_created ON chat_conversation(session_id,created_at DESC)""",
        """CREATE TABLE assistant_turn(id INTEGER PRIMARY KEY NOT NULL,session_id TEXT NOT NULL,client_message_id TEXT NOT NULL,
            task_id INTEGER NOT NULL UNIQUE,received_at INTEGER NOT NULL,time_zone_id TEXT NOT NULL,response_message_id INTEGER,
            requires_resume INTEGER NOT NULL DEFAULT 0,action_plan_json TEXT NOT NULL DEFAULT '',context_json TEXT NOT NULL DEFAULT '',
            UNIQUE(session_id,client_message_id))""",
        """CREATE TABLE assistant_action(id TEXT PRIMARY KEY NOT NULL,turn_id INTEGER NOT NULL,kind TEXT NOT NULL,parameters_json TEXT NOT NULL,
            fingerprint TEXT NOT NULL DEFAULT '',status TEXT NOT NULL,target_id TEXT,task_id INTEGER,error_code TEXT NOT NULL DEFAULT '')""",
        "CREATE INDEX idx_assistant_action_turn ON assistant_action(turn_id)",
        """CREATE TABLE assistant_reference(turn_id INTEGER NOT NULL,source_type TEXT NOT NULL,source_id TEXT NOT NULL,
            title TEXT NOT NULL DEFAULT '',PRIMARY KEY(turn_id,source_type,source_id))""",
    )
    fun seed(d:SqlDriver) {
        install(d)
        d.execute(null,"INSERT INTO chat_conversation VALUES(71,'missing-session','user',?,?,?,3)",3) {
            bindString(0,"原话\u0000tail");bindString(1,"完整检索");bindString(2,"完整步骤\u0000tail")
        }
        d.execute(null,"INSERT INTO chat_conversation VALUES(72,'missing-session','user','原话',NULL,NULL,3)",0)
        d.execute(null,"UPDATE sqlite_sequence SET seq=99 WHERE name='chat_conversation'",0)
        d.execute(null,"INSERT INTO memory_entry(type,source_type,source_id,title,content,evidence,source_version,created_at,updated_at) VALUES('content','diary',999,'人工历史',?,?,?,1,2)",3) {
            bindString(0,"保留正文\u0000tail");bindString(1,"未知证据");bindString(2,"历史指纹\u0000tail")
        }
    }
}

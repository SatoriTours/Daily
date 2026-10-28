package com.dailysatori.service.migration

internal object AssistantRelationsSchema {
    val tables=linkedMapOf(
        "chat_conversation" to """CREATE TABLE chat_conversation(id INTEGER PRIMARY KEY AUTOINCREMENT,
            session_id TEXT NOT NULL REFERENCES assistant_session(id) ON DELETE RESTRICT,role TEXT NOT NULL,content TEXT NOT NULL,
            search_results TEXT,steps TEXT,created_at INTEGER NOT NULL)""",
        "assistant_turn" to """CREATE TABLE assistant_turn(id INTEGER PRIMARY KEY NOT NULL,
            session_id TEXT NOT NULL REFERENCES assistant_session(id) ON DELETE RESTRICT,client_message_id TEXT NOT NULL,
            task_id INTEGER NOT NULL UNIQUE REFERENCES async_task(id) ON DELETE RESTRICT,received_at INTEGER NOT NULL,time_zone_id TEXT NOT NULL,
            response_message_id INTEGER,requires_resume INTEGER NOT NULL DEFAULT 0,action_plan_json TEXT NOT NULL DEFAULT '',
            context_json TEXT NOT NULL DEFAULT '',UNIQUE(session_id,client_message_id))""",
        "assistant_action" to """CREATE TABLE assistant_action(id TEXT PRIMARY KEY NOT NULL,
            turn_id INTEGER NOT NULL REFERENCES assistant_turn(id) ON DELETE RESTRICT,kind TEXT NOT NULL,parameters_json TEXT NOT NULL,
            fingerprint TEXT NOT NULL DEFAULT '',status TEXT NOT NULL,target_id TEXT,
            task_id INTEGER REFERENCES async_task(id) ON DELETE RESTRICT,error_code TEXT NOT NULL DEFAULT '')""",
        "assistant_reference" to """CREATE TABLE assistant_reference(turn_id INTEGER NOT NULL REFERENCES assistant_turn(id) ON DELETE RESTRICT,
            source_type TEXT NOT NULL,source_id TEXT NOT NULL,title TEXT NOT NULL DEFAULT '',PRIMARY KEY(turn_id,source_type,source_id))"""
    )
    val indexes=listOf(
        "CREATE INDEX IF NOT EXISTS idx_chat_conversation_session_created ON chat_conversation(session_id,created_at DESC,id DESC)",
        "CREATE INDEX IF NOT EXISTS idx_assistant_action_turn ON assistant_action(turn_id)",
    )
    val triggers=listOf("INSERT","UPDATE").mapIndexed { i,op ->
        """CREATE TRIGGER assistant_turn_links_${if(i==0) "insert" else "update"} BEFORE $op ON assistant_turn BEGIN
            SELECT RAISE(ABORT,'Invalid assistant input relation') WHERE
                EXISTS(SELECT 1 FROM chat_conversation c WHERE c.id=new.id AND (c.role!='user' OR c.session_id!=new.session_id))
                OR (NOT EXISTS(SELECT 1 FROM chat_conversation c WHERE c.id=new.id)
                    AND NOT EXISTS(SELECT 1 FROM assistant_memory_exclusion x WHERE x.source_type='chat' AND x.source_id=CAST(new.id AS TEXT)));
            SELECT RAISE(ABORT,'Invalid assistant response relation') WHERE new.response_message_id IS NOT NULL AND (
                EXISTS(SELECT 1 FROM chat_conversation c WHERE c.id=new.response_message_id AND (c.role!='assistant' OR c.session_id!=new.session_id))
                OR (NOT EXISTS(SELECT 1 FROM chat_conversation c WHERE c.id=new.response_message_id)
                    AND NOT EXISTS(SELECT 1 FROM assistant_memory_exclusion x WHERE x.source_type='chat' AND x.source_id=CAST(new.response_message_id AS TEXT))));
            END"""
    }+listOf(
        """CREATE TRIGGER assistant_message_links_insert AFTER INSERT ON chat_conversation BEGIN
            SELECT RAISE(ABORT,'Invalid assistant message relation') WHERE
                EXISTS(SELECT 1 FROM assistant_turn t WHERE (t.id=new.id AND (new.role!='user' OR new.session_id!=t.session_id))
                    OR(t.response_message_id=new.id AND (new.role!='assistant' OR new.session_id!=t.session_id))); END""",
        """CREATE TRIGGER assistant_message_links_update BEFORE UPDATE OF role,session_id ON chat_conversation BEGIN
            SELECT RAISE(ABORT,'Invalid assistant message relation') WHERE
                EXISTS(SELECT 1 FROM assistant_turn t WHERE (t.id=old.id AND (new.role!='user' OR new.session_id!=t.session_id))
                    OR(t.response_message_id=old.id AND (new.role!='assistant' OR new.session_id!=t.session_id))); END""",
        """CREATE TRIGGER assistant_task_history_restrict BEFORE DELETE ON async_task
            WHEN EXISTS(SELECT 1 FROM assistant_turn WHERE task_id=old.id) OR EXISTS(SELECT 1 FROM assistant_action WHERE task_id=old.id)
            BEGIN SELECT RAISE(ABORT,'Referenced assistant task'); END"""
    )
}

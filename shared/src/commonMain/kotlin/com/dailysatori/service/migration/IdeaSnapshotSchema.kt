package com.dailysatori.service.migration

internal object IdeaSnapshotSchema {
    const val snapshot="""CREATE TABLE idea_source_snapshot(id TEXT PRIMARY KEY NOT NULL,format_version INTEGER NOT NULL,
        payload_json TEXT NOT NULL,sha256 TEXT NOT NULL,created_at INTEGER NOT NULL)"""
    const val references="""CREATE TABLE idea_event_snapshot(event_id TEXT NOT NULL REFERENCES idea_topic_event(id) ON DELETE RESTRICT,
        role TEXT NOT NULL CHECK(role IN ('captured_source','merged_duplicate')),ordinal INTEGER NOT NULL CHECK(ordinal>=0),
        snapshot_id TEXT NOT NULL REFERENCES idea_source_snapshot(id) ON DELETE RESTRICT,PRIMARY KEY(event_id,role,ordinal))"""
    val tables=linkedMapOf(
        "idea_topic" to """CREATE TABLE idea_topic(id TEXT PRIMARY KEY NOT NULL,title TEXT NOT NULL,description TEXT NOT NULL DEFAULT '',
            provenance_summary TEXT NOT NULL DEFAULT '',conclusions TEXT NOT NULL DEFAULT '',next_action TEXT NOT NULL DEFAULT '',
            status TEXT NOT NULL,merged_into_topic_id TEXT REFERENCES idea_topic(id) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED,
            context_revision INTEGER NOT NULL DEFAULT 0,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)""",
        "idea_topic_source" to """CREATE TABLE idea_topic_source(id TEXT PRIMARY KEY NOT NULL,
            topic_id TEXT NOT NULL REFERENCES idea_topic(id) ON DELETE RESTRICT,original_topic_id TEXT NOT NULL,
            source_type TEXT NOT NULL,source_record_id TEXT NOT NULL,snapshot_id TEXT NOT NULL REFERENCES idea_source_snapshot(id) ON DELETE RESTRICT,
            captured_at INTEGER NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)""",
        "idea_topic_event" to """CREATE TABLE idea_topic_event(id TEXT PRIMARY KEY NOT NULL,
            topic_id TEXT NOT NULL REFERENCES idea_topic(id) ON DELETE RESTRICT,original_topic_id TEXT NOT NULL,kind TEXT NOT NULL,
            payload_json TEXT NOT NULL DEFAULT '{}',payload_version INTEGER NOT NULL DEFAULT 1 CHECK(payload_version IN (1,2)),created_at INTEGER NOT NULL)""",
        "idea_topic_session" to """CREATE TABLE idea_topic_session(id TEXT PRIMARY KEY NOT NULL,
            topic_id TEXT NOT NULL REFERENCES idea_topic(id) ON DELETE RESTRICT,original_topic_id TEXT NOT NULL,title TEXT NOT NULL,
            summary TEXT NOT NULL DEFAULT '',summary_through_message_id TEXT,summary_covered_message_ids TEXT NOT NULL DEFAULT '[]',
            summary_status TEXT NOT NULL DEFAULT 'none',created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)""",
        "idea_topic_message" to """CREATE TABLE idea_topic_message(id TEXT PRIMARY KEY NOT NULL,
            session_id TEXT NOT NULL REFERENCES idea_topic_session(id) ON DELETE RESTRICT,role TEXT NOT NULL,content TEXT NOT NULL,
            status TEXT NOT NULL,error TEXT,created_at INTEGER NOT NULL)""",
    )
    val indices=listOf(
        "CREATE INDEX IF NOT EXISTS idx_idea_topic_merged ON idea_topic(merged_into_topic_id)",
        "CREATE INDEX IF NOT EXISTS idx_idea_topic_updated ON idea_topic(updated_at)",
        "CREATE UNIQUE INDEX IF NOT EXISTS idx_idea_topic_source_key ON idea_topic_source(topic_id,source_type,source_record_id)",
        "CREATE INDEX IF NOT EXISTS idx_idea_topic_source_lookup ON idea_topic_source(source_type,source_record_id)",
        "CREATE INDEX IF NOT EXISTS idx_idea_topic_source_topic ON idea_topic_source(topic_id,captured_at,id)",
        "CREATE INDEX IF NOT EXISTS idx_idea_topic_event_topic ON idea_topic_event(topic_id,created_at,id)",
        "CREATE INDEX IF NOT EXISTS idx_idea_topic_event_kind ON idea_topic_event(topic_id,kind,created_at,id)",
        "CREATE INDEX IF NOT EXISTS idx_idea_topic_session_topic ON idea_topic_session(topic_id,updated_at,id)",
        "CREATE INDEX IF NOT EXISTS idx_idea_topic_message_session ON idea_topic_message(session_id,created_at,id)",
        "CREATE INDEX IF NOT EXISTS idx_idea_topic_message_status ON idea_topic_message(status,created_at,id)",
        "CREATE INDEX IF NOT EXISTS idx_idea_snapshot_hash ON idea_source_snapshot(format_version,sha256)",
        "CREATE INDEX IF NOT EXISTS idx_idea_event_snapshot_id ON idea_event_snapshot(snapshot_id)",
        "CREATE INDEX IF NOT EXISTS idx_idea_source_snapshot_id ON idea_topic_source(snapshot_id)",
    )
    const val view="""CREATE VIEW idea_source_record AS SELECT s.*,p.payload_json AS snapshot_json
        FROM idea_topic_source s JOIN idea_source_snapshot p ON p.id=s.snapshot_id"""
    val triggers=listOf(
        """CREATE TRIGGER idea_snapshot_immutable BEFORE UPDATE ON idea_source_snapshot
            BEGIN SELECT RAISE(ABORT,'Immutable idea snapshot'); END""",
        """CREATE TRIGGER idea_snapshot_referenced BEFORE DELETE ON idea_source_snapshot
            WHEN EXISTS(SELECT 1 FROM idea_topic_source WHERE snapshot_id=old.id)
                OR EXISTS(SELECT 1 FROM idea_event_snapshot WHERE snapshot_id=old.id)
            BEGIN SELECT RAISE(ABORT,'Referenced idea snapshot'); END""",
    )+listOf("INSERT","UPDATE OF merged_into_topic_id").mapIndexed { i,op ->
        """CREATE TRIGGER idea_topic_merge_cycle_${if(i==0) "insert" else "update"} BEFORE $op ON idea_topic
            WHEN new.merged_into_topic_id IS NOT NULL BEGIN
            SELECT RAISE(ABORT,'Idea merge cycle') WHERE EXISTS(
                WITH RECURSIVE chain(id) AS (SELECT new.merged_into_topic_id UNION
                    SELECT t.merged_into_topic_id FROM idea_topic t JOIN chain c ON c.id=t.id WHERE t.merged_into_topic_id IS NOT NULL)
                SELECT 1 FROM chain WHERE id=new.id); END"""
    }
}

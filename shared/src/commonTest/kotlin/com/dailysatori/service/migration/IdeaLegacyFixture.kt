package com.dailysatori.service.migration

import app.cash.sqldelight.db.SqlDriver

/** Frozen physical schema42; tests must not synthesize legacy rows through future generated APIs. */
internal object IdeaLegacyFixture {
    fun install(driver:SqlDriver) {
        DiaryLegacyFieldsFixture.install(driver)
        driver.execute(null,"DROP VIEW IF EXISTS idea_source_record",0)
        listOf("idea_topic_merge_cycle_insert","idea_topic_merge_cycle_update","idea_snapshot_referenced").forEach {
            driver.execute(null,"DROP TRIGGER IF EXISTS $it",0)
        }
        listOf("idea_event_snapshot","idea_topic_message","idea_topic_session","idea_topic_event","idea_topic_source","idea_topic","idea_source_snapshot").forEach {
            driver.execute(null,"DROP TABLE IF EXISTS $it",0)
        }
        ddl.forEach { driver.execute(null,it,0) }
    }
    val raw="""{"key":{"type":"diary","recordId":"gone"},"originalTitle":"标题","originalContent":"body\\u0000tail","unknown":{"keep":true}}"""
    fun seed(driver:SqlDriver) {
        install(driver)
        driver.execute(null,"INSERT INTO idea_topic(id,title,status,context_revision,created_at,updated_at) VALUES('legacy-idea','原主题','advancing',7,1,2)",0)
        driver.execute(null,"INSERT INTO idea_topic_source VALUES('legacy-source','legacy-idea','gone-owner','diary','gone',?,3,4,5)",1) { bindString(0,raw) }
        driver.execute(null,"INSERT INTO idea_topic_event VALUES('legacy-captured','legacy-idea','gone-owner','captured',?,6)",1) {
            bindString(0,"""{"snapshot":$raw,"initialContent":{"title":"原始历史"},"future":"retained"}""")
        }
        driver.execute(null,"INSERT INTO idea_topic_event VALUES('legacy-opaque','legacy-idea','gone-owner','future',?,7)",1) { bindString(0,"unknown\u0000tail") }
        driver.execute(null,"INSERT INTO idea_topic_session(id,topic_id,original_topic_id,title,created_at,updated_at) VALUES('legacy-session','legacy-idea','gone-owner','讨论',8,9)",0)
        driver.execute(null,"INSERT INTO idea_topic_message VALUES('legacy-message','legacy-session','assistant',?,'complete',NULL,9)",1) { bindString(0,"完整回复\u0000tail") }
    }
    private val ddl=listOf(
        """CREATE TABLE idea_topic(id TEXT PRIMARY KEY NOT NULL,title TEXT NOT NULL,description TEXT NOT NULL DEFAULT '',
            provenance_summary TEXT NOT NULL DEFAULT '',conclusions TEXT NOT NULL DEFAULT '',next_action TEXT NOT NULL DEFAULT '',
            status TEXT NOT NULL,merged_into_topic_id TEXT,context_revision INTEGER NOT NULL DEFAULT 0,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)""",
        "CREATE INDEX idx_idea_topic_merged ON idea_topic(merged_into_topic_id)",
        "CREATE INDEX idx_idea_topic_updated ON idea_topic(updated_at)",
        """CREATE TABLE idea_topic_source(id TEXT PRIMARY KEY NOT NULL,topic_id TEXT NOT NULL,original_topic_id TEXT NOT NULL,
            source_type TEXT NOT NULL,source_record_id TEXT NOT NULL,snapshot_json TEXT NOT NULL,captured_at INTEGER NOT NULL,
            created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)""",
        "CREATE UNIQUE INDEX idx_idea_topic_source_key ON idea_topic_source(topic_id,source_type,source_record_id)",
        "CREATE INDEX idx_idea_topic_source_lookup ON idea_topic_source(source_type,source_record_id)",
        "CREATE INDEX idx_idea_topic_source_topic ON idea_topic_source(topic_id,captured_at,id)",
        """CREATE TABLE idea_topic_event(id TEXT PRIMARY KEY NOT NULL,topic_id TEXT NOT NULL,original_topic_id TEXT NOT NULL,
            kind TEXT NOT NULL,payload_json TEXT NOT NULL DEFAULT '{}',created_at INTEGER NOT NULL)""",
        "CREATE INDEX idx_idea_topic_event_topic ON idea_topic_event(topic_id,created_at,id)",
        "CREATE INDEX idx_idea_topic_event_kind ON idea_topic_event(topic_id,kind,created_at,id)",
        """CREATE TABLE idea_topic_session(id TEXT PRIMARY KEY NOT NULL,topic_id TEXT NOT NULL,original_topic_id TEXT NOT NULL,
            title TEXT NOT NULL,summary TEXT NOT NULL DEFAULT '',summary_through_message_id TEXT,summary_covered_message_ids TEXT NOT NULL DEFAULT '[]',
            summary_status TEXT NOT NULL DEFAULT 'none',created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)""",
        "CREATE INDEX idx_idea_topic_session_topic ON idea_topic_session(topic_id,updated_at,id)",
        """CREATE TABLE idea_topic_message(id TEXT PRIMARY KEY NOT NULL,session_id TEXT NOT NULL,role TEXT NOT NULL,
            content TEXT NOT NULL,status TEXT NOT NULL,error TEXT,created_at INTEGER NOT NULL)""",
        "CREATE INDEX idx_idea_topic_message_session ON idea_topic_message(session_id,created_at,id)",
        "CREATE INDEX idx_idea_topic_message_status ON idea_topic_message(status,created_at,id)",
    )
}

package com.dailysatori.service.migration

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.*

class IdeaSnapshotMigrationTest {
    private val raw="""{ "key":{"type":"diary","recordId":"deleted-1"},"originalTitle":"旧标题","originalContent":"line\\u0000tail","unknown":{"future":[1,2]} }"""
    private val different=raw.replace("旧标题","另一份历史")
    @Test fun physical42PreservesUnknownPayloadsSharedReferencesOrderingAndOtherHistory()=fixture { d ->
        seed(d)
        IdeaSnapshotMigration.migrate(d)
        assertEquals(listOf("2"),strings(d,"SELECT COUNT(*) FROM idea_source_snapshot"))
        assertEquals(listOf(raw),strings(d,"SELECT payload_json FROM idea_source_snapshot WHERE id=(SELECT snapshot_id FROM idea_topic_source WHERE id='source')"))
        assertEquals(listOf("2","2","1"),strings(d,"SELECT payload_version FROM idea_topic_event ORDER BY id"))
        assertEquals(listOf("0","0","1"),strings(d,"SELECT ordinal FROM idea_event_snapshot ORDER BY event_id,role,ordinal"))
        assertEquals(listOf("future opaque\u0000tail"),strings(d,"SELECT payload_json FROM idea_topic_event WHERE id='unknown'"))
        assertEquals(listOf("full reply\u0000tail"),strings(d,"SELECT content FROM idea_topic_message"))
        assertEquals(listOf("deleted-owner"),strings(d,"SELECT original_topic_id FROM idea_topic_source"))
        assertEquals(listOf("1"),strings(d,"SELECT context_revision FROM idea_topic"))
        IdeaSnapshotMigration.migrate(d)
        assertEquals(listOf("2"),strings(d,"SELECT COUNT(*) FROM idea_source_snapshot"))
        assertFails { d.execute(null,"UPDATE idea_source_snapshot SET payload_json='changed'",0) }
        assertFails { d.execute(null,"DELETE FROM idea_source_snapshot",0) }
    }
    @Test fun damagedKnownPayloadAndMergeCycleStopWithoutChangingOldRepresentations()=fixture { d ->
        seed(d)
        d.execute(null,"UPDATE idea_topic_event SET payload_json='broken' WHERE id='captured'",0)
        assertFails { IdeaSnapshotMigration.migrate(d) }
        assertEquals(listOf(raw),strings(d,"SELECT snapshot_json FROM idea_topic_source"))
        d.execute(null,"DELETE FROM idea_topic_event WHERE id='captured'",0)
        d.execute(null,"UPDATE idea_topic SET merged_into_topic_id=id",0)
        assertFails { IdeaSnapshotMigration.migrate(d) }
        assertEquals(listOf(raw),strings(d,"SELECT snapshot_json FROM idea_topic_source"))
    }
    private fun seed(d:JdbcSqliteDriver) {
        d.execute(null,"INSERT INTO idea_topic(id,title,status,context_revision,created_at,updated_at) VALUES('topic','保留','researching',1,1,2)",0)
        d.execute(null,"INSERT INTO idea_topic_source VALUES('source','topic','deleted-owner','diary','deleted-1',?,3,4,5)",1) { bindString(0,raw) }
        d.execute(null,"INSERT INTO idea_topic_event VALUES('captured','topic','deleted-owner','captured',?,6)",1) {
            bindString(0,"""{ "snapshot":$raw, "initialContent":{"title":"保留历史"},"future":"keep" }""")
        }
        d.execute(null,"INSERT INTO idea_topic_event VALUES('merged','topic','deleted-owner','merged',?,7)",1) {
            bindString(0,"""{"fromContent":{"description":"before"},"intoContent":{"description":"after"},"duplicateSourceSnapshots":[$raw,$different],"unknown":true}""")
        }
        d.execute(null,"INSERT INTO idea_topic_event VALUES('unknown','topic','deleted-owner','future',?,8)",1) { bindString(0,"future opaque\u0000tail") }
        d.execute(null,"INSERT INTO idea_topic_session(id,topic_id,original_topic_id,title,created_at,updated_at) VALUES('session','topic','deleted-owner','discussion',1,2)",0)
        d.execute(null,"INSERT INTO idea_topic_message VALUES('message','session','assistant',?,'complete',NULL,9)",1) { bindString(0,"full reply\u0000tail") }
    }
    private fun strings(d:JdbcSqliteDriver,sql:String)=DatabaseSchemaInventory.strings(d,sql)
    private fun fixture(block:(JdbcSqliteDriver)->Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use {
            DailySatoriDatabase.Schema.create(it)
            IdeaLegacyFixture.install(it)
            block(it)
        }
    }
}

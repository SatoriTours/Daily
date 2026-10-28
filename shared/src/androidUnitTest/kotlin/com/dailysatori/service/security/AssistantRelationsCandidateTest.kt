package com.dailysatori.service.security

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.service.migration.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.*

class AssistantRelationsCandidateTest {
    @Test fun onlyProvenMissingSessionAndLegacyOriginAreAllowed()=fixture { d ->
        val expected=MigrationContentSnapshot.capture(d)
        AssistantRelationsMigration.migrate(d)
        expected.requirePreserved(d,migratedAssistant=true)
        d.execute(null,"UPDATE memory_entry SET content_origin='automatic'",0)
        assertFails { expected.requirePreserved(d,migratedAssistant=true) }
        d.execute(null,"UPDATE memory_entry SET content_origin='legacy_unknown'",0)
        d.execute(null,"UPDATE assistant_session SET summary='invented' WHERE id='missing-session'",0)
        assertFails { expected.requirePreserved(d,migratedAssistant=true) }
    }
    @Test fun rejectsUnrelatedSessionsAndLateSourceWrites()=fixture { d ->
        val expected=MigrationContentSnapshot.capture(d)
        d.execute(null,"UPDATE chat_conversation SET content='late' WHERE id=71",0)
        assertFails { expected.requirePreserved(d) }
        d.execute(null,"UPDATE chat_conversation SET content=? WHERE id=71",1) { bindString(0,"原话\u0000tail") }
        AssistantRelationsMigration.migrate(d)
        expected.requirePreserved(d,migratedAssistant=true)
        d.execute(null,"INSERT INTO assistant_session(id,created_at) VALUES('unrelated',8)",0)
        assertFails { expected.requirePreserved(d,migratedAssistant=true) }
    }
    private fun fixture(block:(JdbcSqliteDriver)->Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { d ->
            DailySatoriDatabase.Schema.create(d);AssistantLegacyFixture.seed(d);block(d)
        }
    }
}

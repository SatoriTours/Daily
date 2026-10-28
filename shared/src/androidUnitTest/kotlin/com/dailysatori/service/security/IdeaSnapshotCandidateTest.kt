package com.dailysatori.service.security

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.service.migration.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.*

class IdeaSnapshotCandidateTest {
    @Test fun candidateAllowsOnlyExactSourceAndEventConversion()=fixture { d ->
        val original=MigrationContentSnapshot.capture(d)
        IdeaSnapshotMigration.migrate(d)
        original.requirePreserved(d,migratedIdeas=true)
        d.execute(null,"UPDATE idea_topic_event SET payload_json='changed' WHERE id='legacy-captured'",0)
        assertFails { original.requirePreserved(d,migratedIdeas=true) }
    }
    @Test fun candidateRejectsExtraSnapshotsAndLateOriginalChanges()=fixture { d ->
        val original=MigrationContentSnapshot.capture(d)
        d.execute(null,"UPDATE idea_topic_source SET updated_at=100",0)
        assertFails { original.requirePreserved(d) }
        d.execute(null,"UPDATE idea_topic_source SET updated_at=5",0)
        IdeaSnapshotMigration.migrate(d)
        original.requirePreserved(d,migratedIdeas=true)
        d.execute(null,"INSERT INTO idea_source_snapshot VALUES('extra',1,'{}','hash',1)",0)
        assertFails { original.requirePreserved(d,migratedIdeas=true) }
    }
    private fun fixture(block:(JdbcSqliteDriver)->Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use {
            DailySatoriDatabase.Schema.create(it)
            IdeaLegacyFixture.seed(it)
            block(it)
        }
    }
}

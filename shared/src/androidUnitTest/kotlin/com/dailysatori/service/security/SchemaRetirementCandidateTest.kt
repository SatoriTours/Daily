package com.dailysatori.service.security

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.*
import com.dailysatori.service.migration.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class SchemaRetirementCandidateTest {
    @Test fun removalAllowsOnlyRetiredColumnsNotContentOrAttachmentChanges()=runBlocking<Unit> {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { d ->
            DailySatoriDatabase.Schema.create(d)
            val db=DailySatoriDatabase(d)
            val id=DiaryRepository(db,d).create("完整\u0000正文","标签",images="photo.png")
            DiaryLegacyFieldsFixture.install(d)
            val snapshot=MigrationContentSnapshot.capture(d)
            SchemaRetirementMigration.migrate(d)
            snapshot.requirePreserved(d,migratedRetirement=true)
            d.execute(null,"UPDATE diary_attachment SET transcript='tampered' WHERE diary_id=$id",0)
            assertFails { snapshot.requirePreserved(d,migratedRetirement=true) }
            d.execute(null,"UPDATE diary_attachment SET transcript='' WHERE diary_id=$id",0)
            snapshot.requirePreserved(d,migratedRetirement=true)
            d.execute(null,"UPDATE diary SET content='changed' WHERE id=$id",0)
            assertFails { snapshot.requirePreserved(d,migratedRetirement=true) }
        }
    }
}

package com.dailysatori.service.migration

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.*

class SchemaRetirementMigrationTest {
    @Test fun declared45CannotHideStillPersistedRetiredRepresentations() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { d ->
            DailySatoriDatabase.Schema.create(d)
            DiaryLegacyFieldsFixture.install(d)
            DiaryLegacyFieldsFixture.readProjection(d)
            val settings=SettingRepository(DailySatoriDatabase(d))
            settings.upsert("schema_version","45")
            assertFails { DatabaseMigration(d,settings).runMigrations() }
            assertEquals("45",settings.get("schema_version"))
            assertTrue("images" in DatabaseSchemaInventory.strings(d,"SELECT name FROM pragma_table_info('diary')"))
        }
    }
    @Test fun physical44RetiresOnlyVerifiedColumnsAndKeepsCurrentFactsAndUnknownKeys()=kotlinx.coroutines.runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { d ->
            DailySatoriDatabase.Schema.create(d)
            val db=DailySatoriDatabase(d);val diaries=DiaryRepository(db,d)
            val id=diaries.create("原文\u0000tail","保留标签",images="""["重复.png","重复.png","含,逗号.png"]""")
            val settings=SettingRepository(db)
            settings.upsert("schema_version","44");settings.upsert("diary_tag_state_01","unknown opaque")
            val expected=diaries.getById(id)
            val images=db.dailySatoriQueries.selectAttachmentsForDiary(id).executeAsList()
            val history=DatabaseSchemaInventory.strings(d,"SELECT id||':'||operation_id FROM diary_tag_history ORDER BY id")
            DiaryLegacyFieldsFixture.install(d)
            DatabaseMigration(d,settings).runMigrations()
            val fields=DatabaseSchemaInventory.strings(d,"SELECT name FROM pragma_table_info('diary')")
            assertFalse("tags" in fields);assertFalse("images" in fields)
            assertEquals("45",settings.get("schema_version"))
            assertEquals(expected,diaries.getById(id))
            assertEquals(images,db.dailySatoriQueries.selectAttachmentsForDiary(id).executeAsList())
            assertEquals(history,DatabaseSchemaInventory.strings(d,"SELECT id||':'||operation_id FROM diary_tag_history ORDER BY id"))
            assertEquals("unknown opaque",settings.get("diary_tag_state_01"))
            DatabaseMigration(d,settings).runMigrations()
            assertEquals(expected,diaries.getById(id))
            diaries.update(id,"新正文","保留标签",null,"""["重复.png","含,逗号.png"]""")
            assertEquals("新正文",diaries.getById(id)?.content)
            assertEquals(2,db.dailySatoriQueries.selectAttachmentsForDiary(id).executeAsList().size)
        }
    }
}

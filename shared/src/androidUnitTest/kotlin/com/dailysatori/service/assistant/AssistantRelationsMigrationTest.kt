package com.dailysatori.service.assistant

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.*
import com.dailysatori.service.migration.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.*

class AssistantRelationsMigrationTest {
    @Test fun fillsOnlyMissingSessionIdentityAndPreservesDeletedInputHistoryAndUnknownMemory()=fixture { d,db ->
        val q=db.dailySatoriQueries
        val turn=AssistantTurnRepository(d).accept("owned","client","private input",1,"UTC")
        d.execute(null,"INSERT INTO assistant_action(id,turn_id,kind,parameters_json,status,task_id) VALUES('action',?,'audit','opaque','done',?)",2) {
            bindLong(0,turn.id);bindLong(1,turn.taskId)
        }
        AssistantPrivacyRepository(d).deleteChatMessage(turn.id.toString())
        q.insertChat("legacy","user","same",null,"full steps\u0000tail",3)
        q.insertChat("legacy","user","same",null,null,3)
        d.execute(null,"INSERT INTO memory_entry(type,source_type,source_id,title,content,created_at,updated_at) VALUES('content','diary',99,'old',?,1,2)",1) { bindString(0,"hand edited\u0000tail") }
        AssistantRelationsMigration.migrate(d)
        assertEquals(listOf("3"),s(d,"SELECT created_at FROM assistant_session WHERE id='legacy'"))
        assertEquals(listOf("legacy_unknown"),s(d,"SELECT content_origin FROM memory_entry"))
        assertNull(q.selectChatById(turn.id).executeAsOneOrNull())
        assertEquals(turn.taskId,AssistantTurnRepository(d).get(turn.id)!!.taskId)
        assertEquals(listOf("opaque"),s(d,"SELECT parameters_json FROM assistant_action"))
        assertEquals(listOf("hand edited\u0000tail"),s(d,"SELECT content FROM memory_entry"))
        assertTrue(AssistantPrivacyRepository(d).isExcluded("chat",turn.id.toString()))
        assertEquals(2,q.selectChatBySession("legacy").executeAsList().size)
        AssistantRelationsMigration.migrate(d)
        assertFails { d.execute(null,"DELETE FROM async_task WHERE id=?",1) { bindLong(0,turn.taskId) } }
    }
    @Test fun wrongExistingInputRoleAndDanglingTaskStopBeforeChangingOldRows()=fixture { d,db ->
        val turn=AssistantTurnRepository(d).accept("owned","client","original",1,"UTC")
        d.execute(null,"UPDATE chat_conversation SET role='assistant' WHERE id=?",1) { bindLong(0,turn.id) }
        assertFails { AssistantRelationsMigration.migrate(d) }
        assertFalse("content_origin" in s(d,"SELECT name FROM pragma_table_info('memory_entry')"))
        assertEquals("assistant",db.dailySatoriQueries.selectChatById(turn.id).executeAsOne().role)
        d.execute(null,"UPDATE chat_conversation SET role='user' WHERE id=?",1) { bindLong(0,turn.id) }
        d.execute(null,"UPDATE assistant_turn SET task_id=99999",0)
        assertFails { AssistantRelationsMigration.migrate(d) }
        assertFalse("content_origin" in s(d,"SELECT name FROM pragma_table_info('memory_entry')"))
    }
    @Test fun declared43MissingAuditTableCannotAdvanceVersionByRecreatingEmptyHistory()=fixture { d,db ->
        val settings=SettingRepository(db)
        settings.upsert("schema_version","43")
        d.execute(null,"DROP TABLE assistant_action",0)
        assertFails { DatabaseMigration(d,settings).runMigrations() }
        assertEquals("43",settings.get("schema_version"))
        assertTrue(s(d,"SELECT name FROM sqlite_master WHERE name='assistant_action'").isEmpty())
        assertFalse("content_origin" in s(d,"SELECT name FROM pragma_table_info('memory_entry')"))
    }
    @Test fun restorePreparationRejectsDeclared43MissingAuditTableWithoutChangingSource()=fixture { d,db ->
        val settings=SettingRepository(db)
        settings.upsert("schema_version","43")
        d.execute(null,"DROP TABLE assistant_action",0)
        val cipher=object:com.dailysatori.service.security.SecretValueCipher {
            override fun encrypt(value:String)=value
            override fun decrypt(value:String)=value
            override fun isEncrypted(value:String)=false
        }
        assertFails { com.dailysatori.service.backup.BackupDatabaseData(d).prepareSecrets(cipher,legacyFields=false) }
        assertEquals("43",settings.get("schema_version"))
        assertTrue(s(d,"SELECT name FROM sqlite_master WHERE name='assistant_action'").isEmpty())
        assertFalse("content_origin" in s(d,"SELECT name FROM pragma_table_info('memory_entry')"))
    }
    private fun s(d:JdbcSqliteDriver,sql:String)=DatabaseSchemaInventory.strings(d,sql)
    private fun fixture(block:(JdbcSqliteDriver,DailySatoriDatabase)->Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { d ->
            DailySatoriDatabase.Schema.create(d)
            AssistantLegacyFixture.install(d)
            block(d,DailySatoriDatabase(d))
        }
    }
}

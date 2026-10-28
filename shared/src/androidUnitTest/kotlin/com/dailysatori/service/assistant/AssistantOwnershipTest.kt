package com.dailysatori.service.assistant

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.*

class AssistantOwnershipTest {
    @Test fun sameTimestampAndSameContentMessagesPageByIdentityWithoutLossOrDuplication()=fixture { _,db ->
        val repo=ChatConversationRepository(db)
        repeat(5) { repo.insert("legacy","user","same body",createdAt=10) }
        val newest=repo.getLatestBySession("legacy",2)
        val middle=repo.getBefore("legacy",newest.first().created_at,newest.first().id,2)
        val oldest=repo.getBefore("legacy",middle.first().created_at,middle.first().id,2)
        assertEquals(repo.getBySession("legacy").map { it.id },(oldest+middle+newest).map { it.id })
        assertEquals(5,(oldest+middle+newest).map { it.id }.distinct().size)
    }
    @Test fun backgroundWriteRequiresMatchingSourceAndCannotOverwriteUserOrRaceANewerWrite()=fixture { _,db ->
        val q=db.dailySatoriQueries;val memory=MemoryRepository(db)
        q.insertDiary("source", null, 1, 1)
        val token=assertNotNull(memory.automaticSnapshot("diary",1,"日记","source"))
        assertTrue(memory.saveAutomatic("diary",1,"日记","generated",token))
        assertFalse(memory.saveAutomatic("diary",1,"日记","late generation",token))
        val update=assertNotNull(memory.automaticSnapshot("diary",1,"日记","source"))
        memory.update(memory.getAllSync().single().id,"用户标题","用户正文",null)
        assertFalse(memory.saveAutomatic("diary",1,"日记","overwrite",update))
        assertEquals("用户正文",memory.getAllSync().single().content)
        memory.deleteAllByType("content")
        assertEquals("用户正文",memory.getAllSync().single().content)
    }
    @Test fun sourceChangesAndPrivacyExclusionFenceGeneratedContent()=fixture { d,db ->
        val q=db.dailySatoriQueries;val memory=MemoryRepository(db)
        q.insertDiary("source", null, 1, 1)
        val token=assertNotNull(memory.automaticSnapshot("diary",1,"日记","source"))
        d.execute(null,"UPDATE diary SET content='changed' WHERE id=1",0)
        assertFalse(memory.saveAutomatic("diary",1,"日记","stale",token))
        assertTrue(memory.getAllSync().isEmpty())
        val current=assertNotNull(memory.automaticSnapshot("diary",1,"日记","changed"))
        AssistantPrivacyRepository(d).exclude("diary","1")
        assertFalse(memory.saveAutomatic("diary",1,"日记","forbidden",current))
        assertTrue(memory.getAllSync().isEmpty())
    }
    @Test fun deletionUsesMessageIdentityAndRetainsPrivateExecutionAuditWithoutResurrectingBodies()=fixture { d,db ->
        val privacy=AssistantPrivacyRepository(d);val repo=ChatConversationRepository(db,privacy)
        repo.insert("duplicates","user","same",createdAt=7)
        repo.insert("duplicates","user","same",createdAt=7)
        val originals=repo.getBySession("duplicates")
        repo.deleteMessage(originals.first().id)
        assertEquals(listOf(originals.last().id),repo.getBySession("duplicates").map { it.id })
        val turn=AssistantTurnRepository(d).accept("durable","client","private",8,"UTC")
        d.execute(null,"UPDATE assistant_turn SET context_json='private-copy',action_plan_json='private-copy'",0)
        d.execute(null,"INSERT INTO assistant_action(id,turn_id,kind,parameters_json,status) VALUES('audit',?,'unknown','private-copy','done')",1) { bindLong(0,turn.id) }
        repo.deleteBySession("durable")
        assertTrue(privacy.isExcluded("chat",turn.id.toString()))
        assertNotNull(AssistantTurnRepository(d).get(turn.id))
        assertNull(repo.getById(turn.id))
        assertEquals(listOf(""),com.dailysatori.service.migration.DatabaseSchemaInventory.strings(d,"SELECT context_json FROM assistant_turn"))
        assertEquals(listOf("{}"),com.dailysatori.service.migration.DatabaseSchemaInventory.strings(d,"SELECT parameters_json FROM assistant_action"))
    }
    @Test fun reusingDeletedMessageIdentityCannotAttachAnUnrelatedRoleOrSession()=fixture { d,db ->
        val turn=AssistantTurnRepository(d).accept("owned","client","private",1,"UTC")
        AssistantPrivacyRepository(d).deleteChatMessage(turn.id.toString())
        assertFails { d.execute(null,"INSERT INTO chat_conversation(id,session_id,role,content,created_at) VALUES(?,'owned','assistant','wrong',2)",1) { bindLong(0,turn.id) } }
        assertNull(ChatConversationRepository(db).getById(turn.id))
    }
    private fun fixture(block:(JdbcSqliteDriver,DailySatoriDatabase)->Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { d ->
            DailySatoriDatabase.Schema.create(d);block(d,DailySatoriDatabase(d))
        }
    }
}

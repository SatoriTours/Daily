package com.dailysatori.service.ideatopic

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.*
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.service.migration.DatabaseSchemaInventory
import kotlinx.serialization.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.test.*

class IdeaSnapshotRepositoryTest {
    @Test fun collisionsAndFormatNeverSubstituteDifferentRawSnapshots()=fixture { d,db ->
        val store=IdeaSnapshots(db) { "collision" }
        val a=store.intern("raw-a",1,1)
        val b=store.intern("raw-b",1,2)
        val future=store.intern("raw-a",2,3)
        assertEquals(a,store.intern("raw-a",1,4))
        assertNotEquals(a,b);assertNotEquals(a,future)
        assertEquals("raw-a",store.payload(a));assertEquals("raw-b",store.payload(b))
        assertEquals(listOf("3"),DatabaseSchemaInventory.strings(d,"SELECT COUNT(*) FROM idea_source_snapshot"))
    }
    @Test fun freshCaptureSharesOneSnapshotAndMergeKeepsOpaqueDuplicateBeforeComponentDeletion()=fixture { d,db ->
        d.execute(null,"PRAGMA foreign_keys=ON",0)
        val repo=IdeaTopicRepository(db);val q=db.dailySatoriQueries
        val source=IdeaSourceSnapshot(IdeaSourceKey("diary","gone"),"title","full body")
        repo.capture(source,IdeaTopicContent(title="from"),null,"from","from-source","captured",1)
        assertEquals(1L,q.countIdeaSnapshots().executeAsOne())
        assertEquals(2L,q.selectIdeaTopicEventById("captured").executeAsOne().payload_version)
        assertFalse(q.selectIdeaTopicEventById("captured").executeAsOne().payload_json.contains("full body"))
        q.insertIdeaTopic("into","into","","","","","researching",null,0,2,2)
        val raw="""{"key":{"type":"diary","recordId":"gone"},"originalTitle":"older","originalContent":"old body","future":{"retain":true}}"""
        val store=IdeaSnapshots(db)
        val id=store.intern(raw,createdAt=2)
        // The duplicate from-topic snapshot contains unknown data that must survive the merge.
        d.execute(null,"UPDATE idea_topic_source SET snapshot_id=? WHERE id='from-source'",1) { bindString(0,id) }
        q.insertIdeaTopicSource("into-source","into","into","diary","gone",store.intern(Json.encodeToString(IdeaSourceSnapshot.serializer(),source)),2,2,2)
        repo.merge("from","into","merged",3)
        val event=repo.getDetailSync("into")!!.events.single { it.id=="merged" }
        val value=Json.parseToJsonElement(event.payload).jsonObject["duplicateSourceSnapshots"]!!.jsonArray.single()
        assertEquals(Json.parseToJsonElement(raw),value)
        assertFails { d.execute(null,"DELETE FROM idea_source_snapshot WHERE id=?",1) { bindString(0,id) } }
        assertFails { q.markIdeaTopicMergedInto("from",4,"into") }
        repo.deleteComponent("into")
        assertTrue(q.selectMainIdeaTopics().executeAsList().isEmpty())
        assertEquals(raw,store.payload(id)) // No implicit cache GC of a user snapshot.
    }
    @Test fun observerCanReadDeletedTopicWithoutHydratingStaleEventReferences()=runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { d ->
            DailySatoriDatabase.Schema.create(d)
            val repo=IdeaTopicRepository(DailySatoriDatabase(d))
            repo.capture(IdeaSourceSnapshot(IdeaSourceKey("diary","old"),"title","body"),IdeaTopicContent(title="topic"),
                null,"topic","source","capture",1)
            assertNotNull(repo.observeDetail("topic").first())
            val deletion=async(start=CoroutineStart.UNDISPATCHED) { withTimeout(5_000) {
                repo.observeDetail("topic").first { it==null }
            } }
            repo.deleteComponent("topic")
            assertNull(deletion.await())
        }
    }
    private fun fixture(block:(JdbcSqliteDriver,DailySatoriDatabase)->Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { d ->
            DailySatoriDatabase.Schema.create(d)
            block(d,DailySatoriDatabase(d))
        }
    }
}

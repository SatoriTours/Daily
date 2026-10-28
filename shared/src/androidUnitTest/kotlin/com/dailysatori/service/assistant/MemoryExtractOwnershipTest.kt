package com.dailysatori.service.assistant

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.*
import com.dailysatori.service.ai.*
import com.dailysatori.service.memory.MemoryExtractService
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class MemoryExtractOwnershipTest {
    @Test fun savingAReplyRefreshesCanonicalRootMemoryIncludingCompletedTranscripts()=diaryCase(false)
    @Test fun rebuildingProcessesEachRootOnceAndPreservesManualOwnership()=diaryCase(true)
    private fun diaryCase(rebuild:Boolean)=runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { d ->
            DailySatoriDatabase.Schema.create(d)
            val db=DailySatoriDatabase(d);val q=db.dailySatoriQueries;val memory=MemoryRepository(db)
            d.execute(null,"INSERT INTO diary(id,content,created_at,updated_at,parent_diary_id) VALUES(1,'主日记',1,1,NULL),(2,'续写正文',2,2,1),(3,'人工来源',3,3,NULL)",0)
            d.execute(null,"INSERT INTO diary_attachment(diary_id,kind,local_path,transcript,transcript_status,created_at,updated_at) VALUES(2,'audio','audio.m4a','完成转写','completed',2,2)",0)
            q.insertAutomaticMemory("diary",1,"日记","old automatic",1,1,"old")
            memory.insert("content","diary",3,"人工","不能覆盖")
            val configs=AIConfigRepository(db)
            configs.insert("openai","https://example.test/v1","test-only","test-model",1)
            var requests=0
            HttpClient(MockEngine { request ->
                requests++
                val body=(request.body as io.ktor.http.content.OutgoingContent.ByteArrayContent).bytes().decodeToString()
                assertTrue(body.contains("主日记"));assertTrue(body.contains("续写正文"));assertTrue(body.contains("完成转写"))
                respond("""{"choices":[{"message":{"content":"generated"}}]}""",
                    HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
            }).use { client ->
                val service=MemoryExtractService(AiService(client),AiConfigService(configs),memory)
                if(rebuild) service.rebuildAll(ArticleRepository(db),DiaryRepository(db,d),BookRepository(db),BookViewpointRepository(db)) {}
                else service.extractAndSave("diary",2,"日记","续写正文")
                assertEquals("generated",memory.getBySource("diary",1)?.content)
                assertEquals("不能覆盖",memory.getBySource("diary",3)?.content)
                assertNull(memory.getBySource("diary",2))
                assertEquals(1,requests)
            }
        }
    }
    @Test fun actualExtractorCannotCommitStaleOrUserOwnedMemory()=runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { d ->
            DailySatoriDatabase.Schema.create(d)
            val db=DailySatoriDatabase(d);val memory=MemoryRepository(db)
            db.dailySatoriQueries.insertDiary("source", null, 1, 1)
            val configs=AIConfigRepository(db)
            configs.insert("openai","https://example.test/v1","test-only","test-model",1)
            var requests=0
            var duringResponse:()->Unit={ d.execute(null,"UPDATE diary SET content='changed' WHERE id=1",0) }
            HttpClient(MockEngine {
                requests++;duringResponse()
                respond("""{"choices":[{"message":{"content":"generated"}}]}""",
                    HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
            }).use { client ->
                val extractor=MemoryExtractService(AiService(client),AiConfigService(configs),memory)
                extractor.extractAndSave("diary",1,"日记","source")
                assertTrue(memory.getAllSync().isEmpty())
                d.execute(null,"UPDATE diary SET content='source' WHERE id=1",0)
                duringResponse={}
                extractor.extractAndSave("diary",1,"日记","source")
                assertEquals("automatic",memory.getAllSync().single().content_origin)
                assertEquals("generated",memory.getAllSync().single().content)
                duringResponse={ memory.update(memory.getAllSync().single().id,"人工","manual",null) }
                extractor.extractAndSave("diary",1,"日记","source")
                assertEquals("manual",memory.getAllSync().single().content)
                assertEquals("manual",memory.getAllSync().single().content_origin)
                extractor.extractAndSave("diary",1,"日记","source")
                assertEquals(3,requests)
            }
        }
    }
}

package com.dailysatori.service.migration

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DiaryImageMigrationTest {
    @Test fun legacyImagesKeepDuplicatesOrderRemoteAndMissingFilesWithoutChangingOriginals() = fixture { driver, db ->
        DiaryLegacyFieldsFixture.install(driver)
        val q = db.dailySatoriQueries
        q.insertDiary("原文\u0000后半段", "开心", 11, 12)
        driver.execute(null,"UPDATE diary SET images='diary_images/b.png,https://example.test/a.png,diary_images/b.png,missing.png' WHERE id=1",0)
        q.insertDiaryAttachment(1, "audio", "/audio/original.m4a", "原录音", "audio/mp4",
            123, 456, "原始转写", "completed", "failed", "keep error", 10, 20)
        val settings = SettingRepository(db)
        settings.upsert(SettingKeys.schemaVersion, "39")
        DatabaseMigration(driver, settings).runMigrations()
        assertEquals(com.dailysatori.config.DatabaseConfig.currentSchemaVersion.toString(), settings.get(SettingKeys.schemaVersion))
        val rows = driver.executeQuery(null,
            "SELECT kind,local_path,remote_url,position FROM diary_attachment ORDER BY position,id", { c ->
                val result = mutableListOf<List<String?>>()
                while (c.next().value) result += listOf(c.getString(0),c.getString(1),c.getString(2),c.getLong(3).toString())
                QueryResult.Value(result)
            },0).value
        assertEquals(listOf(
            listOf("image","diary_images/b.png",null,"0"),
            listOf("image","","https://example.test/a.png","1"),
            listOf("image","diary_images/b.png",null,"2"),
            listOf("image","missing.png",null,"3"),
            listOf("audio","/audio/original.m4a",null,"4")), rows)
        val original = q.selectDiaryAttachmentById(1).executeAsOne()
        assertEquals("原始转写", original.transcript)
        assertEquals("failed", original.knowledge_status)
        assertEquals(20L, original.updated_at)
        assertEquals("原文\u0000后半段", q.selectDiaryById(1).executeAsOne().content)
        DatabaseMigration(driver,settings).runMigrations()
        assertEquals(5, q.selectAttachmentsForDiary(1).executeAsList().size)
    }

    @Test fun unknownKindsStopMigrationWithoutAdvancingVersionOrLosingRows() = fixture { driver, db ->
        db.dailySatoriQueries.insertDiary("keep", null, 1, 1)
        db.dailySatoriQueries.insertDiaryAttachment(1,"unknown","keep.bin","","",0,0,"keep transcript","none","none","",1,1)
        val settings = SettingRepository(db)
        settings.upsert(SettingKeys.schemaVersion,"39")
        assertFails { DatabaseMigration(driver,settings).runMigrations() }
        assertEquals("39",settings.get(SettingKeys.schemaVersion))
        assertEquals("keep transcript",db.dailySatoriQueries.selectDiaryAttachmentById(1).executeAsOne().transcript)
        assertEquals(1,db.dailySatoriQueries.selectAttachmentsForDiary(1).executeAsList().size)
    }

    @Test fun runtimeWritesImagesOnlyAsAttachmentsAndReorderKeepsIdentity() = fixture { driver, db ->
        val repository = DiaryRepository(db,driver)
        val id = runBlocking { repository.create("正文",images="b.png,a.png,b.png") }
        assertNull(db.dailySatoriQueries.selectDiaryById(id).executeAsOne().images)
        val before = db.dailySatoriQueries.selectAttachmentsForDiary(id).executeAsList()
        assertEquals(listOf("b.png","a.png","b.png"),before.map { it.local_path })
        repository.update(id,"修改正文",null,null,"b.png,b.png,a.png")
        val after = db.dailySatoriQueries.selectAttachmentsForDiary(id).executeAsList()
        assertEquals(listOf(before[0].id,before[2].id,before[1].id),after.map { it.id })
        assertEquals("b.png,b.png,a.png",repository.getById(id)?.images)
        val reply = runBlocking { DiaryThreadRepository(db,driver).createReply(id,"续写",images="https://example.test/reply.png") }
        val snapshot = DiaryThreadRepository(db,driver).getSnapshot(id)!!
        assertEquals("https://example.test/reply.png",snapshot.entries.single { it.id==reply }.images)
        assertNull(db.dailySatoriQueries.selectDiaryById(reply).executeAsOne().images)
    }

    @Test fun editingProjectedImagesDoesNotSplitCommaOrWhitespacePathsOrReplaceTheirIdentity() = fixture { driver,db ->
        val diaries=DiaryRepository(db,driver)
        val id=runBlocking { diaries.create("keep") }
        val attachments=DiaryAttachmentRepository(db,driver)
        val path="diary_images/a,b.png"
        val attachmentId=attachments.create(id,DiaryAttachmentDraft(DiaryAttachmentKind.image,path,displayName="original"))
        val projected=diaries.getById(id)!!
        diaries.update(id,projected.content,projected.tags,projected.mood,projected.images)
        val after=attachments.getForDiary(id)
        assertEquals(1,after.size)
        assertEquals(attachmentId,after.single().id)
        assertEquals(path,after.single().local_path)
        assertEquals("original",after.single().display_name)
    }

    @Test fun twoUnprovenRepresentationsOfTheSameFileRemainTwoAssociations() = fixture { driver,db ->
        DiaryLegacyFieldsFixture.install(driver)
        db.dailySatoriQueries.insertDiary("keep", null, 1, 2)
        driver.execute(null,"UPDATE diary SET images='same.png' WHERE id=1",0)
        val original=DiaryAttachmentRepository(db,driver).create(1,DiaryAttachmentDraft(DiaryAttachmentKind.image,"same.png",transcript="keep metadata"))
        val settings=SettingRepository(db)
        settings.upsert(SettingKeys.schemaVersion,"39")
        DatabaseMigration(driver,settings).runMigrations()
        val rows=db.dailySatoriQueries.selectAttachmentsForDiary(1).executeAsList()
        assertEquals(2,rows.size)
        assertEquals(listOf("same.png","same.png"),rows.map { it.local_path })
        assertEquals("keep metadata",rows.single { it.id==original }.transcript)
    }

    @Test fun deletingAReferenceMustAccountForOtherDiariesArticleImagesAndCovers() = fixture { driver,db ->
        val diaries=DiaryRepository(db,driver)
        val first=runBlocking { diaries.create("first",images="images/shared.png") }
        val second=runBlocking { diaries.create("second",images="/old/app_flutter/images/shared.png") }
        diaries.delete(first)
        assertTrue(mediaFileIsReferenced(db,"/new/DailySatori/images/shared.png","/new/DailySatori"))
        diaries.delete(second)
        assertFalse(mediaFileIsReferenced(db,"images/shared.png","/new/DailySatori"))
        driver.execute(null,"INSERT INTO article(id,cover_image,created_at,updated_at) VALUES(1,'images/shared.png',1,1)",0)
        assertTrue(mediaFileIsReferenced(db,"/old/app_flutter/images/shared.png","/new/DailySatori"))
        driver.execute(null,"UPDATE article SET cover_image=NULL",0)
        driver.execute(null,"INSERT INTO image(path,article_id,created_at,updated_at) VALUES('images/shared.png',1,1,1)",0)
        assertTrue(mediaFileIsReferenced(db,"images/shared.png","/new/DailySatori"))
        driver.execute(null,"DELETE FROM image",0)
        driver.execute(null,"INSERT INTO book(title,author,category,cover_image,introduction,created_at,updated_at) VALUES('b','','','images/shared.png','',1,1)",0)
        assertTrue(mediaFileIsReferenced(db,"images/shared.png","/new/DailySatori"))
    }

    @Test fun cachedImageUsesLocalCopyButKeepsRemoteUrlAndRemoteOnlyImagesNeedNoFile() = fixture { driver,db ->
        val diaries=DiaryRepository(db,driver)
        val id=runBlocking { diaries.create("offline first") }
        val attachments=DiaryAttachmentRepository(db,driver)
        val cached=attachments.create(id,DiaryAttachmentDraft(DiaryAttachmentKind.image,"cache.png",remoteUrl="https://example.test/original.png"))
        attachments.create(id,DiaryAttachmentDraft(DiaryAttachmentKind.image,"",remoteUrl="https://example.test/only.png"))
        assertEquals("cache.png,https://example.test/only.png",diaries.getById(id)?.images)
        val row=attachments.getById(cached)!!
        assertEquals("cache.png",row.local_path)
        assertEquals("https://example.test/original.png",row.remote_url)
    }

    @Test fun actualAttachmentNamesThatLookLikeLegacyPlaceholdersAreNotDiscardedDuringEditing() = fixture { driver,db ->
        val diaries=DiaryRepository(db,driver)
        val id=runBlocking { diaries.create("keep attachments") }
        val attachments=DiaryAttachmentRepository(db,driver)
        val paths=listOf("null","diary_images/null","images/null")
        paths.forEach { attachments.create(id,DiaryAttachmentDraft(DiaryAttachmentKind.image,it)) }
        val ids=attachments.getForDiary(id).map { it.id }
        val diary=diaries.getById(id)!!
        assertEquals(paths,com.dailysatori.service.diary.parseDiaryImagePaths(diary.images))
        diaries.update(id,diary.content,diary.tags,diary.mood,diary.images)
        assertEquals(ids,attachments.getForDiary(id).map { it.id })
    }

    private fun fixture(block:(JdbcSqliteDriver,DailySatoriDatabase)->Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            driver.execute(null,"PRAGMA foreign_keys=ON",0)
            DailySatoriDatabase.Schema.create(driver)
            block(driver,DailySatoriDatabase(driver))
        }
    }
}

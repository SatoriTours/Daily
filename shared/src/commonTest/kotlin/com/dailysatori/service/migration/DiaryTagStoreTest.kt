package com.dailysatori.service.migration

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.*
import com.dailysatori.service.diary.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DiaryTagStoreTest {
    @Test fun currentProjectionAndPastSuppressionHaveIndependentLifetimes() = fixture { driver,db ->
        val diaries=DiaryRepository(db,driver)
        val id=runBlocking { diaries.create("first body") }
        val tags=DiaryTagRepository(db,DiaryThreadRepository(db,driver))
        tags.apply(assertNotNull(tags.prepare(id)),listOf("读书"))
        tags.edit(id,emptyList())
        assertEquals(listOf("读书"),tags.state(id).suppressed)
        assertEquals(listOf("1"),DatabaseSchemaInventory.strings(driver,"SELECT COUNT(*) FROM diary_tag_history WHERE decision='suppressed'"))
        diaries.update(id,"second body",null,null,null)
        assertTrue(tags.state(id).suppressed.isEmpty())
        assertTrue(tags.apply(assertNotNull(tags.prepare(id)),listOf("读书")))
        assertEquals(listOf("读书"),tags.tags(id))
        assertEquals(listOf("1"),DatabaseSchemaInventory.strings(driver,"SELECT COUNT(*) FROM diary_tag_history WHERE decision='suppressed'"))
        assertNull(SettingRepository(db).get("diary_tag_state_v1_$id"))
        assertEquals("读书",db.dailySatoriQueries.selectDiaryById(id).executeAsOne().tags)
        assertFalse("tags" in DatabaseSchemaInventory.strings(driver,"SELECT name FROM pragma_table_info('diary')"))
    }

    @Test fun runtimeDoesNotReadRetiredTagsAndCatalogKeys() = fixture { driver,db ->
        val diaries=DiaryRepository(db,driver)
        val id=runBlocking { diaries.create("body","current") }
        SettingRepository(db).upsert("diary_tag_vocabulary_v1","broken-retired-value")
        assertFails { driver.execute(null,"UPDATE diary SET tags='retired' WHERE id=1",0) }
        val tags=DiaryTagRepository(db,DiaryThreadRepository(db,driver))
        assertEquals(listOf("current"),tags.tags(id))
        assertEquals("current",diaries.getById(id)!!.tags)
        assertTrue(diaries.searchSync("retired").isEmpty())
        assertEquals(id,diaries.searchSync("current").single().id)
    }

    @Test fun explicitDiaryDeletionRemovesItsTagDomainEvenWithoutForeignKeys() = fixture { driver,db ->
        val diaries=DiaryRepository(db,driver)
        val id=runBlocking { diaries.create("body","keep") }
        diaries.delete(id)
        for(table in listOf("diary_tag_assignment","diary_tag_history","diary_tag_processing")) {
            assertEquals(listOf("0"),DatabaseSchemaInventory.strings(driver,"SELECT COUNT(*) FROM $table"))
        }
        for(table in listOf("diary_tag_assignment","diary_tag_history","diary_tag_processing")) {
            assertTrue(DatabaseSchemaInventory.strings(driver,"PRAGMA foreign_key_check('$table')").isEmpty())
        }
    }

    @Test fun mergingSuppressedOnlyTagsAdvancesTheirVersionAndRejectsEarlierAiResults() = fixture { driver,db ->
        val id=runBlocking { DiaryRepository(db,driver).create("body") }
        val tags=DiaryTagRepository(db,DiaryThreadRepository(db,driver))
        tags.apply(assertNotNull(tags.prepare(id)),listOf("压力"))
        tags.edit(id,emptyList())
        val stale=assertNotNull(tags.prepare(id,force=true))
        tags.merge("压力","焦虑")
        assertEquals(listOf("焦虑"),tags.state(id).suppressed)
        assertFalse(tags.apply(stale,listOf("旅行")))
        assertTrue(tags.undoMerge())
        assertEquals(listOf("压力"),tags.state(id).suppressed)
    }

    @Test fun suppressedSourceMergedIntoActiveTargetCanUndoUnlessEditedLater() {
        for(editedLater in listOf(false,true)) fixture { driver,db ->
            val id=runBlocking { DiaryRepository(db,driver).create("body") }
            val tags=DiaryTagRepository(db,DiaryThreadRepository(db,driver))
            tags.apply(assertNotNull(tags.prepare(id)),listOf("压力"))
            tags.edit(id,listOf("焦虑"))
            assertEquals(listOf("压力"),tags.state(id).suppressed)
            val stale=assertNotNull(tags.prepare(id,force=true))
            tags.merge("压力","焦虑")
            assertEquals(listOf("焦虑"),tags.tags(id))
            assertTrue(tags.state(id).suppressed.isEmpty())
            assertFalse(tags.apply(stale,listOf("旅行")))
            if(editedLater) tags.edit(id,listOf("其他"))
            assertEquals(!editedLater,tags.undoMerge())
            assertEquals(listOf(if(editedLater) "其他" else "焦虑"),tags.tags(id))
            assertEquals(if(editedLater) emptyList() else listOf("压力"),tags.state(id).suppressed)
        }
    }

    private fun fixture(block:(JdbcSqliteDriver,DailySatoriDatabase)->Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            block(driver,DailySatoriDatabase(driver))
        }
    }
}

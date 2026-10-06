package com.dailysatori.service.diary

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.DiaryTagRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DiaryTagOptimizationTest {
    @Test
    fun pendingNamesStayOutOfVocabularyUntilUserApproves() = Fixture().use { f ->
        f.tags.suggestPending(listOf("园艺", "园艺"))
        assertFalse("园艺" in f.tags.vocabulary().names)
        assertEquals(listOf("园艺"), f.tags.vocabulary().pendingNames)
        f.tags.approvePending("园艺")
        assertTrue("园艺" in f.tags.vocabulary().names)
        assertTrue(f.tags.vocabulary().pendingNames.isEmpty())
    }
    @Test
    fun deletionAppliesToEditedBodyAndExpiresOnNextSavedBodyChange() = Fixture().use { f ->
        f.tags.apply(assertNotNull(f.tags.prepare(f.id)), listOf("读书"))
        val draft = DiaryTagDraft.from("读书", f.tags.state(f.id), "原正文", f.tags.vocabulary()).remove("读书")
        f.tags.saveEditedDiary(f.id, "修改正文", draft.tags, null, null, draft)
        f.tags.apply(assertNotNull(f.tags.prepare(f.id)), listOf("读书", "旅行"))
        assertEquals(listOf("旅行"), f.tags.tags(f.id))
        f.diaries.update(f.id, "下一次修改正文", "旅行", null, null)
        f.tags.apply(assertNotNull(f.tags.prepare(f.id)), listOf("读书"))
        assertEquals(listOf("读书"), f.tags.tags(f.id))
    }

    @Test
    fun generationDuringSameEditingSessionRespectsExplicitDeletionAfterBodyChanges() {
        val draft = DiaryTagDraft(listOf("读书"), listOf("读书"), contentFingerprint = diaryTagFingerprint("旧正文"))
            .remove("读书").withGenerated("新正文", listOf("读书", "旅行"))
        assertEquals(listOf("旅行"), draft.tags)
    }

    @Test
    fun undoMergePreservesUnrelatedNewTagsAndSuggestions() = Fixture().use { f ->
        f.tags.edit(f.id, listOf("压力"))
        f.tags.merge("压力", "焦虑")
        val other = runBlocking { f.diaries.create("其他日记") }
        f.tags.edit(other, listOf("园艺"))
        f.tags.suggest(listOf(DiaryTagMerge("散步", "步行")))
        assertTrue(f.tags.undoMerge())
        assertEquals(listOf("压力"), f.tags.tags(f.id))
        assertEquals(listOf("园艺"), f.tags.tags(other))
        assertTrue("园艺" in f.tags.vocabulary().names)
        assertTrue(DiaryTagMerge("散步", "步行") in f.tags.vocabulary().suggestions)
        assertEquals("压力", f.tags.canonical("压力"))
    }

    @Test
    fun undoMergeRefusesToOverwriteChangesToAffectedDiary() = Fixture().use { f ->
        f.tags.edit(f.id, listOf("压力"))
        f.tags.merge("压力", "焦虑")
        f.tags.edit(f.id, listOf("焦虑", "工作"))
        assertFalse(f.tags.undoMerge())
        assertEquals(listOf("焦虑", "工作"), f.tags.tags(f.id))
    }

    @Test
    fun undoMergeKeepsUnrelatedAliasesAddedSinceTheMerge() = Fixture().use { f ->
        f.tags.edit(f.id, listOf("压力"))
        f.tags.merge("压力", "焦虑")
        val other = runBlocking { f.diaries.create("其他日记", "学习") }
        f.tags.merge("学习", "读书")
        // Only the latest merge is undoable; older aliases must survive it.
        assertTrue(f.tags.undoMerge())
        assertEquals("焦虑", f.tags.canonical("压力"))
        assertEquals(listOf("焦虑"), f.tags.tags(f.id))
        assertEquals(listOf("学习"), f.tags.tags(other))
    }

    private class Fixture : AutoCloseable {
        private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        init { DailySatoriDatabase.Schema.create(driver) }
        private val db = DailySatoriDatabase(driver)
        val diaries = DiaryRepository(db, driver)
        val tags = DiaryTagRepository(db)
        val id = runBlocking { diaries.create("原正文") }
        override fun close() = driver.close()
    }
}

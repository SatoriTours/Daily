package com.dailysatori.service.diary

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.DiaryThoughtRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiaryThoughtChatContextTest {
    @Test
    fun chatReceivesGroundedThoughtsAndOpenableDiaryReferences() = withFixture { fixture ->
        val context = assertNotNull(fixture.provider.getContext())
        assertTrue(context.prompt.contains("先做重要的事情"))
        assertTrue(context.prompt.contains("明确表达"))
        assertEquals(listOf(fixture.diaryId), context.references.map { it.id })
        assertEquals("diary", context.references.single().type)
        assertTrue(context.prompt.contains("diary_${fixture.diaryId}"))
        assertTrue(context.prompt.length < 16_000)
    }

    @Test
    fun disabledContextIsPersistedAndDoesNotDeleteArchive() = withFixture { fixture ->
        fixture.repository.setUseInChat(false)
        assertFalse(DiaryThoughtRepository(fixture.settings).useInChat())
        assertNull(fixture.provider.getContext())
        assertEquals(1, fixture.repository.load().thoughts.size)
    }

    @Test
    fun editedDeletedOrNewDiariesPreventUsingStaleConclusions() = withFixture { fixture ->
        val original = fixture.diaries.getById(fixture.diaryId)!!
        fixture.diaries.update(fixture.diaryId, "我改变了想法", null, null, null)
        assertNull(fixture.provider.getContext())
        fixture.diaries.update(fixture.diaryId, original.content, null, null, null)
        assertNotNull(fixture.provider.getContext())
        val newId = fixture.diaries.create("新增的反思")
        assertNull(fixture.provider.getContext())
        fixture.diaries.delete(newId)
        fixture.diaries.delete(fixture.diaryId)
        assertNull(fixture.provider.getContext())
    }

    @Test
    fun latestCorrectionsStaySeparateAndInvalidateOlderInterpretations() = withFixture { fixture ->
        fixture.repository.saveCorrections("临时决定不代表长期习惯")
        val context = assertNotNull(fixture.provider.getContext())
        assertTrue(context.prompt.contains("临时决定不代表长期习惯"))
        assertFalse(context.prompt.contains("先做重要的事情"))
        assertTrue(context.references.isEmpty())
    }

    @Test
    fun emptyArchiveAddsNoPretendPersonality() = withFixture { fixture ->
        fixture.repository.save(DiaryThoughtArchive())
        assertNull(fixture.provider.getContext())
    }

    private fun withFixture(block: suspend (Fixture) -> Unit) = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val database = DailySatoriDatabase(driver)
            val diaries = DiaryRepository(database, driver)
            val settings = SettingRepository(database)
            val repository = DiaryThoughtRepository(settings)
            val id = diaries.create("我决定先做重要的事情")
            val diary = diaries.getById(id)!!
            val thought = DiaryThought("做事准则", "先做重要的事情", "明确表达", listOf(DiaryThoughtEvidence(id, "先做重要的事情")))
            repository.save(DiaryThoughtArchive(
                fingerprint = diaryThoughtFingerprint(listOf(DiaryThoughtSource(id, diary.content, diary.created_at)), ""),
                thoughts = listOf(thought), diaryCount = 1, generatedAt = 123,
            ))
            block(Fixture(id, diaries, repository, settings, DiaryThoughtChatContextProvider(repository, diaries)))
        } finally {
            driver.close()
        }
    }

    private data class Fixture(
        val diaryId: Long,
        val diaries: DiaryRepository,
        val repository: DiaryThoughtRepository,
        val settings: SettingRepository,
        val provider: DiaryThoughtChatContextProvider,
    )
}

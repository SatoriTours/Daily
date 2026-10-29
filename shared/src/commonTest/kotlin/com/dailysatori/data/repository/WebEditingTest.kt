package com.dailysatori.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.*

class WebEditingTest {
    @Test fun diaryRejectsStaleEditsWithoutChangingMediaOrTags() = fixture { driver, db ->
        val repo = DiaryRepository(db, driver)
        repo.insert("old", "生活", "平静", null)
        val expected = repo.getAllSync().single()
        repo.update(expected.id, "phone", "工作", "开心", null)
        assertFalse(repo.updateIfUnchanged(expected, "web", "生活", "平静"))
        assertEquals("phone", repo.getById(expected.id)!!.content)
        assertTrue(repo.updateIfUnchanged(repo.getById(expected.id)!!, "web", "工作", "平静"))
        assertEquals("web", repo.getById(expected.id)!!.content)
    }

    @Test fun bookAndViewpointEditsRejectStaleSnapshotsAndKeepOtherFields() = fixture { _, db ->
        val books = BookRepository(db)
        val id = books.insertAndReturnId("old", "author", "category", "", "intro")
        val expected = books.getById(id)!!
        books.update(id, "phone", "author", "category", "", "intro", 0)
        assertFalse(books.updateIfUnchanged(expected, "web", "a", "c", "", "i"))
        assertTrue(books.updateIfUnchanged(books.getById(id)!!, "web", "a", "c", "", "i"))
        val notes = BookViewpointRepository(db)
        notes.insert(id, "note", "body", "example")
        val note = notes.getByBookSync(id).single()
        notes.update(note.id, "phone", "new body", "example")
        assertFalse(notes.updateIfUnchanged(note, "web", "body", "example"))
        assertTrue(notes.updateIfUnchanged(notes.getById(note.id)!!, "web", "body", "example"))
    }

    @Test fun articleMetadataEditsKeepGeneratedContentAndRejectDeletedSnapshots() = fixture { _, db ->
        val repo = ArticleRepository(db)
        repo.insert(url = "https://example.com", title = "original")
        val original = repo.getAllSync().single()
        repo.updateAiContent(original.id, "summary", "AI title", null)
        val expected = repo.getById(original.id)!!
        assertTrue(repo.updateDetailsIfUnchanged(expected, "edited", "my note"))
        assertFalse(repo.updateDetailsIfUnchanged(expected, "stale", "lost"))
        assertEquals("summary", repo.getById(original.id)!!.ai_content)
        assertEquals("my note", repo.getById(original.id)!!.comment)
        val latest = repo.getById(original.id)!!
        repo.delete(latest.id)
        assertFalse(repo.updateDetailsIfUnchanged(latest, "resurrect", ""))
    }

    private fun fixture(block: (JdbcSqliteDriver, DailySatoriDatabase) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            block(driver, DailySatoriDatabase(driver))
        }
    }
}

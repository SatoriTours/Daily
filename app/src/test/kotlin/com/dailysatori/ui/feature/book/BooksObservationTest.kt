package com.dailysatori.ui.feature.book

import android.content.ContextWrapper
import androidx.lifecycle.viewModelScope
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.core.worker.AsyncTaskScheduler
import com.dailysatori.data.ObservationTrackingDriver
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointRepository
import com.dailysatori.service.book.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class BooksObservationTest {
    @Test
    fun switchingBooksKeepsOneObserverAndOldBookUpdatesCannotOverwriteSelection() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val databaseFile = Files.createTempFile("books-observation", ".db")
        val driver = ObservationTrackingDriver(JdbcSqliteDriver("jdbc:sqlite:$databaseFile"))
        var viewModel: BooksViewModel? = null
        try {
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val books = BookRepository(db)
            val viewpoints = BookViewpointRepository(db)
            val firstId = books.insertAndReturnId("first", "", "", "", "")
            val secondId = books.insertAndReturnId("second", "", "", "", "")
            viewpoints.insert(firstId, "first viewpoint", "content", "")
            viewpoints.insert(secondId, "second viewpoint", "content", "")
            val model = BooksViewModel(books, viewpoints, unusedGenerator, AsyncTaskRepository(db), AsyncTaskScheduler(unusedContext()))
            viewModel = model
            withTimeout(5_000) { model.state.first { it.viewpoints.isNotEmpty() } }
            repeat(10) { index ->
                val bookId = if (index % 2 == 0) firstId else secondId
                model.selectBook(bookId)
                withTimeout(5_000) { model.state.first { it.viewpoints.singleOrNull()?.book_id == bookId } }
                assertEquals(1, driver.activeSubscriptions("book_viewpoint"))
            }
            viewpoints.insert(firstId, "old book update", "content", "")
            viewpoints.insert(secondId, "current book update", "content", "")
            val state = withTimeout(5_000) { model.state.first { it.viewpoints.size == 2 } }
            assertEquals(secondId, state.currentBookId)
            assertEquals(setOf(secondId), state.viewpoints.map { it.book_id }.toSet())
            val subscriptions = driver.totalSubscriptions("book")
            repeat(10) { model.loadBooks() }
            assertEquals(subscriptions, driver.totalSubscriptions("book"))
            model.selectBook(null)
            withTimeout(5_000) { model.state.first { it.viewpoints.isEmpty() } }
            assertEquals(0, driver.activeSubscriptions("book_viewpoint"))
        } finally {
            viewModel?.viewModelScope?.coroutineContext?.get(Job)?.cancelAndJoin()
            driver.close()
            Files.deleteIfExists(databaseFile)
            Dispatchers.resetMain()
        }
    }

    private val unusedGenerator = object : BookAiFallbackGenerator {
        override suspend fun generate(book: BookSearchResult, info: WeReadBookInfo, chapters: List<WeReadChapter>, reviews: List<WeReadReview>): List<BookViewpointDraft> =
            error("Observation must not invoke AI")
    }

    private fun unusedContext(): ContextWrapper {
        val field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = field.get(null)
        return unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, ContextWrapper::class.java) as ContextWrapper
    }
}

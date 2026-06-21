package com.dailysatori.core.task

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookViewpointGenerateTaskSourceTest {
    @Test
    fun payloadJsonCarriesBookId() {
        val json = bookViewpointGenerateTaskPayloadJson(bookId = 9)

        assertTrue(json.contains("\"bookId\":9"))
    }

    @Test
    fun taskHandlerUsesBookViewpointTaskType() {
        assertEquals("book_viewpoint_generate", BookViewpointGenerateTaskHandler.TYPE)
    }

    @Test
    fun booksViewModelEnqueuesRefreshIntoAsyncTaskFramework() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/book/BooksViewModel.kt").readText()

        assertTrue(source.contains("AsyncTaskType.book_viewpoint_generate.name"))
        assertTrue(source.contains("bookViewpointGenerateTaskPayloadJson(bookId)"))
        assertTrue(source.contains("\"book_viewpoint_generate:\$bookId\""))
        assertTrue(source.contains("asyncTaskRepo.enqueue("))
        assertTrue(source.contains("asyncTaskScheduler.enqueue(taskId)"))
    }

    @Test
    fun appModuleRegistersBookViewpointGenerateHandler() {
        val source = File("src/main/kotlin/com/dailysatori/core/di/AppModule.kt").readText()

        assertTrue(source.contains("single { BookViewpointGenerateTaskHandler(get(), get(), get()) }"))
        assertTrue(source.contains("get<BookViewpointGenerateTaskHandler>()"))
    }
}

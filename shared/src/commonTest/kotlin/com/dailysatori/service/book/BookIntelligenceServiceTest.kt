package com.dailysatori.service.book

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookIntelligenceServiceTest {
    @Test
    fun searchBooksDelegatesToSource() = runBlocking {
        val expected = listOf(BookSearchResult(title = "三体", author = "刘慈欣"))
        val source = FakeBookIntelligenceSource(searchResults = expected)
        val service = BookIntelligenceService(source)

        val results = service.searchBooks("三体")

        assertEquals(expected, results)
        assertEquals("三体", source.searchQuery)
    }

    @Test
    fun generateViewpointsDelegatesToSource() = runBlocking {
        val book = BookSearchResult(title = "三体", author = "刘慈欣")
        val expected = listOf(BookViewpointDraft(title = "判断", content = "解释", example = "案例"))
        val source = FakeBookIntelligenceSource(viewpoints = expected)
        val service = BookIntelligenceService(source)

        val results = service.generateViewpoints(book)

        assertEquals(expected, results)
        assertEquals(book, source.viewpointBook)
    }

    @Test
    fun parsesDoubanSuggestResultWithAuthorCoverAndUrl() {
        val json = """
            [
              {"title":"供应链架构师","url":"https:\/\/book.douban.com\/subject\/26995807\/","pic":"https://img9.doubanio.com\/view\/subject\/s\/public\/s33514286.jpg","author_name":"施云","year":"2016","type":"b","id":"26995807"}
            ]
        """.trimIndent()

        val results = parseDoubanSuggestResults(json)

        assertEquals(1, results.size)
        assertEquals("供应链架构师", results.first().title)
        assertEquals("施云", results.first().author)
        assertEquals("https://img9.doubanio.com/view/subject/s/public/s33514286.jpg", results.first().coverUrl)
        assertEquals("https://book.douban.com/subject/26995807/", results.first().sourceUrl)
    }

    @Test
    fun stripsAiLocalizationTermsBeforeExternalBookSearch() {
        assertEquals("供应链架构师", externalBookSearchQuery("供应链架构师 中文书籍 中文资料"))
        assertEquals("Ray Dalio", externalBookSearchQuery("Ray Dalio"))
    }

    @Test
    fun parsesDoubanSubjectDetailsForIntroAndLargeCover() {
        val html = """
            <meta property="og:description" content="供应链是一个复杂的系统，供应链的变革九死一生。" />
            <meta property="og:image" content="https://img9.doubanio.com/view/subject/l/public/s33514286.jpg" />
            <meta property="book:isbn" content="9787504760463" />
        """.trimIndent()

        val details = parseDoubanSubjectDetails(html)

        assertEquals("供应链是一个复杂的系统，供应链的变革九死一生。", details.introduction)
        assertEquals("https://img9.doubanio.com/view/subject/l/public/s33514286.jpg", details.coverUrl)
        assertEquals("9787504760463", details.isbn)
    }

    @Test
    fun doubanSubjectDetailFetchUsesShortTimeout() {
        assertTrue(doubanSubjectDetailTimeoutMs() <= 3_000L)
    }

    private class FakeBookIntelligenceSource(
        private val searchResults: List<BookSearchResult> = emptyList(),
        private val viewpoints: List<BookViewpointDraft> = emptyList(),
    ) : BookIntelligenceSource {
        var searchQuery: String? = null
            private set
        var viewpointBook: BookSearchResult? = null
            private set

        override suspend fun searchBooks(query: String): List<BookSearchResult> {
            searchQuery = query
            return searchResults
        }

        override suspend fun generateViewpoints(book: BookSearchResult): List<BookViewpointDraft> {
            viewpointBook = book
            return viewpoints
        }
    }
}

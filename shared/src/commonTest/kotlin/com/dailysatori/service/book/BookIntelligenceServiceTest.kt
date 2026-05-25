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
        val expected = BookViewpointGenerationResult(
            drafts = listOf(BookViewpointDraft(title = "判断", content = "解释", example = "案例")),
            source = BookViewpointSource.WeRead,
        )
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
    fun weReadMaterialIsAlwaysSentToAiForViewpointGeneration() = runBlocking {
        val aiDrafts = (1..10).map { index ->
            BookViewpointDraft(
                title = "观点 $index",
                content = "这个观点说明行动必须先确认风险来源、现实条件和可接受边界，避免把态度误当方案。",
                example = "一个团队准备推进改革时，负责人先判断阻力来自预算、人手还是外部时间窗口，再确认现有资源能否支撑改变，最后划定哪些步骤必须推进、哪些风险不能触碰。这样行动既不空喊口号，也不盲目冒进。",
            )
        }
        val generator = RecordingBookAiFallbackGenerator(aiDrafts)
        val result = selectWeReadOrAiViewpoints(
            book = BookSearchResult(title = "实践论", author = "毛泽东"),
            info = WeReadBookInfo(bookId = "1", title = "实践论", author = "毛泽东", intro = "认识来自实践，并回到实践中接受检验。"),
            chapters = (1..10).map { WeReadChapter(chapterUid = it, chapterIdx = it, title = "第 $it 章", wordCount = 1000) },
            reviews = listOf(WeReadReview(content = "这本书强调从具体矛盾中形成判断。")),
            aiFallbackGenerator = generator,
        )

        assertEquals(BookViewpointSource.AiFallback, result.source)
        assertEquals(aiDrafts, result.drafts)
        assertEquals("实践论", generator.info?.title)
        assertEquals(10, generator.chapters.size)
        assertEquals(1, generator.reviews.size)
    }

    @Test
    fun aiFallbackPromptRequiresRiskConditionBoundaryAndDirectStories() {
        val prompt = buildAiFallbackViewpointPrompt(
            book = BookSearchResult(title = "实践论", author = "毛泽东", category = "哲学"),
            info = WeReadBookInfo(bookId = "1", title = "实践论", author = "毛泽东", intro = "认识来自实践。"),
            chapters = listOf(WeReadChapter(chapterUid = 1, chapterIdx = 1, title = "实践与认识")),
            reviews = listOf(WeReadReview(content = "强调具体问题具体分析。")),
        )

        assertTrue(prompt.contains("风险"))
        assertTrue(prompt.contains("条件"))
        assertTrue(prompt.contains("边界"))
        assertTrue(prompt.contains("直接讲故事"))
        assertTrue(prompt.contains("不要写“在某某书中”“书中情境”"))
    }

    @Test
    fun doubanSubjectDetailFetchUsesShortTimeout() {
        assertTrue(doubanSubjectDetailTimeoutMs() <= 3_000L)
    }

    private class FakeBookIntelligenceSource(
        private val searchResults: List<BookSearchResult> = emptyList(),
        private val viewpoints: BookViewpointGenerationResult = BookViewpointGenerationResult(emptyList(), BookViewpointSource.WeRead),
    ) : BookIntelligenceSource {
        var searchQuery: String? = null
            private set
        var viewpointBook: BookSearchResult? = null
            private set

        override suspend fun searchBooks(query: String): List<BookSearchResult> {
            searchQuery = query
            return searchResults
        }

        override suspend fun generateViewpoints(book: BookSearchResult): BookViewpointGenerationResult {
            viewpointBook = book
            return viewpoints
        }
    }

    private class RecordingBookAiFallbackGenerator(
        private val result: List<BookViewpointDraft>,
    ) : BookAiFallbackGenerator {
        var info: WeReadBookInfo? = null
            private set
        var chapters: List<WeReadChapter> = emptyList()
            private set
        var reviews: List<WeReadReview> = emptyList()
            private set

        override suspend fun generate(
            book: BookSearchResult,
            info: WeReadBookInfo,
            chapters: List<WeReadChapter>,
            reviews: List<WeReadReview>,
        ): List<BookViewpointDraft> {
            this.info = info
            this.chapters = chapters
            this.reviews = reviews
            return result
        }
    }
}

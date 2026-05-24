package com.dailysatori.ui.feature.book

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookSearchUiTextTest {
    @Test
    fun primaryBookActionAddsAndAnalyzes() {
        assertEquals("添加并分析", bookSearchPrimaryActionText(isAnalyzing = false))
        assertEquals("分析中...", bookSearchPrimaryActionText(isAnalyzing = true))
    }

    @Test
    fun addBookSearchUsesSingleInputIconAndCompactAction() {
        assertEquals(false, bookAddSearchShowsTrailingSearchButton())
        assertEquals("添加并分析", compactBookAddActionText(isAnalyzing = false))
        assertEquals("分析中", compactBookAddActionText(isAnalyzing = true))
    }

    @Test
    fun bookSearchShowsTimeoutInsteadOfLoadingForever() {
        assertEquals("搜索超时，请换个关键词再试", bookSearchTimeoutMessage())
        kotlin.test.assertTrue(bookSearchTimeoutMs() >= 30_000L)
        kotlinx.coroutines.runBlocking {
            val error = try {
                kotlinx.coroutines.withTimeout(1) { kotlinx.coroutines.delay(10) }
                error("expected timeout")
            } catch (error: Exception) {
                error
            }
            assertEquals("搜索超时，请换个关键词再试", bookSearchFailureMessage(error))
        }
    }

    @Test
    fun mapsWeReadErrorsToRequiredMessages() {
        assertEquals(
            "请先在设置中配置微信读书 API Key",
            bookSearchFailureMessage(com.dailysatori.service.book.WeReadSkillException(
                com.dailysatori.service.book.WeReadSkillErrorType.MissingApiKey,
                "missing",
            )),
        )
        assertEquals(
            "微信读书未找到相关书籍",
            bookSearchFailureMessage(com.dailysatori.service.book.WeReadSkillException(
                com.dailysatori.service.book.WeReadSkillErrorType.NoResults,
                "none",
            )),
        )
        assertEquals(
            "微信读书服务调用失败，请稍后重试",
            bookSearchFailureMessage(com.dailysatori.service.book.WeReadSkillException(
                com.dailysatori.service.book.WeReadSkillErrorType.RemoteFailure,
                "remote",
            )),
        )
        assertEquals(
            "微信读书资料不足，请先配置默认 AI 模型后重试",
            bookSearchFailureMessage(com.dailysatori.service.book.WeReadSkillException(
                com.dailysatori.service.book.WeReadSkillErrorType.MissingAiFallbackConfig,
                "missing ai config",
            )),
        )
    }

    @Test
    fun candidatePromptPrefersChineseResultsForChineseQuery() {
        val prompt = buildChineseBookSearchInstruction("孔子")

        kotlin.test.assertTrue(prompt.contains("优先返回中文"))
        kotlin.test.assertTrue(prompt.contains("孔子"))
    }

    @Test
    fun hidesSearchResultsWhenQueryIsBlank() {
        val state = BookSearchState(
            query = "",
            results = listOf(
                com.dailysatori.service.book.BookSearchResult(
                    title = "原则",
                    author = "Ray Dalio",
                    category = "管理",
                    introduction = "",
                    coverUrl = "",
                    sourceSummary = "",
                ),
            ),
        )

        assertEquals(emptyList(), state.visibleResults)
    }

    @Test
    fun buildsSourceUrlFromWeReadBookResult() {
        val result = com.dailysatori.service.book.BookSearchResult(
            title = "三体",
            author = "刘慈欣",
            sourceUrl = "weread://reading?bId=3300045871",
        )

        assertEquals("weread://reading?bId=3300045871", bookSourceUrl(result))
    }

    @Test
    fun buildsWeReadSearchFallbackWhenSourceUrlIsBlank() {
        val result = com.dailysatori.service.book.BookSearchResult(title = "三体", author = "刘慈欣")

        assertEquals("https://weread.qq.com/web/search/books?keyword=%E4%B8%89%E4%BD%93+%E5%88%98%E6%85%88%E6%AC%A3", bookSourceUrl(result))
    }

    @Test
    fun addFailureAfterInsertDoesNotLeaveEmptyBookSelected() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/book/BookSearchViewModel.kt").readText()

        assertTrue(source.contains("bookRepo.delete(bookId)"))
        assertTrue(source.contains("bookAnalysisFailureError(error)"))
        assertTrue(source.contains("error = visibleError"))
    }

    @Test
    fun sourceOpenFailureHasUserFacingMessage() {
        assertEquals("无法打开微信读书，请确认已安装微信读书", bookSourceOpenFailureMessage())
    }

    @Test
    fun bookResultActionsUseIconsWithAccessibleLabels() {
        assertEquals("打开微信读书介绍", bookResultSourceActionDescription())
        assertEquals("添加并分析", bookResultAddActionDescription())
        assertEquals("重新搜索", bookSearchRetryActionText())
        assertEquals(true, bookResultActionsUseBottomRow())
    }
}

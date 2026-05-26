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
            "请先在 Skills 中配置微信读书 Token",
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
        assertEquals(
            "AI 观点生成失败，请稍后重试",
            bookAnalysisFailureError(com.dailysatori.service.book.WeReadSkillException(
                com.dailysatori.service.book.WeReadSkillErrorType.AiFallbackFailure,
                "AI 观点生成失败，请稍后重试",
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
    fun appendsAiGeneratedDisclosureForFallbackViewpoints() {
        assertEquals("基于微信读书资料由 AI 生成", bookAiGeneratedDisclosure())
        assertEquals(
            "《待上架新书》已添加，10 个观点已生成（基于微信读书资料由 AI 生成）",
            bookAnalysisCompletionNotice("待上架新书", 10, com.dailysatori.service.book.BookViewpointSource.AiFallback),
        )
        assertEquals(
            "《三体》已添加，10 个观点已生成",
            bookAnalysisCompletionNotice("三体", 10, com.dailysatori.service.book.BookViewpointSource.WeRead),
        )
    }

    @Test
    fun completionNoticeMentionsFailedViewpointsCanRetry() {
        assertEquals(
            "《实践论》已添加，7 个观点已生成，3 个可在阅读页重试（基于微信读书资料由 AI 生成）",
            bookAnalysisCompletionNotice(
                title = "实践论",
                count = 7,
                source = com.dailysatori.service.book.BookViewpointSource.AiFallback,
                failedCount = 3,
            ),
        )
    }

    @Test
    fun bookResultActionsUseIconsWithAccessibleLabels() {
        assertEquals("打开微信读书介绍", bookResultSourceActionDescription())
        assertEquals("添加并分析", bookResultAddActionDescription())
        assertEquals("重新搜索", bookSearchRetryActionText())
        assertEquals(true, bookResultActionsUseBottomRow())
    }

    @Test
    fun bookPickerSheetUsesScrollableListWithSafeBottomPadding() {
        assertEquals(true, bookPickerUsesLazyList())
        assertTrue(bookPickerBottomPaddingDp() >= 32)
    }

    @Test
    fun addBookResultPrioritizesIntroductionAndClearPrimaryAction() {
        assertTrue(bookResultIntroductionPreviewLength() >= 160)
        assertEquals("添加并分析", bookResultPrimaryActionText(isAnalyzing = false))
        assertEquals("分析中", bookResultPrimaryActionText(isAnalyzing = true))
        assertEquals(true, bookResultActionsUseBottomRow())
        assertEquals("微信读书", bookResultSourceActionText())
    }

    @Test
    fun importedBookViewpointsAreCappedAtTwenty() {
        assertEquals(20, bookViewpointImportLimit())
        assertEquals(20, bookViewpointDraftsForImport((1..25).toList()).size)
        assertEquals(7, bookViewpointDraftsForImport((1..7).toList()).size)
    }
}

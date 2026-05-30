package com.dailysatori.service.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

class McpAgentPresentationTest {
    @Test
    fun buildsFallbackAnswerFromArticleResultsWhenAiFinalAnswerFails() {
        val answer = buildFallbackAnswer(
            query = "这两天有什么重要新闻",
            listOf(
                McpSearchResult(1, "article", "新闻一", "摘要一", "2026-05-02", isFavorite = true),
                McpSearchResult(2, "article", "新闻二", null, "2026-05-01"),
                McpSearchResult(3, "article", "新闻三", "摘要三", null),
            ),
        )

        assertTrue(answer.contains("找到 3 条相关内容"))
        assertTrue(answer.contains("## 结论"))
        assertTrue(answer.contains("## 重点内容"))
        assertTrue(answer.contains("## 可继续查看"))
        assertTrue(answer.contains("新闻一"))
        assertTrue(answer.contains("新闻二"))
        assertFalse(answer.contains("AI 请求失败"))
    }

    @Test
    fun answersSecondLatestDiaryDateFromFallbackResults() {
        val answer = buildFallbackAnswer(
            query = "倒数第二天的日记是哪一天的",
            results = listOf(
                McpSearchResult(11, "diary", "2026年05月03日的日记", "最新日记", "2026-05-03"),
                McpSearchResult(10, "diary", "2026年05月02日的日记", "倒数第二篇日记", "2026-05-02"),
            ),
        )

        assertTrue(answer.contains("2026-05-02"))
        assertTrue(answer.contains("倒数第二"))
        assertFalse(answer.contains("AI 总结暂时失败"))
    }

    @Test
    fun detectsSecondLatestDiaryQueries() {
        assertEquals(1, orderedDiaryIndexFromQuery("我倒数第二近的日记是什么"))
        assertEquals(1, orderedDiaryIndexFromQuery("第二近的日记是什么"))
        assertEquals(2, orderedDiaryIndexFromQuery("倒数第三篇日记是什么"))
        assertEquals(null, orderedDiaryIndexFromQuery("最近的文章是什么"))
    }

    @Test
    fun reducesOrderedDiaryReferencesToOneResult() {
        val results = listOf(
            McpSearchResult(11, "diary", "最新日记", "one", "2026-05-03"),
            McpSearchResult(10, "diary", "倒数第二篇", "two", "2026-05-02"),
            McpSearchResult(9, "diary", "倒数第三篇", "three", "2026-05-01"),
        )

        assertEquals(listOf(results[1]), preciseSearchResultsForQuery("倒数第二近的日记", results))
    }

    @Test
    fun mapsSearchResultTypesToReadableLabels() {
        assertEquals("文章", searchResultTypeLabel("article"))
        assertEquals("日记", searchResultTypeLabel("diary"))
        assertEquals("书籍", searchResultTypeLabel("book"))
        assertEquals("读书笔记", searchResultTypeLabel("book_viewpoint"))
        assertEquals("内容", searchResultTypeLabel("unknown"))
    }

    @Test
    fun mapsBookViewpointSearchResultToReadableLabelAndOpenTarget() {
        assertEquals("读书笔记", searchResultTypeLabel("book_viewpoint"))
        assertEquals(true, canOpenSearchResult("book_viewpoint"))
        assertEquals(SearchResultOpenTarget.BookViewpoint, searchResultOpenTarget("book_viewpoint"))
    }

    @Test
    fun fallsBackToRankedReferencesWhenAiRefsAreMissingOrInvalid() {
        val ranked = listOf(
            McpSearchResult(1, "article", "文章", "摘要", "2026-05-30"),
            McpSearchResult(2, "diary", "日记", "片段", "2026-05-29"),
        )

        assertEquals(ranked, referencesForAnswer("没有 refs", ranked))
        assertEquals(ranked, referencesForAnswer("回答\n<!-- refs: article_999 -->", ranked))
        assertEquals(listOf(ranked[0]), referencesForAnswer("回答\n<!-- refs: article_1 -->", ranked))
    }

    @Test
    fun invalidRefsCanFallBackToAllCollectedOpenableResults() {
        val localOnly = listOf(McpSearchResult(1, "diary", "本地日记", "片段", "2026-05-29"))
        val collected = localOnly + McpSearchResult(7, "article", "工具文章", "摘要", "2026-05-30")

        assertEquals(collected, referencesForAnswer("回答\n<!-- refs: article_999 -->", localOnly, collected))
    }

    @Test
    fun articleDiaryAndBookSearchResultsCanOpenDetails() {
        assertEquals(true, canOpenSearchResult("article"))
        assertEquals(true, canOpenSearchResult("diary"))
        assertEquals(true, canOpenSearchResult("book"))
        assertEquals(true, canOpenSearchResult("book_viewpoint"))
        assertEquals(false, canOpenSearchResult("unknown"))
    }

    @Test
    fun mapsSearchResultsToOpenTargets() {
        assertEquals(SearchResultOpenTarget.Article, searchResultOpenTarget("article"))
        assertEquals(SearchResultOpenTarget.Diary, searchResultOpenTarget("diary"))
        assertEquals(SearchResultOpenTarget.Book, searchResultOpenTarget("book"))
        assertEquals(SearchResultOpenTarget.BookViewpoint, searchResultOpenTarget("book_viewpoint"))
        assertEquals(null, searchResultOpenTarget("unknown"))
    }

    @Test
    fun retriesAiSummaryThreeTimesBeforeFallback() {
        assertEquals(listOf(1, 2, 3), aiSummaryRetryAttempts())
    }

    @Test
    fun assistantToolMessagePreservesReasoningContent() {
        val message = buildJsonObject {
            put("role", JsonPrimitive("assistant"))
            put("content", JsonPrimitive(""))
            put("reasoning_content", JsonPrimitive("thinking trace"))
            put("tool_calls", buildJsonArray {
                add(buildJsonObject {
                    put("id", JsonPrimitive("call_1"))
                    put("type", JsonPrimitive("function"))
                    put("function", buildJsonObject {
                        put("name", JsonPrimitive("query_local_database"))
                        put("arguments", JsonPrimitive("{}"))
                    })
                })
            })
        }

        val assistantMessage = buildAssistantToolMessage(message)

        assertEquals("thinking trace", assistantMessage["reasoning_content"]?.jsonPrimitive?.content)
    }

    @Test
    fun mcpAgentUsesAiSearchOrchestratorBeforeToolLoop() {
        val service = java.io.File("src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentService.kt").readText()
        val di = java.io.File("src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt").readText()

        assertTrue(service.contains("private val aiSearchOrchestrator: AiSearchOrchestrator"))
        assertTrue(service.contains("val localSearch = aiSearchOrchestrator.search(query)"))
        assertTrue(service.contains("referencesForAnswer(answerForRefs, referenceBase, collectedResults)"))
        assertTrue(di.contains("single { AiSearchOrchestrator(get(), get(), get(), get(), get()) }"))
        assertTrue(di.contains("McpAgentService(get(), get(), get(), get())"))
    }
}

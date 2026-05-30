package com.dailysatori.service.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiSearchOrchestratorTest {
    @Test
    fun detectsSearchIntentsFromChineseQuery() {
        val diary = analyzeAiSearchQuery("我之前写过焦虑的日记吗")
        assertTrue(diary.searchMemory)
        assertTrue(diary.searchDiaries)
        assertFalse(diary.searchArticles)

        val article = analyzeAiSearchQuery("我收藏过哪些 AI 文章")
        assertTrue(article.searchMemory)
        assertTrue(article.searchArticles)

        val book = analyzeAiSearchQuery("读书笔记里有没有长期主义")
        assertTrue(book.searchBooks)
        assertTrue(book.searchBookViewpoints)
    }

    @Test
    fun genericRecallSearchesAllLocalContent() {
        val plan = analyzeAiSearchQuery("我之前有没有提过工作节奏")
        assertTrue(plan.searchMemory)
        assertTrue(plan.searchDiaries)
        assertTrue(plan.searchArticles)
        assertTrue(plan.searchBooks)
        assertTrue(plan.searchBookViewpoints)
    }

    @Test
    fun extractsUsefulKeywordsAndDropsFillers() {
        val keywords = extractAiSearchKeywords("帮我找一下我之前有没有写过对工作节奏焦虑的日记")
        assertTrue("工作节奏" in keywords || "工作" in keywords)
        assertTrue("焦虑" in keywords)
        assertFalse("帮我" in keywords)
        assertTrue(keywords.size in 2..5)
    }

    @Test
    fun detectsSimpleTimeIntent() {
        assertEquals(AiSearchTimeIntent.RecentDays(7), detectAiSearchTimeIntent("最近一周我写过什么"))
        assertEquals(AiSearchTimeIntent.Month("2026-05"), detectAiSearchTimeIntent("2026-05 的日记"))
        assertEquals(AiSearchTimeIntent.Date("2026-05-30"), detectAiSearchTimeIntent("2026-05-30 写了什么"))
    }

    @Test
    fun ranksIntentTitleFavoriteAndRecentMatchesHigher() {
        val oldArticle = AiSearchEvidence(
            result = McpSearchResult(1, "article", "普通文章", "AI", "2020-01-01"),
            searchableText = "AI",
        )
        val favoriteTitle = AiSearchEvidence(
            result = McpSearchResult(2, "article", "AI 搜索文章", "摘要", "2026-05-30", isFavorite = true),
            searchableText = "AI 搜索文章 摘要",
        )

        val ranked = rankAiSearchEvidence(
            evidence = listOf(oldArticle, favoriteTitle),
            keywords = listOf("AI"),
            primaryTypes = setOf("article"),
            nowDate = "2026-05-30",
        )

        assertEquals(2L, ranked.first().result.id)
        assertTrue(ranked.first().result.matchReason.orEmpty().contains("AI"))
    }

    @Test
    fun buildsEvidencePromptWithSufficiencyAndOpenableRefsOnly() {
        val evidence = listOf(
            AiSearchEvidence(McpSearchResult(1, "article", "AI 文章", "摘要", "2026-05-30", matchReason = "命中：AI"), "AI 摘要"),
            AiSearchEvidence(McpSearchResult(2, "core_memory", "偏好", "喜欢结构化", null, matchReason = "命中：结构化"), "喜欢结构化", evidenceOnly = true),
        )

        val prompt = buildAiSearchEvidencePrompt("我收藏过哪些 AI 文章", evidence)

        assertTrue(prompt.contains("用户问题：我收藏过哪些 AI 文章"))
        assertTrue(prompt.contains("证据充足度：少量相关记录"))
        assertTrue(prompt.contains("[article_1]"))
        assertTrue(prompt.contains("[core_memory_2]"))
        assertTrue(prompt.contains("只能基于上述证据回答"))
    }

    @Test
    fun fallbackAnswerMentionsCountTypesTopMatchesAndSparseEvidence() {
        val answer = buildAiSearchFallbackAnswer(
            query = "我之前写过焦虑吗",
            rankedResults = listOf(McpSearchResult(1, "diary", "日记", "片段", "2026-05-30", matchReason = "命中：焦虑")),
        )

        assertTrue(answer.contains("找到 1 条相关内容"))
        assertTrue(answer.contains("我只找到少量相关记录"))
        assertTrue(answer.contains("日记"))
        assertTrue(answer.contains("命中：焦虑"))
    }
}

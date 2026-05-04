package com.dailysatori.service.book

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookIntelligenceServiceTest {
    @Test
    fun parsesBookCandidatesFromJsonArray() {
        val json = """
            [
              {"title":"穷查理宝典","author":"查理·芒格","category":"投资","introduction":"芒格思想合集","isbn":"","coverUrl":"https://img.example/a.jpg","sourceSummary":"来自公开书评"},
              {"title":"","author":"无效"}
            ]
        """.trimIndent()

        val results = parseBookCandidateJson(json)

        assertEquals(1, results.size)
        assertEquals("穷查理宝典", results.first().title)
        assertEquals("查理·芒格", results.first().author)
        assertEquals("投资", results.first().category)
        assertEquals("来自公开书评", results.first().sourceSummary)
    }

    @Test
    fun parsesBookCandidatesFromFencedJson() {
        val json = """
            ```json
            [{"title":"原则","author":"Ray Dalio","introduction":"原则说明"}]
            ```
        """.trimIndent()

        val results = parseBookCandidateJson(json)

        assertEquals(1, results.size)
        assertEquals("原则", results.first().title)
    }

    @Test
    fun parsesValidViewpointDraftsOnly() {
        val json = """
            [
              {"title":"复利来自长期主义","content":"长期投入会放大优势。","example":"持续阅读和复盘会积累判断力。"},
              {"title":"缺少解释","content":"","example":"无效"},
              {"title":"缺少案例","content":"缺字段"},
              "not-an-object"
            ]
        """.trimIndent()

        val drafts = parseBookViewpointJson(json)

        assertEquals(1, drafts.size)
        assertEquals("复利来自长期主义", drafts.first().title)
        assertEquals("长期投入会放大优势。", drafts.first().content)
        assertEquals("持续阅读和复盘会积累判断力。", drafts.first().example)
    }

    @Test
    fun viewpointPromptRequiresTenStructuredCards() {
        val prompt = buildBookViewpointPrompt(
            title = "穷查理宝典",
            author = "查理·芒格",
            introduction = "投资与人生智慧",
            sourceNotes = "公开资料摘要",
        )

        assertTrue(prompt.contains("10"))
        assertTrue(prompt.contains("title"))
        assertTrue(prompt.contains("content"))
        assertTrue(prompt.contains("example"))
        assertTrue(prompt.contains("只返回 JSON 数组"))
    }

    @Test
    fun androidDoesNotUseLocalCommandMcpAsCallableSource() {
        assertFalse(isAndroidCallableMcpSource("local"))
        assertFalse(isAndroidCallableMcpSource("stdio"))
        assertTrue(isAndroidCallableMcpSource("remote"))
        assertTrue(isAndroidCallableMcpSource("http"))
    }
}

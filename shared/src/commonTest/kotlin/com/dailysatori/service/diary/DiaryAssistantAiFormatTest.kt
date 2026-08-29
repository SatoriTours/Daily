package com.dailysatori.service.diary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiaryAssistantAiFormatTest {
    @Test
    fun parserCapsAndDeduplicatesSources() {
        val parsed = parseDiaryAssistantAiResponse(
            """{"content":"背景说明","sources":[{"title":"A","url":"https://a.example"},{"title":"A2","url":"https://a.example"},{"title":"B","url":"https://b.example"},{"title":"C","url":"https://c.example"},{"title":"D","url":"https://d.example"}]}""",
            emptyList(),
        )
        assertEquals(3, parsed.sources.size)
        assertEquals(listOf("https://a.example", "https://b.example", "https://c.example"), parsed.sources.map { it.url })
    }

    @Test
    fun parserStripsCodeFenceAndFallsBackSafely() {
        val parsed = parseDiaryAssistantAiResponse(
            "```json\n{\"content\":\"补充\",\"sources\":[{\"title\":\"资料\",\"url\":\"ftp://bad\"}]}\n```",
            listOf(DiaryAssistantSource("搜索", "https://search.example")),
        )
        assertEquals("补充", parsed.content)
        assertEquals(listOf("https://search.example"), parsed.sources.map { it.url })

        val text = parseDiaryAssistantAiResponse("普通回答", listOf(DiaryAssistantSource("搜索", "https://search.example")))
        assertEquals("普通回答", text.content)
        assertEquals(listOf("https://search.example"), text.sources.map { it.url })
    }

    @Test
    fun promptsContainSelectionAndBoundedContextOnly() {
        val unrelatedFixture = "整篇日记中不应发送的内容"
        val knowledge = buildDiaryKnowledgePrompt("选中的文字", "前文", "后文")
        val link = buildDiaryLinkSummaryPrompt("选中的文字", "https://example.com", "前文", "后文")
        assertTrue(knowledge.contains("选中的文字"))
        assertTrue(knowledge.contains("前文") && knowledge.contains("后文"))
        assertTrue(link.contains("选中的文字") && link.contains("https://example.com"))
        assertFalse(knowledge.contains(unrelatedFixture))
        assertFalse(link.contains(unrelatedFixture))
    }

    @Test
    fun markdownAppendsCompactSources() {
        assertEquals(
            "背景说明\n\n来源：[资料 A](https://a.example)",
            renderDiaryAssistantMarkdown("背景说明", listOf(DiaryAssistantSource("资料 A", "https://a.example"))),
        )
    }

    @Test
    fun markdownEscapesReservedSourceFieldsAndRejectsUnsafeUrls() {
        val sources = listOf(
            DiaryAssistantSource("[伪链接](javascript:bad)", "https://example.com/path)"),
            DiaryAssistantSource("无主机", "https://"),
            DiaryAssistantSource("含空格", "https://not valid"),
            DiaryAssistantSource("有效", "https://valid.example/path"),
        )

        val markdown = renderDiaryAssistantMarkdown("正文", sources)

        assertEquals(
            "正文\n\n来源：[\\[伪链接\\]\\(javascript:bad\\)](https://example.com/path\\))、[有效](https://valid.example/path)",
            markdown,
        )
        assertEquals(2, markdown.split("](").size - 1)
    }
}

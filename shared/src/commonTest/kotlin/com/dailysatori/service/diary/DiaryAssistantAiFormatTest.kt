package com.dailysatori.service.diary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
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
    fun parserStripsCodeFenceFallsBackSourcesAndRejectsPlainText() {
        val parsed = parseDiaryAssistantAiResponse(
            "```json\n{\"content\":\"补充\",\"sources\":[{\"title\":\"资料\",\"url\":\"ftp://bad\"}]}\n```",
            listOf(DiaryAssistantSource("搜索", "https://search.example")),
        )
        assertEquals("补充", parsed.content)
        assertEquals(listOf("https://search.example"), parsed.sources.map { it.url })

        assertFailsWith<DiaryAssistantInvalidResponseException> {
            parseDiaryAssistantAiResponse("普通回答", listOf(DiaryAssistantSource("搜索", "https://search.example")))
        }
    }

    @Test
    fun knowledgePromptContainsSelectionAndBoundedContextOnly() {
        val unrelatedFixture = "整篇日记中不应发送的内容"
        val knowledge = buildDiaryKnowledgePrompt("选中的文字", "前文", "后文")
        assertTrue(knowledge.contains("选中的文字"))
        assertTrue(knowledge.contains("前文") && knowledge.contains("后文"))
        assertFalse(knowledge.contains(unrelatedFixture))
    }

    @Test
    fun markdownAppendsCompactSources() {
        assertEquals(
            "背景说明\n\n来源：[资料 A](<https://a.example>)",
            renderDiaryAssistantMarkdown("背景说明", listOf(DiaryAssistantSource("资料 A", "https://a.example"))),
        )
    }

    @Test
    fun markdownEscapesReservedSourceFieldsAndRejectsUnsafeUrls() {
        val sources = listOf(
            DiaryAssistantSource("[伪链接](javascript:bad)", "https://example.com/path)"),
            DiaryAssistantSource("用户信息", "https://user:secret@example.com/private"),
            DiaryAssistantSource("无主机", "https://"),
            DiaryAssistantSource("含空格", "https://not valid"),
            DiaryAssistantSource("有效", "https://valid.example/path"),
        )

        val markdown = renderDiaryAssistantMarkdown("正文", sources)

        assertEquals(
            "正文\n\n来源：[\\[伪链接\\]\\(javascript:bad\\)](<https://example.com/path)>)、[有效](<https://valid.example/path>)",
            markdown,
        )
        assertEquals(2, markdown.split("](").size - 1)
    }

    @Test
    fun markdownRendersEncodedUrlInsideAnAngleBracketDestination() {
        val markdown = renderDiaryAssistantMarkdown(
            "正文",
            listOf(DiaryAssistantSource("编码地址", "https://example.com/CasePath/a>b?q=encoded%20value")),
        )

        assertEquals(
            "正文\n\n来源：[编码地址](<https://example.com/CasePath/a%3Eb?q=encoded%20value>)",
            markdown,
        )
    }

    @Test
    fun markdownRemovesEveryModelGeneratedLinkFormFromBody() {
        val markdown = renderDiaryAssistantMarkdown(
            "普通 [脚本](javascript:alert(1)) [相对链接](//evil.example/path) [引用][bad] " +
                "<https://auto.example> <ftp://files.example/a> <alice@example.com> " +
                "mailto:bob@example.com www.unverified.example。\n[bad]: https://reference.example",
            emptyList(),
        )

        assertFalse(markdown.contains("javascript:"))
        assertFalse(markdown.contains("//evil.example"))
        assertFalse(markdown.contains("auto.example"))
        assertFalse(markdown.contains("reference.example"))
        assertFalse(markdown.contains("files.example"))
        assertFalse(markdown.contains("@example.com"))
        assertFalse(markdown.contains("www."))
        assertFalse(markdown.contains("]("))
        assertFalse(markdown.contains("]["))
        assertTrue(markdown.contains("脚本 相对链接 引用"))
        assertTrue(markdown.endsWith("。"))
        assertFalse(markdown.contains("脚本)"))
    }

    @Test
    fun markdownSanitizerHandlesNestedLabelsWithoutRemovingOrdinaryColonText() {
        val markdown = renderDiaryAssistantMarkdown(
            "[外层 [内层]](//evil.example/path) version:2.0 Note:important C:\\notes " +
                "data:point tel:extension geo:37.422,-122.084 sms:+15551234 " +
                "[多行\n标签](//multiline.example/path) [转义引用][ref\\]name] " +
                "[带标题](//quoted.example/path \"标题 (\") " +
                "（https://bad.example/path）\n[ref\\]name]: //reference-escape.example/path",
            emptyList(),
        )

        assertEquals(
            "外层 [内层] version:2.0 Note:important C:\\notes " +
                "data:point tel:extension geo:37.422,-122.084 sms:+15551234 多行\n标签 转义引用 带标题 （）",
            markdown,
        )
        assertFalse(markdown.contains("evil.example"))
        assertFalse(markdown.contains("bad.example"))
        assertFalse(markdown.contains("multiline.example"))
        assertFalse(markdown.contains("reference-escape.example"))
        assertFalse(markdown.contains("quoted.example"))
    }
}

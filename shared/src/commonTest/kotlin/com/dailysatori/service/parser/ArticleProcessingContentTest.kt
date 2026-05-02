package com.dailysatori.service.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArticleProcessingContentTest {
    @Test
    fun trimsAiConfigValuesBeforeSendingRequests() {
        val normalized = normalizeAiConfigValues(
            apiAddress = " https://api.example.com/\n",
            apiToken = "sk-test\n",
            modelName = " gpt-test ",
            provider = " openai ",
        )

        assertEquals("https://api.example.com", normalized.apiAddress)
        assertEquals("sk-test", normalized.apiToken)
        assertEquals("gpt-test", normalized.modelName)
        assertEquals("openai", normalized.provider)
    }

    @Test
    fun preservesExistingContentWhenGeneratedOutputIsBlank() {
        assertEquals("old summary", generatedOrExisting("", "old summary", "summary"))
        assertEquals("old markdown", generatedOrExisting("   ", "old markdown", "markdown"))
    }

    @Test
    fun rejectsBlankGeneratedOutputWhenNoExistingContentExists() {
        assertFailsWith<IllegalStateException> {
            generatedOrExisting("", null, "summary")
        }
    }

    @Test
    fun articleSummaryPromptRequiresStructuredConciseAnalysis() {
        val prompt = articleSummaryPrompt()

        assertEquals(true, prompt.contains("# 标题"))
        assertEquals(true, prompt.contains("## 核心内容"))
        assertEquals(true, prompt.contains("## 核心观点"))
        assertEquals(true, prompt.contains("15-25 字"))
        assertEquals(true, prompt.contains("50-80 字"))
        assertEquals(true, prompt.contains("2-5 个"))
        assertEquals(true, prompt.contains("越少越好"))
        assertEquals(true, prompt.contains("不要使用代码块包裹"))
    }

    @Test
    fun htmlToReadableMarkdownPromptMatchesReferenceStyle() {
        val prompt = htmlToReadableMarkdownPrompt()

        assertEquals(true, prompt.contains("专业的技术文档排版专家"))
        assertEquals(true, prompt.contains("GitHub Markdown"))
        assertEquals(true, prompt.contains("严格保持原文主要内容"))
        assertEquals(true, prompt.contains("删除无关元素"))
        assertEquals(true, prompt.contains("不保留原生 HTML 标签"))
        assertEquals(true, prompt.contains("不输出摘要"))
        assertEquals(true, prompt.contains("返回内容翻译成流畅的中文"))
    }
}

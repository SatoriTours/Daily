package com.dailysatori.service.book

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookReflectionServiceTest {
    @Test
    fun userPromptIncludesViewpointContextAndExistingSummaries() {
        val prompt = buildBookReflectionUserPrompt(
            bookTitle = "反脆弱",
            author = "塔勒布",
            viewpointTitle = "压力让系统暴露真实结构",
            viewpointContent = "系统在压力下会显露隐藏脆弱点。",
            viewpointExample = "健身通过负荷暴露身体短板。",
            existingSummaries = listOf("我理解到的核心：压力不是坏事。"),
            recentMessages = listOf(BookReflectionPromptMessage("user", "这个点和拖延有什么关系？")),
            userQuestion = "我还是不理解为什么压力有价值",
        )

        assertTrue(prompt.contains("书名：反脆弱"))
        assertTrue(prompt.contains("作者：塔勒布"))
        assertTrue(prompt.contains("当前观点标题：压力让系统暴露真实结构"))
        assertTrue(prompt.contains("系统在压力下会显露隐藏脆弱点。"))
        assertTrue(prompt.contains("健身通过负荷暴露身体短板。"))
        assertTrue(prompt.contains("我理解到的核心：压力不是坏事。"))
        assertTrue(prompt.contains("user：这个点和拖延有什么关系？"))
        assertTrue(prompt.contains("用户本次问题：我还是不理解为什么压力有价值"))
    }

    @Test
    fun summarySystemPromptRequiresFixedStructure() {
        val prompt = bookReflectionSummarySystemPrompt()

        assertTrue(prompt.contains("我理解到的核心："))
        assertTrue(prompt.contains("我补上的角度："))
        assertTrue(prompt.contains("还值得继续想的问题："))
    }

    @Test
    fun aiNotConfiguredMessageIsStable() {
        assertEquals("AI 服务未配置，请先在设置中配置 AI 接口", bookReflectionAiNotConfiguredMessage())
    }
}

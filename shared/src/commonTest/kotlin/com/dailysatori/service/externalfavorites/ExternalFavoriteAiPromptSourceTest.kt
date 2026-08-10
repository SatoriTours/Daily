package com.dailysatori.service.externalfavorites

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalFavoriteAiPromptSourceTest {
    @Test
    fun promptRequiresDirectContentVoiceInsteadOfThirdPersonArticleSummary() {
        val source = File("src/commonMain/kotlin/com/dailysatori/service/externalfavorites/ExternalFavoriteAiOrganizer.kt").readText()

        assertTrue(source.contains("直接输出内容本身"))
        assertTrue(source.contains("不要用第三方视角"))
        assertTrue(source.contains("禁止使用“本文"))
        assertTrue(source.contains("不要写“谁分享了"))
    }

    @Test
    fun promptUsesTheGeneralArticleChineseTranslationAndSummaryRules() {
        val prompt = externalFavoriteAnalysisPrompt()

        assertTrue(prompt.contains("中文翻译和内容整理"))
        assertTrue(prompt.contains("内容较长时必须包含简短总结和关键观点列表"))
        assertTrue(prompt.contains("将原文主要内容转换为中文 Markdown"))
        assertEquals(prompt, externalFavoriteAnalysisPrompt())
    }

    @Test
    fun githubEnglishResultIsRetriedUntilSummaryAndMarkdownAreChinese() {
        val input = ExternalFavoriteAiInput(
            provider = ExternalFavoriteProvider.GITHUB.id,
            title = "sample/repository",
            text = "This repository provides reliable automation tools for software development teams.",
            authorName = "sample",
            sourceCreatedAt = null,
            canonicalUrl = "https://github.com/sample/repository",
        )

        assertTrue(
            githubAnalysisNeedsChineseRetry(
                input,
                ExternalFavoriteAiAnalysis("English title", "This is still an English summary.", "The markdown remains entirely in English."),
            ),
        )
        assertFalse(
            githubAnalysisNeedsChineseRetry(
                input,
                ExternalFavoriteAiAnalysis("中文标题", "这是已经翻译完成的中文摘要内容。", "# 中文正文\n\n这是整理后的中文内容。"),
            ),
        )
    }
}

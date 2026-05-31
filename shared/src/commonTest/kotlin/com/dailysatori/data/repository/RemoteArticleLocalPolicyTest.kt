package com.dailysatori.data.repository

import com.dailysatori.service.remotenews.RemoteArticle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteArticleLocalPolicyTest {
    @Test
    fun cleanRemoteArticleTextTrimsBlankStringsToNull() {
        assertEquals("标题", cleanRemoteArticleText(" 标题 "))
        assertNull(cleanRemoteArticleText("   "))
        assertNull(cleanRemoteArticleText(null))
    }

    @Test
    fun remoteArticleViewpointMarkdownKeepsOnlyNonBlankPoints() {
        assertEquals(
            "## 关键观点\n\n- 观点 A\n- 观点 B",
            remoteArticleViewpointMarkdown(listOf(" 观点 A ", "", "  ", "观点 B")),
        )
        assertNull(remoteArticleViewpointMarkdown(listOf(" ", "")))
    }

    @Test
    fun remoteArticleLanguageSampleUsesTitleSummaryViewpointsAndContent() {
        val article = RemoteArticle(
            id = 1,
            title = "Title",
            summary = "Summary",
            viewpoints = listOf("Point A", "Point B"),
            content = "Body",
        )

        assertEquals("Title\nSummary\nPoint A\nPoint B\nBody", remoteArticleLanguageSample(article))
    }

    @Test
    fun remoteArticleLanguagePolicyKeepsChineseAndReprocessesEnglish() {
        val chinese = RemoteArticle(
            id = 2,
            url = "https://example.com/zh",
            title = "这是中文标题",
            summary = "这里有足够多的中文内容用于判断。",
            content = "更多中文内容确保超过阈值。",
        )
        val english = RemoteArticle(
            id = 3,
            url = "https://example.com/en",
            title = "OpenAI launches new coding model",
            summary = "The company announced a major update for developers and enterprise teams.",
            content = "The model improves reliability and long context reasoning for teams building software every day.",
        )

        assertTrue(hasEnoughChineseForLocalArticle(chinese))
        assertFalse(chinese.needsLocalAiReprocessingForChineseOutput())
        assertTrue(hasEnoughEnglishForLocalArticle(english))
        assertTrue(english.needsLocalAiReprocessingForChineseOutput())
    }
}

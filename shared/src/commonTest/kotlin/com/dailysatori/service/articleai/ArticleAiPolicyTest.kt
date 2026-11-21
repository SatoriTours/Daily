package com.dailysatori.service.articleai

import kotlin.test.*

class ArticleAiPolicyTest {
    @Test fun readingPlanUsesBodyLengthAndOnlyRequestsMissingResults() {
        assertNull(analyzeArticleBody("图".repeat(300)).automatic(false))
        assertEquals(ArticleAiMode.SUMMARY, analyzeArticleBody("图".repeat(301)).automatic(false))
        assertEquals(ArticleAiMode.TRANSLATION, analyzeArticleBody("book ".repeat(150)).automatic(false))
        assertEquals(ArticleAiMode.BOTH, analyzeArticleBody("book ".repeat(151)).automatic(false))
        assertEquals(ArticleAiMode.TRANSLATION, analyzeArticleBody("book ".repeat(151)).automatic(true))
    }

    @Test fun charactersAndWordsExcludeMarkupAndCode() {
        val text = "汉".repeat(98) + "，[新闻](https://example.com)！ `a b c`"
        assertEquals(100, analyzeArticleBody(text).units)
        assertNull(analyzeArticleBody(text).automatic(false))
        assertEquals(ArticleAiMode.SUMMARY, analyzeArticleBody(text + "新".repeat(201)).automatic(false))
    }

    @Test fun mixedJapaneseAndShortForeign() {
        assertEquals(ArticleLanguage.CHINESE, analyzeArticleBody("腾讯发布新的AI项目，使用 Go 和 React。").language)
        assertEquals(ArticleLanguage.FOREIGN, analyzeArticleBody("東京大学が新研究を発表しました。").language)
        assertEquals(ArticleLanguage.UNCERTAIN, analyzeArticleBody("国際交通情報").language)
        assertEquals(ArticleAiMode.TRANSLATION, analyzeArticleBody("Hello world!").automatic(false))
        assertEquals(ArticleAiMode.TRANSLATION, analyzeArticleBody("Café ouvert.").automatic(true))
        assertEquals(ArticleLanguage.UNCERTAIN, analyzeArticleBody("2026 🤔").language)
    }

    @Test fun recognizableKanjiOnlyJapaneseAndNonLatinScriptsAreNotChinese() {
        assertEquals(ArticleLanguage.FOREIGN, analyzeArticleBody("東京駅運転情報").language)
        assertEquals(ArticleLanguage.FOREIGN, analyzeArticleBody("한국의 최신 소식입니다.").language)
        assertEquals(ArticleLanguage.FOREIGN, analyzeArticleBody("هذه أخبار اليوم.").language)
        assertEquals(ArticleLanguage.UNCERTAIN, analyzeArticleBody("国際交通情報").language)
    }

    @Test fun declinesPreviewSummaryAndAccessError() {
        assertEquals(ArticleBodyKind.PREVIEW, analyzeArticleBody("New findings…", ArticleBodyKind.PREVIEW).kind)
        assertEquals(ArticleBodyKind.SOURCE_SUMMARY, analyzeArticleBody("一些消息", ArticleBodyKind.SOURCE_SUMMARY).kind)
        assertEquals(ArticleBodyKind.ERROR_PAGE, analyzeArticleBody("# Access denied\nPlease verify you are human").kind)
        assertEquals(ArticleBodyKind.MISSING, analyzeArticleBody(null).kind)
    }

    @Test fun requestedProcessingKeepsIncompleteKindButDerivesVersionData() {
        val preview = analyzeArticleBody("A short preview paragraph in English.", ArticleBodyKind.PREVIEW,
            analyzeIncomplete = true)
        assertEquals(ArticleBodyKind.PREVIEW, preview.kind)
        assertEquals(ArticleLanguage.FOREIGN, preview.language)
        assertTrue(preview.hash.isNotBlank() && preview.units > 0)
        assertTrue(preview.manualProcessable())
        assertNull(preview.automatic(hasSummary = false))
        assertFalse(analyzeArticleBody(" ", ArticleBodyKind.PREVIEW, analyzeIncomplete = true).manualProcessable())
    }

    @Test fun ignoresLayoutButNotLinkTargetsInFingerprint() {
        assertEquals(analyzeArticleBody("新闻  正文\n\n测试").hash, analyzeArticleBody("新闻 正文 测试").hash)
        assertNotEquals(analyzeArticleBody("[正文](https://a.com)").hash, analyzeArticleBody("[正文](https://b.com)").hash)
    }

    @Test fun budgetUsesCompletePrefix() {
        val body = ("这是完整句子。".repeat(100) + "\n\n").repeat(40)
        val input = articleAiInput(body, ArticleAiMode.BOTH, ArticleModelLimits(32_768, 8_192))!!
        assertTrue(input.partial && input.body.endsWith("。"))
        assertTrue(input.estimatedTokens <= 12_000 && body.startsWith(input.body))
        assertNull(articleAiInput("甲".repeat(5000), ArticleAiMode.BOTH, ArticleModelLimits(8_192, 4_096)))
    }
}

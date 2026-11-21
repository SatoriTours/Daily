package com.dailysatori.ui.feature.article

import com.dailysatori.data.repository.ArticleAiView
import com.dailysatori.service.articleai.ArticleBodyKind
import com.dailysatori.service.articleai.ArticleLanguage
import kotlin.test.*

class ArticleAiReadingTest {
    @Test fun pauseReasonsAreActionableAndUnexpectedCodesUseANeutralLabel() {
        assertEquals("paused_configuration", articleAiPauseKey("configuration"))
        assertEquals("paused_auth", articleAiPauseKey("http_401"))
        assertEquals("paused_auth", articleAiPauseKey("http_403"))
        assertEquals("paused_billing", articleAiPauseKey("http_402"))
        assertEquals("paused_rate", articleAiPauseKey("http_429"))
        assertEquals("queue_paused", articleAiPauseKey("unknown"))
    }

    @Test fun bodyAndSummaryRetainTheirOwnReadingNotes() {
        val loading = ArticleAiView(ArticleBodyKind.BODY, ArticleLanguage.FOREIGN, 20, null, null,
            false, false, false, "running", null, null, "")
        val completed = loading.copy(summary = "摘要", translation = "译文", translationPartial = true, jobState = "succeeded")
        assertTrue(loading.canRequestReading())
        assertEquals(emptyList(), articleReadingNotes(completed, ArticleReadMode.ORIGINAL))
        assertEquals(listOf("ai_translation", "partial"), articleReadingNotes(completed, ArticleReadMode.TRANSLATION))
        assertEquals(listOf("source_summary"), articleReadingNotes(completed.copy(sourceSummary = true), ArticleReadMode.SUMMARY))
    }
}

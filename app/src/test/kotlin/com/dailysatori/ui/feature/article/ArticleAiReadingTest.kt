package com.dailysatori.ui.feature.article

import com.dailysatori.data.repository.ArticleAiView
import com.dailysatori.service.articleai.ArticleAiMode
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

    @Test fun retryUsesSavedBodyAndOnlyForRecoverableFailures() {
        val events = mutableListOf<String>()
        val process: () -> Unit = { events += "fill missing" }
        val retranslate: () -> Unit = { events += "replace translation" }
        assertNotNull(savedReadingRetry("retry_saved_summary", process, retranslate)).invoke()
        assertNotNull(savedReadingRetry("retranslation_failed", process, retranslate)).invoke()
        for (key in listOf("paused_configuration", "paused_auth", "paused_rate", "fetch_failed")) {
            assertNull(savedReadingRetry(key, process, retranslate))
        }
        assertNull(savedReadingRetry("retry_saved_translation", null, null))

        val failed = ArticleAiView(ArticleBodyKind.BODY, ArticleLanguage.FOREIGN, 20, null, null,
            false, false, false, "failed", ArticleAiMode.TRANSLATION, null, "", shortBody = true)
        assertNotNull(remoteReadingRetry(failed, false, process, retranslate)).invoke()
        assertNotNull(remoteReadingRetry(failed.copy(translation = "已有译文"), false, process, retranslate)).invoke()
        assertNull(remoteReadingRetry(failed.copy(blocked = "configuration"), false, process, retranslate))
        assertNull(remoteReadingRetry(failed.copy(jobState = "running"), false, process, retranslate))
        assertNull(remoteReadingRetry(failed, true, process, retranslate))
        assertEquals(listOf("fill missing", "replace translation", "fill missing", "replace translation"), events)
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

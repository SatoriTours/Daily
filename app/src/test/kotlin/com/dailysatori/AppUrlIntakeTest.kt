package com.dailysatori

import androidx.work.OutOfQuotaPolicy
import com.dailysatori.core.worker.buildArticleSaveWorkRequest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppUrlIntakeTest {
    @Test
    fun extractsFirstHttpUrlFromSharedText() {
        assertEquals(
            "https://example.com/a?b=1",
            extractFirstUrl("看看这个链接 https://example.com/a?b=1 很有意思"),
        )
    }

    @Test
    fun normalizesUrlForDuplicateComparison() {
        assertEquals("https://example.com/a", normalizeArticleUrl(" https://example.com/a/ "))
        assertEquals("http://example.com/a", normalizeArticleUrl("http://example.com/a"))
    }

    @Test
    fun detectsExistingUrlAfterNormalization() {
        assertEquals(
            true,
            articleUrlExists("https://example.com/a/", listOf("https://example.com/a")),
        )
        assertEquals(
            false,
            articleUrlExists("https://example.com/b", listOf("https://example.com/a")),
        )
    }

    @Test
    fun suppressesRepeatedClipboardUrlAfterUserDecision() {
        val state = ClipboardUrlPromptState()

        assertEquals(true, state.shouldPrompt("https://example.com/a"))
        state.markHandled("https://example.com/a")
        assertEquals(false, state.shouldPrompt("https://example.com/a/"))
        assertEquals(true, state.shouldPrompt("https://example.com/b"))
    }

    @Test
    fun skipsClipboardCheckWhenLaunchWasTriggeredByShareText() {
        assertEquals(false, shouldCheckClipboardOnForeground(launchedFromShare = true))
        assertEquals(true, shouldCheckClipboardOnForeground(launchedFromShare = false))
    }

    @Test
    fun suppressesNextClipboardCheckAfterShareHandling() {
        val state = ClipboardCheckGate()

        assertEquals(true, state.shouldCheck())
        state.suppressNextCheck()
        assertEquals(false, state.shouldCheck())
        assertEquals(true, state.shouldCheck())
    }

    @Test
    fun suppressesRepeatedReadsForSameClipboardContent() {
        val gate = ClipboardReadGate()

        assertEquals(true, gate.shouldRead(100L))
        gate.markRead(100L)
        assertEquals(false, gate.shouldRead(100L))
        assertEquals(true, gate.shouldRead(101L))
    }

    @Test
    fun visibleMessagesExplainClipboardPromptAndDuplicateSnackbar() {
        assertEquals("链接已存在", duplicateUrlSnackbarMessage())
        assertEquals("检测到剪切板链接", clipboardPromptTitle())
    }

    @Test
    fun shareReceiverMessagesUseToastText() {
        assertEquals("已开始保存文章", shareSaveStartedToastMessage())
        assertEquals("未找到链接", shareInvalidUrlToastMessage())
    }

    @Test
    fun shareSaveWorkRunsAsExpeditedUserInitiatedBackgroundWork() {
        val request = buildArticleSaveWorkRequest(
            url = "https://example.com/a",
            normalizedUrl = "https://example.com/a",
        )

        assertEquals(true, request.workSpec.expedited)
        assertEquals(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST, request.workSpec.outOfQuotaPolicy)
    }

    @Test
    fun articleProcessingWorkerDoesNotStartForegroundServiceInsideDoWork() {
        val source = File("src/main/kotlin/com/dailysatori/core/worker/ArticleProcessingWorker.kt").readText()

        assertTrue(source.contains("override suspend fun getForegroundInfo(): ForegroundInfo"))
        assertFalse(source.contains("setForeground(createForegroundInfo())"))
    }

    @Test
    fun webViewLoaderReturnsVisibleTextAndHtmlSnapshot() {
        val common = File("../shared/src/commonMain/kotlin/com/dailysatori/platform/WebViewLoader.kt").readText()
        val android = File("../shared/src/androidMain/kotlin/com/dailysatori/platform/WebViewLoader.android.kt").readText()

        assertTrue(common.contains("data class WebViewPageContent"))
        assertTrue(common.contains("Result<WebViewPageContent>"))
        assertTrue(android.contains("document.body.innerText"))
        assertTrue(android.contains("document.documentElement.outerHTML"))
    }

    @Test
    fun webViewLoaderWaitsForStableInnerHtmlBeforeCompleting() {
        val source = File("../shared/src/androidMain/kotlin/com/dailysatori/platform/WebViewLoader.android.kt").readText()

        assertTrue(source.contains("document.documentElement.innerHTML"))
        assertTrue(source.contains("stableHtml"))
        assertTrue(source.contains("stableReadCount >= 2"))
        assertTrue(source.contains("lastPageContent"))
    }

    @Test
    fun webViewLoaderUsesJsonDecoderForEvaluateJavascriptResult() {
        val source = File("../shared/src/androidMain/kotlin/com/dailysatori/platform/WebViewLoader.android.kt").readText()

        assertTrue(source.contains("JSONTokener"))
        assertFalse(source.contains("replace(\"\\\\n\", \"\\n\")"))
    }

    @Test
    fun webViewLoadingCanBeCancelledByCoroutineCaller() {
        val common = File("../shared/src/commonMain/kotlin/com/dailysatori/platform/WebViewLoader.kt").readText()
        val parser = File("../shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt").readText()
        val android = File("../shared/src/androidMain/kotlin/com/dailysatori/platform/WebViewLoader.android.kt").readText()

        assertTrue(common.contains("expect class WebViewLoadHandle"))
        assertTrue(parser.contains("suspendCancellableCoroutine"))
        assertTrue(parser.contains("invokeOnCancellation"))
        assertTrue(android.contains("actual fun cancel()"))
    }

    @Test
    fun articleProcessingWorkerRetriesWhenBackgroundWorkIsCancelled() {
        val source = File("src/main/kotlin/com/dailysatori/core/worker/ArticleProcessingWorker.kt").readText()

        assertTrue(source.contains("CancellationException"))
        assertTrue(source.contains("Result.retry()"))
    }

    @Test
    fun parserResumeModePropagatesCancellationToWorkerRetry() {
        val source = File("../shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt").readText()
        val resumeCatch = source.substringAfter("suspend fun resumeInterruptedProcessing")
            .substringBefore("private fun enqueueArticleProcessing")

        assertTrue(resumeCatch.contains("shouldPersistArticleProcessingError"))
    }

    @Test
    fun twitterExtractionDoesNotSwallowCoroutineCancellation() {
        val source = File("../shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt").readText()
        val twitterExtraction = source.substringAfter("private suspend fun extractTwitterContent")
            .substringBefore("suspend fun refreshArticle")

        assertFalse(twitterExtraction.contains("runCatching"))
    }

    @Test
    fun resumeQueryIncludesLegacyCancellationErrors() {
        val source = File("../shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()
        val query = source.substringAfter("selectRecoverableArticles:")
            .substringBefore("selectArticlesByTag:")

        assertTrue(query.contains("status = 'error'"))
        assertTrue(query.contains("cancelled"))
    }

    @Test
    fun scrollsToTopOnlyAfterArticleWasAdded() {
        assertEquals(false, shouldScrollToTopAfterArticleAdded(0))
        assertEquals(true, shouldScrollToTopAfterArticleAdded(1))
    }

    @Test
    fun countsNewLeadingArticlesAboveRememberedItem() {
        assertEquals(0, countNewLeadingArticles(listOf(10, 9, 8), rememberedTopArticleId = 10))
        assertEquals(2, countNewLeadingArticles(listOf(12, 11, 10, 9), rememberedTopArticleId = 10))
        assertEquals(0, countNewLeadingArticles(listOf(12, 11, 10, 9), rememberedTopArticleId = null))
        assertEquals(0, countNewLeadingArticles(listOf(12, 11, 10, 9), rememberedTopArticleId = 7))
    }

    @Test
    fun showsNewArticleIndicatorOnlyWhenUserIsAwayFromTop() {
        assertEquals(false, shouldShowNewArticlesIndicator(newCount = 0, isAtTop = false))
        assertEquals(false, shouldShowNewArticlesIndicator(newCount = 2, isAtTop = true))
        assertEquals(true, shouldShowNewArticlesIndicator(newCount = 2, isAtTop = false))
    }
}

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
        assertTrue(common.contains("readableContent"))
        assertTrue(common.contains("Result<WebViewPageContent>"))
        assertTrue(android.contains("document.body.innerText"))
        assertTrue(android.contains("document.documentElement.outerHTML"))
    }

    @Test
    fun webViewLoaderInjectsReadabilityAndUsesBrowserLikeLoadingSettings() {
        val source = File("../shared/src/androidMain/kotlin/com/dailysatori/platform/WebViewLoader.android.kt").readText()

        assertTrue(source.contains("Readability.js"))
        assertTrue(source.contains("new Readability"))
        assertTrue(source.contains("userAgentString"))
        assertTrue(source.contains("Windows NT 10.0; Win64; x64"))
        assertTrue(source.contains("onReceivedError"))
        assertTrue(source.contains("onReceivedHttpError"))
        assertTrue(source.contains("shouldInterceptRequest"))
    }

    @Test
    fun webViewLoaderWaitsForStableInnerHtmlBeforeCompleting() {
        val source = File("../shared/src/androidMain/kotlin/com/dailysatori/platform/WebViewLoader.android.kt").readText()

        assertTrue(source.contains("document.documentElement.innerHTML"))
        assertTrue(source.contains("stableHtml"))
        assertTrue(source.contains("shouldCompleteWebViewPolling"))
        assertTrue(source.contains("isUsableContent"))
        assertTrue(source.contains("hasUsableContent"))
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
    fun shareCanRetryIncompleteExistingArticles() {
        assertEquals(true, shouldRetryExistingSharedArticle("error"))
        assertEquals(true, shouldRetryExistingSharedArticle("pending"))
        assertEquals(true, shouldRetryExistingSharedArticle("webContentFetched"))
        assertEquals(true, shouldRetryExistingSharedArticle("aiProcessing"))
        assertEquals(false, shouldRetryExistingSharedArticle("completed"))
    }

    @Test
    fun shareReceiverRetriesExistingArticleBeforeCheckingPendingGate() {
        val source = File("src/main/kotlin/com/dailysatori/ShareReceiverActivity.kt").readText()
        val pendingIndex = source.indexOf("articleProcessingScheduler.isSavePending(url)")
        val retryIndex = source.indexOf("retryExistingArticle(url)")

        assertTrue(retryIndex >= 0)
        assertTrue(pendingIndex >= 0)
        assertTrue(retryIndex < pendingIndex)
    }

    @Test
    fun clipboardExistingIncompleteArticleCanRetryInsteadOfDuplicateOnly() {
        val source = File("src/main/kotlin/com/dailysatori/AppUrlIntakeViewModel.kt").readText()
        val clipboard = source.substringAfter("fun checkClipboard()")
            .substringBefore("fun confirmClipboardUrl()")
        val retryIndex = clipboard.indexOf("retryExistingArticle(url)")
        val existingIndex = clipboard.indexOf("isExistingArticle(url)")

        assertTrue(retryIndex >= 0)
        assertTrue(existingIndex >= 0)
        assertTrue(retryIndex < existingIndex)
    }

    @Test
    fun savePendingMarkerExpiresSoStalePrefsDoNotBlockForever() {
        val source = File("src/main/kotlin/com/dailysatori/core/worker/ArticleProcessingWorker.kt").readText()

        assertTrue(source.contains("SAVE_PENDING_TTL_MS"))
        assertTrue(source.contains("System.currentTimeMillis()"))
        assertTrue(source.contains("getLong(normalizedUrl"))
        assertTrue(source.contains("clearSavePending(normalizedUrl)"))
    }

    @Test
    fun parallelAiTasksUseFieldSpecificArticleUpdates() {
        val parser = File("../shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt").readText()
        val repository = File("../shared/src/commonMain/kotlin/com/dailysatori/data/repository/ArticleRepository.kt").readText()
        val queries = File("../shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()

        assertTrue(parser.contains("updateAiTitle("))
        assertTrue(parser.contains("updateAiContent("))
        assertTrue(parser.contains("updateAiMarkdownContent("))
        assertTrue(repository.contains("fun updateAiTitle("))
        assertTrue(repository.contains("fun updateAiContent("))
        assertTrue(repository.contains("fun updateAiMarkdownContent("))
        assertTrue(queries.contains("updateArticleAiTitle:"))
        assertTrue(queries.contains("updateArticleAiContent:"))
        assertTrue(queries.contains("updateArticleAiMarkdownContent:"))
    }

    @Test
    fun parallelAiTasksPropagateChildFailuresToWorkerRetry() {
        val source = File("../shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt").readText()
        val aiProcessing = source.substringAfter("suspend fun processAiTasks")
            .substringBefore("private suspend fun generateArticleTitle")

        assertTrue(aiProcessing.contains("async"))
        assertTrue(aiProcessing.contains("awaitAll"))
        assertFalse(aiProcessing.contains("joinAll"))
    }

    @Test
    fun aiProcessingFailureReloadsLatestArticleBeforePersistingError() {
        val source = File("../shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt").readText()
        val catchBlock = source.substringAfter("AI processing failed: articleId=\$articleId")
            .substringBefore("val errorState")

        assertTrue(catchBlock.contains("articleRepo.getById(articleId)"))
        assertTrue(catchBlock.contains("latestArticle.ai_content"))
        assertTrue(catchBlock.contains("latestArticle.ai_markdown_content"))
    }

    @Test
    fun existingArticleRetryUsesActiveProcessingGuard() {
        val source = File("../shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt").readText()
        val existingBranch = source.substringAfter("findExistingArticleByUrl(url)?.let")
            .substringBefore("val articleId = articleRepo.insert")

        assertTrue(existingBranch.contains("markArticleActive(existing.id)"))
        assertTrue(existingBranch.contains("finishQueuedArticle(existing.id)"))
        assertTrue(existingBranch.contains("enqueueArticleProcessing(existing.id)"))
        assertFalse(existingBranch.contains("throw CancellationException"))
    }

    @Test
    fun articleInsertUsesAtomicInsertedIdInsteadOfMaxId() {
        val repository = File("../shared/src/commonMain/kotlin/com/dailysatori/data/repository/ArticleRepository.kt").readText()
        val queries = File("../shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()
        val insertMethod = repository.substringAfter("fun insert(")
            .substringBefore("fun update(")

        assertTrue(queries.contains("selectArticleByUrl:"))
        assertTrue(insertMethod.contains("insertArticle"))
        assertTrue(insertMethod.contains("selectArticleByUrl"))
        assertTrue(insertMethod.contains("executeAsOne"))
        assertFalse(insertMethod.contains("maxOfOrNull"))
        assertFalse(queries.contains("RETURNING id"))
    }

    @Test
    fun refreshArticleUsesActiveProcessingGuard() {
        val source = File("../shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt").readText()
        val refresh = source.substringAfter("suspend fun refreshArticle")
            .substringBefore("suspend fun reprocessArticle")

        assertTrue(refresh.contains("markArticleActive(articleId)"))
        assertTrue(refresh.contains("articleRepo.updateStatus(articleId, \"pending\")"))
        assertTrue(refresh.contains("enqueueArticleProcessing(articleId)"))
        assertTrue(refresh.contains("finishQueuedArticle(articleId)"))
    }

    @Test
    fun resumeQueuesRecoverableArticlesWhenSlotsAreFull() {
        val source = File("../shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt").readText()
        val resume = source.substringAfter("suspend fun resumeInterruptedProcessing")
            .substringBefore("suspend fun saveWebpage")

        assertFalse(resume.contains("if (!markArticleActive(article.id)) return@forEach"))
        assertTrue(resume.contains("enqueueArticleProcessing(article.id)"))
    }

    @Test
    fun articleProcessingQueueRunsOneArticleAtATimeAndQueuesNewArticles() {
        val source = File("../shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt").readText()
        val saveNewArticle = source.substringAfter("val ownsProcessing = markArticleActive(articleId)")
            .substringBefore("val state = mutableMapOf<Long, ArticleProcessingState>()")
        val enqueueIndex = saveNewArticle.indexOf("enqueueArticleProcessing(articleId)")
        val returnIndex = saveNewArticle.indexOf("return articleId")

        assertTrue(source.contains("const val MAX_CONCURRENT_PROCESSING = 1"))
        assertTrue(enqueueIndex >= 0)
        assertTrue(returnIndex >= 0)
        assertTrue(enqueueIndex < returnIndex)
    }

    @Test
    fun androidWebViewLoaderSerializesPageLoads() {
        val source = File("../shared/src/androidMain/kotlin/com/dailysatori/platform/WebViewLoader.android.kt").readText()

        assertTrue(source.contains("pendingLoads"))
        assertTrue(source.contains("activeLoad"))
        assertTrue(source.contains("startNextLoad()"))
        assertTrue(source.contains("finishLoad(load)"))
    }

    @Test
    fun aiCompletionUsesFieldSpecificStatusAndCoverUpdate() {
        val parser = File("../shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt").readText()
        val repository = File("../shared/src/commonMain/kotlin/com/dailysatori/data/repository/ArticleRepository.kt").readText()
        val queries = File("../shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()
        val completion = parser.substringAfter("Downloading cover image")
            .substringBefore("setProcessingState(articleId, finalArticleStatus")

        assertTrue(completion.contains("updateProcessingCompletion("))
        assertFalse(completion.contains("articleRepo.update("))
        assertTrue(repository.contains("fun updateProcessingCompletion("))
        assertTrue(queries.contains("updateArticleProcessingCompletion:"))
    }

    @Test
    fun shareRetryWorkReplacesExistingStuckWork() {
        val source = File("src/main/kotlin/com/dailysatori/core/worker/ArticleProcessingWorker.kt").readText()

        assertTrue(source.contains("enqueueRetrySave"))
        assertTrue(source.contains("ExistingWorkPolicy.REPLACE"))
    }

    @Test
    fun backgroundShareSaveRetriesTransientFailureBeforeClearingPendingMarker() {
        val source = File("src/main/kotlin/com/dailysatori/core/worker/ArticleProcessingWorker.kt").readText()
        val saveMode = source.substringAfter("MODE_SAVE ->")
            .substringBefore("MODE_RESUME ->")
        val exceptionBranch = saveMode.substringAfter("catch (e: Exception)")
        val retryIndex = exceptionBranch.indexOf("Result.retry()")
        val clearIndex = exceptionBranch.indexOf("clearPendingSave()")

        assertTrue(source.contains("MAX_SAVE_ATTEMPTS"))
        assertTrue(retryIndex >= 0)
        assertTrue(clearIndex >= 0)
        assertTrue(retryIndex < clearIndex)
    }

    @Test
    fun parserResumeModePropagatesCancellationToWorkerRetry() {
        val source = File("../shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt").readText()
        val resumeCatch = source.substringAfter("suspend fun resumeInterruptedProcessing")
            .substringBefore("private fun enqueueArticleProcessing")

        assertTrue(resumeCatch.contains("shouldPersistArticleProcessingError"))
    }

    @Test
    fun parserDoesNotUseFxTwitterExtraction() {
        val source = File("../shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt").readText()

        assertFalse(source.contains("api.fxtwitter.com"))
        assertFalse(source.contains("extractTwitterContent"))
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

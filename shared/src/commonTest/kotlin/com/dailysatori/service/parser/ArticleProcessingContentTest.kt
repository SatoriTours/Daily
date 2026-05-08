package com.dailysatori.service.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import com.dailysatori.platform.WebViewPageContent
import com.dailysatori.platform.shouldCompleteWebViewPolling

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
    fun fallsBackToExtractedContentWhenMarkdownGenerationIsBlank() {
        assertEquals(
            "网页正文",
            generatedMarkdownOrFallback("", existing = null, extractedContent = "网页正文"),
        )
        assertEquals(
            "old markdown",
            generatedMarkdownOrFallback("", existing = "old markdown", extractedContent = "网页正文"),
        )
    }

    @Test
    fun fallsBackToExtractedContentWhenSummaryGenerationIsBlank() {
        assertEquals(
            "网页正文",
            generatedSummaryOrFallback("", existing = null, extractedContent = "网页正文"),
        )
        assertEquals(
            "old summary",
            generatedSummaryOrFallback("", existing = "old summary", extractedContent = "网页正文"),
        )
    }

    @Test
    fun fallsBackToExistingOriginalWhenSummaryGenerationIsBlank() {
        assertEquals(
            "已有原文",
            generatedSummaryOrFallback("", existing = null, extractedContent = null, existingMarkdownContent = "已有原文"),
        )
    }

    @Test
    fun keepsExistingSummaryWhenLaterProcessingFails() {
        assertEquals("已有摘要", summaryAfterProcessingError("已有摘要", "已有原文", "AI timeout"))
        assertEquals("已有原文", summaryAfterProcessingError(null, "已有原文", "AI timeout"))
        assertEquals("AI timeout", summaryAfterProcessingError(null, null, "AI timeout"))
    }

    @Test
    fun marksArticleCompletedWhenAnyAiContentExists() {
        assertEquals("completed", finalArticleStatus(aiContent = "摘要", aiMarkdownContent = null))
        assertEquals("completed", finalArticleStatus(aiContent = null, aiMarkdownContent = "原文"))
        assertEquals("error", finalArticleStatus(aiContent = null, aiMarkdownContent = null))
    }

    @Test
    fun legacyProcessingErrorTextDoesNotMakeArticleCompleted() {
        assertEquals("error", finalArticleStatus(aiContent = "Job was cancelled", aiMarkdownContent = null))
        assertEquals("error", finalArticleStatus(aiContent = "AI summary generation returned empty result", aiMarkdownContent = null))
        assertEquals("completed", finalArticleStatus(aiContent = "Job was cancelled", aiMarkdownContent = "已有原文"))
    }

    @Test
    fun doesNotReuseLegacyProcessingErrorsAsExistingSummary() {
        assertEquals(null, existingSummaryOrNull("Job was cancelled"))
        assertEquals(null, existingSummaryOrNull("AI summary generation returned empty result"))
        assertEquals("The event was cancelled by the organizer.", existingSummaryOrNull("The event was cancelled by the organizer."))
        assertEquals("正常摘要", existingSummaryOrNull("正常摘要"))
    }

    @Test
    fun realRetryFailureReplacesLegacyRecoverableErrorMessage() {
        assertEquals("AI config not set", articleProcessingErrorMessage(IllegalStateException("AI config not set")))
        assertEquals("AI processing failed", articleProcessingErrorMessage(Exception()))
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
        assertEquals(false, prompt.contains("网页 HTML"))
        assertEquals(false, prompt.contains("COVER_IMAGE_URL"))
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
        assertEquals(true, prompt.contains("必须保留正文图片"))
        assertEquals(false, prompt.contains("保留原文语言"))
        assertEquals(true, prompt.contains("用中文输出"))
        assertEquals(false, prompt.contains("保留原文语言，不翻译正文内容"))
    }

    @Test
    fun articleTitlePromptRequiresChineseOutput() {
        val prompt = articleTitlePrompt()

        assertEquals(true, prompt.contains("用中文输出"))
        assertEquals(true, prompt.contains("15-25 字"))
        assertEquals(true, prompt.contains("只返回标题"))
    }

    @Test
    fun articleSummaryPromptRequiresChineseOutput() {
        val prompt = articleSummaryPrompt()

        assertEquals(true, prompt.contains("用中文输出"))
    }

    @Test
    fun prefersGeneratedTitleThenSummaryThenExistingTitles() {
        assertEquals(
            "AI 优化标题",
            selectedArticleAiTitle("AI 优化标题", "摘要标题", "网页标题", "原标题"),
        )
        assertEquals(
            "摘要标题",
            selectedArticleAiTitle("", "摘要标题", "网页标题", "原标题"),
        )
        assertEquals(
            "网页标题",
            selectedArticleAiTitle("", null, "网页标题", "原标题"),
        )
        assertEquals(
            "原标题",
            selectedArticleAiTitle("", null, null, "原标题"),
        )
    }

    @Test
    fun webViewPollingUsesCurrentSnapshotAfterMaximumChecks() {
        assertEquals(false, shouldCompleteWebViewPolling(stableReadCount = 0, readCount = 4))
        assertEquals(true, shouldCompleteWebViewPolling(stableReadCount = 0, readCount = 5))
        assertEquals(true, shouldCompleteWebViewPolling(stableReadCount = 2, readCount = 2))
    }

    @Test
    fun articleMarkdownInputUsesExtractedTextAndImagesInsteadOfFullHtml() {
        val extracted = ExtractedContent(
            title = "标题",
            content = "渲染后的正文",
            htmlContent = "<html><body><script>noise()</script><article>HTML正文</article></body></html>",
            coverImageUrl = null,
            imageUrls = listOf("https://example.com/image.jpg"),
        )

        val input = articleMarkdownInput(extracted, "gpt-5")

        assertEquals(true, input.contains("正文文本：\n渲染后的正文"))
        assertEquals(true, input.contains("正文图片：\nhttps://example.com/image.jpg"))
        assertEquals(false, input.contains("<script>"))
    }

    @Test
    fun parsesCoverImageUrlFromSummaryOutputAndRemovesMetadataLine() {
        val output = """
            # 标题

            ## 核心内容
            正文摘要。

            ## 核心观点
            1. 要点

            COVER_IMAGE_URL: https://example.com/article/cover.jpg
        """.trimIndent()

        val parsed = parseArticleSummaryOutput(output)

        assertEquals("https://example.com/article/cover.jpg", parsed.coverImageUrl)
        assertEquals(false, parsed.summary.contains("COVER_IMAGE_URL"))
        assertEquals(true, parsed.summary.contains("# 标题"))
    }

    @Test
    fun validatesAiCoverUrlAgainstImagesFoundInHtml() {
        val html = """
            <html><body>
              <img src="/small.png" width="200" />
              <img src="/cover.jpg" width="640" />
            </body></html>
        """.trimIndent()
        val imageUrls = extractContentImageUrls(html, "https://example.com/post")

        assertEquals(listOf("https://example.com/cover.jpg"), imageUrls)
        assertEquals(
            "https://example.com/cover.jpg",
            validatedCoverImageUrl("https://example.com/cover.jpg", imageUrls),
        )
        assertEquals(null, validatedCoverImageUrl("https://example.com/avatar.jpg", imageUrls))
    }

    @Test
    fun limitsHtmlByModelContextWithoutDefaultEarlyTruncation() {
        val html = "x".repeat(12_000)

        assertEquals(12_000, htmlForAiModel(html, "gemini-3.1-pro-preview").length)
        assertEquals(10_000, htmlForAiModel(html, "unknown-model", fallbackLimit = 10_000).length)
    }

    @Test
    fun articleSummaryInputUsesExtractedTextInsteadOfFullHtml() {
        val html = "<html>${"x".repeat(20_000)}</html>"
        val extracted = ExtractedContent(
            title = "标题",
            content = "正文内容".repeat(4_000),
            htmlContent = html,
            coverImageUrl = null,
        )

        val input = articleSummaryInput(extracted, "gpt-5")

        assertEquals(10_000, input.length)
        assertEquals(false, input.contains("<html>"))
    }

    @Test
    fun extractedContentUsesWebViewVisibleTextForAiSummary() {
        val page = WebViewPageContent(
            html = "<html><body><main>HTML正文</main></body></html>",
            text = "浏览器渲染后的完整可见文本",
        )

        assertEquals("浏览器渲染后的完整可见文本", page.summaryTextOrHtmlFallback())
    }

    @Test
    fun extractedContentFallsBackToHtmlWhenVisibleTextIsBlank() {
        val page = WebViewPageContent(
            html = "<html><body><main>HTML正文</main></body></html>",
            text = "   ",
        )

        assertEquals("<html><body><main>HTML正文</main></body></html>", page.summaryTextOrHtmlFallback())
    }

    @Test
    fun onlyInterruptedArticleStatusesAreRecoverable() {
        assertEquals(true, isRecoverableArticleStatus("pending"))
        assertEquals(true, isRecoverableArticleStatus("webContentFetched"))
        assertEquals(true, isRecoverableArticleStatus("aiProcessing"))

        assertEquals(false, isRecoverableArticleStatus("completed"))
        assertEquals(false, isRecoverableArticleStatus("error"))
        assertEquals(false, isRecoverableArticleStatus(""))
        assertEquals(false, isRecoverableArticleStatus("unknown"))
    }

    @Test
    fun legacyCancellationErrorsAreRecoverableOnAppResume() {
        assertEquals(true, isRecoverableArticleForProcessing("error", "Job was cancelled", null))
        assertEquals(true, isRecoverableArticleForProcessing("error", "StandaloneCoroutine was cancelled", null))
        assertEquals(false, isRecoverableArticleForProcessing("error", "AI config not set", null))
    }

    @Test
    fun legacyBlankSummaryErrorsWithOriginalContentAreRecoverableOnAppResume() {
        assertEquals(
            true,
            isRecoverableArticleForProcessing(
                "error",
                "AI summary generation returned empty result",
                "已有原文",
            ),
        )
        assertEquals(
            false,
            isRecoverableArticleForProcessing("error", "AI summary generation returned empty result", ""),
        )
    }

    @Test
    fun cancellationDoesNotPersistAsArticleProcessingError() {
        assertEquals(false, shouldPersistArticleProcessingError(CancellationException("Job was cancelled")))
        val timeout = runBlocking {
            try {
                withTimeout(1) { awaitCancellation() }
                null
            } catch (e: Exception) {
                e
            }
        }
        assertEquals(true, shouldPersistArticleProcessingError(timeout ?: error("Expected timeout")))
        assertEquals(true, shouldPersistArticleProcessingError(IllegalStateException("AI config not set")))
    }

    @Test
    fun prefersLargestTwitterArticleImageAndRequestsLargeVariant() {
        val html = """
            <html>
              <head>
                <meta property="og:image" content="https://pbs.twimg.com/profile_images/avatar.jpg" />
              </head>
              <body>
                <img src="https://pbs.twimg.com/profile_images/avatar.jpg" width="400" height="400" />
                <img src="https://pbs.twimg.com/media/HHR2R6CbAAApwp-?format=jpg&name=4096x4096" width="4096" height="4096" />
                <img src="https://pbs.twimg.com/media/small?format=jpg&name=small" width="680" height="383" />
              </body>
            </html>
        """.trimIndent()

        assertEquals(
            "https://pbs.twimg.com/media/HHR2R6CbAAApwp-?format=jpg&name=large",
            extractCoverImageUrl(html, "https://x.com/0xMulight/status/2050393928340488265"),
        )
    }

    @Test
    fun skipsDecorativeImagesAndPrefersMainPageImageBeforeOgImage() {
        val html = """
            <html>
              <head>
                <meta property="og:image" content="https://example.com/fallback-og.jpg" />
              </head>
              <body>
                <img src="data:image/png;base64,AAAA" width="1200" height="800" />
                <img src="https://example.com/brand-logo.png" width="900" height="900" />
                <img src="https://example.com/hero.gif" width="1600" height="900" />
                <img src="/article/main.jpg" width="640" height="360" />
              </body>
            </html>
        """.trimIndent()

        assertEquals(
            "https://example.com/article/main.jpg",
            extractCoverImageUrl(html, "https://example.com/posts/123"),
        )
    }

    @Test
    fun skipsPlaceholderAndBlankImagesWhenChoosingCover() {
        val html = """
            <html>
              <head>
                <meta property="og:image" content="https://example.com/default-placeholder.jpg" />
              </head>
              <body>
                <img src="https://example.com/blank.png" width="1200" height="800" />
                <img src="https://example.com/article-cover.jpg" width="640" height="360" />
              </body>
            </html>
        """.trimIndent()

        assertEquals(
            "https://example.com/article-cover.jpg",
            extractCoverImageUrl(html, "https://example.com/posts/123"),
        )
    }

    @Test
    fun skipsAvatarWhenFallingBackToImagesWithoutKnownDimensions() {
        val html = """
            <html>
              <head>
                <meta property="og:image" content="https://pbs.twimg.com/profile_images/avatar.jpg" />
              </head>
              <body>
                <img src="https://pbs.twimg.com/profile_images/avatar.jpg" />
                <img src="https://pbs.twimg.com/media/HHR2R6CbAAApwp-?format=jpg&name=4096x4096" />
              </body>
            </html>
        """.trimIndent()

        assertEquals(
            "https://pbs.twimg.com/media/HHR2R6CbAAApwp-?format=jpg&name=large",
            extractCoverImageUrl(html, "https://x.com/0xMulight/status/2050393928340488265"),
        )
    }

    @Test
    fun extractsLazyLoadedArticleImages() {
        val html = """
            <html><body>
              <img src="data:image/gif;base64,R0lG" data-src="/article/hero.jpg" width="900" height="500" />
            </body></html>
        """.trimIndent()

        assertEquals(
            "https://example.com/article/hero.jpg",
            extractCoverImageUrl(html, "https://example.com/posts/123"),
        )
        assertEquals(
            listOf("https://example.com/article/hero.jpg"),
            extractContentImageUrls(html, "https://example.com/posts/123"),
        )
    }

    @Test
    fun extractsAlternateLazyLoadedArticleImageAttributes() {
        val html = """
            <html><body>
              <img src="/placeholder.png" data-original="/article/original.jpg" width="900" height="500" />
              <img src="/placeholder.png" data-lazy-src="/article/lazy.jpg" width="800" height="450" />
            </body></html>
        """.trimIndent()

        assertEquals(
            "https://example.com/article/original.jpg",
            extractCoverImageUrl(html, "https://example.com/posts/123"),
        )
        assertEquals(
            listOf("https://example.com/article/original.jpg", "https://example.com/article/lazy.jpg"),
            extractContentImageUrls(html, "https://example.com/posts/123"),
        )
    }

    @Test
    fun extractsBestSrcsetCandidateWhenSrcIsMissingOrTiny() {
        val html = """
            <html><body>
              <img src="/tiny.jpg" srcset="/small.jpg 320w, /article/large.jpg 1280w" />
            </body></html>
        """.trimIndent()

        assertEquals(
            "https://example.com/article/large.jpg",
            extractCoverImageUrl(html, "https://example.com/posts/123"),
        )
    }

    @Test
    fun resolvesRelativeArticleImageUrlsAgainstPagePath() {
        val html = """
            <html><body>
              <img src="images/hero.jpg" width="900" height="500" />
            </body></html>
        """.trimIndent()

        assertEquals(
            "https://example.com/posts/images/hero.jpg",
            extractCoverImageUrl(html, "https://example.com/posts/123"),
        )
    }

    @Test
    fun fallsBackToTwitterImageMetadataWhenOgImageIsMissing() {
        val html = """
            <html><head>
              <meta name="twitter:image" content="https://example.com/twitter-card.jpg" />
            </head><body></body></html>
        """.trimIndent()

        assertEquals(
            "https://example.com/twitter-card.jpg",
            extractCoverImageUrl(html, "https://example.com/posts/123"),
        )
    }

}

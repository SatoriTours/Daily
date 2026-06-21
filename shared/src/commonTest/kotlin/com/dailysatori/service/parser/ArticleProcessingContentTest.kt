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
    fun rejectsUnusableExtractedArticleContent() {
        assertFailsWith<IllegalStateException> {
            usableArticleContentOrThrow("登录\n注册\n首页", "https://example.com/article")
        }
        assertFailsWith<IllegalStateException> {
            usableArticleContentOrThrow("Just a moment...\nChecking your browser before accessing", "https://example.com/article")
        }

        assertEquals(
            "这是一段可用正文，包含足够的信息密度，用来证明文章内容已经被正确提取，而不是导航、登录或错误页面。",
            usableArticleContentOrThrow(
                "这是一段可用正文，包含足够的信息密度，用来证明文章内容已经被正确提取，而不是导航、登录或错误页面。",
                "https://example.com/article",
            ),
        )
    }

    @Test
    fun articleSummaryPromptAvoidsUiHeadingsAndOverExpansion() {
        val prompt = articleSummaryPrompt()

        assertEquals(false, prompt.contains("# 标题"))
        assertEquals(false, prompt.contains("## 核心内容"))
        assertEquals(false, prompt.contains("## 核心观点"))
        assertEquals(false, prompt.contains("15-25 字"))
        assertEquals(false, prompt.contains("50-80 字"))
        assertEquals(false, prompt.contains("2-5 个"))
        assertEquals(true, prompt.contains("不要输出“标题”“核心内容”“核心观点”"))
        assertEquals(true, prompt.contains("内容很短时只做忠实翻译或轻量整理"))
        assertEquals(true, prompt.contains("禁止补充原文没有的背景、动机、情节或评价"))
        assertEquals(true, prompt.contains("不要使用代码块包裹"))
        assertEquals(false, prompt.contains("网页 HTML"))
        assertEquals(false, prompt.contains("COVER_IMAGE_URL"))
        assertEquals(true, prompt.contains("不要用“这是一篇"))
        assertEquals(true, prompt.contains("直接进入内容本身"))
        assertEquals(true, prompt.contains("避免第三视角介绍文章"))
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
        assertEquals(true, prompt.contains("只调整排版、分段和 Markdown 结构"))
        assertEquals(true, prompt.contains("禁止总结、缩写、改写或合并原文信息"))
        assertEquals(true, prompt.contains("长段落必须按自然语义拆分"))
        assertEquals(false, prompt.contains("保留原文语言"))
        assertEquals(true, prompt.contains("用中文输出"))
        assertEquals(false, prompt.contains("保留原文语言，不翻译正文内容"))
        assertEquals(true, prompt.contains("输入可能是 HTML、纯文本或已有 Markdown"))
        assertEquals(true, prompt.contains("如果输入已经是 Markdown"))
        assertEquals(true, prompt.contains("不要把整篇内容压缩成摘要"))
    }

    @Test
    fun articleAnalysisPromptRequiresReadableMarkdownWithoutRewritingContent() {
        val prompt = articleAnalysisPrompt()

        assertEquals(true, prompt.contains("markdown"))
        assertEquals(false, prompt.contains("必须包含 \"# 标题\""))
        assertEquals(false, prompt.contains("## 核心内容"))
        assertEquals(false, prompt.contains("## 核心观点"))
        assertEquals(true, prompt.contains("summary 不要输出“标题”“核心内容”“核心观点”"))
        assertEquals(true, prompt.contains("内容很短时只做忠实翻译或轻量整理"))
        assertEquals(true, prompt.contains("内容较长时必须包含简短总结和关键观点列表"))
        assertEquals(true, prompt.contains("如果原文已经是中文短内容，summary 直接返回原文"))
        assertEquals(true, prompt.contains("禁止补充原文没有的背景、动机、情节或评价"))
        assertEquals(true, prompt.contains("不要用“这是一篇"))
        assertEquals(true, prompt.contains("直接进入内容本身"))
        assertEquals(true, prompt.contains("只调整排版、分段和 Markdown 结构"))
        assertEquals(true, prompt.contains("禁止总结、缩写、改写或合并原文信息"))
        assertEquals(true, prompt.contains("长段落必须按自然语义拆分"))
    }

    @Test
    fun articleTitlePromptRequiresChineseOutput() {
        val prompt = articleTitlePrompt()

        assertEquals(true, prompt.contains("用中文输出"))
        assertEquals(true, prompt.contains("8-18 个中文字符"))
        assertEquals(true, prompt.contains("最多不超过 24 个字符"))
        assertEquals(true, prompt.contains("禁止换行"))
        assertEquals(true, prompt.contains("只返回标题"))
    }

    @Test
    fun articleSummaryPromptRequiresChineseOutput() {
        val prompt = articleSummaryPrompt()

        assertEquals(true, prompt.contains("用中文输出"))
    }

    @Test
    fun summarySanitizerRemovesThirdPersonArticleIntro() {
        assertEquals(
            "强调持续练习比短期冲刺更重要。",
            sanitizeArticleSummaryMarkdown("这是一篇关于学习方法的文章，强调持续练习比短期冲刺更重要。"),
        )
        assertEquals(
            "AI 工具正在进入日常写作流程。",
            sanitizeArticleSummaryMarkdown("本文介绍了 AI 工具正在进入日常写作流程。"),
        )
        assertEquals(
            "作者在访谈中说，本文不是一份投资建议。",
            sanitizeArticleSummaryMarkdown("作者在访谈中说，本文不是一份投资建议。"),
        )
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
    fun rejectsBodyLikeAiTitlesBeforePersisting() {
        val bodyLikeTitle = "这是一段很长的正文内容，包含多个逗号，多个事实，并且明显不是一个适合展示在详情页顶部的标题"

        assertEquals(
            "网页标题",
            selectedArticleAiTitle(bodyLikeTitle, "## 核心内容\n正文", "网页标题", "原标题"),
        )
        assertEquals(
            "短标题",
            selectedArticleAiTitle("\"1. 短标题\"", null, null, "原标题"),
        )
    }

    @Test
    fun webViewPollingCompletesAfterThreeStableReadsOrTenChecks() {
        assertEquals(false, shouldCompleteWebViewPolling(stableReadCount = 2, readCount = 9))
        assertEquals(true, shouldCompleteWebViewPolling(stableReadCount = 3, readCount = 3))
        assertEquals(true, shouldCompleteWebViewPolling(stableReadCount = 0, readCount = 10))
    }

    @Test
    fun articleMarkdownInputUsesReadableHtmlAndImagesInsteadOfFullPageHtml() {
        val extracted = ExtractedContent(
            title = "标题",
            content = "渲染后的正文",
            htmlContent = "<html><body><script>noise()</script><article>HTML正文</article></body></html>",
            readableHtmlContent = "<article><h1>标题</h1><p>Readability 正文</p><img src=\"https://example.com/image.jpg\"></article>",
            coverImageUrl = null,
            imageUrls = listOf("https://example.com/image.jpg"),
        )

        val input = articleMarkdownInput(extracted, "gpt-5")

        assertEquals(true, input.contains("正文 HTML：\n<article><h1>标题</h1><p>Readability 正文</p>"))
        assertEquals(true, input.contains("正文图片：\nhttps://example.com/image.jpg"))
        assertEquals(false, input.contains("<script>"))
    }

    @Test
    fun parsesStructuredArticleAnalysisJson() {
        val parsed = parseArticleAnalysisOutput(
            """
                ```json
                {
                  "title": "中文标题",
                  "summary": "# 标题\n\n## 核心内容\n摘要",
                  "markdown": "# 原文\n\n正文"
                }
                ```
            """.trimIndent(),
        )

        assertEquals("中文标题", parsed.title)
        assertEquals(true, parsed.summary.contains("摘要"))
        assertEquals("# 原文\n\n正文", parsed.markdown)
    }

    @Test
    fun repairsMalformedImageMarkdownWhenExtractedImageUrlExists() {
        assertEquals(
            "正文\n\n![图片](https://example.com/article.jpg)",
            normalizeArticleMarkdownImages(
                "正文\n\n!图片",
                listOf("https://example.com/article.jpg"),
            ),
        )
        assertEquals(
            "正文\n\n![配图](https://example.com/article.jpg)",
            normalizeArticleMarkdownImages(
                "正文\n\n![配图]",
                listOf("https://example.com/article.jpg"),
            ),
        )
    }

    @Test
    fun repairsFullWidthMalformedImageMarkdownWhenExtractedImageUrlExists() {
        assertEquals(
            "正文\n\n![图片](https://example.com/article.jpg)",
            normalizeArticleMarkdownImages(
                "正文\n\n！图片",
                listOf("https://example.com/article.jpg"),
            ),
        )
    }

    @Test
    fun repairsFullWidthImageMarkdownLinkWhenExtractedImageUrlExists() {
        assertEquals(
            "正文\n\n![图像](https://pbs.twimg.com/media/G7ckOWAbYAE_fDS?format=jpg&name=large)",
            normalizeArticleMarkdownImages(
                "正文\n\n！[图像](https：//pbs.twimg.com/media/G7ckOWAbYAE_fDS？format=jpg&name=medium)",
                listOf("https://pbs.twimg.com/media/G7ckOWAbYAE_fDS?format=jpg&name=large"),
            ),
        )
    }

    @Test
    fun appendsMissingImageMarkdownWhenModelDropsImagePlaceholder() {
        assertEquals(
            "正文\n\n![图片 1](https://example.com/article.jpg)",
            normalizeArticleMarkdownImages(
                "正文",
                listOf("https://example.com/article.jpg"),
            ),
        )
    }

    @Test
    fun preservesExistingValidImageMarkdown() {
        val markdown = "正文\n\n![原图](https://example.com/article.jpg)"

        assertEquals(
            markdown,
            normalizeArticleMarkdownImages(markdown, listOf("https://example.com/article.jpg")),
        )
    }

    @Test
    fun detectsTwitterStatusUrls() {
        assertEquals(true, isTwitterStatusUrl("https://x.com/i/status/2050393928340488265"))
        assertEquals(true, isTwitterStatusUrl("https://x.com/user/status/2050393928340488265"))
        assertEquals(true, isTwitterStatusUrl("https://twitter.com/user/status/2050393928340488265"))
    }

    @Test
    fun xPostLookupDraftBecomesTwitterExtractedContent() {
        val draft = com.dailysatori.service.externalfavorites.ExternalFavoriteItemDraft(
            provider = "x",
            externalId = "2068340624907202872",
            canonicalUrl = "https://example.com/long-article",
            title = "Long Article",
            text = "原推里的文章卡片 https://t.co/article",
            authorName = "Writer",
            sourceCreatedAt = null,
            favoritedAt = null,
            normalizedJson = """
                {
                  "url_description": "Article summary from the card",
                  "url_images": ["https://example.com/cover.jpg"],
                  "media": [{"url": "https://pbs.twimg.com/media/photo.jpg"}]
                }
            """.trimIndent(),
            contentHash = "hash",
            aiInputHash = "ai",
        )

        val extracted = xPostLookupDraftExtractedContent(draft)

        assertEquals("Long Article", extracted.title)
        assertEquals(
            """
                原推里的文章卡片 https://t.co/article

                Long Article

                Article summary from the card
            """.trimIndent(),
            extracted.content,
        )
        assertEquals("https://example.com/cover.jpg", extracted.coverImageUrl)
        assertEquals(
            listOf("https://example.com/cover.jpg", "https://pbs.twimg.com/media/photo.jpg"),
            extracted.imageUrls,
        )
    }

    @Test
    fun rejectsNonTwitterStatusUrls() {
        assertEquals(false, isTwitterStatusUrl("https://x.com/user"))
        assertEquals(false, isTwitterStatusUrl("https://x.com/search?q=kotlin"))
        assertEquals(false, isTwitterStatusUrl("https://mobile.twitter.com/user/status/2050393928340488265"))
        assertEquals(false, isTwitterStatusUrl("https://example.com/user/status/2050393928340488265"))
        assertEquals(false, isTwitterStatusUrl(null))
    }

    @Test
    fun formatsTwitterMarkdownWithCleanedTextOriginalUrlAndMediaUrls() {
        val url = "https://x.com/user/status/2050393928340488265"
        val extracted = ExtractedContent(
            title = "Post / X",
            content = """
                X
                Post
                See new posts
                User
                @user
                This is the actual post.
                It spans two lines.
                Translate post
                12:34 PM · May 9, 2026
            """.trimIndent(),
            htmlContent = null,
            coverImageUrl = "https://pbs.twimg.com/media/first?format=jpg&name=large",
            imageUrls = listOf(
                "https://pbs.twimg.com/media/first?format=jpg&name=large",
                "https://pbs.twimg.com/profile_images/avatar.jpg",
                "https://pbs.twimg.com/media/second?format=png&name=large",
                "https://pbs.twimg.com/media/first?format=jpg&name=large",
            ),
        )

        assertEquals(
            """
                # 推文内容

                User
                @user
                This is the actual post.
                It spans two lines.

                原文链接：https://x.com/user/status/2050393928340488265

                ## 媒体

                ![媒体 1](https://pbs.twimg.com/media/first?format=jpg&name=large)
                ![媒体 2](https://pbs.twimg.com/profile_images/avatar.jpg)
                ![媒体 3](https://pbs.twimg.com/media/second?format=png&name=large)
            """.trimIndent(),
            twitterStatusMarkdown(url, extracted),
        )
    }

    @Test
    fun fallsBackToMinimalTwitterMarkdownWhenOnlyUiNoiseIsAvailable() {
        val url = "https://twitter.com/user/status/2050393928340488265"
        val extracted = ExtractedContent(
            title = "X",
            content = """
                X
                Post
                See new posts
                Translate post
                Reply
                Repost
                Like
                Share
            """.trimIndent(),
            htmlContent = null,
            coverImageUrl = null,
            imageUrls = emptyList(),
        )

        assertEquals(
            """
                # 推文内容

                原文链接：https://twitter.com/user/status/2050393928340488265
            """.trimIndent(),
            twitterStatusMarkdown(url, extracted),
        )
    }

    @Test
    fun preservesExistingTwitterMarkdownBeforeFormattingNewMarkdown() {
        val existing = "# 已保存推文原文\n\n已有内容"
        val extracted = ExtractedContent(
            title = "Post / X",
            content = "new tweet text",
            htmlContent = null,
            coverImageUrl = null,
            imageUrls = emptyList(),
        )

        assertEquals(
            existing,
            twitterStatusMarkdownOrExisting(
                url = "https://x.com/user/status/2050393928340488265",
                extracted = extracted,
                existing = existing,
            ),
        )
    }

    @Test
    fun preservesExistingTwitterMarkdownExactlyBeforeFormattingNewMarkdown() {
        val existing = "\n  # 已保存推文原文\n\n已有内容  \n"

        assertEquals(
            existing,
            twitterStatusMarkdownOrExisting(
                url = "https://x.com/user/status/2050393928340488265",
                extracted = null,
                existing = existing,
            ),
        )
    }

    @Test
    fun replacesMinimalTwitterMarkdownWhenNewExtractionHasSubstantialContent() {
        val url = "https://x.com/Xudong07452910/status/2051891753821556976"
        val existing = """
            # 推文内容

            **Xudong Han**
            http://
            x.com/i/article/2051
            …

            原文链接：$url
        """.trimIndent()
        val extracted = ExtractedContent(
            title = "15天搭建个人Agent工作系统",
            content = """
                15天搭建个人Agent工作系统：从选基座到配测试的可复制方法论

                本文复盘了作者用15天将个人Agent从脚本迭代为日常工作系统的完整方法论。
                核心是通过选择轻量开源基座、建立项目记忆文件、用AI对话把模块拆解成可验证任务。
                文章还详细说明了如何配置测试、沉淀工作流，并把日常高频任务纳入同一个Agent系统。
            """.trimIndent(),
            htmlContent = "<article><h1>15天搭建个人Agent工作系统</h1><p>完整正文</p></article>",
            coverImageUrl = null,
            readableHtmlContent = "<article><h1>15天搭建个人Agent工作系统</h1><p>完整正文</p></article>",
            imageUrls = emptyList(),
        )

        val markdown = twitterStatusMarkdownOrExisting(url, extracted, existing)

        assertEquals(true, markdown.contains("15天搭建个人Agent工作系统"))
        assertEquals(false, markdown.contains("x.com/i/article/2051\n…"))
    }

    @Test
    fun twitterMarkdownUsesReadableHtmlWhenTweetTextOnlyContainsArticleLink() {
        val url = "https://x.com/Xudong07452910/status/2051891753821556976"
        val extracted = ExtractedContent(
            title = "15天搭建个人Agent工作系统",
            content = """
                **Xudong Han**
                http://
                x.com/i/article/2051
                …
            """.trimIndent(),
            htmlContent = "<html><body>shell</body></html>",
            coverImageUrl = null,
            readableHtmlContent = """
                <article>
                    <h1>15天搭建个人Agent工作系统：从选基座到配测试的可复制方法论</h1>
                    <p>本文复盘了作者用15天将个人Agent从脚本迭代为日常工作系统的完整方法论。</p>
                    <p>核心是通过选择轻量开源基座、建立项目记忆文件、用AI对话把模块拆解成可验证任务。</p>
                </article>
            """.trimIndent(),
            imageUrls = emptyList(),
        )

        val markdown = twitterStatusMarkdown(url, extracted)

        assertEquals(true, markdown.contains("15天搭建个人Agent工作系统"))
        assertEquals(false, markdown.contains("x.com/i/article/2051\n…"))
    }

    @Test
    fun twitterReadableHtmlMarkdownIsSplitIntoReadableParagraphsWithoutChangingText() {
        val url = "https://x.com/Xudong07452910/status/2051891753821556976"
        val extracted = ExtractedContent(
            title = "15天搭建个人Agent工作系统",
            content = """
                **Xudong Han**
                http://
                x.com/i/article/2051
                …
            """.trimIndent(),
            htmlContent = "<html><body>shell</body></html>",
            coverImageUrl = null,
            readableHtmlContent = """
                <article><h1>15天搭建个人Agent工作系统</h1><p>第一步选择轻量开源基座，把项目记忆文件放在固定位置。第二步用AI对话拆解模块，确保每个任务都可以验证。第三步配置测试流程，让Agent每次修改后都能回到可检查状态。</p></article>
            """.trimIndent(),
            imageUrls = emptyList(),
        )

        val markdown = twitterStatusMarkdown(url, extracted)

        assertEquals(true, markdown.contains("第一步选择轻量开源基座，把项目记忆文件放在固定位置。"))
        assertEquals(true, markdown.contains("第二步用AI对话拆解模块，确保每个任务都可以验证。"))
        assertEquals(true, markdown.contains("第三步配置测试流程，让Agent每次修改后都能回到可检查状态。"))
        assertEquals(true, markdown.contains("位置。\n\n第二步"))
        assertEquals(true, markdown.contains("验证。\n\n第三步"))
    }

    @Test
    fun twitterLongExtractedTextMarkdownIsSplitIntoReadableParagraphs() {
        val url = "https://x.com/Xudong07452910/status/2051891753821556976"
        val extracted = ExtractedContent(
            title = "15天搭建个人Agent工作系统",
            content = "2026年个人Agent自建实战：我用Vibe Coding + 借开源轮子，15天搭出每天都在用的工作系统（方法篇）。上一篇我聊了为什么自己不再去追新框架、不再频繁迁移、而是决定自己搭一套Agent。很多朋友看完后留言说想动手但不知道从哪开始，所以这篇文章讲了我用15天把我的个人Agent从能跑迭代成每天都在用的工作系统，完整复盘可复制的方法论。第一步，选一个轻量好扩展的基础项目不要一上来就从零写，也别直接上那种功能堆得很重的框架。第二步，用Claude Code装上，把飞书也顺手跑通，让Agent真的活起来。第三步，先建脚手架文件，让Claude Code知道你是谁、你要什么，避免每次从零猜。",
            htmlContent = "<html><body>shell</body></html>",
            coverImageUrl = null,
            readableHtmlContent = null,
            imageUrls = emptyList(),
        )

        val markdown = twitterStatusMarkdown(url, extracted)

        assertEquals(true, markdown.contains("上一篇我聊了为什么自己不再去追新框架"))
        assertEquals(true, markdown.contains("第一步，选一个轻量好扩展的基础项目"))
        assertEquals(true, markdown.contains("第二步，用Claude Code装上"))
        assertEquals(true, markdown.contains("第三步，先建脚手架文件"))
        assertEquals(true, markdown.contains("方法论。\n\n第一步"))
        assertEquals(true, markdown.contains("框架。\n\n第二步"))
        assertEquals(true, markdown.contains("起来。\n\n第三步"))
    }

    @Test
    fun twitterLongArticleMarkdownRemovesCollapsedXHeaderNoise() {
        val url = "https://x.com/Xudong07452910/status/2051891753821556976"
        val extracted = ExtractedContent(
            title = "15天搭建个人Agent工作系统",
            content = "对话Xudong Han@Xudong074529102026年个人Agent自建实战：我用Vibe Coding + 借开源轮子，15天搭出每天都在用的工作系统（方法篇）1010848213万上一篇我聊了为什么自己不再去追新框架、不再频繁迁移、而是决定自己搭一套Agent。第一步，选一个轻量好扩展的基础项目。第二步，用Claude Code装上，把飞书也顺手跑通。",
            htmlContent = "<html><body>shell</body></html>",
            coverImageUrl = null,
            readableHtmlContent = null,
            imageUrls = emptyList(),
        )

        val markdown = twitterStatusMarkdown(url, extracted)

        assertEquals(false, markdown.contains("对话Xudong Han@"))
        assertEquals(false, markdown.contains("1010848213万上一篇"))
        assertEquals(true, markdown.contains("2026年个人Agent自建实战"))
        assertEquals(true, markdown.contains("上一篇我聊了为什么自己不再去追新框架"))
    }

    @Test
    fun replacesAutoGeneratedTwitterMarkdownEvenWhenExistingContentIsLong() {
        val url = "https://x.com/Xudong07452910/status/2051891753821556976"
        val existing = """
            # 推文内容

            对话Xudong Han@Xudong074529102026年个人Agent自建实战：我用Vibe Coding + 借开源轮子，15天搭出每天都在用的工作系统（方法篇）1010848213万上一篇我聊了为什么自己不再去追新框架、不再频繁迁移、而是决定自己搭一套Agent。第一步，选一个轻量好扩展的基础项目不要一上来就从零写，也别直接上那种功能堆得很重的框架。第二步，用Claude Code装上，把飞书也顺手跑通。

            原文链接：$url
        """.trimIndent()
        val extracted = ExtractedContent(
            title = "15天搭建个人Agent工作系统",
            content = "2026年个人Agent自建实战：我用Vibe Coding + 借开源轮子，15天搭出每天都在用的工作系统（方法篇）。上一篇我聊了为什么自己不再去追新框架、不再频繁迁移、而是决定自己搭一套Agent。第一步，选一个轻量好扩展的基础项目不要一上来就从零写，也别直接上那种功能堆得很重的框架。第二步，用Claude Code装上，把飞书也顺手跑通。",
            htmlContent = "<html><body>shell</body></html>",
            coverImageUrl = null,
            readableHtmlContent = null,
            imageUrls = emptyList(),
        )

        val markdown = twitterStatusMarkdownOrExisting(url, extracted, existing)

        assertEquals(false, markdown.contains("对话Xudong Han@"))
        assertEquals(false, markdown.contains("1010848213万上一篇"))
        assertEquals(true, markdown.contains("方法篇）。\n\n上一篇"))
        assertEquals(true, markdown.contains("Agent。\n\n第一步"))
    }

    @Test
    fun includesTwitterThumbnailOrCardUrlsAsMedia() {
        val url = "https://x.com/user/status/2050393928340488265"
        val extracted = ExtractedContent(
            title = "Post / X",
            content = "tweet text",
            htmlContent = null,
            coverImageUrl = "https://example.com/twitter-card.jpg",
            imageUrls = listOf(
                "   ",
                "https://example.com/twitter-card.jpg",
                "https://cdn.example.com/thumb.png",
            ),
        )

        assertEquals(
            """
                # 推文内容

                tweet text

                原文链接：https://x.com/user/status/2050393928340488265

                ## 媒体

                ![媒体 1](https://example.com/twitter-card.jpg)
                ![媒体 2](https://cdn.example.com/thumb.png)
            """.trimIndent(),
            twitterStatusMarkdown(url, extracted),
        )
    }

    @Test
    fun detectsWebViewNetworkErrorPages() {
        assertEquals(true, isWebViewNetworkErrorContent("网页无法打开\nnet::ERR_CONNECTION_CLOSE", ""))
        assertEquals(true, isWebViewNetworkErrorContent("", "<body>net::ERR_CONNECTION_RESET</body>"))
        assertEquals(false, isWebViewNetworkErrorContent("正常正文", "<body>正常正文</body>"))
    }

    @Test
    fun failedExtractionDoesNotFabricateOriginalContentForTwitterStatusUrls() {
        val twitterUrl = "https://x.com/i/status/2051891753821556976"

        assertFailsWith<IllegalStateException> {
            failedExtractionFallback(twitterUrl)
        }
        assertFailsWith<IllegalStateException> {
            failedExtractionFallback("https://example.com/article")
        }
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
        assertEquals(false, parsed.summary.contains("# 标题"))
        assertEquals(false, parsed.summary.contains("## 核心内容"))
        assertEquals(false, parsed.summary.contains("## 核心观点"))
        assertEquals(true, parsed.summary.contains("正文摘要。"))
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
    fun existingArticleOriginalContentCanBeReusedAsAiInputWithoutUrlExtraction() {
        val extracted = existingArticleOriginalExtractedContent(
            title = "远程文章标题",
            aiTitle = "AI 标题",
            aiContent = "旧摘要",
            aiMarkdownContent = "远程 API 已返回的原文内容",
            coverImageUrl = "https://example.com/cover.jpg",
        )

        assertEquals("远程文章标题", extracted?.title)
        assertEquals("远程 API 已返回的原文内容", extracted?.content)
        assertEquals(null, extracted?.htmlContent)
        assertEquals("https://example.com/cover.jpg", extracted?.coverImageUrl)
    }

    @Test
    fun articleMarkdownInputSupportsPlainTextOriginalsFromRemoteApis() {
        val extracted = ExtractedContent(
            title = "Codex 新功能",
            content = "第一段很长。第二段也很长。第三段继续解释功能。",
            htmlContent = null,
            coverImageUrl = null,
        )

        val input = articleMarkdownInput(extracted, "gpt-5")

        assertEquals(true, input.startsWith("正文文本：\n"))
        assertEquals(true, input.contains("第一段很长。第二段也很长。"))
        assertEquals(false, input.contains("正文 HTML："))
    }

    @Test
    fun extractedContentUsesReadableContentBeforeVisibleTextForAiSummary() {
        val page = WebViewPageContent(
            html = "<html><body><main>HTML正文</main></body></html>",
            text = "导航 登录 推荐",
            readableTitle = "Readability 标题",
            readableContent = "<article><p>Readability 正文</p></article>",
        )

        assertEquals("Readability 正文", page.summaryTextOrHtmlFallback())
    }

    @Test
    fun extractedContentUsesWebViewVisibleTextWhenReadableContentIsBlank() {
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

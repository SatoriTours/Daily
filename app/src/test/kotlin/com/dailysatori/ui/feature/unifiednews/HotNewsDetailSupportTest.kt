package com.dailysatori.ui.feature.unifiednews

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HotNewsDetailSupportTest {

    @Test
    fun readingTimeReturnsNullForEmptyOrBlankContent() {
        assertNull(calculateHotNewsReadingTimeMinutes(""))
        assertNull(calculateHotNewsReadingTimeMinutes("   \n\t  "))
        assertNull(calculateHotNewsReadingTimeMinutes("```\n\n```"))
        assertNull(calculateHotNewsReadingTimeMinutes("# * _ ` ~ > |"))
    }

    @Test
    fun readingTimeCalculatesMinutesAtApprox350CharsPerMinute() {
        val shortText = "这是一段简短的内容。".repeat(5) // ~50 chars
        assertEquals(1, calculateHotNewsReadingTimeMinutes(shortText))

        val exactOneMin = "字".repeat(350)
        assertEquals(1, calculateHotNewsReadingTimeMinutes(exactOneMin))

        val justOverOneMin = "字".repeat(351)
        assertEquals(2, calculateHotNewsReadingTimeMinutes(justOverOneMin))

        val twoMins = "字".repeat(700)
        assertEquals(2, calculateHotNewsReadingTimeMinutes(twoMins))
    }

    @Test
    fun readingTimeStripsMarkdownFormattingAndUrlsWithoutInflatingCount() {
        // Link target should not count towards reading characters, but link text should
        val longUrl = "https://example.com/very/long/path/with/lots/of/parameters/and/tokens?a=1&b=2"
        val normalText = "[文章标题]($longUrl)"
        assertEquals(1, calculateHotNewsReadingTimeMinutes(normalText))

        // Raw bare url should be stripped so it does not inflate reading time
        val bareUrl = "https://example.com/long/path/here"
        assertNull(calculateHotNewsReadingTimeMinutes(bareUrl))
    }

    @Test
    fun readingTimeIncludesVisibleCodeAndImagesCaptionsReasonably() {
        val markdownWithCode = """
            # 标题
            下面是关键代码：
            ```kotlin
            val x = 10
            println(x)
            ```
            以及说明文本。
        """.trimIndent()
        // Visible code prose inside code fence should be included in reading time!
        val minutes = calculateHotNewsReadingTimeMinutes(markdownWithCode)
        assertEquals(1, minutes)
    }

    @Test
    fun contentLabelResolvesToExpectedKind() {
        // Summary tab is always AI summary
        assertEquals(
            HotNewsContentLabel.AI_SUMMARY,
            resolveHotNewsContentLabel(
                isBodySelected = false,
                hasTranslation = false,
                isOriginalChinese = false,
            )
        )
        assertEquals(
            HotNewsContentLabel.AI_SUMMARY,
            resolveHotNewsContentLabel(
                isBodySelected = false,
                hasTranslation = true,
                isOriginalChinese = false,
            )
        )

        // Body tab with translation is Chinese translation
        assertEquals(
            HotNewsContentLabel.CHINESE_TRANSLATION,
            resolveHotNewsContentLabel(
                isBodySelected = true,
                hasTranslation = true,
                isOriginalChinese = false,
            )
        )

        // Body tab without translation: if original is Chinese -> Chinese original, otherwise -> Original
        assertEquals(
            HotNewsContentLabel.CHINESE_ORIGINAL,
            resolveHotNewsContentLabel(
                isBodySelected = true,
                hasTranslation = false,
                isOriginalChinese = true,
            )
        )
        assertEquals(
            HotNewsContentLabel.ORIGINAL,
            resolveHotNewsContentLabel(
                isBodySelected = true,
                hasTranslation = false,
                isOriginalChinese = false,
            )
        )
    }

    @Test
    fun isChineseOriginalContentEvaluatesActualBodyAndRejectsEnglishWithChineseTitle() {
        // 1. Genuine Chinese body
        val chineseBody = "这是一篇纯中文的技术分析文章，详细介绍了系统的架构与实现细节。"
        assertTrue(isChineseOriginalContent(chineseBody))

        // 2. English body with a Chinese title or brief Chinese phrase
        val englishBodyWithChineseTitle = """
            中文标题：关于新架构的讨论

            This is a comprehensive English article detailing the complete architecture, implementation,
            performance evaluation, and deployment guidelines for the new distributed consensus mechanism.
            Every component is built with scalability, fault tolerance, and security in mind.
        """.trimIndent()
        assertFalse(isChineseOriginalContent(englishBodyWithChineseTitle))

        // 3. English body with some Chinese annotations
        val englishBody = "The quick brown fox jumps over the lazy dog repeatedly to test English text detection."
        assertFalse(isChineseOriginalContent(englishBody))

        // 4. Blank or null
        assertFalse(isChineseOriginalContent(""))
        assertFalse(isChineseOriginalContent(null))
    }

    @Test
    fun metaStatusPrioritizesLoadingThenFailedThenReady() {
        assertEquals(
            HotNewsMetaStatus.PROCESSING,
            resolveHotNewsMetaStatus(isLoading = true, isFailed = false)
        )
        assertEquals(
            HotNewsMetaStatus.PROCESSING,
            resolveHotNewsMetaStatus(isLoading = true, isFailed = true)
        )
        assertEquals(
            HotNewsMetaStatus.FAILED,
            resolveHotNewsMetaStatus(isLoading = false, isFailed = true)
        )
        assertEquals(
            HotNewsMetaStatus.READY,
            resolveHotNewsMetaStatus(isLoading = false, isFailed = false)
        )
    }

    @Test
    fun formatHotNewsSummaryMarkdownExtractsSectionsWithoutLosingContent() {
        val raw = """
            **核心内容：** RepRapMicron 希望打造微米级 3D 打印机。

            **为什么重要（分析）：** 微米制造能让更多人获取实验设备。

            **项目状态：** 正在开发中。
        """.trimIndent()

        val formatted = formatHotNewsSummaryMarkdown(raw)

        assertTrue(formatted.contains("### 核心内容"))
        assertTrue(formatted.contains("RepRapMicron 希望打造微米级 3D 打印机。"))
        assertTrue(formatted.contains("### 为什么重要（分析）"))
        assertTrue(formatted.contains("微米制造能让更多人获取实验设备。"))
        assertTrue(formatted.contains("### 项目状态"))
        assertTrue(formatted.contains("正在开发中。"))
    }

    @Test
    fun formatHotNewsSummaryMarkdownLeavesCodeFencesAndPlainMarkdownIntact() {
        val rawWithCode = """
            ```markdown
            **核心内容：** 这在代码块内，不应被分节改写
            ```

            ~~~markdown
            **为什么重要（分析）：** 这在波浪号代码块内，不应被分节改写
            ~~~

                **项目状态：** 4空格缩进代码块内，不应被分节改写

            - 列表项 1
            - [链接](https://example.com)
        """.trimIndent()

        val formatted = formatHotNewsSummaryMarkdown(rawWithCode)

        assertTrue(formatted.contains("```markdown\n**核心内容：** 这在代码块内，不应被分节改写\n```"))
        assertTrue(formatted.contains("~~~markdown\n**为什么重要（分析）：** 这在波浪号代码块内，不应被分节改写\n~~~"))
        assertTrue(formatted.contains("    **项目状态：** 4空格缩进代码块内，不应被分节改写"))
        assertTrue(formatted.contains("- 列表项 1"))
        assertTrue(formatted.contains("[链接](https://example.com)"))
    }

    @Test
    fun shouldShowDiscussionOnlyWhenUrlDistinct() {
        assertFalse(shouldShowHotNewsDiscussion(itemUrl = "https://example.com", discussionUrl = null))
        assertFalse(shouldShowHotNewsDiscussion(itemUrl = "https://example.com", discussionUrl = ""))
        assertFalse(shouldShowHotNewsDiscussion(itemUrl = "https://example.com", discussionUrl = "https://example.com"))
        assertTrue(shouldShowHotNewsDiscussion(itemUrl = "https://example.com", discussionUrl = "https://news.ycombinator.com/item?id=1"))
    }
}

package com.dailysatori.ui.feature.unifiednews

import java.time.LocalDate
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnifiedNewsBriefingLayoutTest {
    private val today = LocalDate.of(2026, 11, 28)

    @Test
    fun `top bar title uses today title for today and localized month-day for history`() {
        assertEquals(
            "今日要闻",
            resolveBriefingTitle(
                summaryDate = "2026-11-28",
                todayTitle = "今日要闻",
                pastTitle = "当日要闻",
                dateTitleTemplate = "%1\$s要闻",
                today = today,
                locale = Locale.SIMPLIFIED_CHINESE,
            ),
        )
        assertEquals(
            "11月16日要闻",
            resolveBriefingTitle(
                summaryDate = "2026-11-16",
                todayTitle = "今日要闻",
                pastTitle = "当日要闻",
                dateTitleTemplate = "%1\$s要闻",
                today = today,
                locale = Locale.SIMPLIFIED_CHINESE,
            ),
        )
        assertEquals(
            "1月5日要闻",
            resolveBriefingTitle(
                summaryDate = "2026-01-05",
                todayTitle = "今日要闻",
                pastTitle = "当日要闻",
                dateTitleTemplate = "%1\$s要闻",
                today = today,
                locale = Locale.SIMPLIFIED_CHINESE,
            ),
        )
        assertEquals(
            "Nov 16 Headlines",
            resolveBriefingTitle(
                summaryDate = "2026-11-16",
                todayTitle = "Today’s headlines",
                pastTitle = "Headlines",
                dateTitleTemplate = "%1\$s Headlines",
                today = today,
                locale = Locale.US,
            ),
        )
    }

    @Test
    fun `top bar title falls back safely on blank or malformed dates`() {
        for (badDate in listOf(null, "", "   ", "2026/11/16", "2026-13-40", "not-a-date")) {
            val spec = briefingTitleSpec(badDate, today = today, locale = Locale.SIMPLIFIED_CHINESE)
            assertEquals(false, spec.isToday)
            assertNull(spec.formattedMonthDay)
            assertEquals(
                "当日要闻",
                resolveBriefingTitle(
                    summaryDate = badDate,
                    todayTitle = "今日要闻",
                    pastTitle = "当日要闻",
                    dateTitleTemplate = "%1\$s要闻",
                    today = today,
                    locale = Locale.SIMPLIFIED_CHINESE,
                ),
            )
        }
    }

    @Test
    fun `standard briefing moves only key points ahead of overview and preserves remaining section order`() {
        val content = """
            ## 今日要闻
            开源与 AI 代理工具是今日科技热点主线。[H1][R2]

            第二段补充社区生态变化。[H3]

            ## 今日要点
            - Univer 开源 Office SDK 支持嵌入表格与文档编辑 [H1]
            - **AG-UI**：以事件协议把 AI 代理接入前端应用 [R2][H1]
            - 社区项目尝试让网页版 AI 控制本地文件充当 Agent [H3]
            - 开发者分享 DeepSeek Harness 从编码到部署的完整流程 [R4]
            - 第五条要点 [R5]
            - 第六条要点，没有引用

            ## 重要变化
            - 协议层转向事件流 [R2]

            ## 值得关注
            - 关注权限隔离 [H3]
        """.trimIndent()

        val doc = assertIs<BriefingDocument.Structured>(parseBriefingDocument(content))
        assertEquals(6, doc.keyPoints.size)
        assertEquals("Univer 开源 Office SDK 支持嵌入表格与文档编辑 [H1]", doc.keyPoints[0].markdown)
        assertEquals(listOf("H1"), doc.keyPoints[0].citations)
        assertEquals("**AG-UI**：以事件协议把 AI 代理接入前端应用 [R2][H1]", doc.keyPoints[1].markdown)
        assertEquals(listOf("R2", "H1"), doc.keyPoints[1].citations)
        assertEquals("第六条要点，没有引用", doc.keyPoints[5].markdown)
        assertEquals(emptyList(), doc.keyPoints[5].citations)

        assertEquals(3, doc.sections.size)
        val overview = assertIs<BriefingSection.Overview>(doc.sections[0])
        assertTrue(overview.markdown.contains("开源与 AI 代理工具是今日科技热点主线。[H1][R2]"))
        assertTrue(overview.markdown.contains("第二段补充社区生态变化。[H3]"))

        val changes = assertIs<BriefingSection.Other>(doc.sections[1])
        assertEquals("重要变化", changes.title)
        assertEquals("- 协议层转向事件流 [R2]", changes.markdown)

        val watch = assertIs<BriefingSection.Other>(doc.sections[2])
        assertEquals("值得关注", watch.title)
        assertEquals("- 关注权限隔离 [H3]", watch.markdown)
    }

    @Test
    fun `legacy headings with emojis daily cover and list-style overview are preserved without losing content`() {
        val content = """
            # 今日统一新闻总结

            ## 🗞️ 每日封面
            - 主线一：开源协作提速 [R1]
            - 主线二：代理协议统一 [R2]

            ## 📌 今日要点：
            1. 第一条有序要点 [R1]
            2) 第二条括号要点 [R2]
            3、第三条顿号要点
              补充细节行

            ## 🔍 深度观察
            ### 子主题
            - 观察条目 [R1]
        """.trimIndent()

        val doc = assertIs<BriefingDocument.Structured>(parseBriefingDocument(content))
        assertEquals(3, doc.keyPoints.size)
        assertEquals("第一条有序要点 [R1]", doc.keyPoints[0].markdown)
        assertEquals("第二条括号要点 [R2]", doc.keyPoints[1].markdown)
        assertEquals("第三条顿号要点\n补充细节行", doc.keyPoints[2].markdown)

        assertEquals(2, doc.sections.size)
        val overview = assertIs<BriefingSection.Overview>(doc.sections[0])
        assertEquals("- 主线一：开源协作提速 [R1]\n- 主线二：代理协议统一 [R2]", overview.markdown)

        val observation = assertIs<BriefingSection.Other>(doc.sections[1])
        assertEquals("深度观察", observation.title)
        assertTrue(observation.markdown.contains("### 子主题"))
        assertTrue(observation.markdown.contains("- 观察条目 [R1]"))
    }

    @Test
    fun `overview without key points stays structured and does not invent points`() {
        val content = """
            ## 今日要闻
            今天只有综述段落，没有要点栏目。[R1]

            ## 值得关注
            - 关注项 [R1]
        """.trimIndent()

        val doc = assertIs<BriefingDocument.Structured>(parseBriefingDocument(content))
        assertEquals(emptyList(), doc.keyPoints)
        assertEquals(2, doc.sections.size)
        assertIs<BriefingSection.Overview>(doc.sections[0])
        assertEquals("值得关注", assertIs<BriefingSection.Other>(doc.sections[1]).title)
    }

    @Test
    fun `preamble before key points without overview heading is preserved as overview`() {
        val content = """
            # 今日统一新闻总结

            今天科技圈聚焦开源工具。

            ## 今日要点
            - 要点一 [R1]
        """.trimIndent()

        val doc = assertIs<BriefingDocument.Structured>(parseBriefingDocument(content))
        assertEquals(1, doc.keyPoints.size)
        val overview = assertIs<BriefingSection.Overview>(doc.sections.single())
        assertEquals("今天科技圈聚焦开源工具。", overview.markdown)
    }

    @Test
    fun `plain text or headingless legacy summary falls back safely without dropping anything`() {
        val plain = "今天来源不足，无法可靠生成完整简报。"
        assertEquals(BriefingDocument.Fallback(plain), parseBriefingDocument(plain))

        val legacyNoSections = """
            # 今日统一新闻总结

            今天概览段落。

            - 旧新闻一 [R1]
            - 旧新闻二 [R2]
        """.trimIndent()
        val fallback = assertIs<BriefingDocument.Fallback>(parseBriefingDocument(legacyNoSections))
        assertTrue(fallback.rawMarkdown.contains("今天概览段落。"))
        assertTrue(fallback.rawMarkdown.contains("- 旧新闻一 [R1]"))
        assertTrue(fallback.rawMarkdown.contains("- 旧新闻二 [R2]"))
    }

    @Test
    fun `unsafe key points structure or unclosed fence falls back to original markdown`() {
        val mixedKeyPoints = """
            ## 今日要闻
            综述内容。

            ## 今日要点
            这里有一段无序说明文字，不是列表项。
            - 第一条 [R1]
        """.trimIndent()
        assertIs<BriefingDocument.Fallback>(parseBriefingDocument(mixedKeyPoints))

        val unclosedFence = """
            ## 今日要闻
            ```kotlin
            val x = 1
            ## 今日要点
            - 不应被拆出的代码行 [R1]
        """.trimIndent()
        assertIs<BriefingDocument.Fallback>(parseBriefingDocument(unclosedFence))
    }

    @Test
    fun `headings inside closed code fence do not split sections`() {
        val content = """
            ## 今日要闻
            示例代码：
            ```markdown
            ## 今日要点
            - 伪要点 [R9]
            ```

            ## 今日要点
            - 真正要点 [R1]
        """.trimIndent()

        val doc = assertIs<BriefingDocument.Structured>(parseBriefingDocument(content))
        assertEquals(listOf("真正要点 [R1]"), doc.keyPoints.map { it.markdown })
        val overview = assertIs<BriefingSection.Overview>(doc.sections.single())
        assertTrue(overview.markdown.contains("## 今日要点"))
        assertTrue(overview.markdown.contains("- 伪要点 [R9]"))
    }

    @Test
    fun `blank content returns empty document`() {
        assertEquals(BriefingDocument.Empty, parseBriefingDocument("   \n\t  "))
    }

    @Test
    fun `inline citation linkifier links only matched citations and preserves markdown links and code`() {
        val input = "查看 [官网](https://example.com) 与 `[R1]`，主线参考 [R1][H2] 及未匹配 [R9]。"
        val output = linkifyBriefingCitations(input, availableRefKeys = setOf("R1", "H2"))

        assertEquals(
            "查看 [官网](https://example.com) 与 `[R1]`，主线参考 [R1](daily-satori-citation://R1) [H2](daily-satori-citation://H2) 及未匹配 [R9]。",
            output,
        )
    }

    @Test
    fun `inline citation linkifier leaves all citations plain when sources are empty`() {
        val input = "开源与 AI 代理工具 [R1][H2]"
        assertEquals(input, linkifyBriefingCitations(input, availableRefKeys = emptySet()))
    }

    @Test
    fun `standalone and mixed bodyless unknown headings are never dropped or treated as empty`() {
        val standalone = "## 独立标题"
        val standaloneDoc = parseBriefingDocument(standalone)
        assertIs<BriefingDocument.Fallback>(standaloneDoc)
        assertEquals(standalone, standaloneDoc.rawMarkdown)

        val mixed = """
            ## 今日要闻
            综述正文。[R1]

            ## 今日要点
            - 要点一 [R1]

            ## 独立标题

            ## 值得关注
            - 关注项 [R1]
        """.trimIndent()
        val mixedDoc = parseBriefingDocument(mixed)
        when (mixedDoc) {
            is BriefingDocument.Structured -> {
                assertEquals(listOf("要点一 [R1]"), mixedDoc.keyPoints.map { it.markdown })
                assertTrue(
                    mixedDoc.sections.any { it is BriefingSection.Other && it.title == "独立标题" },
                    "Unknown heading must be retained in structured sections",
                )
            }
            is BriefingDocument.Fallback -> assertTrue(mixedDoc.rawMarkdown.contains("## 独立标题"))
            BriefingDocument.Empty -> error("Non-empty mixed document must not become Empty")
        }

        val knownEmptyPlaceholder = """
            ## 今日要闻
            只有综述。[R1]

            ## 今日要点
        """.trimIndent()
        val placeholderDoc = assertIs<BriefingDocument.Structured>(parseBriefingDocument(knownEmptyPlaceholder))
        assertEquals(emptyList(), placeholderDoc.keyPoints)
        assertEquals(1, placeholderDoc.sections.size)
        assertIs<BriefingSection.Overview>(placeholderDoc.sections.single())
    }

    @Test
    fun `multi-paragraph key point preserves paragraph break and complex nesting falls back safely`() {
        val twoParagraphPoint = """
            ## 今日要点
            - 首段 [R1]

              次段 [R2]
        """.trimIndent()
        val doc = parseBriefingDocument(twoParagraphPoint)
        when (doc) {
            is BriefingDocument.Structured -> {
                val point = doc.keyPoints.single()
                assertTrue(point.markdown.contains("\n\n"), "Blank line between paragraphs must be preserved: ${point.markdown}")
                assertEquals("首段 [R1]\n\n次段 [R2]", point.markdown)
                assertEquals(listOf("R1", "R2"), point.citations)
            }
            is BriefingDocument.Fallback -> assertTrue(doc.rawMarkdown.contains("首段 [R1]\n\n  次段 [R2]"))
            BriefingDocument.Empty -> error("Non-empty key points must not become Empty")
        }

        val complexNested = """
            ## 今日要点
            - 首段 [R1]

                  深层缩进代码块 [R2]
        """.trimIndent()
        assertIs<BriefingDocument.Fallback>(parseBriefingDocument(complexNested))
    }

    @Test
    fun `markdown reference links and definitions are preserved and cross-section docs fall back intact`() {
        val refDoc = "[说明][R1]\n\n[R1]: https://example.org"
        assertEquals(refDoc, linkifyBriefingCitations(refDoc, availableRefKeys = setOf("R1")))

        val collapsedAndShortcut = "主文 [R1][] 与 [R1]，另外参考 [H2]。\n\n[R1]: https://example.org"
        assertEquals(
            "主文 [R1][] 与 [R1]，另外参考 [H2](daily-satori-citation://H2)。\n\n[R1]: https://example.org",
            linkifyBriefingCitations(collapsedAndShortcut, availableRefKeys = setOf("R1", "H2")),
        )

        val crossSection = """
            ## 今日要闻
            综述引用 [说明][R1]。

            ## 今日要点
            - 要点引用 [说明][R1]

            [R1]: https://example.org
        """.trimIndent()
        val parsed = assertIs<BriefingDocument.Fallback>(parseBriefingDocument(crossSection))
        assertTrue(parsed.rawMarkdown.contains("[R1]: https://example.org"))
    }
}

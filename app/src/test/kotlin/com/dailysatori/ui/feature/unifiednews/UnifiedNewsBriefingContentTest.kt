package com.dailysatori.ui.feature.unifiednews

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UnifiedNewsBriefingContentTest {
    @Test
    fun `extracts lead and citation points from summary markdown`() {
        val model = unifiedNewsBriefingContent(
            """
            # 今日统一新闻总结

            今天 AI 工具开始从功能展示走向团队治理。

            - **AI 编程工具强调治理** [R1]
            - 消费电子新品集中发布 [R2]
            """.trimIndent(),
        )

        assertEquals("今天 AI 工具开始从功能展示走向团队治理。", model.lead)
        assertEquals("今日封面", model.title)
        assertEquals(2, model.points.size)
        assertEquals("AI 编程工具强调治理", model.points[0].text)
        assertEquals("R1", model.points[0].citation)
        assertEquals("消费电子新品集中发布", model.points[1].text)
        assertEquals("R2", model.points[1].citation)
    }

    @Test
    fun `falls back when there are no citation points`() {
        val model = unifiedNewsBriefingContent("只有一段普通总结，没有引用。")

        assertEquals("今日封面", model.title)
        assertEquals("只有一段普通总结，没有引用。", model.lead)
        assertEquals(emptyList(), model.points)
    }

    @Test
    fun `plain markdown without citation remains available for fallback`() {
        val model = unifiedNewsBriefingContent(
            """
            今天市场关注 AI 硬件。

            普通段落继续保留给 CitationText fallback。
            """.trimIndent(),
        )

        assertEquals("今日封面", model.title)
        assertEquals("今天市场关注 AI 硬件。", model.lead)
        assertEquals(0, model.points.size)
    }

    @Test
    fun `ignores headings when selecting lead`() {
        val model = unifiedNewsBriefingContent(
            """
            ## 科技
            第一段真正导语。
            - 新闻条目 [R1]
            """.trimIndent(),
        )

        assertEquals("第一段真正导语。", model.lead)
    }

    @Test
    fun `uses daily cover section as magazine lead`() {
        val model = unifiedNewsBriefingContent(
            """
            ## 每日封面
            今天的封面导语应该直接展示在封面区域。

            ## 今日要点
            - AI 工具进入治理阶段 [R1]
            """.trimIndent(),
        )

        assertEquals("今天的封面导语应该直接展示在封面区域。", model.lead)
        assertEquals(1, model.points.size)
    }

    @Test
    fun `uses cited daily cover paragraph as lead without turning it into story row`() {
        val model = unifiedNewsBriefingContent(
            """
            ## 每日封面
            今天市场主线集中在 AI 基建与终端落地 [R1]

            ## 今日要点
            - 其他新闻 [R2]
            """.trimIndent(),
        )

        assertEquals("今天市场主线集中在 AI 基建与终端落地", model.lead)
        assertEquals(1, model.points.size)
        assertEquals("其他新闻", model.points.single().text)
    }

    @Test
    fun `empty content has no lead and default title`() {
        val model = unifiedNewsBriefingContent("   ")

        assertEquals("今日封面", model.title)
        assertNull(model.lead)
        assertEquals(emptyList(), model.points)
    }
}

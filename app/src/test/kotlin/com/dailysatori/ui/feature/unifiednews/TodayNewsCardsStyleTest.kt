package com.dailysatori.ui.feature.unifiednews

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Source-level layout contracts; device rendering is checked separately before release. */
class TodayNewsCardsStyleTest {
    private val cards = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/TodayNewsCards.kt").readText()

    @Test
    fun headlineRowsKeepOriginalPointsAndReportClicksWithoutArticlePreviews() {
        val row = cards.substringAfter("private fun DailyHeadlineStoryRow(")
            .substringBefore("@Composable")

        assertTrue(row.contains("clickable(enabled = source != null)"))
        assertTrue(row.contains("source?.let(onOpen)"))
        assertTrue(row.contains("Text(story.title"))
        assertFalse(row.contains("story.description"), "要点不再拼接来源摘要、正文或原始 HTML")
        assertFalse(row.contains("maxLines"), "要点必须完整展示")
        assertFalse(row.contains("source.source_"), "来源只在报道详情展示，不占用要闻行")
        assertFalse(row.contains("OpenInNew"))
        assertFalse(row.contains("news_focus_view_report"))
    }

    @Test
    fun focusCardsKeepReadableContentAndCompactMetadataWithIndependentTopicAction() {
        val card = cards.substringAfter("internal fun TodayNewsOpportunityCard(")
        val title = card.indexOf("Text(item.title")
        val description = card.indexOf("Text(description")
        val metadata = card.indexOf("listOf(item.category, item.article.source)")

        assertTrue(title >= 0 && description > title && metadata > description,
            "标题和摘要优先，分类与来源合并为底部元信息")
        assertFalse(card.contains("Card(onClick"), "关注条目不再使用卡片容器")
        assertTrue(card.contains("if (showDivider) HorizontalDivider"), "只在条目之间使用分隔线")
        assertTrue(card.contains("onClickLabel = stringResource(R.string.news_focus_view_opportunity)"),
            "详情操作保留无障碍说明")
        assertFalse(card.contains("maxLines"), "长标题和摘要完整展示")
        assertFalse(card.contains("Height.buttonSmall"), "点子操作保留默认触摸区域")
        assertTrue(card.contains("onClick = onOpen"))
        assertTrue(card.contains("TextButton(onClick = onTopicAction"))
        assertTrue(card.contains("opportunityActionLabelKey(isCaptured)"), "保留收为点子和查看已有点子的入口")
    }

    @Test
    fun focusLabelsUseNeutralLanguageInBothLocales() {
        assertEquals("重点关注", label("values", "news_focus_for_you"))
        assertEquals("Highlights", label("values-en", "news_focus_for_you"))
        assertEquals("查看详情", label("values", "news_focus_view_opportunity"))
        assertEquals("View details", label("values-en", "news_focus_view_opportunity"))
        assertEquals("重点关注 · %1\$d 条", label("values", "news_focus_recommended_count"))
        assertEquals("Highlights · %1\$d items", label("values-en", "news_focus_recommended_count"))
    }

    private fun label(locale: String, key: String): String {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/$locale/news_focus.xml"))
        val strings = document.getElementsByTagName("string")
        return (0 until strings.length).map(strings::item)
            .single { it.attributes.getNamedItem("name").nodeValue == key }.textContent
    }
}

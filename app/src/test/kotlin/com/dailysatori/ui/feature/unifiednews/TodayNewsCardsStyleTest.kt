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
    fun headlineRowsKeepTheReportClickWithoutASeparateSourceLine() {
        val row = cards.substringAfter("private fun DailyHeadlineStoryRow(")
            .substringBefore("@Composable")

        assertTrue(row.contains("clickable(enabled = source != null)"))
        assertTrue(row.contains("source?.let(onOpen)"))
        assertTrue(row.contains("Text(story.title"))
        assertTrue(row.contains("Text(description"))
        assertFalse(row.contains("source.source_"), "来源只在报道详情展示，不占用要闻行")
        assertFalse(row.contains("OpenInNew"))
        assertFalse(row.contains("news_focus_view_report"))
    }

    @Test
    fun focusCardsPutMetadataAndActionsBelowTheContent() {
        val card = cards.substringAfter("internal fun TodayNewsOpportunityCard(")
        val description = card.indexOf("Text(item.relevance.ifBlank { item.action }")
        val divider = card.indexOf("HorizontalDivider(")
        val source = card.indexOf("Text(item.article.source")

        assertTrue(description >= 0 && divider > description && source > divider,
            "关注卡片先展示分类、标题和摘要，再展示底部来源与操作")
        assertTrue(card.contains("Text(stringResource(R.string.news_focus_view_opportunity)"))
        assertTrue(card.contains("RoundedCornerShape(Radius.m)"))
        assertTrue(card.contains("onClick = onOpen"))
        assertTrue(card.contains("IconButton(onClick = onTopicAction"), "保留收为点子和查看已有点子的入口")
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

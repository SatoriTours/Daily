package com.dailysatori.ui.feature.book

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ViewpointCardLayoutTest {
    @Test
    fun shortViewpointCardsStayTopAligned() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt").readText()
        val body = source.extractCallBlock("fun ViewpointCard(")

        assertEquals(true, viewpointCardFillsAvailableHeight(fillAvailableHeight = true))
        assertEquals(false, viewpointCardFillsAvailableHeight(fillAvailableHeight = false))
        assertEquals(true, viewpointCardContentStartsAtTop())
        assertTrue(body.contains("Column("))
        assertTrue(body.contains("modifier = contentModifier"))
        assertTrue(body.contains(".verticalScroll(rememberScrollState())"))
    }

    @Test
    fun viewpointCardSplitsHeaderAndBodyHelpers() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt").readText()
        val body = source.extractCallBlock("fun ViewpointCard(")

        assertTrue(body.contains("ViewpointHeader("))
        assertTrue(body.contains("ViewpointBody("))
    }

    @Test
    fun viewpointTitleRemovesCurrentBookPrefix() {
        assertEquals(
            "用事实材料校正抽象判断中的理解偏差。",
            viewpointDisplayTitle("毛泽东选集（全四卷）：用事实材料校正抽象判断中的理解偏差。", "毛泽东选集（全四卷）"),
        )
        assertEquals(
            "用事实材料校正抽象判断中的理解偏差。",
            viewpointDisplayTitle("《毛泽东选集（全四卷）》：用事实材料校正抽象判断中的理解偏差。", "毛泽东选集（全四卷）"),
        )
        assertEquals(
            "组织先遇到局势混乱。",
            viewpointDisplayTitle("《毛泽东选集（全四卷）》: 组织先遇到局势混乱。", "毛泽东选集（全四卷）"),
        )
        assertEquals(
            "避免把愿望当事实。",
            viewpointDisplayTitle("《毛泽东选集（全四卷）》 ： 避免把愿望当事实。", "毛泽东选集（全四卷）"),
        )
        assertEquals(
            "不要用情绪代替事实",
            viewpointDisplayTitle("不要用情绪代替事实", "毛泽东选集（全四卷）"),
        )
    }

    @Test
    fun viewpointBookLineFormatsTitleAndAuthor() {
        assertEquals("《毛泽东选集（全四卷）》 · 毛泽东", viewpointBookLine("毛泽东选集（全四卷）", "毛泽东"))
        assertEquals("《毛泽东选集（全四卷）》 · 毛泽东", viewpointBookLine("《毛泽东选集（全四卷）》", "毛泽东"))
        assertEquals("《毛泽东选集（全四卷）》", viewpointBookLine("毛泽东选集（全四卷）", ""))
        assertEquals("", viewpointBookLine("", "毛泽东"))
    }

    @Test
    fun viewpointProgressCanBeHiddenForSingleReferenceDetail() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt").readText()
        val body = source.extractCallBlock("fun ViewpointCard(")

        assertTrue(body.contains("showProgress: Boolean = true"))
        assertTrue(body.contains("showProgress = showProgress"))
    }

    private fun String.extractCallBlock(anchor: String): String {
        assertTrue(contains(anchor), "Missing call anchor: $anchor")
        val start = indexOf(anchor)
        val bodyStart = indexOf('{', start)
        assertTrue(bodyStart >= 0, "Missing block body for call anchor: $anchor")

        var depth = 0
        for (index in bodyStart until length) {
            when (this[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return substring(start, index + 1)
                }
            }
        }
        throw AssertionError("Missing closing brace for call anchor: $anchor")
    }
}

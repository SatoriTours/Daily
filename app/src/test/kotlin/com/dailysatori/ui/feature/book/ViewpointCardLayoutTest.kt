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
        assertTrue(body.contains("reserveBottomSpace: Boolean = false"))
        assertTrue(body.contains("if (reserveBottomSpace)"))
        assertTrue(body.contains("Spacer(modifier = Modifier.height(bookReadingBottomSpace()))"))
        assertEquals(true, bookReadingBottomSpace() > com.dailysatori.ui.theme.Height.navBar)
    }

    @Test
    fun viewpointCardSplitsHeaderAndBodyHelpers() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt").readText()
        val body = source.extractCallBlock("fun ViewpointCard(")

        assertTrue(body.contains("ViewpointHeader("))
        assertTrue(body.contains("ViewpointBody("))
    }

    @Test
    fun viewpointCardUsesPureReadingLayoutWithoutCardChrome() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt").readText()
        val body = source.extractCallBlock("fun ViewpointCard(")

        assertTrue(body.contains("Column("))
        assertTrue(!body.contains("Surface("), "Approved reading layout must not wrap content in a card background")
        assertTrue(!source.contains("HorizontalDivider"), "Approved reading layout must not separate example with a line")
        assertTrue(!source.contains("BorderStroke"), "Approved reading layout must not add left-line or border dividers")
    }

    @Test
    fun viewpointCardKeepsPageCounterButNoProgressBar() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt").readText()

        assertTrue(source.contains("booksReadingProgressText(page, total)"))
        assertTrue(!source.contains("LinearProgressIndicator"), "Approved layout removes the bottom progress bar")
    }

    @Test
    fun viewpointCaseHeadingIsChineseAndVisuallyEmphasized() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt").readText()
        val caseBody = source.extractCallBlock("private fun ViewpointBody(")

        assertTrue(caseBody.contains("text = \"案例\""))
        assertTrue(caseBody.contains("Icons.AutoMirrored.Filled.Article"))
        assertTrue(caseBody.contains("MaterialTheme.typography.titleMedium"))
        assertTrue(caseBody.contains("Modifier.size(IconSize.m)"))
        assertTrue(caseBody.contains("modifier = Modifier.padding(top = Spacing.m)"))
        assertTrue(caseBody.contains("FontWeight.Bold"))
        assertTrue(!caseBody.contains("RoundedCornerShape(Radius.circular)"), "Case heading should not use the cramped pill background")
        assertTrue(!caseBody.contains("surfaceContainerHighest"), "Case heading should not use a background block")
        assertTrue(caseBody.contains("MaterialTheme.colorScheme.primary"), "Case heading should use app primary blue accent")
    }

    @Test
    fun viewpointCardUsesNewsAndDiarySizedTypography() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt").readText()
        val headerBody = source.extractCallBlock("private fun ViewpointHeader(")
        val body = source.extractCallBlock("private fun ViewpointBody(")

        assertTrue(headerBody.contains("MaterialTheme.typography.headlineSmall"))
        assertTrue(!headerBody.contains("headlineMedium"), "Book title should not use oversized headline typography")
        assertTrue(body.contains("MarkdownStyles.bookTypography()"), "Body should use shared book reading typography")
        assertTrue(!source.contains("fontSize ="), "Viewpoint typography should not hardcode font sizes")
    }

    @Test
    fun viewpointTitleIsCenteredAndCaseHeadingUsesPrimaryTextColor() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt").readText()
        val headerBody = source.extractCallBlock("private fun ViewpointHeader(")
        val caseBody = source.extractCallBlock("private fun ViewpointBody(")

        assertTrue(headerBody.contains("textAlign = TextAlign.Center"))
        assertTrue(caseBody.contains("color = MaterialTheme.colorScheme.primary"))
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

        assertTrue(body.contains("showProgress: Boolean = false"))
        assertTrue(body.contains("showProgress = showProgress"))
    }

    @Test
    fun viewpointCardShowsRetryForFailedGeneration() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt").readText()
        val body = source.extractCallBlock("fun ViewpointCard(")

        assertTrue(body.contains("status: String = \"ready\""))
        assertTrue(body.contains("onRetry: () -> Unit = {}"))
        assertTrue(source.contains("重新生成这个观点"))
        assertTrue(source.contains("正在重新生成这个观点"))
    }

    private fun String.extractCallBlock(anchor: String): String {
        assertTrue(contains(anchor), "Missing call anchor: $anchor")
        val start = indexOf(anchor)
        val signatureEnd = indexOf(") {", start)
        val bodyStart = if (signatureEnd >= 0) indexOf('{', signatureEnd) else -1
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

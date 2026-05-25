package com.dailysatori.ui.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainContentRhythmTest {
    @Test
    fun newsSummaryStartsCloserToTitleBar() {
        val news = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()
        val compactStatePadding = ".padding(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.s)"
        val summaryPage = news.extractCallBlock("private fun UnifiedNewsSummaryPage")
        val refreshMessage = news.extractBetween(
            start = "private fun UnifiedNewsRefreshMessage",
            end = "private fun UnifiedNewsGeneratingSkeleton",
        )
        val generatingSkeleton = news.extractBetween(
            start = "private fun UnifiedNewsGeneratingSkeleton",
            end = "private fun UnifiedNewsSourceDetailLoadingScreen",
        )

        assertTrue(summaryPage.contains("contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.m)"))
        assertFalse(summaryPage.contains("contentPadding = PaddingValues(Spacing.m)"))
        assertTrue(refreshMessage.contains(compactStatePadding))
        assertTrue(generatingSkeleton.contains(compactStatePadding))
    }

    @Test
    fun booksReadingUsesCompactHeaderAndReadableBody() {
        val spacing = File("src/main/kotlin/com/dailysatori/ui/theme/Spacing.kt").readText()
        val appTopBar = File("src/main/kotlin/com/dailysatori/ui/component/appbar/AppTopBar.kt").readText()
        val books = File("src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt").readText()
        val viewpoint = File("src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt").readText()
        val booksTopBar = books.extractCallExpression("AppTopBar(")
        val readingPager = books.extractCallBlock("HorizontalPager(")
        val viewpointBody = viewpoint.extractCallBlock("fun ViewpointCard(")
        val viewpointContent = viewpoint.extractCallBlock("private fun ViewpointBody(")

        assertTrue(spacing.contains("val appBarCompact = 48.dp"))
        assertTrue(appTopBar.contains("expandedHeight: Dp = Height.appBar"))
        assertTrue(appTopBar.contains("expandedHeight = expandedHeight"))
        assertTrue(booksTopBar.contains("expandedHeight = Height.appBarCompact"))
        assertTrue(readingPager.contains("modifier = Modifier.fillMaxSize()"))
        assertFalse(readingPager.contains("modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.m)"))
        assertTrue(viewpointBody.contains("verticalScroll(rememberScrollState())"))
        assertFalse(viewpointBody.contains("\n    Card("))
        assertTrue(countOccurrences(viewpointContent, "typography = MarkdownStyles.bookTypography()") >= 2)
        assertTrue(countOccurrences(viewpointContent, "padding = MarkdownStyles.cardPadding()") >= 2)
    }

    @Test
    fun mainCardsUseSharedPaddingAndMarkdownPreset() {
        val diary = File("src/main/kotlin/com/dailysatori/ui/component/card/DiaryCard.kt").readText()
        val viewpoint = File("src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt").readText()
        val message = File("src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt").readText()
        val citation = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/CitationText.kt").readText()
        val diaryBody = diary.extractCallBlock("fun DiaryCard(")
        val viewpointBody = viewpoint.extractCallBlock("fun ViewpointCard(")
        val viewpointHeader = viewpoint.extractCallBlock("private fun ViewpointHeader(")
        val viewpointContent = viewpoint.extractCallBlock("private fun ViewpointBody(")
        val messageBody = message.extractCallBlock("fun MessageBubble(")
        val citationBody = citation.extractCallBlock("fun CitationText(")

        assertTrue(diaryBody.contains("Modifier.padding(Spacing.m)"))
        assertTrue(diaryBody.contains("MarkdownStyles.cardTypography()"))
        assertTrue(diaryBody.contains("MarkdownStyles.cardPadding()"))
        assertFalse(viewpointBody.contains("shape = RoundedCornerShape(Radius.l)"))
        assertFalse(viewpointBody.contains("border = BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outline)"))
        assertTrue(viewpointHeader.contains("style = MaterialTheme.typography.headlineSmall"))
        assertTrue(viewpointContent.contains("MarkdownStyles.bookTypography()"))
        assertTrue(viewpointContent.contains("MarkdownStyles.cardPadding()"))
        assertTrue(viewpointHeader.contains("style = MaterialTheme.typography.labelMedium"))
        assertTrue(viewpointHeader.contains("color = MaterialTheme.colorScheme.onSurfaceVariant"))
        assertFalse(
            viewpointBody.contains(
                "bookTitle,\n" +
                    "                style = MaterialTheme.typography.titleSmall,\n" +
                    "                color = MaterialTheme.colorScheme.primary",
            ),
        )
        assertTrue(messageBody.contains("MarkdownStyles.cardTypography()"))
        assertTrue(messageBody.contains("MarkdownStyles.cardPadding()"))
        assertTrue(citationBody.contains("MarkdownStyles.summaryTypography()"))
        assertFalse(citationBody.contains("style = MaterialTheme.typography.bodyLarge"))
    }

    @Test
    fun bookViewpointUsesLargerReadingTypographyThanSharedCards() {
        val styles = File("src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt").readText()
        val viewpoint = File("src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt").readText()
        val viewpointHeader = viewpoint.extractCallBlock("private fun ViewpointHeader(")
        val viewpointBody = viewpoint.extractCallBlock("private fun ViewpointBody(")

        assertTrue(styles.contains("fun bookTypography(): MarkdownTypography = typographyFrom("))
        assertTrue(styles.contains("body = bookTextStyle()"))
        assertTrue(styles.contains("private fun bookTextStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy"))
        assertEquals(2, countOccurrences(viewpointBody, "typography = MarkdownStyles.bookTypography()"))
        assertTrue(viewpointHeader.contains("style = MaterialTheme.typography.headlineSmall"))
        assertTrue(viewpointHeader.contains("style = MaterialTheme.typography.labelMedium"))
        assertTrue(viewpointBody.contains("style = MaterialTheme.typography.titleMedium"))
    }

    private fun String.extractCallBlock(anchor: String): String {
        assertTrue(contains(anchor), "Missing call anchor: $anchor")
        val start = indexOf(anchor)
        val bodyStart = findBodyStart(start)
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

    private fun String.extractBetween(start: String, end: String): String {
        assertTrue(contains(start), "Missing start anchor: $start")
        val afterStart = substringAfter(start)
        assertTrue(afterStart.contains(end), "Missing end anchor after start: $end")
        return afterStart.substringBefore(end)
    }

    private fun String.extractCallExpression(anchor: String): String {
        assertTrue(contains(anchor), "Missing call anchor: $anchor")
        val start = indexOf(anchor)
        val parenStart = indexOf('(', start)
        assertTrue(parenStart >= 0, "Missing call paren for anchor: $anchor")

        var depth = 0
        for (index in parenStart until length) {
            when (this[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return substring(start, index + 1)
                }
            }
        }
        throw AssertionError("Missing closing paren for call anchor: $anchor")
    }

    private fun String.findBodyStart(start: Int): Int {
        var parenDepth = 0
        for (index in start until length) {
            when (this[index]) {
                '(' -> parenDepth++
                ')' -> if (parenDepth > 0) parenDepth--
                '{' -> if (parenDepth == 0) return index
            }
        }
        return -1
    }

    private fun countOccurrences(source: String, needle: String): Int =
        source.windowed(needle.length).count { it == needle }
}

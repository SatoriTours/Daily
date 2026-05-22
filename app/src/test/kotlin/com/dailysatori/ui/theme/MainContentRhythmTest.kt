package com.dailysatori.ui.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainContentRhythmTest {
    @Test
    fun newsSummaryStartsCloserToTitleBar() {
        val news = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()
        val compactStatePadding = ".padding(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.s)"
        val refreshMessage = news
            .substringAfter("private fun UnifiedNewsRefreshMessage")
            .substringBefore("private fun UnifiedNewsGeneratingSkeleton")
        val generatingSkeleton = news
            .substringAfter("private fun UnifiedNewsGeneratingSkeleton")
            .substringBefore("private fun UnifiedNewsSourceDetailLoadingScreen")

        assertTrue(news.contains("contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.m)"))
        assertFalse(news.contains("contentPadding = PaddingValues(Spacing.m)"))
        assertTrue(refreshMessage.contains(compactStatePadding))
        assertTrue(generatingSkeleton.contains(compactStatePadding))
    }

    @Test
    fun booksReadingUsesCompactHeaderAndReadableBody() {
        val books = File("src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt").readText()
        val viewpoint = File("src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt").readText()
        val readingPager = books.substringAfter("HorizontalPager(")
        val markdownBlocks = viewpoint.split("Markdown(").drop(1)

        assertTrue(books.contains("Modifier.fillMaxWidth().height(Height.listItemSmall)"))
        assertTrue(
            readingPager.contains(
                "modifier = Modifier.padding(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.m)",
            ),
        )
        assertFalse(readingPager.contains("modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.m)"))
        assertTrue(viewpoint.contains("Spacer(modifier = Modifier.height(Spacing.s))"))
        assertFalse(viewpoint.contains("Spacer(modifier = Modifier.height(Spacing.l))"))
        assertTrue(markdownBlocks.all { it.contains("typography = MarkdownStyles.cardTypography()") })
        assertTrue(markdownBlocks.all { it.contains("padding = MarkdownStyles.cardPadding()") })
    }

    @Test
    fun mainCardsUseSharedPaddingAndMarkdownPreset() {
        val diary = File("src/main/kotlin/com/dailysatori/ui/component/card/DiaryCard.kt").readText()
        val viewpoint = File("src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt").readText()
        val message = File("src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt").readText()
        val citation = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/CitationText.kt").readText()

        assertTrue(diary.contains("Modifier.padding(Spacing.m)"))
        assertTrue(diary.contains("MarkdownStyles.cardTypography()"))
        assertTrue(diary.contains("MarkdownStyles.cardPadding()"))
        assertTrue(viewpoint.contains("shape = RoundedCornerShape(Radius.l)"))
        assertTrue(viewpoint.contains("border = BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outline)"))
        assertTrue(viewpoint.contains("style = MaterialTheme.typography.titleMedium"))
        assertTrue(viewpoint.contains("MarkdownStyles.cardTypography()"))
        assertTrue(viewpoint.contains("MarkdownStyles.cardPadding()"))
        assertTrue(
            viewpoint.contains("style = MaterialTheme.typography.labelSmall") ||
                viewpoint.contains("style = MaterialTheme.typography.bodySmall"),
        )
        assertTrue(viewpoint.contains("color = MaterialTheme.colorScheme.onSurfaceVariant"))
        assertFalse(
            viewpoint.contains(
                "bookTitle,\n" +
                    "                style = MaterialTheme.typography.titleSmall,\n" +
                    "                color = MaterialTheme.colorScheme.primary",
            ),
        )
        assertTrue(message.contains("MarkdownStyles.cardTypography()"))
        assertTrue(message.contains("MarkdownStyles.cardPadding()"))
        assertTrue(citation.contains("style = MaterialTheme.typography.bodyMedium"))
        assertFalse(citation.contains("style = MaterialTheme.typography.bodyLarge"))
    }
}

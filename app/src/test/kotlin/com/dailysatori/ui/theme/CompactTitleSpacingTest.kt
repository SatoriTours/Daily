package com.dailysatori.ui.theme

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompactTitleSpacingTest {
    @Test
    fun appTopBarUsesCompactHeightToken() {
        val source = readProjectFile("app/src/main/kotlin/com/dailysatori/ui/component/appbar/AppTopBar.kt")

        assertTrue(source.contains("import com.dailysatori.ui.theme.Height"))
        assertTrue(source.contains("import androidx.compose.ui.unit.Dp"))
        assertTrue(source.contains("expandedHeight: Dp = Height.appBar"))
        assertTrue(source.contains("expandedHeight = expandedHeight"))
        assertTrue(source.contains("windowInsets = TopAppBarDefaults.windowInsets"))
        assertFalse(source.contains("Modifier.height(Height.appBar)"))
        assertFalse(source.contains("height(64.dp)"))
    }

    @Test
    fun localArticleDetailUsesCompactFirstBodyPadding() {
        val source = readProjectFile("app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt")
        val reader = readProjectFile("app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleAiReader.kt")
        assertTrue(source.substringBetween("private fun ArticleDetailBody(", "private fun localReaderTranslation(")
            .contains("ArticleReadingContent(summary,"))
        val firstBodyWrapper = reader.substringBetween("internal fun ArticleReadingContent(", "if (distinctSummary != null)")

        assertTrue(firstBodyWrapper.contains("Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)"))
        assertFalse(firstBodyWrapper.contains("Modifier.padding(Spacing.m)"))
    }

    @Test
    fun remoteArticleDetailUsesCompactFirstBodyPadding() {
        val source = readProjectFile("app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt")
        val firstBodyWrapper = source.substringBetween(
            start = "private fun RemoteArticleDetailBody(",
            end = "ArticleReaderBody(",
        )

        assertTrue(firstBodyWrapper.contains("Box(modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s))"))
        assertFalse(firstBodyWrapper.contains("Box(modifier = Modifier.padding(Spacing.m))"))
    }
}

private fun readProjectFile(path: String): String =
    (java.io.File(path).takeIf { it.exists() }
        ?: java.io.File(path.removePrefix("app/")))
        .readText()

private fun String.substringBetween(start: String, end: String): String {
    assertTrue(contains(start), "Missing start anchor: $start")
    val section = substringAfter(start)
    assertTrue(section.contains(end), "Missing end anchor after $start: $end")
    return section.substringBefore(end)
}

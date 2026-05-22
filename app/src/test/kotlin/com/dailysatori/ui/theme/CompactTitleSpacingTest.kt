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
        val firstBodyWrapper = source.substringBetween(
            start = "item(key = \"content-\$page\")",
            end = "MarkdownContent(",
        )

        assertTrue(firstBodyWrapper.contains("Box(modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s))"))
        assertFalse(firstBodyWrapper.contains("Box(modifier = Modifier.padding(Spacing.m))"))
    }

    @Test
    fun remoteArticleDetailUsesCompactFirstBodyPadding() {
        val source = readProjectFile("app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt")
        val firstBodyWrapper = source.substringBetween(
            start = "item(key = \"remote-content-\$page\")",
            end = "RemoteArticleMarkdownContent(",
        )

        assertTrue(firstBodyWrapper.contains("Box(modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s))"))
        assertFalse(firstBodyWrapper.contains("Box(modifier = Modifier.padding(Spacing.m))"))
    }
}

private fun readProjectFile(path: String): String =
    (java.io.File(path).takeIf { it.exists() }
        ?: java.io.File(path.removePrefix("app/")))
        .readText()

private fun String.substringBetween(start: String, end: String): String =
    substringAfter(start).substringBefore(end)

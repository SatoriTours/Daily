package com.dailysatori.ui.theme

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompactTitleSpacingTest {
    @Test
    fun appTopBarUsesCompactHeightToken() {
        val source = readProjectFile("app/src/main/kotlin/com/dailysatori/ui/component/appbar/AppTopBar.kt")

        assertTrue(source.contains("import com.dailysatori.ui.theme.Height"))
        assertTrue(source.contains("expandedHeight = Height.appBar"))
        assertTrue(source.contains("windowInsets = TopAppBarDefaults.windowInsets"))
        assertFalse(source.contains("Modifier.height(Height.appBar)"))
        assertFalse(source.contains("height(64.dp)"))
    }

    @Test
    fun localArticleDetailUsesCompactFirstBodyPadding() {
        val source = readProjectFile("app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt")

        assertTrue(source.contains("Box(modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s))"))
        assertFalse(source.contains("Box(modifier = Modifier.padding(Spacing.m))"))
    }

    @Test
    fun remoteArticleDetailUsesCompactFirstBodyPadding() {
        val source = readProjectFile("app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt")

        assertTrue(source.contains("Box(modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s))"))
        assertFalse(source.contains("Box(modifier = Modifier.padding(Spacing.m))"))
    }
}

private fun readProjectFile(path: String): String =
    (java.io.File(path).takeIf { it.exists() }
        ?: java.io.File(path.removePrefix("app/")))
        .readText()

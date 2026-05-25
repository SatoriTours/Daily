package com.dailysatori.ui.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadingComfortThemeTest {
    @Test
    fun markdownReadingPresetUsesComfortableContentRhythm() {
        val source = File("src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt").readText()

        assertTrue(source.contains("block = 14.dp"))
        assertTrue(source.contains("list = 10.dp"))
        assertTrue(source.contains("listItemBottom = 8.dp"))
        assertTrue(source.contains("indentList = 24.dp"))
        assertTrue(source.contains("codeBlock = PaddingValues(14.dp)"))
        assertTrue(source.contains("private fun readingTextStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = ContentFontFamily)"))
        assertTrue(source.contains("private fun headingStyle(style: TextStyle): TextStyle = style.copy(fontFamily = ContentFontFamily)"))
        assertFalse(source.contains("private fun readingTextStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = UiFontFamily)"))
    }

    @Test
    fun detailScreensUseComfortableReadingMargins() {
        val article = File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt").readText()
        val remoteArticle = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt").readText()
        val remoteDigest = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteDigestDetailScreen.kt").readText()
        val crayfish = File("src/main/kotlin/com/dailysatori/ui/feature/crayfishnews/CrayfishNewsDetailScreen.kt").readText()
        val articleContentItem = article.substringAfter("item(key = \"content-\$page\")").substringBefore("MarkdownContent(")
        val remoteArticleContentItem = remoteArticle.substringAfter("item(key = \"remote-content-\$page\")").substringBefore("RemoteArticleMarkdownContent(")

        assertTrue(articleContentItem.contains("horizontal = Spacing.l, vertical = Spacing.s"))
        assertTrue(remoteArticleContentItem.contains("horizontal = Spacing.l, vertical = Spacing.s"))
        assertTrue(remoteDigest.contains("contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.m)"))
        assertTrue(crayfish.contains("contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.m)"))
    }
}

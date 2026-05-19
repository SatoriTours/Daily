package com.dailysatori.ui.feature.remotenews

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteArticleDetailLayoutTest {
    @Test
    fun remoteArticleDetailDisplaysCoverUrlWithCollapsibleArticleCover() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt").readText()

        assertTrue(source.contains("val coverImage = article.coverUrl"))
        assertTrue(source.contains("RemoteArticleCoverImage("))
        assertTrue(source.contains("articleCoverHeightAfterScroll("))
        assertTrue(source.contains("originalImageUrls = listOfNotNull(article.coverUrl)"))
    }

    @Test
    fun remoteArticleDetailOrdersCoverThenTabsThenHeaderThenContent() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt").readText()
        val pagerContent = source.substringAfter("Column(modifier = Modifier.fillMaxSize()) {")

        val coverIndex = pagerContent.indexOf("RemoteArticleCoverImage(")
        val tabsIndex = pagerContent.indexOf("MarkdownTabRow(")
        val headerIndex = pagerContent.indexOf("RemoteArticleHeader(article)")
        val contentIndex = pagerContent.indexOf("LazyColumn(")

        assertTrue(coverIndex >= 0)
        assertTrue(tabsIndex > coverIndex)
        assertTrue(headerIndex > tabsIndex)
        assertTrue(contentIndex > headerIndex)
    }

    @Test
    fun remoteArticleHeaderUsesCompactMetadataInsteadOfHeavyHeroLabel() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt").readText()

        assertTrue(source.contains("RemoteArticleHeader(article)"))
        assertFalse(source.contains("阅读详情"))
        assertTrue(source.contains("articleRemoteMetaText(article)"))
    }

    @Test
    fun remoteArticleDetailUsesDedicatedReaderCardMarkdownStyle() {
        val screen = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt").readText()
        val styles = File("src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt").readText()

        assertTrue(screen.contains("RemoteArticleMarkdownContent("))
        assertFalse(screen.contains("import com.dailysatori.ui.component.content.MarkdownContent"))
        assertTrue(screen.contains("MarkdownStyles.remoteArticleTypography()"))
        assertTrue(screen.contains("MarkdownStyles.remoteArticlePadding()"))
        assertTrue(screen.contains("MaterialTheme.colorScheme.surfaceContainerLow"))
        assertTrue(styles.contains("fun remoteArticleTypography()"))
        assertTrue(styles.contains("fun remoteArticlePadding()"))
    }

    @Test
    fun remoteArticleDetailExposesFavoriteAction() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt").readText()

        assertTrue(source.contains("isFavorite: Boolean = false"))
        assertTrue(source.contains("onFavoriteClick: () -> Unit = {}"))
        assertTrue(source.contains("showFavoriteAction: Boolean = false"))
        assertTrue(source.contains("if (showFavoriteAction)"))
        assertTrue(source.contains("IconButton(onClick = onFavoriteClick)"))
        assertTrue(source.contains("Icons.Default.Favorite"))
        assertTrue(source.contains("Icons.Default.FavoriteBorder"))
        assertTrue(source.contains("contentDescription = if (isFavorite) \"取消收藏\" else \"收藏\""))
    }
}

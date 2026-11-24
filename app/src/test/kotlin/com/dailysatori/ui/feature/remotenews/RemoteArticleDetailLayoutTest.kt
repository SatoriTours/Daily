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
        assertTrue(source.contains("rememberArticleCoverNestedScrollConnection("))
        assertTrue(source.contains("originalImageUrls = listOfNotNull(article.coverUrl)"))
    }

    @Test
    fun remoteArticleDetailOrdersCoverThenHeaderThenSummaryContentWithoutTabs() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt").readText()
        val pagerContent = source.functionBody("RemoteArticleDetailPage")

        val coverIndex = pagerContent.indexOf("RemoteArticleCoverImage(")
        val headerIndex = pagerContent.indexOf("ArticleReadingTitle(")
        val contentIndex = pagerContent.indexOf("LazyColumn(")

        assertTrue(coverIndex >= 0)
        assertTrue(headerIndex > coverIndex)
        assertTrue(contentIndex > headerIndex)
        assertFalse(pagerContent.contains("intro = article.summary"))
        assertFalse(source.contains("MagazineArticleTabSelector("))
        assertFalse(source.contains("MarkdownTabPager("))
        assertFalse(source.contains("MarkdownTabRow(remoteArticleDetailTabTitles"))
    }

    @Test
    fun remoteArticleHeaderUsesCompactMetadataInsteadOfHeavyHeroLabel() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt").readText()

        assertTrue(source.contains("ArticleReadingTitle("))
        assertFalse(source.contains("MagazineArticleHeader("))
        assertFalse(source.contains("阅读详情"))
        assertTrue(source.contains("remoteArticleMetaChips(article)"))
    }

    @Test
    fun remoteArticleDetailUsesLightweightReaderWithoutBodySurface() {
        val screen = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt").readText()
        val styles = File("src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt").readText()
        val readerBody = screen.functionBody("RemoteArticleDetailBody")

        assertTrue(screen.contains("ArticleReaderBody("))
        assertFalse(screen.contains("MagazineArticleBody("))
        assertFalse(screen.contains("import com.dailysatori.ui.component.content.MarkdownContent"))
        assertTrue(screen.contains("MarkdownStyles.remoteArticleTypography()"))
        assertTrue(screen.contains("MarkdownStyles.remoteArticlePadding()"))
        assertFalse(readerBody.contains("border = BorderStroke"))
        assertTrue(styles.contains("fun remoteArticleTypography()"))
        assertTrue(styles.contains("fun remoteArticlePadding()"))
    }

    @Test
    fun remoteArticleDetailFunctionsStayFocused() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt").readText()

        listOf(
            "RemoteArticleDetailScreen",
            "RemoteArticleDetailPager",
            "RemoteArticleDetailPage",
            "RemoteArticleDetailBody",
        ).forEach { functionName ->
            assertTrue(source.functionLineCount(functionName) <= 50, "$functionName exceeds 50 lines")
        }
        val cover = File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailLayout.kt").readText()
        assertTrue(cover.functionLineCount("rememberArticleCoverNestedScrollConnection") <= 50)
    }

    @Test
    fun remoteArticleDetailExposesFavoriteAction() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt").readText()
        val actionsBody = source.functionBody("RemoteArticleDetailActions")

        assertTrue(source.contains("isFavorite: Boolean = false"))
        assertTrue(source.contains("onFavoriteClick: () -> Unit = {}"))
        assertTrue(source.contains("showFavoriteAction: Boolean = false"))
        assertTrue(source.contains("if (showFavoriteAction)"))
        assertTrue(actionsBody.contains("MoreActionsIcon"))
        assertTrue(actionsBody.contains("PageAction(i18n.t(\"article.more_actions\")") && actionsBody.contains("menuContent ="))
        val originalItem = actionsBody.indexOf("ArticleOriginalMenuItem(dismiss, onOriginalClick)")
        assertTrue(originalItem in 0 until actionsBody.indexOf("MarkNewsReadMenuItem("))
        assertTrue(actionsBody.contains("RemoteArticleFavoriteMenuItem("))
        assertTrue(actionsBody.contains("RemoteArticleOpenMenuItem("))
        assertFalse(actionsBody.contains("IconButton(onClick = onFavoriteClick)"))
        assertFalse(actionsBody.contains("IconButton(onClick = { openArticleUrl"))
        assertTrue(source.contains("Icons.Default.Favorite"))
        assertTrue(source.contains("Icons.Default.FavoriteBorder"))
        assertTrue(source.contains("i18n.t(if (isFavorite) \"article.unfavorite\" else \"article.favorite\")"))
        assertTrue(actionsBody.contains("RemoteArticleCopyLinkMenuItem("))
    }

    @Test
    fun remoteArticleDetailShowsOriginalFromOverflowBottomSheet() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt").readText()
        val sheetBody = source.functionBody("RemoteArticleOriginalBottomSheet")

        assertTrue(source.contains("RemoteArticleOriginalBottomSheet("))
        assertTrue(source.contains("showOriginalSheet"))
        assertTrue(source.contains("remoteArticleOriginalPageContent("))
        assertTrue(sheetBody.contains("LazyColumn("), "原文弹窗必须支持长文滚动")
        assertTrue(sheetBody.contains("copyArticleUrl("), "原文弹窗应支持复制链接")
        assertTrue(sheetBody.contains("FilterChip("), "有译文时应支持原文与译文切换")
    }
}

private fun String.functionBody(functionName: String): String {
    val match = Regex("fun\\s+(?:[A-Za-z0-9_<>.]+\\.)?${Regex.escape(functionName)}\\s*\\(").find(this)
    val start = match?.range?.first ?: -1
    require(start >= 0) { "Missing function $functionName" }
    val bodyStart = indexOf('{', start)
    require(bodyStart >= 0) { "Missing body for $functionName" }
    val bodyEnd = matchingBraceIndex(bodyStart)
    return substring(bodyStart, bodyEnd + 1)
}

private fun String.functionLineCount(functionName: String): Int = functionBody(functionName).lineSequence().count() + 1

private fun String.matchingBraceIndex(openBraceIndex: Int): Int {
    var depth = 0
    for (index in openBraceIndex until length) {
        when (this[index]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return index
            }
        }
    }
    error("Missing matching brace")
}

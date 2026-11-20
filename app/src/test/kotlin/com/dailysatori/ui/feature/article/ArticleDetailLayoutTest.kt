package com.dailysatori.ui.feature.article

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArticleDetailLayoutTest {
    @Test
    fun coverHeightShrinksWithScrollAndNeverGoesBelowZero() {
        assertEquals(260, articleCollapsedCoverHeightDp(scrollOffsetDp = 0))
        assertEquals(140, articleCollapsedCoverHeightDp(scrollOffsetDp = 120))
        assertEquals(0, articleCollapsedCoverHeightDp(scrollOffsetDp = 300))
    }

    @Test
    fun syncedCoverHeightsUseOneSharedScrollOffsetForAllPages() {
        assertEquals(listOf(180, 180), articleSyncedCoverHeightsDp(sharedScrollOffsetDp = 80, pageCount = 2))
    }

    @Test
    fun sharedCoverHeightChangesBeforePageContentScrolls() {
        assertEquals(220, articleCoverHeightAfterScroll(currentHeightDp = 260, scrollDeltaDp = -40, contentAtTop = true))
        assertEquals(0, articleCoverHeightAfterScroll(currentHeightDp = 20, scrollDeltaDp = -40, contentAtTop = true))
        assertEquals(80, articleCoverHeightAfterScroll(currentHeightDp = 40, scrollDeltaDp = 40, contentAtTop = true))
        assertEquals(40, articleCoverHeightAfterScroll(currentHeightDp = 40, scrollDeltaDp = 40, contentAtTop = false))
    }

    @Test
    fun localArticleDetailUsesLightweightReaderWithoutBodySurface() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt").readText()
        val detailBody = source.functionBody("ArticleDetailBody")

        assertFalse(detailBody.contains("border = BorderStroke"))
        assertTrue(source.contains("ArticleReaderHeader("))
        assertTrue(source.contains("ArticleReaderBody("))
        assertFalse(source.contains("MagazineArticleHeader("))
        assertFalse(source.contains("MagazineArticleBody("))
    }

    @Test
    fun localArticleDetailUsesSingleSummaryStreamAndOriginalSheet() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt").readText()
        val headerBody = source.functionBody("ArticleMagazineHeader")
        val pageBody = source.functionBody("ArticleDetailPage")

        assertFalse(headerBody.contains("intro = article.ai_content"))
        assertFalse(headerBody.contains("intro = null"))
        assertTrue(pageBody.contains("ArticleDetailBody(article)"))
        assertTrue(source.contains("ArticleOriginalBottomSheet("))
        assertFalse(source.contains("MagazineArticleTabSelector("))
        assertFalse(source.contains("MarkdownTabPager("))
        assertFalse(source.contains("MarkdownTabRow(articleDetailTabTitles"))
    }

    @Test
    fun localArticleDetailKeepsAllActionsInOverflowMenu() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt").readText()
        val actionsBody = source.functionBody("ArticleDetailActions")

        assertTrue(actionsBody.contains("Icons.Default.MoreVert"))
        assertTrue(actionsBody.contains("PageAction(\"更多操作\"") && actionsBody.contains("menuContent ="))
        assertTrue(actionsBody.contains("ArticleOriginalMenuItem("))
        assertTrue(actionsBody.contains("ArticleRefreshMenuItem("))
        assertTrue(actionsBody.contains("ArticleAiProcessMenuItem("))
        assertFalse(actionsBody.contains("ArticleXApiRefreshMenuItem("))
        assertTrue(actionsBody.contains("ArticleFavoriteMenuItem("))
        assertTrue(actionsBody.contains("ArticleCopyLinkMenuItem("))
        assertTrue(actionsBody.contains("ArticleOpenMenuItem("))
        assertTrue(actionsBody.contains("ArticleDeleteMenuItem("))
        assertFalse(actionsBody.contains("IconButton(onClick = onFavoriteClick)"))
        assertFalse(actionsBody.contains("IconButton(onClick = { openArticleUrl"))
    }

    @Test
    fun localArticleDetailCanCopyArticleUrlFromOverflowMenu() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt").readText()
        val copyBody = source.functionBody("ArticleCopyLinkMenuItem")

        assertTrue(source.contains("ClipboardManager"))
        assertTrue(source.contains("ClipData.newPlainText(articleCopyLinkClipLabel(), url)"))
        assertTrue(copyBody.contains("Text(articleCopyLinkMenuLabel())"))
        assertTrue(copyBody.contains("enabled = !url.isNullOrBlank()"))
        assertTrue(copyBody.contains("onCopyClick(url)"))
        assertTrue(source.contains("Toast.makeText(context, articleCopyLinkSuccessMessage(), Toast.LENGTH_SHORT).show()"))
        assertEquals("复制网页链接", articleCopyLinkMenuLabel())
        assertEquals("网页链接", articleCopyLinkClipLabel())
        assertEquals("已复制网页链接", articleCopyLinkSuccessMessage())
    }

    @Test
    fun localArticleDetailReplacesBothRefreshEntriesWithTwoActions() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt").readText()
        val aiProcessBody = source.functionBody("ArticleAiProcessMenuItem")

        assertTrue(source.contains("onRefreshConfirm = viewModel::refreshArticle"))
        assertTrue(source.contains("viewModel::processWithExistingBody"))
        assertFalse(source.contains("refreshArticleWithXApi"))
        assertTrue(aiProcessBody.contains("Text(articleAiProcessMenuLabel())"))
        assertTrue(aiProcessBody.contains("enabled = canStartArticleAiProcess("))
        assertEquals("刷新", articleRefreshMenuLabel())
        assertEquals("AI 处理", articleAiProcessMenuLabel())
        assertTrue(canStartArticleAiProcess(isRefreshing = false, hasProcessableText = true))
        assertFalse(canStartArticleAiProcess(isRefreshing = true, hasProcessableText = true))
        assertFalse(canStartArticleAiProcess(isRefreshing = false, hasProcessableText = false))
    }

    @Test
    fun articleDetailTabsUsePrimaryBlueSelectedAccent() {
        val source = File("src/main/kotlin/com/dailysatori/ui/component/news/MagazineArticleDetail.kt").readText()
        val tabBody = source.functionBody("MagazineArticleTabSelector")

        assertTrue(tabBody.contains("FilterChipDefaults.filterChipColors"))
        assertTrue(tabBody.contains("selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)"))
        assertTrue(tabBody.contains("selectedLabelColor = MaterialTheme.colorScheme.primary"))
        assertTrue(tabBody.contains("selectedLeadingIconColor = MaterialTheme.colorScheme.primary"))
    }

    @Test
    fun localArticleDetailFunctionsStayFocused() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt").readText()

        listOf(
            "ArticleDetailScreen",
            "ArticleDetailActions",
            "ArticleDetailContent",
            "ArticleDetailLoadedContent",
            "ArticleDetailBody",
            "ArticleOriginalBottomSheet",
            "ArticleRefreshConfirmDialog",
            "ArticleDeleteConfirmDialog",
        ).forEach { functionName ->
            assertTrue(source.functionLineCount(functionName) <= 50, "$functionName exceeds 50 lines")
        }
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

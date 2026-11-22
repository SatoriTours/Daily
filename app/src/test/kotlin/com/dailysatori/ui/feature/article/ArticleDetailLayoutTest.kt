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
        assertTrue(source.contains("ArticleReadingTitle("))
        assertTrue(source.contains("ArticleReaderBody("))
        assertFalse(source.contains("MagazineArticleHeader("))
        assertFalse(source.contains("MagazineArticleBody("))
    }

    @Test
    fun localArticleDetailUsesSingleSummaryStreamAndOriginalSheet() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt").readText()
        val headerBody = File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleAiReader.kt").readText()
            .functionBody("ArticleReadingTitle")
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
    fun articleAndAssistantMenusHaveDistinctNamesAndArticleKeepsAssistant() {
        val article = File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt").readText()
        val remote = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt").readText()
        assertFalse(article.contains("showAssistant = false"), "The article must keep its independent assistant launcher")
        assertFalse(remote.contains("showAssistant = localArticleId == null"))
        val articleLabels = File("src/main/assets/i18n/zh.txt").readLines()
        val assistantLabels = File("src/main/res/values/strings.xml").readText()
        assertTrue("article.more_actions\t文章更多操作" in articleLabels)
        assertTrue(assistantLabels.contains("name=\"assistant_more\">助手更多操作"))
    }

    @Test
    fun articleDetailSeparatesHeaderActionsFromOverflow() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt").readText()
        val actions = source.functionBody("ArticleDetailActions")
        assertTrue(source.contains("AppScaffold(\n        title = \"\",\n        onBack = onBack,"))
        assertFalse(actions.contains("Icons.Default.FavoriteBorder"), "收藏不能占用顶栏")
        assertFalse(actions.contains("Icons.Default.AutoStories"))
        val remote = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt").readText()
        val readingAction = """PageAction\(i18n\.t\("article_ai\.smart_reading"\), SmartReadingIcon"""
        assertTrue(Regex(readingAction).containsMatchIn(actions), "本地详情提供有无障碍名称的文星图标")
        assertTrue(Regex(readingAction).containsMatchIn(remote), "同步详情使用相同图标")
        val overflow = source.functionBody("ArticleDetailOverflow")
        assertTrue(overflow.contains("MoreActionsIcon"))
        assertTrue(overflow.contains("MarkNewsReadMenuItem("))
        assertTrue(overflow.contains("ArticleCopyLinkMenuItem("))
        assertTrue(overflow.contains("ArticleRefreshMenuItem("))
        assertTrue(overflow.indexOf("HorizontalDivider(") in 1 until overflow.indexOf("ArticleDeleteMenuItem {"))
        val originalEntry = overflow.indexOf("ArticleOriginalMenuItem(dismiss, onOriginalClick)")
        assertTrue(originalEntry >= 0 && originalEntry < overflow.indexOf("DropdownMenuItem("))
        assertFalse(overflow.contains("onAiProcessClick"))
        assertTrue(overflow.contains("Icons.Default.FavoriteBorder"))
        assertTrue(overflow.contains("onFavoriteClick"))
        assertFalse(overflow.contains("onOpenClick"))
    }

    @Test
    fun originalSheetKeepsLinkActionsAndClipboardBehavior() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt").readText()
        val sheet = source.functionBody("ArticleOriginalBottomSheet")
        assertTrue(source.functionBody("ArticleDetailPage").contains("ArticleReadingTitle("))
        assertFalse(source.contains("ArticleOriginalLink("))
        assertTrue(sheet.contains("ArticleOriginalMetadata("))
        assertTrue(sheet.contains("copyArticleUrl("))
        assertTrue(sheet.contains("openArticleUrl("))
        assertTrue(source.contains("ClipData.newPlainText("))
        assertTrue(source.functionBody("ArticleCopyLinkMenuItem").contains("enabled = !url.isNullOrBlank()"))
    }

    @Test
    fun detailUsesOneSafeActionableFailureInsteadOfShowingTheExceptionAndArticleStatus() {
        val screen = File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt").readText()
        val viewModel = File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailViewModel.kt").readText()
        val details = screen.functionBody("ArticleDetailLoadedContent")
        assertEquals(1, Regex("ArticleRefreshError\\(").findAll(details).count())
        assertFalse(details.contains("可刷新重试"))
        assertTrue(details.contains("savedReadingRetry("))
        assertFalse(viewModel.contains("refreshError = e.message"))
        assertFalse(viewModel.contains("refreshError = article.ai_content"))
    }

    @Test
    fun articleDetailPreservesRefreshAndLocalAiHandlers() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt").readText()
        assertTrue(source.contains("onRefreshConfirm = viewModel::refreshArticle"))
        assertTrue(source.contains("viewModel.processWithExistingBody()") && source.contains("processWithExistingBody(retranslate = true)"))
        assertFalse(source.contains("refreshArticleWithXApi"))
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

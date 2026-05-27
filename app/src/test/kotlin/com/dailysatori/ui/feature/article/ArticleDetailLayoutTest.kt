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
    fun localArticleDetailUsesBorderlessSharedMagazineReader() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt").readText()
        val detailBody = source.functionBody("ArticleDetailBody")

        assertFalse(detailBody.contains("border = BorderStroke"))
        assertTrue(source.contains("MagazineArticle"))
        assertTrue(source.contains("MagazineArticleHeader("))
        assertTrue(source.contains("MagazineArticleBody("))
    }

    @Test
    fun localArticleDetailHeaderDoesNotRepeatSummaryAboveTabs() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt").readText()
        val headerBody = source.functionBody("ArticleMagazineHeader")
        val pageBody = source.functionBody("ArticleDetailPage")

        assertFalse(headerBody.contains("intro = article.ai_content"))
        assertTrue(headerBody.contains("intro = null"))
        assertTrue(pageBody.contains("MagazineArticleTabSelector("))
        assertFalse(source.contains("MarkdownTabRow(articleDetailTabTitles"))
    }

    @Test
    fun localArticleDetailFunctionsStayFocused() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt").readText()

        listOf(
            "ArticleDetailScreen",
            "ArticleDetailActions",
            "ArticleDetailContent",
            "ArticleDetailLoadedContent",
            "ArticleDetailPager",
            "ArticleDetailPage",
            "ArticleDetailBody",
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

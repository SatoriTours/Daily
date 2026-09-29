package com.dailysatori.ui.feature.unifiednews

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class UnifiedNewsSourceSwipeTest {
    @Test
    fun sourceSwitchingUsesPagerInsteadOfReleaseOnlyGestures() {
        val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()
        assertTrue(screen.contains("HorizontalPager("))
        assertTrue(screen.contains("animateScrollToPage("))
        assertFalse(screen.contains("detectHorizontalDragGestures"))
    }

    private val state = UnifiedNewsState(
        remoteSources = listOf(UnifiedNewsRemoteSourceOption(1, "来源")),
        externalFavoriteSources = listOf(UnifiedNewsExternalFavoriteSourceOption(1, "来源")),
    )

    @Test
    fun pagesFollowHeaderOrderEvenWithDuplicateNamesAndIds() {
        val pages = unifiedNewsSourcePages(state)
        assertEquals(listOf("news-summary", "news-remote-1", "news-favorite-1", "news-local"), pages.map(::unifiedNewsSourcePageKey))
        assertEquals(1, unifiedNewsSourcePageIndex(pages, UnifiedNewsSourceSelection.RemoteSource(1, "旧名称")))
        assertEquals(2, unifiedNewsSourcePageIndex(pages, UnifiedNewsSourceSelection.ExternalFavoriteSource(1, "来源")))
    }

    @Test
    fun sourceInsertionPreservesSelectionByIdentity() {
        val selection = UnifiedNewsSourceSelection.ExternalFavoriteSource(1, "来源")
        val updated = state.copy(remoteSources = listOf(UnifiedNewsRemoteSourceOption(2, "新增")) + state.remoteSources)
        val pages = unifiedNewsSourcePages(updated)
        assertEquals(3, unifiedNewsSourcePageIndex(pages, selection))
        assertEquals(unifiedNewsSourcePageKey(selection), unifiedNewsSourcePageKey(pages[3]))
    }

    @Test
    fun emptySourcesStillAllowSwitchingBetweenSummaryAndLocal() {
        assertEquals(listOf(UnifiedNewsSourceSelection.Summary, UnifiedNewsSourceSelection.LocalArticles), unifiedNewsSourcePages(UnifiedNewsState()))
    }

    @Test
    fun removedSourceFallsBackToSummary() {
        val removed = UnifiedNewsSourceSelection.RemoteSource(99, "已移除")
        assertEquals(0, unifiedNewsSourcePageIndex(unifiedNewsSourcePages(state), removed))
    }

    @Test
    fun adjacentPagesDoNotInheritSelectedSourceSearchErrorsOrRequests() {
        val remote = UnifiedNewsSourceSelection.RemoteSource(1, "来源")
        val selected = state.copy(sourceSelection = remote, searchQuery = "AI", sourceArticlesError = "离线",
            scrollToTopRequestKey = 5, localArticleRefreshRequestKey = 3)
        val adjacent = unifiedNewsSourcePageState(selected, UnifiedNewsSourceSelection.LocalArticles, 0, 0)
        assertEquals("", adjacent.searchQuery)
        assertNull(adjacent.sourceArticlesError)
        assertEquals(0, adjacent.scrollToTopRequestKey)
        assertEquals(0, adjacent.localArticleRefreshRequestKey)
        val active = unifiedNewsSourcePageState(selected, remote, 5, 3)
        assertEquals("AI", active.searchQuery)
        assertEquals("离线", active.sourceArticlesError)
        assertEquals(5, active.scrollToTopRequestKey)
        assertEquals(3, active.localArticleRefreshRequestKey)
    }
}

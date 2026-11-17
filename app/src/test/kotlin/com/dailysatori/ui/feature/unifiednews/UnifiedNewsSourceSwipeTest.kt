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
        assertEquals(listOf("news-summary", "news-opportunities", "news-hotlists", "news-remote-1", "news-favorite-1", "news-local", "news-favorites"), pages.map(::unifiedNewsSourcePageKey))
        assertEquals(3, unifiedNewsSourcePageIndex(pages, UnifiedNewsSourceSelection.RemoteSource(1, "旧名称")))
        assertEquals(4, unifiedNewsSourcePageIndex(pages, UnifiedNewsSourceSelection.ExternalFavoriteSource(1, "来源")))
    }

    @Test
    fun sourceInsertionPreservesSelectionByIdentity() {
        val selection = UnifiedNewsSourceSelection.ExternalFavoriteSource(1, "来源")
        val updated = state.copy(remoteSources = listOf(UnifiedNewsRemoteSourceOption(2, "新增")) + state.remoteSources)
        val pages = unifiedNewsSourcePages(updated)
        assertEquals(5, unifiedNewsSourcePageIndex(pages, selection))
        assertEquals("news-favorite-1", unifiedNewsSourcePageKey(pages[5]))
    }

    @Test
    fun renamingASourceKeepsTheTabSelectedByIdentity() {
        val renamedOptions = listOf(UnifiedNewsRemoteSourceOption(1, "我的订阅"))
        val renamed = state.copy(
            remoteSources = renamedOptions,
            sourceSelection = UnifiedNewsSourceSelection.RemoteSource(1, "来源").withUpdatedRemoteSourceName(renamedOptions),
        )
        val pages = unifiedNewsSourcePages(renamed)
        assertEquals(UnifiedNewsSourceSelection.RemoteSource(1, "我的订阅"), renamed.sourceSelection)
        assertEquals(3, unifiedNewsSourcePageIndex(pages, renamed.sourceSelection))
        assertEquals(3, unifiedNewsSourcePageIndex(pages, UnifiedNewsSourceSelection.RemoteSource(1, "来源")))
        assertEquals("news-remote-1", unifiedNewsSourcePageKey(pages[3]))
        assertEquals(
            UnifiedNewsSourceSelection.RemoteSource(9, "其它"),
            UnifiedNewsSourceSelection.RemoteSource(9, "其它").withUpdatedRemoteSourceName(renamedOptions),
        )
    }

    @Test
    fun homeTabsFollowRenamedSourcesWithoutChangingTheirId() {
        val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()
        val switcher = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsSourceSwitcher.kt").readText()
        assertTrue(viewModel.contains("remoteNewsSourceRepo.observeAll()"))
        assertTrue(viewModel.contains("withUpdatedRemoteSourceName(options)"))
        assertTrue(switcher.contains("label = { Text(source.name) }"))
        assertTrue(switcher.contains("?.id == source.id"))
    }

    @Test
    fun emptySourcesStillAllowSwitchingBetweenSummaryAndLocal() {
        assertEquals(listOf(UnifiedNewsSourceSelection.Summary, UnifiedNewsSourceSelection.Opportunities, UnifiedNewsSourceSelection.Hotlists, UnifiedNewsSourceSelection.LocalArticles, UnifiedNewsSourceSelection.Favorites), unifiedNewsSourcePages(UnifiedNewsState()))
    }

    @Test
    fun removedSourceFallsBackToSummary() {
        val removed = UnifiedNewsSourceSelection.RemoteSource(99, "已移除")
        assertEquals(UnifiedNewsSourceSelection.Summary, resolvedUnifiedNewsSourceSelection(removed, state.remoteSources, state.externalFavoriteSources))
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

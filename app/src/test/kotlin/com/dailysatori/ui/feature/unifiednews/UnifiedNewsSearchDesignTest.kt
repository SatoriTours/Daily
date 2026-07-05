package com.dailysatori.ui.feature.unifiednews

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnifiedNewsSearchDesignTest {
    @Test
    fun searchUsesTemporaryTopBarModeInsteadOfPersistentContentSearchBar() {
        val screen = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()
        val summaryPage = screen.substringAfter("private fun UnifiedNewsSummaryPage(").substringBefore("@Composable\nprivate fun UnifiedNewsTopBar")

        assertTrue(screen.contains("UnifiedNewsTopBar("))
        assertTrue(screen.contains("UnifiedNewsSearchTopBar("))
        assertTrue(screen.contains("state.isSearchVisible"))
        assertTrue(screen.contains("toggleSearch"))
        assertTrue(screen.contains("closeSearch"))
        assertTrue(screen.contains("BasicTextField("))
        assertFalse(screen.substringAfter("private fun UnifiedNewsSearchTopBar(").contains("\n            TextField("))
        assertFalse(screen.substringAfter("private fun UnifiedNewsSearchTopBar(").contains("TextFieldDefaults"))
        assertFalse(summaryPage.contains("SearchBar("))
    }

    @Test
    fun remoteSourceSearchFiltersLoadedArticlesAndDisablesLoadMore() {
        val remote = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsRemoteSourceContent.kt").readText()

        assertTrue(remote.contains("filteredUnifiedNewsRemoteArticles"))
        assertTrue(remote.contains("canLoadMore = state.searchQuery.isBlank()"))
        assertTrue(remote.contains("if (canLoadMore) LoadMoreWhenAtEnd"))
        assertTrue(remote.contains("这个来源没有匹配新闻"))
    }

    @Test
    fun embeddedArticleListsReceiveUnifiedNewsSearchQuery() {
        val screen = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()
        val articleList = File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleListScreen.kt").readText()

        assertTrue(screen.contains("embeddedSearchQuery = state.searchQuery"))
        assertTrue(articleList.contains("embeddedSearchQuery: String? = null"))
        assertTrue(articleList.contains("LaunchedEffect(externalFavoriteSourceId, embeddedSearchQuery)"))
    }

    @Test
    fun cancelSearchRestoresEmbeddedArticleListsAndTopBarDoubleClickScrollsToTop() {
        val viewModel = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()
        val screen = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()
        val summary = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsSummaryContent.kt").readText()
        val remote = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsRemoteSourceContent.kt").readText()
        val articleList = File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleListScreen.kt").readText()
        val appTopBar = File("src/main/kotlin/com/dailysatori/ui/component/appbar/AppTopBar.kt").readText()

        assertTrue(viewModel.contains("val scrollToTopRequestKey: Int = 0"))
        assertTrue(viewModel.contains("fun requestScrollToTop()"))
        assertTrue(screen.contains("onTitleDoubleClick = viewModel::requestScrollToTop"))
        assertTrue(appTopBar.contains("combinedClickable"))
        assertTrue(appTopBar.contains("onDoubleClick = onTitleDoubleClick"))
        assertTrue(summary.contains("LaunchedEffect(state.scrollToTopRequestKey)"))
        assertTrue(remote.contains("scrollToTopRequestKey = state.scrollToTopRequestKey"))
        assertTrue(remote.contains("LaunchedEffect(scrollToTopRequestKey)"))
        assertTrue(screen.contains("scrollToTopRequestKey = state.scrollToTopRequestKey"))
        assertTrue(articleList.contains("scrollToTopRequestKey: Int = 0"))
        assertTrue(articleList.contains("LaunchedEffect(scrollToTopRequestKey, state.articles.isNotEmpty())"))
        assertTrue(articleList.contains("viewModel.search(embeddedSearchQuery)"))
    }
}

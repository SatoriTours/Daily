package com.dailysatori.ui.feature.unifiednews

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnifiedNewsLocalArticleBackTest {
    @Test
    fun localArticlePagesPassBackActionToArticleList() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()
        val pageSwitch = source.substringAfter("when (state.page)")
            .substringBefore("}\n}")

        assertTrue(pageSwitch.contains("UnifiedNewsPage.LOCAL_ARTICLES -> ArticleListScreen("))
        assertTrue(pageSwitch.contains("UnifiedNewsPage.LOCAL_FAVORITES -> ArticleListScreen("))
        assertTrue(pageSwitch.contains("onBack = { viewModel.switchPage(UnifiedNewsPage.SUMMARY) }"))
    }

    @Test
    fun articleListCanRenderBackButtonWhenEmbedded() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleListScreen.kt").readText()

        assertTrue(source.contains("onBack: (() -> Unit)? = null"))
        assertTrue(source.contains("showBack = onBack != null"))
        assertTrue(source.contains("onBack = onBack"))
    }

    @Test
    fun unifiedNewsViewModelKeepsSourceArticleStateSeparateFromSummaryErrors() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()

        assertTrue(source.contains("sealed class UnifiedNewsSourceSelection"))
        assertTrue(source.contains("data object Summary : UnifiedNewsSourceSelection()"))
        assertTrue(source.contains("data class RemoteSource"))
        assertTrue(source.contains("data class UnifiedNewsRemoteSourceOption(val id: Long, val name: String)"))
        assertTrue(source.contains("remoteSources: List<UnifiedNewsRemoteSourceOption> = emptyList()"))
        assertFalse(source.contains("remoteSources: List<Remote_news_source> = emptyList()"))
        assertTrue(source.contains("sourceArticlesByCacheKey: Map<SourceArticleCacheKey, List<RemoteArticle>> = emptyMap()"))
        assertTrue(source.contains("sourceArticlesLoadingSourceId: Long? = null"))
        assertTrue(source.contains("sourceArticlesError: String? = null"))
    }

    @Test
    fun unifiedNewsSourceArticlesUseCurrentRemoteSourceAndCacheResults() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()
        val selectRemoteSource = source.substringAfter("fun selectRemoteSource(source: UnifiedNewsRemoteSourceOption)")
            .substringBefore("fun refreshSelectedRemoteSource()")

        assertTrue(source.contains("fun selectSummarySource()"))
        assertTrue(source.contains("fun selectRemoteSource(source: UnifiedNewsRemoteSourceOption)"))
        assertTrue(source.contains("fun refreshSelectedRemoteSource()"))
        assertTrue(source.contains("fun openSourceArticle(article: RemoteArticle)"))
        assertTrue(source.contains("hasUnifiedNewsSourceArticlesCache(_state.value, source.id, summaryDate)"))
        assertTrue(source.contains("remoteNewsService.fetchTopArticlesToday(config.value, page = 1, limit = 50)"))
        assertTrue(source.contains("selectedRemoteArticle = article"))
        assertTrue(source.contains("articleRepo.findLocalArticleForRemote(article)"))
        assertTrue(source.contains("import java.util.concurrent.atomic.AtomicLong"))
        assertTrue(source.contains("private val sourceArticleRequestToken = AtomicLong(0L)"))
        assertTrue(source.contains("private val sourceArticleRequestLock = Any()"))
        assertTrue(source.contains("val token = beginSourceArticleRequest(sourceId)"))
        assertTrue(source.contains("private fun beginSourceArticleRequest(sourceId: Long): Long"))
        assertTrue(source.contains("synchronized(sourceArticleRequestLock)"))
        assertTrue(source.contains("sourceArticleRequestToken.incrementAndGet()"))
        assertTrue(source.contains("ifLatestSourceArticleRequest(token)"))
        assertTrue(source.contains("private fun ifLatestSourceArticleRequest(token: Long, transform: (UnifiedNewsState) -> UnifiedNewsState)"))
        assertTrue(source.contains("if (token == sourceArticleRequestToken.get()) _state.update(transform)"))
        assertTrue(source.contains("catch (e: CancellationException)"))
        assertTrue(source.contains("state.withUnifiedNewsSourceArticlesLoaded(sourceId, cacheKey.summaryDate, result.value.articles)"))
        assertTrue(source.contains("state.withUnifiedNewsSourceArticlesFailure(\"来源文章加载失败，请稍后重试\")"))
        assertTrue(source.contains("invalidateSourceArticleRequest()"))
        assertTrue(source.contains("_state.update { it.withUnifiedNewsSourceArticleRequestInvalidated() }"))
        assertTrue(source.contains("_state.update { it.withUnifiedNewsSourceArticlesLoading(sourceId) }"))
        assertTrue(source.contains("if (!sourceArticlesCached)"))
        assertTrue(source.contains("shouldResetUnifiedNewsSourceSelection(currentSelection, remoteSources, externalFavoriteSources)"))
        assertTrue(source.contains("resolvedUnifiedNewsSourceSelection(currentSelection, remoteSources, externalFavoriteSources)"))
        assertTrue(selectRemoteSource.indexOf("invalidateSourceArticleRequest()") < selectRemoteSource.indexOf("_state.update"))
        assertTrue(selectRemoteSource.indexOf("_state.update") < selectRemoteSource.indexOf("fetchSourceArticles(source.id, force = false)"))
    }

    @Test
    fun unifiedNewsScreenRendersSourceSwitcherAndSourceArticleStates() {
        val source = unifiedNewsFeatureUiSource()

        assertTrue(source.contains("UnifiedNewsSourceSwitcher("))
        assertTrue(source.contains("UnifiedNewsSourceArticleContent("))
        assertTrue(source.contains("FilterChip("))
        assertTrue(source.contains("Text(\"汇总\")"))
        assertTrue(source.contains("sourceArticlesLoadingSourceId == selection.id"))
        assertTrue(source.contains("这个来源今天还没有新闻"))
        assertTrue(source.contains("viewModel.openSourceArticle(article)"))
    }

    @Test
    fun unifiedNewsSourceSwitcherProvidesRefreshActionAndRemoteListHasNoHeader() {
        val sourceSwitcher = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsSourceSwitcher.kt").readText()
        val remoteSourceContent = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsRemoteSourceContent.kt").readText()
        val articleList = remoteSourceContent.substringAfter("private fun UnifiedNewsSourceArticleList(")
            .substringBefore("@Composable\nprivate fun UnifiedNewsSourceArticleMessage")

        assertTrue(sourceSwitcher.contains("Icons.Default.Refresh") || sourceSwitcher.contains("refreshSelectedSource"))
        assertFalse(articleList.contains("TextButton(onClick = viewModel::refreshSelectedRemoteSource)"))
        assertFalse(articleList.contains("\${selection.name} · 今日文章"))
        assertFalse(articleList.contains("共 \${articles.size} 篇"))
        assertTrue(articleList.contains("RemoteArticleSummaryCard(article)"))
    }

    @Test
    fun sourceArticleCacheKeyIncludesSourceAndDate() {
        assertTrue(sourceArticleCacheKey(7L, "2026-05-26").sourceId == 7L)
        assertTrue(sourceArticleCacheKey(7L, "2026-05-26").summaryDate == "2026-05-26")
        assertTrue(sourceArticleCacheKey(7L, "2026-05-26") != sourceArticleCacheKey(7L, "2026-05-27"))

        val source = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()
        assertTrue(source.contains("sourceArticlesByCacheKey: Map<SourceArticleCacheKey, List<RemoteArticle>> = emptyMap()"))
        assertTrue(source.contains("hasUnifiedNewsSourceArticlesCache(_state.value, source.id, summaryDate)"))
    }

    @Test
    fun sourceArticleClickUsesRenderedSourceIdInsteadOfCurrentSelection() {
        val viewModel = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()
        val screen = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsRemoteSourceContent.kt").readText()

        assertTrue(viewModel.contains("fun openSourceArticle(article: RemoteArticle)"))
        assertTrue(viewModel.contains("selectedRemoteArticle = article"))
        assertTrue(viewModel.contains("articleRepo.findLocalArticleForRemote(article)"))
        assertFalse(viewModel.contains("fun openSelectedSourceArticle(articleId: Long)"))
        assertTrue(screen.contains("viewModel.openSourceArticle(article)"))
    }

    @Test
    fun cachedSourceArticleListShowsRefreshFailureMessage() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsRemoteSourceContent.kt").readText()
        val content = source.substringAfter("internal fun UnifiedNewsSourceArticleContent(")
            .substringBefore("@Composable\nprivate fun UnifiedNewsSourceArticleList")
        val list = source.substringAfter("private fun UnifiedNewsSourceArticleList(")
            .substringBefore("@Composable\nprivate fun UnifiedNewsSourceArticleMessage")

        assertTrue(content.contains("sourceArticlesError = state.sourceArticlesError"))
        assertTrue(list.contains("sourceArticlesError: String?"))
        assertTrue(list.contains("刷新失败，正在显示上次结果"))
        assertTrue(list.contains("sourceArticlesError != null"))
    }

    private fun unifiedNewsFeatureUiSource(): String =
        listOf(
            "UnifiedNewsScreen.kt",
            "UnifiedNewsSourceSwitcher.kt",
            "UnifiedNewsRemoteSourceContent.kt",
        ).joinToString("\n") { fileName ->
            File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/$fileName").readText()
        }
}

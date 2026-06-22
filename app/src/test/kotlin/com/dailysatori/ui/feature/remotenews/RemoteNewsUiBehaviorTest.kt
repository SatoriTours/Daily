package com.dailysatori.ui.feature.remotenews

import com.dailysatori.service.remotenews.RemoteDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File

class RemoteNewsUiBehaviorTest {
    @Test
    fun digestTimestampIncludesTimeWhenGeneratedAtIsAvailable() {
        val digest = RemoteDigest(
            id = 1,
            date = "2026-05-13",
            generatedAt = "2026-05-13T08:35:12Z",
        )

        assertEquals("2026-05-13 08:35", remoteDigestTimestampText(digest))
    }

    @Test
    fun digestTimestampFallsBackToDateWhenTimeIsMissing() {
        val digest = RemoteDigest(id = 1, date = "2026-05-13")

        assertEquals("2026-05-13", remoteDigestTimestampText(digest))
    }

    @Test
    fun remoteNewsListScrollsToTopAfterRefreshCompletes() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsScreen.kt").readText()

        assertTrue(source.contains("refreshCompletedToken"))
        assertTrue(source.contains("scrollToItem(0)"))
    }

    @Test
    fun remoteNewsListsUseStableKeysAndDerivedLoadMoreState() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsScreen.kt").readText()

        assertTrue(source.contains("derivedStateOf"))
        assertTrue(source.contains("items(state.digests, key = { it.id })"))
        assertTrue(source.contains("items(state.articles, key = { it.id })"))
        assertTrue(source.contains("items(state.feeds, key = { it.id })"))
        assertTrue(source.contains("item(key = \"remote-news-loading-more\")"))
        assertTrue(source.contains("item(key = \"remote-news-load-more-error\")"))
        assertTrue(source.contains("contentPadding = newsListContentPadding()"))
        assertTrue(source.contains("NewsStateMessage("))
    }

    @Test
    fun remoteNewsDetailErrorsDoNotReuseListErrorState() {
        val viewModel = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt").readText()
        val screen = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsScreen.kt").readText()

        assertTrue(viewModel.contains("detailError"))
        assertTrue(viewModel.substringAfter("fun openArticle").substringBefore("fun toggleSelectedArticleFavorite").contains("detailError"))
        assertFalse(viewModel.substringAfter("fun openArticle").substringBefore("fun toggleSelectedArticleFavorite").contains("error = result.message"))
        assertTrue(screen.contains("state.detailError"))
    }

    @Test
    fun remoteNewsListOpensArticleFromListPayloadWithoutDetailApi() {
        val viewModel = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt").readText()
        val screen = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsScreen.kt").readText()
        val openArticleBody = viewModel.substringAfter("fun openArticle(").substringBefore("fun toggleSelectedArticleFavorite")

        assertTrue(viewModel.contains("fun openArticle(article: RemoteArticle)"))
        assertTrue(openArticleBody.contains("selectedArticle = article"))
        assertFalse(openArticleBody.contains("remoteNewsService.fetchArticle"))
        assertTrue(screen.contains("viewModel.openArticle(it)"))
        assertFalse(screen.contains("viewModel.openArticle(it.id)"))
    }

    @Test
    fun remoteArticleOpensEvenWhenFavoriteLookupFails() {
        val viewModel = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt").readText()
        val openBody = viewModel.substringAfter("fun openArticle(article: RemoteArticle)").substringBefore("fun toggleSelectedArticleFavorite")

        assertTrue(openBody.contains("selectedArticle = article"))
        assertTrue(openBody.contains("runCatching { articleRepo.findLocalArticleForRemote(article) }.getOrNull()"))
        assertTrue(openBody.contains("selectedArticleIsFavorite = local?.is_favorite == 1L"))
    }

    @Test
    fun remoteArticleDetailUsesReadableTitleFallback() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt").readText()

        assertTrue(source.contains("private fun remoteArticleDisplayTitle(article: RemoteArticle): String"))
        assertTrue(source.contains("remoteArticleDisplayTitle(article)"))
        assertTrue(source.contains("article.title?.trim()?.takeIf { it.isNotBlank() }"))
        assertTrue(source.contains("article.summary?.trim()?.takeIf { it.isNotBlank() }?.take(48)"))
        assertTrue(source.contains("\"未命名远程文章\""))
    }

    @Test
    fun remoteArticleDetailShowsExplicitOriginalFallback() {
        assertEquals(
            "暂无原文内容，请刷新当前来源后重试。",
            remoteArticleDetailPageContent(page = 1, summary = null, viewpoints = emptyList(), original = null),
        )
    }

    @Test
    fun remoteArticleDetailShowsExplicitSummaryFallback() {
        assertEquals(
            "暂无摘要内容，请刷新当前来源后重试。",
            remoteArticleDetailPageContent(page = 0, summary = null, viewpoints = emptyList(), original = null),
        )
    }

    @Test
    fun remoteArticleFavoriteLookupIncludesUrlLessFallback() {
        val repository = File(
            "../shared/src/commonMain/kotlin/com/dailysatori/data/repository/ArticleRepository.kt",
        ).readText()
        val remoteNews = File(
            "src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt",
        ).readText()
        val unifiedNews = File(
            "src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt",
        ).readText()

        assertTrue(repository.contains("fun findLocalArticleForRemote(remoteArticle: RemoteArticle): Article?"))
        assertTrue(repository.contains("article.url.isNullOrBlank()"))
        assertTrue(remoteNews.contains("articleRepo.findLocalArticleForRemote(article)"))
        assertTrue(unifiedNews.contains("articleRepo.findLocalArticleForRemote(article)"))
    }

    @Test
    fun remoteArticlesSyncLocallyBeforeFavorite() {
        val repository = File(
            "../shared/src/commonMain/kotlin/com/dailysatori/data/repository/ArticleRepository.kt",
        ).readText()
        val viewModel = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt").readText()
        val mapper = File(
            "../shared/src/commonMain/kotlin/com/dailysatori/data/repository/RemoteArticleFavoriteMapper.kt",
        ).readText()
        val loadBody = viewModel.substringAfter("private suspend fun loadArticles(").substringBefore("private suspend fun loadFeeds")

        assertTrue(repository.contains("fun saveRemoteArticleForSync(remoteArticle: RemoteArticle"))
        assertTrue(loadBody.contains("remoteNewsSourceRepo.getEnabled().firstOrNull()"))
        assertTrue(loadBody.contains("remoteNewsService.fetchTopArticlesToday(sourceConfig.value, page = 1, limit = 50)"))
        assertTrue(loadBody.contains("remoteArticleSyncService.syncSourceArticles("))
        assertTrue(loadBody.contains("remoteArticleSyncRepo.getArticlesBySourceDate(source.id, remoteNewsArticleSourceDate())"))
        assertTrue(repository.contains("fun saveRemoteArticleAsFavorite(remoteArticle: RemoteArticle): Article?"))
        assertTrue(mapper.contains("isFavorite: Long = 1L"))
        assertFalse(repository.contains("WebpageParserService"))
        assertFalse(repository.contains("ArticleProcessingScheduler"))
    }

    @Test
    fun remoteNewsScreenCanNavigateSyncedArticlesToLocalDetail() {
        val screen = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsScreen.kt").readText()
        val viewModel = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt").readText()

        assertTrue(screen.contains("fun RemoteNewsScreen(onArticleClick: (Long) -> Unit = {})"))
        assertTrue(screen.contains("LaunchedEffect(state.localArticleNavigationTarget)"))
        assertTrue(viewModel.contains("localArticleNavigationTarget = local.id"))
        assertTrue(viewModel.contains("fun clearLocalArticleNavigationTarget()"))
    }

    @Test
    fun remoteArticleFavoriteReprocessesEnglishContentThroughLocalAiPipeline() {
        val remoteNewsViewModel = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt").readText()
        val unifiedNewsViewModel = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()
        val service = File("../shared/src/commonMain/kotlin/com/dailysatori/service/remotenews/RemoteArticleFavoriteService.kt").readText()
        val sharedDi = File("../shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt").readText()

        assertTrue(remoteNewsViewModel.contains("remoteArticleFavoriteService.toggleFavorite(article, localId)"))
        assertTrue(unifiedNewsViewModel.contains("remoteArticleFavoriteService.toggleFavorite(article, localId)"))
        assertTrue(service.contains("needsLocalAiReprocessingForChineseOutput"))
        assertTrue(service.contains("webpageParserService.reprocessArticle(saved.id)"))
        assertTrue(sharedDi.contains("RemoteArticleFavoriteService(get(), get())"))
    }

    @Test
    fun remoteArticleSummaryCardMetadataIncludesArticleTime() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleCards.kt").readText()

        assertTrue(source.contains("private fun remoteArticleTimeText(article: RemoteArticle): String?"))
        assertTrue(source.contains("article.publishedAt ?: article.createdAt ?: article.processedAt"))
        assertTrue(source.contains("private fun remoteArticleMetaText(article: RemoteArticle): String?"))
        assertTrue(source.contains("remoteArticleTimeText(article)"))
        assertTrue(source.contains("article.feedName?.takeIf { it.isNotBlank() }"))
        assertTrue(source.contains("article.domain?.takeIf { it.isNotBlank() }"))
    }

    @Test
    fun remoteArticleDetailMetadataPrefersPublishedAt() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt").readText()

        assertTrue(source.contains("article.publishedAt?.take(10) ?: article.createdAt?.take(10)"))
    }

    @Test
    fun remoteAndLocalArticleCardsUseMagazineNewsCardContent() {
        val remoteCards = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleCards.kt").readText()
        val localCard = File("src/main/kotlin/com/dailysatori/ui/component/card/ArticleCard.kt").readText()

        assertTrue(remoteCards.contains("MagazineNewsCard("))
        assertTrue(localCard.contains("MagazineNewsCard("))
        assertTrue(remoteCards.contains("remoteArticleIntroText(article)"))
        assertTrue(localCard.contains("articleIntroText(article"))
    }

    @Test
    fun magazineCardsUseReadableIntroFallbacks() {
        val remoteCards = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleCards.kt").readText()
        val localCard = File("src/main/kotlin/com/dailysatori/ui/component/card/ArticleCard.kt").readText()

        assertTrue(remoteCards.contains("remoteArticleIntroText"))
        assertTrue(remoteCards.contains("article.summary"))
        assertTrue(remoteCards.contains("article.viewpoints"))
        assertTrue(remoteCards.contains("article.content"))
        assertTrue(localCard.contains("articleIntroText"))
        assertTrue(localCard.contains("article.ai_content"))
        assertTrue(localCard.contains("article.ai_markdown_content"))
    }
}

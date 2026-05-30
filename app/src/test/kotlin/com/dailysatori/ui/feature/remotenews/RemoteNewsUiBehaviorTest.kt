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
    fun remoteArticleCacheSavesCompletedNonFavoriteWithoutProcessing() {
        val repository = File(
            "../shared/src/commonMain/kotlin/com/dailysatori/data/repository/ArticleRepository.kt",
        ).readText()
        val mapper = File(
            "../shared/src/commonMain/kotlin/com/dailysatori/data/repository/RemoteArticleFavoriteMapper.kt",
        ).readText()

        assertTrue(repository.contains("fun cacheRemoteArticle(remoteArticle: RemoteArticle, sourceTime: Long? = null): Article?"))
        assertTrue(mapper.contains("isFavorite = 0"))
        assertTrue(repository.contains("status = fields.status"))
        assertTrue(repository.contains("insertRemoteArticleCacheWithoutUrl"))
        assertFalse(repository.contains("WebpageParserService"))
        assertFalse(repository.contains("ArticleProcessingScheduler"))
    }

    @Test
    fun remoteArticleFavoriteReprocessesEnglishContentThroughLocalAiPipeline() {
        val remoteNewsViewModel = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt").readText()
        val unifiedNewsViewModel = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()
        val di = File("src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt").readText()

        assertTrue(remoteNewsViewModel.contains("needsLocalAiReprocessingForChineseOutput"))
        assertTrue(remoteNewsViewModel.contains("webpageParserService.reprocessArticle(savedId)"))
        assertTrue(unifiedNewsViewModel.contains("needsLocalAiReprocessingForChineseOutput"))
        assertTrue(unifiedNewsViewModel.contains("webpageParserService.reprocessArticle(savedId)"))
        assertTrue(di.contains("get<WebpageParserService>()"))
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

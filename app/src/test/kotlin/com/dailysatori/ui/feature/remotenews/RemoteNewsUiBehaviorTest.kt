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
    fun activeRemoteNewsListRespondsToScrollToTopRequests() {
        val source = unifiedSource("UnifiedNewsRemoteSourceContent")

        assertTrue(source.contains("LaunchedEffect(scrollToTopRequestKey)"))
        assertTrue(source.contains("listState.animateScrollToItem(0)"))
    }

    @Test
    fun remoteNewsListsUseStableKeysAndDerivedLoadMoreState() {
        val source = unifiedSource("UnifiedNewsRemoteSourceContent")

        assertTrue(source.contains("derivedStateOf"))
        assertTrue(source.contains("items(articles, key = { it.id })"))
        assertTrue(source.contains("item(key = \"source-article-loading-more\")"))
        assertTrue(source.contains("item(key = \"source-article-error\")"))
        assertTrue(source.contains("NewsStatusBanner("))
        assertTrue(source.contains("contentPadding = PaddingValues("))
        assertTrue(source.contains("NewsStateMessage("))
    }

    @Test
    fun remoteNewsDetailErrorsDoNotReuseListErrorState() {
        val viewModel = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt").readText()
        val screen = unifiedSource("UnifiedNewsScreen")

        assertTrue(viewModel.contains("detailError"))
        assertTrue(viewModel.substringAfter("fun openArticle").substringBefore("fun toggleSelectedArticleFavorite").contains("detailError"))
        assertFalse(viewModel.substringAfter("fun openArticle").substringBefore("fun toggleSelectedArticleFavorite").contains("error = result.message"))
        assertTrue(screen.contains("if (state.navigationTarget != null && detailError != null)"))
        assertTrue(screen.contains("UnifiedNewsSourceDetailErrorScreen(message = detailError"))
        assertTrue(unifiedSource("UnifiedNewsRemoteSourceContent").contains("state.sourceArticlesError"))
    }

    @Test
    fun remoteNewsListOpensArticleFromListPayloadWithoutDetailApi() {
        val viewModel = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt").readText()
        val screen = unifiedSource("UnifiedNewsScreen")
        val activeOpen = unifiedSource("UnifiedNewsViewModel").substringAfter("fun openSourceArticle(article: RemoteArticle)")
            .substringBefore("fun toggleSelectedRemoteArticleFavorite()")
        val openArticleBody = viewModel.substringAfter("fun openArticle(").substringBefore("fun toggleSelectedArticleFavorite")

        assertTrue(viewModel.contains("fun openArticle(article: RemoteArticle)"))
        assertTrue(openArticleBody.contains("selectedArticle = article"))
        assertFalse(openArticleBody.contains("remoteNewsService.fetchArticle"))
        assertTrue(screen.contains("onArticleClick = viewModel::openSourceArticle"))
        assertTrue(activeOpen.contains(") { article }"))
        assertFalse(activeOpen.contains("remoteNewsService.fetchArticle"))
    }

    @Test
    fun remoteArticleOpensEvenWhenFavoriteLookupFails() {
        val viewModel = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt").readText()
        val openBody = viewModel.substringAfter("fun openArticle(article: RemoteArticle)").substringBefore("fun toggleSelectedArticleFavorite")

        val compact = openBody.replace(Regex("\\s+"), " ")

        // 先发布选中文章再查询本地收藏：收藏查询失败不能阻止正文打开。
        assertTrue(compact.contains("selectedArticle = article"))
        assertTrue(compact.contains("articleRepo.findLocalArticleForRemote(article)"))
        assertTrue(
            compact.indexOf("selectedArticle = article") < compact.indexOf("findLocalArticleForRemote(article)"),
            "收藏查询必须在文章已发布之后进行",
        )
        // 协程取消继续传播，其余异常退化为“没有本地收藏”。
        assertTrue(
            compact.contains("catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }"),
            "取消必须继续传播",
        )
        assertTrue(compact.contains("catch (_: Exception) { null }"), "收藏查询失败不得中断打开")
        // 收藏语义不变：只依据本地文章标记收藏状态。
        assertTrue(compact.contains("selectedArticleIsFavorite = local?.is_favorite == 1L"))
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
    fun remoteArticleDetailFallsBackToFeedBodyWhenNoAiSummaryExists() {
        // RSS entries have no AI summary: the reading area shows the stored feed body instead of an empty state.
        assertEquals(
            "RSS 正文",
            remoteArticleDetailPageContent(page = 0, summary = null, viewpoints = emptyList(), original = "RSS 正文"),
        )

        val withSummary = remoteArticleDetailPageContent(
            page = 0,
            summary = "AI 摘要",
            viewpoints = listOf("观点"),
            original = "RSS 正文",
        )
        assertTrue(withSummary.contains("AI 摘要"))
        assertFalse(withSummary.contains("RSS 正文"))
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
        assertTrue(repository.contains("selectArticleByNoUrlRemoteContent"))
        assertTrue(remoteNews.contains("articleRepo.findLocalArticleForRemote(article)"))
        assertTrue(unifiedNews.contains("remoteArticleSyncService.readLocalArticle"))
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

        val compact = loadBody.replace(Regex("\\s+"), " ")

        assertTrue(repository.contains("fun saveRemoteArticleForSync("))
        assertTrue(compact.contains("remoteNewsSourceRepo.getEnabled().firstOrNull()"))
        assertTrue(compact.contains("remoteNewsService.fetchSourceArticles(sourceConfig.value, limit = 50)"))
        // 列表加载走被动入库：写入内容与来源日期，但不写入收藏。
        assertTrue(compact.contains("remoteArticleSyncService.storePassively("))
        assertTrue(compact.contains("remoteSourceId = source.id"))
        assertTrue(compact.contains("articles = result.value.articles"))
        assertTrue(compact.contains("val sourceDate = remoteNewsArticleSourceDate()"))
        assertTrue(compact.contains("remoteArticleSyncRepo.getArticlesBySourceDate(source.id, remoteNewsArticleSourceDate()"))
        assertFalse(compact.contains("saveRemoteArticleAsFavorite"), "被动同步不得写入收藏")
        assertTrue(repository.contains("fun saveRemoteArticleAsFavorite(remoteArticle: RemoteArticle): Article?"))
        assertTrue(mapper.contains("isFavorite: Long = 1L"))
        assertFalse(repository.contains("WebpageParserService"))
        assertFalse(repository.contains("ArticleProcessingScheduler"))
    }

    @Test
    fun activeRemoteSourceCanNavigateSyncedArticlesToLocalDetail() {
        val screen = unifiedSource("UnifiedNewsScreen")
        val viewModel = unifiedSource("UnifiedNewsViewModel")

        assertTrue(unifiedSource("UnifiedNewsRemoteSourceContent").contains("viewModel.openLocalArticle(article.id)"))
        assertTrue(viewModel.contains("beginDetailRequest(UnifiedNewsNavigationTarget.LocalArticle(id))"))
        assertTrue(screen.contains("as? UnifiedNewsNavigationTarget.LocalArticle"))
        assertTrue(screen.contains("onArticleClick(target.id)"))
    }

    private fun unifiedSource(name: String) = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/$name.kt").readText()

    @Test
    fun remoteArticleFavoriteReprocessesEnglishContentThroughLocalAiPipeline() {
        val remoteNewsViewModel = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt").readText()
        val unifiedNewsViewModel = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()
        val service = File("../shared/src/commonMain/kotlin/com/dailysatori/service/remotenews/RemoteArticleFavoriteService.kt").readText()
        val sharedDi = File("../shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt").readText()
        val handler = File("src/main/kotlin/com/dailysatori/core/task/ArticlePostProcessingTaskHandlers.kt").readText()

        assertTrue(remoteNewsViewModel.contains("remoteArticleFavoriteService.toggleFavorite(article, localId)"))
        assertTrue(unifiedNewsViewModel.contains("remoteArticleFavoriteService.toggleFavorite(article, localId)"))
        assertTrue(service.contains("needsLocalAiReprocessingForChineseOutput"))
        assertTrue(remoteNewsViewModel.contains("enqueueRemoteArticlePostProcessing"))
        assertTrue(unifiedNewsViewModel.contains("enqueueRemoteArticlePostProcessing"))
        assertTrue(handler.contains("parser.reprocessArticle(articleId)"))
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

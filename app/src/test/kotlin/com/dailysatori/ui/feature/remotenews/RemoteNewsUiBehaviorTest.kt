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
    fun remoteArticleSummaryCardMetadataIncludesArticleTime() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleCards.kt").readText()

        assertTrue(source.contains("private fun remoteArticleTimeText(article: RemoteArticle): String?"))
        assertTrue(source.contains("article.createdAt ?: article.processedAt"))
        assertTrue(source.contains("val timeText = remoteArticleTimeText(article)"))
        assertTrue(source.contains("timeText?.let { time ->"))
        assertTrue(source.contains("Text(time"))
    }
}

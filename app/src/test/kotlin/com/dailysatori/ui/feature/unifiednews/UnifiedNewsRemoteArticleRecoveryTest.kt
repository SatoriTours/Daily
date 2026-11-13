package com.dailysatori.ui.feature.unifiednews

import com.dailysatori.service.remotenews.RemoteArticle
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnifiedNewsRemoteArticleRecoveryTest {
    @Test
    fun recoveryMatchesStableIdFirstThenLink() {
        val cited = article(id = 7, url = "https://example.com/a")
        val other = article(id = 9, url = "https://example.com/b")
        val feed = listOf(other, cited)

        assertEquals(cited, remoteArticleFromFeed(feed, 7, null))
        assertEquals(cited, remoteArticleFromFeed(feed, 999, "https://example.com/a"))
        assertEquals(cited, remoteArticleFromFeed(feed, 999, " https://example.com/a "))
        assertNull(remoteArticleFromFeed(feed, 999, "https://example.com/missing"))
        assertNull(remoteArticleFromFeed(feed, 999, null))
        assertNull(remoteArticleFromFeed(emptyList(), 7, "https://example.com/a"))
    }

    @Test
    fun citedArticleReadsLocallyAndRecoversOnlyFromTheConfiguredFeed() {
        val viewModel = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()
        val open = viewModel.substringAfter("private fun openRemoteArticle(id: Long")
            .substringBefore("private suspend fun recoverRemoteArticleFromFeed(")
        val recovery = viewModel.substringAfter("private suspend fun recoverRemoteArticleFromFeed(")
            .substringBefore("private fun openRemoteArticle(source")

        // Local first, and the missing case never asks the removed JSON detail endpoint.
        assertTrue(open.contains("remoteArticleSyncService.readLocalArticle"))
        assertTrue(open.contains("recoverRemoteArticleFromFeed(id, remoteSourceId, url)"))
        assertFalse(open.contains("fetchArticle("))
        assertTrue(recovery.contains("remoteNewsService.fetchSourceArticles"))
        assertFalse(recovery.contains("fetchArticle("))

        // Unavailable content is reported explicitly, not guessed.
        assertTrue(open.contains("RemoteArticleContentUnavailable"))
        assertTrue(viewModel.contains("REMOTE_ARTICLE_LOCAL_MISSING"))
        assertTrue(viewModel.contains("REMOTE_ARTICLE_NOT_FOUND"))
        assertTrue(viewModel.contains("REMOTE_ARTICLE_SOURCE_UNAVAILABLE"))
    }

    private fun article(id: Long, url: String) = RemoteArticle(id = id, title = "标题", url = url, content = "正文")
}

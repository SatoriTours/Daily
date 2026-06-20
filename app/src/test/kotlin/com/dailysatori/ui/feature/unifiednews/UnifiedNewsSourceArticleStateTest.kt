package com.dailysatori.ui.feature.unifiednews

import com.dailysatori.service.remotenews.RemoteArticle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnifiedNewsSourceArticleStateTest {
    @Test
    fun removedRemoteSourceResetsSelectionToSummary() {
        val selection = UnifiedNewsSourceSelection.RemoteSource(id = 7L, name = "旧来源")
        val sources = listOf(UnifiedNewsRemoteSourceOption(id = 8L, name = "新来源"))

        assertTrue(shouldResetUnifiedNewsSourceSelection(selection, sources))
        assertEquals(UnifiedNewsSourceSelection.Summary, resolvedUnifiedNewsSourceSelection(selection, sources))
    }

    @Test
    fun existingRemoteSourceKeepsSelection() {
        val selection = UnifiedNewsSourceSelection.RemoteSource(id = 7L, name = "来源")
        val sources = listOf(UnifiedNewsRemoteSourceOption(id = 7L, name = "来源"))

        assertFalse(shouldResetUnifiedNewsSourceSelection(selection, sources))
        assertEquals(selection, resolvedUnifiedNewsSourceSelection(selection, sources))
    }

    @Test
    fun summaryAndLocalSelectionsNeverResetForRemoteSourceList() {
        val sources = emptyList<UnifiedNewsRemoteSourceOption>()

        assertFalse(shouldResetUnifiedNewsSourceSelection(UnifiedNewsSourceSelection.Summary, sources))
        assertFalse(shouldResetUnifiedNewsSourceSelection(UnifiedNewsSourceSelection.LocalArticles, sources))
    }

    @Test
    fun removedExternalFavoriteSourceResetsSelectionToSummary() {
        val selection = UnifiedNewsSourceSelection.ExternalFavoriteSource(id = 9L, name = "X 收藏")
        val remoteSources = emptyList<UnifiedNewsRemoteSourceOption>()
        val externalSources = listOf(UnifiedNewsExternalFavoriteSourceOption(id = 10L, name = "另一个收藏源"))

        assertTrue(shouldResetUnifiedNewsSourceSelection(selection, remoteSources, externalSources))
        assertEquals(UnifiedNewsSourceSelection.Summary, resolvedUnifiedNewsSourceSelection(selection, remoteSources, externalSources))
    }

    @Test
    fun existingExternalFavoriteSourceKeepsSelection() {
        val selection = UnifiedNewsSourceSelection.ExternalFavoriteSource(id = 9L, name = "X 收藏")
        val remoteSources = emptyList<UnifiedNewsRemoteSourceOption>()
        val externalSources = listOf(UnifiedNewsExternalFavoriteSourceOption(id = 9L, name = "X 收藏"))

        assertFalse(shouldResetUnifiedNewsSourceSelection(selection, remoteSources, externalSources))
        assertEquals(selection, resolvedUnifiedNewsSourceSelection(selection, remoteSources, externalSources))
    }

    @Test
    fun cacheChecksUseSourceIdAndSummaryDate() {
        val cachedArticle = RemoteArticle(id = 1L, title = "已缓存")
        val state = UnifiedNewsState(
            sourceArticlesByCacheKey = mapOf(sourceArticleCacheKey(7L, "2026-05-31") to listOf(cachedArticle)),
        )

        assertTrue(hasUnifiedNewsSourceArticlesCache(state, sourceId = 7L, summaryDate = "2026-05-31"))
        assertFalse(hasUnifiedNewsSourceArticlesCache(state, sourceId = 7L, summaryDate = "2026-06-01"))
        assertEquals(listOf(cachedArticle), cachedUnifiedNewsSourceArticles(state, sourceId = 7L, summaryDate = "2026-05-31"))
        assertEquals(emptyList(), cachedUnifiedNewsSourceArticles(state, sourceId = 8L, summaryDate = "2026-05-31"))
    }

    @Test
    fun sourceArticleLoadingStateTransitionsPreserveCachedArticles() {
        val cachedArticle = RemoteArticle(id = 1L, title = "已缓存")
        val state = UnifiedNewsState(
            sourceArticlesByCacheKey = mapOf(sourceArticleCacheKey(7L, "2026-05-31") to listOf(cachedArticle)),
            sourceArticlesError = "旧错误",
        )

        val loading = state.withUnifiedNewsSourceArticlesLoading(sourceId = 7L)
        assertEquals(7L, loading.sourceArticlesLoadingSourceId)
        assertNull(loading.sourceArticlesError)
        assertEquals(state.sourceArticlesByCacheKey, loading.sourceArticlesByCacheKey)

        val failed = loading.withUnifiedNewsSourceArticlesFailure("新错误")
        assertNull(failed.sourceArticlesLoadingSourceId)
        assertEquals("新错误", failed.sourceArticlesError)
        assertEquals(state.sourceArticlesByCacheKey, failed.sourceArticlesByCacheKey)
    }
}

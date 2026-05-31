package com.dailysatori.ui.feature.unifiednews

import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.service.remotenews.RemoteDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class UnifiedNewsDetailStateTest {
    @Test
    fun clearSourceDetailRemovesAllSelectedDetailPayloadsWithoutChangingSourceCache() {
        val article = RemoteArticle(id = 7L, title = "远程文章")
        val state = UnifiedNewsState(
            selectedRemoteDigest = RemoteDigest(id = 9L, date = "2026-05-31"),
            selectedRemoteArticle = article,
            selectedRemoteArticleLocalId = 11L,
            selectedRemoteArticleIsFavorite = true,
            sourceArticlesByCacheKey = mapOf(sourceArticleCacheKey(1L, "2026-05-31") to listOf(article)),
        )

        val cleared = state.withUnifiedNewsSourceDetailCleared()

        assertNull(cleared.selectedRemoteDigest)
        assertNull(cleared.selectedRemoteArticle)
        assertNull(cleared.selectedRemoteArticleLocalId)
        assertFalse(cleared.selectedRemoteArticleIsFavorite)
        assertEquals(state.sourceArticlesByCacheKey, cleared.sourceArticlesByCacheKey)
    }

    @Test
    fun beginDetailRequestClearsPreviousDetailAndSetsNavigationTarget() {
        val target = UnifiedNewsNavigationTarget.RemoteDigest(id = 99L)
        val state = UnifiedNewsState(
            selectedRemoteDigest = RemoteDigest(id = 9L, date = "2026-05-31"),
            error = "旧错误",
        )

        val next = state.withUnifiedNewsDetailRequestStarted(target)

        assertEquals(target, next.navigationTarget)
        assertNull(next.selectedRemoteDigest)
        assertNull(next.error)
    }

    @Test
    fun closeSourceDetailClearsNavigationAndLoading() {
        val state = UnifiedNewsState(
            navigationTarget = UnifiedNewsNavigationTarget.RemoteArticle(id = 1L, remoteSourceId = 2L),
            selectedRemoteArticle = RemoteArticle(id = 1L, title = "远程文章"),
            isLoading = true,
        )

        val closed = state.withUnifiedNewsSourceDetailClosed()

        assertNull(closed.navigationTarget)
        assertNull(closed.selectedRemoteArticle)
        assertFalse(closed.isLoading)
    }
}

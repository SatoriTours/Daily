package com.dailysatori.ui.feature.remotenews

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteNewsLoadStateTest {
    @Test
    fun normalPageLoadShowsLoadingAndClearsErrors() {
        val state = RemoteNewsState(error = "旧错误", loadMoreError = "更多错误")

        val next = state.withRemoteNewsPageLoadStarted(refresh = false, append = false)

        assertTrue(next.isLoading)
        assertFalse(next.isRefreshing)
        assertFalse(next.isLoadingMore)
        assertNull(next.error)
        assertNull(next.loadMoreError)
    }

    @Test
    fun refreshLoadUsesRefreshingFlagOnly() {
        val next = RemoteNewsState(refreshCompletedToken = 2).withRemoteNewsPageLoadStarted(refresh = true, append = false)

        assertFalse(next.isLoading)
        assertTrue(next.isRefreshing)
        assertFalse(next.isLoadingMore)
    }

    @Test
    fun appendLoadUsesLoadingMoreFlagOnly() {
        val next = RemoteNewsState().withRemoteNewsPageLoadStarted(refresh = false, append = true)

        assertFalse(next.isLoading)
        assertFalse(next.isRefreshing)
        assertTrue(next.isLoadingMore)
    }

    @Test
    fun refreshCompletionOnlyIncrementsTokenForRefresh() {
        val state = RemoteNewsState(refreshCompletedToken = 5, isLoading = true, isRefreshing = true, isLoadingMore = true)

        val refreshed = state.withRemoteNewsPageLoadFinished(refresh = true)
        assertEquals(6, refreshed.refreshCompletedToken)
        assertFalse(refreshed.isLoading)
        assertFalse(refreshed.isRefreshing)
        assertFalse(refreshed.isLoadingMore)

        val normal = state.withRemoteNewsPageLoadFinished(refresh = false)
        assertEquals(5, normal.refreshCompletedToken)
    }

    @Test
    fun failureUsesListErrorForInitialLoadAndLoadMoreErrorForAppend() {
        val state = RemoteNewsState(error = "保留旧列表错误")

        val initialFailure = state.withRemoteNewsPageLoadFailure("加载失败", append = false)
        assertEquals("加载失败", initialFailure.error)
        assertNull(initialFailure.loadMoreError)
        assertFalse(initialFailure.isLoading)

        val appendFailure = state.withRemoteNewsPageLoadFailure("更多失败", append = true)
        assertEquals("保留旧列表错误", appendFailure.error)
        assertEquals("更多失败", appendFailure.loadMoreError)
        assertFalse(appendFailure.isLoadingMore)
    }
}

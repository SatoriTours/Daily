package com.dailysatori.core.task

import com.dailysatori.service.remotenews.RemoteArticle
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NewsSourceShareResolutionTest {
    private fun article(id: Long) = RemoteArticle(id = id, title = "article " + id)

    @Test
    fun aMergedCallReloadsTheRepositoryResultAndClearsLoading() = runBlocking {
        var reloads = 0
        var loaded: List<RemoteArticle>? = null
        var failure: String? = null

        resolveSharedSourceArticles(
            share = NewsSourceFetchShare.Merged,
            reload = { reloads++; listOf(article(1), article(2)) },
            onLoaded = { loaded = it },
            onFailure = { failure = it },
        )

        assertEquals(1, reloads, "a merged call must read the owner's result from the repository")
        assertEquals(listOf(1L, 2L), loaded?.map { it.id })
        assertTrue(failure == null, "a merged call is not a failure")
    }

    @Test
    fun aFailedShareReportsFailureWithoutReadingTheRepository() = runBlocking {
        var reloads = 0
        var loaded = false
        var failure: String? = null

        resolveSharedSourceArticles(
            share = NewsSourceFetchShare.Failed(null),
            reload = { reloads++; emptyList() },
            onLoaded = { loaded = true },
            onFailure = { failure = it },
        )

        assertEquals(0, reloads)
        assertTrue(!loaded)
        assertTrue(failure!!.isNotBlank(), "a failed share keeps the caller's failure path")
    }

    @Test
    fun anOwnedCallDoesNotRepeatWorkTheBlockAlreadyDid() = runBlocking {
        var reloads = 0
        var loaded = false
        var failure: String? = null

        resolveSharedSourceArticles(
            share = NewsSourceFetchShare.Owned(Unit),
            reload = { reloads++; emptyList() },
            onLoaded = { loaded = true },
            onFailure = { failure = it },
        )

        assertEquals(0, reloads)
        assertTrue(!loaded)
        assertTrue(failure == null)
    }
}


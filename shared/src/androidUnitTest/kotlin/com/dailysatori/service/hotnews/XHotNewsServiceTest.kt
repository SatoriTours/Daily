package com.dailysatori.service.hotnews

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.test.*

class XHotNewsServiceTest {
    @Test fun officialAuthorizationErrorRetainsCachedPostsAndTheirFetchTime() = withStore { http, store ->
        val item = HotNewsItem("12", HotNewsPlatform.X, 1, "已缓存的帖子", "https://x.com/i/status/12")
        store.save(HotNewsBoard(HotNewsPlatform.X, listOf(item), fetchedAt = 123))
        val service = HotNewsService(HotNewsClient(http) { throw XHotNewsException(HotNewsError.X_QUOTA_EXCEEDED) },
            store, { error("Failed fetching must not translate") })
        service.refresh(HotNewsPlatform.X, force = true)
        val board = service.boards.value.first { it.platform == HotNewsPlatform.X }
        assertEquals(listOf(item), board.items)
        assertEquals(123L, board.fetchedAt)
        assertEquals(HotNewsError.X_QUOTA_EXCEEDED, board.error)
        assertFalse(board.refreshing)
    }

    @Test fun validEmptyOfficialResultsAreCachedInsteadOfSpendingAgainOnEveryRefresh() = withStore { http, store ->
        var requests = 0
        val service = HotNewsService(HotNewsClient(http) { requests++; emptyList() }, store,
            { error("Empty results must not translate") })
        service.refresh(HotNewsPlatform.X)
        service.refresh(HotNewsPlatform.X)
        assertEquals(1, requests)
        val board = service.boards.value.first { it.platform == HotNewsPlatform.X }
        assertTrue(board.items.isEmpty())
        assertNull(board.error)
        assertTrue(board.fetchedAt > 0)
    }

    @Test fun legacyFeedCacheDoesNotMasqueradeAsFreshOfficialPostsAfterUpgrade() = withStore { _, store ->
        store.save(HotNewsBoard(HotNewsPlatform.X, listOf(HotNewsItem("old-topic", HotNewsPlatform.X, 1,
            "旧的 RSS 话题", "https://x.com/explore", fromFeed = true)),
            fetchedAt = Clock.System.now().toEpochMilliseconds()))
        assertNull(store.load(HotNewsPlatform.X))
    }

    @Test fun automaticRefreshCannotEraseQuotaErrorsByTreatingStaleCacheAsSuccessfulFetch() = withStore { http, store ->
        store.save(HotNewsBoard(HotNewsPlatform.X, listOf(HotNewsItem("12", HotNewsPlatform.X, 1,
            "缓存内容", "https://x.com/i/status/12")), fetchedAt = Clock.System.now().toEpochMilliseconds()))
        var requests = 0
        val service = HotNewsService(HotNewsClient(http) {
            requests++
            throw XHotNewsException(HotNewsError.X_QUOTA_EXCEEDED)
        }, store, { error("Quota errors must not become successful cached translations") })
        service.refresh(HotNewsPlatform.X, force = true)
        service.refresh(HotNewsPlatform.X)
        assertEquals(1, requests)
        assertEquals(HotNewsError.X_QUOTA_EXCEEDED, service.boards.value.first { it.platform == HotNewsPlatform.X }.error)
        service.refresh(HotNewsPlatform.X, force = true)
        assertEquals(2, requests)
    }

    private fun withStore(block: suspend (HttpClient, HotNewsStore) -> Unit) = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            HttpClient(MockEngine { error("X official routing must not make generic requests") }).use { http ->
                block(http, HotNewsStore(SettingRepository(DailySatoriDatabase(driver))))
            }
        }
    }
}

package com.dailysatori.service.hotnews

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import java.net.SocketException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.test.*

class HotNewsTranslationRecoveryTest {
    private val items = listOf(
        HotNewsItem("acme/docs", HotNewsPlatform.GITHUB, 1, "acme/docs", "https://github.com/acme/docs"),
        HotNewsItem("acme/reader", HotNewsPlatform.GITHUB, 2, "acme/reader", "https://github.com/acme/reader"),
    )
    private val partialResponse = """[
        {"id":"github:acme/docs","title":"文档转换工具","summary":""},
        {"id":"github:acme/reader","title":"Still English","summary":""}
    ]"""

    @Test fun malformedEntryDoesNotDiscardOtherValidTranslations() {
        val result = parseHotNewsTranslations(partialResponse, items)
        assertEquals(listOf(items.first().identity), result.map { it.identity })
        assertEquals(items.first().url, result.single().url)
        assertEquals("文档转换工具", result.single().chineseTitle)
    }

    @Test fun omittedEntriesDoNotDiscardMatchingTranslations() {
        val result = parseHotNewsTranslations(
            """[{"id":"github:acme/reader","title":"本地阅读工具","summary":""}]""", items)
        assertEquals(listOf(items.last().identity), result.map { it.identity })
    }

    @Test fun duplicateIdsAndUnknownEntriesCannotReplaceValidItems() {
        val response = """[
            {"id":"github:acme/docs","title":"文档转换工具","summary":""},
            {"id":"github:acme/docs","title":"重复标题","summary":""},
            {"id":"unknown","title":"无关标题","summary":""},
            null,
            {"id":"github:acme/reader","title":"本地阅读工具","summary":""}
        ]"""
        val result = parseHotNewsTranslations(response, items)
        assertEquals(listOf(items.last().identity), result.map { it.identity })
    }

    @Test fun partialBatchRetriesOnlyTheUntranslatedItem() {
        val calls = mutableListOf<List<String>>()
        withCachedBoard({ batch ->
            calls += batch.map { it.id }
            val response = if (batch.size > 1) partialResponse
                else """[{"id":"github:acme/reader","title":"本地阅读工具","summary":""}]"""
            parseHotNewsTranslations(response, batch)
        }) { service, store ->
            service.refresh(HotNewsPlatform.GITHUB)
            assertEquals(listOf(items.map { it.id }, listOf(items.last().id)), calls)
            val board = service.boards.value.first { it.platform == HotNewsPlatform.GITHUB }
            assertEquals(listOf("文档转换工具", "本地阅读工具"), board.items.map { it.chineseTitle })
            assertNull(board.error)
            assertEquals(board.items, store.load(HotNewsPlatform.GITHUB)!!.items)
        }
    }

    @Test fun permanentlyInvalidItemLeavesSuccessfulTranslationAvailable() {
        var attempts = 0
        withCachedBoard({ batch ->
            attempts++
            parseHotNewsTranslations(partialResponse, batch)
        }) { service, store ->
            service.refresh(HotNewsPlatform.GITHUB)
            val board = service.boards.value.first { it.platform == HotNewsPlatform.GITHUB }
            assertEquals(2, attempts)
            assertEquals("文档转换工具", board.items.first().chineseTitle)
            assertNull(board.items.last().chineseTitle)
            assertEquals(HotNewsError.TRANSLATION_FAILED, board.error)
            assertFalse(board.refreshing)
            assertEquals(board.items, store.load(HotNewsPlatform.GITHUB)!!.items)
        }
    }

    @Test fun explicitTranslationRetryUsesEvenExpiredRankingWithoutFetching() {
        withCachedBoard({ batch -> batch.map { it.copy(chineseTitle = "中文工具标题", chineseSummary = "") } },
            fetchedAt = 1L) { service, store ->
            service.retryTranslations(HotNewsPlatform.GITHUB)
            val board = service.boards.value.first { it.platform == HotNewsPlatform.GITHUB }
            assertEquals(1L, board.fetchedAt)
            assertTrue(board.items.all { it.chineseTitle != null })
            assertNull(board.error)
            assertEquals(board.items, store.load(HotNewsPlatform.GITHUB)!!.items)
        }
    }

    @Test fun networkFailureDoesNotTriggerPerItemRequestStorm() {
        var attempts = 0
        withCachedBoard({ attempts++; throw IllegalStateException("Network unavailable") }) { service, _ ->
            service.refresh(HotNewsPlatform.GITHUB)
            assertEquals(1, attempts)
            val board = service.boards.value.first { it.platform == HotNewsPlatform.GITHUB }
            assertEquals(HotNewsError.TRANSLATION_FAILED, board.error)
            assertFalse(board.refreshing)
        }
    }

    @Test fun aBrokenAiConnectionRetriesTheSameBatchAndKeepsSuccessfulTranslations() {
        val calls = mutableListOf<List<String>>()
        withCachedBoard({ batch ->
            calls += batch.map { it.id }
            if (calls.size < 3) throw IllegalStateException("transport wrapper", SocketException("connection reset"))
            batch.map { it.copy(chineseTitle = "中文工具标题", chineseSummary = "") }
        }) { service, store ->
            service.retryTranslations(HotNewsPlatform.GITHUB)
            assertEquals(List(3) { items.map { it.id } }, calls)
            val board = service.boards.value.first { it.platform == HotNewsPlatform.GITHUB }
            assertTrue(board.items.all { it.chineseTitle != null })
            assertNull(board.error)
            assertFalse(board.refreshing)
            assertEquals(board.items, store.load(HotNewsPlatform.GITHUB)!!.items)
        }
    }

    @Test fun persistentNetworkFailureStopsAfterThreeAttemptsWithoutPerItemOrLaterBatchStorms() {
        val translated = items.first().copy(chineseTitle = "已完成的文档工具", chineseSummary = "")
        val pending = (1..8).map { items.last().copy(id = "acme/reader-$it") }
        val calls = mutableListOf<List<String>>()
        withCachedBoard({ batch ->
            calls += batch.map { it.id }
            throw SocketException("connection reset")
        }, cachedItems = listOf(translated) + pending) { service, store ->
            service.retryTranslations(HotNewsPlatform.GITHUB)
            assertEquals(List(3) { pending.take(5).map { it.id } }, calls)
            val board = service.boards.value.first { it.platform == HotNewsPlatform.GITHUB }
            assertEquals("TRANSLATION_NETWORK_FAILED", board.error?.name)
            assertEquals(translated, board.items.first())
            assertFalse(board.refreshing)
            assertEquals(board, store.load(HotNewsPlatform.GITHUB))
        }
    }

    @Test fun cancellingTranslationDoesNotRetryOrReportANetworkFailure() {
        var attempts = 0
        withCachedBoard({ attempts++; throw CancellationException("user cancelled") }) { service, _ ->
            assertFailsWith<CancellationException> { service.retryTranslations(HotNewsPlatform.GITHUB) }
            assertEquals(1, attempts)
            val board = service.boards.value.first { it.platform == HotNewsPlatform.GITHUB }
            assertNull(board.error)
            assertFalse(board.refreshing)
        }
    }

    @Test fun incompleteCacheWithoutAnErrorStillResumesTranslation() {
        var attempts = 0
        withCachedBoard({ batch ->
            attempts++
            batch.map { it.copy(chineseTitle = "中文工具标题", chineseSummary = "") }
        }, initialError = null) { service, _ ->
            service.refresh(HotNewsPlatform.GITHUB)
            assertEquals(1, attempts)
            assertTrue(service.boards.value.first { it.platform == HotNewsPlatform.GITHUB }
                .items.all { it.chineseTitle != null })
        }
    }

    private fun withCachedBoard(
        translate: suspend (List<HotNewsItem>) -> List<HotNewsItem>,
        fetchedAt: Long = Clock.System.now().toEpochMilliseconds(),
        initialError: HotNewsError? = HotNewsError.TRANSLATION_FAILED,
        cachedItems: List<HotNewsItem> = items,
        verify: suspend (HotNewsService, HotNewsStore) -> Unit,
    ) = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val store = HotNewsStore(SettingRepository(DailySatoriDatabase(driver)))
            store.save(HotNewsBoard(HotNewsPlatform.GITHUB, cachedItems, fetchedAt,
                error = initialError))
            HttpClient(MockEngine { error("Translation retry must use the cached ranking") }).use { http ->
                verify(HotNewsService(HotNewsClient(http), store, translate), store)
            }
        }
    }
}

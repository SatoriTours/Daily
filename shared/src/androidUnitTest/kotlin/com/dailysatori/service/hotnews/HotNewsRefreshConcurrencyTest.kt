package com.dailysatori.service.hotnews

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class HotNewsRefreshConcurrencyTest {
    @Test
    fun retryingAFailedBoardDoesNotWaitForAnUnrelatedSourceRequest() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val store = HotNewsStore(SettingRepository(DailySatoriDatabase(driver)))
            val item = HotNewsItem("acme/docs", HotNewsPlatform.GITHUB, 1, "acme/docs", "https://github.com/acme/docs")
            store.save(HotNewsBoard(HotNewsPlatform.GITHUB, listOf(item), 1L, HotNewsError.TRANSLATION_FAILED))
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            HttpClient(MockEngine {
                started.complete(Unit)
                release.await()
                respond("""[{"id":8,"title":"中文新闻标题","url":"https://v2ex.com/t/8"}]""")
            }).use { http ->
                var translated = 0
                val service = HotNewsService(HotNewsClient(http), store, { batch ->
                    translated++
                    batch.map { it.copy(chineseTitle = "文档转换工具", chineseSummary = "") }
                })
                val refresh = launch { service.refresh(HotNewsPlatform.V2EX, force = true) }
                started.await()
                try {
                    val completed = withTimeoutOrNull(1_000) {
                        service.retryTranslations(HotNewsPlatform.GITHUB)
                        true
                    }
                    assertEquals(true, completed, "另一来源仍在加载时，当前榜单的重试也必须执行")
                    assertFalse(refresh.isCompleted)
                    assertEquals(1, translated)
                    val board = service.boards.value.first { it.platform == HotNewsPlatform.GITHUB }
                    assertEquals("文档转换工具", board.items.single().chineseTitle)
                    assertNull(board.error)
                } finally {
                    release.complete(Unit)
                    refresh.join()
                }
            }
        }
    }
}

package com.dailysatori.service.externalfavorites

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.ExternalFavoriteItemRepository
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class XFavoriteRemoteOrderSyncTest {
    @Test
    fun existingXFavoritesAreReorderedToMatchRemotePages() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        val db = DailySatoriDatabase(driver)
        val sources = ExternalFavoriteSourceRepository(
            db = db,
            encryptSecret = { it },
            decryptSecret = { it },
        )
        val items = ExternalFavoriteItemRepository(db)
        val articles = ArticleRepository(db)
        val sourceId = sources.save(
            provider = ExternalFavoriteProvider.X.id,
            displayName = "X 收藏",
            accountId = "42",
            accountName = "daily",
            authJson = """{"access_token":"token"}""",
            configJson = """{"history_complete":true}""",
        )
        listOf("100", "200", "300").forEach { id ->
            val (item, _) = items.upsertDraft(sourceId, draftWithoutFavoriteTime(id))
            val articleId = articles.insert(title = id, url = "https://x.com/daily/status/$id", status = "completed")
            items.markImported(item.id, articleId, duplicateLinked = false)
        }
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    val cursor = request.url.parameters["pagination_token"]
                    val body = if (cursor == null) {
                        xPage(listOf("300", "100"), nextCursor = "page-2")
                    } else {
                        xPage(listOf("200"), nextCursor = null)
                    }
                    respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        }
        val connector = XBookmarksConnector(client)
        FavoriteSyncService(
            sourceRepo = sources,
            itemRepo = items,
            registry = FavoriteConnectorRegistry(listOf(connector)),
            importPending = { 0 },
            organizePending = { 0 },
        ).syncSource(sourceId, FavoriteSyncMode.sync)

        val orderedTitles = articles.getExternalFavoritesBySourceSync(sourceId).map { it.title }
        assertEquals(listOf("300", "100", "200"), orderedTitles)
        items.getBySource(sourceId).forEach { assertNotNull(it.favorited_at) }
        assertEquals(2, sources.getById(sourceId)?.last_pages_seen_count)
    }

    private fun draftWithoutFavoriteTime(id: String) = ExternalFavoriteItemDraft(
        provider = ExternalFavoriteProvider.X.id,
        externalId = id,
        canonicalUrl = "https://x.com/daily/status/$id",
        title = id,
        text = "Body $id",
        authorName = "Daily",
        sourceCreatedAt = 1_700_000_000_000,
        favoritedAt = null,
        normalizedJson = """{"id":"$id"}""",
        contentHash = "content-$id",
        aiInputHash = "ai-$id",
    )

    private fun xPage(ids: List<String>, nextCursor: String?): String {
        val data = ids.joinToString(",") { id ->
            """{"id":"$id","text":"Body $id","author_id":"42","created_at":"2026-01-01T00:00:00Z"}"""
        }
        val next = nextCursor?.let { ",\"next_token\":\"$it\"" }.orEmpty()
        return """{"data":[$data],"includes":{"users":[{"id":"42","username":"daily","name":"Daily"}]},"meta":{"result_count":${ids.size}$next}}"""
    }
}

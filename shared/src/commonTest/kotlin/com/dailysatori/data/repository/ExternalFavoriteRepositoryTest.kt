package com.dailysatori.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.service.externalfavorites.ExternalFavoriteItemDraft
import com.dailysatori.service.externalfavorites.ExternalFavoriteProvider
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExternalFavoriteRepositoryTest {
    @Test
    fun sourceRepositoryEncryptsAuthJsonAndDecryptsOnRead() = withRepositories { db, sources, _ ->
        val sourceId = saveXSource(
            sources = sources,
            displayName = "X Favorites",
            accountId = "acct-1",
            accountName = "@daily",
            authJson = """{"access_token":"secret"}""",
        )

        val raw = db.dailySatoriQueries.selectExternalFavoriteSourceById(sourceId).executeAsOne()
        assertEquals("""enc:v1:{"access_token":"secret"}""", raw.auth_json)

        val decrypted = sources.getById(sourceId)
        assertNotNull(decrypted)
        assertEquals("""{"access_token":"secret"}""", decrypted.auth_json)
        assertEquals("""{"access_token":"secret"}""", sources.getAll().single().auth_json)
        assertEquals("""{"access_token":"secret"}""", sources.getEnabled().single().auth_json)
        assertEquals(
            """{"access_token":"secret"}""",
            sources.getByProviderAccount(ExternalFavoriteProvider.X.id, "acct-1")?.auth_json,
        )

        sources.markAuthCheckRequiredAfterRestore()
        assertEquals("auth_check_required", sources.getById(sourceId)?.status)
    }

    @Test
    fun itemRepositoryUpsertsBySourceScopedExternalId() = withRepositories { _, sources, items ->
        val firstSourceId = saveXSource(sources, accountId = "acct-1")
        val secondSourceId = saveXSource(sources, accountId = "acct-2")

        val (firstItem, firstInserted) = items.upsertDraft(firstSourceId, xDraft(externalId = "post-1", title = "Old"))
        val (updatedItem, secondInserted) = items.upsertDraft(firstSourceId, xDraft(externalId = "post-1", title = "New"))
        val (otherSourceItem, otherInserted) = items.upsertDraft(secondSourceId, xDraft(externalId = "post-1", title = "Other"))

        assertTrue(firstInserted)
        assertEquals(false, secondInserted)
        assertTrue(otherInserted)
        assertEquals(firstItem.id, updatedItem.id)
        assertEquals("New", updatedItem.title)
        assertEquals(otherSourceItem.id, items.getBySource(secondSourceId).single().id)
        assertEquals(1, items.getBySource(firstSourceId).size)
        assertEquals(1, items.getBySource(secondSourceId).size)
    }

    @Test
    fun deleteSourceRemovesSourceItemsButKeepsArticleRows() = withRepositories { db, sources, items ->
        val articleRepository = ArticleRepository(db)
        val sourceId = saveXSource(sources)
        val (item, _) = items.upsertDraft(sourceId, xDraft(externalId = "post-1"))
        val articleId = articleRepository.insert(
            title = "Imported",
            aiContent = "Imported body",
            url = "https://example.com/imported",
            isFavorite = 1,
            status = "completed",
        )

        items.markImported(item.id, articleId, duplicateLinked = false)
        sources.delete(sourceId)

        assertNull(sources.getById(sourceId))
        assertEquals(emptyList(), items.getBySource(sourceId))
        assertNotNull(articleRepository.getById(articleId))
    }

    private fun withRepositories(
        block: (
            db: DailySatoriDatabase,
            sources: ExternalFavoriteSourceRepository,
            items: ExternalFavoriteItemRepository,
        ) -> Unit,
    ) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        DailySatoriDatabase.Schema.create(driver)
        val db = DailySatoriDatabase(driver)
        val sources = ExternalFavoriteSourceRepository(
            db = db,
            encryptSecret = { value -> if (value.isBlank()) value else "enc:v1:$value" },
            decryptSecret = { value -> value.removePrefix("enc:v1:") },
        )
        val items = ExternalFavoriteItemRepository(db)
        block(db, sources, items)
    }

    private fun saveXSource(
        sources: ExternalFavoriteSourceRepository,
        displayName: String = "X Favorites",
        accountId: String = "acct-1",
        accountName: String = "@daily",
        authJson: String = """{"access_token":"secret"}""",
    ): Long = sources.save(
        provider = ExternalFavoriteProvider.X.id,
        displayName = displayName,
        accountId = accountId,
        accountName = accountName,
        authJson = authJson,
    )

    private fun xDraft(
        externalId: String = "post-1",
        title: String = "Title",
    ): ExternalFavoriteItemDraft = ExternalFavoriteItemDraft(
        provider = ExternalFavoriteProvider.X.id,
        externalId = externalId,
        canonicalUrl = "https://example.com/$externalId",
        title = title,
        text = "Body for $title",
        authorName = "Author",
        sourceCreatedAt = 1_700_000_000_000,
        favoritedAt = 1_700_000_100_000,
        normalizedJson = """{"id":"$externalId"}""",
        contentHash = "content-$externalId-$title",
        aiInputHash = "ai-$externalId-$title",
    )
}

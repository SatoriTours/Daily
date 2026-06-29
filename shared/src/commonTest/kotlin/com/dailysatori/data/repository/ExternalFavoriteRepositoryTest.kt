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
    fun sourceRepositoryEncryptsLegacyPlaintextAuthJson() = withRepositories { db, sources, _ ->
        val now = 1_700_000_000_000
        db.dailySatoriQueries.insertExternalFavoriteSource(
            provider = ExternalFavoriteProvider.X.id,
            display_name = "Legacy X Favorites",
            account_id = "legacy-acct",
            account_name = "@legacy",
            enabled = 1,
            sync_interval_minutes = 720,
            status = "idle",
            auth_json = """{"access_token":"legacy"}""",
            config_json = "",
            capabilities_json = "",
            created_at = now,
            updated_at = now,
        )

        sources.encryptStoredSecrets()

        val raw = db.dailySatoriQueries
            .selectExternalFavoriteSourceByProviderAccount(ExternalFavoriteProvider.X.id, "legacy-acct")
            .executeAsOne()
        assertEquals("""enc:v1:{"access_token":"legacy"}""", raw.auth_json)
        assertEquals("""{"access_token":"legacy"}""", sources.getById(raw.id)?.auth_json)
    }

    @Test
    fun sourceRepositoryUpdatesExistingProviderAccountWhenSavingAgain() = withRepositories { db, sources, _ ->
        val firstId = saveXSource(
            sources = sources,
            displayName = "Old X",
            accountId = "acct-1",
            accountName = "old",
            authJson = """{"access_token":"old"}""",
        )

        val secondId = sources.save(
            provider = ExternalFavoriteProvider.X.id,
            displayName = "New X",
            accountId = "acct-1",
            accountName = "new",
            authJson = """{"access_token":"new"}""",
            status = "idle",
        )

        assertEquals(firstId, secondId)
        assertEquals(1, sources.getAll().size)
        val updated = sources.getById(firstId)
        assertNotNull(updated)
        assertEquals("New X", updated.display_name)
        assertEquals("new", updated.account_name)
        assertEquals("idle", updated.status)
        assertEquals("""{"access_token":"new"}""", updated.auth_json)
        assertEquals("""enc:v1:{"access_token":"new"}""", db.dailySatoriQueries.selectExternalFavoriteSourceById(firstId).executeAsOne().auth_json)
    }

    @Test
    fun itemRepositoryUpsertsBySourceScopedExternalId() = withRepositories { _, sources, items ->
        val firstSourceId = saveXSource(sources, accountId = "acct-1")
        val secondSourceId = saveXSource(sources, accountId = "acct-2")

        val (firstItem, firstInserted) = items.upsertDraft(firstSourceId, xDraft(externalId = "post-1", title = "Old"))
        val (updatedItem, secondInserted) = items.upsertDraft(firstSourceId, xDraft(externalId = "post-1", title = "New"))
        val (otherSourceItem, otherInserted) = items.upsertDraft(secondSourceId, xDraft(externalId = "post-1", title = "Other"))
        val (_, unchangedInserted) = items.upsertDraft(firstSourceId, xDraft(externalId = "post-1", title = "New"))

        assertTrue(firstInserted)
        assertTrue(secondInserted)
        assertTrue(otherInserted)
        assertEquals(false, unchangedInserted)
        assertEquals(firstItem.id, updatedItem.id)
        assertEquals("New", updatedItem.title)
        assertEquals(otherSourceItem.id, items.getBySource(secondSourceId).single().id)
        assertEquals(1, items.getBySource(firstSourceId).size)
        assertEquals(1, items.getBySource(secondSourceId).size)
    }

    @Test
    fun itemRepositoryResetsImportAndAiStateWhenImportedDraftContentChanges() = withRepositories { db, sources, items ->
        val articleRepository = ArticleRepository(db)
        val sourceId = saveXSource(sources)
        val (item, _) = items.upsertDraft(sourceId, xDraft(externalId = "post-1", title = "Old"))
        val articleId = articleRepository.insert(
            title = "Imported",
            aiContent = "Imported body",
            url = "https://example.com/imported-for-reset",
            isFavorite = 1,
            status = "completed",
        )
        items.markImported(item.id, articleId, duplicateLinked = false)
        items.markAiState(item.id, "completed")

        val (changed, changedFlag) = items.upsertDraft(sourceId, xDraft(externalId = "post-1", title = "New"))

        assertTrue(changedFlag)
        assertEquals(articleId, changed.article_id)
        assertEquals("not_imported", changed.import_status)
        assertEquals("pending", changed.ai_status)
        assertEquals("", changed.last_error_code)
        assertEquals("", changed.last_error_message)
        assertEquals(listOf(changed.id), items.pendingImport(10).map { it.id })
    }

    @Test
    fun itemRepositoryTreatsSourceCreatedAtChangeAsChangedDraftContent() = withRepositories { db, sources, items ->
        val articleRepository = ArticleRepository(db)
        val sourceId = saveXSource(sources)
        val originalDraft = xDraft(externalId = "post-1", title = "Same")
        val (item, _) = items.upsertDraft(sourceId, originalDraft)
        val articleId = articleRepository.insert(
            title = "Imported",
            aiContent = "Imported body",
            url = "https://example.com/timestamp-reset",
            isFavorite = 1,
            status = "completed",
        )
        items.markImported(item.id, articleId, duplicateLinked = false)
        items.markAiState(item.id, "completed")

        val (changed, changedFlag) = items.upsertDraft(
            sourceId,
            originalDraft.copy(sourceCreatedAt = originalDraft.sourceCreatedAt?.plus(1_000)),
        )

        assertTrue(changedFlag)
        assertEquals(originalDraft.sourceCreatedAt?.plus(1_000), changed.source_created_at)
        assertEquals(articleId, changed.article_id)
        assertEquals("not_imported", changed.import_status)
        assertEquals("pending", changed.ai_status)
    }

    @Test
    fun pendingImportIncludesImportedItemsWhoseLinkedArticleWasDeleted() = withRepositories { db, sources, items ->
        val articleRepository = ArticleRepository(db)
        val sourceId = saveXSource(sources)
        val (item, _) = items.upsertDraft(sourceId, xDraft(externalId = "post-1"))
        val articleId = articleRepository.insert(
            title = "Imported",
            aiContent = "Imported body",
            url = "https://example.com/deleted-imported",
            isFavorite = 1,
            status = "completed",
        )
        items.markImported(item.id, articleId, duplicateLinked = false)

        articleRepository.delete(articleId)

        val pending = items.pendingImport(10).single()
        assertEquals(item.id, pending.id)
        assertNull(pending.article_id)
        assertEquals("imported", pending.import_status)
    }

    @Test
    fun pendingImportIncludesImportedItemsWithDanglingArticleId() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        val db = DailySatoriDatabase(driver)
        val sources = ExternalFavoriteSourceRepository(
            db = db,
            encryptSecret = { value -> if (value.isBlank()) value else "enc:v1:$value" },
            decryptSecret = { value -> value.removePrefix("enc:v1:") },
            isSecretEncrypted = { value -> value.startsWith("enc:v1:") },
        )
        val items = ExternalFavoriteItemRepository(db)
        val articleRepository = ArticleRepository(db)
        val sourceId = saveXSource(sources)
        val (item, _) = items.upsertDraft(sourceId, xDraft(externalId = "post-dangling"))
        val articleId = articleRepository.insert(
            title = "Imported",
            aiContent = "Imported body",
            url = "https://example.com/dangling-imported",
            isFavorite = 1,
            status = "completed",
        )
        items.markImported(item.id, articleId, duplicateLinked = false)
        articleRepository.delete(articleId)

        val pending = items.pendingImport(10).single()

        assertEquals(item.id, pending.id)
        assertEquals(articleId, pending.article_id)
        assertEquals("imported", pending.import_status)
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

    @Test
    fun articleRepositoryCanListImportedArticlesForOneExternalFavoriteSource() = withRepositories { db, sources, items ->
        val articleRepository = ArticleRepository(db)
        val firstSourceId = saveXSource(sources, accountId = "acct-1", displayName = "X One")
        val secondSourceId = saveXSource(sources, accountId = "acct-2", displayName = "X Two")
        val (firstItem, _) = items.upsertDraft(firstSourceId, xDraft(externalId = "post-1", title = "First"))
        val (secondItem, _) = items.upsertDraft(secondSourceId, xDraft(externalId = "post-2", title = "Second"))
        val firstArticleId = articleRepository.insert(
            title = "First",
            aiContent = "First body",
            url = "https://example.com/first-imported-source",
            isFavorite = 1,
            status = "completed",
        )
        val secondArticleId = articleRepository.insert(
            title = "Second",
            aiContent = "Second body",
            url = "https://example.com/second-imported-source",
            isFavorite = 1,
            status = "completed",
        )
        items.markImported(firstItem.id, firstArticleId, duplicateLinked = false)
        items.markImported(secondItem.id, secondArticleId, duplicateLinked = false)

        assertEquals(listOf(firstArticleId), articleRepository.getExternalFavoritesBySourceSync(firstSourceId).map { it.id })
        assertEquals(listOf(secondArticleId), articleRepository.getExternalFavoritesBySourceSync(secondSourceId).map { it.id })
        assertEquals(setOf(firstArticleId, secondArticleId), articleRepository.getExternalFavoritesSync().map { it.id }.toSet())
    }

    @Test
    fun articleRepositoryDeduplicatesExternalFavoriteArticlesLinkedToSameArticle() = withRepositories { db, sources, items ->
        val articleRepository = ArticleRepository(db)
        val sourceId = saveXSource(sources)
        val (firstItem, _) = items.upsertDraft(sourceId, xDraft(externalId = "post-1", title = "First"))
        val (secondItem, _) = items.upsertDraft(sourceId, xDraft(externalId = "post-2", title = "Second"))
        val articleId = articleRepository.insert(
            title = "Shared article",
            aiContent = "Shared body",
            url = "https://example.com/shared-imported-source",
            isFavorite = 1,
            status = "completed",
        )
        items.markImported(firstItem.id, articleId, duplicateLinked = false)
        items.markImported(secondItem.id, articleId, duplicateLinked = true)

        assertEquals(listOf(articleId), articleRepository.getExternalFavoritesBySourceSync(sourceId).map { it.id })
        assertEquals(listOf(articleId), articleRepository.getExternalFavoritesSync().map { it.id })
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
            isSecretEncrypted = { value -> value.startsWith("enc:v1:") },
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

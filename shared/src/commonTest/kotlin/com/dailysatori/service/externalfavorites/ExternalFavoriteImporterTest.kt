package com.dailysatori.service.externalfavorites

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.ExternalFavoriteItemRepository
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExternalFavoriteImporterTest {
    @Test
    fun importsXItemAsFavoriteArticleWithOriginalBlockAndSourceUrl() = withRepositories { _, sources, items, articles ->
        val sourceId = saveXSource(sources)
        items.upsertDraft(
            sourceId,
            xDraft(
                externalId = "post-1",
                canonicalUrl = "https://x.com/daily/status/100",
                title = "X 收藏",
                text = "这是一条值得保存的原文。",
                authorName = "@daily",
            ),
        )

        val imported = ExternalFavoriteImporter(items, articles).importPending()

        assertEquals(1, imported)
        val article = articles.getByUrl("https://x.com/daily/status/100")
        assertNotNull(article)
        assertEquals(1, article.is_favorite)
        assertEquals("completed", article.status)
        assertTrue(article.ai_markdown_content.orEmpty().contains("## 原文"))
        assertTrue(article.ai_markdown_content.orEmpty().contains("这是一条值得保存的原文。"))
        assertTrue(article.ai_markdown_content.orEmpty().contains("https://x.com/daily/status/100"))

        val item = items.getBySource(sourceId).single()
        assertEquals(article.id, item.article_id)
        assertEquals("imported", item.import_status)
    }

    @Test
    fun duplicateXItemsFromDifferentSourcesLinkToOneLocalArticleByCanonicalUrl() = withRepositories { _, sources, items, articles ->
        val firstSourceId = saveXSource(sources, accountId = "acct-1")
        val secondSourceId = saveXSource(sources, accountId = "acct-2")
        val canonicalUrl = "https://x.com/daily/status/200"
        items.upsertDraft(firstSourceId, xDraft(externalId = "post-a", canonicalUrl = canonicalUrl))
        items.upsertDraft(secondSourceId, xDraft(externalId = "post-b", canonicalUrl = canonicalUrl))

        val imported = ExternalFavoriteImporter(items, articles).importPending()

        assertEquals(2, imported)
        val article = articles.getByUrl(canonicalUrl)
        assertNotNull(article)
        assertEquals(1, articles.searchSync("Body for").size)

        val importedItems = items.getBySource(firstSourceId) + items.getBySource(secondSourceId)
        assertEquals(setOf(article.id), importedItems.map { it.article_id }.toSet())
        assertEquals(setOf("imported", "duplicate_linked"), importedItems.map { it.import_status }.toSet())
    }

    @Test
    fun importerDoesNotOverwriteUserCommentOrRicherExistingMarkdown() = withRepositories { _, sources, items, articles ->
        val canonicalUrl = "https://x.com/daily/status/300"
        val existingMarkdown = """
            # Existing Analysis

            This existing markdown contains a much longer user-reviewed analysis with many details,
            annotations, context, and notes that should not be replaced by deterministic import text.
        """.trimIndent()
        val articleId = articles.insert(
            title = "Existing title",
            aiContent = "Existing summary",
            aiMarkdownContent = existingMarkdown,
            url = canonicalUrl,
            isFavorite = 0,
            comment = "user comment",
            status = "completed",
        )
        val sourceId = saveXSource(sources)
        items.upsertDraft(
            sourceId,
            xDraft(
                externalId = "post-3",
                canonicalUrl = canonicalUrl,
                text = "Short import text.",
            ),
        )

        val imported = ExternalFavoriteImporter(items, articles).importPending()

        assertEquals(1, imported)
        val article = articles.getById(articleId)
        assertNotNull(article)
        assertEquals(1, article.is_favorite)
        assertEquals("user comment", article.comment)
        assertEquals(existingMarkdown, article.ai_markdown_content)
        assertEquals("completed", article.status)

        val item = items.getBySource(sourceId).single()
        assertEquals(articleId, item.article_id)
        assertEquals("duplicate_linked", item.import_status)
    }

    @Test
    fun itemWithoutCanonicalUrlIsMarkedImportFailed() = withRepositories { _, sources, items, articles ->
        val sourceId = saveXSource(sources)
        items.upsertDraft(sourceId, xDraft(externalId = "post-missing-url", canonicalUrl = null))

        val imported = ExternalFavoriteImporter(items, articles).importPending()

        assertEquals(0, imported)
        assertEquals(emptyList(), articles.getAllSync())
        val item = items.getBySource(sourceId).single()
        assertNull(item.article_id)
        assertEquals("failed", item.import_status)
        assertEquals("missing_url", item.last_error_code)
    }

    private fun withRepositories(
        block: (
            db: DailySatoriDatabase,
            sources: ExternalFavoriteSourceRepository,
            items: ExternalFavoriteItemRepository,
            articles: ArticleRepository,
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
        val articles = ArticleRepository(db)
        block(db, sources, items, articles)
    }

    private fun saveXSource(
        sources: ExternalFavoriteSourceRepository,
        accountId: String = "acct-1",
        accountName: String = "@daily",
    ): Long = sources.save(
        provider = ExternalFavoriteProvider.X.id,
        displayName = "X Favorites",
        accountId = accountId,
        accountName = accountName,
        authJson = """{"access_token":"secret"}""",
    )

    private fun xDraft(
        externalId: String,
        canonicalUrl: String? = "https://x.com/daily/status/$externalId",
        title: String = "X 收藏",
        text: String = "Body for $externalId",
        authorName: String = "Author",
        sourceCreatedAt: Long? = 1_700_000_000_000,
    ): ExternalFavoriteItemDraft = ExternalFavoriteItemDraft(
        provider = ExternalFavoriteProvider.X.id,
        externalId = externalId,
        canonicalUrl = canonicalUrl,
        title = title,
        text = text,
        authorName = authorName,
        sourceCreatedAt = sourceCreatedAt,
        favoritedAt = 1_700_000_100_000,
        normalizedJson = """{"id":"$externalId"}""",
        contentHash = "content-$externalId-$title-$text",
        aiInputHash = "ai-$externalId-$title-$text",
    )
}

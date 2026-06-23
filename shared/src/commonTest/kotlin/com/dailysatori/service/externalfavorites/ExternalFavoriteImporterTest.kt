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
    fun importsXItemAsArticleWithOriginalBlockAndSourceUrlWithoutLocalFavoriteFlag() = withRepositories { _, sources, items, articles ->
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
        assertEquals(0, article.is_favorite)
        assertEquals("completed", article.status)
        assertTrue(article.ai_markdown_content.orEmpty().contains("## 原文"))
        assertTrue(article.ai_markdown_content.orEmpty().contains("这是一条值得保存的原文。"))
        assertTrue(article.ai_markdown_content.orEmpty().contains("https://x.com/daily/status/100"))

        val item = items.getBySource(sourceId).single()
        assertEquals(article.id, item.article_id)
        assertEquals("imported", item.import_status)
    }

    @Test
    fun importingXItemLeavesArticleOutOfStandardArticleProcessing() = withRepositories { _, sources, items, articles ->
        val sourceId = saveXSource(sources)
        items.upsertDraft(sourceId, xDraft(externalId = "post-pending"))

        ExternalFavoriteImporter(items, articles).importPending()

        val article = articles.getByUrl("https://x.com/daily/status/post-pending")
        assertNotNull(article)
        assertEquals("completed", article.status)
        assertEquals(false, articles.getRecoverableForProcessingSync().map { it.id }.contains(article.id))
    }

    @Test
    fun importsXPostWithExternalUrlAsCompletedXPostWithoutWebProcessing() = withRepositories { _, sources, items, articles ->
        val sourceId = saveXSource(sources)
        val postUrl = "https://x.com/daily/status/post-article-card"
        items.upsertDraft(
            sourceId,
            xDraft(
                externalId = "post-article-card",
                canonicalUrl = postUrl,
                title = "Article Card",
                text = "转发壳里的文字",
                normalizedJson = """
                    {
                      "id": "post-article-card",
                      "canonical_tweet_url": "$postUrl",
                      "primary_url": "https://example.com/article-from-retweet-card"
                    }
                """.trimIndent(),
            ),
        )

        val imported = ExternalFavoriteImporter(items, articles).importPending()

        assertEquals(1, imported)
        val article = articles.getByUrl(postUrl)
        assertNotNull(article)
        assertEquals("completed", article.status)
        assertEquals(false, articles.getRecoverableForProcessingSync().map { it.id }.contains(article.id))
        assertTrue(article.ai_markdown_content.orEmpty().contains("转发壳里的文字"))
        assertEquals("pending", items.getBySource(sourceId).single().ai_status)
    }

    @Test
    fun importsXLongArticleUrlAsCompletedArticleWithoutWebProcessing() = withRepositories { _, sources, items, articles ->
        val sourceId = saveXSource(sources)
        val articleUrl = "https://x.com/i/article/2068336874515734528"
        val tweetUrl = "https://x.com/i/status/2068340624907202872"
        items.upsertDraft(
            sourceId,
            xDraft(
                externalId = "2068340624907202872",
                canonicalUrl = articleUrl,
                title = "X 长文章",
                text = "这是通过 X API 获取到的长文章正文，不应该再交给 WebView 打开网页。",
                normalizedJson = """
                    {
                      "id": "2068340624907202872",
                      "canonical_tweet_url": "$tweetUrl",
                      "primary_url": "$articleUrl"
                    }
                """.trimIndent(),
            ),
        )

        val imported = ExternalFavoriteImporter(items, articles).importPending()

        assertEquals(1, imported)
        assertNull(articles.getByUrl(articleUrl))
        val article = articles.getByUrl(tweetUrl)
        assertNotNull(article)
        assertEquals("completed", article.status)
        assertEquals(false, articles.getRecoverableForProcessingSync().map { it.id }.contains(article.id))
        assertTrue(article.ai_markdown_content.orEmpty().contains("这是通过 X API 获取到的长文章正文"))
        assertTrue(article.ai_markdown_content.orEmpty().contains(tweetUrl))
        assertTrue(article.ai_markdown_content.orEmpty().contains(articleUrl))
        assertEquals("pending", items.getBySource(sourceId).single().ai_status)
    }

    @Test
    fun importsXLongArticleMetadataInsteadOfOnlyShortLinkText() = withRepositories { _, sources, items, articles ->
        val sourceId = saveXSource(sources)
        val articleUrl = "https://x.com/i/article/2068336874515734528"
        items.upsertDraft(
            sourceId,
            xDraft(
                externalId = "2068340624907202872",
                canonicalUrl = articleUrl,
                title = "长文卡片标题",
                text = "https://t.co/iAedHNUNSa",
                normalizedJson = """
                    {
                      "id": "2068340624907202872",
                      "canonical_tweet_url": "https://x.com/i/status/2068340624907202872",
                      "primary_url": "$articleUrl",
                      "url_title": "真正的 X 长文章标题",
                      "url_description": "真正的 X 长文章摘要，应该作为可整理内容进入原文。"
                    }
                """.trimIndent(),
            ),
        )

        ExternalFavoriteImporter(items, articles).importPending()

        val article = articles.getByUrl("https://x.com/i/status/2068340624907202872")
        assertNotNull(article)
        assertEquals("completed", article.status)
        assertEquals("真正的 X 长文章标题\n\n真正的 X 长文章摘要，应该作为可整理内容进入原文。", article.ai_content)
        assertTrue(article.ai_markdown_content.orEmpty().contains("真正的 X 长文章标题"))
        assertTrue(article.ai_markdown_content.orEmpty().contains("真正的 X 长文章摘要"))
        assertEquals(false, article.ai_markdown_content.orEmpty().contains("\n\nhttps://t.co/iAedHNUNSa\n\n"))
    }

    @Test
    fun importsXLongArticleMetadataInsteadOfOnlyArticleLinkText() = withRepositories { _, sources, items, articles ->
        val sourceId = saveXSource(sources)
        val articleUrl = "https://x.com/i/article/2010742786430021632"
        items.upsertDraft(
            sourceId,
            xDraft(
                externalId = "2010742786430021632",
                canonicalUrl = articleUrl,
                title = "长文卡片标题",
                text = "链接：https://x.com/i/article/2010742786430021632",
                normalizedJson = """
                    {
                      "id": "2010742786430021632",
                      "canonical_tweet_url": "https://x.com/i/status/2010742786430021632",
                      "primary_url": "$articleUrl",
                      "url_title": "X 长文章真实标题",
                      "url_description": "X 长文章真实摘要，应该替代单独的链接行。"
                    }
                """.trimIndent(),
            ),
        )

        ExternalFavoriteImporter(items, articles).importPending()

        val article = articles.getByUrl("https://x.com/i/status/2010742786430021632")
        assertNotNull(article)
        assertEquals("X 长文章真实标题\n\nX 长文章真实摘要，应该替代单独的链接行。", article.ai_content)
        assertTrue(article.ai_markdown_content.orEmpty().contains("X 长文章真实标题"))
        assertTrue(article.ai_markdown_content.orEmpty().contains("X 长文章真实摘要"))
        assertEquals(false, article.ai_content.orEmpty().contains("链接：https://x.com/i/article/2010742786430021632"))
    }

    @Test
    fun repairsAlreadyImportedPendingXLongArticleFromExternalItemContent() = withRepositories { _, sources, items, articles ->
        val sourceId = saveXSource(sources)
        val articleUrl = "https://x.com/i/article/2068336874515734528"
        val (item, _) = items.upsertDraft(
            sourceId,
            xDraft(
                externalId = "2068340624907202872",
                canonicalUrl = articleUrl,
                title = "X 长文章",
                text = "这是已经同步到 external item 的正文，修复时应该直接写回文章。",
                normalizedJson = """
                    {
                      "id": "2068340624907202872",
                      "canonical_tweet_url": "https://x.com/i/status/2068340624907202872",
                      "primary_url": "$articleUrl"
                    }
                """.trimIndent(),
            ),
        )
        val articleId = articles.insert(
            title = "X 长文章",
            aiContent = "",
            aiMarkdownContent = "",
            url = articleUrl,
            isFavorite = 0,
            status = "pending",
        )
        items.markImported(item.id, articleId, duplicateLinked = false)

        val repaired = ExternalFavoriteImporter(items, articles).repairImportedXLongArticlePendingArticles(limit = 10)

        assertEquals(1, repaired)
        val article = articles.getById(articleId)
        assertNotNull(article)
        assertEquals("completed", article.status)
        assertEquals(false, articles.getRecoverableForProcessingSync().map { it.id }.contains(articleId))
        assertTrue(article.ai_markdown_content.orEmpty().contains("这是已经同步到 external item 的正文"))
        assertTrue(article.ai_markdown_content.orEmpty().contains("https://x.com/i/status/2068340624907202872"))
        assertEquals("pending", items.getBySource(sourceId).single().ai_status)
    }

    @Test
    fun importsXItemMediaAsArticleCoverImageUrl() = withRepositories { _, sources, items, articles ->
        val sourceId = saveXSource(sources)
        items.upsertDraft(
            sourceId,
            xDraft(
                externalId = "post-cover",
                canonicalUrl = "https://x.com/daily/status/cover",
                normalizedJson = """
                    {
                      "id": "post-cover",
                      "media": [
                        {
                          "type": "photo",
                          "url": "https://pbs.twimg.com/media/cover?format=jpg&name=large"
                        }
                      ]
                    }
                """.trimIndent(),
            ),
        )

        ExternalFavoriteImporter(items, articles).importPending()

        val article = articles.getByUrl("https://x.com/daily/status/cover")
        assertNotNull(article)
        assertEquals("https://pbs.twimg.com/media/cover?format=jpg&name=large", article.cover_image_url)
    }

    @Test
    fun repairsCoverImageUrlForAlreadyImportedXItems() = withRepositories { _, sources, items, articles ->
        val sourceId = saveXSource(sources)
        val (item, _) = items.upsertDraft(
            sourceId,
            xDraft(
                externalId = "post-existing-cover",
                normalizedJson = """
                    {
                      "id": "post-existing-cover",
                      "media": [
                        {
                          "type": "video",
                          "preview_image_url": "https://pbs.twimg.com/media/video-preview.jpg"
                        }
                      ]
                    }
                """.trimIndent(),
            ),
        )
        val articleId = articles.insert(
            title = "Existing X",
            aiContent = "Existing body",
            aiMarkdownContent = "# Existing",
            url = "https://x.com/daily/status/post-existing-cover",
            isFavorite = 1,
            status = "completed",
        )
        items.markImported(item.id, articleId, duplicateLinked = false)

        val repaired = ExternalFavoriteImporter(items, articles).repairImportedArticleCovers(limit = 10)

        assertEquals(1, repaired)
        assertEquals("https://pbs.twimg.com/media/video-preview.jpg", articles.getById(articleId)?.cover_image_url)
    }

    @Test
    fun repairsAlreadyImportedPlaceholderArticlesForExternalAiProcessing() = withRepositories { _, sources, items, articles ->
        val sourceId = saveXSource(sources)
        val (item, _) = items.upsertDraft(sourceId, xDraft(externalId = "post-placeholder"))
        val placeholderMarkdown = """
            # X 收藏

            ## 原文

            - 作者：Author
            - 时间：2023-11-14T22:13:20Z
            - 链接：https://x.com/daily/status/post-placeholder

            Body for post-placeholder

            ## AI 整理

            待整理
        """.trimIndent()
        val articleId = articles.insert(
            title = "X 收藏",
            aiContent = "Body for post-placeholder",
            aiMarkdownContent = placeholderMarkdown,
            url = "https://x.com/daily/status/post-placeholder",
            isFavorite = 1,
            status = "completed",
        )
        items.markImported(item.id, articleId, duplicateLinked = false)

        val repaired = ExternalFavoriteImporter(items, articles).repairImportedPlaceholderArticles(limit = 10)

        assertEquals(1, repaired)
        assertEquals("completed", articles.getById(articleId)?.status)
        assertEquals("pending", items.getBySource(sourceId).single().ai_status)
        assertEquals(false, articles.getRecoverableForProcessingSync().map { it.id }.contains(articleId))
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
        assertEquals(0, article.is_favorite)
        assertEquals("user comment", article.comment)
        assertEquals(existingMarkdown, article.ai_markdown_content)
        assertEquals("completed", article.status)

        val item = items.getBySource(sourceId).single()
        assertEquals(articleId, item.article_id)
        assertEquals("duplicate_linked", item.import_status)
    }

    @Test
    fun importerPreservesExistingUserArticleStatusCommentAndRicherMarkdown() = withRepositories { _, sources, items, articles ->
        val canonicalUrl = "https://x.com/daily/status/301"
        val existingMarkdown = """
            # Existing Pending Analysis

            This existing markdown has already been expanded with a longer user-reviewed note,
            so importing a shorter deterministic favorite should keep it in place.
        """.trimIndent()
        val articleId = articles.insert(
            title = "Existing pending title",
            aiContent = "Existing pending summary",
            aiMarkdownContent = existingMarkdown,
            url = canonicalUrl,
            isFavorite = 0,
            comment = "keep this user comment",
            status = "pending",
        )
        val sourceId = saveXSource(sources)
        items.upsertDraft(
            sourceId,
            xDraft(
                externalId = "post-301",
                canonicalUrl = canonicalUrl,
                text = "Short import text.",
            ),
        )

        val imported = ExternalFavoriteImporter(items, articles).importPending()

        assertEquals(1, imported)
        val article = articles.getById(articleId)
        assertNotNull(article)
        assertEquals(0, article.is_favorite)
        assertEquals("pending", article.status)
        assertEquals("keep this user comment", article.comment)
        assertEquals(existingMarkdown, article.ai_markdown_content)

        val item = items.getBySource(sourceId).single()
        assertEquals(articleId, item.article_id)
        assertEquals("duplicate_linked", item.import_status)
        assertEquals("not_needed", item.ai_status)
    }

    @Test
    fun importerDoesNotCompleteOrQueueAiForExistingUserArticle() = withRepositories { _, sources, items, articles ->
        val canonicalUrl = "https://x.com/daily/status/304"
        val existingMarkdown = "# User article\n\nStill waiting for normal processing."
        val articleId = articles.insert(
            title = "Existing user article",
            aiContent = "Existing user summary",
            aiMarkdownContent = existingMarkdown,
            url = canonicalUrl,
            isFavorite = 0,
            comment = "keep user article",
            status = "pending",
        )
        val sourceId = saveXSource(sources)
        items.upsertDraft(
            sourceId,
            xDraft(
                externalId = "post-304",
                canonicalUrl = canonicalUrl,
                text = "External favorite text should not overwrite user article.",
            ),
        )

        val imported = ExternalFavoriteImporter(items, articles).importPending()

        assertEquals(1, imported)
        val article = articles.getById(articleId)
        assertNotNull(article)
        assertEquals("pending", article.status)
        assertEquals("Existing user summary", article.ai_content)
        assertEquals(existingMarkdown, article.ai_markdown_content)
        assertTrue(articles.getRecoverableForProcessingSync().map { it.id }.contains(articleId))

        val item = items.getBySource(sourceId).single()
        assertEquals("duplicate_linked", item.import_status)
        assertEquals("not_needed", item.ai_status)
    }

    @Test
    fun importerDoesNotOverwriteShortUserAuthoredMarkdown() = withRepositories { _, sources, items, articles ->
        val canonicalUrl = "https://x.com/daily/status/302"
        val existingMarkdown = "# My note\n\nImportant."
        val articleId = articles.insert(
            title = "Existing title",
            aiContent = "Existing summary",
            aiMarkdownContent = existingMarkdown,
            url = canonicalUrl,
            isFavorite = 0,
            comment = "user comment",
            status = "pending",
        )
        val sourceId = saveXSource(sources)
        items.upsertDraft(
            sourceId,
            xDraft(
                externalId = "post-302",
                canonicalUrl = canonicalUrl,
                text = "Imported text that makes deterministic markdown longer than the note.",
            ),
        )

        val imported = ExternalFavoriteImporter(items, articles).importPending()

        assertEquals(1, imported)
        val article = articles.getById(articleId)
        assertNotNull(article)
        assertEquals("pending", article.status)
        assertEquals("user comment", article.comment)
        assertEquals(existingMarkdown, article.ai_markdown_content)

        val item = items.getBySource(sourceId).single()
        assertEquals(articleId, item.article_id)
        assertEquals("duplicate_linked", item.import_status)
        assertEquals("not_needed", item.ai_status)
    }

    @Test
    fun importerRefreshesOlderDeterministicExternalFavoriteMarkdown() = withRepositories { _, sources, items, articles ->
        val canonicalUrl = "https://x.com/daily/status/303"
        val existingMarkdown = """
            # X 收藏

            ## 原文

            - 作者：Old Author
            - 时间：2023-11-14T22:13:20Z
            - 链接：$canonicalUrl

            Older deterministic imported text that is intentionally much longer than the new item text.

            ## AI 整理

            待整理
        """.trimIndent()
        val articleId = articles.insert(
            title = "Existing placeholder",
            aiContent = "Existing summary",
            aiMarkdownContent = existingMarkdown,
            url = canonicalUrl,
            isFavorite = 1,
            comment = "user comment",
            status = "completed",
        )
        val sourceId = saveXSource(sources)
        items.upsertDraft(
            sourceId,
            xDraft(
                externalId = "post-303",
                canonicalUrl = canonicalUrl,
                text = "New text.",
                authorName = "New Author",
            ),
        )

        val imported = ExternalFavoriteImporter(items, articles).importPending()

        assertEquals(1, imported)
        val article = articles.getById(articleId)
        assertNotNull(article)
        assertTrue(article.ai_markdown_content.orEmpty().contains("New Author"))
        assertTrue(article.ai_markdown_content.orEmpty().contains("New text."))
        assertTrue(article.ai_markdown_content.orEmpty().contains("## 原文"))
        assertEquals("user comment", article.comment)

        val item = items.getBySource(sourceId).single()
        assertEquals(articleId, item.article_id)
        assertEquals("duplicate_linked", item.import_status)
    }

    @Test
    fun importerRefreshesLinkedExternalFavoriteArticleAfterXArticleApiAddsFullContent() = withRepositories { _, sources, items, articles ->
        val canonicalUrl = "https://x.com/i/article/2010742786430021632"
        val tweetUrl = "https://x.com/i/status/2010742786430021632"
        val articleId = articles.insert(
            title = "Old X article",
            aiContent = "旧卡片摘要",
            aiMarkdownContent = "# X 收藏\n\n## 原文\n\n旧卡片摘要\n\n## AI 整理\n\n已经整理过的旧内容",
            url = canonicalUrl,
            isFavorite = 0,
            comment = "keep comment",
            status = "completed",
        )
        val sourceId = saveXSource(sources)
        val (item, _) = items.upsertDraft(
            sourceId,
            xDraft(
                externalId = "post-x-article",
                canonicalUrl = canonicalUrl,
                title = "完整 X Article",
                text = "这是 X API 后来拿到的完整长文正文。",
                normalizedJson = """
                    {
                      "id": "post-x-article",
                      "canonical_tweet_url": "$tweetUrl",
                      "primary_url": "$canonicalUrl"
                    }
                """.trimIndent(),
            ),
        )
        items.markImported(item.id, articleId, duplicateLinked = false, aiStatus = ExternalItemAiStatus.not_needed)
        items.upsertDraft(
            sourceId,
            xDraft(
                externalId = "post-x-article",
                canonicalUrl = canonicalUrl,
                title = "完整 X Article",
                text = "这是 X API 后来拿到的完整长文正文。",
                normalizedJson = """
                    {
                      "id": "post-x-article",
                      "canonical_tweet_url": "$tweetUrl",
                      "primary_url": "$canonicalUrl",
                      "article_refreshed": true
                    }
                """.trimIndent(),
            ),
        )

        val imported = ExternalFavoriteImporter(items, articles).importPending()

        assertEquals(1, imported)
        val article = articles.getById(articleId)
        assertNotNull(article)
        assertEquals("keep comment", article.comment)
        assertEquals("这是 X API 后来拿到的完整长文正文。", article.ai_content)
        assertTrue(article.ai_markdown_content.orEmpty().contains("这是 X API 后来拿到的完整长文正文。"))
        assertEquals(1, articles.getAllSync().size)
        assertEquals(articleId, items.getBySource(sourceId).single().article_id)
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

    @Test
    fun itemWithoutCanonicalUrlIsNotRetriedUntilRemoteContentChanges() = withRepositories { _, sources, items, articles ->
        val sourceId = saveXSource(sources)
        items.upsertDraft(sourceId, xDraft(externalId = "post-missing-url", canonicalUrl = null))

        val first = ExternalFavoriteImporter(items, articles).importPending()
        val second = ExternalFavoriteImporter(items, articles).importPending()

        assertEquals(0, first)
        assertEquals(0, second)
        assertEquals("failed", items.getBySource(sourceId).single().import_status)
        assertEquals("missing_url", items.getBySource(sourceId).single().last_error_code)
        assertEquals(emptyList(), items.pendingImport(10))
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
        normalizedJson: String = """{"id":"$externalId"}""",
    ): ExternalFavoriteItemDraft = ExternalFavoriteItemDraft(
        provider = ExternalFavoriteProvider.X.id,
        externalId = externalId,
        canonicalUrl = canonicalUrl,
        title = title,
        text = text,
        authorName = authorName,
        sourceCreatedAt = sourceCreatedAt,
        favoritedAt = 1_700_000_100_000,
        normalizedJson = normalizedJson,
        contentHash = "content-$externalId-$title-$text",
        aiInputHash = "ai-$externalId-$title-$text",
    )
}

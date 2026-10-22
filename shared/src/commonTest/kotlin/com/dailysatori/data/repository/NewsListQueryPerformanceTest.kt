package com.dailysatori.data.repository

import app.cash.sqldelight.db.*
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.config.DatabaseConfig
import com.dailysatori.config.SettingKeys
import com.dailysatori.service.migration.DatabaseMigration
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.*

class NewsListQueryPerformanceTest {
    @Test fun initialManualAndFavoritePagesUseOrderedIndexes() = withDatabase { driver, db ->
        val articles = ArticleRepository(db)
        articles.insert(title = "manual", url = "https://example.com/manual", isFavorite = 1)
        articles.getCardsSync(limit = 31)
        assertIndexedArticleRead(driver.plan)
        assertFalse(driver.plan.any { it.contains("TEMP B-TREE") }, driver.plan.toString())
        articles.getCardsSync(favoritesOnly = true, limit = 31)
        assertIndexedArticleRead(driver.plan)
        assertFalse(driver.plan.any { it.contains("TEMP B-TREE") }, driver.plan.toString())
    }

    @Test fun externalSourcePageStartsFromSourceMappingsNotAllArticles() = withDatabase { driver, db ->
        seedSources(driver)
        ArticleRepository(db).getCardsSync(sourceId = 1, limit = 31)
        assertIndexedArticleRead(driver.plan)
        assertTrue(driver.plan.any { it.contains("source_id=?") }, driver.plan.toString())
    }

    @Test fun remotePageStartsFromSourceMappingsAndDeduplicatesBeforePagination() = withDatabase { driver, db ->
        seedSources(driver)
        val articles = ArticleRepository(db)
        val old = articles.insert(title = "old", url = "https://example.com/old", pubDate = 10)
        val newest = articles.insert(title = "new", url = "https://example.com/new", pubDate = 30)
        val middle = articles.insert(title = "middle", url = "https://example.com/middle", pubDate = 20)
        val sync = RemoteArticleSyncRepository(db)
        listOf(old, newest, newest, middle).forEachIndexed { i, id ->
            sync.upsertMapping(1, i.toLong(), id, null, "2026-10-22", 100)
        }
        assertEquals(listOf(newest, middle), sync.getArticlesBySource(1, 2, 0).map { it.id })
        assertIndexedArticleRead(driver.plan)
        assertTrue(driver.plan.any { it.contains("remote_source_id=?") }, driver.plan.toString())
        assertEquals(listOf(old), sync.getArticlesBySource(1, 2, 2).map { it.id })
    }

    @Test fun fastPagesKeepFavoriteOrderDeduplicationAndLightweightCards() = withDatabase { driver, db ->
        seedSources(driver)
        val articles = ArticleRepository(db)
        val one = articles.insert(title = "one", url = "https://example.com/one", aiContent = "s".repeat(2000), aiMarkdownContent = "m".repeat(2000))
        val two = articles.insert(title = "two", url = "https://example.com/two")
        driver.execute(null, "UPDATE article SET created_at=10, is_favorite=1", 0)
        listOf(Triple(one, 90, 1), Triple(one, 20, 2), Triple(two, 40, 3)).forEach { (id, time, external) ->
            driver.execute(null, "INSERT INTO external_favorite_item(source_id,provider,external_id,article_id,first_seen_at,last_seen_at,created_at,updated_at) VALUES(1,'x','$external',$id,$time,1,1,1)", 0)
        }
        assertEquals(listOf(one, two), articles.getCardsSync(sourceId = 1).map { it.id })
        assertEquals(listOf(one), articles.getCardsSync(sourceId = 1, limit = 1).map { it.id })
        assertEquals(listOf(two, one), articles.getCardsSync(favoritesOnly = true).map { it.id })
        val card = articles.getCardsSync(sourceId = 1, limit = 1).single()
        assertEquals(512, card.ai_content?.length)
        assertEquals(512, card.ai_markdown_content?.length)
        assertNull(card.original_markdown_content)
    }

    @Test fun version34UpgradeInstallsOrderedIndexesWithoutLosingDataAndIsRepeatable() = withDatabase { driver, db ->
        val articles = ArticleRepository(db)
        val id = articles.insert(title = "preserved", url = "https://example.com/preserved", aiMarkdownContent = "original body", isFavorite = 1)
        driver.execute(null, "DROP INDEX IF EXISTS idx_article_manual_cards", 0)
        driver.execute(null, "DROP INDEX IF EXISTS idx_article_favorite_cards", 0)
        driver.execute(null, "DROP INDEX IF EXISTS idx_unified_news_source_normalized_url", 0)
        val settings = SettingRepository(db)
        repeat(2) {
            settings.upsert(SettingKeys.schemaVersion, "34")
            DatabaseMigration(driver, settings).runMigrations()
            assertEquals("original body", articles.getById(id)?.ai_markdown_content)
            articles.getCardsSync(limit = 31)
            assertIndexedArticleRead(driver.plan)
            assertFalse(driver.plan.any { it.contains("TEMP B-TREE") }, driver.plan.toString())
            articles.getCardsSync(favoritesOnly = true, limit = 31)
            assertFalse(driver.plan.any { it.contains("TEMP B-TREE") }, driver.plan.toString())
            assertEquals(DatabaseConfig.currentSchemaVersion.toString(), settings.get(SettingKeys.schemaVersion))
        }
    }

    private fun assertIndexedArticleRead(plan: List<String>) {
        assertFalse(plan.any { Regex("SCAN (a|article)(?:$| )").containsMatchIn(it) && !it.contains("USING") },
            "List must not scan every article: $plan")
        assertTrue(plan.any { it.contains("USING") }, plan.toString())
    }

    private fun seedSources(driver: SqlDriver) {
        driver.execute(null, "INSERT INTO remote_news_source(id,name,base_url,api_token,created_at,updated_at) VALUES(1,'news','https://example.com','',1,1)", 0)
        driver.execute(null, "INSERT INTO external_favorite_source(id,provider,display_name,account_id,created_at,updated_at) VALUES(1,'x','X','test',1,1)", 0)
    }

    private fun withDatabase(block: (PlanDriver, DailySatoriDatabase) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { jdbc ->
            DailySatoriDatabase.Schema.create(jdbc)
            val driver = PlanDriver(jdbc)
            block(driver, DailySatoriDatabase(driver))
        }
    }

    private class PlanDriver(private val delegate: SqlDriver) : SqlDriver by delegate {
        var plan: List<String> = emptyList()
        override fun <R> executeQuery(identifier: Int?, sql: String, mapper: (SqlCursor) -> QueryResult<R>,
            parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<R> {
            plan = delegate.executeQuery(null, "EXPLAIN QUERY PLAN $sql", { cursor ->
                val details = mutableListOf<String>()
                while (cursor.next().value) details += requireNotNull(cursor.getString(3))
                QueryResult.Value(details)
            }, parameters, binders).value
            return delegate.executeQuery(identifier, sql, mapper, parameters, binders)
        }
    }
}

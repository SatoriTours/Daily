package com.dailysatori.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

class NewsArticleOwnershipTest {
    @Test fun manualListExcludesImportedSourcesButFavoritesAggregateWithoutMovingArticles() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val articles = ArticleRepository(db)
            val manual = articles.insert(title = "共同关键词 手动", url = "https://example.com/manual", isFavorite = 1)
            val imported = articles.insert(title = "共同关键词 导入", url = "https://example.com/imported", isFavorite = 1)
            val remote = articles.insert(title = "共同关键词 远程", url = "https://example.com/remote", isFavorite = 1)
            driver.execute(null, "UPDATE article SET source_type = 'remote_news' WHERE id = $remote", 0)
            driver.execute(null, "INSERT INTO external_favorite_source(id,provider,display_name,account_id,created_at,updated_at) VALUES(1,'x','X','test',1,1)", 0)
            driver.execute(null, "INSERT INTO external_favorite_item(source_id,provider,external_id,article_id,first_seen_at,last_seen_at,created_at,updated_at) VALUES(1,'x','one',$imported,1,1,1,1)", 0)

            val hot = articles.insert(title = "共同关键词 热榜", url = "https://example.com/hot")
            articles.markHotNewsOrigin(hot)
            assertEquals(listOf(manual), articles.getCardsSync().map { it.id })
            assertEquals(listOf(manual), articles.getCardsSync(searchQuery = "共同关键词").map { it.id })
            assertEquals(setOf(manual, imported, remote), articles.getCardsSync(favoritesOnly = true).map { it.id }.toSet())
            assertEquals(listOf(imported), articles.getCardsSync(sourceId = 1).map { it.id })
            articles.toggleFavorite(imported)
            assertEquals(listOf(imported), articles.getCardsSync(sourceId = 1).map { it.id })
            assertEquals(setOf(manual, remote), articles.getCardsSync(favoritesOnly = true).map { it.id }.toSet())
            driver.execute(null, "UPDATE article SET source_type = 'manual' WHERE id = $imported", 0)
            assertEquals(setOf(manual, imported), articles.getCardsSync().map { it.id }.toSet(),
                "用户明确再次添加的URL也属于手动来源，原来源映射仍保留")
        }
    }

    @Test fun legacySummaryDownloadsStayInTheirSourceButOlderManualSavesArePreserved() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val articles = ArticleRepository(DailySatoriDatabase(driver))
            val manual = articles.insert(title = "先手动保存", url = "https://example.com/manual")
            val hot = articles.insert(title = "旧热榜下载", url = "https://example.com/hot")
            val remote = articles.insert(title = "旧远程下载", url = "https://example.com/remote")
            driver.execute(null, "UPDATE article SET created_at = CASE WHEN id = $manual THEN 10 ELSE 30 END", 0)
            driver.execute(null, "INSERT INTO unified_news_summary(id,summary_date,window_key,window_start_ms,window_end_ms,title,content,status,created_at,updated_at) VALUES(1,'2026-10-19','daily',1,2,'新闻','新闻','success',20,20)", 0)
            listOf("manual" to "hot_news", "hot" to "hot_news", "remote" to "remote_article").forEachIndexed { index, (path, type) ->
                driver.execute(null, "INSERT INTO unified_news_source(summary_id,ref_key,source_type,source_url,title,summary) VALUES(1,'$index','$type','https://example.com/$path/#comments','新闻','摘要')", 0)
            }
            assertEquals(listOf(manual), articles.getCardsSync().map { it.id })
            assertEquals(hot, articles.recordManualIntake("https://example.com/hot", 40))
            assertEquals(listOf(hot, manual), articles.getCardsSync().map { it.id })
            assertEquals(3L, articles.count(), "兼容旧来源只改变展示，不删除文章")
        }
    }

    @Test fun manualCardsAreBoundedAndNewestAddedWinsOverPublicationDate() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val articles = ArticleRepository(DailySatoriDatabase(driver))
            repeat(75) { articles.insert(title = "旧文章 $it", url = "https://example.com/$it", pubDate = Long.MAX_VALUE) }
            val newest = articles.createPendingFromUrl("https://example.com/newest")
            val page = articles.getCardsSync(limit = 50)
            assertEquals(50, page.size)
            assertEquals(newest, page.first().id)
            assertEquals(76, articles.getCardsSync(limit = 100).size)
            val oldest = articles.getCardsSync().last().id
            val url = articles.getById(oldest)!!.url!!
            assertEquals(oldest, articles.recordManualIntake(url, Long.MAX_VALUE - 1))
            assertEquals(oldest, articles.getCardsSync(limit = 50).first().id)
            assertEquals(76, articles.getCardsSync().size, "重复添加只能更新添加顺序，不能重复建文章")
        }
    }
}

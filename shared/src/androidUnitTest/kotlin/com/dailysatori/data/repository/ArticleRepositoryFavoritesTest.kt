package com.dailysatori.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArticleRepositoryFavoritesTest {
    private fun database(block: suspend (ArticleRepository) -> Unit) = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            block(ArticleRepository(DailySatoriDatabase(driver)))
        } finally {
            driver.close()
        }
    }

    @Test
    fun favoritesPageInSqlWithoutLoadingEveryArticle() = database { articles ->
        repeat(25) { articles.insert(title = "收藏 $it", url = "https://example.com/fav-$it", isFavorite = 1) }
        repeat(30) { articles.insert(title = "普通 $it", url = "https://example.com/plain-$it") }

        assertEquals(25L, articles.countFavorites())
        assertEquals(55L, articles.count())

        val firstPage = articles.getFavoritesPaginated(20, 0).first()
        assertEquals(20, firstPage.size)
        assertTrue(firstPage.all { (it.is_favorite ?: 0L) > 0L }, "收藏分页不能混入非收藏文章")
        assertTrue(firstPage.zipWithNext().all { (a, b) -> a.created_at >= b.created_at })

        val secondPage = articles.getFavoritesPaginated(20, 20).first()
        assertEquals(5, secondPage.size)
        assertEquals(emptyList(), firstPage.map { it.id }.intersect(secondPage.map { it.id }.toSet()).toList())
    }

    @Test
    fun favoritesPaginationIsBoundedAndEmptyBeyondTheLastPage() = database { articles ->
        repeat(3) { articles.insert(title = "收藏 $it", url = "https://example.com/only-$it", isFavorite = 1) }

        assertEquals(3, articles.getFavoritesPaginated(20, 0).first().size)
        assertEquals(0, articles.getFavoritesPaginated(20, 20).first().size)
    }
}

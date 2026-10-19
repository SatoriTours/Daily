package com.dailysatori.ui.feature.article

import android.content.ContextWrapper
import androidx.lifecycle.viewModelScope
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.core.task.ArticlePostProcessingScheduler
import com.dailysatori.core.worker.ArticleProcessingScheduler
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.TagRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ArticlesViewModelFailureTest {
    @Test
    fun nullArticleRowDoesNotEscapeBackgroundLoadAndRetryRecovers() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        var viewModel: ArticlesViewModel? = null
        try {
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val articles = ArticleRepository(db)
            val id = articles.insert(title = "保留文章", url = "https://example.com/article")
            val context = unusedContext()
            val model = ArticlesViewModel(articles, TagRepository(db), ArticleProcessingScheduler(context),
                ArticlePostProcessingScheduler(context, AsyncTaskRepository(db)))
            viewModel = model
            withTimeout(3_000) { model.state.first { it.articles.isNotEmpty() } }
            // 真实执行生成的 SQL mapper，复现崩溃堆栈中的非空列读取异常。
            driver.execute(null, "ALTER TABLE article RENAME TO stored_article", 0)
            driver.execute(null, "CREATE VIEW article AS SELECT NULL AS id, stored_article.* FROM stored_article", 0)
            assertFailsWith<NullPointerException> { articles.getLocalSync() }
            model.loadArticles()
            val loadJob = ArticlesViewModel::class.java.getDeclaredField("loadJob").apply { isAccessible = true }
                .get(model) as Job
            withTimeout(3_000) { loadJob.join() }
            assertFalse(model.state.value.isLoading, "查询失败必须结束加载，而不是让后台异常退出进程")
            assertNotNull(model.state.value.loadError)
            assertEquals(id, model.state.value.articles.single().id, "读取失败不能清空已经显示的数据")

            driver.execute(null, "DROP VIEW article", 0)
            driver.execute(null, "ALTER TABLE stored_article RENAME TO article", 0)
            model.refreshArticles()
            val secondId = articles.insert(title = "新文章", url = "https://example.com/new")
            val recovered = withTimeout(3_000) { model.state.first { it.articles.size == 2 } }
            assertEquals(setOf(id, secondId), recovered.articles.map { it.id }.toSet())
            assertNull(recovered.loadError)
            val thirdId = articles.insert(title = "订阅恢复后的文章", url = "https://example.com/third")
            val updated = withTimeout(3_000) { model.state.first { it.articles.size == 3 } }
            assertEquals(setOf(id, secondId, thirdId), updated.articles.map { it.id }.toSet())
        } finally {
            viewModel?.viewModelScope?.coroutineContext?.get(Job)?.cancelAndJoin()
            driver.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun favoriteSearchNeverIncludesUnfavoritedArticlesAndFirstLoadIsBounded() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        var model: ArticlesViewModel? = null
        try {
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val articles = ArticleRepository(db)
            repeat(75) { articles.insert(title = "共同关键词 $it", url = "https://example.com/$it") }
            val favorite = articles.insert(title = "共同关键词 收藏", url = "https://example.com/favorite", isFavorite = 1)
            val context = unusedContext()
            val vm = ArticlesViewModel(articles, TagRepository(db), ArticleProcessingScheduler(context),
                ArticlePostProcessingScheduler(context, AsyncTaskRepository(db)))
            model = vm
            val initial = withTimeout(3_000) { vm.state.first { !it.isLoading } }
            kotlin.test.assertTrue(initial.articles.size <= 50, "首屏不能加载全部文章")
            kotlin.test.assertTrue(initial.hasMore)
            vm.loadMoreArticles()
            val all = withTimeout(3_000) { vm.state.first { it.articles.size == 76 } }
            assertFalse(all.hasMore)
            vm.setFavoritesOnly(true)
            withTimeout(3_000) { vm.state.first { !it.isLoading && it.articles.size == 1 } }
            vm.search("共同关键词")
            val searched = withTimeout(3_000) { vm.state.first { !it.isLoading && it.searchQuery == "共同关键词" } }
            assertEquals(listOf(favorite), searched.articles.map { it.id })
        } finally {
            model?.viewModelScope?.coroutineContext?.get(Job)?.cancelAndJoin()
            driver.close()
            Dispatchers.resetMain()
        }
    }

    // 调度器在本测试中不执行；绕开 android.jar 的 Context 构造桩。
    private fun unusedContext(): ContextWrapper {
        val field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = field.get(null)
        return unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, ContextWrapper::class.java) as ContextWrapper
    }
}

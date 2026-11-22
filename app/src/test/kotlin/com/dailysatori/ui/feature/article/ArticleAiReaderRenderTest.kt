package com.dailysatori.ui.feature.article

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.ArticleAiRepository
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.ui.theme.DailySatoriTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.sql.Driver
import java.sql.DriverManager
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = Application::class, qualifiers = "zh-rCN")
class ArticleAiReaderRenderTest {
    /** 规则统一管理 Activity、帧时钟、语义点击与滚动，无手工送帧与 sleep。 */
    @get:Rule
    val compose = createComposeRule()

    @Test fun summaryAndTranslationAreContinuousAndShortTranslationAppearsOnce() {
        // Robolectric 沙箱与普通用例用不同类加载器解析 JDBC 驱动，而 DriverManager 的驱动扫描只
        // 执行一次：先跑的一方决定后续能否连接 jdbc:sqlite。两种类加载器各注册一次，结果不再依赖
        // 用例执行顺序（不改变任何阅读页断言）。
        listOfNotNull(ClassLoader.getSystemClassLoader(), javaClass.classLoader).distinct().forEach { loader ->
            runCatching {
                val driverType = Class.forName("org.sqlite.JDBC", true, loader)
                DriverManager.registerDriver(driverType.getDeclaredConstructor().newInstance() as Driver)
            }
        }
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            val db = DailySatoriDatabase(driver)
            DailySatoriDatabase.Schema.create(driver)
            val settings = SettingRepository(db)
            val i18n = I18nService(settings).apply {
                init("zh")
                loadTranslation("zh", File("src/main/assets/i18n/zh.yaml").readText())
            }
            val original = (1..20).joinToString("\n\n") { "Original paragraph $it reports the complete library story." }
            val remote = RemoteArticle(1, TITLE, "https://example.org/story", content = original)
            val articles = ArticleRepository(db)
            val id = assertNotNull(articles.saveRemoteArticleForSync(remote)).article.id
            val article = assertNotNull(articles.getById(id))
            val repo = ArticleAiRepository(db, settings).apply { record(article, remote) }
            val hash = repo.input(article).hash
            val legacySummary = "**核心内容：** 缓存摘要样文\n\n**为什么重要（分析）：** 旧版分析。"
            val oneTranslation = "缓存译文样文\n\n末尾细节完整保留。"
            db.articleAiQueries.putArticleAiResult(hash, "summary", 1, "zh-Hans", legacySummary, 0)
            db.articleAiQueries.putArticleAiResult(hash, "translation", 1, "zh-Hans", "$oneTranslation\n\n$oneTranslation", 1)
            val cached = repo.view(article)
            // 全局 Koin 由本用例单一拥有：此处启动，finally 回收（失败路径同样不遗留）。
            startKoin { modules(module { single { i18n } }) }
            try {
                val readingView = mutableStateOf(cached.copy(shortBody = false))
                compose.setContent {
                    DailySatoriTheme {
                        Box(modifier = Modifier.size(width = 360.dp, height = 640.dp)) {
                            ArticleAiReader(readingView.value, TITLE, original)
                        }
                    }
                }
                compose.onNodeWithText("缓存摘要样文").assertExists()
                compose.onNodeWithText("旧版分析。", substring = true).assertDoesNotExist()
                compose.onNodeWithText("缓存译文样文").assertExists()
                compose.onNodeWithText("末尾细节完整保留。").assertExists()
                compose.onNodeWithText("仅处理文章前部", substring = true).assertExists()
                compose.onNodeWithText(TITLE).assertExists()
                compose.onNodeWithText("查看原文").assertDoesNotExist()
                compose.onNodeWithText("example.org", substring = true).assertDoesNotExist()

                compose.runOnIdle { readingView.value = cached.copy(shortBody = true, translationPartial = false) }
                compose.onNodeWithText("缓存摘要样文").assertDoesNotExist()
                assertEquals(1, compose.onAllNodesWithText("缓存译文样文").fetchSemanticsNodes().size)
                compose.runOnIdle { assertFalse(readingView.value.canRequestReading()) }

                compose.runOnIdle { readingView.value = cached.copy(summary = null, translation = null) }
                compose.onNodeWithText("Original paragraph 1 reports", substring = true).assertExists()
                compose.runOnIdle { assertTrue(readingView.value.canRequestReading()) }
            } finally {
                stopKoin()
            }
        }
    }

    private companion object {
        const val TITLE = "A long fixture title: the complete library news is available for reading"
    }
}

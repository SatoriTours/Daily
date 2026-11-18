package com.dailysatori.ui.feature.article

import android.app.Application
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.ArticleAiRepository
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.ui.theme.DailySatoriTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = Application::class, qualifiers = "zh-rCN")
class ArticleAiReaderRenderTest {
    @Test fun cachedModesAndOriginalTitleScrollOnTheRealReader() {
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
            db.articleAiQueries.putArticleAiResult(hash, "summary", 1, "zh-Hans", "缓存摘要样文", 0)
            db.articleAiQueries.putArticleAiResult(hash, "translation", 1, "zh-Hans", "缓存译文样文", 1)
            val cached = repo.view(article)
            Robolectric.buildActivity(ComponentActivity::class.java).use { controller ->
                val activity = controller.setup().visible().get()
                val compose = ComposeView(activity)
                compose.setContent {
                    KoinApplication(application = { modules(module { single { i18n } }) }) {
                        DailySatoriTheme {
                            val selection = rememberArticleReaderSelection(id, cached)
                            Column {
                                ArticleAiMenu(cached, selection, dismiss = {}, onForce = { error("No AI calls") })
                                ArticleAiReader(cached, selection, TITLE, emptyList(), "RSS", original,
                                    request = { error("No AI calls") })
                            }
                        }
                    }
                }
                activity.setContentView(compose)
                val reader = ReaderSemantics(compose)
                assertTrue(reader.has("缓存摘要样文"))
                reader.click("查看译文")
                assertTrue(reader.has("缓存译文样文"))
                assertTrue(reader.has("仅处理文章前部"))
                reader.click("查看原文", first = true)
                assertTrue(reader.has("Original paragraph 1 reports"))
                assertTrue(reader.has(TITLE))
                reader.scroll()
                assertFalse(reader.has(TITLE))
                reader.click("查看摘要")
                assertTrue(reader.has("缓存摘要样文"))
            }
        }
    }

    private class ReaderSemantics(private val view: ComposeView) {
        private fun nodes(): List<SemanticsNode> {
            val looper = shadowOf(Looper.getMainLooper())
            looper.idleFor(100, TimeUnit.MILLISECONDS)
            view.measure(exact(380), exact(840))
            view.layout(0, 0, 380, 840)
            looper.idleFor(100, TimeUnit.MILLISECONDS)
            val root = view.getChildAt(0) as ViewRootForTest
            root.measureAndLayoutForTest()
            fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
            return walk(root.semanticsOwner.unmergedRootSemanticsNode)
        }

        fun has(text: String) = nodes().any { it.text().contains(text) }

        fun click(text: String, first: Boolean = false) {
            val actions = nodes().filter { it.hasText(text) }.mapNotNull { it.config.getOrNull(SemanticsActions.OnClick) }
            val action = if (first) actions.first() else actions.single()
            assertTrue(action.action!!.invoke())
        }

        fun scroll() {
            val scroll = nodes().firstNotNullOf { it.config.getOrNull(SemanticsActions.ScrollBy)?.action }
            assertTrue(scroll.invoke(0f, 700f))
        }

        private fun SemanticsNode.hasText(text: String): Boolean =
            this.text() == text || children.any { it.hasText(text) }

        private fun SemanticsNode.text() =
            config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

        private fun exact(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
    }

    private companion object {
        const val TITLE = "A long fixture title: the complete library news is available for reading"
    }
}

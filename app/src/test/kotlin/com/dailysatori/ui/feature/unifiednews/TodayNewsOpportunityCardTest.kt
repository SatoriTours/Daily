package com.dailysatori.ui.feature.unifiednews

import android.app.Application
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.opportunity.NewsOpportunity
import com.dailysatori.service.opportunity.ReadNewsArticle
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.ui.theme.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = Application::class, qualifiers = "zh-rCN")
class TodayNewsOpportunityCardTest {
    private val driver = AndroidSqliteDriver(DailySatoriDatabase.Schema,
        org.robolectric.RuntimeEnvironment.getApplication(), null)
    private val controllers = mutableListOf<org.robolectric.android.controller.ActivityController<ComponentActivity>>()
    private val item = NewsOpportunity("id",
        ReadNewsArticle("key", "IP 检测只给分数，却不解释原因", "正文", source = "远程新闻", readAt = 0),
        "开发者 IP 诊断工具", "网络诊断",
        "开发者排查网络时，常遇到只报分数的 IP 检测站。扣分依据不透明，不同定位来源的结果也难以核对。",
        "开发者需要知道具体哪里出了问题。",
        "可做逐项解释扣分、支持多个定位来源并排对照的 IP 诊断工具。", "仍需验证需求", "引用", 0)

    @Before fun prepare() {
        val i18n = I18nService(SettingRepository(DailySatoriDatabase(driver))).apply {
            loadTranslation("zh", """
                idea_topic:
                  capture_title: 收为点子
                  capture_view_topic: 查看点子
            """.trimIndent())
        }
        startKoin { modules(module { single { i18n } }) }
    }

    @After fun close() {
        controllers.forEach { it.pause().stop().destroy() }
        stopKoin()
        driver.close()
    }

    @Test fun overviewUsesFullWidthAndActionsRemainIndependent() {
        var opened = 0
        var captured = 0
        val nodes = render(onOpen = { opened++ }, onTopicAction = { captured++ })
        val title = nodes.text(item.article.title)
        val overview = nodes.text(item.fact)
        assertEquals(title.boundsInRoot.left, overview.boundsInRoot.left)
        assertTrue(overview.boundsInRoot.top >= title.boundsInRoot.bottom)
        assertFalse(nodes.any { it.text() == "关注" || it.text() == "精简摘要待更新" })
        val action = nodes.single { it.config.getOrNull(SemanticsActions.OnClick) != null &&
            it.config.getOrNull(SemanticsActions.OnClick)?.label != "查看详情" }
        action.config[SemanticsActions.OnClick].action!!.invoke()
        assertEquals(1, captured)
        assertEquals(0, opened)
        val card = nodes.single { it.config.getOrNull(SemanticsActions.OnClick)?.label == "查看详情" }
        card.config[SemanticsActions.OnClick].action!!.invoke()
        assertEquals(1, opened)
        assertEquals(1, captured)
    }

    @Test fun longTitlesAndMetadataRemainReadableOnNarrowScreensInBothThemes() {
        for (dark in listOf(false, true)) {
            val long = item.copy(article = item.article.copy(title = item.article.title.repeat(3)),
                category = "开发者网络诊断与隐私安全工具")
            val nodes = render(long, width = 320, fontScale = 1.5f, dark = dark, captured = true)
            nodes.filter { it.text().isNotBlank() }.forEach(::assertTextFits)
            val action = nodes.text("查看点子")
            val meta = nodes.text("开发者网络诊断与隐私安全工具 · 远程新闻")
            assertTrue(meta.boundsInRoot.right <= action.boundsInRoot.left)
            assertTrue(action.boundsInRoot.top >= nodes.text(long.action).boundsInRoot.bottom)
        }
    }

    @Test fun absentAnalysisDoesNotLeaveEmptyLabels() {
        val nodes = render(item.copy(fact = "", relevance = "", action = ""))
        assertTrue(nodes.any { it.text() == item.article.title })
        assertFalse(nodes.any { it.text() == "机会" || it.text() == "关注" })
    }

    private fun render(
        value: NewsOpportunity = item,
        width: Int = 400,
        fontScale: Float = 1f,
        dark: Boolean = false,
        captured: Boolean = false,
        onOpen: () -> Unit = {},
        onTopicAction: () -> Unit = {},
    ): List<SemanticsNode> {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        controllers += controller
        val view = ComposeView(controller.get())
        view.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                DailySatoriTheme(darkTheme = dark) {
                    Surface {
                        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.l)) {
                            TodayNewsOpportunityCard(value, onOpen, captured, onTopicAction)
                        }
                    }
                }
            }
        }
        controller.get().setContentView(view)
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(3000, View.MeasureSpec.AT_MOST))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
        val root = view.getChildAt(0) as ViewRootForTest
        root.measureAndLayoutForTest()
        fun collect(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::collect)
        return collect(root.semanticsOwner.unmergedRootSemanticsNode)
    }

    private fun assertTextFits(node: SemanticsNode) {
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        assertTrue(results.isNotEmpty())
        // Compose retains the maximum paragraph constraint for wrap-content labels;
        // compare glyph extents, not multiParagraph.width, against the measured text.
        assertTrue(results.all { result ->
            val paragraph = result.multiParagraph
            paragraph.height <= result.size.height + 1f &&
                (0 until paragraph.lineCount).all { line ->
                    paragraph.getLineRight(line) - paragraph.getLineLeft(line) <= result.size.width + 1f
                }
        }, "Text must not be clipped: ${node.text()}")
    }

    private fun List<SemanticsNode>.text(value: String) = single { it.text() == value }
    private fun SemanticsNode.text() =
        config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
}

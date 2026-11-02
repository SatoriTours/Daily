package com.dailysatori.ui.feature.unifiednews

import android.app.Application
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.service.hotnews.*
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.ui.component.appbar.LocalAssistantLauncher
import com.dailysatori.ui.theme.AppTypography
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = Application::class, qualifiers = "zh-rCN")
class HotNewsLayoutTest {
    private val driver = AndroidSqliteDriver(DailySatoriDatabase.Schema, org.robolectric.RuntimeEnvironment.getApplication(), null)
    private val controllers = mutableListOf<org.robolectric.android.controller.ActivityController<ComponentActivity>>()

    @Before
    fun prepareTranslations() {
        val i18n = I18nService(SettingRepository(DailySatoriDatabase(driver))).apply {
            loadTranslation("zh", """
                news_sources:
                  subscription: RSS 订阅 · 按订阅顺序
                  feed_required: 请在设置 → 内容来源 → 核心平台中配置 RSSHub 订阅地址。
            """.trimIndent())
        }
        startKoin { modules(module { single { i18n } }) }
    }

    @After
    fun close() {
        controllers.forEach { it.pause().stop().destroy() }
        stopKoin()
        driver.close()
    }

    @Test
    fun emptyBoardKeepsStatusInsideBottomPaddingAndHidesMisleadingUpdateTime() {
        val layout = render {
            board(HotNewsBoard(HotNewsPlatform.X, fetchedAt = 1700000000000))
        }
        val empty = layout.text("暂未获取榜单")
        assertTrue(layout.bounds.bottom - empty.boundsInRoot.bottom >= 15f,
            "The final status line needs a full bottom inset, not the rounded card edge")
        assertFalse(layout.nodes.any { it.text().startsWith("更新于") },
            "An empty fetch must not look like a successfully updated board")
    }

    @Test
    fun configurationActionFlowsBelowLongDescriptionAtLargeFontSize() {
        val layout = render(width = 320, fontScale = 1.5f) {
            board(HotNewsBoard(HotNewsPlatform.XIAOHONGSHU, error = HotNewsError.FEED_NOT_CONFIGURED))
        }
        val description = layout.text("请在设置 → 内容来源 → 核心平台中配置 RSSHub 订阅地址。")
        val action = layout.text("配置来源")
        assertTrue(action.boundsInRoot.top >= description.boundsInRoot.bottom + 4f,
            "A long configuration message must not compete with the action on one row")
        assertTrue(layout.bounds.bottom - action.boundsInRoot.bottom >= 15f)
        assertTextFits(description)
    }

    @Test
    fun platformAndRankHaveARealGapInsteadOfAnEmbeddedSpace() {
        val layout = render {
            HotNewsDetailTopBar("Hacker News", 3, true, false, {}, {}, false, true, {})
        }
        val platform = layout.text("Hacker News")
        val rank = layout.nodes.single { it.text().contains("第 3 名") }
        assertTrue(rank.boundsInRoot.left - platform.boundsInRoot.right >= 7f,
            "Platform and rank need layout spacing rather than adjacent text glyphs")
    }

    @Test
    fun readingStatusWrapsWithoutSqueezingTheContentTabs() {
        val layout = render(width = 320, fontScale = 1.5f) {
            HotNewsDetailModeRow(false, {}, {}, HotNewsMetaStatus.READY,
                HotNewsContentLabel.AI_SUMMARY, 12, {})
        }
        val summary = layout.text("摘要")
        val body = layout.text("正文")
        val status = layout.nodes.single { it.text().contains("约 12 分钟") }
        assertTrue(summary.boundsInRoot.right <= body.boundsInRoot.left)
        assertTrue(status.boundsInRoot.top >= body.boundsInRoot.bottom,
            "On a narrow screen with large text, status should flow below the tabs")
        assertTextFits(summary)
        assertTextFits(body)
        assertTextFits(status)
    }

    @Test
    fun singleLineReadingStatusIsVerticallyAlignedWithTabs() {
        val layout = render {
            HotNewsDetailModeRow(false, {}, {}, HotNewsMetaStatus.READY,
                HotNewsContentLabel.AI_SUMMARY, 1, {})
        }
        val tab = layout.text("正文").boundsInRoot
        val status = layout.nodes.single { it.text().contains("约 1 分钟") }.boundsInRoot
        assertTrue(kotlin.math.abs(tab.center.y - status.center.y) <= 3f,
            "A single-line status should align with the tab labels rather than the container top")
    }

    @Test
    fun bottomActionsKeepTheirFullLabelsOnNarrowScreensWithLargeText() {
        val layout = render(width = 320, fontScale = 1.5f) {
            HotNewsDetailBottomBar("https://example.com/story", "https://example.com/discussion", {})
        }
        assertTextFits(layout.text("打开原文"), maxLines = 1)
        assertTextFits(layout.text("查看讨论"), maxLines = 1)
    }

    @Composable
    private fun board(board: HotNewsBoard) {
        HotNewsBoardCard(board, false, "", emptySet(), {}, {}, {}, {})
    }

    private fun render(width: Int = 400, fontScale: Float = 1f, content: @Composable () -> Unit): Snapshot {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        controllers += controller
        val view = ComposeView(controller.get())
        var bounds = Rect.Zero
        view.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale),
                LocalAssistantLauncher provides {}) {
                MaterialTheme(typography = AppTypography) {
                    Box(Modifier.fillMaxWidth().onGloballyPositioned { bounds = it.boundsInRoot() }) {
                        content()
                    }
                }
            }
        }
        controller.get().setContentView(view)
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
        val root = view.getChildAt(0) as ViewRootForTest
        root.measureAndLayoutForTest()
        fun collect(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::collect)
        val nodes = collect(root.semanticsOwner.unmergedRootSemanticsNode)
        return Snapshot(bounds, nodes)
    }

    private fun assertTextFits(node: SemanticsNode, maxLines: Int = Int.MAX_VALUE) {
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        assertTrue(results.isNotEmpty())
        assertTrue(node.boundsInRoot.width > 0 && node.boundsInRoot.height > 0)
        // The paragraph width is its maximum constraint, not the actual glyph width.
        assertTrue(results.all { result ->
            val paragraph = result.multiParagraph
            paragraph.lineCount <= maxLines && paragraph.height <= result.size.height + 1f &&
                (0 until paragraph.lineCount).all { line ->
                    paragraph.getLineRight(line) - paragraph.getLineLeft(line) <= result.size.width + 1f
                }
        }, "Text must remain readable: ${node.text()}")
    }

    private data class Snapshot(val bounds: Rect, val nodes: List<SemanticsNode>) {
        fun text(value: String): SemanticsNode = nodes.single { it.text() == value }
    }
}

private fun SemanticsNode.text(): String =
    config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

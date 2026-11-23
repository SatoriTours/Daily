package com.dailysatori.ui.feature.unifiednews

import android.app.Application
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.dailysatori.shared.db.Unified_news_source
import com.dailysatori.shared.db.Unified_news_summary
import com.dailysatori.ui.theme.DailySatoriTheme
import com.dailysatori.ui.theme.Spacing
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = Application::class, qualifiers = "zh-rCN")
class HeadlineStoryBlockRenderTest {
    private val controllers = mutableListOf<org.robolectric.android.controller.ActivityController<ComponentActivity>>()

    @After fun close() {
        controllers.forEach { it.pause().stop().destroy() }
    }

    @Test
    fun fullParagraphsAndAllSourceLinksRenderWithoutClamping() {
        val source = source(11, "H1", "VoiceStudio")
        val second = source(12, "R1", "内核项目")
        val story = DailyHeadlineStory(
            title = "多来源事件",
            description = "第一段完整正文。",
            source = source,
            paragraphs = listOf("第一段完整正文。", "第二段补充细节，不应被行数或字数静默截断。"),
            sources = listOf(source, second),
            citations = listOf("H1", "R1"),
        )
        val nodes = render(story)

        assertTrue(nodes.any { it.text() == "多来源事件" })
        assertTrue(nodes.any { it.text() == "第一段完整正文。" })
        assertTrue(nodes.any { it.text().contains("第二段补充细节") })
        assertTrue(nodes.any { it.text().contains("example.com") })
        assertTrue(nodes.any { it.text().contains("查看来源") })
    }

    @Test
    fun unmatchedCitationIsExplicitAndShowsNoSourceLink() {
        val story = DailyHeadlineStory(
            title = "未知引用事件",
            description = "正文仍可读。",
            source = null,
            paragraphs = listOf("正文仍可读。"),
            unmatchedCitations = listOf("R9"),
        )
        val nodes = render(story)

        assertTrue(nodes.any { it.text().contains("未匹配引用 R9") })
        assertTrue(nodes.none { it.text().contains("查看来源") })
    }

    @Test
    fun longCachedOverviewIsEllipsizedWithFullBriefingActionBeforeStories() {
        val overview = "多个项目今天发布新版本，主要变化涉及部署方式和团队协作。".repeat(20)
        var opened = false
        val nodes = renderDigest(overview, width = 320) { opened = true }
        val preview = nodes.single { it.text().startsWith("多个项目今天") }.text()
        assertTrue(preview.endsWith("…"))
        assertEquals(151, preview.length)
        assertTrue(overview.startsWith(preview.removeSuffix("…")))
        val actionText = nodes.single { it.text() == "阅读完整简报" }
        val storyTitle = nodes.single { it.text() == "第一篇新闻" }
        assertTrue(actionText.boundsInRoot.bottom < storyTitle.boundsInRoot.top,
            "Full briefing action ${actionText.boundsInRoot} should precede story ${storyTitle.boundsInRoot}")
        actionText.parent!!.config[SemanticsActions.OnClick].action!!.invoke()
        assertTrue(opened)
    }

    @Test
    fun shortOverviewIsUnclippedWithSpaceBeforeTheFirstStory() {
        val overview = "多项服务发布面向普通用户的更新。"
        val nodes = renderDigest(overview)
        val lead = nodes.single { it.text() == overview }
        val storyTitle = nodes.single { it.text() == "第一篇新闻" }
        assertTrue(storyTitle.boundsInRoot.top - lead.boundsInRoot.bottom > Spacing.m.value)
        assertTrue(nodes.single { it.text() == "阅读完整简报" }.boundsInRoot.top > storyTitle.boundsInRoot.bottom)
    }

    private fun renderDigest(overview: String, width: Int = 400, onBriefing: () -> Unit = {}) = render(width) {
        val entry = Unified_news_summary(7, "2026-10-17", "daily", 0L, 1L, "标题", overview,
            "success", null, null, 1L, 1L, 1L)
        val story = DailyHeadlineStory("第一篇新闻", null, null, paragraphs = listOf("新闻正文不改变。"))
        HeadlineDigestBody(entry, DailyHeadlineDigest(listOf(overview), listOf(story)),
            query = "", onBriefing = onBriefing, onSourceClick = {}, onCitationClick = {})
    }

    private fun source(id: Long, ref: String, title: String) = Unified_news_source(
        id, 7, ref, "remote_article", null, "remote_news_source:1",
        "https://example.com/$ref", title, "来源摘要", null,
    )

    private fun render(story: DailyHeadlineStory) = render { HeadlineStoryBlock(story, onSourceClick = {}) }

    private fun render(width: Int = 400, content: @Composable () -> Unit): List<SemanticsNode> {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        controllers += controller
        val view = ComposeView(controller.get())
        view.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                DailySatoriTheme {
                    Surface {
                        Column(Modifier.fillMaxWidth().padding(Spacing.m)) {
                            content()
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

    private fun SemanticsNode.text() =
        config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
}

package com.dailysatori.ui.component.news

import android.app.Application
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.dailysatori.ui.theme.DailySatoriTheme
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = Application::class, qualifiers = "zh-rCN")
class MagazineNewsCoverRenderTest {
    private val controllers = mutableListOf<org.robolectric.android.controller.ActivityController<ComponentActivity>>()
    private val summary = "支持离线配音，适合内容创作，并能在本地完成常用处理。"

    @After fun close() {
        controllers.forEach { it.pause().stop().destroy() }
    }

    @Test
    fun validCoverShrinksSummaryThenInvalidPathRestoresFullWidthAndCanRecover() {
        val noCoverWidth = render(harness(null)).text(summary).boundsInRoot.width

        val loaded = awaitCover(harness(true), loaded = true)
        val coverNode = loaded.single { it.testTag() == COVER_TAG }
        val withCoverWidth = loaded.text(summary).boundsInRoot.width
        assertTrue(abs(coverNode.boundsInRoot.width - 88f) <= 2f, "封面应保持约 88dp 宽")
        assertTrue(withCoverWidth < noCoverWidth - 20f, "有封面时摘要应让出右侧空间")

        val failed = awaitCover(harness(false), loaded = false)
        assertEquals(noCoverWidth, failed.text(summary).boundsInRoot.width, 1f, "加载失败后摘要应恢复铺满")
        assertFalse(failed.any { it.testTag() == COVER_TAG }, "失败后不应留下占位块")

        val recovered = awaitCover(harness(true), loaded = true)
        assertTrue(recovered.text(summary).boundsInRoot.width <= withCoverWidth + 2f, "更换 URL 回有效图片后封面应恢复")
    }

    @Test
    fun invalidCoverPathNeverLeavesPlaceholderOrImageSlot() {
        val nodes = awaitCover(harness(false), loaded = false)
        val noCoverWidth = render(harness(null)).text(summary).boundsInRoot.width

        assertEquals(noCoverWidth, nodes.text(summary).boundsInRoot.width, 1f)
        assertFalse(nodes.any { it.testTag() == COVER_TAG })
        assertFalse(nodes.any { it.text().contains("封面") || it.text().contains("图片") })
    }

    private class Harness(
        val view: ComposeView,
        val coverStates: BlockingQueue<Boolean>,
    )

    private fun harness(initial: Boolean?): Harness {
        val state = mutableStateOf(initial)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        controllers += controller
        val coverStates = LinkedBlockingQueue<Boolean>()
        val view = ComposeView(controller.get())
        view.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                DailySatoriTheme {
                    Surface {
                        MagazineNewsCardCore(
                            title = "长标题新闻卡片",
                            summary = summary,
                            meta = "example.com · 1周前",
                            coverUrl = if (state.value == false) "/tmp/daily-missing-cover.png" else null,
                            coverModel = if (state.value == true) Unit else null,
                            imageLoader = null,
                            onCoverState = { coverStates.offer(it) },
                            coverRenderer = { onSuccess, onError ->
                                SideEffect { if (state.value == true) onSuccess() else onError() }
                                Box(Modifier.fillMaxSize())
                            },
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        controller.get().setContentView(view)
        return Harness(view, coverStates)
    }

    private fun render(harness: Harness): List<SemanticsNode> {
        harness.view.invalidate()
        shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
        harness.view.requestLayout()
        harness.view.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(3000, View.MeasureSpec.AT_MOST),
        )
        harness.view.layout(0, 0, harness.view.measuredWidth, harness.view.measuredHeight)
        shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
        val root = harness.view.getChildAt(0) as ViewRootForTest
        root.measureAndLayoutForTest()
        harness.view.invalidate()
        shadowOf(Looper.getMainLooper()).idle()
        fun collect(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::collect)
        return collect(root.semanticsOwner.unmergedRootSemanticsNode)
    }

    private fun awaitCover(harness: Harness, loaded: Boolean): List<SemanticsNode> {
        val deadline = System.currentTimeMillis() + 5_000
        var observed: Boolean? = null
        while (System.currentTimeMillis() < deadline) {
            val nodes = render(harness)
            harness.coverStates.poll()?.let { observed = it }
            if (observed == loaded && nodes.any { it.testTag() == COVER_TAG } == loaded) return nodes
            Thread.sleep(30)
        }
        error(if (loaded) "封面未在超时内加载成功" else "无效封面未在超时内回退")
    }

    private fun SemanticsNode.text() =
        config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun SemanticsNode.testTag() = config.getOrNull(SemanticsProperties.TestTag).orEmpty()

    private fun List<SemanticsNode>.text(value: String) = single { it.text() == value }

    companion object {
        private const val COVER_TAG = "magazine-news-cover"
    }
}

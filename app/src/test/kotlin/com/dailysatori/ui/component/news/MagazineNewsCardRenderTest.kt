package com.dailysatori.ui.component.news

import android.app.Application
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
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
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = Application::class, qualifiers = "zh-rCN")
class MagazineNewsCardRenderTest {
    private val controllers = mutableListOf<org.robolectric.android.controller.ActivityController<ComponentActivity>>()

    @After fun close() {
        controllers.forEach { it.pause().stop().destroy() }
    }

    @Test
    fun heightFollowsContentAndTheWholeCardStaysClickable() {
        var clicks = 0
        val withSummary = render("支持离线配音，适合内容创作。", onClick = { clicks++ })
        val titleOnly = render(null)

        assertTrue(withSummary.height > titleOnly.height, "摘要行应增加卡片高度，而不是固定旧高度")
        assertTrue(withSummary.nodes.any { it.text().contains("长标题") })
        assertTrue(withSummary.nodes.any { it.text().contains("支持离线配音") })
        assertTrue(withSummary.nodes.any { it.text().contains("example.com") })
        val clickable = withSummary.nodes.single { it.config.getOrNull(SemanticsActions.OnClick) != null }
        clickable.config[SemanticsActions.OnClick].action!!.invoke()
        assertEquals(1, clicks)
    }

    @Test
    fun noCoverStillRendersFullWidthTextWithoutPlaceholderContent() {
        val rendered = render("支持离线配音，适合内容创作。", hasCover = false)

        assertTrue(rendered.nodes.none { it.text().contains("暂时没有封面") })
        assertTrue(rendered.nodes.any { it.text().contains("长标题") })
    }

    private data class Rendered(val nodes: List<SemanticsNode>, val height: Int)

    private fun render(summary: String?, hasCover: Boolean = false, onClick: () -> Unit = {}): Rendered {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        controllers += controller
        val view = ComposeView(controller.get())
        view.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                DailySatoriTheme {
                    Surface {
                        MagazineNewsCard(
                            title = "这是一个需要换行的长标题：新闻卡片标题应当完整占据整行并自然换行",
                            summary = summary,
                            meta = "example.com · 1周前",
                            coverUrl = if (hasCover) "/tmp/not-a-real-cover.png" else null,
                            onClick = onClick,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        controller.get().setContentView(view)
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(3000, View.MeasureSpec.AT_MOST),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
        val root = view.getChildAt(0) as ViewRootForTest
        root.measureAndLayoutForTest()
        fun collect(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::collect)
        return Rendered(collect(root.semanticsOwner.unmergedRootSemanticsNode), view.measuredHeight)
    }

    private fun SemanticsNode.text() =
        config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
}

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
import androidx.compose.ui.unit.Density
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.opportunity.NewsOpportunity
import com.dailysatori.service.opportunity.OpportunityPreview
import com.dailysatori.service.opportunity.ReadNewsArticle
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.ui.theme.DailySatoriTheme
import com.dailysatori.ui.theme.Spacing
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = Application::class, qualifiers = "zh-rCN")
class OpportunityReadingItemRenderTest {
    private val driver = AndroidSqliteDriver(DailySatoriDatabase.Schema,
        org.robolectric.RuntimeEnvironment.getApplication(), null)
    private val controllers = mutableListOf<org.robolectric.android.controller.ActivityController<ComponentActivity>>()

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

    @Test
    fun judgmentAndEvidenceRenderInSeparateSectionsWithoutValidationSteps() {
        var captured = 0
        val item = opportunity()
        val nodes = render(item, onTopicAction = { captured++ })

        assertTrue(nodes.any { it.text() == "机会点是什么" })
        assertTrue(nodes.any { it.text().contains("独立开发者：需要在设备之间临时传文字。") })
        assertTrue(nodes.any { it.text().contains("无需安装的临时中转") })
        assertTrue(nodes.any { it.text() == "为什么值得关注" })
        assertTrue(nodes.any { it.text().contains("推断：现有步骤多，轻量中转可减少操作。") })
        assertTrue(nodes.any { it.text() == "新闻里的信号" })
        assertTrue(nodes.any { it.text() == item.fact })
        assertFalse(nodes.any { it.text().contains(item.action) }, "先验证/最小版本不能出现在阅读面")
        assertFalse(nodes.any { it.text().contains(item.caveat) }, "验证提示不能混入阅读面")
        assertTrue(nodes.any { it.text() == "收为点子" })
        val capture = nodes.single { it.text() == "收为点子" }
        assertTrue(capture.boundsInRoot.width > 0)
        val captureButton = generateSequence(capture) { it.parent }
            .first { it.config.getOrNull(SemanticsActions.OnClick) != null }
        captureButton.config[SemanticsActions.OnClick].action!!.invoke()
        assertEquals(1, captured)
    }

    @Test
    fun opportunityItemSupportsDirectDetailOpenAndIgnoreAction() {
        var opened = 0
        var ignored = 0
        val item = opportunity()
        val nodes = render(item, onOpen = { opened++ }, onIgnore = { ignored++ })

        val titleNode = nodes.single { it.text() == item.title }
        val openRegion = generateSequence(titleNode) { it.parent }
            .first { it.config.getOrNull(SemanticsActions.OnClick) != null }
        openRegion.config[SemanticsActions.OnClick].action!!.invoke()
        assertEquals(1, opened)

        val ignoreNode = nodes.single { it.text() == "与我无关" }
        val ignoreButton = generateSequence(ignoreNode) { it.parent }
            .first { it.config.getOrNull(SemanticsActions.OnClick) != null }
        ignoreButton.config[SemanticsActions.OnClick].action!!.invoke()
        assertEquals(1, ignored)
    }

    @Test
    fun oldItemWithoutJudgmentHidesWhyInsteadOfShowingFact() {
        val item = opportunity().copy(
            relevance = "独立开发者：需要在设备之间临时传文字。",
            preview = null,
        )
        val nodes = render(item)

        assertFalse(nodes.any { it.text() == "为什么值得关注" })
        assertTrue(nodes.any { it.text() == item.fact })
    }

    private fun opportunity() = NewsOpportunity(
        id = "1",
        article = ReadNewsArticle("key", "跨设备文本中转调查", "正文", source = "远程新闻", readAt = 1),
        title = "跨设备文本中转工具",
        category = "效率工具",
        fact = "Text Dropbox 通过浏览器同步纯文本。",
        relevance = "独立开发者：需要在设备之间临时传文字。\n与关注方向相关；推断：现有步骤多，轻量中转可减少操作。",
        action = "先验证是否存在重复使用需求。",
        caveat = "还需验证付费意愿。",
        quote = "Through the browser, plain text is synced.",
        createdAt = 1,
        preview = OpportunityPreview("临时文本中转", "值得尝试", "无需安装的临时中转"),
    )

    private fun render(
        item: NewsOpportunity,
        onOpen: () -> Unit = {},
        onTopicAction: () -> Unit = {},
        onIgnore: (() -> Unit)? = null,
    ): List<SemanticsNode> {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        controllers += controller
        val view = ComposeView(controller.get())
        view.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                DailySatoriTheme {
                    Surface {
                        Column(Modifier.fillMaxWidth().padding(Spacing.m)) {
                            OpportunityReadingItem(
                                item = item,
                                isCaptured = false,
                                onOpen = onOpen,
                                onTopicAction = onTopicAction,
                                onIgnore = onIgnore,
                            )
                        }
                    }
                }
            }
        }
        controller.get().setContentView(view)
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
        view.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
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

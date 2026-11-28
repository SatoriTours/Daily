package com.dailysatori.ui.feature.unifiednews

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dailysatori.shared.db.Unified_news_source
import com.dailysatori.shared.db.Unified_news_summary
import com.dailysatori.ui.theme.DailySatoriTheme
import com.dailysatori.ui.theme.Spacing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = Application::class, qualifiers = "zh-rCN-w360dp-h800dp-xhdpi")
class HeadlineStoryBlockRenderTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun singleSourceStoryTapsWholeRowWithoutStandaloneSourceFooter() {
        val source = source(11, "H1", "VoiceStudio")
        val story = DailyHeadlineStory(
            title = "单来源事件",
            description = "第一段完整正文。",
            source = source,
            paragraphs = listOf("第一段完整正文。", "第二段补充细节，不应被行数或字数静默截断。"),
            sources = listOf(source),
            citations = listOf("H1"),
        )
        var clicked: Unified_news_source? = null
        setThemedContent { HeadlineStoryBlock(story, onSourceClick = { clicked = it }, index = 1) }

        compose.onNodeWithText("单来源事件").assertExists()
        compose.onNodeWithText("第一段完整正文。").assertExists()
        compose.onNodeWithText("第二段补充细节", substring = true).assertExists()
        compose.onNodeWithText("example.com", substring = true).assertDoesNotExist()
        compose.onNodeWithText("查看来源", substring = true).assertDoesNotExist()

        val rowNode = compose.onNodeWithText("第二段补充细节", substring = true)
        assertEquals("查看来源", rowNode.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label)
        rowNode.performClick()
        assertEquals(source, clicked)
    }

    @Test
    fun multiSourceStoryOpensSourceMenuAndRoutesEachChoice() {
        val first = source(11, "H1", "VoiceStudio")
        val second = source(12, "R1", "内核项目")
        val story = DailyHeadlineStory(
            title = "多来源事件",
            description = "第一段完整正文。",
            source = first,
            paragraphs = listOf("第一段完整正文。"),
            sources = listOf(first, second),
            citations = listOf("H1", "R1"),
        )
        val clicked = mutableListOf<Unified_news_source>()
        setThemedContent { HeadlineStoryBlock(story, onSourceClick = { clicked += it }, index = 1) }

        val storyNode = compose.onNodeWithText("多来源事件")
        assertEquals("选择来源", storyNode.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label)
        storyNode.performClick()
        compose.onNodeWithText("内核项目").performClick()

        storyNode.performClick()
        compose.onNodeWithText("VoiceStudio").performClick()

        assertEquals(listOf(second, first), clicked)
    }

    @Test
    fun sourcelessStoryHasNoFakeClickOrSourceFooter() {
        val story = DailyHeadlineStory(
            title = "未知引用事件",
            description = "正文仍可读。",
            source = null,
            paragraphs = listOf("正文仍可读。"),
            unmatchedCitations = listOf("R9"),
        )
        setThemedContent { HeadlineStoryBlock(story, onSourceClick = {}, index = 1) }

        compose.onNodeWithText("未知引用事件").assertExists().assertHasNoClickAction()
        compose.onNodeWithText("正文仍可读。").assertExists().assertHasNoClickAction()
        compose.onNodeWithText("查看来源", substring = true).assertDoesNotExist()
        compose.onNodeWithText("未匹配引用", substring = true).assertDoesNotExist()
    }

    @Test
    fun overviewBlockIsDirectlyClickableWithoutCardActionOrTruncation() {
        val lead = "开源与代理工具成为今日热点主线。"
        val body = "第一组项目开放了表格和文档编辑核心，协议层兼容常见推送方式。".repeat(4)
        val overview = lead + body
        var openCount = 0
        setThemedContent {
            val entry = Unified_news_summary(
                7, "2026-10-17", "daily", 0L, 1L, "标题", overview,
                "success", null, null, 1L, 1L, 1L,
            )
            val story = DailyHeadlineStory("第一篇新闻", null, null, paragraphs = listOf("新闻正文不改变。"))
            HeadlineDigestBody(
                entry, DailyHeadlineDigest(listOf(overview), listOf(story)),
                query = "", onBriefing = { openCount++ }, onSourceClick = {}, onCitationClick = {},
            )
        }

        compose.onNodeWithText(lead, substring = true).assertExists()
        compose.onNodeWithText("…", substring = true).assertDoesNotExist()
        compose.onNodeWithText("今日总览").assertDoesNotExist()
        compose.onNodeWithText("阅读完整简报").assertDoesNotExist()
        compose.onNodeWithText("更新于", substring = true).assertDoesNotExist()

        val overviewNode = compose.onNodeWithText(lead, substring = true)
        assertEquals("阅读完整简报", overviewNode.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label)
        val storyBounds = compose.onNodeWithText("第一篇新闻").fetchSemanticsNode().boundsInRoot
        assertTrue(overviewNode.fetchSemanticsNode().boundsInRoot.bottom <= storyBounds.top)

        overviewNode.performClick()
        assertEquals(1, openCount)
    }

    @Test
    fun queryModeFallsBackToBorderlessClickableDateHeader() {
        var opened = false
        setThemedContent {
            val entry = Unified_news_summary(
                7, "2026-10-17", "daily", 0L, 1L, "标题", "## 今日要点\n- 第一篇新闻：正文。[R1]",
                "success", null, null, 1L, 1L, 1L,
            )
            val story = DailyHeadlineStory("第一篇新闻", null, null, paragraphs = listOf("正文。"))
            HeadlineDigestBody(
                entry, DailyHeadlineDigest(emptyList(), listOf(story)),
                query = "新闻", onBriefing = { opened = true }, onSourceClick = {}, onCitationClick = {},
            )
        }

        val fallback = compose.onNodeWithText("2026-10-17")
        compose.onNodeWithText("阅读完整简报").assertDoesNotExist()
        assertEquals("阅读完整简报", fallback.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label)
        fallback.performClick()
        assertTrue(opened)
    }

    @Test
    fun emptyOverviewWithoutQueryFallsBackToBorderlessClickableDateHeader() {
        var opened = false
        setThemedContent {
            val entry = Unified_news_summary(
                7, "2026-10-17", "daily", 0L, 1L, "标题", "## 今日要点\n- 第一篇新闻：正文。[R1]",
                "success", null, null, 1L, 1L, 1L,
            )
            val story = DailyHeadlineStory("第一篇新闻", null, null, paragraphs = listOf("正文。"))
            HeadlineDigestBody(
                entry, DailyHeadlineDigest(emptyList(), listOf(story)),
                query = "", onBriefing = { opened = true }, onSourceClick = {}, onCitationClick = {},
            )
        }

        val fallback = compose.onNodeWithText("2026-10-17")
        fallback.performClick()
        assertTrue(opened)
    }

    @Test
    fun remainingSectionKeepsInlineCitationClickInteraction() {
        var clickedCitation: String? = null
        setThemedContent {
            val entry = Unified_news_summary(
                7, "2026-10-17", "daily", 0L, 1L, "标题", "## 今日要闻\n主线一句话。\n\n## 重要变化\n- 端侧趋势 [R1]",
                "success", null, null, 1L, 1L, 1L,
            )
            val digest = DailyHeadlineDigest(
                overview = listOf("主线一句话。"),
                stories = listOf(DailyHeadlineStory("第一篇新闻", null, null, paragraphs = listOf("新闻正文。"))),
                remaining = "## 重要变化\n- 端侧趋势 [R1]",
            )
            HeadlineDigestBody(
                entry, digest, query = "",
                onBriefing = {}, onSourceClick = {}, onCitationClick = { clickedCitation = it },
            )
        }

        compose.onNodeWithText("端侧趋势", substring = true).performClick()
        assertEquals("R1", clickedCitation)
    }

    private fun source(id: Long, ref: String, title: String) = Unified_news_source(
        id, 7, ref, "remote_article", null, "remote_news_source:1",
        "https://example.com/$ref", title, "来源摘要", null,
    )

    private fun setThemedContent(content: @Composable () -> Unit) {
        compose.setContent {
            DailySatoriTheme {
                Surface {
                    Column(Modifier.fillMaxWidth().padding(Spacing.m)) {
                        content()
                    }
                }
            }
        }
    }
}

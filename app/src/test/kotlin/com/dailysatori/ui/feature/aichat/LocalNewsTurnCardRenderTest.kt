package com.dailysatori.ui.feature.aichat

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.dailysatori.service.localnews.LocalNewsBriefing
import com.dailysatori.service.localnews.LocalNewsBriefingState
import com.dailysatori.service.localnews.LocalNewsIntent
import com.dailysatori.service.localnews.LocalNewsSourceView
import com.dailysatori.ui.theme.DailySatoriTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = Application::class, qualifiers = "zh-rCN-w360dp-h800dp-xhdpi")
class LocalNewsTurnCardRenderTest {
    @get:Rule
    val compose = createComposeRule()

    private val briefing = LocalNewsBriefing(
        intent = LocalNewsIntent.Today,
        state = LocalNewsBriefingState.Ready,
        summaryDate = "2026-11-28",
        window = "1330",
        title = "长标题检验：多条要闻在同一窗口内合并呈现时标题不会被截断成无意义片段",
        content = "## 今日要点\n\n- 第一条要点内容。\n- 第二条要点内容。",
        sources = listOf(LocalNewsSourceView("榜单标题", "hot_news", "知乎")),
        isToday = true,
    )

    @Test
    fun aReadyBriefingShowsItsRealTitleContentAndSourcesOnDevice() {
        compose.setContent {
            DailySatoriTheme(darkTheme = false) {
                Surface {
                    LocalNewsTurnCard(LocalNewsTurn("今天有什么重点的新闻吗？", isLoading = false, briefing = briefing))
                }
            }
        }

        compose.onNodeWithText("本机阅读 · 未发送云端").assertExists()
        compose.onNodeWithText("今天有什么重点的新闻吗？").assertExists()
        compose.onNodeWithText("2026-11-28 13:30").assertExists()
        compose.onNodeWithText("长标题检验：多条要闻在同一窗口内合并呈现时标题不会被截断成无意义片段").assertExists()
        compose.onNodeWithText("知乎 · 榜单标题").assertExists()
        compose.onNodeWithText("本机阅读，不写入对话记录，也不会随下一次提问发送。").assertExists()
    }

    @Test
    fun everyNonReadyStateExplainsItselfWithoutFakeNews() {
        var state by mutableStateOf(LocalNewsTurn("最近一次简报", isLoading = true))
        compose.setContent {
            DailySatoriTheme(darkTheme = false) {
                Surface { LocalNewsTurnCard(state) }
            }
        }
        compose.onNodeWithText("正在读取本机简报…").assertExists()

        state = LocalNewsTurn("最近一次简报", isLoading = false, briefing = null)
        compose.waitForIdle()
        compose.onNodeWithText("无法读取本机简报，请稍后重试。").assertExists()

        state = LocalNewsTurn(
            "今天有什么新闻",
            isLoading = false,
            briefing = LocalNewsBriefing(LocalNewsIntent.Today, LocalNewsBriefingState.NotPrepared, "2026-11-28", isToday = true, latestDate = "2026-11-27"),
        )
        compose.waitForIdle()
        compose.onNodeWithText("今天还没有生成简报。最近一次在 2026-11-27。").assertExists()

        state = LocalNewsTurn(
            "今天有什么新闻",
            isLoading = false,
            briefing = LocalNewsBriefing(LocalNewsIntent.Today, LocalNewsBriefingState.Generating, "2026-11-28", isToday = true),
        )
        compose.waitForIdle()
        compose.onNodeWithText("今天的简报正在生成中。").assertExists()

        state = LocalNewsTurn(
            "最近一次简报",
            isLoading = false,
            briefing = LocalNewsBriefing(LocalNewsIntent.Latest, LocalNewsBriefingState.NotPrepared),
        )
        compose.waitForIdle()
        compose.onNodeWithText("还没有可读的简报。").assertExists()
    }
}

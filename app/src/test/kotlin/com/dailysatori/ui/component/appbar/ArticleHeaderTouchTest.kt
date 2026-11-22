package com.dailysatori.ui.component.appbar

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import com.dailysatori.ui.theme.IconSize
import com.dailysatori.ui.theme.SmartReadingIcon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.theme.DailySatoriTheme
import com.dailysatori.ui.theme.Height
import com.dailysatori.ui.theme.MoreActionsIcon
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
@Config(sdk = [28], application = Application::class, qualifiers = "zh-rCN-w320dp-h640dp-xhdpi")
class ArticleHeaderTouchTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun adjacentActionsHaveIndependentTouchTargetsAndDispatch() {
        val events = mutableListOf<String>()
        var touchTargetPx = 0
        val readingEnabled = mutableStateOf(true)
        val readingLabel = mutableStateOf("智能阅读")
        val favorited = mutableStateOf(false)
        compose.setContent {
            touchTargetPx = with(LocalDensity.current) { Height.touchTarget.roundToPx() }
            DailySatoriTheme {
                CompositionLocalProvider(LocalAssistantLauncher provides { events += "assistant" }) {
                    Box(Modifier.width(320.dp)) {
                        AppTopBar("", onBack = {}, pageActions = listOf(
                            PageAction(readingLabel.value, SmartReadingIcon, iconSize = IconSize.l,
                                enabled = readingEnabled.value, alwaysVisible = true) { events += "reading" },
                            PageAction("文章更多操作", MoreActionsIcon, alwaysVisible = true, menuContent = { close ->
                                DropdownMenuItem(text = { Text("查看原文") }, onClick = { events += "original"; close() })
                                DropdownMenuItem(text = { Text(if (favorited.value) "取消收藏" else "收藏") },
                                    onClick = { favorited.value = !favorited.value; close() })
                                DropdownMenuItem(text = { Text("重新翻译") }, onClick = { events += "article"; close() })
                                DropdownMenuItem(text = { Text("重新获取原文") }, onClick = close)
                            }) {},
                        ))
                    }
                }
            }
        }

        val reading = compose.onNodeWithContentDescription("智能阅读")
        compose.onNodeWithText("智能阅读").assertDoesNotExist()
        compose.onNodeWithText("收藏").assertDoesNotExist()
        compose.onNodeWithText("文章详情").assertDoesNotExist()
        val article = compose.onNodeWithContentDescription("文章更多操作")
        val assistant = compose.onNodeWithContentDescription("打开 AI 助手")
        val targets = listOf(reading, article, assistant).map { it.fetchSemanticsNode().boundsInRoot }
        targets.forEach {
            assertTrue(it.width >= touchTargetPx && it.height >= touchTargetPx, "Touch target: $it")
        }
        targets.zipWithNext().forEach { (left, right) -> assertTrue(left.right <= right.left) }

        compose.onNodeWithText("查看原文").assertDoesNotExist()
        article.performTouchInput { click(center) }
        compose.onNodeWithText("查看原文").performTouchInput { click(center) }
        article.performTouchInput { click(center) }
        compose.onNodeWithText("收藏").performTouchInput { click(center) }
        article.performTouchInput { click(center) }
        compose.onNodeWithText("取消收藏").assertExists()
        compose.onNodeWithText("重新获取原文").assertExists()
        compose.onNodeWithText("重新翻译").performTouchInput { click(center) }
        reading.performTouchInput { click(center) }
        compose.runOnIdle { readingEnabled.value = false }
        reading.performTouchInput { click(center) }
        assistant.performTouchInput { click(center) }
        compose.runOnIdle {
            assertEquals(listOf("original", "article", "reading", "assistant"), events)
            readingLabel.value = "Smart reading"
        }
        compose.onNodeWithText("Smart reading").assertDoesNotExist()
        val englishTarget = compose.onNodeWithContentDescription("Smart reading").fetchSemanticsNode().boundsInRoot
        assertTrue(englishTarget.width >= touchTargetPx && englishTarget.height >= touchTargetPx)
        assertTrue(englishTarget.right <= article.fetchSemanticsNode().boundsInRoot.left)
    }
}

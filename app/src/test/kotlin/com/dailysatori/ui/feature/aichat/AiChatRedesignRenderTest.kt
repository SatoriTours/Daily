package com.dailysatori.ui.feature.aichat

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.dailysatori.service.assistant.AssistantReference
import com.dailysatori.ui.theme.DailySatoriTheme
import com.dailysatori.ui.theme.Height
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = Application::class, qualifiers = "zh-rCN-w360dp-h800dp-xhdpi")
class AiChatRedesignRenderTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun assistantPaperInputBarSwitchesMutedSendEnabledSendAndStopWithAccessibleTouchTarget() {
        var draft by mutableStateOf(TextFieldValue(""))
        var processing by mutableStateOf(false)
        var canSend by mutableStateOf(true)
        var canEdit by mutableStateOf(true)
        var sendClicks = 0
        var stopClicks = 0

        setThemedContent {
            ChatInputBar(
                value = draft,
                onValueChange = { draft = it },
                onSend = { sendClicks++ },
                onStop = { stopClicks++ },
                isProcessing = processing,
                canSend = canSend,
                canEdit = canEdit,
            )
        }

        // Empty draft -> muted disabled send button with accessible touch target
        compose.onNodeWithContentDescription("发送")
            .assertExists()
            .assertIsNotEnabled()
            .assertWidthIsAtLeast(Height.touchTarget)
            .assertHeightIsAtLeast(Height.touchTarget)

        // Non-empty draft -> enabled send button triggers onSend
        draft = TextFieldValue("今天有什么重点的新闻吗？", TextRange(12))
        compose.waitForIdle()
        compose.onNodeWithContentDescription("发送")
            .assertIsEnabled()
            .performClick()
        assertEquals(1, sendClicks)

        // Disabled when canSend is false
        canSend = false
        compose.waitForIdle()
        compose.onNodeWithContentDescription("发送").assertIsNotEnabled()

        // Disabled when canEdit is false
        canSend = true
        canEdit = false
        compose.waitForIdle()
        compose.onNodeWithContentDescription("发送").assertIsNotEnabled()

        // Processing -> stop button enabled even with empty draft, triggers onStop
        canEdit = true
        draft = TextFieldValue("")
        processing = true
        compose.waitForIdle()
        compose.onNodeWithContentDescription("停止生成")
            .assertExists()
            .assertIsEnabled()
            .assertWidthIsAtLeast(Height.touchTarget)
            .assertHeightIsAtLeast(Height.touchTarget)
            .performClick()
        assertEquals(1, stopClicks)
    }

    @Test
    fun standardChatInputFieldForBookReflectionKeepsSendAndStopCallbacks() {
        var text by mutableStateOf("读书反思问题")
        var processing by mutableStateOf(false)
        var sendClicks = 0
        var stopClicks = 0

        setThemedContent {
            ChatInputField(
                inputText = text,
                onInputChange = { text = it },
                onSend = { sendClicks++ },
                onStop = { stopClicks++ },
                isProcessing = processing,
            )
        }

        compose.onNodeWithContentDescription("发送").assertIsEnabled().performClick()
        assertEquals(1, sendClicks)

        processing = true
        compose.waitForIdle()
        compose.onNodeWithContentDescription("停止生成").assertIsEnabled().performClick()
        assertEquals(1, stopClicks)
    }

    @Test
    fun editorialMessageBubbleSuppressesDuplicateActiveTaskCardWhileStandardKeepsIt() {
        val activeMsg = ChatMessageUi(
            id = "a-active",
            role = "assistant",
            content = "",
            timestamp = 1L,
            turnId = 10L,
            taskId = 20L,
            taskDisplayState = TaskDisplayState.Processing,
            requestStage = "requesting",
        )
        var style by mutableStateOf(MessageBubbleStyle.AssistantEditorial)

        setThemedContent {
            MessageBubble(
                message = activeMsg,
                style = style,
            )
        }

        compose.onNodeWithText("取消任务").assertDoesNotExist()
        compose.onNodeWithText("等待 AI 返回").assertDoesNotExist()

        style = MessageBubbleStyle.Standard
        compose.waitForIdle()
        compose.onNodeWithText("取消任务").assertExists()
        compose.onNodeWithText("等待 AI 返回").assertExists()
    }

    @Test
    fun editorialMessageBubbleUnifiesFailedFeedbackAndPreservesRetryAndTaskCenter() {
        var retriedTurnId: Long? = null
        var openedTaskCenter = 0
        val failedMsg = ChatMessageUi(
            id = "a-failed",
            role = "assistant",
            content = "",
            timestamp = 1L,
            isError = true,
            turnId = 42L,
            taskId = 99L,
            taskDisplayState = TaskDisplayState.Failed,
            errorMessage = "网络连接超时",
        )

        setThemedContent {
            MessageBubble(
                message = failedMsg,
                onRetryTurn = { retriedTurnId = it },
                onTaskCenterClick = { openedTaskCenter++ },
                style = MessageBubbleStyle.AssistantEditorial,
            )
        }

        assertEquals(1, compose.onAllNodesWithText("任务执行失败").fetchSemanticsNodes().size)
        compose.onNodeWithText("网络连接超时").assertExists()
        compose.onNodeWithText("重试任务").performClick()
        assertEquals(42L, retriedTurnId)
        compose.onNodeWithContentDescription("任务中心").performClick()
        assertEquals(1, openedTaskCenter)
    }

    @Test
    fun editorialMessageBubblePreservesRequiresResumeCancelledAndReferences() {
        var retriedTurnId: Long? = null
        var clickedArticleId: Long? = null
        var msg by mutableStateOf(
            ChatMessageUi(
                id = "a-resume",
                role = "assistant",
                content = "",
                timestamp = 1L,
                turnId = 55L,
                taskDisplayState = TaskDisplayState.RequiresResume,
            ),
        )

        setThemedContent {
            MessageBubble(
                message = msg,
                onRetryTurn = { retriedTurnId = it },
                onArticleClick = { clickedArticleId = it },
                style = MessageBubbleStyle.AssistantEditorial,
            )
        }

        compose.onNodeWithText("任务恢复中…").assertExists()
        compose.onNodeWithText("重试任务").performClick()
        assertEquals(55L, retriedTurnId)

        msg = ChatMessageUi(
            id = "a-cancelled",
            role = "assistant",
            content = "已完成前半段内容。",
            timestamp = 2L,
            taskDisplayState = TaskDisplayState.Cancelled,
        )
        compose.waitForIdle()
        compose.onNodeWithText("已完成前半段内容。").assertExists()
        compose.onNodeWithText("任务已取消").assertExists()

        msg = ChatMessageUi(
            id = "a-reply",
            role = "assistant",
            content = "## 今日摘要\n正文内容完整可读。",
            timestamp = 3L,
            references = listOf(AssistantReference(source = "article", id = "108", title = "示例参考文章")),
        )
        compose.waitForIdle()
        compose.onNodeWithText("今日摘要").assertExists()
        compose.onNodeWithText("正文内容完整可读。").assertExists()
        compose.onNodeWithText("展开").assertExists()
        compose.onNodeWithText("参考来源 · 1 条").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("收起").assertExists()
        compose.onNodeWithText("示例参考文章").performClick()
        assertEquals(108L, clickedArticleId)
    }

    private fun setThemedContent(content: @Composable () -> Unit) {
        compose.setContent {
            DailySatoriTheme(darkTheme = false) {
                Surface { content() }
            }
        }
    }
}

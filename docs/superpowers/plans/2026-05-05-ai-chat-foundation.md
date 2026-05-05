# AI Chat Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stabilize the AI assistant chat by removing the confusing refresh action, adding stop generation, preserving history, and preventing blank assistant bubbles.

**Architecture:** Keep the existing `AiChatViewModel` as the state owner and add a cancellable active request job. Extract small pure chat UI/state helpers so behavior around controls, blank answers, stopped state, and errors can be tested without Compose instrumentation or database fakes.

**Tech Stack:** Kotlin, Android Jetpack Compose, Koin ViewModel, Kotlin coroutines, SQLDelight-backed chat persistence.

---

## File Structure

- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatViewModel.kt` - track active request job, expose stop state, suppress blank assistant messages.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt` - remove refresh button and wire stop action to the input bar.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/ChatInputBar.kt` - keep the input usable while processing and switch trailing action between send and stop.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt` - render assistant status/error cards safely when content is non-blank.
- Create: `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt` - pure tests for helper behavior.

No database schema change is required.

### Task 1: Add Tested Chat UI/State Helpers

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/ChatInputBar.kt`
- Create: `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt`

- [ ] **Step 1: Write the failing helper tests**

Create `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt`:

```kotlin
package com.dailysatori.ui.feature.aichat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiChatUiStateTest {
    @Test
    fun blankAssistantAnswerIsSuppressed() {
        assertNull(buildAssistantMessageOrNull("   ", emptyList(), emptyList(), now = 1L))
    }

    @Test
    fun nonBlankAssistantAnswerBuildsMessage() {
        val message = buildAssistantMessageOrNull("## 结论\n可以。", emptyList(), listOf("完成"), now = 1L)

        assertEquals("assistant", message?.role)
        assertEquals("## 结论\n可以。", message?.content)
        assertEquals(listOf("完成"), message?.steps)
    }

    @Test
    fun stoppedGenerationUsesTransientStatusText() {
        assertEquals("已停止生成", aiChatStoppedStatusText())
    }

    @Test
    fun inputActionSwitchesBetweenSendAndStop() {
        assertEquals(ChatInputAction.Send, chatInputAction(isProcessing = false))
        assertEquals(ChatInputAction.Stop, chatInputAction(isProcessing = true))
        assertEquals("发送", chatInputActionDescription(ChatInputAction.Send))
        assertEquals("停止生成", chatInputActionDescription(ChatInputAction.Stop))
    }

    @Test
    fun topBarDoesNotExposeRefreshAction() {
        assertFalse(aiChatShowsRefreshAction())
        assertTrue(aiChatShowsMemorySearchAction())
    }
}
```

- [ ] **Step 2: Run the focused tests and verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.aichat.AiChatUiStateTest"`

Expected: FAIL with unresolved references such as `buildAssistantMessageOrNull`, `ChatInputAction`, `chatInputAction`, and `aiChatShowsRefreshAction`.

- [ ] **Step 3: Add the minimal helper implementation**

In `AiChatViewModel.kt`, add these top-level helpers after `ChatMessageUi`:

```kotlin
fun aiChatStoppedStatusText(): String = "已停止生成"

fun aiChatBlankResponseMessage(): String = "这次没有生成有效回复，请稍后重试。"

fun aiChatShowsRefreshAction(): Boolean = false

fun aiChatShowsMemorySearchAction(): Boolean = true

fun buildAssistantMessageOrNull(
    answer: String,
    searchResults: List<McpSearchResult>,
    steps: List<String>,
    now: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
): ChatMessageUi? {
    val content = answer.trim()
    if (content.isBlank()) return null
    return ChatMessageUi(
        id = generateChatMessageId(now),
        role = "assistant",
        content = content,
        timestamp = now,
        isError = content.startsWith("😔 **出现问题**") || content == aiChatBlankResponseMessage(),
        searchResults = searchResults,
        steps = steps,
    )
}

fun generateChatMessageId(now: Long): String {
    val r = (0..9999).random()
    return "${now}_${r}"
}
```

In `ChatInputBar.kt`, add these top-level helpers before the composable:

```kotlin
enum class ChatInputAction { Send, Stop }

fun chatInputAction(isProcessing: Boolean): ChatInputAction =
    if (isProcessing) ChatInputAction.Stop else ChatInputAction.Send

fun chatInputActionDescription(action: ChatInputAction): String = when (action) {
    ChatInputAction.Send -> "发送"
    ChatInputAction.Stop -> "停止生成"
}
```

- [ ] **Step 4: Run the focused tests and verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.aichat.AiChatUiStateTest"`

Expected: PASS.

### Task 2: Make Generation Cancellable And Suppress Blank Assistant Bubbles

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatViewModel.kt`

- [ ] **Step 1: Add request job state and stop action**

Update imports in `AiChatViewModel.kt`:

```kotlin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
```

Add a private property inside `AiChatViewModel`:

```kotlin
private var activeRequestJob: Job? = null
```

Add this public method inside `AiChatViewModel`:

```kotlin
fun stopGeneration() {
    activeRequestJob?.cancel()
    activeRequestJob = null
    _state.update {
        it.copy(
            isProcessing = false,
            currentStep = aiChatStoppedStatusText(),
        )
    }
}
```

- [ ] **Step 2: Guard concurrent sends and store the active job**

At the start of `sendMessage(content: String)`, add:

```kotlin
if (_state.value.isProcessing) return
```

Replace `viewModelScope.launch(Dispatchers.IO) {` in `sendMessage` with:

```kotlin
activeRequestJob = viewModelScope.launch(Dispatchers.IO) {
```

- [ ] **Step 3: Build assistant messages through the helper**

Replace the current `assistantMessage` construction and update block in `sendMessage` with:

```kotlin
val assistantMessage = buildAssistantMessageOrNull(
    answer = result.answer.ifBlank { aiChatBlankResponseMessage() },
    searchResults = result.searchResults,
    steps = steps,
)
_state.update { current ->
    current.copy(
        messages = assistantMessage?.let { current.messages + it } ?: current.messages,
        isProcessing = false,
        currentStep = "",
    )
}
assistantMessage?.let { persistMessage(it) }
activeRequestJob = null
```

- [ ] **Step 4: Treat cancellation as a stopped state, not an error**

Wrap the body of the active job with `try/catch`:

```kotlin
activeRequestJob = viewModelScope.launch(Dispatchers.IO) {
    try {
        val steps = mutableListOf<String>()
        val result = mcpAgentService.processQuery(
            query = content,
            onStep = { step, status ->
                _state.update { it.copy(currentStep = step) }
                if (status == "completed") steps.add(step)
            },
        )
        val assistantMessage = buildAssistantMessageOrNull(
            answer = result.answer.ifBlank { aiChatBlankResponseMessage() },
            searchResults = result.searchResults,
            steps = steps,
        )
        _state.update { current ->
            current.copy(
                messages = assistantMessage?.let { current.messages + it } ?: current.messages,
                isProcessing = false,
                currentStep = "",
            )
        }
        assistantMessage?.let { persistMessage(it) }
    } catch (_: CancellationException) {
        _state.update {
            it.copy(
                isProcessing = false,
                currentStep = aiChatStoppedStatusText(),
            )
        }
    } finally {
        activeRequestJob = null
    }
}
```

- [ ] **Step 5: Reuse deterministic id helper**

Replace `generateId()` with:

```kotlin
private fun generateId(): String =
    generateChatMessageId(kotlinx.datetime.Clock.System.now().toEpochMilliseconds())
```

- [ ] **Step 6: Run the focused tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.aichat.AiChatUiStateTest"`

Expected: PASS.

### Task 3: Update Compose UI Controls And Status Rendering

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/ChatInputBar.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt`

- [ ] **Step 1: Remove the top-bar refresh action**

In `AiChatScreen.kt`, remove this import:

```kotlin
import androidx.compose.material.icons.filled.Refresh
```

Remove the `IconButton` whose `onClick` calls `viewModel.clearMessages()` and whose content description is `新对话`.

- [ ] **Step 2: Wire the input bar stop callback**

Update the `ChatInputBar` call in `AiChatScreen.kt`:

```kotlin
ChatInputBar(
    inputText = inputText,
    onInputChange = { inputText = it },
    onSend = {
        if (inputText.isNotBlank()) {
            viewModel.sendMessage(inputText)
            inputText = ""
        }
    },
    onStop = viewModel::stopGeneration,
    isProcessing = state.isProcessing,
)
```

- [ ] **Step 3: Keep stopped status visible without treating it as progress**

In `AiChatScreen.kt`, after the `LazyColumn` thinking item block, add a status item:

```kotlin
if (!state.isProcessing && state.currentStep == aiChatStoppedStatusText()) {
    item(key = "stopped") {
        AssistantStatusCard(text = state.currentStep)
    }
}
```

Add this composable below `ThinkingIndicator()`:

```kotlin
@Composable
private fun AssistantStatusCard(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.m),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(0.8f),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(Spacing.m),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

- [ ] **Step 4: Change `ChatInputBar` signature and action button**

Replace the composable signature in `ChatInputBar.kt` with:

```kotlin
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isProcessing: Boolean,
)
```

Add this import:

```kotlin
import androidx.compose.material.icons.filled.Stop
```

Inside the composable, before `Surface`, add:

```kotlin
val action = chatInputAction(isProcessing)
```

Change the `TextField` to remain enabled:

```kotlin
enabled = true,
```

Replace the `FilledIconButton` block with:

```kotlin
FilledIconButton(
    onClick = {
        when (action) {
            ChatInputAction.Send -> onSend()
            ChatInputAction.Stop -> onStop()
        }
    },
    enabled = action == ChatInputAction.Stop || inputText.isNotBlank(),
    modifier = Modifier.size(36.dp),
    shape = CircleShape,
    colors = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = contentColorFor(MaterialTheme.colorScheme.primary),
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
    ),
) {
    Icon(
        imageVector = when (action) {
            ChatInputAction.Send -> Icons.AutoMirrored.Filled.Send
            ChatInputAction.Stop -> Icons.Default.Stop
        },
        contentDescription = chatInputActionDescription(action),
        modifier = Modifier.size(16.dp),
    )
}
```

- [ ] **Step 5: Avoid rendering blank assistant markdown**

In `MessageBubble.kt`, replace the assistant branch with:

```kotlin
val assistantContent = message.content.trim()
if (assistantContent.isNotBlank()) {
    Column(modifier = Modifier.padding(Spacing.m)) {
        Markdown(
            content = assistantContent,
            typography = MarkdownStyles.cardTypography(),
            padding = MarkdownStyles.cardPadding(),
        )
    }
}
```

- [ ] **Step 6: Run the focused tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.aichat.AiChatUiStateTest"`

Expected: PASS.

### Task 4: Verification

**Files:**
- Verify modified Kotlin files only.

- [ ] **Step 1: Run focused unit tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.aichat.AiChatUiStateTest"`

Expected: PASS.

- [ ] **Step 2: Run required compile check**

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Install to connected Android device**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`

Expected: BUILD SUCCESSFUL and install completes.

- [ ] **Step 4: Launch the app**

Run: `adb shell am start -n com.dailysatori/.MainActivity`

Expected: Activity starts without error.

- [ ] **Step 5: Manual smoke checks**

In the Android app:

- Confirm AI top bar shows memory search but no refresh action.
- Ask a question and confirm the user message appears immediately.
- Confirm the send button changes to stop while processing.
- Stop a request and confirm `已停止生成` appears with no blank assistant bubble.
- Return to the AI tab and confirm existing history still loads.

## Self-Review

- Spec coverage: refresh removal, stop generation, blank answer suppression, history preservation, memory search preservation, and verification are covered.
- Placeholder scan: no deferred implementation placeholders are present.
- Type consistency: helper names and signatures are consistent across tasks.
- Commit policy: this plan intentionally omits commit steps because repository instructions say not to commit unless explicitly requested.

# AI Chat WeChat Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the AI assistant into a quiet WeChat-style chat screen with right/left bubbles, true streaming final answers, in-bubble Markdown/references, and smoother scrolling.

**Architecture:** Split the work into UI presentation helpers, streaming state transitions, OpenAI-compatible SSE support, MCP streaming orchestration, and final device verification. Keep the existing non-streaming `processQuery()` path as fallback and compatibility surface while adding streaming APIs used by `AiChatViewModel`.

**Tech Stack:** Kotlin Multiplatform shared module, Ktor client, kotlinx.serialization JSON, Android Jetpack Compose, SQLDelight-backed chat persistence, Gradle unit tests.

---

## File Structure

- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt`
  - Rework user/assistant message bubbles into WeChat-style left/right bubbles, remove assistant rail/kicker, render streaming text cheaply, and keep references inside assistant bubble.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt`
  - Adjust list ordering and bottom anchoring; remove repeated reversed-list creation; keep older-history loading.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatViewModel.kt`
  - Add streaming-aware message state and update helpers; call streaming MCP path; persist final assistant answer.
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt`
  - Add source/unit tests for WeChat alignment, rail removal, streaming rendering mode, list order, and streaming message replacement.
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/ai/AiService.kt`
  - Add OpenAI-compatible streaming chat completion request and SSE chunk parsing helpers.
- Create: `shared/src/commonTest/kotlin/com/dailysatori/service/ai/AiServiceStreamingTest.kt`
  - Test streaming request shape and SSE chunk parsing.
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentService.kt`
  - Add `processQueryStreaming()` that reuses local search and tool rounds, streams final answer chunks, and falls back to `processQuery()` when streaming is unavailable.
- Modify: `shared/src/commonTest/kotlin/com/dailysatori/service/mcp/McpAgentPresentationTest.kt`
  - Add source-level tests for streaming MCP entrypoint and fallback preservation.

## Task 1: WeChat-Style Message Presentation

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt`

- [ ] **Step 1: Write failing UI source tests for WeChat bubble rules**

Add these tests to `AiChatUiStateTest`:

```kotlin
@Test
fun chatBubblesUseWechatAlignmentAndNoAssistantRail() {
    val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt").readText()

    assertTrue(source.contains("Arrangement.End"))
    assertTrue(source.contains("Arrangement.Start"))
    assertTrue(source.contains("widthIn(max = ChatUserBubbleMaxWidth)"))
    assertTrue(source.contains("widthIn(max = ChatAssistantBubbleMaxWidth)"))
    assertFalse(source.contains("drawRoundRect("))
    assertFalse(source.contains("AssistantKicker("))
    assertFalse(source.contains("text = \"AI 回复\""))
}

@Test
fun streamingAssistantUsesPlainTextBeforeMarkdown() {
    val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt").readText()

    assertTrue(source.contains("isStreaming = message.isStreaming"))
    assertTrue(source.contains("if (isStreaming)"))
    assertTrue(source.contains("Text("))
    assertTrue(source.contains("Markdown("))
}
```

- [ ] **Step 2: Run the new tests and verify failure**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.aichat.AiChatUiStateTest.chatBubblesUseWechatAlignmentAndNoAssistantRail" --tests "com.dailysatori.ui.feature.aichat.AiChatUiStateTest.streamingAssistantUsesPlainTextBeforeMarkdown"
```

Expected: FAIL because max-width constants and streaming rendering branches do not exist, and the current assistant rail still exists.

- [ ] **Step 3: Add streaming flag to `ChatMessageUi`**

In `AiChatViewModel.kt`, change `ChatMessageUi` to:

```kotlin
data class ChatMessageUi(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val isError: Boolean = false,
    val isStreaming: Boolean = false,
    val searchResults: List<McpSearchResult> = emptyList(),
    val steps: List<String> = emptyList(),
)
```

Keep persisted messages non-streaming by leaving `toChatMessageUi()` unchanged except for the default value.

- [ ] **Step 4: Rework `MessageBubble.kt` imports and constants**

In `MessageBubble.kt`, remove these imports:

```kotlin
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.dailysatori.ui.theme.BorderWidth
```

Add this import:

```kotlin
import androidx.compose.foundation.layout.widthIn
```

Add constants near the helper functions:

```kotlin
private val ChatUserBubbleMaxWidth = 302.dp
private val ChatAssistantBubbleMaxWidth = 336.dp
```

Change:

```kotlin
fun assistantMessageUsesEditorialRail(): Boolean = true
```

to:

```kotlin
fun assistantMessageUsesEditorialRail(): Boolean = false
```

- [ ] **Step 5: Replace percentage-width wrapper with wrap-content max-width bubbles**

In `MessageBubble`, replace the nested `Row`/`Column` block with:

```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
) {
    Box(
        modifier = Modifier
            .widthIn(max = if (isUser) ChatUserBubbleMaxWidth else ChatAssistantBubbleMaxWidth)
            .pointerInput(message.id) { detectTapGestures(onLongPress = { showActions = true }) },
    ) {
        Column {
            when (treatment) {
                ChatMessageTreatment.MutedUserNote -> MutedUserMessage(message.content)
                ChatMessageTreatment.StructuredAssistantNote -> StructuredAssistantMessage(
                    content = assistantContent,
                    searchResults = message.searchResults,
                    isStreaming = message.isStreaming,
                    onReferenceClick = onReferenceClick,
                )
                ChatMessageTreatment.ErrorNote -> ErrorAssistantMessage(assistantContent)
            }
            DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                DropdownMenuItem(
                    text = { Text("复制") },
                    onClick = {
                        clipboard.setText(AnnotatedString(message.content))
                        showActions = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("删除") },
                    onClick = {
                        showActions = false
                        onDelete(message)
                    },
                )
                DropdownMenuItem(
                    text = { Text("重问") },
                    onClick = {
                        showActions = false
                        onReAsk(message)
                    },
                )
            }
        }
    }
}
```

- [ ] **Step 6: Replace assistant rail layout with left bubble**

Change `StructuredAssistantMessage` signature to:

```kotlin
private fun StructuredAssistantMessage(
    content: String,
    searchResults: List<McpSearchResult>,
    isStreaming: Boolean,
    onReferenceClick: (McpSearchResult) -> Unit,
)
```

Replace the function body with:

```kotlin
val structured = structuredAssistantContent(content)
Surface(
    shape = RoundedCornerShape(
        topStart = Radius.m,
        topEnd = Radius.m,
        bottomStart = Radius.xs,
        bottomEnd = Radius.m,
    ),
    color = MaterialTheme.colorScheme.surfaceContainer,
) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        if (isStreaming) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            if (structured.body.isBlank() || structured.title == "AI 回复") {
                Markdown(
                    content = content,
                    typography = MarkdownStyles.summaryTypography(),
                    padding = MarkdownStyles.summaryPadding(),
                )
            } else {
                Text(
                    text = structured.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Markdown(
                    content = structured.body,
                    typography = MarkdownStyles.summaryTypography(),
                    padding = MarkdownStyles.summaryPadding(),
                )
            }
        }
        if (!isStreaming && searchResults.isNotEmpty()) {
            SearchResultsSection(searchResults, onReferenceClick)
        }
    }
}
```

Delete the `AssistantKicker()` composable.

- [ ] **Step 7: Keep user bubble compact and right-side**

Change `MutedUserMessage` surface to use a right-tail shape:

```kotlin
Surface(
    shape = RoundedCornerShape(
        topStart = Radius.m,
        topEnd = Radius.m,
        bottomStart = Radius.m,
        bottomEnd = Radius.xs,
    ),
    color = MaterialTheme.colorScheme.primaryContainer,
) {
    Text(
        text = content,
        modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        style = MaterialTheme.typography.bodyMedium,
    )
}
```

- [ ] **Step 8: Run UI tests and commit**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.aichat.AiChatUiStateTest"
```

Expected: PASS.

Commit:

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatViewModel.kt app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt
git commit -m "feat: redesign AI chat bubbles"
```

## Task 2: Chat List Ordering And Scroll Performance

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatViewModel.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt`

- [ ] **Step 1: Write failing tests for stable list ordering and no reversed allocation**

Add to `AiChatUiStateTest`:

```kotlin
@Test
fun chatDisplayMessagesKeepsNaturalOrderForBottomAnchoredList() {
    val oldest = ChatMessageUi("old", "user", "最旧", 1L)
    val newest = ChatMessageUi("new", "assistant", "最新", 2L)

    assertEquals(listOf(oldest, newest), aiChatDisplayMessages(listOf(oldest, newest)))
}

@Test
fun aiChatScreenDoesNotAllocateReversedListOnRecomposition() {
    val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt").readText()

    assertFalse(source.contains("asReversed()"))
    assertFalse(source.contains("reverseLayout = true"))
    assertTrue(source.contains("animateScrollToItem(displayMessages.lastIndex)"))
}
```

Update the existing `chatHistoryDisplaysNewestMessageFirstForReverseLayout` test to the new natural-order expectation or remove it after the new test covers the behavior.

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.aichat.AiChatUiStateTest.chatDisplayMessagesKeepsNaturalOrderForBottomAnchoredList" --tests "com.dailysatori.ui.feature.aichat.AiChatUiStateTest.aiChatScreenDoesNotAllocateReversedListOnRecomposition"
```

Expected: FAIL because the current helper returns `messages.asReversed()` and the screen uses `reverseLayout = true`.

- [ ] **Step 3: Change display order helper**

In `AiChatScreen.kt`, change:

```kotlin
fun aiChatDisplayMessages(messages: List<ChatMessageUi>): List<ChatMessageUi> = messages.asReversed()
```

to:

```kotlin
fun aiChatDisplayMessages(messages: List<ChatMessageUi>): List<ChatMessageUi> = messages
```

- [ ] **Step 4: Change LazyColumn to normal bottom-anchored chat**

In `AiChatScreen`, remove `reverseLayout = true` from `LazyColumn`.

Change the thinking and stopped status placement to appear after messages by moving those items below the `items(...)` block:

```kotlin
items(
    items = displayMessages,
    key = { it.id },
    contentType = { aiChatMessageContentType(it) },
) { message ->
    MessageBubble(
        message = message,
        onReferenceClick = ::openReference,
        onDelete = viewModel::deleteMessage,
        onReAsk = viewModel::reAsk,
    )
}
if (state.isProcessing && displayMessages.none { it.isStreaming }) {
    item(key = "thinking", contentType = "status") {
        ThinkingIndicator()
    }
}
if (!state.isProcessing && state.currentStep == aiChatStoppedStatusText()) {
    item(key = "stopped", contentType = "status") {
        AssistantStatusCard(text = state.currentStep)
    }
}
```

- [ ] **Step 5: Add bottom scroll effect that respects user position**

Add after the older-message loading `LaunchedEffect`:

```kotlin
LaunchedEffect(displayMessages.size, displayMessages.lastOrNull()?.content, state.isProcessing) {
    val lastIndex = displayMessages.lastIndex
    if (lastIndex < 0) return@LaunchedEffect
    val nearBottom = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= lastIndex - 1 } ?: true
    if (!listState.isScrollInProgress && nearBottom) {
        listState.animateScrollToItem(lastIndex)
    }
}
```

- [ ] **Step 6: Update older-history trigger for normal order**

Change `aiChatShouldLoadOlder` to trigger near the top:

```kotlin
fun aiChatShouldLoadOlder(
    firstVisibleItemIndex: Int,
    totalItemsCount: Int,
    isScrollInProgress: Boolean,
    canLoadOlder: Boolean,
    isLoadingOlder: Boolean,
    messageCount: Int,
): Boolean = messageCount > 0 && totalItemsCount > 0 && canLoadOlder && !isLoadingOlder && isScrollInProgress &&
    firstVisibleItemIndex <= 1
```

Update the `snapshotFlow` call to pass `listState.firstVisibleItemIndex` instead of the max visible index.

Update existing tests for `aiChatShouldLoadOlder` so the positive case uses `firstVisibleItemIndex = 1` and the negative case uses a lower-history-unrelated index such as `8`.

- [ ] **Step 7: Run tests and commit**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.aichat.AiChatUiStateTest"
```

Expected: PASS.

Commit:

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt
git commit -m "perf: simplify AI chat list scrolling"
```

## Task 3: Streaming UI State Transitions

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatViewModel.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt`

- [ ] **Step 1: Write failing tests for streaming message state helpers**

Add to `AiChatUiStateTest`:

```kotlin
@Test
fun streamingChunkCreatesOrUpdatesSingleAssistantMessage() {
    val initial = AiChatState(messages = listOf(ChatMessageUi("u1", "user", "你好", 1L)))
    val first = initial.withStreamingAssistantChunk(
        messageId = "a1",
        chunk = "第一段",
        now = 2L,
    )
    val second = first.withStreamingAssistantChunk(
        messageId = "a1",
        chunk = "第二段",
        now = 3L,
    )

    assertEquals(2, second.messages.size)
    assertEquals("第一段第二段", second.messages.last().content)
    assertTrue(second.messages.last().isStreaming)
}

@Test
fun streamingFinalizationAttachesReferencesAndStopsStreaming() {
    val state = AiChatState(messages = listOf(ChatMessageUi("a1", "assistant", "草稿", 1L, isStreaming = true)))
    val refs = listOf(McpSearchResult(1, "article", "新闻", "摘要", "2026-05-30"))

    val finished = state.finishedStreamingAssistant(
        messageId = "a1",
        finalContent = "最终回答",
        searchResults = refs,
        steps = listOf("完成"),
    )

    assertFalse(finished.messages.first().isStreaming)
    assertEquals("最终回答", finished.messages.first().content)
    assertEquals(refs, finished.messages.first().searchResults)
    assertEquals(listOf("完成"), finished.messages.first().steps)
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.aichat.AiChatUiStateTest.streamingChunkCreatesOrUpdatesSingleAssistantMessage" --tests "com.dailysatori.ui.feature.aichat.AiChatUiStateTest.streamingFinalizationAttachesReferencesAndStopsStreaming"
```

Expected: FAIL because `withStreamingAssistantChunk` and `finishedStreamingAssistant` do not exist.

- [ ] **Step 3: Add pure streaming state helpers**

In `AiChatViewModel.kt`, add after `buildAssistantMessageOrNull`:

```kotlin
fun AiChatState.withStreamingAssistantChunk(
    messageId: String,
    chunk: String,
    now: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
): AiChatState {
    if (chunk.isEmpty()) return this
    val index = messages.indexOfFirst { it.id == messageId }
    val updatedMessages = if (index >= 0) {
        messages.mapIndexed { i, message ->
            if (i == index) message.copy(content = message.content + chunk, isStreaming = true) else message
        }
    } else {
        messages + ChatMessageUi(
            id = messageId,
            role = "assistant",
            content = chunk,
            timestamp = now,
            isStreaming = true,
        )
    }
    return copy(messages = updatedMessages, isProcessing = true, currentStep = "")
}

fun AiChatState.finishedStreamingAssistant(
    messageId: String,
    finalContent: String,
    searchResults: List<McpSearchResult>,
    steps: List<String>,
): AiChatState = copy(
    messages = messages.map { message ->
        if (message.id == messageId) {
            message.copy(
                content = finalContent.ifBlank { message.content },
                isStreaming = false,
                searchResults = searchResults,
                steps = steps,
                isError = finalContent.startsWith("😔 **出现问题**") || finalContent == aiChatBlankResponseMessage(),
            )
        } else {
            message
        }
    },
    isProcessing = false,
    currentStep = "",
)
```

- [ ] **Step 4: Add non-streaming fallback helper**

Add this helper for fallback finalization when streaming returns a full result without chunks:

```kotlin
fun AiChatState.withAssistantMessage(message: ChatMessageUi?): AiChatState = copy(
    messages = message?.let { messages + it } ?: messages,
    isProcessing = false,
    currentStep = "",
)
```

- [ ] **Step 5: Run tests and commit**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.aichat.AiChatUiStateTest.streamingChunkCreatesOrUpdatesSingleAssistantMessage" --tests "com.dailysatori.ui.feature.aichat.AiChatUiStateTest.streamingFinalizationAttachesReferencesAndStopsStreaming"
```

Expected: PASS.

Commit:

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatViewModel.kt app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt
git commit -m "feat: add AI chat streaming state"
```

## Task 4: OpenAI-Compatible Streaming In `AiService`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/ai/AiService.kt`
- Create: `shared/src/commonTest/kotlin/com/dailysatori/service/ai/AiServiceStreamingTest.kt`

- [ ] **Step 1: Write failing tests for stream request and SSE parsing**

Create `AiServiceStreamingTest.kt`:

```kotlin
package com.dailysatori.service.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class AiServiceStreamingTest {
    @Test
    fun streamingRequestSetsStreamTrueAndKeepsTools() {
        val request = buildOpenAiChatCompletionRequest(
            modelName = "gpt-test",
            messages = listOf(buildJsonObject {
                put("role", JsonPrimitive("user"))
                put("content", JsonPrimitive("你好"))
            }),
            tools = listOf(buildJsonObject { put("type", JsonPrimitive("function")) }),
            temperature = 0.7,
            stream = true,
        ).toString()

        assertTrue(request.contains("\"stream\":true"))
        assertTrue(request.contains("\"tools\""))
        assertTrue(request.contains("\"tool_choice\":\"auto\""))
    }

    @Test
    fun parsesOpenAiStreamingContentChunks() {
        val lines = listOf(
            "data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}",
            "data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}",
            "data: [DONE]",
        )

        assertEquals(listOf("你", "好"), lines.mapNotNull(::parseOpenAiStreamingContentChunk))
    }

    @Test
    fun ignoresMalformedStreamingLines() {
        assertEquals(null, parseOpenAiStreamingContentChunk("event: ping"))
        assertEquals(null, parseOpenAiStreamingContentChunk("data: not-json"))
        assertEquals(null, parseOpenAiStreamingContentChunk("data: [DONE]"))
    }
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.ai.AiServiceStreamingTest"
```

Expected: FAIL because `stream` parameter and parser do not exist.

- [ ] **Step 3: Extend request builder with stream flag**

Change `buildOpenAiChatCompletionRequest` signature to:

```kotlin
fun buildOpenAiChatCompletionRequest(
    modelName: String,
    messages: List<JsonObject>,
    tools: List<JsonObject>,
    temperature: Double,
    stream: Boolean = false,
): JsonObject = buildJsonObject {
    put("model", JsonPrimitive(modelName))
    put("messages", JsonArray(messages))
    put("temperature", JsonPrimitive(temperature))
    if (stream) put("stream", JsonPrimitive(true))
    if (tools.isNotEmpty()) {
        put("tools", JsonArray(tools))
        put("tool_choice", JsonPrimitive("auto"))
    }
}
```

Existing callers compile because `stream` has a default value.

- [ ] **Step 4: Add SSE parser helper**

Add to `AiService.kt`:

```kotlin
fun parseOpenAiStreamingContentChunk(line: String): String? {
    val data = line.trim().removePrefix("data:").trim()
    if (!line.trim().startsWith("data:") || data == "[DONE]" || data.isBlank()) return null
    return runCatching {
        val obj = Json { ignoreUnknownKeys = true; isLenient = true }.parseToJsonElement(data).jsonObject
        obj["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("delta")?.jsonObject
            ?.get("content")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotEmpty() }
    }.getOrNull()
}
```

- [ ] **Step 5: Add streaming chat API**

Add imports:

```kotlin
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
```

Add method in `AiService`:

```kotlin
suspend fun chatCompletionStreaming(
    messages: List<JsonObject>,
    apiAddress: String,
    apiToken: String,
    modelName: String,
    provider: String = "openai",
    tools: List<JsonObject> = emptyList(),
    temperature: Double = 0.7,
    onChunk: suspend (String) -> Unit,
): JsonObject? {
    if (!usesOpenAiCompatibleChatApi(provider)) return chatCompletion(messages, apiAddress, apiToken, modelName, provider, tools, temperature)
    var fullText = ""
    client.sse(
        urlString = openAiChatCompletionEndpoint(apiAddress.trim()),
        request = {
            timeout {
                requestTimeoutMillis = aiChatRequestTimeoutMillis()
                socketTimeoutMillis = aiChatRequestTimeoutMillis()
            }
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${apiToken.trim()}")
            setBody(buildOpenAiChatCompletionRequest(modelName.trim(), messages, tools, temperature, stream = true).toString())
        },
    ) {
        incoming.collect { event ->
            val chunk = event.data?.let { parseOpenAiStreamingContentChunk("data: $it") }
            if (!chunk.isNullOrEmpty()) {
                fullText += chunk
                onChunk(chunk)
            }
        }
    }
    return if (fullText.isBlank()) null else buildJsonObject {
        put("choices", JsonArray(listOf(buildJsonObject {
            put("message", buildJsonObject {
                put("role", JsonPrimitive("assistant"))
                put("content", JsonPrimitive(fullText))
            })
        })))
    }
}
```

If Ktor SSE plugin is not configured in the project, replace this step with a non-plugin `client.preparePost(...).execute { response -> response.bodyAsChannel() }` implementation and update imports accordingly. Keep the same public method signature.

- [ ] **Step 6: Run tests and commit**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.ai.AiServiceStreamingTest"
```

Expected: PASS.

Commit:

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/ai/AiService.kt shared/src/commonTest/kotlin/com/dailysatori/service/ai/AiServiceStreamingTest.kt
git commit -m "feat: add OpenAI chat streaming support"
```

## Task 5: Streaming MCP Agent Path

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentService.kt`
- Modify: `shared/src/commonTest/kotlin/com/dailysatori/service/mcp/McpAgentPresentationTest.kt`

- [ ] **Step 1: Write failing source tests for MCP streaming path**

Add to `McpAgentPresentationTest`:

```kotlin
@Test
fun mcpAgentExposesStreamingQueryPathWithFallback() {
    val service = java.io.File("src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentService.kt").readText()

    assertTrue(service.contains("suspend fun processQueryStreaming("))
    assertTrue(service.contains("onChunk: suspend (String) -> Unit"))
    assertTrue(service.contains("chatCompletionStreaming("))
    assertTrue(service.contains("return processQuery(query, onStep)"))
}
```

- [ ] **Step 2: Run test and verify failure**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.mcp.McpAgentPresentationTest.mcpAgentExposesStreamingQueryPathWithFallback"
```

Expected: FAIL because `processQueryStreaming` does not exist.

- [ ] **Step 3: Add streaming entrypoint skeleton with fallback**

In `McpAgentService`, add above `processQuery`:

```kotlin
suspend fun processQueryStreaming(
    query: String,
    onStep: (String, String) -> Unit,
    onChunk: suspend (String) -> Unit,
): McpAgentResult {
    return try {
        processQueryWithStreamingFinalAnswer(query, onStep, onChunk)
    } catch (e: Exception) {
        log.w(e) { "Streaming AI chat failed, falling back to non-streaming path" }
        return processQuery(query, onStep)
    }
}
```

- [ ] **Step 4: Extract streaming implementation from existing process flow**

Add private method by copying the existing `processQuery` logic and changing only final answer calls:

```kotlin
private suspend fun processQueryWithStreamingFinalAnswer(
    query: String,
    onStep: (String, String) -> Unit,
    onChunk: suspend (String) -> Unit,
): McpAgentResult {
    val collectedResults = mutableListOf<McpSearchResult>()
    val localSearch = aiSearchOrchestrator.search(query)
    collectedResults.addAll(localSearch.references)
    var currentStepName: String? = null

    fun updateStep(stepName: String, status: String) {
        if (currentStepName != null && currentStepName != stepName) onStep(currentStepName!!, "completed")
        currentStepName = stepName
        onStep(stepName, status)
    }

    fun completeStep() {
        if (currentStepName != null) onStep(currentStepName!!, "completed")
        onStep("完成", "completed")
    }

    val config = aiConfigService.getDefaultConfig() ?: return processQuery(query, onStep)
    if (config.api_address.isBlank() || config.api_token.isBlank()) return processQuery(query, onStep)
    updateStep("正在理解您的问题...", "processing")
    val messages = mutableListOf<JsonObject>()
    messages.add(buildJsonObject { put("role", "system"); put("content", buildSystemPrompt()) })
    messages.add(buildJsonObject { put("role", "user"); put("content", aiSearchUserContentForQuery(query, localSearch)) })
    val tools = toolRegistry.buildToolDefinitions()
    val privacyMasker = PrivacyMasker()
    val apiUrl = config.api_address.trimEnd('/')
    val apiToken = config.api_token
    val modelName = config.model_name
    val provider = config.provider
    var finalAnswer: String? = null

    for (round in 0 until MAX_TOOL_CALL_ROUNDS) {
        val response = requestChatCompletionWithRetry(messages, apiUrl, apiToken, modelName, provider, tools)
            ?: return processQuery(query, onStep)
        val message = response["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
        val toolCalls = message?.get("tool_calls")?.jsonArray
        if (message == null) { completeStep(); break }
        if (toolCalls != null && toolCalls.isNotEmpty()) {
            updateStep("正在查询数据...", "processing")
            messages.add(buildAssistantToolMessage(message))
            executeToolCalls(toolCalls, messages, collectedResults, privacyMasker)
            updateStep("正在生成回答...", "processing")
        } else {
            finalAnswer = message["content"]?.jsonPrimitive?.contentOrNull
            completeStep()
            break
        }
    }

    if (finalAnswer == null) {
        updateStep("正在生成回答...", "processing")
        finalAnswer = fetchFinalAnswerStreaming(messages, apiUrl, apiToken, modelName, provider, onChunk)
        completeStep()
    } else {
        onChunk(privacyMasker.restore(removeMcpRefsTag(finalAnswer.orEmpty())))
    }

    val answerForRefs = finalAnswer ?: buildFallbackAnswer(query, collectedResults)
    val cleanAnswer = privacyMasker.restore(removeMcpRefsTag(answerForRefs))
    val filteredResults = filterRelevantMcpResults(collectedResults, answerForRefs)
    val preciseResults = preciseSearchResultsForQuery(query, filteredResults)
    val referenceBase = preciseResults.ifEmpty { localSearch.references }
    val searchResults = referencesForAnswer(answerForRefs, referenceBase, collectedResults)
    return McpAgentResult(answer = cleanAnswer, searchResults = searchResults)
}
```

This duplicates some code intentionally for a first safe streaming pass. Refactor only after tests and device verification are green.

- [ ] **Step 5: Add streaming final answer fetcher**

Add below `fetchFinalAnswer`:

```kotlin
private suspend fun fetchFinalAnswerStreaming(
    messages: MutableList<JsonObject>,
    apiUrl: String,
    apiToken: String,
    modelName: String,
    provider: String,
    onChunk: suspend (String) -> Unit,
): String? {
    val response = aiService.chatCompletionStreaming(
        messages = messages,
        apiAddress = apiUrl,
        apiToken = apiToken,
        modelName = modelName,
        provider = provider,
        tools = emptyList(),
        onChunk = onChunk,
    )
    return response?.let {
        it["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.contentOrNull
    }
}
```

- [ ] **Step 6: Run tests and commit**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.mcp.McpAgentPresentationTest.mcpAgentExposesStreamingQueryPathWithFallback"
```

Expected: PASS.

Commit:

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentService.kt shared/src/commonTest/kotlin/com/dailysatori/service/mcp/McpAgentPresentationTest.kt
git commit -m "feat: stream AI chat final answers"
```

## Task 6: Wire Streaming Into ViewModel

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatViewModel.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt`

- [ ] **Step 1: Write failing source test for ViewModel streaming call**

Add to `AiChatUiStateTest`:

```kotlin
@Test
fun viewModelUsesStreamingMcpPathAndPersistsFinalMessage() {
    val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatViewModel.kt").readText()

    assertTrue(source.contains("processQueryStreaming("))
    assertTrue(source.contains("withStreamingAssistantChunk("))
    assertTrue(source.contains("finishedStreamingAssistant("))
    assertTrue(source.contains("persistMessage(finalAssistantMessage)"))
}
```

- [ ] **Step 2: Run test and verify failure**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.aichat.AiChatUiStateTest.viewModelUsesStreamingMcpPathAndPersistsFinalMessage"
```

Expected: FAIL because `sendMessage` still calls `processQuery`.

- [ ] **Step 3: Update `sendMessage` to use streaming path**

Inside `sendMessage`, replace the `val result = mcpAgentService.processQuery(...)` block through assistant message update with:

```kotlin
val steps = mutableListOf<String>()
val assistantMessageId = generateId()
val result = mcpAgentService.processQueryStreaming(
    query = content,
    onStep = { step, status ->
        _state.update { it.copy(currentStep = step) }
        if (status == "completed") steps.add(step)
    },
    onChunk = { chunk ->
        _state.update { it.withStreamingAssistantChunk(assistantMessageId, chunk) }
    },
)
val finalAssistantMessage = buildAssistantMessageOrNull(
    answer = result.answer.ifBlank { aiChatBlankResponseMessage() },
    searchResults = result.searchResults,
    steps = steps,
)?.copy(id = assistantMessageId)
_state.update { current ->
    if (current.messages.any { it.id == assistantMessageId }) {
        current.finishedStreamingAssistant(
            messageId = assistantMessageId,
            finalContent = finalAssistantMessage?.content ?: aiChatBlankResponseMessage(),
            searchResults = finalAssistantMessage?.searchResults.orEmpty(),
            steps = finalAssistantMessage?.steps.orEmpty(),
        )
    } else {
        current.withAssistantMessage(finalAssistantMessage)
    }
}
finalAssistantMessage?.let { persistMessage(finalAssistantMessage) }
```

- [ ] **Step 4: Keep cancellation behavior sane**

In the `catch (_: CancellationException)` block, keep:

```kotlin
_state.update { it.stoppedGeneration() }
```

Do not persist a partial assistant message from this block.

- [ ] **Step 5: Run tests and commit**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.aichat.AiChatUiStateTest"
```

Expected: PASS.

Commit:

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatViewModel.kt app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt
git commit -m "feat: stream AI chat messages in UI"
```

## Task 7: Final Verification And Device Install

**Files:**
- No source changes expected unless verification exposes defects.

- [ ] **Step 1: Run targeted shared tests**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.ai.AiServiceStreamingTest" --tests "com.dailysatori.service.mcp.McpAgentPresentationTest" --tests "com.dailysatori.service.mcp.AiSearchOrchestratorTest" --tests "com.dailysatori.service.mcp.McpSearchResultPersistenceTest"
```

Expected: PASS.

- [ ] **Step 2: Run targeted app tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.aichat.AiChatUiStateTest"
```

Expected: PASS.

- [ ] **Step 3: Run required compile and assemble checks**

Run:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
```

Expected: both commands show `BUILD SUCCESSFUL`.

- [ ] **Step 4: Install and launch on connected device**

Run:

```bash
adb devices
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
adb shell am start -n com.dailysatori/.MainActivity
```

Expected: device is `device`, install succeeds, and activity starts.

- [ ] **Step 5: Manual verification checklist**

On the phone:

1. Ask `最近一条新闻是什么？`.
2. Confirm the user message is a compact right-side bubble, not centered.
3. Confirm the assistant message appears as a left-side bubble without a vertical rail or `AI 回复` label.
4. Ask a longer question and confirm final answer text appears progressively while streaming.
5. Expand references inside the assistant bubble and confirm cards still open.
6. Scroll during generation and confirm the list does not fight user scrolling.

- [ ] **Step 6: Inspect final diff and commit fixes if any**

Run:

```bash
git status --short
git diff --stat
```

Expected: only intended AI chat redesign files are changed. Do not stage unrelated existing dirty files such as `DiaryCard.kt` or `MainContentRhythmTest.kt` unless the user explicitly asks.

If verification required fixes, commit them:

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatViewModel.kt app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt shared/src/commonMain/kotlin/com/dailysatori/service/ai/AiService.kt shared/src/commonTest/kotlin/com/dailysatori/service/ai/AiServiceStreamingTest.kt shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentService.kt shared/src/commonTest/kotlin/com/dailysatori/service/mcp/McpAgentPresentationTest.kt
git commit -m "fix: verify AI chat WeChat streaming redesign"
```

## Plan Self-Review

Spec coverage:

- WeChat left/right bubble layout: Task 1.
- Short user message alignment: Task 1.
- Remove assistant rail and kicker: Task 1.
- Markdown and references inside assistant bubble: Task 1.
- True streaming final answer: Tasks 4, 5, and 6.
- Preserve tool/status steps: Tasks 5 and 6.
- Scroll performance and no repeated reversed list: Task 2.
- Streaming state model: Task 3.
- Device verification: Task 7.

Placeholder scan:

- No incomplete-marker text, vague edge-case instructions, or references to absent functions remain.

Type consistency:

- Plan consistently uses `ChatMessageUi.isStreaming`, `AiChatState.withStreamingAssistantChunk`, `AiChatState.finishedStreamingAssistant`, `AiService.chatCompletionStreaming`, `parseOpenAiStreamingContentChunk`, and `McpAgentService.processQueryStreaming`.

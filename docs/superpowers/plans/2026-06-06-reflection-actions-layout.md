# Reflection Actions Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the scattered book reflection sheet actions with the approved scheme B layout: header capsule actions, guide card, and question rows.

**Architecture:** Keep behavior in `BookReflectionSheet.kt` and reuse existing callbacks. Replace body action rows with small focused composables: header actions, guide card, question rows, and follow-up rows. Preserve the existing fixed input and auto-scroll structure.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, existing Daily Satori theme tokens, Gradle Android unit tests.

---

## File Structure

- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionSheet.kt`: update layout and add focused composables for header action capsule and question rows.
- Modify `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookReflectionStateTest.kt`: add source-level regression tests for scheme B action placement and removal of old scattered actions.
- Use existing `docs/superpowers/specs/2026-06-06-reflection-actions-layout-design.md` as design reference.

### Task 1: Add Regression Tests

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookReflectionStateTest.kt`

- [ ] **Step 1: Add failing tests for scheme B layout**

Add tests asserting the new named composables and labels exist, and the old body action stack labels are gone from `BookReflectionInitialActions`.

```kotlin
@Test
fun reflectionSheetUsesSchemeBHeaderActionsAndQuestionRows() {
    val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionSheet.kt").readText()

    assertTrue(source.contains("BookReflectionHeaderActions("))
    assertTrue(source.contains("BookReflectionGuideCard()"))
    assertTrue(source.contains("BookReflectionQuestionRows("))
    assertTrue(source.contains("补角度"))
    assertTrue(source.contains("举例子"))
    assertTrue(source.contains("反问我"))
    assertTrue(source.contains("继续追问"))
    assertTrue(source.contains("换个角度"))
    assertTrue(source.contains("Text(\"开始\")"))
    assertTrue(source.contains("Text(\"历史\")"))
    assertTrue(source.contains("Text(if (summary.isBlank()) \"沉淀\" else \"更新\")"))
}

@Test
fun reflectionSheetRemovesScatteredBodyActionButtons() {
    val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionSheet.kt").readText()

    assertFalse(source.contains("BookReflectionInitialActions("))
    assertFalse(source.contains("Text(\"开始想\")"))
    assertFalse(source.contains("Text(\"换个问法\")"))
    assertFalse(source.contains("Text(\"看历史\")"))
    assertFalse(source.contains("BookReflectionSettleRow("))
}
```

- [ ] **Step 2: Run tests to verify RED**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.book.BookReflectionStateTest.reflectionSheetUsesSchemeBHeaderActionsAndQuestionRows --tests com.dailysatori.ui.feature.book.BookReflectionStateTest.reflectionSheetRemovesScatteredBodyActionButtons`

Expected: FAIL because the new composables do not exist and old scattered action labels still exist.

### Task 2: Implement Scheme B Layout

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionSheet.kt`

- [ ] **Step 1: Update `BookReflectionScrollableContent` call sites**

Change header item to pass callbacks into the header. Replace the old action item with guide and question rows.

```kotlin
item {
    BookReflectionHeader(
        state = state,
        onStart = { onPromptClick(bookReflectionStartingPrompts().first()) },
        onToggleHistory = onToggleHistory,
        onGenerateSummary = onGenerateSummary,
    )
}
if (!state.showHistory) {
    item { BookReflectionGuideCard() }
    item {
        BookReflectionQuestionRows(
            hasMessages = state.messages.isNotEmpty(),
            isProcessing = state.isProcessing,
            onPromptClick = onPromptClick,
        )
    }
}
```

- [ ] **Step 2: Replace header signature and content**

Change `BookReflectionHeader(state: BookReflectionState)` to include header actions.

```kotlin
@Composable
private fun BookReflectionHeader(
    state: BookReflectionState,
    onStart: () -> Unit,
    onToggleHistory: () -> Unit,
    onGenerateSummary: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("想一想", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (state.messages.isEmpty()) "先让 AI 帮你拆开这一段，再决定是否沉淀。" else "继续追问，或者把有用的部分沉淀下来。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BookReflectionHeaderActions(
                isProcessing = state.isProcessing,
                isSummarizing = state.isSummarizing,
                summary = state.activeSession?.summary.orEmpty(),
                canSettle = bookReflectionShouldShowSettleAction(state.messages, state.showHistory),
                onStart = onStart,
                onToggleHistory = onToggleHistory,
                onGenerateSummary = onGenerateSummary,
            )
        }
        Text(
            text = if (expanded) "收起观点" else "展开观点",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { expanded = !expanded },
        )
        if (expanded) {
            Text(state.viewpointTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("《${state.bookTitle}》", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(state.viewpointContent, style = MaterialTheme.typography.bodySmall)
            if (state.viewpointExample.isNotBlank()) Text(state.viewpointExample, style = MaterialTheme.typography.bodySmall)
        }
    }
}
```

- [ ] **Step 3: Add header action capsule composable**

```kotlin
@Composable
private fun BookReflectionHeaderActions(
    isProcessing: Boolean,
    isSummarizing: Boolean,
    summary: String,
    canSettle: Boolean,
    onStart: () -> Unit,
    onToggleHistory: () -> Unit,
    onGenerateSummary: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(Radius.circular), color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 1.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), modifier = Modifier.padding(Spacing.xxs)) {
            AssistChip(onClick = onStart, enabled = !isProcessing, label = { Text("开始") })
            AssistChip(onClick = onToggleHistory, enabled = !isProcessing, label = { Text("历史") })
            AssistChip(
                onClick = onGenerateSummary,
                enabled = canSettle && !(isProcessing || isSummarizing),
                label = { Text(if (summary.isBlank()) "沉淀" else "更新") },
            )
        }
    }
}
```

- [ ] **Step 4: Add guide card and question rows**

```kotlin
@Composable
private fun BookReflectionGuideCard() {
    Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) {
        Text(
            text = "从一个问题开始。也可以直接在底部输入自己的问题。",
            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BookReflectionQuestionRows(
    hasMessages: Boolean,
    isProcessing: Boolean,
    onPromptClick: (String) -> Unit,
) {
    val prompts = if (hasMessages) {
        listOf(
            "继续追问" to "这个提醒可以怎么落到我今天的一个决定里？",
            "换个角度" to bookReflectionAlternativePrompt(),
        )
    } else {
        listOf(
            "补角度" to bookReflectionStartingPrompts()[0],
            "举例子" to bookReflectionStartingPrompts()[1],
            "反问我" to bookReflectionStartingPrompts()[2],
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        prompts.forEach { (label, prompt) ->
            BookReflectionQuestionRow(label = label, prompt = prompt, enabled = !isProcessing, onClick = { onPromptClick(prompt) })
        }
    }
}

@Composable
private fun BookReflectionQuestionRow(
    label: String,
    prompt: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(Radius.l),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(Spacing.m)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(prompt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
```

- [ ] **Step 5: Remove old scattered composables**

Delete `BookReflectionInitialActions` and `BookReflectionSettleRow` from `BookReflectionSheet.kt`. Keep `BookReflectionSummaryCard`, `BookReflectionStatusCard`, messages, history, and auto-scroll unchanged.

- [ ] **Step 6: Run tests to verify GREEN**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.book.BookReflectionStateTest`

Expected: BUILD SUCCESSFUL.

### Task 3: Verification and Deployment

**Files:**
- No source files changed in this task.

- [ ] **Step 1: Run focused reflection tests**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.book.BookReflectionStateTest --tests com.dailysatori.ui.feature.book.BooksScreenUiTextTest --tests com.dailysatori.ui.feature.book.ViewpointCardLayoutTest`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Compile debug Kotlin**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Build debug APK**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Deploy to connected device**

Run: `adb connect 192.168.2.9:42825 && JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug && adb -s 192.168.2.9:42825 shell am start -n com.dailysatori/.MainActivity`

Expected: APK installs on one device and MainActivity starts.

---

## Self-Review

- Spec coverage: Header capsule, guide card, question rows, old action removal, fixed input preservation, and summary behavior are covered by tasks 1-3.
- Placeholder scan: No TBD/TODO placeholders remain.
- Type consistency: The plan uses existing `BookReflectionState`, `BookReflectionMessageUi`, callback names, theme tokens, and `bookReflectionShouldShowSettleAction` consistently.

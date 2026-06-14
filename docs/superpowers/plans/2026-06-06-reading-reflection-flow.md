# Reading Reflection Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the button-heavy book reflection flow with a simpler reading-first flow: lightweight reading-page entry, progressive reflection actions, and browsable history.

**Architecture:** Keep the existing `BooksScreen`, `ViewpointCard`, `BookReflectionSheet`, and `BookReflectionViewModel` boundaries. The change is UI and interaction state only: `ViewpointCard` exposes a lighter reflection entry, `BookReflectionSheet` presents progressive actions, and existing session history remains powered by `showHistory` and session selection.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Koin ViewModel, existing unit/source tests under `app/src/test`.

---

## File Structure

- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt`: replace the bottom `OutlinedButton` reflection action with a lighter inline/floating-style action component inside the card.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionSheet.kt`: simplify header, replace stacked full-width actions with progressive action UI, and make history cards more timeline-like.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionViewModel.kt`: add a small pure helper for when settle actions should be visible if needed; avoid data-layer changes.
- Modify `app/src/test/kotlin/com/dailysatori/ui/feature/book/ViewpointCardLayoutTest.kt`: protect the lighter reflection action.
- Modify `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookReflectionStateTest.kt`: update label and structure tests for the new progressive reflection UI.
- Modify `app/src/test/kotlin/com/dailysatori/ui/feature/book/BooksScreenUiTextTest.kt`: update expected reflection action label if changed.

---

### Task 1: Make Reading-Page Reflection Entry Lightweight

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/book/ViewpointCardLayoutTest.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/book/BooksScreenUiTextTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt`

- [ ] **Step 1: Write failing tests for the lighter entry**

Add these assertions to `ViewpointCardLayoutTest.viewpointCardCanShowReflectionAction`:

```kotlin
assertTrue(source.contains("ReflectionActionChip("))
assertFalse(source.contains("OutlinedButton(onClick = onReflect, modifier = Modifier.align(Alignment.End))"))
assertTrue(source.contains("Modifier.align(Alignment.End)"))
```

Update `BooksScreenUiTextTest.booksReflectionActionTextIsRestrained`:

```kotlin
assertEquals("想一想", booksReflectionActionText())
```

- [ ] **Step 2: Run tests to verify RED**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.book.ViewpointCardLayoutTest --tests com.dailysatori.ui.feature.book.BooksScreenUiTextTest
```

Expected: FAIL because `ReflectionActionChip` does not exist and `booksReflectionActionText()` still returns `深入想想`.

- [ ] **Step 3: Implement minimal lighter entry**

In `ViewpointCard.kt`, add `Surface` import if missing:

```kotlin
import androidx.compose.material3.Surface
```

Replace the current reflection button block:

```kotlin
OutlinedButton(onClick = onReflect, modifier = Modifier.align(Alignment.End)) {
    Text(booksReflectionActionText())
}
```

with:

```kotlin
ReflectionActionChip(
    onClick = onReflect,
    modifier = Modifier.align(Alignment.End),
)
```

Add this composable near `ViewpointBody`:

```kotlin
@Composable
private fun ReflectionActionChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.circular),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    ) {
        Text(
            text = booksReflectionActionText(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
        )
    }
}
```

In `BooksScreen.kt`, change:

```kotlin
fun booksReflectionActionText(): String = "深入想想"
```

to:

```kotlin
fun booksReflectionActionText(): String = "想一想"
```

- [ ] **Step 4: Run tests to verify GREEN**

Run the same command from Step 2.

Expected: PASS.

---

### Task 2: Simplify Reflection Sheet Header And Initial Actions

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookReflectionStateTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionSheet.kt`

- [ ] **Step 1: Write failing tests for progressive actions**

Replace `reflectionSheetUsesRequiredUserFacingLabels` with assertions matching the new labels:

```kotlin
assertTrue(source.contains("想一想"))
assertTrue(source.contains("开始想"))
assertTrue(source.contains("换个问法"))
assertTrue(source.contains("看历史"))
assertTrue(source.contains("沉淀"))
assertTrue(source.contains("回到当前"))
assertFalse(source.contains("沉淀这一段"))
assertFalse(source.contains("换个角度聊"))
assertFalse(source.contains("Text(state.viewpointTitle"))
```

Update `reflectionSheetHandlesScrollStateAndHistoryActions`:

```kotlin
assertTrue(source.contains("BookReflectionInitialActions("))
assertTrue(source.contains("BookReflectionSettleRow("))
assertTrue(source.contains("BookReflectionHistory(state.sessions, state.isProcessing"))
assertFalse(source.contains("Button(onClick = onGenerateSummary, enabled = !(isProcessing || isSummarizing), modifier = Modifier.fillMaxWidth())"))
```

- [ ] **Step 2: Run tests to verify RED**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.book.BookReflectionStateTest
```

Expected: FAIL because labels/components are still old.

- [ ] **Step 3: Implement simplified header and actions**

In `BookReflectionSheet.kt`, replace `BookReflectionHeader` body with:

```kotlin
private fun BookReflectionHeader(state: BookReflectionState) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("想一想", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            text = "这个观点对我正在做的一个决定有什么提醒？",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

Replace `BookReflectionActions` with two smaller composables:

```kotlin
@Composable
private fun BookReflectionInitialActions(
    isProcessing: Boolean,
    onStart: () -> Unit,
    onNewSegment: () -> Unit,
    onToggleHistory: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Button(onClick = onStart, enabled = !isProcessing, modifier = Modifier.fillMaxWidth()) {
            Text("开始想")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
            AssistChip(onClick = onNewSegment, enabled = !isProcessing, label = { Text("换个问法") })
            AssistChip(onClick = onToggleHistory, enabled = !isProcessing, label = { Text("看历史") })
        }
    }
}

@Composable
private fun BookReflectionSettleRow(
    visible: Boolean,
    summary: String,
    isProcessing: Boolean,
    isSummarizing: Boolean,
    onGenerateSummary: () -> Unit,
) {
    if (!visible) return
    Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            modifier = Modifier.fillMaxWidth().padding(Spacing.s),
        ) {
            Text(
                text = "觉得这段有用时，再沉淀成一条记录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onGenerateSummary, enabled = !(isProcessing || isSummarizing)) {
                Text(if (isSummarizing) "沉淀中" else if (summary.isBlank()) "沉淀" else "更新")
            }
        }
    }
}
```

In the `LazyColumn` item currently calling `BookReflectionActions`, replace it with:

```kotlin
BookReflectionInitialActions(
    isProcessing = state.isProcessing,
    onStart = { onPromptClick(bookReflectionStartingPrompts().first()) },
    onNewSegment = onNewSegment,
    onToggleHistory = onToggleHistory,
)
BookReflectionSettleRow(
    visible = bookReflectionShouldShowSettleAction(state.messages, state.showHistory),
    summary = state.activeSession?.summary.orEmpty(),
    isProcessing = state.isProcessing,
    isSummarizing = state.isSummarizing,
    onGenerateSummary = onGenerateSummary,
)
```

- [ ] **Step 4: Add missing imports**

Ensure `BookReflectionSheet.kt` imports:

```kotlin
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.weight
```

If `weight` import is not needed by this Kotlin version, remove it after compilation feedback.

- [ ] **Step 5: Run tests to verify GREEN**

Run the command from Step 2.

Expected: PASS after minor import adjustments.

---

### Task 3: Add Pure Helper For Settle Visibility

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookReflectionStateTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionViewModel.kt`

- [ ] **Step 1: Write failing tests for settle visibility**

Add to `BookReflectionStateTest`:

```kotlin
@Test
fun settleActionAppearsOnlyForActiveUsefulConversation() {
    assertFalse(bookReflectionShouldShowSettleAction(emptyList(), showHistory = false))
    assertFalse(
        bookReflectionShouldShowSettleAction(
            listOf(BookReflectionMessageUi("1", "user", "问题", 1L, "ready", "")),
            showHistory = false,
        ),
    )
    assertTrue(
        bookReflectionShouldShowSettleAction(
            listOf(
                BookReflectionMessageUi("1", "user", "问题", 1L, "ready", ""),
                BookReflectionMessageUi("2", "assistant", "回答", 2L, "ready", ""),
            ),
            showHistory = false,
        ),
    )
    assertFalse(
        bookReflectionShouldShowSettleAction(
            listOf(
                BookReflectionMessageUi("1", "user", "问题", 1L, "ready", ""),
                BookReflectionMessageUi("2", "assistant", "回答", 2L, "ready", ""),
            ),
            showHistory = true,
        ),
    )
}
```

- [ ] **Step 2: Run tests to verify RED**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.book.BookReflectionStateTest
```

Expected: FAIL because `bookReflectionShouldShowSettleAction` is undefined.

- [ ] **Step 3: Implement helper**

Add to `BookReflectionViewModel.kt` near other pure helpers:

```kotlin
fun bookReflectionShouldShowSettleAction(messages: List<BookReflectionMessageUi>, showHistory: Boolean): Boolean {
    if (showHistory) return false
    return messages.any { it.role == "assistant" && it.status == "ready" && it.content.isNotBlank() }
}
```

- [ ] **Step 4: Run tests to verify GREEN**

Run the command from Step 2.

Expected: PASS.

---

### Task 4: Make History Easier To Browse

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookReflectionStateTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionSheet.kt`

- [ ] **Step 1: Write failing tests for history timeline structure**

Add or update assertions in `reflectionSheetHandlesScrollStateAndHistoryActions`:

```kotlin
assertTrue(source.contains("Text(\"反思历史\""))
assertTrue(source.contains("Text(\"回到当前\""))
assertTrue(source.contains("BookReflectionHistoryItem("))
assertFalse(source.contains("Text(\"查看过程\")"))
assertFalse(source.contains("Text(\"继续聊\")"))
```

- [ ] **Step 2: Run tests to verify RED**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.book.BookReflectionStateTest
```

Expected: FAIL because history still uses two full-width buttons.

- [ ] **Step 3: Implement timeline item**

Replace `BookReflectionHistory` item content with:

```kotlin
item {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text("反思历史", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "回到当前",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(enabled = !isProcessing) { onToggleHistory() },
        )
    }
}
items(sessions, key = { it.id }) { session ->
    BookReflectionHistoryItem(
        session = session,
        isProcessing = isProcessing,
        onClick = {
            onViewSessionProcess(session.id)
            onToggleHistory()
        },
    )
}
```

Add this composable:

```kotlin
@Composable
private fun BookReflectionHistoryItem(
    session: BookReflectionSessionUi,
    isProcessing: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = !isProcessing,
        shape = RoundedCornerShape(Radius.l),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(session.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(session.summary.ifBlank { "还没有沉淀" }, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                text = if (session.summary.isBlank()) "未沉淀" else "已沉淀",
                style = MaterialTheme.typography.labelMedium,
                color = if (session.summary.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
        }
    }
}
```

- [ ] **Step 4: Run tests to verify GREEN**

Run the command from Step 2.

Expected: PASS.

---

### Task 5: Full Verification And Device Install

**Files:**
- Verify all modified files.

- [ ] **Step 1: Run focused tests**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.book.BookReflectionStateTest --tests com.dailysatori.ui.feature.book.ViewpointCardLayoutTest --tests com.dailysatori.ui.feature.book.BooksScreenUiTextTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run required compile**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run required debug build**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Install to connected device if available**

Run:

```bash
adb connect 192.168.2.9:39245
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
adb -s 192.168.2.9:39245 shell am start -n com.dailysatori/.MainActivity
```

Expected: app installs and launches. If the device is unavailable, record the `No connected devices` or connection error.

- [ ] **Step 5: Review diff**

Run:

```bash
git diff -- app/src/main/kotlin/com/dailysatori/ui/feature/book app/src/test/kotlin/com/dailysatori/ui/feature/book docs/superpowers/specs/2026-06-06-reading-reflection-demo-design.md
```

Expected: diff only contains reflection flow changes, tests, and the design spec.

---

## Self-Review

- Spec coverage: reading entry, progressive reflection sheet, delayed settle action, and history browsing each have a task.
- Scope: no repository/schema/API changes; implementation stays in existing Compose UI and pure helpers.
- Test plan: every behavior change has a RED/GREEN source or helper test plus compile/build/device verification.

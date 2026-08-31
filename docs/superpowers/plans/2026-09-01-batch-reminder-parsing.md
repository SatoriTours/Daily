# Batch Reminder Parsing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Parse one explicit natural-language submission into an editable, selectable batch of reminders and save valid selected items without duplicate creation.

**Architecture:** Add a deterministic input splitter and batch interpretation model around the existing single-item local parser. Resolve all locally unsupported fragments in one indexed remote JSON-array request, then expose the ordered batch through a dedicated ViewModel state machine that delegates each confirmed item to the existing repository/coordinator path.

**Tech Stack:** Kotlin Multiplatform, kotlinx.serialization JSON, Kotlin coroutines/Flow, Jetpack Compose Material 3, SQLDelight repository interfaces, kotlin.test, Gradle.

**Spec:** `docs/superpowers/specs/2026-08-31-batch-reminder-parsing-design.md`

## Global Constraints

- Parsing happens only after the user presses the explicit parse action.
- One explicit submission makes at most one remote AI request, regardless of item count.
- Fully local batches make zero remote requests.
- Keep source order by indexed fragment identity; never guess mappings for invalid remote indexes.
- Successful saves are idempotent within the batch; failed items keep their edited state.
- Preserve the existing single-item behavior as a one-item batch.
- Work in the current workspace; do not create a git worktree.

---

### Task 1: Deterministic Batch Splitting and Models

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/reminder/ReminderBatchModels.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/reminder/ReminderInputSplitter.kt`
- Create: `shared/src/commonTest/kotlin/com/dailysatori/service/reminder/ReminderInputSplitterTest.kt`

**Interfaces:**
- Produces: `ReminderInputFragment(index: Int, text: String)`, `ReminderBatchItem`, `ReminderBatchInterpretation`, and `splitReminderInput(text: String): List<ReminderInputFragment>`.
- Consumes: `ReminderDraft` and `ReminderInterpretation`.

- [ ] **Step 1: Write failing splitter/model tests**

```kotlin
@Test fun splitsLinesSemicolonsAndNumberedItemsWithoutSplittingCommaQualifiers() {
    val input = "1. 9月2日提醒我还信用卡，工作时间静音\n2. 9月5日提醒我充值；每年12月20日提醒我续订域名"
    assertEquals(
        listOf("9月2日提醒我还信用卡，工作时间静音", "9月5日提醒我充值", "每年12月20日提醒我续订域名"),
        splitReminderInput(input).map { it.text },
    )
}

@Test fun removesEmptyAndExactDuplicateFragmentsWhileKeepingFirstIndexOrder() {
    assertEquals(
        listOf(0 to "9月2日提醒我还信用卡", 2 to "9月5日提醒我充值"),
        splitReminderInput("9月2日提醒我还信用卡；；9月5日提醒我充值；9月2日提醒我还信用卡").map { it.index to it.text },
    )
}
```

- [ ] **Step 2: Run tests and verify RED**

Run: `./gradlew :shared:testDebugUnitTest --tests '*ReminderInputSplitterTest'`

Expected: compilation fails because the batch types and splitter do not exist.

- [ ] **Step 3: Implement the splitter and immutable batch result types**

```kotlin
data class ReminderInputFragment(val index: Int, val text: String)

data class ReminderBatchItem(
    val id: String,
    val sourceIndex: Int,
    val sourceText: String,
    val interpretation: ReminderInterpretation,
)

data class ReminderBatchInterpretation(
    val batchId: String,
    val normalizedInput: String,
    val items: List<ReminderBatchItem>,
    val failure: String? = null,
)
```

Implement `splitReminderInput` with line/semicolon/numbered-list boundaries, trimming and exact de-duplication. Do not split commas.

- [ ] **Step 4: Run splitter tests and verify GREEN**

Run: `./gradlew :shared:testDebugUnitTest --tests '*ReminderInputSplitterTest'`

Expected: all splitter tests pass.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/reminder/ReminderBatchModels.kt shared/src/commonMain/kotlin/com/dailysatori/service/reminder/ReminderInputSplitter.kt shared/src/commonTest/kotlin/com/dailysatori/service/reminder/ReminderInputSplitterTest.kt
git commit -m "feat: model batch reminder input"
```

### Task 2: Local-First Batch Interpreter With One Remote Call

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/reminder/ReminderTextInterpreter.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/reminder/ReminderBatchCodec.kt`
- Modify: `shared/src/commonTest/kotlin/com/dailysatori/service/reminder/ReminderTextInterpreterTest.kt`

**Interfaces:**
- Consumes: `splitReminderInput`, existing local `ReminderDraftCodec`, `ReminderInterpretationRemote`.
- Produces: `suspend fun ReminderTextInterpreter.interpretBatch(text: String, now: Instant, zone: TimeZone): ReminderBatchInterpretation` and `ReminderInterpretationRemote.interpretBatch(fragments, now, zone)` through one remote completion.

- [ ] **Step 1: Write failing batch interpretation tests**

```kotlin
@Test fun fullyLocalBatchMakesZeroRemoteCalls() = runBlocking {
    val result = interpreter(remote).interpretBatch(
        "9月2日晚上8点提醒我还信用卡；每年12月20日提醒我续订域名",
        now,
        zone,
    )
    assertEquals(listOf("还信用卡", "续订域名"), result.items.map { it.interpretation.draft.content })
    assertEquals(0, remote.calls)
}

@Test fun unresolvedFragmentsShareOneIndexedRemoteRequest() = runBlocking {
    val result = interpreter(remoteWithIndexedArray).interpretBatch("明晚提醒我充值；下周提醒我交账单", now, zone)
    assertEquals(2, result.items.size)
    assertEquals(1, remoteWithIndexedArray.calls)
    assertEquals(listOf(0, 1), result.items.map { it.sourceIndex })
}

@Test fun mixedLocalAndRemoteResultsMergeInSourceOrder() = runBlocking {
    val result = interpreter(remoteForIndexOne).interpretBatch("9月2日提醒我还信用卡；明晚提醒我充值", now, zone)
    assertEquals(listOf("还信用卡", "充值"), result.items.map { it.interpretation.draft.content })
    assertEquals(1, remoteForIndexOne.calls)
}
```

- [ ] **Step 2: Run interpreter tests and verify RED**

Run: `./gradlew :shared:testDebugUnitTest --tests '*ReminderTextInterpreterTest'`

Expected: compilation fails because `interpretBatch` and the indexed response codec do not exist.

- [ ] **Step 3: Implement indexed batch remote parsing and cache**

Add a private local-only entry point that returns `null` without invoking remote. `interpretBatch` must:

```kotlin
val fragments = splitReminderInput(text)
val local = fragments.associateWith { parseLocally(it.text, now, zone) }
val unresolved = fragments.filter { local[it] == null }
val remoteResults = if (unresolved.isEmpty()) emptyMap() else remote.interpretBatch(unresolved, now, zone)
return mergeBySourceIndex(fragments, local, remoteResults)
```

`ReminderBatchCodec` must accept only a JSON array whose elements contain `source_index` plus the existing draft fields. Reject duplicate/out-of-range indexes into per-item failed drafts. Cache by normalized whole input, local date, and zone; cache only batches without batch/item failures.

- [ ] **Step 4: Run interpreter tests and verify GREEN**

Run: `./gradlew :shared:testDebugUnitTest --tests '*ReminderTextInterpreterTest' --tests '*ReminderInputSplitterTest'`

Expected: local, mixed, indexed error, cache, timezone, and single-item regression tests pass.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/reminder/ReminderTextInterpreter.kt shared/src/commonMain/kotlin/com/dailysatori/service/reminder/ReminderBatchCodec.kt shared/src/commonTest/kotlin/com/dailysatori/service/reminder/ReminderTextInterpreterTest.kt
git commit -m "feat: interpret reminder batches in one request"
```

### Task 3: Selectable Batch State and Idempotent Partial Save

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/reminder/ReminderBatchUiState.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/reminder/ReminderViewModel.kt`
- Create: `app/src/test/kotlin/com/dailysatori/ui/feature/reminder/ReminderBatchUiStateTest.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/reminder/ReminderAiParseStateTest.kt`

**Interfaces:**
- Consumes: `ReminderBatchInterpretation`, `ReminderRepository.createConfirmed`, `ReminderCoordinator.recompute`.
- Produces: `ReminderBatchUiState`, `toggleBatchItem`, `removeBatchItem`, `updateBatchItem`, `saveSelectedBatch`, and per-item `PENDING/SAVING/SAVED/FAILED` state.

- [ ] **Step 1: Write failing state and partial-save tests**

```kotlin
@Test fun validItemsStartSelectedAndFailedItemsStartUnselected() {
    val state = ReminderBatchUiState.from(batch(validItem, failedItem))
    assertEquals(setOf(validItem.id), state.selectedIds)
    assertEquals(1, state.selectedCount)
}

@Test fun partialFailureKeepsOnlyFailedItemRetryable() = runTest {
    val result = saveBatch(stateWithTwoSelected, save = { item -> if (item.id == "b") error("disk") else "created-a" })
    assertEquals(BatchSaveStatus.SAVED, result.items.getValue("a").saveStatus)
    assertEquals(BatchSaveStatus.FAILED, result.items.getValue("b").saveStatus)
    assertEquals(1, result.selectedCount)
}

@Test fun savedItemIsSkippedOnSecondSave() = runTest {
    var calls = 0
    val once = saveBatch(stateWithOneSelected) { calls++; "created" }
    saveBatch(once) { calls++; "duplicate" }
    assertEquals(1, calls)
}
```

- [ ] **Step 2: Run app state tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReminderBatchUiStateTest' --tests '*ReminderAiParseStateTest'`

Expected: compilation fails because batch UI state and actions do not exist.

- [ ] **Step 3: Implement batch state transitions and ViewModel orchestration**

Use a single `ReminderBatchUiState` in `ReminderUiState`; remove the single `draft/requiresConfirmation` parse result fields after adapting their tests. `saveSelectedBatch` must snapshot selected unsaved IDs, save sequentially through existing repository/coordinator behavior, and update each item independently. Preserve edited failed items and created reminder IDs.

- [ ] **Step 4: Run app state tests and verify GREEN**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReminderBatchUiStateTest' --tests '*ReminderAiParseStateTest' --tests '*ReminderRouteStateTest'`

Expected: selection, editing, removal, partial failure, idempotency, reset, and one-item regressions pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/reminder/ReminderBatchUiState.kt app/src/main/kotlin/com/dailysatori/ui/feature/reminder/ReminderViewModel.kt app/src/test/kotlin/com/dailysatori/ui/feature/reminder/ReminderBatchUiStateTest.kt app/src/test/kotlin/com/dailysatori/ui/feature/reminder/ReminderAiParseStateTest.kt app/src/test/kotlin/com/dailysatori/ui/feature/reminder/ReminderRouteStateTest.kt
git commit -m "feat: manage selectable reminder batches"
```

### Task 4: Batch Preview, Editing, and Save UI

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/reminder/ReminderBatchPreview.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/reminder/ReminderEditScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/reminder/ReminderUiSourceTest.kt`

**Interfaces:**
- Consumes: `ReminderBatchUiState` and Task 3 ViewModel actions.
- Produces: ordered selectable draft cards, inline edit/remove, `保存选中的 N 条`, partial-result summary, and unsaved-exit confirmation.

- [ ] **Step 1: Write failing UI contract tests**

```kotlin
@Test fun reminderEditorUsesBatchPreviewAndBatchActions() {
    val editor = source("ui/feature/reminder/ReminderEditScreen.kt")
    val preview = source("ui/feature/reminder/ReminderBatchPreview.kt")
    assertTrue(editor.contains("ReminderBatchPreview("))
    assertTrue(preview.contains("onToggleItem"))
    assertTrue(preview.contains("onRemoveItem"))
    assertTrue(preview.contains("onUpdateItem"))
    assertTrue(preview.contains("selectedCount"))
}

@Test fun batchReminderResourcesStayInLocaleParity() {
    val zh = resourceNames("src/main/res/values/strings.xml").filter { it.startsWith("reminder_batch_") }.toSet()
    val en = resourceNames("src/main/res/values-en/strings.xml").filter { it.startsWith("reminder_batch_") }.toSet()
    assertEquals(zh, en)
    assertTrue("reminder_batch_save_selected" in zh)
}
```

- [ ] **Step 2: Run UI tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReminderUiSourceTest' --tests '*ReminderBatchUiStateTest'`

Expected: tests fail because `ReminderBatchPreview` and batch callbacks are absent.

- [ ] **Step 3: Implement the Compose batch workflow**

Replace the single “apply parsed result” section with `ReminderBatchPreview`. Each card must show selection, content, occurrence rule/time, validation/error, edit and remove. Keep the parse button explicit. The bottom action uses `保存选中的 %1$d 条`; disable it for zero selected or while saving. Show a discard dialog when navigating back with unsaved batch items. For a one-item batch, render the same component once.

- [ ] **Step 4: Run feature tests and build**

Run: `./gradlew :shared:testDebugUnitTest --tests '*Reminder*Test' :app:testDebugUnitTest --tests '*Reminder*Test' :app:assembleDebug`

Expected: all reminder parsing, state, route, UI, localization tests pass and Debug APK builds.

- [ ] **Step 5: Install and perform device acceptance**

Run:

```bash
adb connect 192.168.2.120:42997
adb -s 192.168.2.120:42997 install -r app/build/outputs/apk/debug/app-debug.apk
```

Verify on device: enter three strict local reminders separated by newlines and confirm three cards; enter one strict and one ambiguous reminder and confirm ordered cards; deselect and remove cards; open the keyboard and scroll to the bottom save action; save selected cards twice and confirm the reminder list contains no duplicate successful items. Partial repository failure remains covered by the deterministic Task 3 unit test because production storage failure cannot be safely induced on the user's device.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/reminder/ReminderBatchPreview.kt app/src/main/kotlin/com/dailysatori/ui/feature/reminder/ReminderEditScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml app/src/test/kotlin/com/dailysatori/ui/feature/reminder/ReminderUiSourceTest.kt
git commit -m "feat: add batch reminder preview"
```

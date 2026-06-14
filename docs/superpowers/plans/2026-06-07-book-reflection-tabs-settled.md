# Book Reflection Tabs Settled Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the confusing start/history/settle header actions with title-row tabs for `当前`, `历史`, and `已沉淀`, while keeping `沉淀/更新沉淀` as a current-conversation action.

**Architecture:** Store the active reflection view in `BookReflectionState` as an enum-like value. Render header chips as view tabs only, render `已沉淀` as a filtered list of summarized sessions, and render the summary action in the current message flow when the current session can be summarized.

**Tech Stack:** Kotlin, Jetpack Compose Material3, existing unit tests in `BookReflectionStateTest`.

---

### Task 1: Add View State Tests

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookReflectionStateTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionViewModel.kt`

- [ ] **Step 1: Write failing tests**

Add tests that expect:
- `BookReflectionView.Current`, `History`, and `Settled`.
- `bookReflectionHeaderTabState()` selects only the active view.
- `bookReflectionSettledSessions()` returns only sessions with nonblank summary.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.book.BookReflectionStateTest`
Expected: FAIL because the new view model helpers do not exist yet.

- [ ] **Step 3: Implement minimal state helpers**

Add `BookReflectionView`, `reflectionView` to `BookReflectionState`, `BookReflectionHeaderTabState`, `bookReflectionHeaderTabState()`, and `bookReflectionSettledSessions()`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.book.BookReflectionStateTest`
Expected: PASS.

### Task 2: Replace Header Actions With Three View Tabs

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookReflectionStateTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionSheet.kt`

- [ ] **Step 1: Write failing tests**

Add tests that expect:
- Header labels contain `当前`, `历史`, and `已沉淀`.
- Header no longer labels a tab as `开始`.
- Header chips use `tabState.currentSelected`, `historySelected`, and `settledSelected`.
- No `onGenerateSummary` is passed into `BookReflectionHeaderActions`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.book.BookReflectionStateTest`
Expected: FAIL because current implementation still renders `开始` and `沉淀/更新` in the header.

- [ ] **Step 3: Implement header tabs**

Update `BookReflectionHeaderActions` to accept `BookReflectionHeaderTabState` and callbacks for current/history/settled view changes. Render `当前`, `历史`, `已沉淀` as `FilterChip`s using primary selected colors.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.book.BookReflectionStateTest`
Expected: PASS.

### Task 3: Add Settled View and Move Summary Action

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookReflectionStateTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionSheet.kt`

- [ ] **Step 1: Write failing tests**

Add tests that expect:
- Content dispatch uses `BookReflectionView.Settled`.
- Settled view calls a dedicated `BookReflectionSettled(...)` renderer.
- Current messages render `BookReflectionSummaryAction(...)`.
- The summary action text remains `沉淀` or `更新`.
- Settled view shows `已沉淀` sessions and no unsummarized sessions.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.book.BookReflectionStateTest`
Expected: FAIL because the settled renderer and current summary action do not exist.

- [ ] **Step 3: Implement content views**

Dispatch on `state.reflectionView`. Render history as all sessions, settled as `bookReflectionSettledSessions(state.sessions)`, and current as messages plus a `BookReflectionSummaryAction` button when `bookReflectionShouldShowSettleAction(...)` allows it.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.book.BookReflectionStateTest`
Expected: PASS.

### Task 4: Wire View Callbacks and Verify Build

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt`

- [ ] **Step 1: Replace toggle API**

Add `showCurrent()`, `showHistory()`, and `showSettled()` methods. Keep `toggleHistory()` only if tests or callers still require it, but route UI through explicit view methods.

- [ ] **Step 2: Update sheet parameters and caller**

Replace `onToggleHistory` with explicit callbacks in `BookReflectionSheet` and `BooksScreen`.

- [ ] **Step 3: Run targeted tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.book.BookReflectionStateTest`
Expected: PASS.

- [ ] **Step 4: Build debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

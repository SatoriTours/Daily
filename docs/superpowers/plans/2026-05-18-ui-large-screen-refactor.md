# UI Large Screen Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split oversized Android Compose screens into focused internal UI components without changing public screen composable APIs or navigation behavior.

**Architecture:** Keep each public screen composable stable as the route-level integration point. Extract only private or internal feature components into colocated files, with ViewModel wiring, state collection, and navigation callbacks remaining at the current public boundary.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Koin ViewModel integration, Android navigation, Gradle.

---

## File Structure

- `app/src/main/kotlin/com/dailysatori/ui/feature/settings/mcp/McpServerScreen.kt`: keep the public `McpServerScreen` composable stable; retain route-level state and ViewModel wiring.
- `app/src/main/kotlin/com/dailysatori/ui/feature/settings/mcp/McpServerComponents.kt`: create only if extracted private MCP server list, dialog, or form components need a focused home.
- `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt`: keep the public `BooksScreen` composable stable; split book list, empty state, and import/action UI internally.
- `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreenComponents.kt`: create only for private book screen UI pieces.
- `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`: keep the public `UnifiedNewsScreen` composable stable; split feed, filters, status, and summary sections internally.
- `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreenComponents.kt`: create only for private unified news UI pieces.
- `app/src/main/kotlin/com/dailysatori/ui/feature/settings/importing/DataImportScreen.kt`: keep the public `DataImportScreen` composable stable; split import source, progress, and result panels internally.
- `app/src/main/kotlin/com/dailysatori/ui/feature/settings/importing/DataImportComponents.kt`: create only for private import UI pieces.
- `app/src/main/kotlin/com/dailysatori/core/navigation/NavHost.kt`: keep public navigation graph behavior stable; extract repeated route wrapper or argument helpers only if they reduce local complexity.

## Guardrails

- Do not rename public screen composables, route constants, navigation destinations, or ViewModel classes.
- Do not move state collection below leaf components unless the state is already local UI state.
- Do not introduce new design primitives; use existing `com.dailysatori.ui.theme.*` and existing reusable components.
- Keep extracted composables `private` when they remain in the same file, or `internal` when moved to a sibling file in the same package.
- Prefer one screen per commit so regressions are easy to isolate.

## Task 1: Refactor MCP Server Screen Internals

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/mcp/McpServerScreen.kt`
- Optional create: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/mcp/McpServerComponents.kt`

- [ ] **Step 1: Identify route boundary**

Run:

```bash
grep -n "fun McpServerScreen\|@Composable" app/src/main/kotlin/com/dailysatori/ui/feature/settings/mcp/McpServerScreen.kt
```

Expected: the public `McpServerScreen` remains the only external screen entry point.

- [ ] **Step 2: Extract internal UI only**

Move repeated or deeply nested list rows, edit dialogs, delete confirmations, empty states, and form sections into private/internal composables. Keep ViewModel access, snackbar host, navigation callbacks, and public parameters in `McpServerScreen`.

- [ ] **Step 3: Verify stable public API**

Run:

```bash
git diff -- app/src/main/kotlin/com/dailysatori/ui/feature/settings/mcp/McpServerScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/settings/mcp/McpServerComponents.kt
```

Expected: no public `McpServerScreen` signature change; extracted components are private or internal.

## Task 2: Refactor Books Screen Internals

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt`
- Optional create: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreenComponents.kt`

- [ ] **Step 1: Identify route boundary**

Run:

```bash
grep -n "fun BooksScreen\|@Composable" app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt
```

Expected: the public `BooksScreen` remains the stable route composable.

- [ ] **Step 2: Extract internal UI only**

Move book list items, shelf/filter controls, empty/loading states, import action surfaces, and dialog content into private/internal composables. Keep route-level callbacks and ViewModel state handling in `BooksScreen`.

- [ ] **Step 3: Verify stable public API**

Run:

```bash
git diff -- app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreenComponents.kt
```

Expected: no public `BooksScreen` signature change; extracted components are private or internal.

## Task 3: Refactor Unified News Screen Internals

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`
- Optional create: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreenComponents.kt`

- [ ] **Step 1: Identify route boundary**

Run:

```bash
grep -n "fun UnifiedNewsScreen\|@Composable" app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt
```

Expected: the public `UnifiedNewsScreen` remains the stable route composable.

- [ ] **Step 2: Extract internal UI only**

Move feed cards, source filters, refresh/progress indicators, summary panels, error/empty states, and action rows into private/internal composables. Keep data loading triggers, navigation callbacks, and ViewModel state collection in `UnifiedNewsScreen`.

- [ ] **Step 3: Verify stable public API**

Run:

```bash
git diff -- app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreenComponents.kt
```

Expected: no public `UnifiedNewsScreen` signature change; extracted components are private or internal.

## Task 4: Refactor Data Import Screen Internals

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/importing/DataImportScreen.kt`
- Optional create: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/importing/DataImportComponents.kt`

- [ ] **Step 1: Identify route boundary**

Run:

```bash
grep -n "fun DataImportScreen\|@Composable" app/src/main/kotlin/com/dailysatori/ui/feature/settings/importing/DataImportScreen.kt
```

Expected: the public `DataImportScreen` remains the stable route composable.

- [ ] **Step 2: Extract internal UI only**

Move import source pickers, selected-file display, progress/result panels, destructive confirmations, and help text into private/internal composables. Keep permission handling, launcher wiring, ViewModel events, and public callbacks in `DataImportScreen`.

- [ ] **Step 3: Verify stable public API**

Run:

```bash
git diff -- app/src/main/kotlin/com/dailysatori/ui/feature/settings/importing/DataImportScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/settings/importing/DataImportComponents.kt
```

Expected: no public `DataImportScreen` signature change; extracted components are private or internal.

## Task 5: Simplify Navigation Host Internals

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/core/navigation/NavHost.kt`

- [ ] **Step 1: Identify repeated navigation wiring**

Run:

```bash
grep -n "composable\|navigation\|navigate" app/src/main/kotlin/com/dailysatori/core/navigation/NavHost.kt
```

Expected: repeated route wrappers, argument parsing, or callback adapters are visible before editing.

- [ ] **Step 2: Extract only local helpers**

Extract repeated private helpers inside `NavHost.kt` only when they reduce duplication without hiding route definitions. Keep destination names, route arguments, start destination, and screen composable calls behaviorally stable.

- [ ] **Step 3: Verify navigation graph stability**

Run:

```bash
git diff -- app/src/main/kotlin/com/dailysatori/core/navigation/NavHost.kt
```

Expected: route strings, destination coverage, and screen composable calls are unchanged except for private helper use.

## Verification

- [ ] **Compile after each screen or navigation task**

Run:

```bash
./gradlew :app:compileDebugKotlin --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Run focused UI smoke checks on device after the full stage**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
adb shell am start -n com.dailysatori/.MainActivity
```

Expected: app launches; MCP settings, books, unified news, data import, and primary navigation paths render without crashes.

- [ ] **Check patch hygiene**

Run:

```bash
git diff --check
```

Expected: no whitespace errors.

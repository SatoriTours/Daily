# Unified News Inline Citations And History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make unified news citations clickable in the Markdown body, remove duplicate titles, and show historical summaries in a scrollable feed.

**Architecture:** Keep the existing `UnifiedNewsViewModel` and `UnifiedNewsScreen` boundary. Add small pure formatting helpers in the unified news UI package so tests can verify citation-link rewriting, title cleanup, and date labels without Compose instrumentation.

**Tech Stack:** Kotlin, Jetpack Compose, MikePenz Multiplatform Markdown Renderer, SQLDelight query results, Android unit tests.

---

### Task 1: Markdown Body Cleanup And Inline Citation Links

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsContentFormat.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/CitationText.kt`

- [ ] **Step 1: Write failing tests for cleanup and citation links**

Add tests that require stripping the leading generated title and converting `[C1]` into a custom Markdown link.

- [ ] **Step 2: Run tests and confirm they fail**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsContentFormatRemovesGeneratedTitle --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsContentFormatConvertsCitationsToLinks --no-configuration-cache`

Expected: FAIL because helpers do not exist.

- [ ] **Step 3: Implement helpers and use them in `CitationText`**

Create helpers:
- `displayUnifiedNewsMarkdown(content: String): String`
- `unifiedNewsMarkdownWithCitationLinks(content: String): String`
- `unifiedNewsCitationUrl(citation: String): String`

Use `unifiedNewsMarkdownWithCitationLinks(displayUnifiedNewsMarkdown(content))` as Markdown content.

### Task 2: Summary Feed Instead Of Single Selected Summary

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`

- [ ] **Step 1: Write failing tests for history feed**

Add tests requiring `UnifiedNewsContent` to receive `summaries`, render `items(summaries`, and load sources per summary.

- [ ] **Step 2: Run tests and confirm they fail**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsSummaryRendersScrollableHistory --no-configuration-cache`

Expected: FAIL because the screen only renders `selectedSummary`.

- [ ] **Step 3: Implement history feed**

Load `sourcesBySummaryId` in state. Render each success summary as a feed item with a date/time label and its own citation source map.

### Task 3: Prompt Avoids Duplicate Heading

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsPrompt.kt`

- [ ] **Step 1: Write failing test for prompt title rule**

Add a test requiring the prompt to not ask for `# 今日统一新闻总结`.

- [ ] **Step 2: Run test and confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsPromptDoesNotAskForDuplicateTitle --no-configuration-cache`

Expected: FAIL because prompt currently asks for that heading.

- [ ] **Step 3: Update prompt structure**

Ask AI to output only sections such as `## 重点速览` and `## 值得关注`, without the top-level title.

### Task 4: Verification

**Files:**
- No new files.

- [ ] **Step 1: Run focused tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest --no-configuration-cache`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Compile and assemble**

Run: `./gradlew :app:compileDebugKotlin --no-configuration-cache && ./gradlew :app:assembleDebug --no-configuration-cache`

Expected: BUILD SUCCESSFUL for both.

- [ ] **Step 3: Install and launch**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug --no-configuration-cache && adb shell am start -n com.dailysatori/.MainActivity`

Expected: install succeeds and app starts.

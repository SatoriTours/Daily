# Unified News Readable Content Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make unified news summaries readable as Markdown and increase daily source coverage by prompting from full crayfish files instead of truncated list previews.

**Architecture:** Keep the existing unified news feature boundaries. UI rendering changes stay inside `CitationText.kt` and `UnifiedNewsScreen.kt`; source collection changes stay inside `UnifiedNewsSummaryService.kt` using the existing `CrayfishNewsService.fetchNewsFile()` API.

**Tech Stack:** Kotlin Multiplatform shared module, Android Jetpack Compose, existing `com.mikepenz.markdown.m3.Markdown`, SQLDelight-backed repositories, Kotlin unit tests.

---

### Task 1: Markdown-First Summary Rendering

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/CitationText.kt`

- [ ] **Step 1: Write the failing test**

Add a test asserting `CitationText.kt` uses Markdown rendering and no longer lays out the full article with `FlowRow`.

```kotlin
@Test
fun unifiedNewsSummaryUsesMarkdownRendererForReadableFormatting() {
    val citationText = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/CitationText.kt").readText()

    assertTrue(citationText.contains("com.mikepenz.markdown.m3.Markdown"))
    assertTrue(citationText.contains("Markdown("))
    assertFalse(citationText.contains("FlowRow("))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsSummaryUsesMarkdownRendererForReadableFormatting --no-configuration-cache`

Expected: FAIL because `CitationText.kt` still imports/uses `FlowRow` and does not use `Markdown`.

- [ ] **Step 3: Implement minimal Markdown renderer**

Update `CitationText.kt` so it renders the whole summary with the existing Markdown renderer. Keep `splitCitationTokens()` for existing tests and future citation support, but stop using `FlowRow` for document layout.

```kotlin
@Composable
fun CitationText(
    content: String,
    modifier: Modifier = Modifier,
    onCitationClick: (String) -> Unit = {},
) {
    SelectionContainer(modifier = modifier) {
        Markdown(
            content = content,
            typography = MarkdownStyles.typography(),
            padding = MarkdownStyles.padding(),
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsSummaryUsesMarkdownRendererForReadableFormatting --no-configuration-cache`

Expected: PASS.

### Task 2: Full Crayfish Source Content

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt`

- [ ] **Step 1: Write the failing test**

Add a test asserting the collector fetches matching crayfish files and uses detail content.

```kotlin
@Test
fun unifiedNewsServiceFetchesFullCrayfishFilesForPromptContent() {
    val source = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt").readText()

    assertTrue(source.contains("fetchNewsFile"))
    assertTrue(source.contains("contentForUnifiedPrompt"))
    assertTrue(source.contains("detail.content"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsServiceFetchesFullCrayfishFilesForPromptContent --no-configuration-cache`

Expected: FAIL because the collector only uses `CrayfishNewsListItem.preview`.

- [ ] **Step 3: Implement full-file fetch**

Change `collectCrayfishCategory()` from a direct list transform to: fetch the list, filter by window, then for each matching item call `crayfishNewsService.fetchNewsFile(config, category, item.filename)`. If detail fetch fails, keep the preview and add a warning.

The source item content must prefer `detail.content`, then `detail.sections.values.joinToString("\n\n")`, then `detail.preview`, then `item.preview`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsServiceFetchesFullCrayfishFilesForPromptContent --no-configuration-cache`

Expected: PASS.

### Task 3: Final Verification

**Files:**
- No new files.

- [ ] **Step 1: Run focused tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest --no-configuration-cache`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run compile check**

Run: `./gradlew :app:compileDebugKotlin --no-configuration-cache`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run assemble check**

Run: `./gradlew :app:assembleDebug --no-configuration-cache`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Install and launch**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug --no-configuration-cache && adb shell am start -n com.dailysatori/.MainActivity`

Expected: install succeeds and app starts.

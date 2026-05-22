# News Books Density Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tighten the remaining news/books title-to-content gaps and make books body text match the readable news/diary rhythm.

**Architecture:** Keep the global scaffold unchanged. Apply targeted density changes in `UnifiedNewsScreen`, `BooksScreen`, and `ViewpointCard`, guarded by source-contract tests that verify the intended padding and typography tokens.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Android Gradle build.

---

## File Structure

- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`
  - Tighten news list top padding and transient refresh/generating state top padding.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt`
  - Use a compact wrapper height around the existing `AppTopBar` and reduce viewpoint card outer top padding.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt`
  - Reduce internal vertical gaps and make body Markdown explicitly use the shared card/body typography.
- Modify: `app/src/test/kotlin/com/dailysatori/ui/theme/MainContentRhythmTest.kt`
  - Add source-contract assertions for news/books density.

---

### Task 1: Tighten News First Content Gap

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/theme/MainContentRhythmTest.kt`

- [ ] **Step 1: Add failing source-contract assertions**

In `MainContentRhythmTest.kt`, add this test inside `MainContentRhythmTest`:

```kotlin
    @Test
    fun newsSummaryStartsCloserToTitleBar() {
        val news = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()

        assertTrue(news.contains("contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.m)"))
        assertFalse(news.contains("contentPadding = PaddingValues(Spacing.m)"))
        assertTrue(news.contains(".padding(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.s)"))
        assertTrue(news.contains(".padding(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.s)"))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.theme.MainContentRhythmTest`

Expected: FAIL because `UnifiedNewsScreen.kt` still uses `PaddingValues(Spacing.m)` for the list.

- [ ] **Step 3: Implement news density changes**

In `UnifiedNewsScreen.kt`, replace:

```kotlin
contentPadding = PaddingValues(Spacing.m),
```

with:

```kotlin
contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.m),
```

In `UnifiedNewsRefreshMessage`, replace:

```kotlin
modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
```

with:

```kotlin
modifier = Modifier.fillMaxWidth().padding(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.s),
```

In `UnifiedNewsGeneratingSkeleton`, replace:

```kotlin
modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
```

with:

```kotlin
modifier = Modifier.fillMaxWidth().padding(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.s),
```

- [ ] **Step 4: Run focused test and compile**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.theme.MainContentRhythmTest`

Expected: PASS.

Run: `./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit news density change**

Run:

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt app/src/test/kotlin/com/dailysatori/ui/theme/MainContentRhythmTest.kt
git commit -m "fix: tighten news title content gap"
```

---

### Task 2: Tighten Books Header And Reading Card Rhythm

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/theme/MainContentRhythmTest.kt`

- [ ] **Step 1: Add failing source-contract assertions**

Add this test inside `MainContentRhythmTest`:

```kotlin
    @Test
    fun booksReadingUsesCompactHeaderAndReadableBody() {
        val books = File("src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt").readText()
        val viewpoint = File("src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt").readText()

        assertTrue(books.contains("Modifier.height(Height.listItemSmall)"))
        assertTrue(books.contains("modifier = Modifier.padding(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.m)"))
        assertFalse(books.contains("modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.m)"))
        assertTrue(viewpoint.contains("Spacer(modifier = Modifier.height(Spacing.s))"))
        assertFalse(viewpoint.contains("Spacer(modifier = Modifier.height(Spacing.l))"))
        assertTrue(viewpoint.contains("typography = MarkdownStyles.cardTypography()"))
        assertTrue(viewpoint.contains("padding = MarkdownStyles.cardPadding()"))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.theme.MainContentRhythmTest`

Expected: FAIL because Books does not yet use compact header height or reduced viewpoint padding.

- [ ] **Step 3: Implement compact Books header wrapper**

In `BooksScreen.kt`, add import:

```kotlin
import com.dailysatori.ui.theme.Height
```

Wrap the existing `AppTopBar(...)` call in a compact `Box`:

```kotlin
        Box(modifier = Modifier.fillMaxWidth().height(Height.listItemSmall)) {
            AppTopBar(
                title = "读书",
                showBack = false,
                myNavigationLabel = "我的",
                onMyNavigationClick = onMyClick,
                actions = {
                    IconButton(onClick = { inlineMode = inlineMode.toggleAdd() }) {
                        Icon(Icons.Default.Add, contentDescription = booksAddActionContentDescription())
                    }
                    IconButton(onClick = { inlineMode = inlineMode.toggleContentSearch() }) {
                        Icon(Icons.Default.Search, contentDescription = booksContentSearchActionContentDescription())
                    }
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(booksFilterMenuText()) },
                                leadingIcon = { Icon(Icons.Default.FilterList, null) },
                                onClick = { showBookSheet = true; showMenu = false },
                            )
                            DropdownMenuItem(
                                text = { Text("随机") },
                                leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                onClick = { viewModel.shuffle(); showMenu = false },
                            )
                            if (state.currentBookId != null) {
                                DropdownMenuItem(
                                    text = { Text("删除") },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showDeleteDialog = state.books.find { it.id == state.currentBookId }
                                        showMenu = false
                                    },
                                )
                            }
                        }
                    }
                },
            )
        }
```

Then replace ViewpointCard outer padding:

```kotlin
modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.m),
```

with:

```kotlin
modifier = Modifier.padding(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.m),
```

- [ ] **Step 4: Implement ViewpointCard internal rhythm**

In `ViewpointCard.kt`, replace the spacer after title:

```kotlin
Spacer(modifier = Modifier.height(Spacing.m))
```

with:

```kotlin
Spacer(modifier = Modifier.height(Spacing.s))
```

Replace both `Spacing.l` spacers with `Spacing.s`:

```kotlin
Spacer(modifier = Modifier.height(Spacing.s))
```

Keep both Markdown blocks on:

```kotlin
typography = MarkdownStyles.cardTypography(),
padding = MarkdownStyles.cardPadding(),
```

- [ ] **Step 5: Run focused test and compile**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.theme.MainContentRhythmTest`

Expected: PASS.

Run: `./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit books density change**

Run:

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt app/src/test/kotlin/com/dailysatori/ui/theme/MainContentRhythmTest.kt
git commit -m "fix: tighten books reading density"
```

---

### Task 3: Final Verification And Install

**Files:**
- Verify all modified source/test files.

- [ ] **Step 1: Run focused rhythm test**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.theme.MainContentRhythmTest`

Expected: PASS.

- [ ] **Step 2: Run full tests**

Run: `./gradlew :app:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Compile app**

Run: `./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Install and launch if device reachable**

Run: `adb connect 192.168.2.11:38915`

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`

Run: `adb -s 192.168.2.11:38915 shell am start -n com.dailysatori/.MainActivity`

Expected: connect succeeds, install `BUILD SUCCESSFUL`, launch prints `Starting: Intent`.

- [ ] **Step 5: Check git status**

Run: `git status --short`

Expected: no output.

---

## Self-Review

- Spec coverage: Task 1 handles news gaps; Task 2 handles books title/header/body density; Task 3 verifies tests, compile, install, launch.
- Placeholder scan: no TBD/TODO placeholders.
- Type/path consistency: all file paths and token names match current project files.

# Book Viewpoint Immersive Reading Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the boxed book viewpoint reader with a cleaner immersive reading layout that removes redundant book labels and bottom previous/next controls.

**Architecture:** Keep `BooksScreen` responsible for pager state and app-level actions, but simplify its reader flow to only render the `HorizontalPager`. Keep the existing `ViewpointCard` composable name to avoid call-site churn, but turn it into a direct scrollable content reader with helper functions for display title cleanup and book metadata formatting.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Compose Markdown renderer, Gradle JVM unit tests.

---

## File Structure

- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt`: remove progress strip and bottom navigation calls from the reader flow; pass book title, author, page, and total to `ViewpointCard`.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt`: remove `Card` container; render direct page content with title/progress row, right-aligned book metadata, Markdown content, and example section.
- Modify `app/src/test/kotlin/com/dailysatori/ui/feature/book/BooksScreenUiTextTest.kt`: update reader behavior tests for immersive layout.
- Modify `app/src/test/kotlin/com/dailysatori/ui/feature/book/ViewpointCardLayoutTest.kt`: add helper-function tests for title cleanup and metadata formatting.
- Modify `app/src/test/kotlin/com/dailysatori/ui/theme/MainContentRhythmTest.kt`: update source-text expectations that currently require card chrome.

## Task 1: Add Tests For Immersive Reader Contract

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/book/BooksScreenUiTextTest.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/book/ViewpointCardLayoutTest.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/theme/MainContentRhythmTest.kt`

- [ ] **Step 1: Update BooksScreen reader test**

In `BooksScreenUiTextTest.kt`, replace `immersiveReaderExposesCurrentBookProgressAndBottomNavigation()` with:

```kotlin
@Test
fun immersiveReaderRemovesChromeAndKeepsSwipePaging() {
    val source = File("src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt").readText()
    val readerFlow = source.substringAfter("Column(modifier = Modifier.fillMaxSize()) {").substringBefore("if (inlineMode != BooksInlineMode.Reading)")

    assertEquals("读书", booksReaderTitle(null, null))
    assertEquals("原则", booksReaderTitle("原则", "Ray Dalio"))
    assertEquals("3 / 18", booksReadingProgressText(page = 2, total = 18))
    assertEquals("更多读书操作", booksMoreActionsContentDescription())
    assertTrue(readerFlow.contains("HorizontalPager("))
    assertTrue(readerFlow.contains("ViewpointCard("))
    assertTrue(readerFlow.contains("page = pagerState.currentPage"))
    assertTrue(readerFlow.contains("total = state.viewpoints.size"))
    assertTrue(readerFlow.contains("bookTitle = currentBook?.title.orEmpty()"))
    assertTrue(readerFlow.contains("author = currentBook?.author.orEmpty()"))
    assertFalse(readerFlow.contains("BookReadingProgressStrip("))
    assertFalse(readerFlow.contains("BookReadingNavigationBar("))
    assertFalse(readerFlow.contains("pagerState.animateScrollToPage"))
}
```

Also add `import kotlin.test.assertFalse` to the imports if it is not present.

- [ ] **Step 2: Expand ViewpointCard layout tests**

Replace `ViewpointCardLayoutTest.kt` with:

```kotlin
package com.dailysatori.ui.feature.book

import kotlin.test.Test
import kotlin.test.assertEquals

class ViewpointCardLayoutTest {
    @Test
    fun shortViewpointCardsStayTopAligned() {
        assertEquals(true, viewpointCardFillsAvailableHeight(fillAvailableHeight = true))
        assertEquals(false, viewpointCardFillsAvailableHeight(fillAvailableHeight = false))
        assertEquals(true, viewpointCardContentStartsAtTop())
    }

    @Test
    fun viewpointTitleRemovesCurrentBookPrefix() {
        assertEquals(
            "用事实材料校正抽象判断中的理解偏差。",
            viewpointDisplayTitle("毛泽东选集（全四卷）：用事实材料校正抽象判断中的理解偏差。", "毛泽东选集（全四卷）"),
        )
        assertEquals(
            "用事实材料校正抽象判断中的理解偏差。",
            viewpointDisplayTitle("《毛泽东选集（全四卷）》：用事实材料校正抽象判断中的理解偏差。", "毛泽东选集（全四卷）"),
        )
        assertEquals(
            "组织先遇到局势混乱。",
            viewpointDisplayTitle("《毛泽东选集（全四卷）》: 组织先遇到局势混乱。", "毛泽东选集（全四卷）"),
        )
        assertEquals(
            "不要用情绪代替事实",
            viewpointDisplayTitle("不要用情绪代替事实", "毛泽东选集（全四卷）"),
        )
    }

    @Test
    fun viewpointBookLineFormatsTitleAndAuthor() {
        assertEquals("《毛泽东选集（全四卷）》 · 毛泽东", viewpointBookLine("毛泽东选集（全四卷）", "毛泽东"))
        assertEquals("《毛泽东选集（全四卷）》", viewpointBookLine("毛泽东选集（全四卷）", ""))
        assertEquals("", viewpointBookLine("", "毛泽东"))
    }
}
```

- [ ] **Step 3: Update MainContentRhythm source expectations**

In `MainContentRhythmTest.kt`, update the book/viewpoint expectations in `booksReadingUsesCompactHeaderAndReadableBody()` and `mainCardsUseSharedPaddingAndMarkdownPreset()`:

Replace:

```kotlin
assertTrue(viewpoint.contains("Spacer(modifier = Modifier.height(Spacing.s))"))
assertFalse(viewpoint.contains("Spacer(modifier = Modifier.height(Spacing.l))"))
```

with:

```kotlin
assertTrue(viewpoint.contains("verticalScroll(rememberScrollState())"))
assertFalse(viewpoint.contains("Card("))
```

Replace:

```kotlin
assertTrue(viewpoint.contains("shape = RoundedCornerShape(Radius.l)"))
assertTrue(viewpoint.contains("border = BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outline)"))
```

with:

```kotlin
assertFalse(viewpoint.contains("shape = RoundedCornerShape(Radius.l)"))
assertFalse(viewpoint.contains("border = BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outline)"))
```

- [ ] **Step 4: Run tests and confirm RED**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.book.BooksScreenUiTextTest' --tests 'com.dailysatori.ui.feature.book.ViewpointCardLayoutTest' --tests 'com.dailysatori.ui.theme.MainContentRhythmTest'
```

Expected: FAIL because `BooksScreen` still renders progress/navigation chrome, `ViewpointCard` still uses `Card`, and helper functions do not exist yet.

## Task 2: Implement Direct Viewpoint Reader Content

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt`

- [ ] **Step 1: Replace card imports and signature**

In `ViewpointCard.kt`, remove these imports:

```kotlin
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.theme.BorderWidth
import com.dailysatori.ui.theme.Radius
```

Add these imports:

```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
```

Change the function signature to:

```kotlin
fun ViewpointCard(
    title: String,
    content: String,
    example: String,
    bookTitle: String,
    author: String,
    page: Int,
    total: Int,
    modifier: Modifier = Modifier,
    fillAvailableHeight: Boolean = false,
)
```

- [ ] **Step 2: Replace ViewpointCard implementation**

Replace the body of `ViewpointCard` with:

```kotlin
val contentModifier = if (fillAvailableHeight) modifier.fillMaxWidth().fillMaxHeight() else modifier.fillMaxWidth()
Column(
    modifier = contentModifier
        .verticalScroll(rememberScrollState())
        .padding(horizontal = Spacing.l, vertical = Spacing.m),
    verticalArrangement = Arrangement.spacedBy(Spacing.s),
) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = viewpointDisplayTitle(title, bookTitle),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(Spacing.s))
        Text(
            text = booksReadingProgressText(page, total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    viewpointBookLine(bookTitle, author).takeIf { it.isNotBlank() }?.let { line ->
        Text(
            text = line,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Markdown(
        content = content,
        typography = MarkdownStyles.bookTypography(),
        padding = MarkdownStyles.cardPadding(),
    )

    if (example.isNotBlank()) {
        Text(
            "案例",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Markdown(
            content = example,
            typography = MarkdownStyles.bookTypography(),
            padding = MarkdownStyles.cardPadding(),
        )
    }
}
```

- [ ] **Step 3: Add helper functions**

At the bottom of `ViewpointCard.kt`, add:

```kotlin
fun viewpointDisplayTitle(title: String, bookTitle: String): String {
    val cleanTitle = title.trim()
    val cleanBook = bookTitle.trim()
    if (cleanBook.isBlank()) return cleanTitle

    val plainPrefixes = listOf("$cleanBook：", "$cleanBook:")
    val quotedPrefixes = listOf("《$cleanBook》：", "《$cleanBook》:")
    val prefix = (quotedPrefixes + plainPrefixes).firstOrNull { cleanTitle.startsWith(it) }
    return prefix?.let { cleanTitle.removePrefix(it).trim() } ?: cleanTitle
}

fun viewpointBookLine(bookTitle: String, author: String): String {
    val cleanTitle = bookTitle.trim()
    if (cleanTitle.isBlank()) return ""
    val cleanAuthor = author.trim()
    return if (cleanAuthor.isBlank()) "《$cleanTitle》" else "《$cleanTitle》 · $cleanAuthor"
}
```

- [ ] **Step 4: Run ViewpointCard tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.book.ViewpointCardLayoutTest'
```

Expected: PASS.

## Task 3: Simplify BooksScreen Reader Flow

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt`

- [ ] **Step 1: Remove unused navigation imports**

In `BooksScreen.kt`, remove:

```kotlin
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.TextButton
```

- [ ] **Step 2: Remove progress strip and bottom navigation from reader flow**

Inside the `Column(modifier = Modifier.fillMaxSize())` in the non-empty reader branch, replace the whole block:

```kotlin
BookReadingProgressStrip(
    bookTitle = currentBook?.title,
    author = currentBook?.author,
    page = pagerState.currentPage,
    total = state.viewpoints.size,
)
HorizontalPager(
    state = pagerState,
    modifier = Modifier.weight(1f).fillMaxWidth(),
) { page ->
    val vp = state.viewpoints[page]
    val bookTitle = if (currentBook != null) "《${currentBook.title}》 · ${currentBook.author}" else ""

    ViewpointCard(
        title = vp.title,
        content = vp.content,
        example = vp.example,
        bookTitle = bookTitle,
        modifier = Modifier.padding(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.s),
        fillAvailableHeight = true,
    )
}
BookReadingNavigationBar(
    pagerState = pagerState,
    total = state.viewpoints.size,
    onPrevious = {
        scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) }
    },
    onNext = {
        scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(state.viewpoints.lastIndex)) }
    },
)
```

with:

```kotlin
HorizontalPager(
    state = pagerState,
    modifier = Modifier.weight(1f).fillMaxWidth(),
) { page ->
    val vp = state.viewpoints[page]

    ViewpointCard(
        title = vp.title,
        content = vp.content,
        example = vp.example,
        bookTitle = currentBook?.title.orEmpty(),
        author = currentBook?.author.orEmpty(),
        page = pagerState.currentPage,
        total = state.viewpoints.size,
        fillAvailableHeight = true,
    )
}
```

Also remove the now-unused line:

```kotlin
val scope = rememberCoroutineScope()
```

from the reader branch if no longer needed there. Keep other `rememberCoroutineScope()` usages for sheets.

- [ ] **Step 3: Remove obsolete private composables and text helpers**

Delete these private composables from `BooksScreen.kt`:

```kotlin
private fun BookReadingProgressStrip(...)
private fun BookReadingNavigationBar(...)
```

Delete these no-longer-used helper functions if no tests or code reference them after Task 1 changes:

```kotlin
fun booksReaderSubtitle(title: String?, author: String?): String = author?.takeIf { it.isNotBlank() } ?: ""
fun booksPreviousViewpointText(): String = "上一条"
fun booksNextViewpointText(): String = "下一条"
```

Keep:

```kotlin
fun booksReadingProgressText(page: Int, total: Int): String = "${(page + 1).coerceAtMost(total.coerceAtLeast(1))} / ${total.coerceAtLeast(1)}"
```

- [ ] **Step 4: Run BooksScreen tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.book.BooksScreenUiTextTest' --tests 'com.dailysatori.ui.theme.MainContentRhythmTest'
```

Expected: PASS.

## Task 4: Full Verification

**Files:**
- No code changes unless verification reveals a direct regression.

- [ ] **Step 1: Run focused book tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.book.BooksScreenUiTextTest' --tests 'com.dailysatori.ui.feature.book.ViewpointCardLayoutTest' --tests 'com.dailysatori.ui.theme.MainContentRhythmTest'
```

Expected: PASS.

- [ ] **Step 2: Run full CI unit test command**

Run:

```bash
./gradlew :app:testDebugUnitTest :shared:testDebugUnitTest --no-configuration-cache
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run compile check**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run debug build**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Install and launch if a device is connected**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
adb shell am start -n com.dailysatori/.MainActivity
```

Expected: install succeeds on connected device and app launch prints `Starting: Intent { cmp=com.dailysatori/.MainActivity }`.

## Self-Review Notes

- Spec coverage: Task 1 locks removal of chrome, title cleanup, and metadata formatting. Task 2 removes the card and implements direct content. Task 3 removes progress/nav chrome from `BooksScreen`. Task 4 covers tests/build/install.
- Placeholder scan: no placeholder tasks remain; commands and expected results are explicit.
- Type consistency: `ViewpointCard` gains `author`, `page`, and `total`; Task 3 updates the only call site in `BooksScreen`.

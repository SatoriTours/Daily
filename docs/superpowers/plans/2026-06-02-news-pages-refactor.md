# News Pages Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the three news pages into clearer, shared, performance-conscious Compose code without changing primary behavior or visual identity.

**Architecture:** Add small business-neutral news UI primitives under `ui/component/news/`, then refactor each news page to use them while keeping page-specific ViewModels and business rules in place. Improve performance by using stable lazy-list keys, avoiding broad state reads in deep composables, and moving repeated display derivation out of hot row recomposition paths.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Koin ViewModel, Kotlin unit/source tests, Gradle Android build.

---

## File Structure

- Create `app/src/main/kotlin/com/dailysatori/ui/component/news/NewsListLayouts.kt`: common news list padding and centered state message components.
- Create `app/src/main/kotlin/com/dailysatori/ui/component/news/NewsStatusBanner.kt`: reusable inline warning/status banner for refresh failures with cached content.
- Create `app/src/test/kotlin/com/dailysatori/ui/component/news/NewsListLayoutsTest.kt`: source-level guard for shared list padding and state message API.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`: keep entry route, AppScaffold, top-level source switching, and detail routing.
- Create `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsSummaryContent.kt`: summary lazy list, empty/loading routing, and summary card host.
- Create `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsSourceSwitcher.kt`: source tabs and refresh action.
- Create `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsRemoteSourceContent.kt`: remote source article list, cached-refresh banner, and source article messages.
- Create `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsBriefingCard.kt`: magazine cover, story rows, source row, and briefing fallback card.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsScreen.kt`: reuse shared state/list primitives and improve load-more trigger with derived state.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/crayfishnews/CrayfishNewsScreen.kt`: reuse shared state/list primitives and improve load-more trigger with derived state.
- Modify relevant tests under `app/src/test/kotlin/com/dailysatori/ui/feature/unifiednews/`, `remotenews/`, and `crayfishnews/` only when file splits change source paths or stable-key checks are added.

---

### Task 1: Add Shared News UI Primitives

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/component/news/NewsListLayouts.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/component/news/NewsStatusBanner.kt`
- Create: `app/src/test/kotlin/com/dailysatori/ui/component/news/NewsListLayoutsTest.kt`

- [ ] **Step 1: Write the failing source test**

Create `NewsListLayoutsTest.kt` with checks for the planned shared APIs:

```kotlin
package com.dailysatori.ui.component.news

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class NewsListLayoutsTest {
    @Test
    fun sharedNewsLayoutsExposeConsistentPaddingAndStateMessage() {
        val layouts = File("src/main/kotlin/com/dailysatori/ui/component/news/NewsListLayouts.kt").readText()
        val banner = File("src/main/kotlin/com/dailysatori/ui/component/news/NewsStatusBanner.kt").readText()

        assertTrue(layouts.contains("fun newsListContentPadding(): PaddingValues"))
        assertTrue(layouts.contains("PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.m)"))
        assertTrue(layouts.contains("fun NewsStateMessage("))
        assertTrue(layouts.contains("actionLabel: String? = null"))
        assertTrue(banner.contains("fun NewsStatusBanner("))
        assertTrue(banner.contains("color = MaterialTheme.colorScheme.surfaceContainerHighest"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.component.news.NewsListLayoutsTest`

Expected: FAIL because `NewsListLayouts.kt` and `NewsStatusBanner.kt` do not exist.

- [ ] **Step 3: Implement shared primitives**

Create `NewsListLayouts.kt`:

```kotlin
package com.dailysatori.ui.component.news

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.theme.Spacing

fun newsListContentPadding(): PaddingValues =
    PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.m)

@Composable
fun NewsStateMessage(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    isError: Boolean = false,
) {
    if (icon != null && subtitle != null && actionLabel == null) {
        EmptyState(icon = icon, title = title, subtitle = subtitle, modifier = modifier)
        return
    }

    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
```

Create `NewsStatusBanner.kt`:

```kotlin
package com.dailysatori.ui.component.news

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dailysatori.ui.theme.BorderWidth
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

@Composable
fun NewsStatusBanner(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.l),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outline),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.component.news.NewsListLayoutsTest`

Expected: PASS.

---

### Task 2: Split Unified News Summary and Source UI

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsSourceSwitcher.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsSummaryContent.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsRemoteSourceContent.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsSourceTabsStyleTest.kt`

- [ ] **Step 1: Write/update source tests for split files and stable keys**

Update `UnifiedNewsSourceTabsStyleTest.kt` so it reads `UnifiedNewsSourceSwitcher.kt` for tabs and `UnifiedNewsSummaryContent.kt` / `UnifiedNewsRemoteSourceContent.kt` for lazy-list key checks:

```kotlin
@Test
fun sourceTabsUsePrimaryBlueSelectedAccent() {
    val source = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsSourceSwitcher.kt").readText()
    val tabsBody = source.extractCallBlock("internal fun UnifiedNewsSourceTabs(")

    assertTrue(tabsBody.contains("FilterChipDefaults.filterChipColors"))
    assertTrue(tabsBody.contains("selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)"))
    assertTrue(tabsBody.contains("selectedLabelColor = MaterialTheme.colorScheme.primary"))
    assertTrue(tabsBody.contains("selectedLeadingIconColor = MaterialTheme.colorScheme.primary"))
}

@Test
fun refreshActionUsesSubduedSurfaceVariantTone() {
    val source = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsSourceSwitcher.kt").readText()
    val switcherBody = source.extractCallBlock("internal fun UnifiedNewsSourceSwitcher(")

    assertTrue(switcherBody.contains("tint = MaterialTheme.colorScheme.onSurfaceVariant"))
}

@Test
fun unifiedNewsListsUseStableKeysAndSharedPadding() {
    val summary = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsSummaryContent.kt").readText()
    val remote = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsRemoteSourceContent.kt").readText()

    assertTrue(summary.contains("contentPadding = newsListContentPadding()"))
    assertTrue(summary.contains("items(visibleSummaries, key = { it.id })"))
    assertTrue(remote.contains("contentPadding = newsListContentPadding()"))
    assertTrue(remote.contains("items(articles, key = { it.id })"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.unifiednews.UnifiedNewsSourceTabsStyleTest`

Expected: FAIL because split files do not exist yet.

- [ ] **Step 3: Move source switcher code**

Create `UnifiedNewsSourceSwitcher.kt` by moving `UnifiedNewsSourceSwitcher` and `UnifiedNewsSourceTabs` from `UnifiedNewsScreen.kt`. Keep signatures `internal` and keep selected colors/tint unchanged.

- [ ] **Step 4: Move summary and remote source list code**

Create `UnifiedNewsSummaryContent.kt` containing `UnifiedNewsSummaryContent` and its direct list-state behavior. Use `newsListContentPadding()` instead of inline `PaddingValues(...)`.

Create `UnifiedNewsRemoteSourceContent.kt` containing `UnifiedNewsSourceArticleContent`, `UnifiedNewsSourceArticleList`, and `UnifiedNewsSourceArticleMessage`. Use `NewsStateMessage` and `NewsStatusBanner`. Keep `items(articles, key = { it.id })`.

- [ ] **Step 5: Remove moved code and imports from `UnifiedNewsScreen.kt`**

Keep only `UnifiedNewsScreen`, detail route, main route, summary page scaffold, menu, detail loading/error screens, and any helpers still used there.

- [ ] **Step 6: Run tests and compile**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.unifiednews.UnifiedNewsSourceTabsStyleTest`

Run: `./gradlew :app:compileDebugKotlin`

Expected: both PASS.

---

### Task 3: Split Unified News Briefing Card

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsBriefingCard.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsBriefingContentTest.kt`

- [ ] **Step 1: Run current briefing parser tests as baseline**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.unifiednews.UnifiedNewsBriefingContentTest`

Expected: PASS before moving UI code.

- [ ] **Step 2: Move briefing card composables**

Create `UnifiedNewsBriefingCard.kt` and move these composables from `UnifiedNewsScreen.kt` without changing rendered text:

- `TodayUnifiedNewsCard`
- `UnifiedNewsMagazineCover`
- `UnifiedNewsBriefingBadge`
- `UnifiedNewsMagazineStoryList`
- `UnifiedNewsMagazineStoryRow`
- `UnifiedNewsBriefingFallback`
- `UnifiedNewsBriefingSourceRow`
- `UnifiedNewsBriefingSourceChip`
- `unifiedNewsBriefingSourceHint`

Keep `val briefing = remember(summary.content) { unifiedNewsBriefingContent(summary.content) }` in `TodayUnifiedNewsCard` so parsing remains memoized by summary content.

- [ ] **Step 3: Remove moved imports from `UnifiedNewsScreen.kt`**

Remove imports that are only used by the moved briefing composables, such as `ExperimentalLayoutApi`, `FlowRow`, `HorizontalDivider`, `clickable`, and `TextOverflow`, unless still used elsewhere.

- [ ] **Step 4: Run tests and compile**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.unifiednews.UnifiedNewsBriefingContentTest`

Run: `./gradlew :app:compileDebugKotlin`

Expected: both PASS.

---

### Task 4: Refactor Remote News List Performance and Shared States

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsScreen.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsUiBehaviorTest.kt`

- [ ] **Step 1: Add source-level performance checks**

Add or update a test in `RemoteNewsUiBehaviorTest.kt`:

```kotlin
@Test
fun remoteNewsListsUseStableKeysAndDerivedLoadMoreState() {
    val source = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsScreen.kt").readText()

    assertTrue(source.contains("derivedStateOf"))
    assertTrue(source.contains("items(state.digests, key = { it.id })"))
    assertTrue(source.contains("items(state.articles, key = { it.id })"))
    assertTrue(source.contains("items(state.feeds, key = { it.id })"))
    assertTrue(source.contains("contentPadding = newsListContentPadding()"))
    assertTrue(source.contains("NewsStateMessage("))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.remotenews.RemoteNewsUiBehaviorTest`

Expected: FAIL because `derivedStateOf`, shared padding, and `NewsStateMessage` are not used yet.

- [ ] **Step 3: Replace local state/error UI and list padding**

In `RemoteNewsScreen.kt`, import `NewsStateMessage` and `newsListContentPadding`. Replace `RemoteNewsError` with `NewsStateMessage(title = message, actionLabel = "重试", onAction = onRetry, isError = true)`. Replace empty state with `NewsStateMessage(icon = Icons.Default.Article, title = "暂无内容", subtitle = "远程新闻暂时没有可显示的数据")`. Replace `PaddingValues(Spacing.m)` with `newsListContentPadding()`.

- [ ] **Step 4: Improve load-more trigger**

Change `LoadMoreWhenAtEnd` to use `derivedStateOf`:

```kotlin
@Composable
private fun LoadMoreWhenAtEnd(listState: LazyListState, itemCount: Int, onLoadMore: () -> Unit) {
    val shouldLoadMore by remember(listState, itemCount) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            itemCount > 0 && lastVisible >= itemCount - 1
        }
    }
    LaunchedEffect(shouldLoadMore, itemCount) {
        if (shouldLoadMore) onLoadMore()
    }
}
```

- [ ] **Step 5: Run tests and compile**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.remotenews.RemoteNewsUiBehaviorTest`

Run: `./gradlew :app:compileDebugKotlin`

Expected: both PASS.

---

### Task 5: Refactor Crayfish News List Performance and Shared States

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/crayfishnews/CrayfishNewsScreen.kt`
- Create or modify: `app/src/test/kotlin/com/dailysatori/ui/feature/crayfishnews/CrayfishNewsScreenLayoutTest.kt`

- [ ] **Step 1: Add source-level performance checks**

Create `CrayfishNewsScreenLayoutTest.kt`:

```kotlin
package com.dailysatori.ui.feature.crayfishnews

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class CrayfishNewsScreenLayoutTest {
    @Test
    fun crayfishNewsListUsesStableKeysSharedPaddingAndDerivedLoadMoreState() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/crayfishnews/CrayfishNewsScreen.kt").readText()

        assertTrue(source.contains("derivedStateOf"))
        assertTrue(source.contains("itemsIndexed(articles, key = { _, item -> item.filename })"))
        assertTrue(source.contains("contentPadding = newsListContentPadding()"))
        assertTrue(source.contains("NewsStateMessage("))
        assertTrue(source.contains("remember(article.content) { article.content.withoutIntroBlock() }"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.crayfishnews.CrayfishNewsScreenLayoutTest`

Expected: FAIL because shared padding, `derivedStateOf`, and remembered content trimming are not used yet.

- [ ] **Step 3: Replace local state/error UI and list padding**

In `CrayfishNewsScreen.kt`, import `NewsStateMessage` and `newsListContentPadding`. Replace `CrayfishNewsError` and empty state usages with `NewsStateMessage`. Replace `PaddingValues(Spacing.m)` with `newsListContentPadding()`.

- [ ] **Step 4: Improve load-more trigger and markdown preprocessing**

Change `LoadMoreWhenAtEnd` to the same `derivedStateOf` implementation used in Task 4.

In `CrayfishArticleCard`, compute trimmed markdown once per article content:

```kotlin
val displayContent = remember(article.content) { article.content.withoutIntroBlock() }
Markdown(
    content = displayContent,
    typography = MarkdownStyles.summaryTypography(),
    padding = MarkdownStyles.summaryPadding(),
)
```

- [ ] **Step 5: Run tests and compile**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.crayfishnews.CrayfishNewsScreenLayoutTest`

Run: `./gradlew :app:compileDebugKotlin`

Expected: both PASS.

---

### Task 6: Final Verification and Device Attempt

**Files:**
- No code changes expected.

- [ ] **Step 1: Run focused news tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.component.news.NewsListLayoutsTest --tests com.dailysatori.ui.feature.unifiednews.UnifiedNewsSourceTabsStyleTest --tests com.dailysatori.ui.feature.unifiednews.UnifiedNewsBriefingContentTest --tests com.dailysatori.ui.feature.remotenews.RemoteNewsUiBehaviorTest --tests com.dailysatori.ui.feature.crayfishnews.CrayfishNewsScreenLayoutTest`

Expected: PASS.

- [ ] **Step 2: Run required compile and build**

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

Run: `./gradlew :app:assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Attempt device install and launch**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug && adb shell am start -n com.dailysatori/.MainActivity`

Expected with connected device: install succeeds and app launches.

Expected without connected device: `No connected devices!`; report this as a verification blocker, not a code failure.

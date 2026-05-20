# WeChat-Style Top-Level IA Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reframe the app's top-level navigation around `今日 / 记录 / 读书 / AI` and add a WeChat-style records hub without removing existing features.

**Architecture:** Keep `HomeScreen` as the top-level coordinator. Reuse existing `UnifiedNewsScreen`, `DiaryScreen`, `ArticleListScreen`, `BooksScreen`, and `AiChatScreen`; add a focused `RecordsScreen` hub that switches inline to diary/articles/favorites while preserving article-detail and AI citation navigation.

**Tech Stack:** Kotlin, Android Jetpack Compose, Koin ViewModels, Material 3, source-level Kotlin unit tests, Gradle.

---

## File Structure

- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt`: rename/reorder tabs, update constants, render `RecordsScreen` for the second tab, preserve selected-book and AI behavior.
- Create `app/src/main/kotlin/com/dailysatori/ui/feature/records/RecordsScreen.kt`: records hub with WeChat-style list rows and inline destination switching to existing screens.
- Create `app/src/test/kotlin/com/dailysatori/ui/feature/home/HomeIaTest.kt`: source-level tests for tab labels, records tab existence, selected-book tab index behavior, and article navigation preservation.
- Create `app/src/test/kotlin/com/dailysatori/ui/feature/records/RecordsScreenTest.kt`: source-level tests for records hub rows and reused screen wiring.
- Modify `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`: update stale top-level tab expectations from `新闻汇总` to `今日` and assert `记录` exists.
- Modify `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt`: keep AI tab visibility/index assertions aligned with unchanged AI tab index.

## Task 1: Home Tab IA Contract

**Files:**
- Create: `app/src/test/kotlin/com/dailysatori/ui/feature/home/HomeIaTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Write failing Home IA tests**

Create `app/src/test/kotlin/com/dailysatori/ui/feature/home/HomeIaTest.kt`:

```kotlin
package com.dailysatori.ui.feature.home

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeIaTest {
    @Test
    fun homeTabsUseWeChatStyleInformationArchitecture() {
        assertEquals(listOf("今日", "记录", "读书", "AI"), tabs.map { it.label })
        assertEquals(0, TODAY_TAB_INDEX)
        assertEquals(1, RECORDS_TAB_INDEX)
        assertEquals(2, READING_TAB_INDEX)
        assertEquals(3, AI_CHAT_TAB_INDEX)
        assertTrue(tabs.indices.all(::homeBottomBarVisibleForTab))
    }

    @Test
    fun homeScreenRoutesTopLevelTabsToExpectedSurfaces() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt").readText()

        assertTrue(source.contains("TODAY_TAB_INDEX -> UnifiedNewsScreen"))
        assertTrue(source.contains("RECORDS_TAB_INDEX -> RecordsScreen"))
        assertTrue(source.contains("READING_TAB_INDEX -> BooksScreen"))
        assertTrue(source.contains("AI_CHAT_TAB_INDEX -> AiChatScreen"))
        assertFalse(source.contains("TabItem(\"日记\""))
        assertFalse(source.contains("TabItem(\"新闻汇总\""))
    }

    @Test
    fun selectedBookStillSwitchesToReadingTab() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt").readText()

        assertTrue(source.contains("if (selectedBookId != null) selectedIndex = READING_TAB_INDEX"))
    }
}
```

- [ ] **Step 2: Run tests to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.home.HomeIaTest"`

Expected: FAIL because `TODAY_TAB_INDEX`, `RECORDS_TAB_INDEX`, `READING_TAB_INDEX`, and `RecordsScreen` do not exist, and tab labels are still old.

- [ ] **Step 3: Update `HomeScreen.kt` imports and constants**

Add imports:

```kotlin
import com.dailysatori.ui.feature.records.RecordsScreen
```

Replace the `tabs` list and constants with:

```kotlin
const val TODAY_TAB_INDEX = 0
const val RECORDS_TAB_INDEX = 1
const val READING_TAB_INDEX = 2
const val AI_CHAT_TAB_INDEX = 3

val tabs = listOf(
    TabItem("今日", Icons.Filled.Language, Icons.Outlined.Language),
    TabItem("记录", Icons.Filled.Book, Icons.Outlined.Book),
    TabItem("读书", Icons.Filled.AutoStories, Icons.Outlined.AutoStories),
    TabItem("AI", Icons.Filled.SmartToy, Icons.Outlined.SmartToy),
)
```

Update selected-book effect:

```kotlin
LaunchedEffect(selectedBookId) {
    if (selectedBookId != null) selectedIndex = READING_TAB_INDEX
}
```

Update `when (index)` branches:

```kotlin
TODAY_TAB_INDEX -> UnifiedNewsScreen(settingsViewModel = settingsViewModel, onArticleClick = onArticleClick, onMyClick = openMy)
RECORDS_TAB_INDEX -> RecordsScreen(onArticleClick = onArticleClick, onMyClick = openMy)
READING_TAB_INDEX -> BooksScreen(
    selectedBookId = selectedBookId,
    selectedViewpointId = selectedViewpointId,
    bookAnalysisMessage = bookAnalysisMessage,
    onSelectedBookConsumed = onSelectedBookConsumed,
    onMyClick = openMy,
)
AI_CHAT_TAB_INDEX -> AiChatScreen(onArticleClick = onAiArticleClick, onMyClick = openMy)
else -> UnifiedNewsScreen(settingsViewModel = settingsViewModel, onArticleClick = onArticleClick, onMyClick = openMy)
```

- [ ] **Step 4: Add temporary `RecordsScreen` stub for compilation**

Create `app/src/main/kotlin/com/dailysatori/ui/feature/records/RecordsScreen.kt`:

```kotlin
package com.dailysatori.ui.feature.records

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun RecordsScreen(
    onArticleClick: (Long) -> Unit = {},
    onMyClick: () -> Unit = {},
) {
    Text("记录")
}
```

- [ ] **Step 5: Update stale unified-news tab test**

In `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`, update `homeTabsAreReducedAndFirstTabIsUnifiedNews()` assertions from:

```kotlin
assertTrue(home.contains("TabItem(\"新闻汇总\""))
assertFalse(home.contains("TabItem(\"文章\""))
```

to:

```kotlin
assertTrue(home.contains("TabItem(\"今日\""))
assertTrue(home.contains("TabItem(\"记录\""))
assertFalse(home.contains("TabItem(\"文章\""))
```

- [ ] **Step 6: Run tests and compile**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.home.HomeIaTest" --tests "com.dailysatori.UnifiedNewsBehaviorTest.homeTabsAreReducedAndFirstTabIsUnifiedNews" :app:compileDebugKotlin`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/records/RecordsScreen.kt app/src/test/kotlin/com/dailysatori/ui/feature/home/HomeIaTest.kt app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt
git commit -m "feat: rename top-level tabs for wechat ia"
```

## Task 2: Records Hub UI And Inline Destinations

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/records/RecordsScreen.kt`
- Create: `app/src/test/kotlin/com/dailysatori/ui/feature/records/RecordsScreenTest.kt`

- [ ] **Step 1: Write failing records hub tests**

Create `app/src/test/kotlin/com/dailysatori/ui/feature/records/RecordsScreenTest.kt`:

```kotlin
package com.dailysatori.ui.feature.records

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecordsScreenTest {
    @Test
    fun recordsDestinationsUseRequiredLabels() {
        assertEquals(listOf("日记", "文章", "本地收藏"), recordsDestinations().map { it.title })
        assertEquals(listOf(RecordsDestination.Diary, RecordsDestination.Articles, RecordsDestination.Favorites), recordsDestinations().map { it.destination })
    }

    @Test
    fun recordsScreenUsesExistingSurfacesInline() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/records/RecordsScreen.kt").readText()

        assertTrue(source.contains("AppScaffold("))
        assertTrue(source.contains("title = \"记录\""))
        assertTrue(source.contains("DiaryScreen(onMyClick = onMyClick)"))
        assertTrue(source.contains("ArticleListScreen(onArticleClick = onArticleClick)"))
        assertTrue(source.contains("showFavoritesOnly = true"))
        assertTrue(source.contains("lockFavoritesFilter = true"))
    }
}
```

- [ ] **Step 2: Run tests to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.records.RecordsScreenTest"`

Expected: FAIL because `recordsDestinations()` and inline records UI do not exist.

- [ ] **Step 3: Implement records hub**

Replace `RecordsScreen.kt` with:

```kotlin
package com.dailysatori.ui.feature.records

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.feature.article.ArticleListScreen
import com.dailysatori.ui.feature.diary.DiaryScreen
import com.dailysatori.ui.theme.Spacing

enum class RecordsDestination { Diary, Articles, Favorites }

data class RecordsDestinationItem(
    val title: String,
    val subtitle: String,
    val destination: RecordsDestination,
)

fun recordsDestinations(): List<RecordsDestinationItem> = listOf(
    RecordsDestinationItem("日记", "写下和回看每天的记录", RecordsDestination.Diary),
    RecordsDestinationItem("文章", "管理保存的网页文章", RecordsDestination.Articles),
    RecordsDestinationItem("本地收藏", "集中查看想继续读的内容", RecordsDestination.Favorites),
)

@Composable
fun RecordsScreen(
    onArticleClick: (Long) -> Unit = {},
    onMyClick: () -> Unit = {},
) {
    var selectedDestination by remember { mutableStateOf<RecordsDestination?>(null) }
    BackHandler(enabled = selectedDestination != null) { selectedDestination = null }

    when (selectedDestination) {
        RecordsDestination.Diary -> DiaryScreen(onMyClick = onMyClick)
        RecordsDestination.Articles -> ArticleListScreen(onArticleClick = onArticleClick)
        RecordsDestination.Favorites -> ArticleListScreen(
            onArticleClick = onArticleClick,
            showFavoritesOnly = true,
            lockFavoritesFilter = true,
        )
        null -> RecordsHub(onSelect = { selectedDestination = it }, onMyClick = onMyClick)
    }
}

@Composable
private fun RecordsHub(
    onSelect: (RecordsDestination) -> Unit,
    onMyClick: () -> Unit,
) {
    AppScaffold(
        title = "记录",
        showBack = false,
        myNavigationLabel = "我的",
        onMyNavigationClick = onMyClick,
    ) { modifier ->
        Column(
            modifier = modifier.fillMaxSize().padding(top = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    recordsDestinations().forEachIndexed { index, item ->
                        RecordsRow(item = item, onClick = { onSelect(item.destination) })
                        if (index != recordsDestinations().lastIndex) HorizontalDivider(modifier = Modifier.padding(start = Spacing.xl))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordsRow(item: RecordsDestinationItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.m, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(recordsDestinationIcon(item.destination), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun recordsDestinationIcon(destination: RecordsDestination): ImageVector = when (destination) {
    RecordsDestination.Diary -> Icons.AutoMirrored.Filled.MenuBook
    RecordsDestination.Articles -> Icons.AutoMirrored.Filled.Article
    RecordsDestination.Favorites -> Icons.Default.Bookmark
}
```

- [ ] **Step 4: Run records tests and compile**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.records.RecordsScreenTest" :app:compileDebugKotlin`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/records/RecordsScreen.kt app/src/test/kotlin/com/dailysatori/ui/feature/records/RecordsScreenTest.kt
git commit -m "feat: add records hub"
```

## Task 3: Navigation Regression Verification

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt` if needed.
- No production changes expected unless tests expose an issue.

- [ ] **Step 1: Run focused navigation tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.home.HomeIaTest" --tests "com.dailysatori.ui.feature.records.RecordsScreenTest" --tests "com.dailysatori.ui.feature.aichat.AiChatUiStateTest.homeBottomBarRemainsVisibleOnAiTab" --tests "com.dailysatori.UnifiedNewsBehaviorTest.homeTabsAreReducedAndFirstTabIsUnifiedNews"
```

Expected: PASS.

- [ ] **Step 2: Fix any stale source assertions**

If a source-level assertion still expects the old tab label or old tab index, update only that assertion. For example, `AI_CHAT_TAB_INDEX` should remain `3`, and selected-book behavior should use `READING_TAB_INDEX`.

- [ ] **Step 3: Run required compile**

Run: `./gradlew :app:compileDebugKotlin`

Expected: PASS.

- [ ] **Step 4: Commit test-only fixes if any**

If Step 2 changed files, commit:

```bash
git add app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt
git commit -m "test: update navigation ia assertions"
```

If no files changed, do not commit.

## Task 4: Device Verification

**Files:**
- No code changes expected.

- [ ] **Step 1: Connect remote device**

Run:

```bash
adb connect 192.168.2.12:38819
adb devices
```

Expected: `192.168.2.12:38819 device` is listed.

- [ ] **Step 2: Install debug build**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
```

Expected: PASS. If another emulator is attached, disconnect/stop it first so install targets the remote device only.

- [ ] **Step 3: Launch app on remote device**

Run:

```bash
adb -s 192.168.2.12:38819 shell am start -n com.dailysatori/.MainActivity
```

Expected: Activity starts.

- [ ] **Step 4: Manual smoke check**

On the remote device, confirm:

- Bottom tabs read `今日 / 记录 / 读书 / AI`.
- `今日` opens the existing unified news page.
- `记录` shows `日记 / 文章 / 本地收藏` rows.
- Entering each records row shows the existing surface.
- Article detail navigation from `文章` and `本地收藏` still works.
- `读书` and `AI` still open.

- [ ] **Step 5: Check status**

Run: `git status --short`

Expected: only intentional files are changed or the tree is clean after commits.

## Self-Review Notes

- Spec coverage: tab labels, records hub, existing screen reuse, article navigation, selected-book behavior, no database changes, and manual verification are covered.
- Placeholder scan: no incomplete placeholder steps remain.
- Type consistency: constants `TODAY_TAB_INDEX`, `RECORDS_TAB_INDEX`, `READING_TAB_INDEX`, and `AI_CHAT_TAB_INDEX` are defined before tests reference them; `RecordsDestination` and `recordsDestinations()` are defined in Task 2 before tests use them.

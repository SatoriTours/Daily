# Remote Article Detail Tabs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make remote article details opened from unified summary citations match the local article detail layout by separating AI summary and original content into tabs.

**Architecture:** Keep routing and data loading unchanged. Replace the current remote article detail long `LazyColumn` with a top metadata area plus `TabRow` and `HorizontalPager`, following `ArticleDetailScreen`'s two-tab pattern while keeping remote-specific article fields.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Compose Foundation Pager, Koin-provided existing state, MikePenz Markdown renderer.

---

## File Structure

- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsDetailScreens.kt`: update `RemoteArticleDetailScreen` and add small helpers for tab labels and tab page content.
- Modify `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`: add pure behavior tests for remote detail tab content separation.

## Task 1: Add Remote Detail Content Formatting Tests

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Add failing tests**

Append these tests inside `UnifiedNewsBehaviorTest`:

```kotlin
    @Test
    fun remoteArticleDetailSummaryTabCombinesSummaryAndViewpointsOnly() {
        val content = remoteArticleDetailPageContent(
            page = 0,
            summary = "AI 摘要内容",
            viewpoints = listOf("观点一", "观点二"),
            original = "原文内容",
        )

        assertTrue(content.contains("AI 摘要内容"))
        assertTrue(content.contains("## 关键观点"))
        assertTrue(content.contains("- 观点一"))
        assertTrue(content.contains("- 观点二"))
        assertFalse(content.contains("原文内容"))
    }

    @Test
    fun remoteArticleDetailOriginalTabShowsOriginalOnly() {
        val content = remoteArticleDetailPageContent(
            page = 1,
            summary = "AI 摘要内容",
            viewpoints = listOf("观点一"),
            original = "原文内容",
        )

        assertEquals("原文内容", content)
    }

    @Test
    fun remoteArticleDetailTabsUseFallbacksForMissingContent() {
        assertEquals(
            "暂无摘要内容",
            remoteArticleDetailPageContent(page = 0, summary = " ", viewpoints = emptyList(), original = null),
        )
        assertEquals(
            "暂无原文内容",
            remoteArticleDetailPageContent(page = 1, summary = null, viewpoints = listOf("观点"), original = ""),
        )
    }
```

Add imports if missing:

```kotlin
import com.dailysatori.ui.feature.remotenews.remoteArticleDetailPageContent
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
```

- [ ] **Step 2: Run test to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest --no-configuration-cache`

Expected: FAIL because `remoteArticleDetailPageContent` does not exist or is not visible.

## Task 2: Implement Remote Detail Tab Content Formatter

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsDetailScreens.kt`

- [ ] **Step 1: Add formatter function near remote detail helpers**

Add this function near the existing remote detail helper functions:

```kotlin
internal fun remoteArticleDetailPageContent(
    page: Int,
    summary: String?,
    viewpoints: List<String>,
    original: String?,
): String = when (page) {
    0 -> remoteArticleSummaryPageContent(summary, viewpoints)
    else -> original?.trim()?.takeIf { it.isNotBlank() } ?: "暂无原文内容"
}

private fun remoteArticleSummaryPageContent(summary: String?, viewpoints: List<String>): String {
    val summaryContent = summary?.trim()?.takeIf { it.isNotBlank() }
    val viewpointContent = viewpoints
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(separator = "\n") { "- $it" }

    return listOfNotNull(
        summaryContent,
        viewpointContent.takeIf { it.isNotBlank() }?.let { "## 关键观点\n\n$it" },
    ).joinToString(separator = "\n\n").ifBlank { "暂无摘要内容" }
}
```

- [ ] **Step 2: Run formatter tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest --no-configuration-cache`

Expected: PASS for the new formatter tests.

## Task 3: Replace Remote Article Long List With Tabs

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsDetailScreens.kt`

- [ ] **Step 1: Add imports**

Add imports used by the tabbed layout:

```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
```

- [ ] **Step 2: Replace `RemoteArticleDetailScreen` body**

Replace the content inside `RemoteArticleDetailScreen` after `val context = LocalContext.current` with this structure:

```kotlin
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        if (selectedTabIndex != pagerState.currentPage) selectedTabIndex = pagerState.currentPage
    }

    LaunchedEffect(selectedTabIndex) {
        if (pagerState.currentPage != selectedTabIndex) pagerState.animateScrollToPage(selectedTabIndex)
    }

    BackHandler(onBack = onBack)

    AppScaffold(
        title = article.domain ?: article.feedName ?: "文章",
        onBack = onBack,
        actions = {
            IconButton(onClick = { openArticleUrl(context, article.url) }) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = "在浏览器打开")
            }
        },
    ) { modifier ->
        Column(modifier = modifier.fillMaxSize()) {
            RemoteArticleHeroCard(article)
            RemoteArticleTabRow(
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { index -> coroutineScope.launch { pagerState.animateScrollToPage(index) } },
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                beyondViewportPageCount = 1,
            ) { page ->
                val listState = rememberLazyListState()
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item(key = "remote-content-$page") {
                        Box(modifier = Modifier.padding(Spacing.m)) {
                            RemoteArticleMarkdownContent(
                                remoteArticleDetailPageContent(
                                    page = page,
                                    summary = article.summary,
                                    viewpoints = article.viewpoints,
                                    original = article.content,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
```

- [ ] **Step 3: Add tab and markdown composables**

Add these helpers below `RemoteArticleDetailScreen`:

```kotlin
@Composable
private fun RemoteArticleTabRow(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
) {
    TabRow(selectedTabIndex = selectedTabIndex, modifier = Modifier.fillMaxWidth()) {
        Tab(selected = selectedTabIndex == 0, onClick = { onTabSelected(0) }, text = { Text("AI 摘要") })
        Tab(selected = selectedTabIndex == 1, onClick = { onTabSelected(1) }, text = { Text("原文") })
    }
}

@Composable
private fun RemoteArticleMarkdownContent(content: String) {
    SelectionContainer {
        Markdown(content = content, typography = MarkdownStyles.typography(), padding = MarkdownStyles.padding())
    }
}
```

- [ ] **Step 4: Remove unused long-list section helpers**

Remove these functions if nothing else references them:

```kotlin
private fun RemoteArticleSummaryCard(summary: String)
private fun RemoteArticleViewpointsSection(viewpoints: List<String>)
private fun RemoteArticleContentSection(content: String)
private fun RemoteArticleOriginalLinkCard(url: String)
private fun RemoteArticleSectionSurface(title: String, content: @Composable () -> Unit)
private fun SectionHeader(text: String)
```

Keep `fun RemoteArticleSummaryCard(article: RemoteArticle, onClick: () -> Unit)` because digest lists use it.

## Task 4: Verify Build And Behavior

**Files:**
- No code changes expected.

- [ ] **Step 1: Run focused tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest --no-configuration-cache`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run compile check**

Run: `./gradlew :app:compileDebugKotlin --no-configuration-cache`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Install and launch debug build**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug --no-configuration-cache`

Expected: BUILD SUCCESSFUL.

Run: `adb shell am force-stop com.dailysatori && adb shell am start -n com.dailysatori/.MainActivity`

Expected: Activity starts without command error.

## Self-Review

- Spec coverage: The plan separates remote AI summary and original content into tabs, keeps citation routing unchanged, and preserves browser-open access.
- Placeholder scan: No TODO/TBD placeholders remain.
- Type consistency: `remoteArticleDetailPageContent` is defined in `RemoteNewsDetailScreens.kt` and imported by `UnifiedNewsBehaviorTest`.

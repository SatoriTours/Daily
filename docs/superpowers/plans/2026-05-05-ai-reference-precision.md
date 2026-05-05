# AI Reference Precision Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make AI assistant references precise for ordered diary questions, expandable in the chat UI, and consistent when opening reference details.

**Architecture:** Add small pure helpers around MCP reference semantics and chat reference presentation, then wire them into the existing agent and Compose UI. Avoid database schema changes by reusing existing repository reads and the existing `chat_conversation.search_results` persistence format.

**Tech Stack:** Kotlin Multiplatform shared services, Android Jetpack Compose, Kotlin unit tests, SQLDelight repositories.

---

## File Structure

- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentPresentation.kt` - detect ordered diary queries and reduce references to the exact diary result.
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentService.kt` - apply ordered-diary reference reduction after answer generation.
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpToolRegistry.kt` - describe ordered diary retrieval behavior clearly in tool metadata.
- Modify: `shared/src/commonTest/kotlin/com/dailysatori/service/mcp/McpAgentPresentationTest.kt` - cover ordered diary detection and reference reduction.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt` - route all references through the AI-local detail sheet.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiReferenceDetailViewModel.kt` - support article references in the detail sheet.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiReferenceDetailSheet.kt` - render article preview and optional full-detail action.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt` - add expandable reference list behavior.
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt` - cover reference display counts and expansion text.

No database migration is required.

### Task 1: Add Ordered Diary Reference Semantics

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentPresentation.kt`
- Modify: `shared/src/commonTest/kotlin/com/dailysatori/service/mcp/McpAgentPresentationTest.kt`

- [ ] **Step 1: Write failing tests**

Append to `McpAgentPresentationTest`:

```kotlin
@Test
fun detectsSecondLatestDiaryQueries() {
    assertEquals(1, orderedDiaryIndexFromQuery("我倒数第二近的日记是什么"))
    assertEquals(1, orderedDiaryIndexFromQuery("第二近的日记是什么"))
    assertEquals(2, orderedDiaryIndexFromQuery("倒数第三篇日记是什么"))
    assertEquals(null, orderedDiaryIndexFromQuery("最近的文章是什么"))
}

@Test
fun reducesOrderedDiaryReferencesToOneResult() {
    val results = listOf(
        McpSearchResult(11, "diary", "最新日记", "one", "2026-05-03"),
        McpSearchResult(10, "diary", "倒数第二篇", "two", "2026-05-02"),
        McpSearchResult(9, "diary", "倒数第三篇", "three", "2026-05-01"),
    )

    assertEquals(listOf(results[1]), preciseSearchResultsForQuery("倒数第二近的日记", results))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:allTests --tests "com.dailysatori.service.mcp.McpAgentPresentationTest"`

Expected: FAIL with unresolved references `orderedDiaryIndexFromQuery` and `preciseSearchResultsForQuery`.

- [ ] **Step 3: Implement pure helpers**

Add to `McpAgentPresentation.kt` after `canOpenSearchResult`:

```kotlin
fun orderedDiaryIndexFromQuery(query: String): Int? {
    if (!query.contains("日记")) return null
    val normalized = query.replace("倒数第", "第")
    return when {
        normalized.contains("第二近") || normalized.contains("第二篇") || normalized.contains("第二个") -> 1
        normalized.contains("第三近") || normalized.contains("第三篇") || normalized.contains("第三个") -> 2
        normalized.contains("第四近") || normalized.contains("第四篇") || normalized.contains("第四个") -> 3
        normalized.contains("第五近") || normalized.contains("第五篇") || normalized.contains("第五个") -> 4
        else -> null
    }
}

fun preciseSearchResultsForQuery(
    query: String,
    results: List<McpSearchResult>,
): List<McpSearchResult> {
    val orderedDiaryIndex = orderedDiaryIndexFromQuery(query) ?: return results
    val diaries = results.filter { it.type == "diary" }
    return diaries.getOrNull(orderedDiaryIndex)?.let { listOf(it) } ?: results
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:allTests --tests "com.dailysatori.service.mcp.McpAgentPresentationTest"`

Expected: PASS.

### Task 2: Apply Precise References In Agent Output

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentService.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpToolRegistry.kt`

- [ ] **Step 1: Apply precision after model reference filtering**

In `McpAgentService.processQuery`, replace:

```kotlin
val filteredResults = filterRelevantResults(collectedResults, finalAnswer ?: "")
val cleanAnswer = removeRefsTag(finalAnswer ?: buildFallbackAnswer(query, collectedResults))
McpAgentResult(answer = cleanAnswer, searchResults = filteredResults)
```

with:

```kotlin
val filteredResults = filterRelevantResults(collectedResults, finalAnswer ?: "")
val preciseResults = preciseSearchResultsForQuery(query, filteredResults)
val cleanAnswer = removeRefsTag(finalAnswer ?: buildFallbackAnswer(query, collectedResults))
McpAgentResult(answer = cleanAnswer, searchResults = preciseResults)
```

- [ ] **Step 2: Improve tool guidance for ordered diary requests**

In `McpToolRegistry.buildToolDefinitions`, change the `get_latest_diary` description and limit description to:

```kotlin
buildTool("get_latest_diary", "按时间倒序获取最近日记。用户问倒数第二近/第二近日记时，请用 limit=2 并只引用第二条；问倒数第三近时用 limit=3 并只引用第三条。", mapOf(
    "limit" to buildParam("integer", "返回的日记数量，默认为5，最大为20。按时间倒序排列，第一条最新，第二条为倒数第二近。"),
)),
```

- [ ] **Step 3: Run shared tests**

Run: `./gradlew :shared:allTests --tests "com.dailysatori.service.mcp.McpAgentPresentationTest"`

Expected: PASS.

### Task 3: Add Reference Expansion UI Helpers

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt`

- [ ] **Step 1: Write failing tests**

Append to `AiChatUiStateTest`:

```kotlin
@Test
fun referenceListDefaultsToThreeAndExpandsAll() {
    assertEquals(3, visibleReferenceCount(totalCount = 10, expanded = false))
    assertEquals(10, visibleReferenceCount(totalCount = 10, expanded = true))
    assertEquals(2, visibleReferenceCount(totalCount = 2, expanded = false))
}

@Test
fun referenceExpansionTextShowsRemainingCount() {
    assertEquals("展开剩余 7 条", referenceExpansionText(totalCount = 10, expanded = false))
    assertEquals("收起引用", referenceExpansionText(totalCount = 10, expanded = true))
    assertEquals(null, referenceExpansionText(totalCount = 3, expanded = false))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.aichat.AiChatUiStateTest"`

Expected: FAIL with unresolved references `visibleReferenceCount` and `referenceExpansionText`.

- [ ] **Step 3: Implement helpers and expandable UI**

In `MessageBubble.kt`, add imports:

```kotlin
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
```

Add top-level helpers before `MessageBubble`:

```kotlin
fun visibleReferenceCount(totalCount: Int, expanded: Boolean): Int =
    if (expanded) totalCount else totalCount.coerceAtMost(3)

fun referenceExpansionText(totalCount: Int, expanded: Boolean): String? {
    if (totalCount <= 3) return null
    return if (expanded) "收起引用" else "展开剩余 ${totalCount - 3} 条"
}
```

In `SearchResultsSection`, add state and replace `results.take(3)`:

```kotlin
var expanded by remember(results) { mutableStateOf(false) }
val visibleCount = visibleReferenceCount(results.size, expanded)
```

Then render:

```kotlin
results.take(visibleCount).forEach { result ->
    SearchResultCard(result, onReferenceClick)
}
referenceExpansionText(results.size, expanded)?.let { actionText ->
    TextButton(onClick = { expanded = !expanded }) {
        Text(actionText)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.aichat.AiChatUiStateTest"`

Expected: PASS.

### Task 4: Unify Reference Opening Through AI Detail Sheet

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiReferenceDetailViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiReferenceDetailSheet.kt`

- [ ] **Step 1: Add article state support**

In `AiReferenceDetailViewModel.kt`, add imports:

```kotlin
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.shared.db.Article
```

Add `val article: Article? = null` to `AiReferenceDetailState`.

Add `articleRepo: ArticleRepository` to the ViewModel constructor before `diaryRepo`.

In `load`, add article handling:

```kotlin
SearchResultOpenTarget.Article -> loadArticle(result.id)
```

Add:

```kotlin
private fun loadArticle(id: Long): AiReferenceDetailState {
    val article = articleRepo.getById(id)
    return if (article == null) {
        AiReferenceDetailState(error = MISSING_CONTENT_MESSAGE)
    } else {
        AiReferenceDetailState(article = article)
    }
}
```

- [ ] **Step 2: Update Koin ViewModel wiring if needed**

In `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`, update `AiReferenceDetailViewModel` construction to pass `articleRepo = get()` as the first argument.

- [ ] **Step 3: Route all references through the sheet**

In `AiChatScreen.openReference`, replace the `when` body with:

```kotlin
if (searchResultOpenTarget(result.type) != null) {
    showReferenceSheet = true
    referenceDetailViewModel.load(result)
}
```

Keep `onArticleClick` in the screen signature for the full-detail action inside the sheet.

- [ ] **Step 4: Render article preview in the sheet**

In `AiReferenceDetailSheet.kt`, add imports:

```kotlin
import androidx.compose.material3.TextButton
import com.dailysatori.shared.db.Article
```

Change `AiReferenceDetailSheet` signature to:

```kotlin
fun AiReferenceDetailSheet(
    state: AiReferenceDetailState,
    onDismiss: () -> Unit,
    onArticleClick: (Long) -> Unit = {},
)
```

Pass `onArticleClick` into `ReferenceDetailContent`.

Update `ReferenceDetailContent` signature and add article branch before diary:

```kotlin
state.article != null -> ArticleReferenceSummary(state.article, onArticleClick)
```

Add:

```kotlin
@Composable
private fun ArticleReferenceSummary(
    article: Article,
    onArticleClick: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = article.ai_title ?: article.title ?: "无标题文章",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        val summary = article.ai_content?.takeIf { it.isNotBlank() } ?: article.comment.orEmpty()
        if (summary.isNotBlank()) {
            Spacer(modifier = Modifier.height(Spacing.m))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(Spacing.m))
        TextButton(onClick = { onArticleClick(article.id) }) {
            Text("打开完整文章")
        }
    }
}
```

In `AiChatScreen`, update the sheet call:

```kotlin
AiReferenceDetailSheet(
    state = referenceDetailState,
    onDismiss = {
        showReferenceSheet = false
        referenceDetailViewModel.clear()
    },
    onArticleClick = { articleId ->
        showReferenceSheet = false
        referenceDetailViewModel.clear()
        onArticleClick(articleId)
    },
)
```

- [ ] **Step 5: Compile app**

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

### Task 5: Verification

**Files:**
- Verify all touched files.

- [ ] **Step 1: Run focused shared tests**

Run: `./gradlew :shared:allTests --tests "com.dailysatori.service.mcp.McpAgentPresentationTest"`

Expected: PASS.

- [ ] **Step 2: Run focused app tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.aichat.AiChatUiStateTest"`

Expected: PASS.

- [ ] **Step 3: Run required compile check**

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Install to connected Android device**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`

Expected: BUILD SUCCESSFUL and install completes.

- [ ] **Step 5: Launch the app**

Run: `adb shell am start -n com.dailysatori/.MainActivity`

Expected: Activity starts without error.

## Self-Review

- Spec coverage: ordered diary precision, reference expansion, unified detail sheet, article full-detail escape hatch, and verification are covered.
- Placeholder scan: no deferred implementation placeholders are present.
- Type consistency: helper names and ViewModel state fields are consistent across tasks.
- Commit policy: this plan omits commit steps because commits require explicit user request.

# AI Reference Detail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make AI assistant reference cards open their full article, diary, or book/viewpoint content while reusing existing UI components.

**Architecture:** Keep reference cards in `MessageBubble`, but pass the full `McpSearchResult` to the click handler. Add a small shared pure mapping function for supported open targets, navigate articles through the existing route, and show diary/book content in an AI-local `ModalBottomSheet` loaded from existing repositories.

**Tech Stack:** Kotlin Multiplatform shared module, Android Jetpack Compose Material 3, Koin ViewModel injection, SQLDelight repositories, kotlin.test.

---

## File Structure

- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentPresentation.kt`
  - Owns pure presentation helpers for MCP search results, including open-target mapping.
- Modify: `shared/src/commonTest/kotlin/com/dailysatori/service/mcp/McpAgentPresentationTest.kt`
  - Tests supported reference labels and open targets.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt`
  - Makes supported result cards clickable and passes the full result to the caller.
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiReferenceDetailViewModel.kt`
  - Loads diary/book/viewpoint records for the selected reference using existing repositories.
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiReferenceDetailSheet.kt`
  - Displays selected diary/book reference in a bottom sheet using existing `DiaryCard` and `ViewpointCard` where possible.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt`
  - Owns selected reference state and routes clicks to article navigation or the bottom sheet.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt`
  - Rename callback from article-specific to reference/article target if needed by call sites.
- Modify: `app/src/main/kotlin/com/dailysatori/core/navigation/NavHost.kt`
  - Keep article references on `ArticleDetailRoute`.
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`
  - Register `AiReferenceDetailViewModel`.

Do not commit during execution unless the user explicitly asks for commits.

---

### Task 1: Add Pure Reference Open Target Mapping

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentPresentation.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/mcp/McpAgentPresentationTest.kt`

- [ ] **Step 1: Write the failing tests**

Replace the current `onlyArticleSearchResultsCanOpenDetails` test in `McpAgentPresentationTest.kt` with:

```kotlin
@Test
fun articleDiaryAndBookSearchResultsCanOpenDetails() {
    assertEquals(true, canOpenSearchResult("article"))
    assertEquals(true, canOpenSearchResult("diary"))
    assertEquals(true, canOpenSearchResult("book"))
    assertEquals(false, canOpenSearchResult("unknown"))
}

@Test
fun mapsSearchResultsToOpenTargets() {
    assertEquals(SearchResultOpenTarget.Article, searchResultOpenTarget("article"))
    assertEquals(SearchResultOpenTarget.Diary, searchResultOpenTarget("diary"))
    assertEquals(SearchResultOpenTarget.Book, searchResultOpenTarget("book"))
    assertEquals(null, searchResultOpenTarget("unknown"))
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests com.dailysatori.service.mcp.McpAgentPresentationTest
```

Expected: FAIL because `SearchResultOpenTarget` and `searchResultOpenTarget` do not exist and `canOpenSearchResult("diary")`/`book` still return false.

- [ ] **Step 3: Implement the pure mapping**

Update `McpAgentPresentation.kt` near `canOpenSearchResult`:

```kotlin
enum class SearchResultOpenTarget {
    Article,
    Diary,
    Book,
}

fun searchResultOpenTarget(type: String): SearchResultOpenTarget? = when (type) {
    "article" -> SearchResultOpenTarget.Article
    "diary" -> SearchResultOpenTarget.Diary
    "book" -> SearchResultOpenTarget.Book
    else -> null
}

fun canOpenSearchResult(type: String): Boolean = searchResultOpenTarget(type) != null
```

Remove or replace the old one-line `canOpenSearchResult` implementation.

- [ ] **Step 4: Run the test and verify it passes**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests com.dailysatori.service.mcp.McpAgentPresentationTest
```

Expected: PASS.

---

### Task 2: Upgrade Reference Card Click Callback

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt`

- [ ] **Step 1: Change `MessageBubble` API**

Update the composable signature:

```kotlin
@Composable
fun MessageBubble(
    message: ChatMessageUi,
    onReferenceClick: (McpSearchResult) -> Unit = {},
)
```

- [ ] **Step 2: Pass callback through `SearchResultsSection`**

Change the section call and function signature:

```kotlin
SearchResultsSection(message.searchResults, onReferenceClick)
```

```kotlin
private fun SearchResultsSection(
    results: List<McpSearchResult>,
    onReferenceClick: (McpSearchResult) -> Unit,
)
```

- [ ] **Step 3: Pass callback through `SearchResultCard`**

Change the card invocation and signature:

```kotlin
SearchResultCard(result, onReferenceClick)
```

```kotlin
private fun SearchResultCard(
    result: McpSearchResult,
    onReferenceClick: (McpSearchResult) -> Unit,
)
```

- [ ] **Step 4: Make supported references clickable with the full result**

Update the `Surface` modifier:

```kotlin
modifier = Modifier
    .fillMaxWidth()
    .then(if (canOpen) Modifier.clickable { onReferenceClick(result) } else Modifier),
```

- [ ] **Step 5: Generalize the helper text**

Replace:

```kotlin
text = "点击查看文章",
```

with:

```kotlin
text = "点击查看详情",
```

---

### Task 3: Add Reference Detail ViewModel

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiReferenceDetailViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`

- [ ] **Step 1: Create detail state and loader**

Create `AiReferenceDetailViewModel.kt`:

```kotlin
package com.dailysatori.ui.feature.aichat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.service.mcp.McpSearchResult
import com.dailysatori.service.mcp.SearchResultOpenTarget
import com.dailysatori.service.mcp.searchResultOpenTarget
import com.dailysatori.shared.db.Book
import com.dailysatori.shared.db.Book_viewpoint
import com.dailysatori.shared.db.Diary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiReferenceDetailState(
    val isLoading: Boolean = false,
    val diary: Diary? = null,
    val book: Book? = null,
    val viewpoint: Book_viewpoint? = null,
    val error: String? = null,
)

class AiReferenceDetailViewModel(
    private val diaryRepo: DiaryRepository,
    private val bookRepo: BookRepository,
    private val viewpointRepo: BookViewpointRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AiReferenceDetailState())
    val state: StateFlow<AiReferenceDetailState> = _state.asStateFlow()

    fun load(result: McpSearchResult) {
        _state.value = AiReferenceDetailState(isLoading = true)
        viewModelScope.launch(Dispatchers.IO) {
            val nextState = when (searchResultOpenTarget(result.type)) {
                SearchResultOpenTarget.Diary -> loadDiary(result.id)
                SearchResultOpenTarget.Book -> loadBook(result.id)
                else -> AiReferenceDetailState(error = "内容不存在或已删除")
            }
            _state.value = nextState
        }
    }

    fun clear() {
        _state.value = AiReferenceDetailState()
    }

    private fun loadDiary(id: Long): AiReferenceDetailState {
        val diary = diaryRepo.getById(id)
        return if (diary == null) {
            AiReferenceDetailState(error = "内容不存在或已删除")
        } else {
            AiReferenceDetailState(diary = diary)
        }
    }

    private fun loadBook(id: Long): AiReferenceDetailState {
        val viewpoint = viewpointRepo.getById(id)
        if (viewpoint != null) {
            return AiReferenceDetailState(
                book = bookRepo.getById(viewpoint.book_id),
                viewpoint = viewpoint,
            )
        }
        val book = bookRepo.getById(id)
        val firstViewpoint = viewpointRepo.getByBookSync(id).firstOrNull()
        return if (book == null && firstViewpoint == null) {
            AiReferenceDetailState(error = "内容不存在或已删除")
        } else {
            AiReferenceDetailState(book = book, viewpoint = firstViewpoint)
        }
    }
}
```

- [ ] **Step 2: Register the ViewModel**

Add import in `ViewModelModule.kt`:

```kotlin
import com.dailysatori.ui.feature.aichat.AiReferenceDetailViewModel
```

Add registration after `AiChatViewModel`:

```kotlin
viewModel {
    AiReferenceDetailViewModel(
        diaryRepo = get<DiaryRepository>(),
        bookRepo = get<BookRepository>(),
        viewpointRepo = get<BookViewpointRepository>(),
    )
}
```

---

### Task 4: Add AI Reference Detail Bottom Sheet

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiReferenceDetailSheet.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/card/DiaryCard.kt` if deletion icon cannot be hidden without a small option

- [ ] **Step 1: Add optional delete visibility to `DiaryCard`**

Change `DiaryCard` signature:

```kotlin
fun DiaryCard(
    diary: Diary,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    showDelete: Boolean = true,
)
```

Wrap the existing `IconButton` at lines 127-137:

```kotlin
if (showDelete) {
    IconButton(
        onClick = onDelete,
        modifier = Modifier.size(24.dp),
    ) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = "删除",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(14.dp),
        )
    }
}
```

- [ ] **Step 2: Create the sheet composable**

Create `AiReferenceDetailSheet.kt`:

```kotlin
package com.dailysatori.ui.feature.aichat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.dailysatori.ui.component.card.DiaryCard
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.feature.book.ViewpointCard
import com.dailysatori.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiReferenceDetailSheet(
    state: AiReferenceDetailState,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.m)
                .padding(bottom = Spacing.xxl),
        ) {
            Text(
                text = "引用详情",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = Spacing.m),
            )
            ReferenceDetailContent(state)
        }
    }
}

@Composable
private fun ReferenceDetailContent(state: AiReferenceDetailState) {
    when {
        state.isLoading -> LoadingIndicator()
        state.diary != null -> DiaryCard(
            diary = state.diary,
            onClick = {},
            onDelete = {},
            showDelete = false,
        )
        state.viewpoint != null -> ViewpointCard(
            title = state.viewpoint.title,
            content = state.viewpoint.content,
            example = state.viewpoint.example,
            bookTitle = state.book?.let { "《${it.title}》 · ${it.author}" }.orEmpty(),
        )
        state.book != null -> BookReferenceSummary(state.book.title, state.book.author, state.book.introduction)
        else -> EmptyState(
            icon = Icons.Default.MenuBook,
            title = state.error ?: "内容不存在或已删除",
        )
    }
}

@Composable
private fun BookReferenceSummary(title: String, author: String, introduction: String) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.align(Alignment.CenterStart)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (author.isNotBlank()) {
                Text(author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (introduction.isNotBlank()) {
                Text(
                    introduction,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.m),
                )
            }
        }
    }
}
```

---

### Task 5: Wire Reference Clicks Into AI Chat

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/navigation/NavHost.kt`

- [ ] **Step 1: Update `AiChatScreen` signature and state**

Change signature and add imports:

```kotlin
import com.dailysatori.service.mcp.McpSearchResult
import com.dailysatori.service.mcp.SearchResultOpenTarget
import com.dailysatori.service.mcp.searchResultOpenTarget
```

```kotlin
fun AiChatScreen(onArticleClick: (Long) -> Unit = {}) {
```

can remain article-specific because only articles navigate out. Inside the composable add:

```kotlin
val referenceDetailViewModel: AiReferenceDetailViewModel = koinViewModel()
val referenceDetailState by referenceDetailViewModel.state.collectAsState()
var showReferenceSheet by remember { mutableStateOf(false) }
```

- [ ] **Step 2: Add reference click handler**

Inside `AiChatScreen`, before `Scaffold`, add:

```kotlin
fun openReference(result: McpSearchResult) {
    when (searchResultOpenTarget(result.type)) {
        SearchResultOpenTarget.Article -> onArticleClick(result.id)
        SearchResultOpenTarget.Diary,
        SearchResultOpenTarget.Book -> {
            showReferenceSheet = true
            referenceDetailViewModel.load(result)
        }
        null -> Unit
    }
}
```

- [ ] **Step 3: Pass reference callback to message bubbles**

Replace:

```kotlin
MessageBubble(message = message, onArticleClick = onArticleClick)
```

with:

```kotlin
MessageBubble(message = message, onReferenceClick = ::openReference)
```

- [ ] **Step 4: Show and clear the detail sheet**

After `MemorySearchSheet` block add:

```kotlin
if (showReferenceSheet) {
    AiReferenceDetailSheet(
        state = referenceDetailState,
        onDismiss = {
            showReferenceSheet = false
            referenceDetailViewModel.clear()
        },
    )
}
```

- [ ] **Step 5: Confirm parent wiring still compiles**

Keep existing `HomeScreen(onAiArticleClick = ...)` and `NavHost` article routing unless compilation shows parameter names changed elsewhere. The article path should remain:

```kotlin
HomeScreen(
    onAiArticleClick = { articleId -> navController.navigate(ArticleDetailRoute(articleId)) },
)
```

---

### Task 6: Verification

**Files:**
- No source changes expected unless verification exposes a compile error.

- [ ] **Step 1: Run shared presentation tests**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests com.dailysatori.service.mcp.McpAgentPresentationTest
```

Expected: PASS.

- [ ] **Step 2: Run Android compile check**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Install and launch on connected device**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
```

Expected: BUILD SUCCESSFUL and APK installed.

Run:

```bash
adb shell am start -n com.dailysatori/.MainActivity
```

Expected: Activity starts without terminal error.

---

## Self-Review Notes

- Spec coverage: article navigation, diary bottom sheet, book/viewpoint bottom sheet, unsupported references, missing content, and compile/install verification are covered.
- Placeholder scan: no `TBD`/`TODO` placeholders are present.
- Type consistency: plan uses existing `McpSearchResult`, `Diary`, `Book`, `Book_viewpoint`, repository method names verified from source, and existing `ArticleDetailRoute` remains unchanged.

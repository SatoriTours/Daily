# Remote Article List Content Detail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make remote article details display the full `RemoteArticle` returned by the list/source API instead of relying on a hidden `/articles/{id}` detail API.

**Architecture:** Keep `RemoteArticleDetailScreen` unchanged and change navigation/state to pass full `RemoteArticle` objects from list rows into detail state. For unified-news citation clicks that only have an id, prefer already-cached local articles and stop silently deriving a remote detail URL.

**Tech Stack:** Kotlin, Android Compose, Koin ViewModels, shared KMP remote-news models, Gradle unit tests.

---

## File Structure

- `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt`: Change `openArticle(id)` to `openArticle(article: RemoteArticle)` and remove list-detail calls to `remoteNewsService.fetchArticle`.
- `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsScreen.kt`: Pass the clicked `RemoteArticle` object from list cards.
- `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`: Add direct article opening for remote-source tab rows and change citation-only remote article handling to local-cache-first behavior.
- `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`: Pass clicked `RemoteArticle` objects from remote-source article cards.
- `app/src/test/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsUiBehaviorTest.kt`: Add source-level regression for no list-detail fetch and object-based open.
- `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`: Add source-level regression for unified remote-source tab direct detail and citation no hidden remote fetch.

## Task 1: Remote News List Uses List Article Object

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsScreen.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsUiBehaviorTest.kt`

- [ ] **Step 1: Write the failing test**

Add this test to `RemoteNewsUiBehaviorTest`:

```kotlin
@Test
fun remoteNewsListOpensArticleFromListPayloadWithoutDetailApi() {
    val viewModel = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt").readText()
    val screen = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsScreen.kt").readText()
    val openArticleBody = viewModel.substringAfter("fun openArticle(").substringBefore("fun toggleSelectedArticleFavorite")

    assertTrue(viewModel.contains("fun openArticle(article: RemoteArticle)"))
    assertTrue(openArticleBody.contains("selectedArticle = article"))
    assertFalse(openArticleBody.contains("remoteNewsService.fetchArticle"))
    assertTrue(screen.contains("viewModel.openArticle(it)"))
    assertFalse(screen.contains("viewModel.openArticle(it.id)"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.remotenews.RemoteNewsUiBehaviorTest.remoteNewsListOpensArticleFromListPayloadWithoutDetailApi"
```

Expected: FAIL because `openArticle` currently takes `id: Long` and calls `remoteNewsService.fetchArticle`.

- [ ] **Step 3: Implement object-based open in `RemoteNewsViewModel`**

Replace the current `fun openArticle(id: Long)` body with:

```kotlin
fun openArticle(article: RemoteArticle) {
    viewModelScope.launch(Dispatchers.IO) {
        _state.update { it.copy(isLoading = true, detailError = null) }
        val local = articleRepo.findLocalArticleForRemote(article)
        _state.update {
            it.copy(
                selectedArticle = article,
                selectedArticleLocalId = local?.id,
                selectedArticleIsFavorite = local?.is_favorite == 1L,
                isLoading = false,
            )
        }
    }
}
```

- [ ] **Step 4: Pass the list article object from `RemoteNewsScreen`**

Change the articles row from:

```kotlin
RemoteNewsMode.ARTICLES -> items(state.articles, key = { it.id }) { RemoteArticleSummaryCard(it) { viewModel.openArticle(it.id) } }
```

to:

```kotlin
RemoteNewsMode.ARTICLES -> items(state.articles, key = { it.id }) { RemoteArticleSummaryCard(it) { viewModel.openArticle(it) } }
```

- [ ] **Step 5: Run test to verify it passes**

Run the same `:app:testDebugUnitTest --tests ...remoteNewsListOpensArticleFromListPayloadWithoutDetailApi` command.

Expected: PASS.

## Task 2: Unified Remote Source Tab Uses Source Article Object

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Write the failing test**

Add this test to `UnifiedNewsBehaviorTest`:

```kotlin
@Test
fun unifiedRemoteSourceListOpensArticleFromLoadedPayloadWithoutDetailApi() {
    val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()
    val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()
    val openSourceArticleBody = viewModel.substringAfter("fun openSourceArticle(").substringBefore("fun toggleSelectedRemoteArticleFavorite")

    assertTrue(viewModel.contains("fun openSourceArticle(article: RemoteArticle)"))
    assertTrue(openSourceArticleBody.contains("selectedRemoteArticle = article"))
    assertFalse(openSourceArticleBody.contains("openCitationSource"))
    assertTrue(screen.contains("viewModel.openSourceArticle(article)"))
    assertFalse(screen.contains("viewModel.openSourceArticle(selection.id, article.id)"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.UnifiedNewsBehaviorTest.unifiedRemoteSourceListOpensArticleFromLoadedPayloadWithoutDetailApi"
```

Expected: FAIL because `openSourceArticle` currently takes ids and routes through `openCitationSource`.

- [ ] **Step 3: Implement object-based source article open**

Replace `fun openSourceArticle(sourceId: Long, articleId: Long)` with:

```kotlin
fun openSourceArticle(article: RemoteArticle) {
    viewModelScope.launch(Dispatchers.IO) {
        _state.update { it.clearSelectedSourceDetail().copy(selectedRemoteArticle = article, isLoading = true, error = null) }
        val local = articleRepo.findLocalArticleForRemote(article)
        _state.update { state ->
            if (state.selectedRemoteArticle?.id == article.id) {
                state.copy(
                    selectedRemoteArticleLocalId = local?.id,
                    selectedRemoteArticleIsFavorite = local?.is_favorite == 1L,
                    isLoading = false,
                )
            } else {
                state
            }
        }
    }
}
```

- [ ] **Step 4: Pass the loaded article from `UnifiedNewsScreen`**

Change:

```kotlin
RemoteArticleSummaryCard(article) { viewModel.openSourceArticle(selection.id, article.id) }
```

to:

```kotlin
RemoteArticleSummaryCard(article) { viewModel.openSourceArticle(article) }
```

- [ ] **Step 5: Run test to verify it passes**

Run the same `:app:testDebugUnitTest --tests ...unifiedRemoteSourceListOpensArticleFromLoadedPayloadWithoutDetailApi` command.

Expected: PASS.

## Task 3: Citation-Only Remote Article Stops Hidden Detail Fetch

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Write the failing test**

Add this test to `UnifiedNewsBehaviorTest`:

```kotlin
@Test
fun unifiedCitationRemoteArticleDoesNotDeriveHiddenDetailApi() {
    val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()
    val openRemoteArticleBody = viewModel.substringAfter("private fun openRemoteArticle").substringBefore("private fun fetchSourceArticles")

    assertFalse(openRemoteArticleBody.contains("remoteNewsService.fetchArticle"))
    assertTrue(openRemoteArticleBody.contains("articleRepo.getById(id)"))
    assertTrue(openRemoteArticleBody.contains("文章内容不可用"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.UnifiedNewsBehaviorTest.unifiedCitationRemoteArticleDoesNotDeriveHiddenDetailApi"
```

Expected: FAIL because citation remote article currently calls `remoteNewsService.fetchArticle`.

- [ ] **Step 3: Replace citation detail fetch with local-cache-first behavior**

Replace `private fun openRemoteArticle(id: Long, remoteSourceId: Long?, token: Long)` with a local-cache version:

```kotlin
private fun openRemoteArticle(id: Long, remoteSourceId: Long?, token: Long) {
    detailLoadJob = viewModelScope.launch(Dispatchers.IO) {
        try {
            ifLatestDetailRequest(token) { it.copy(isLoading = true) }
            val local = articleRepo.getById(id)
            if (local == null) {
                ifLatestDetailRequest(token) { it.copy(error = "文章内容不可用，请刷新新闻汇总或远程来源", isLoading = false) }
                return@launch
            }
            ifLatestDetailRequest(token) {
                it.copy(navigationTarget = UnifiedNewsNavigationTarget.LocalArticle(local.id), isLoading = false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ifLatestDetailRequest(token) { it.copy(error = "文章内容不可用，请刷新新闻汇总或远程来源", isLoading = false) }
        }
    }
}
```

Note: Keep the `remoteSourceId` parameter for signature compatibility with existing routing; it is intentionally unused after this change.

- [ ] **Step 4: Run test to verify it passes**

Run the same `:app:testDebugUnitTest --tests ...unifiedCitationRemoteArticleDoesNotDeriveHiddenDetailApi` command.

Expected: PASS.

## Task 4: Run Focused and Required Verification

**Files:**
- No source edits.

- [ ] **Step 1: Run focused tests**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.dailysatori.ui.feature.remotenews.RemoteNewsUiBehaviorTest.remoteNewsListOpensArticleFromListPayloadWithoutDetailApi" \
  --tests "com.dailysatori.UnifiedNewsBehaviorTest.unifiedRemoteSourceListOpensArticleFromLoadedPayloadWithoutDetailApi" \
  --tests "com.dailysatori.UnifiedNewsBehaviorTest.unifiedCitationRemoteArticleDoesNotDeriveHiddenDetailApi"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run required compile check**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run required debug build**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Try device install if a device is connected**

Run:

```bash
adb devices
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
```

Expected if a device is connected: install succeeds. If `adb devices` is empty, record that install could not run because no device was connected.

## Self-Review

- Spec coverage: Covers remote list details, unified source-tab details, citation-only remote article behavior, and verification.
- Placeholder scan: No TBD/TODO placeholders.
- Type consistency: Uses existing `RemoteArticle`, `UnifiedNewsNavigationTarget.LocalArticle`, `articleRepo.getById`, and `RemoteArticleDetailScreen` paths.

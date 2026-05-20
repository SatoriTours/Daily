# Remote Article Favorites Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let remote articles opened from article feeds or summaries be saved as local favorite articles and make AI Chat prefer those favorites.

**Architecture:** Reuse the existing local `article` table and `ArticleRepository` favorite flow. Add a small remote-to-local favorite mapper/helper in shared code, wire it into remote and unified news ViewModels, expose favorite state/actions in `RemoteArticleDetailScreen`, and rank favorite article search results first for AI Chat.

**Tech Stack:** Kotlin Multiplatform shared module, SQLDelight, Android Jetpack Compose, Koin ViewModel injection, Kotlin test/JUnit, Gradle.

---

## File Structure

- Modify `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`: add URL lookup/update queries and favorite-prioritized search query.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/data/repository/ArticleRepository.kt`: add remote favorite persistence helper and favorite-prioritized search sync method.
- Create `shared/src/commonMain/kotlin/com/dailysatori/data/repository/RemoteArticleFavoriteMapper.kt`: pure mapping/parsing helpers from `RemoteArticle` to local article fields.
- Create `shared/src/commonTest/kotlin/com/dailysatori/data/repository/RemoteArticleFavoriteMapperTest.kt`: verify mapping behavior without Android dependencies.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpToolRegistry.kt`: use favorite-prioritized article search.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentPrompts.kt`: instruct AI Chat to prefer favorite articles for article questions.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt`: inject `ArticleRepository`, track selected remote article local favorite state, save/toggle favorite.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`: inject `ArticleRepository`, track selected citation remote article favorite state, save/toggle favorite.
- Modify `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`: pass `ArticleRepository` to both ViewModels.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt`: add favorite action parameters and UI button.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsScreen.kt`: pass favorite state/action to `RemoteArticleDetailScreen`.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`: pass favorite state/action to `RemoteArticleDetailScreen` for citations.
- Modify `app/src/test/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailLayoutTest.kt`: source-level guard for the favorite action wiring.

## Task 1: Pure Remote Article Mapping

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/RemoteArticleFavoriteMapper.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/data/repository/RemoteArticleFavoriteMapperTest.kt`

- [ ] **Step 1: Write failing mapper tests**

Create `shared/src/commonTest/kotlin/com/dailysatori/data/repository/RemoteArticleFavoriteMapperTest.kt`:

```kotlin
package com.dailysatori.data.repository

import com.dailysatori.service.remotenews.RemoteArticle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteArticleFavoriteMapperTest {
    @Test
    fun mapsRemoteArticleIntoLocalFavoriteFields() {
        val article = RemoteArticle(
            id = 42,
            title = "Remote Title",
            url = " https://example.com/article ",
            summary = "Summary text",
            viewpoints = listOf("Point A", " ", "Point B"),
            coverUrl = "https://example.com/cover.jpg",
            content = "# Original",
            processedAt = "2026-05-20T08:30:00Z",
        )

        val fields = article.toLocalFavoriteArticleFields()

        assertEquals("Remote Title", fields.title)
        assertEquals("Remote Title", fields.aiTitle)
        assertEquals("https://example.com/article", fields.url)
        assertEquals("Summary text\n\n## 关键观点\n\n- Point A\n- Point B", fields.aiContent)
        assertEquals("# Original", fields.aiMarkdownContent)
        assertEquals("https://example.com/cover.jpg", fields.coverImageUrl)
        assertEquals("completed", fields.status)
        assertEquals(1L, fields.isFavorite)
        assertNotNull(fields.pubDate)
    }

    @Test
    fun omitsBlankSummaryAndUsesViewpointsOnly() {
        val article = RemoteArticle(
            id = 7,
            title = "Title",
            url = "https://example.com/only-viewpoints",
            summary = " ",
            viewpoints = listOf("Only point"),
        )

        val fields = article.toLocalFavoriteArticleFields()

        assertEquals("## 关键观点\n\n- Only point", fields.aiContent)
    }

    @Test
    fun returnsNullPubDateForUnparseableRemoteTime() {
        val article = RemoteArticle(
            id = 8,
            title = "Title",
            url = "https://example.com/no-date",
            createdAt = "not-a-date",
        )

        val fields = article.toLocalFavoriteArticleFields()

        assertNull(fields.pubDate)
        assertTrue(fields.url!!.contains("example.com"))
    }
}
```

- [ ] **Step 2: Run mapper tests and verify failure**

Run: `./gradlew :shared:allTests --tests "com.dailysatori.data.repository.RemoteArticleFavoriteMapperTest"`

Expected: FAIL because `RemoteArticleFavoriteMapper.kt` and `toLocalFavoriteArticleFields()` do not exist.

- [ ] **Step 3: Add mapper implementation**

Create `shared/src/commonMain/kotlin/com/dailysatori/data/repository/RemoteArticleFavoriteMapper.kt`:

```kotlin
package com.dailysatori.data.repository

import com.dailysatori.service.remotenews.RemoteArticle
import kotlinx.datetime.Instant

data class LocalFavoriteArticleFields(
    val title: String?,
    val aiTitle: String?,
    val aiContent: String?,
    val aiMarkdownContent: String?,
    val url: String?,
    val isFavorite: Long = 1L,
    val comment: String? = null,
    val status: String = "completed",
    val coverImage: String? = null,
    val coverImageUrl: String?,
    val pubDate: Long?,
)

fun RemoteArticle.toLocalFavoriteArticleFields(): LocalFavoriteArticleFields {
    val cleanTitle = title?.trim()?.takeIf { it.isNotBlank() }
    return LocalFavoriteArticleFields(
        title = cleanTitle,
        aiTitle = cleanTitle,
        aiContent = remoteArticleSummaryForLocalFavorite(summary, viewpoints),
        aiMarkdownContent = content?.trim()?.takeIf { it.isNotBlank() },
        url = url?.trim()?.takeIf { it.isNotBlank() },
        coverImageUrl = coverUrl?.trim()?.takeIf { it.isNotBlank() },
        pubDate = remoteArticleTimeMillis(processedAt) ?: remoteArticleTimeMillis(createdAt),
    )
}

internal fun remoteArticleSummaryForLocalFavorite(summary: String?, viewpoints: List<String>): String? {
    val cleanSummary = summary?.trim()?.takeIf { it.isNotBlank() }
    val cleanViewpoints = viewpoints.map { it.trim() }.filter { it.isNotBlank() }
    val viewpointMarkdown = cleanViewpoints
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = "\n") { "- $it" }
        ?.let { "## 关键观点\n\n$it" }
    return listOfNotNull(cleanSummary, viewpointMarkdown)
        .joinToString("\n\n")
        .takeIf { it.isNotBlank() }
}

internal fun remoteArticleTimeMillis(value: String?): Long? = try {
    value?.trim()?.takeIf { it.isNotBlank() }?.let { Instant.parse(it).toEpochMilliseconds() }
} catch (_: Exception) {
    null
}
```

- [ ] **Step 4: Run mapper tests and verify pass**

Run: `./gradlew :shared:allTests --tests "com.dailysatori.data.repository.RemoteArticleFavoriteMapperTest"`

Expected: PASS.

- [ ] **Step 5: Commit mapper task**

Run:

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/data/repository/RemoteArticleFavoriteMapper.kt shared/src/commonTest/kotlin/com/dailysatori/data/repository/RemoteArticleFavoriteMapperTest.kt
git commit -m "feat: map remote articles to local favorites"
```

## Task 2: Repository Persistence And Favorite-Prioritized Search

**Files:**
- Modify: `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/ArticleRepository.kt`

- [ ] **Step 1: Add SQLDelight queries**

In `DailySatori.sq`, add these queries near the existing article queries:

```sql
selectArticleByUrlNullable:
SELECT * FROM article WHERE url = ?;

searchArticlesFavoriteFirst:
SELECT * FROM article
WHERE title LIKE '%' || ? || '%'
   OR ai_title LIKE '%' || ? || '%'
   OR ai_content LIKE '%' || ? || '%'
ORDER BY is_favorite DESC, created_at DESC;

markArticleFavoriteByUrl:
UPDATE article SET
    title = COALESCE(NULLIF(title, ''), ?),
    ai_title = COALESCE(NULLIF(ai_title, ''), ?),
    ai_content = COALESCE(NULLIF(ai_content, ''), ?),
    ai_markdown_content = COALESCE(NULLIF(ai_markdown_content, ''), ?),
    is_favorite = 1,
    status = COALESCE(NULLIF(status, ''), ?),
    cover_image_url = COALESCE(NULLIF(cover_image_url, ''), ?),
    pub_date = COALESCE(pub_date, ?),
    updated_at = ?
WHERE url = ?;
```

- [ ] **Step 2: Generate SQLDelight code and verify query syntax**

Run: `./gradlew :shared:generateCommonMainDailySatoriDatabaseInterface`

Expected: PASS with generated query methods available.

- [ ] **Step 3: Implement repository helpers**

In `ArticleRepository.kt`, add `RemoteArticle` import:

```kotlin
import com.dailysatori.service.remotenews.RemoteArticle
```

Add these methods after `getById(id: Long)` and near existing sync/search helpers:

```kotlin
fun getByUrl(url: String): Article? = q.selectArticleByUrlNullable(url).executeAsOneOrNull()

fun saveRemoteArticleAsFavorite(remoteArticle: RemoteArticle): Article? {
    val fields = remoteArticle.toLocalFavoriteArticleFields()
    val url = fields.url
    if (url.isNullOrBlank()) return insertRemoteArticleFavoriteWithoutUrl(fields)

    val existing = q.selectArticleByUrlNullable(url).executeAsOneOrNull()
    return if (existing == null) {
        val id = insert(
            title = fields.title,
            aiTitle = fields.aiTitle,
            aiContent = fields.aiContent,
            aiMarkdownContent = fields.aiMarkdownContent,
            url = url,
            isFavorite = fields.isFavorite,
            comment = fields.comment,
            status = fields.status,
            coverImage = fields.coverImage,
            coverImageUrl = fields.coverImageUrl,
            pubDate = fields.pubDate,
        )
        getById(id)
    } else {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.markArticleFavoriteByUrl(
            fields.title,
            fields.aiTitle,
            fields.aiContent,
            fields.aiMarkdownContent,
            fields.status,
            fields.coverImageUrl,
            fields.pubDate,
            now,
            url,
        )
        q.selectArticleByUrlNullable(url).executeAsOneOrNull()
    }
}

fun searchFavoriteFirstSync(query: String): List<Article> =
    q.searchArticlesFavoriteFirst(query, query, query).executeAsList()

private fun insertRemoteArticleFavoriteWithoutUrl(fields: LocalFavoriteArticleFields): Article? {
    q.insertArticle(
        fields.title,
        fields.aiTitle,
        fields.aiContent,
        fields.aiMarkdownContent,
        null,
        fields.isFavorite,
        fields.comment,
        fields.status,
        fields.coverImage,
        fields.coverImageUrl,
        fields.pubDate,
        kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
        kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
    )
    return q.selectArticlesPaginated(1, 0).executeAsOneOrNull()
}
```

- [ ] **Step 4: Update MCP search to use favorite-first results**

In `McpToolRegistry.kt`, change `searchArticles` implementation from:

```kotlin
val results = searchWithKeywords(keyword) { kw -> articleRepo.searchSync(kw) }
```

to:

```kotlin
val results = searchWithKeywords(keyword) { kw -> articleRepo.searchFavoriteFirstSync(kw) }
```

- [ ] **Step 5: Compile shared code**

Run: `./gradlew :shared:compileKotlinMetadata`

Expected: PASS.

- [ ] **Step 6: Commit repository task**

Run:

```bash
git add shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq shared/src/commonMain/kotlin/com/dailysatori/data/repository/ArticleRepository.kt shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpToolRegistry.kt
git commit -m "feat: persist remote articles as favorites"
```

## Task 3: Remote Article Favorite UI Contract

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailLayoutTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt`

- [ ] **Step 1: Write failing source-level UI contract test**

Append this test to `RemoteArticleDetailLayoutTest`:

```kotlin
@Test
fun remoteArticleDetailExposesFavoriteAction() {
    val source = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt").readText()

    assertTrue(source.contains("isFavorite: Boolean = false"))
    assertTrue(source.contains("onFavoriteClick: () -> Unit = {}"))
    assertTrue(source.contains("Icons.Default.Favorite"))
    assertTrue(source.contains("Icons.Default.FavoriteBorder"))
    assertTrue(source.contains("contentDescription = if (isFavorite) \"取消收藏\" else \"收藏\""))
}
```

- [ ] **Step 2: Run UI contract test and verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.remotenews.RemoteArticleDetailLayoutTest.remoteArticleDetailExposesFavoriteAction"`

Expected: FAIL because the screen has no favorite parameters/action yet.

- [ ] **Step 3: Add favorite action to remote article detail**

In `RemoteArticleDetailScreen.kt`, add imports:

```kotlin
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
```

Change the composable signature to:

```kotlin
fun RemoteArticleDetailScreen(
    article: RemoteArticle,
    onBack: () -> Unit,
    isFavorite: Boolean = false,
    onFavoriteClick: () -> Unit = {},
) {
```

Add this `IconButton` before the browser button in `actions`:

```kotlin
IconButton(onClick = onFavoriteClick) {
    Icon(
        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
        contentDescription = if (isFavorite) "取消收藏" else "收藏",
        tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
```

- [ ] **Step 4: Run UI contract test and verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.remotenews.RemoteArticleDetailLayoutTest.remoteArticleDetailExposesFavoriteAction"`

Expected: PASS.

- [ ] **Step 5: Commit UI contract task**

Run:

```bash
git add app/src/test/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailLayoutTest.kt app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt
git commit -m "feat: add remote article favorite action"
```

## Task 4: ViewModel Wiring For Remote And Unified News

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`

- [ ] **Step 1: Add state fields and repository injection in `RemoteNewsViewModel`**

Add import:

```kotlin
import com.dailysatori.data.repository.ArticleRepository
```

Add state fields:

```kotlin
val selectedArticleLocalId: Long? = null,
val selectedArticleIsFavorite: Boolean = false,
```

Change constructor:

```kotlin
class RemoteNewsViewModel(
    private val settingRepo: SettingRepository,
    private val remoteNewsService: RemoteNewsService,
    private val articleRepo: ArticleRepository,
) : ViewModel() {
```

After successful `fetchArticle`, compute local match:

```kotlin
is RemoteNewsResult.Success -> {
    val local = result.value.article.url?.trim()?.takeIf { url -> url.isNotBlank() }?.let(articleRepo::getByUrl)
    _state.update {
        it.copy(
            selectedArticle = result.value.article,
            selectedArticleLocalId = local?.id,
            selectedArticleIsFavorite = local?.is_favorite == 1L,
            isLoading = false,
        )
    }
}
```

Add toggle method:

```kotlin
fun toggleSelectedArticleFavorite() {
    val article = _state.value.selectedArticle ?: return
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val localId = _state.value.selectedArticleLocalId
            if (localId != null) {
                articleRepo.toggleFavorite(localId)
                val updated = articleRepo.getById(localId)
                _state.update { it.copy(selectedArticleIsFavorite = updated?.is_favorite == 1L) }
            } else {
                val saved = articleRepo.saveRemoteArticleAsFavorite(article)
                _state.update {
                    it.copy(
                        selectedArticleLocalId = saved?.id,
                        selectedArticleIsFavorite = saved?.is_favorite == 1L,
                    )
                }
            }
        } catch (_: Exception) {
            _state.update { it.copy(error = "收藏文章失败，请稍后重试") }
        }
    }
}
```

Update `closeArticle()` to clear local favorite fields:

```kotlin
fun closeArticle() = _state.update {
    it.copy(selectedArticle = null, selectedArticleLocalId = null, selectedArticleIsFavorite = false)
}
```

- [ ] **Step 2: Add equivalent state and toggle in `UnifiedNewsViewModel`**

Add import:

```kotlin
import com.dailysatori.data.repository.ArticleRepository
```

Add fields to `UnifiedNewsState`:

```kotlin
val selectedRemoteArticleLocalId: Long? = null,
val selectedRemoteArticleIsFavorite: Boolean = false,
```

Add constructor dependency after `remoteNewsSourceRepo`:

```kotlin
private val articleRepo: ArticleRepository,
```

In `openRemoteArticle`, replace success update with:

```kotlin
is RemoteNewsResult.Success -> {
    val local = result.value.article.url?.trim()?.takeIf { url -> url.isNotBlank() }?.let(articleRepo::getByUrl)
    ifLatestDetailRequest(token) {
        it.copy(
            selectedRemoteArticle = result.value.article,
            selectedRemoteArticleLocalId = local?.id,
            selectedRemoteArticleIsFavorite = local?.is_favorite == 1L,
            isLoading = false,
        )
    }
}
```

Add method:

```kotlin
fun toggleSelectedRemoteArticleFavorite() {
    val article = _state.value.selectedRemoteArticle ?: return
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val localId = _state.value.selectedRemoteArticleLocalId
            if (localId != null) {
                articleRepo.toggleFavorite(localId)
                val updated = articleRepo.getById(localId)
                _state.update { it.copy(selectedRemoteArticleIsFavorite = updated?.is_favorite == 1L) }
            } else {
                val saved = articleRepo.saveRemoteArticleAsFavorite(article)
                _state.update {
                    it.copy(
                        selectedRemoteArticleLocalId = saved?.id,
                        selectedRemoteArticleIsFavorite = saved?.is_favorite == 1L,
                    )
                }
            }
        } catch (_: Exception) {
            _state.update { it.copy(error = "收藏文章失败，请稍后重试") }
        }
    }
}
```

Update `clearSelectedSourceDetail()`:

```kotlin
private fun UnifiedNewsState.clearSelectedSourceDetail(): UnifiedNewsState = copy(
    selectedRemoteDigest = null,
    selectedRemoteArticle = null,
    selectedRemoteArticleLocalId = null,
    selectedRemoteArticleIsFavorite = false,
)
```

- [ ] **Step 3: Update Koin dependencies**

In `ViewModelModule.kt`, replace:

```kotlin
viewModel { UnifiedNewsViewModel(get(), get(), get(), get(), get(), com.dailysatori.BuildConfig.DEBUG) }
```

with:

```kotlin
viewModel { UnifiedNewsViewModel(get(), get(), get(), get(), get(), get<ArticleRepository>(), com.dailysatori.BuildConfig.DEBUG) }
```

Add RemoteNewsViewModel wiring if it is registered in another module; if this file does not register it, locate its module with `grep "RemoteNewsViewModel" app/src/main/kotlin/com/dailysatori/core/di -n` and add `get<ArticleRepository>()` to that constructor call.

- [ ] **Step 4: Pass state/action to screens**

In `RemoteNewsScreen.kt`, replace:

```kotlin
state.selectedArticle != null -> RemoteArticleDetailScreen(state.selectedArticle!!, viewModel::closeArticle)
```

with:

```kotlin
state.selectedArticle != null -> RemoteArticleDetailScreen(
    article = state.selectedArticle!!,
    onBack = viewModel::closeArticle,
    isFavorite = state.selectedArticleIsFavorite,
    onFavoriteClick = viewModel::toggleSelectedArticleFavorite,
)
```

In `UnifiedNewsScreen.kt`, replace:

```kotlin
RemoteArticleDetailScreen(article = state.selectedRemoteArticle!!, onBack = viewModel::closeSourceDetail)
```

with:

```kotlin
RemoteArticleDetailScreen(
    article = state.selectedRemoteArticle!!,
    onBack = viewModel::closeSourceDetail,
    isFavorite = state.selectedRemoteArticleIsFavorite,
    onFavoriteClick = viewModel::toggleSelectedRemoteArticleFavorite,
)
```

- [ ] **Step 5: Compile app Kotlin**

Run: `./gradlew :app:compileDebugKotlin`

Expected: PASS. If constructor registration for `RemoteNewsViewModel` is outside `ViewModelModule.kt`, update that exact registration and rerun.

- [ ] **Step 6: Commit wiring task**

Run:

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt
git commit -m "feat: wire remote article favorite state"
```

## Task 5: AI Chat Favorite Weighting Prompt

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentPrompts.kt`

- [ ] **Step 1: Update prompt article guidance**

In `McpAgentPrompts.kt`, replace article tool guidance:

```kotlin
- `get_latest_articles`: 获取最新收藏的文章
- `search_articles`: 按关键词搜索文章
- `get_favorite_articles`: 获取标记为喜爱的文章
```

with:

```kotlin
- `get_latest_articles`: 获取最新保存的文章
- `search_articles`: 按关键词搜索文章，结果会优先返回已收藏文章
- `get_favorite_articles`: 获取标记为喜爱的文章；当用户问文章、想看的内容、收藏内容或阅读管理时，应优先使用这个工具
```

Add this bullet under `## 工具选择策略`:

```kotlin
- 问文章相关问题时，优先查询 `get_favorite_articles` 或使用会优先返回收藏结果的 `search_articles`；收藏文章的相关性高于普通文章。
```

- [ ] **Step 2: Run shared compile**

Run: `./gradlew :shared:compileKotlinMetadata`

Expected: PASS.

- [ ] **Step 3: Commit prompt task**

Run:

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentPrompts.kt
git commit -m "feat: prioritize favorite articles in chat"
```

## Task 6: Full Verification And Device Install

**Files:**
- No new files. Verify all changed files.

- [ ] **Step 1: Run focused tests**

Run:

```bash
./gradlew :shared:allTests --tests "com.dailysatori.data.repository.RemoteArticleFavoriteMapperTest" && ./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.remotenews.RemoteArticleDetailLayoutTest"
```

Expected: PASS.

- [ ] **Step 2: Run required compile check**

Run: `./gradlew :app:compileDebugKotlin`

Expected: PASS.

- [ ] **Step 3: Install on connected device**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`

Expected: PASS and APK installed.

- [ ] **Step 4: Launch app**

Run: `adb shell am start -n com.dailysatori/.MainActivity`

Expected: Activity starts successfully.

- [ ] **Step 5: Manual smoke test**

Open a remote article from the remote article list, tap favorite, navigate to `本地收藏`, and confirm the article appears. Open a digest summary, tap one referenced article, favorite it, and confirm it appears in `本地收藏`. Ask AI Chat about a topic from the favorited article and confirm the returned references include the saved article when relevant.

- [ ] **Step 6: Final commit if verification changed generated files**

If SQLDelight generated files or formatting produced tracked changes, inspect `git status --short` and `git diff`, then commit intended changes only:

```bash
git status --short
git diff
git add <intended files>
git commit -m "chore: verify remote article favorites"
```

Expected: No commit if there are no new tracked changes.

## Self-Review Notes

- Spec coverage: remote detail favorite action, summary citation detail path, local favorite persistence, unified favorite list reuse, and AI Chat favorite weighting are covered by Tasks 1-6.
- No database migration is planned because the existing `article` schema already has `is_favorite` and URL uniqueness.
- The plan intentionally avoids remote server favorite APIs and bidirectional sync, matching non-goals.

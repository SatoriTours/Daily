# Unified News Local Cache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cache every remote article used by unified news summaries into the local `article` table without re-fetching or reprocessing it.

**Architecture:** Add a non-favorite remote article cache path to `ArticleRepository`, then have `UnifiedNewsSummaryService` cache filtered remote articles before summary generation. Cached sources will point at local article IDs so citation navigation opens the fast local article path, with the current remote path as fallback when caching fails.

**Tech Stack:** Kotlin Multiplatform shared module, SQLDelight generated queries, Android ViewModel navigation, Kotlin/JUnit tests, Gradle.

---

## File Structure

- Modify `shared/src/commonMain/kotlin/com/dailysatori/data/repository/RemoteArticleFavoriteMapper.kt`: keep existing favorite mapping and add reusable local cache field helpers.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/data/repository/ArticleRepository.kt`: add `cacheRemoteArticle()` and private insert/update helpers that preserve existing favorite state.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt`: cache remote articles after filtering for the summary window and convert successful cache results to local citation sources.
- Modify `app/src/test/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsUiBehaviorTest.kt`: add source-level guard tests for the repository cache path.
- Modify `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`: add source-level guard tests for unified news caching and local citation conversion.

### Task 1: Add Repository Cache Path

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/RemoteArticleFavoriteMapper.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/ArticleRepository.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsUiBehaviorTest.kt`

- [ ] **Step 1: Write the failing test**

Add this test to `RemoteNewsUiBehaviorTest`:

```kotlin
@Test
fun remoteArticleCacheSavesCompletedNonFavoriteWithoutProcessing() {
    val repository = File(
        "../shared/src/commonMain/kotlin/com/dailysatori/data/repository/ArticleRepository.kt",
    ).readText()

    assertTrue(repository.contains("fun cacheRemoteArticle(remoteArticle: RemoteArticle, sourceTime: Long? = null): Article?"))
    assertTrue(repository.contains("isFavorite = 0"))
    assertTrue(repository.contains("status = fields.status"))
    assertTrue(repository.contains("insertRemoteArticleCacheWithoutUrl"))
    assertFalse(repository.contains("WebpageParserService"))
    assertFalse(repository.contains("ArticleProcessingScheduler"))
}
```

- [ ] **Step 2: Run the focused failing test**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.remotenews.RemoteNewsUiBehaviorTest.remoteArticleCacheSavesCompletedNonFavoriteWithoutProcessing`

Expected: FAIL because `cacheRemoteArticle` is not defined.

- [ ] **Step 3: Add reusable cache fields**

In `RemoteArticleFavoriteMapper.kt`, add this function after `toLocalFavoriteArticleFields()`:

```kotlin
fun RemoteArticle.toLocalCachedArticleFields(sourceTime: Long? = null): LocalFavoriteArticleFields {
    val favoriteFields = toLocalFavoriteArticleFields()
    return favoriteFields.copy(
        isFavorite = 0,
        aiMarkdownContent = favoriteFields.aiMarkdownContent
            ?: favoriteFields.aiContent
            ?: title?.trim()?.takeIf { it.isNotBlank() },
        pubDate = favoriteFields.pubDate ?: sourceTime,
    )
}
```

- [ ] **Step 4: Add repository cache method**

In `ArticleRepository.kt`, add this public method after `saveRemoteArticleAsFavorite()`:

```kotlin
fun cacheRemoteArticle(remoteArticle: RemoteArticle, sourceTime: Long? = null): Article? {
    val fields = remoteArticle.toLocalCachedArticleFields(sourceTime)
    val url = fields.url
    val existing = findLocalArticleForRemote(remoteArticle)
    if (existing == null && url.isNullOrBlank()) return insertRemoteArticleCacheWithoutUrl(fields)

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
        update(
            id = existing.id,
            title = fields.title ?: existing.title,
            aiTitle = fields.aiTitle ?: existing.ai_title,
            aiContent = fields.aiContent ?: existing.ai_content,
            aiMarkdownContent = fields.aiMarkdownContent ?: existing.ai_markdown_content,
            url = existing.url ?: url,
            isFavorite = existing.is_favorite,
            comment = existing.comment,
            status = fields.status,
            coverImage = existing.cover_image,
            coverImageUrl = fields.coverImageUrl ?: existing.cover_image_url,
            pubDate = fields.pubDate ?: existing.pub_date,
        )
        getById(existing.id)
    }
}
```

Add this private helper after `insertRemoteArticleFavoriteWithoutUrl()`:

```kotlin
private fun insertRemoteArticleCacheWithoutUrl(fields: LocalFavoriteArticleFields): Article? {
    val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
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
        now,
        now,
    )
    return q.selectArticlesPaginated(1, 0).executeAsOneOrNull()
}
```

- [ ] **Step 5: Run the focused test**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.remotenews.RemoteNewsUiBehaviorTest.remoteArticleCacheSavesCompletedNonFavoriteWithoutProcessing`

Expected: PASS.

### Task 2: Cache Unified News Remote Sources

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Write the failing test**

Add this test to `UnifiedNewsBehaviorTest`:

```kotlin
@Test
fun unifiedNewsCachesRemoteArticlesAsLocalSourcesBeforeSummaryGeneration() {
    val source = java.io.File(
        "../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt",
    ).readText()

    assertTrue(source.contains("cacheRemoteArticleSource"))
    assertTrue(source.contains("articleRepo.cacheRemoteArticle(article"))
    assertTrue(source.contains("sourceType = UnifiedNewsSourceType.LOCAL_FAVORITE"))
    assertTrue(source.contains("sourceId = cached.id"))
    assertTrue(source.contains("return fallback"))
}
```

- [ ] **Step 2: Run the focused failing test**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsCachesRemoteArticlesAsLocalSourcesBeforeSummaryGeneration`

Expected: FAIL because `cacheRemoteArticleSource` is not defined.

- [ ] **Step 3: Convert remote article collection to cache sources**

In `UnifiedNewsSummaryService.kt`, replace the success branch in `collectConfiguredRemoteArticles()` with this logic:

```kotlin
is RemoteNewsResult.Success -> {
    articles += result.value.articles.mapNotNull { article ->
        val fallback = article.toUnifiedSource(
            window = window,
            ignoreSourceTimeFilter = ignoreSourceTimeFilter,
            sourceFilename = remoteNewsSourceRouteKey(source.id),
        ) ?: return@mapNotNull null
        cacheRemoteArticleSource(article, fallback)
    }
}
```

Add this private method inside `UnifiedNewsSummaryService` after `collectConfiguredRemoteArticles()`:

```kotlin
private fun cacheRemoteArticleSource(
    article: RemoteArticle,
    fallback: UnifiedNewsSourceItem,
): UnifiedNewsSourceItem {
    val cached = try {
        articleRepo.cacheRemoteArticle(article, fallback.sourceTime)
    } catch (e: Exception) {
        log.w(e) { "Remote article cache failed" }
        null
    } ?: return fallback

    return fallback.copy(
        sourceType = UnifiedNewsSourceType.LOCAL_FAVORITE,
        sourceId = cached.id,
        sourceFilename = null,
        title = cached.ai_title ?: cached.title ?: fallback.title,
        summary = cached.ai_content ?: fallback.summary,
        content = cached.ai_markdown_content ?: cached.ai_content ?: fallback.content,
    )
}
```

- [ ] **Step 4: Run the focused test**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsCachesRemoteArticlesAsLocalSourcesBeforeSummaryGeneration`

Expected: PASS.

### Task 3: Verify Navigation And Build

**Files:**
- Verify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`
- Verify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`

- [ ] **Step 1: Confirm local citation navigation already exists**

Verify `UnifiedNewsViewModel.navigationTargetFor()` still contains:

```kotlin
"local_favorite" -> sourceId?.let { UnifiedNewsNavigationTarget.LocalArticle(it) }
```

Verify `UnifiedNewsScreen` still opens local articles through the embedded article detail path for `UnifiedNewsNavigationTarget.LocalArticle`.

- [ ] **Step 2: Run affected unit tests**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest --tests com.dailysatori.ui.feature.remotenews.RemoteNewsUiBehaviorTest`

Expected: PASS.

- [ ] **Step 3: Run required compile check**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Install and launch on connected device**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`

Expected: BUILD SUCCESSFUL and install succeeds.

Run: `adb shell am start -n com.dailysatori/.MainActivity`

Expected: Activity starts without command error.

---

## Self-Review

- Spec coverage: The plan adds non-favorite local caching, preserves favorites, avoids parser/worker processing, converts successful cached citations to local sources, and keeps remote fallback on cache failure.
- Deferred marker scan: no issues found.
- Type consistency: `cacheRemoteArticle(remoteArticle: RemoteArticle, sourceTime: Long? = null)` is used consistently by the repository and unified news service tasks.

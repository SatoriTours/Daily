# Remote News Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a read-only `远程新闻` bottom tab in the Android app backed by the Rails `/api/v1/external` API, with settings for host/token, digest-first UI, and paginated article/feed views.

**Architecture:** Rails keeps the existing read-only external API and adds pagination metadata to list endpoints. Android adds a shared Ktor-based remote news service, settings storage via `SettingRepository`, and Compose screens/ViewModels that keep remote data in memory only. Existing article card/detail visual language is reused by introducing remote-friendly display components rather than writing remote data into SQLite.

**Tech Stack:** Ruby on Rails controller tests, Kotlin Multiplatform shared module, Ktor client, kotlinx.serialization, Koin, Jetpack Compose Material 3, Kotlin coroutines/StateFlow.

---

## File Structure

### Rails Project `../web`

- Modify `app/controllers/api/v1/external_controller.rb`: add `page`, `per_page`, and `pagination` helpers; apply pagination to articles, digests, and feeds.
- Modify `test/controllers/api/v1/external_controller_test.rb`: add pagination tests while preserving token and read-only behavior.

### Android Project `Daily`

- Modify `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt`: add remote news setting keys and page-size constants.
- Create `shared/src/commonMain/kotlin/com/dailysatori/service/remotenews/RemoteNewsModels.kt`: DTOs and UI-facing remote news models.
- Create `shared/src/commonMain/kotlin/com/dailysatori/service/remotenews/RemoteNewsService.kt`: Ktor API client, URL normalization, error mapping.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`: register `RemoteNewsService`.
- Create `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt`: digest/article/feed list state, detail state, pagination, refresh/load-more.
- Create `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsScreen.kt`: single `远程新闻` page with digest default mode and overflow switching.
- Create `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsDetailScreens.kt`: digest detail and article detail UI.
- Create `app/src/main/kotlin/com/dailysatori/ui/feature/settings/remotenews/RemoteNewsSettingsViewModel.kt`: settings load/save/test connection.
- Create `app/src/main/kotlin/com/dailysatori/ui/feature/settings/remotenews/RemoteNewsSettingsScreen.kt`: settings form.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt`: add `远程新闻设置` row and subpage.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt`: add bottom tab `远程新闻`.
- Modify `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`: register remote news ViewModels.
- Test `app/src/test/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModelTest.kt`: pagination merge and empty config behavior.
- Test `app/src/test/kotlin/com/dailysatori/service/remotenews/RemoteNewsServiceTest.kt`: URL/header/error behavior with a fake engine if available; otherwise test pure helper functions.

---

### Task 1: Rails External API Pagination

**Files:**
- Modify: `../web/test/controllers/api/v1/external_controller_test.rb`
- Modify: `../web/app/controllers/api/v1/external_controller.rb`

- [ ] **Step 1: Add failing Rails pagination tests**

Append these tests before the `private` section in `../web/test/controllers/api/v1/external_controller_test.rb`:

```ruby
  test "lists articles with pagination metadata" do
    second_article = Article.create!(
      url: "https://example.com/external/article-2",
      original_title: "External Article 2",
      ai_summary: "External summary 2",
      status: :completed,
      source_type: :feed,
      is_global: true,
      feed: @feed,
      created_at: 1.minute.ago
    )

    get "/api/v1/external/articles?page=1&per_page=1", headers: external_headers, as: :json

    assert_response :success
    payload = response.parsed_body
    assert_equal [ second_article.id ], payload.fetch("articles").map { |article| article.fetch("id") }
    assert_equal({
      "page" => 1,
      "per_page" => 1,
      "total" => 2,
      "total_pages" => 2,
      "next" => 2
    }, payload.fetch("pagination"))
  end

  test "lists digests with pagination metadata" do
    older_digest = DailyDigest.create!(
      user: @user,
      date: Date.current - 1,
      title: "Older Digest",
      status: :completed,
      summary: "Older summary",
      highlights: [],
      sections: [],
      generated_at: 1.day.ago,
      article_count: 0
    )

    get "/api/v1/external/digests?page=2&per_page=1", headers: external_headers, as: :json

    assert_response :success
    payload = response.parsed_body
    assert_equal [ older_digest.id ], payload.fetch("digests").map { |digest| digest.fetch("id") }
    assert_equal({
      "page" => 2,
      "per_page" => 1,
      "total" => 2,
      "total_pages" => 2,
      "next" => nil
    }, payload.fetch("pagination"))
  end

  test "lists feeds with pagination metadata" do
    second_feed = Feed.create!(
      name: "Another External Feed",
      feed_type: :rss,
      url: "https://example.com/another-feed.xml",
      is_global: true,
      refresh_interval: 30,
      is_enabled: true
    )

    get "/api/v1/external/feeds?page=1&per_page=1", headers: external_headers, as: :json

    assert_response :success
    payload = response.parsed_body
    assert_equal [ second_feed.id ], payload.fetch("feeds").map { |feed| feed.fetch("id") }
    assert_equal({
      "page" => 1,
      "per_page" => 1,
      "total" => 2,
      "total_pages" => 2,
      "next" => 2
    }, payload.fetch("pagination"))
  end
```

- [ ] **Step 2: Run the Rails test and verify failure**

Run from `/home/jimxl/Documents/projects/web`:

```bash
bin/rails test test/controllers/api/v1/external_controller_test.rb
```

Expected: tests fail because list responses do not include `pagination` and still use `limit` only.

- [ ] **Step 3: Implement pagination in the Rails controller**

In `../web/app/controllers/api/v1/external_controller.rb`, replace the list render calls and add helpers so the file contains this pagination behavior:

```ruby
      MAX_LIMIT = 100

      def articles
        scope = Article.completed
          .preload(:feed)
          .recent
        scope = scope.where(source_type: params[:source_type]) if params[:source_type].present?
        scope = scope.where(feed_id: params[:feed_id]) if params[:feed_id].present?
        scope = scope.search_by(params[:q]) if params[:q].present?

        render json: paginated_payload(scope) { |records|
          { articles: records.map { |article| external_article_json(article) } }
        }
      end

      def digests
        scope = DailyDigest.completed.includes(:user).order(generated_at: :desc, created_at: :desc)

        render json: paginated_payload(scope) { |records|
          { digests: records.map { |digest| external_digest_json(digest) } }
        }
      end

      def feeds
        scope = Feed.order(:name)
        scope = scope.where(is_enabled: ActiveModel::Type::Boolean.new.cast(params[:enabled])) if params.key?(:enabled)

        render json: paginated_payload(scope) { |records|
          { feeds: records.map { |feed| external_feed_json(feed) } }
        }
      end

      private

      def paginated_payload(scope)
        total = scope.count
        records = scope.offset((requested_page - 1) * requested_limit).limit(requested_limit)
        yield(records).merge(pagination: pagination_json(total))
      end

      def requested_page
        [ params.fetch(:page, 1).to_i, 1 ].max
      end

      def requested_limit
        [ [ params.fetch(:per_page, params.fetch(:limit, 50)).to_i, 1 ].max, MAX_LIMIT ].min
      end

      def pagination_json(total)
        total_pages = (total.to_f / requested_limit).ceil
        next_page = requested_page < total_pages ? requested_page + 1 : nil
        {
          page: requested_page,
          per_page: requested_limit,
          total: total,
          total_pages: total_pages,
          next: next_page
        }
      end
```

Keep `authenticate_external_api_token!`, `article`, `digest`, and JSON serializer methods unchanged.

- [ ] **Step 4: Run the Rails test and verify pass**

Run from `/home/jimxl/Documents/projects/web`:

```bash
bin/rails test test/controllers/api/v1/external_controller_test.rb
```

Expected: all external controller tests pass.

---

### Task 2: Remote News Shared Models And Service

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/remotenews/RemoteNewsModels.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/remotenews/RemoteNewsService.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`

- [ ] **Step 1: Add config constants**

In `Config.kt`, add these keys to `SettingKeys`:

```kotlin
    const val remoteNewsBaseUrl = "remote_news_base_url"
    const val remoteNewsApiToken = "remote_news_api_token"
```

Add this object near other config objects:

```kotlin
object RemoteNewsConfig {
    const val articlesPageSize = 20
    const val digestsPageSize = 20
    const val feedsPageSize = 50
}
```

- [ ] **Step 2: Create remote news models**

Create `RemoteNewsModels.kt` with:

```kotlin
package com.dailysatori.service.remotenews

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteNewsPagination(
    val page: Int = 1,
    @SerialName("per_page") val perPage: Int = 20,
    val total: Int = 0,
    @SerialName("total_pages") val totalPages: Int = 0,
    val next: Int? = null,
)

@Serializable
data class RemoteArticle(
    val id: Long,
    val title: String? = null,
    val url: String? = null,
    val summary: String? = null,
    val viewpoints: List<String> = emptyList(),
    val status: String? = null,
    @SerialName("source_type") val sourceType: String? = null,
    @SerialName("feed_id") val feedId: Long? = null,
    @SerialName("feed_name") val feedName: String? = null,
    val domain: String? = null,
    @SerialName("importance_score") val importanceScore: Double? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("processed_at") val processedAt: String? = null,
    val content: String? = null,
)

@Serializable
data class RemoteDigest(
    val id: Long,
    val date: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val highlights: List<String> = emptyList(),
    val sections: List<RemoteDigestSection> = emptyList(),
    @SerialName("article_count") val articleCount: Int = 0,
    @SerialName("manual_count") val manualCount: Int = 0,
    @SerialName("feed_count") val feedCount: Int = 0,
    @SerialName("generated_at") val generatedAt: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    val articles: List<RemoteArticle> = emptyList(),
)

@Serializable
data class RemoteDigestSection(
    val topic: String? = null,
    val title: String? = null,
    val highlights: List<String> = emptyList(),
    val summary: String? = null,
)

@Serializable
data class RemoteFeed(
    val id: Long,
    val name: String? = null,
    val url: String? = null,
    @SerialName("feed_type") val feedType: String? = null,
    val category: String? = null,
    val status: String? = null,
    @SerialName("is_enabled") val isEnabled: Boolean = false,
    @SerialName("is_global") val isGlobal: Boolean = false,
    @SerialName("refresh_interval") val refreshInterval: Int? = null,
    @SerialName("last_fetched_at") val lastFetchedAt: String? = null,
    @SerialName("next_fetch_at") val nextFetchAt: String? = null,
    @SerialName("health_score") val healthScore: Double? = null,
)

@Serializable
data class RemoteArticlesResponse(
    val articles: List<RemoteArticle> = emptyList(),
    val pagination: RemoteNewsPagination = RemoteNewsPagination(),
)

@Serializable
data class RemoteArticleResponse(val article: RemoteArticle)

@Serializable
data class RemoteDigestsResponse(
    val digests: List<RemoteDigest> = emptyList(),
    val pagination: RemoteNewsPagination = RemoteNewsPagination(),
)

@Serializable
data class RemoteDigestResponse(val digest: RemoteDigest)

@Serializable
data class RemoteFeedsResponse(
    val feeds: List<RemoteFeed> = emptyList(),
    val pagination: RemoteNewsPagination = RemoteNewsPagination(),
)

data class RemoteNewsConfigValues(
    val baseUrl: String,
    val token: String,
)

sealed class RemoteNewsResult<out T> {
    data class Success<T>(val value: T) : RemoteNewsResult<T>()
    data class Failure(val message: String) : RemoteNewsResult<Nothing>()
}
```

- [ ] **Step 3: Create the remote news service**

Create `RemoteNewsService.kt` with:

```kotlin
package com.dailysatori.service.remotenews

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.URLBuilder

class RemoteNewsService(private val client: HttpClient) {
    suspend fun fetchDigests(config: RemoteNewsConfigValues, page: Int, perPage: Int): RemoteNewsResult<RemoteDigestsResponse> =
        request { client.get(buildUrl(config.baseUrl, "digests", page, perPage)) { bearerAuth(config.token) }.body() }

    suspend fun fetchDigest(config: RemoteNewsConfigValues, id: Long): RemoteNewsResult<RemoteDigestResponse> =
        request { client.get(buildUrl(config.baseUrl, "digests/$id")) { bearerAuth(config.token) }.body() }

    suspend fun fetchArticles(config: RemoteNewsConfigValues, page: Int, perPage: Int): RemoteNewsResult<RemoteArticlesResponse> =
        request { client.get(buildUrl(config.baseUrl, "articles", page, perPage)) { bearerAuth(config.token) }.body() }

    suspend fun fetchArticle(config: RemoteNewsConfigValues, id: Long): RemoteNewsResult<RemoteArticleResponse> =
        request { client.get(buildUrl(config.baseUrl, "articles/$id")) { bearerAuth(config.token) }.body() }

    suspend fun fetchFeeds(config: RemoteNewsConfigValues, page: Int, perPage: Int): RemoteNewsResult<RemoteFeedsResponse> =
        request { client.get(buildUrl(config.baseUrl, "feeds", page, perPage)) { bearerAuth(config.token) }.body() }

    fun buildUrl(baseUrl: String, path: String, page: Int? = null, perPage: Int? = null): String {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        val builder = URLBuilder("$normalizedBase/api/v1/external/$path")
        if (page != null) builder.parameters.append("page", page.toString())
        if (perPage != null) builder.parameters.append("per_page", perPage.toString())
        return builder.buildString()
    }

    fun configOrFailure(baseUrl: String?, token: String?): RemoteNewsResult<RemoteNewsConfigValues> {
        val normalizedBaseUrl = baseUrl.orEmpty().trim()
        val normalizedToken = token.orEmpty().trim()
        if (normalizedBaseUrl.isBlank() || normalizedToken.isBlank()) {
            return RemoteNewsResult.Failure("请先配置远程新闻服务")
        }
        return RemoteNewsResult.Success(RemoteNewsConfigValues(normalizedBaseUrl, normalizedToken))
    }

    private suspend fun <T> request(block: suspend () -> T): RemoteNewsResult<T> = try {
        RemoteNewsResult.Success(block())
    } catch (_: ClientRequestException) {
        RemoteNewsResult.Failure("Token 无效，请检查远程新闻设置")
    } catch (_: ServerResponseException) {
        RemoteNewsResult.Failure("远程新闻服务暂时不可用")
    } catch (_: Exception) {
        RemoteNewsResult.Failure("无法连接远程新闻服务")
    }
}
```

- [ ] **Step 4: Register the service in shared DI**

In `SharedModule.kt`, add the import:

```kotlin
import com.dailysatori.service.remotenews.RemoteNewsService
```

Add this singleton near other services:

```kotlin
    single { RemoteNewsService(get()) }
```

- [ ] **Step 5: Compile shared/app Kotlin**

Run from `/home/jimxl/Documents/projects/Daily`:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: compilation succeeds or fails only on missing UI/ViewModel code that later tasks introduce; fix any syntax errors in the shared service before proceeding.

---

### Task 3: Remote News Settings UI

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/remotenews/RemoteNewsSettingsViewModel.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/remotenews/RemoteNewsSettingsScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`

- [ ] **Step 1: Create settings ViewModel**

Create `RemoteNewsSettingsViewModel.kt` with:

```kotlin
package com.dailysatori.ui.feature.settings.remotenews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.remotenews.RemoteNewsConfigValues
import com.dailysatori.service.remotenews.RemoteNewsResult
import com.dailysatori.service.remotenews.RemoteNewsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RemoteNewsSettingsState(
    val baseUrl: String = "",
    val token: String = "",
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val message: String? = null,
)

class RemoteNewsSettingsViewModel(
    private val settingRepo: SettingRepository,
    private val remoteNewsService: RemoteNewsService,
) : ViewModel() {
    private val _state = MutableStateFlow(RemoteNewsSettingsState())
    val state: StateFlow<RemoteNewsSettingsState> = _state.asStateFlow()

    init { load() }

    fun updateBaseUrl(value: String) = _state.update { it.copy(baseUrl = value, message = null) }

    fun updateToken(value: String) = _state.update { it.copy(token = value, message = null) }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    baseUrl = settingRepo.get(SettingKeys.remoteNewsBaseUrl).orEmpty(),
                    token = settingRepo.get(SettingKeys.remoteNewsApiToken).orEmpty(),
                )
            }
        }
    }

    fun save() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isSaving = true, message = null) }
            settingRepo.upsert(SettingKeys.remoteNewsBaseUrl, state.value.baseUrl.trim())
            settingRepo.upsert(SettingKeys.remoteNewsApiToken, state.value.token.trim())
            _state.update { it.copy(isSaving = false, message = "远程新闻设置已保存") }
        }
    }

    fun testConnection() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isTesting = true, message = null) }
            val config = remoteNewsService.configOrFailure(state.value.baseUrl, state.value.token)
            val message = when (config) {
                is RemoteNewsResult.Failure -> config.message
                is RemoteNewsResult.Success<RemoteNewsConfigValues> -> when (val result = remoteNewsService.fetchDigests(config.value, 1, 1)) {
                    is RemoteNewsResult.Success -> "连接成功"
                    is RemoteNewsResult.Failure -> result.message
                }
            }
            _state.update { it.copy(isTesting = false, message = message) }
        }
    }
}
```

- [ ] **Step 2: Create settings screen**

Create `RemoteNewsSettingsScreen.kt` with:

```kotlin
package com.dailysatori.ui.feature.settings.remotenews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun RemoteNewsSettingsScreen(onBack: () -> Unit) {
    val viewModel: RemoteNewsSettingsViewModel = koinViewModel()
    val state = viewModel.state.collectAsStateWithLifecycle().value

    AppScaffold(title = "远程新闻设置", onBack = onBack) { modifier ->
        Column(
            modifier = modifier.padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = viewModel::updateBaseUrl,
                label = { Text("服务地址") },
                placeholder = { Text("http://192.168.1.10:3000") },
                singleLine = true,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.token,
                onValueChange = viewModel::updateToken,
                label = { Text("API Token") },
                singleLine = true,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            )
            if (state.message != null) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            ) { Text(if (state.isSaving) "保存中..." else "保存") }
            TextButton(
                onClick = viewModel::testConnection,
                enabled = !state.isTesting,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            ) { Text(if (state.isTesting) "测试中..." else "测试连接") }
        }
    }
}
```

- [ ] **Step 3: Add settings subpage entry**

In `SettingsScreen.kt`, add `REMOTE_NEWS_SETTINGS` to `SettingsPage`, import `Icons.Default.Newspaper` or use `Icons.Default.Language`, import `RemoteNewsSettingsScreen`, and add:

```kotlin
        SettingsPage.REMOTE_NEWS_SETTINGS -> RemoteNewsSettingsScreen(onBack = { currentPage = SettingsPage.MAIN })
```

Inside `NetworkSection`, add this row before `WebServerRow`:

```kotlin
        SettingsRow(
            icon = Icons.Default.Language,
            title = "远程新闻设置",
            subtitle = "配置服务地址和 API Token",
            onClick = { onNavigate(SettingsPage.REMOTE_NEWS_SETTINGS) },
        )
```

Change `NetworkSection` signature to accept `onNavigate: (SettingsPage) -> Unit`, and update its call from `NetworkSection(state, viewModel)` to `NetworkSection(state, viewModel, onNavigate)`.

- [ ] **Step 4: Register settings ViewModel**

In `ViewModelModule.kt`, import:

```kotlin
import com.dailysatori.ui.feature.settings.remotenews.RemoteNewsSettingsViewModel
```

Add:

```kotlin
    viewModel { RemoteNewsSettingsViewModel(get(), get()) }
```

- [ ] **Step 5: Compile Android**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: compilation succeeds after fixing any missing imports.

---

### Task 4: Remote News ViewModel

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`

- [ ] **Step 1: Create ViewModel state and actions**

Create `RemoteNewsViewModel.kt` with:

```kotlin
package com.dailysatori.ui.feature.remotenews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.config.RemoteNewsConfig
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.service.remotenews.RemoteDigest
import com.dailysatori.service.remotenews.RemoteFeed
import com.dailysatori.service.remotenews.RemoteNewsConfigValues
import com.dailysatori.service.remotenews.RemoteNewsPagination
import com.dailysatori.service.remotenews.RemoteNewsResult
import com.dailysatori.service.remotenews.RemoteNewsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RemoteNewsMode(val title: String) {
    DIGESTS("远程新闻"),
    ARTICLES("远程文章"),
    FEEDS("信息源"),
}

data class RemoteNewsState(
    val mode: RemoteNewsMode = RemoteNewsMode.DIGESTS,
    val digests: List<RemoteDigest> = emptyList(),
    val articles: List<RemoteArticle> = emptyList(),
    val feeds: List<RemoteFeed> = emptyList(),
    val digestPagination: RemoteNewsPagination? = null,
    val articlePagination: RemoteNewsPagination? = null,
    val feedPagination: RemoteNewsPagination? = null,
    val selectedDigest: RemoteDigest? = null,
    val selectedArticle: RemoteArticle? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val loadMoreError: String? = null,
)

class RemoteNewsViewModel(
    private val settingRepo: SettingRepository,
    private val remoteNewsService: RemoteNewsService,
) : ViewModel() {
    private val _state = MutableStateFlow(RemoteNewsState())
    val state: StateFlow<RemoteNewsState> = _state.asStateFlow()

    fun loadInitial() {
        if (_state.value.digests.isEmpty()) loadMode(RemoteNewsMode.DIGESTS, refresh = false)
    }

    fun switchMode(mode: RemoteNewsMode) {
        _state.update { it.copy(mode = mode, error = null, loadMoreError = null) }
        val needsLoad = when (mode) {
            RemoteNewsMode.DIGESTS -> _state.value.digests.isEmpty()
            RemoteNewsMode.ARTICLES -> _state.value.articles.isEmpty()
            RemoteNewsMode.FEEDS -> _state.value.feeds.isEmpty()
        }
        if (needsLoad) loadMode(mode, refresh = false)
    }

    fun refresh() = loadMode(_state.value.mode, refresh = true)

    fun loadMore() {
        val current = _state.value
        if (current.isLoading || current.isRefreshing || current.isLoadingMore) return
        val nextPage = when (current.mode) {
            RemoteNewsMode.DIGESTS -> current.digestPagination?.next
            RemoteNewsMode.ARTICLES -> current.articlePagination?.next
            RemoteNewsMode.FEEDS -> current.feedPagination?.next
        } ?: return
        loadPage(current.mode, nextPage, append = true)
    }

    fun openDigest(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = null) }
            val config = currentConfigOrSetError() ?: return@launch
            when (val result = remoteNewsService.fetchDigest(config, id)) {
                is RemoteNewsResult.Success -> _state.update { it.copy(selectedDigest = result.value.digest, isLoading = false) }
                is RemoteNewsResult.Failure -> _state.update { it.copy(error = result.message, isLoading = false) }
            }
        }
    }

    fun closeDigest() = _state.update { it.copy(selectedDigest = null) }

    fun openArticle(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = null) }
            val config = currentConfigOrSetError() ?: return@launch
            when (val result = remoteNewsService.fetchArticle(config, id)) {
                is RemoteNewsResult.Success -> _state.update { it.copy(selectedArticle = result.value.article, isLoading = false) }
                is RemoteNewsResult.Failure -> _state.update { it.copy(error = result.message, isLoading = false) }
            }
        }
    }

    fun closeArticle() = _state.update { it.copy(selectedArticle = null) }

    private fun loadMode(mode: RemoteNewsMode, refresh: Boolean) = loadPage(mode, 1, append = false, refresh = refresh)

    private fun loadPage(mode: RemoteNewsMode, page: Int, append: Boolean, refresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    isLoading = !refresh && !append,
                    isRefreshing = refresh,
                    isLoadingMore = append,
                    error = null,
                    loadMoreError = null,
                )
            }
            val config = currentConfigOrSetError() ?: return@launch
            when (mode) {
                RemoteNewsMode.DIGESTS -> loadDigests(config, page, append)
                RemoteNewsMode.ARTICLES -> loadArticles(config, page, append)
                RemoteNewsMode.FEEDS -> loadFeeds(config, page, append)
            }
        }
    }

    private suspend fun loadDigests(config: RemoteNewsConfigValues, page: Int, append: Boolean) {
        when (val result = remoteNewsService.fetchDigests(config, page, RemoteNewsConfig.digestsPageSize)) {
            is RemoteNewsResult.Success -> _state.update {
                it.copy(
                    digests = if (append) it.digests + result.value.digests else result.value.digests,
                    digestPagination = result.value.pagination,
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                )
            }
            is RemoteNewsResult.Failure -> applyFailure(result.message, append)
        }
    }

    private suspend fun loadArticles(config: RemoteNewsConfigValues, page: Int, append: Boolean) {
        when (val result = remoteNewsService.fetchArticles(config, page, RemoteNewsConfig.articlesPageSize)) {
            is RemoteNewsResult.Success -> _state.update {
                it.copy(
                    articles = if (append) it.articles + result.value.articles else result.value.articles,
                    articlePagination = result.value.pagination,
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                )
            }
            is RemoteNewsResult.Failure -> applyFailure(result.message, append)
        }
    }

    private suspend fun loadFeeds(config: RemoteNewsConfigValues, page: Int, append: Boolean) {
        when (val result = remoteNewsService.fetchFeeds(config, page, RemoteNewsConfig.feedsPageSize)) {
            is RemoteNewsResult.Success -> _state.update {
                it.copy(
                    feeds = if (append) it.feeds + result.value.feeds else result.value.feeds,
                    feedPagination = result.value.pagination,
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                )
            }
            is RemoteNewsResult.Failure -> applyFailure(result.message, append)
        }
    }

    private fun applyFailure(message: String, append: Boolean) {
        _state.update {
            it.copy(
                error = if (append) it.error else message,
                loadMoreError = if (append) message else null,
                isLoading = false,
                isRefreshing = false,
                isLoadingMore = false,
            )
        }
    }

    private fun currentConfigOrSetError(): RemoteNewsConfigValues? {
        return when (val config = remoteNewsService.configOrFailure(
            settingRepo.get(SettingKeys.remoteNewsBaseUrl),
            settingRepo.get(SettingKeys.remoteNewsApiToken),
        )) {
            is RemoteNewsResult.Success -> config.value
            is RemoteNewsResult.Failure -> {
                _state.update { it.copy(error = config.message, isLoading = false, isRefreshing = false, isLoadingMore = false) }
                null
            }
        }
    }
}
```

- [ ] **Step 2: Register RemoteNewsViewModel**

In `ViewModelModule.kt`, import:

```kotlin
import com.dailysatori.ui.feature.remotenews.RemoteNewsViewModel
```

Add:

```kotlin
    viewModel { RemoteNewsViewModel(get(), get()) }
```

- [ ] **Step 3: Compile Android**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: compilation succeeds after fixing imports.

---

### Task 5: Remote News Compose UI And Bottom Tab

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsScreen.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsDetailScreens.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt`

- [ ] **Step 1: Create the main remote news screen**

Create `RemoteNewsScreen.kt` with digest default mode, overflow switching, pull refresh, and load-more. Use `LazyColumn`, `PullToRefreshBox`, `AppScaffold`, `CustomCard`, `LoadingIndicator`, and `EmptyState` following existing imports and style. The screen must call:

```kotlin
LaunchedEffect(Unit) { viewModel.loadInitial() }
```

Each list should trigger load more with:

```kotlin
LaunchedEffect(listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index, items.size) {
    if (items.isNotEmpty() && listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index == items.lastIndex) {
        viewModel.loadMore()
    }
}
```

The top menu actions must call:

```kotlin
viewModel.switchMode(RemoteNewsMode.ARTICLES)
viewModel.switchMode(RemoteNewsMode.FEEDS)
viewModel.switchMode(RemoteNewsMode.DIGESTS)
viewModel.refresh()
```

- [ ] **Step 2: Create detail screens**

Create `RemoteNewsDetailScreens.kt` with:

```kotlin
@Composable
fun RemoteDigestDetailScreen(
    digest: RemoteDigest,
    onBack: () -> Unit,
    onArticleClick: (Long) -> Unit,
)
```

and:

```kotlin
@Composable
fun RemoteArticleDetailScreen(
    article: RemoteArticle,
    onBack: () -> Unit,
)
```

Use `AppScaffold`, `LazyColumn`, `SelectionContainer`, and `Markdown` for article content. Keep read-only actions only; include browser open if there is already an existing helper available in the article package, otherwise defer browser open to a later small cleanup.

- [ ] **Step 3: Wire detail states into RemoteNewsScreen**

At the top of `RemoteNewsScreen`, before rendering the list, handle selected detail state:

```kotlin
state.selectedArticle?.let { article ->
    RemoteArticleDetailScreen(article = article, onBack = viewModel::closeArticle)
    return
}
state.selectedDigest?.let { digest ->
    RemoteDigestDetailScreen(
        digest = digest,
        onBack = viewModel::closeDigest,
        onArticleClick = viewModel::openArticle,
    )
    return
}
```

- [ ] **Step 4: Add bottom tab**

In `HomeScreen.kt`, add an icon import such as:

```kotlin
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.outlined.Newspaper
import com.dailysatori.ui.feature.remotenews.RemoteNewsScreen
```

Update `tabs` to include `远程新闻` before settings:

```kotlin
    TabItem("远程新闻", Icons.Filled.Newspaper, Icons.Outlined.Newspaper),
```

Update the `when` block so indexes are:

```kotlin
                    0 -> ArticleListScreen(onArticleClick = onArticleClick)
                    1 -> DiaryScreen()
                    2 -> BooksScreen(...)
                    AI_CHAT_TAB_INDEX -> AiChatScreen(onArticleClick = onAiArticleClick)
                    4 -> RemoteNewsScreen()
                    5 -> SettingsScreen(settingsViewModel)
```

Keep `AI_CHAT_TAB_INDEX = 3`.

- [ ] **Step 5: Compile Android**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: compilation succeeds.

---

### Task 6: Verification And Polish

**Files:**
- Review modified files from Tasks 1-5.

- [ ] **Step 1: Run Rails external controller tests**

Run from `/home/jimxl/Documents/projects/web`:

```bash
bin/rails test test/controllers/api/v1/external_controller_test.rb
```

Expected: all tests pass.

- [ ] **Step 2: Run Android compile check**

Run from `/home/jimxl/Documents/projects/Daily`:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: build succeeds.

- [ ] **Step 3: Install and launch if a device is connected**

Run from `/home/jimxl/Documents/projects/Daily`:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
adb shell am start -n com.dailysatori/.MainActivity
```

Expected: install succeeds and the app launches.

- [ ] **Step 4: Manual smoke test**

In the app:

- Open `设置` > `远程新闻设置`.
- Enter the Rails service address and external API token.
- Tap `测试连接` and confirm `连接成功`.
- Open the `远程新闻` bottom tab.
- Confirm the default list is summaries.
- Open `...` and switch to articles.
- Open `...` and switch to feeds.
- Tap a summary and verify associated articles appear.
- Tap an associated article and verify article details load from the remote API.

Expected: all screens load without local persistence or write actions.

---

## Self-Review

- Spec coverage: Rails pagination, settings placement, bottom tab, digest-first page, overflow switching, digest detail articles, read-only remote article detail, no local persistence, and verification are covered.
- Placeholder scan: no `TBD` or unspecified implementation steps remain; Task 5 allows using existing UI components while naming the required behavior and function signatures.
- Type consistency: `RemoteNewsService`, `RemoteNewsViewModel`, `RemoteNewsMode`, DTO names, setting keys, and pagination names are consistent across tasks.

# Crayfish News (小龙虾新闻) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a new "Crayfish News" (小龙虾新闻) data source to the existing remote news tab, with full API integration, independent settings, and markdown-rendered content.

**Architecture:** New `CrayfishNewsService` + `CrayfishNewsViewModel` in shared/app layers following existing `RemoteNews*` patterns. Integrated into the existing remote news "..." menu as a new mode. Independent settings page for Base URL + Token.

**Tech Stack:** Kotlin, Ktor HttpClient, Koin DI, Jetpack Compose, kotlinx.serialization, mikepenz/markdown

---

## File Structure

### New Files

| File | Responsibility |
|------|---------------|
| `shared/.../service/crayfishnews/CrayfishNewsModels.kt` | Data models for Crayfish API |
| `shared/.../service/crayfishnews/CrayfishNewsService.kt` | HTTP service for Crayfish API |
| `app/.../ui/feature/crayfishnews/CrayfishNewsViewModel.kt` | ViewModel managing Crayfish news state |
| `app/.../ui/feature/crayfishnews/CrayfishNewsScreen.kt` | Main screen + list + menu composables |
| `app/.../ui/feature/crayfishnews/CrayfishNewsDetailScreen.kt` | Detail view with markdown rendering |
| `app/.../ui/feature/settings/crayfishnews/CrayfishNewsSettingsViewModel.kt` | Settings ViewModel |
| `app/.../ui/feature/settings/crayfishnews/CrayfishNewsSettingsScreen.kt` | Settings form UI |

### Modified Files

| File | Change |
|------|--------|
| `shared/.../config/Config.kt` | Add `crayfishNewsBaseUrl` + `crayfishNewsApiToken` to SettingKeys |
| `shared/.../di/SharedModule.kt` | Register `CrayfishNewsService` |
| `app/.../core/di/ViewModelModule.kt` | Register `CrayfishNewsViewModel` + `CrayfishNewsSettingsViewModel` |
| `app/.../ui/feature/remotenews/RemoteNewsViewModel.kt` | Add `CRAYFISH` to `RemoteNewsMode` enum |
| `app/.../ui/feature/remotenews/RemoteNewsScreen.kt` | Add menu item + conditional rendering for Crayfish mode |
| `app/.../ui/feature/settings/SettingsScreen.kt` | Add `CRAYFISH_NEWS_SETTINGS` page + settings row |

---

## Task 1: Data Models

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/crayfishnews/CrayfishNewsModels.kt`

- [ ] **Step 1: Create CrayfishNewsModels.kt**

```kotlin
package com.dailysatori.service.crayfishnews

import kotlinx.serialization.Serializable

data class CrayfishNewsConfigValues(
    val baseUrl: String,
    val token: String,
)

sealed class CrayfishNewsResult<out T> {
    data class Success<T>(val value: T) : CrayfishNewsResult<T>()
    data class Failure(val message: String) : CrayfishNewsResult<Nothing>()
}

@Serializable
data class CrayfishNewsDetail(
    val filename: String = "",
    val generated: String? = null,
    val source: String? = null,
    val sections: Map<String, String> = emptyMap(),
    val content: String = "",
    val preview: String = "",
)

@Serializable
data class CrayfishNewsListItem(
    val filename: String = "",
    val generated: String? = null,
    val source: String? = null,
    val preview: String = "",
)

@Serializable
data class CrayfishNewsListResponse(
    val general: List<CrayfishNewsListItem> = emptyList(),
    val dji: List<CrayfishNewsListItem> = emptyList(),
)

@Serializable
data class CrayfishHealthResponse(
    val status: String = "",
    val user: String = "",
    val ts: String = "",
)
```

- [ ] **Step 2: Verify compilation**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :shared:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/crayfishnews/CrayfishNewsModels.kt
git commit -m "feat: add crayfish news data models"
```

---

## Task 2: API Service

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/crayfishnews/CrayfishNewsService.kt`

- [ ] **Step 1: Create CrayfishNewsService.kt**

```kotlin
package com.dailysatori.service.crayfishnews

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.URLBuilder

class CrayfishNewsService(private val client: HttpClient) {

    suspend fun healthCheck(config: CrayfishNewsConfigValues): CrayfishNewsResult<CrayfishHealthResponse> =
        request { client.get(buildUrl(config.baseUrl, "health")) { bearerAuth(config.token) }.body() }

    suspend fun fetchLatest(config: CrayfishNewsConfigValues): CrayfishNewsResult<CrayfishNewsDetail> =
        request { client.get(buildUrl(config.baseUrl, "news/latest")) { bearerAuth(config.token) }.body() }

    suspend fun fetchDji(config: CrayfishNewsConfigValues): CrayfishNewsResult<CrayfishNewsDetail> =
        request { client.get(buildUrl(config.baseUrl, "news/dji")) { bearerAuth(config.token) }.body() }

    suspend fun fetchNewsList(config: CrayfishNewsConfigValues, category: String? = null, limit: Int = 20): CrayfishNewsResult<CrayfishNewsListResponse> =
        request {
            val builder = URLBuilder("${config.baseUrl.trim().trimEnd('/')}/news")
            if (category != null) builder.parameters.append("category", category)
            builder.parameters.append("limit", limit.toString())
            client.get(builder.buildString()) { bearerAuth(config.token) }.body()
        }

    suspend fun fetchNewsFile(config: CrayfishNewsConfigValues, category: String, filename: String): CrayfishNewsResult<CrayfishNewsDetail> =
        request { client.get(buildUrl(config.baseUrl, "news/$category/$filename")) { bearerAuth(config.token) }.body() }

    fun configOrFailure(baseUrl: String?, token: String?): CrayfishNewsResult<CrayfishNewsConfigValues> {
        val normalizedBaseUrl = baseUrl.orEmpty().trim()
        val normalizedToken = token.orEmpty().trim()
        if (normalizedBaseUrl.isBlank() || normalizedToken.isBlank()) {
            return CrayfishNewsResult.Failure("请先配置小龙虾新闻服务")
        }
        return CrayfishNewsResult.Success(CrayfishNewsConfigValues(normalizedBaseUrl, normalizedToken))
    }

    private fun buildUrl(baseUrl: String, path: String): String {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        return "$normalizedBase/$path"
    }

    private suspend fun <T> request(block: suspend () -> T): CrayfishNewsResult<T> = try {
        CrayfishNewsResult.Success(block())
    } catch (_: ClientRequestException) {
        CrayfishNewsResult.Failure("Token 无效，请检查小龙虾新闻设置")
    } catch (_: ServerResponseException) {
        CrayfishNewsResult.Failure("小龙虾新闻服务暂时不可用")
    } catch (_: Exception) {
        CrayfishNewsResult.Failure("无法连接小龙虾新闻服务")
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :shared:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/crayfishnews/CrayfishNewsService.kt
git commit -m "feat: add crayfish news API service"
```

---

## Task 3: Config Keys + DI Registration

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`

- [ ] **Step 1: Add setting keys to Config.kt**

Add to the `SettingKeys` object (after `remoteNewsApiToken`):

```kotlin
    const val crayfishNewsBaseUrl = "crayfish_news_base_url"
    const val crayfishNewsApiToken = "crayfish_news_api_token"
```

- [ ] **Step 2: Register CrayfishNewsService in SharedModule.kt**

Add import: `import com.dailysatori.service.crayfishnews.CrayfishNewsService`

Add after `single { RemoteNewsService(get()) }`:

```kotlin
    single { CrayfishNewsService(get()) }
```

- [ ] **Step 3: Register ViewModels in ViewModelModule.kt**

Add imports:
```kotlin
import com.dailysatori.ui.feature.crayfishnews.CrayfishNewsViewModel
import com.dailysatori.ui.feature.settings.crayfishnews.CrayfishNewsSettingsViewModel
```

Add after `viewModel { RemoteNewsSettingsViewModel(get(), get()) }`:

```kotlin
    viewModel { CrayfishNewsViewModel(get(), get()) }
    viewModel { CrayfishNewsSettingsViewModel(get(), get()) }
```

- [ ] **Step 4: Verify compilation**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (will fail because ViewModel classes don't exist yet — this is expected, proceed to Task 4)

Note: If compilation fails due to missing ViewModel classes, that's expected. We'll create them in the next tasks. Just verify the config/DI changes are syntactically correct by checking error messages reference only the missing classes.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt
git commit -m "feat: add crayfish news config keys and DI registration"
```

---

## Task 4: Crayfish News ViewModel

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/crayfishnews/CrayfishNewsViewModel.kt`

- [ ] **Step 1: Create CrayfishNewsViewModel.kt**

```kotlin
package com.dailysatori.ui.feature.crayfishnews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.crayfishnews.CrayfishNewsConfigValues
import com.dailysatori.service.crayfishnews.CrayfishNewsDetail
import com.dailysatori.service.crayfishnews.CrayfishNewsListItem
import com.dailysatori.service.crayfishnews.CrayfishNewsListResponse
import com.dailysatori.service.crayfishnews.CrayfishNewsResult
import com.dailysatori.service.crayfishnews.CrayfishNewsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CrayfishNewsMode(val title: String) {
    LATEST("小龙虾新闻"),
    DJI("大疆新闻"),
    ARCHIVE("历史新闻"),
}

data class CrayfishNewsState(
    val mode: CrayfishNewsMode = CrayfishNewsMode.LATEST,
    val latestNews: CrayfishNewsDetail? = null,
    val djiNews: CrayfishNewsDetail? = null,
    val archiveGeneral: List<CrayfishNewsListItem> = emptyList(),
    val archiveDji: List<CrayfishNewsListItem> = emptyList(),
    val selectedNews: CrayfishNewsDetail? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

class CrayfishNewsViewModel(
    private val settingRepo: SettingRepository,
    private val crayfishNewsService: CrayfishNewsService,
) : ViewModel() {
    private val _state = MutableStateFlow(CrayfishNewsState())
    val state: StateFlow<CrayfishNewsState> = _state.asStateFlow()

    fun loadInitial() {
        if (_state.value.latestNews == null) loadMode(CrayfishNewsMode.LATEST)
    }

    fun switchMode(mode: CrayfishNewsMode) {
        _state.update { it.copy(mode = mode, error = null) }
        val needsLoad = when (mode) {
            CrayfishNewsMode.LATEST -> _state.value.latestNews == null
            CrayfishNewsMode.DJI -> _state.value.djiNews == null
            CrayfishNewsMode.ARCHIVE -> _state.value.archiveGeneral.isEmpty() && _state.value.archiveDji.isEmpty()
        }
        if (needsLoad) loadMode(mode)
    }

    fun refresh() = loadMode(_state.value.mode, refresh = true)

    fun openArchiveItem(filename: String, category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = null) }
            val config = currentConfigOrSetError() ?: return@launch
            when (val result = crayfishNewsService.fetchNewsFile(config, category, filename)) {
                is CrayfishNewsResult.Success -> _state.update { it.copy(selectedNews = result.value, isLoading = false) }
                is CrayfishNewsResult.Failure -> _state.update { it.copy(error = result.message, isLoading = false) }
            }
        }
    }

    fun openLatestDetail() {
        val news = _state.value.latestNews ?: return
        _state.update { it.copy(selectedNews = news) }
    }

    fun openDjiDetail() {
        val news = _state.value.djiNews ?: return
        _state.update { it.copy(selectedNews = news) }
    }

    fun closeNews() = _state.update { it.copy(selectedNews = null) }

    private fun loadMode(mode: CrayfishNewsMode, refresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = !refresh, isRefreshing = refresh, error = null) }
            val config = currentConfigOrSetError() ?: return@launch
            when (mode) {
                CrayfishNewsMode.LATEST -> loadLatest(config, refresh)
                CrayfishNewsMode.DJI -> loadDji(config, refresh)
                CrayfishNewsMode.ARCHIVE -> loadArchive(config, refresh)
            }
        }
    }

    private suspend fun loadLatest(config: CrayfishNewsConfigValues, refresh: Boolean) {
        when (val result = crayfishNewsService.fetchLatest(config)) {
            is CrayfishNewsResult.Success -> _state.update {
                it.copy(latestNews = result.value, isLoading = false, isRefreshing = false)
            }
            is CrayfishNewsResult.Failure -> applyFailure(result.message)
        }
    }

    private suspend fun loadDji(config: CrayfishNewsConfigValues, refresh: Boolean) {
        when (val result = crayfishNewsService.fetchDji(config)) {
            is CrayfishNewsResult.Success -> _state.update {
                it.copy(djiNews = result.value, isLoading = false, isRefreshing = false)
            }
            is CrayfishNewsResult.Failure -> applyFailure(result.message)
        }
    }

    private suspend fun loadArchive(config: CrayfishNewsConfigValues, refresh: Boolean) {
        when (val result = crayfishNewsService.fetchNewsList(config, limit = 20)) {
            is CrayfishNewsResult.Success -> _state.update {
                it.copy(
                    archiveGeneral = result.value.general,
                    archiveDji = result.value.dji,
                    isLoading = false,
                    isRefreshing = false,
                )
            }
            is CrayfishNewsResult.Failure -> applyFailure(result.message)
        }
    }

    private fun applyFailure(message: String) {
        _state.update { it.copy(error = message, isLoading = false, isRefreshing = false) }
    }

    private fun currentConfigOrSetError(): CrayfishNewsConfigValues? {
        return when (val config = crayfishNewsService.configOrFailure(settingRepo.get(SettingKeys.crayfishNewsBaseUrl), settingRepo.get(SettingKeys.crayfishNewsApiToken))) {
            is CrayfishNewsResult.Success -> config.value
            is CrayfishNewsResult.Failure -> {
                _state.update { it.copy(error = config.message, isLoading = false, isRefreshing = false) }
                null
            }
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/crayfishnews/CrayfishNewsViewModel.kt
git commit -m "feat: add crayfish news viewmodel"
```

---

## Task 5: Crayfish News Detail Screen

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/crayfishnews/CrayfishNewsDetailScreen.kt`

- [ ] **Step 1: Create CrayfishNewsDetailScreen.kt**

```kotlin
package com.dailysatori.ui.feature.crayfishnews

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dailysatori.service.crayfishnews.CrayfishNewsDetail
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Spacing
import com.mikepenz.markdown.m3.Markdown

@Composable
fun CrayfishNewsDetailScreen(
    news: CrayfishNewsDetail,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val title = news.filename.removeSuffix(".md")
        .replace("news-summary-", "")
        .replace("dji-news-", "")

    AppScaffold(title = title, onBack = onBack) { modifier ->
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.m),
        ) {
            news.generated?.takeIf { it.isNotBlank() }?.let { generated ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(Spacing.xs))
                        Text(generated, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            news.sections.forEach { (sectionTitle, sectionContent) ->
                if (sectionTitle.isNotBlank()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                            Spacer(Modifier.width(Spacing.s))
                            Text(sectionTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (sectionContent.isNotBlank()) {
                    item {
                        SelectionContainer {
                            Markdown(
                                content = sectionContent,
                                typography = MarkdownStyles.typography(),
                                padding = MarkdownStyles.padding(),
                            )
                        }
                    }
                }
            }

            if (news.content.isNotBlank() && news.sections.isEmpty()) {
                item {
                    SelectionContainer {
                        Markdown(
                            content = news.content,
                            typography = MarkdownStyles.typography(),
                            padding = MarkdownStyles.padding(),
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/crayfishnews/CrayfishNewsDetailScreen.kt
git commit -m "feat: add crayfish news detail screen"
```

---

## Task 6: Crayfish News Main Screen

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/crayfishnews/CrayfishNewsScreen.kt`

- [ ] **Step 1: Create CrayfishNewsScreen.kt**

```kotlin
package com.dailysatori.ui.feature.crayfishnews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.service.crayfishnews.CrayfishNewsDetail
import com.dailysatori.service.crayfishnews.CrayfishNewsListItem
import com.dailysatori.ui.component.card.CustomCard
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Spacing
import com.mikepenz.markdown.m3.Markdown
import org.koin.androidx.compose.koinViewModel

@Composable
fun CrayfishNewsScreen() {
    val viewModel: CrayfishNewsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadInitial() }

    when {
        state.selectedNews != null -> CrayfishNewsDetailScreen(state.selectedNews!!, viewModel::closeNews)
        else -> CrayfishNewsListScreen(state, viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrayfishNewsListScreen(state: CrayfishNewsState, viewModel: CrayfishNewsViewModel) {
    AppScaffold(title = state.mode.title, showBack = false, actions = { CrayfishNewsMenu(state.mode, viewModel) }) { modifier ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = modifier.fillMaxSize(),
        ) {
            CrayfishNewsListContent(state, viewModel)
        }
    }
}

@Composable
private fun CrayfishNewsMenu(mode: CrayfishNewsMode, viewModel: CrayfishNewsViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "更多") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (mode != CrayfishNewsMode.LATEST) DropdownMenuItem(
                text = { Text("综合新闻") },
                leadingIcon = { Icon(Icons.Default.Article, contentDescription = null) },
                onClick = { viewModel.switchMode(CrayfishNewsMode.LATEST); expanded = false },
            )
            if (mode != CrayfishNewsMode.DJI) DropdownMenuItem(
                text = { Text("大疆新闻") },
                leadingIcon = { Icon(Icons.Default.Flight, contentDescription = null) },
                onClick = { viewModel.switchMode(CrayfishNewsMode.DJI); expanded = false },
            )
            if (mode != CrayfishNewsMode.ARCHIVE) DropdownMenuItem(
                text = { Text("历史新闻") },
                leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                onClick = { viewModel.switchMode(CrayfishNewsMode.ARCHIVE); expanded = false },
            )
            DropdownMenuItem(
                text = { Text("刷新") },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                onClick = { viewModel.refresh(); expanded = false },
            )
        }
    }
}

@Composable
private fun CrayfishNewsListContent(state: CrayfishNewsState, viewModel: CrayfishNewsViewModel) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> LoadingIndicator()
            state.error != null -> CrayfishNewsError(state.error, viewModel::refresh)
            else -> when (state.mode) {
                CrayfishNewsMode.LATEST -> LatestNewsContent(state.latestNews, viewModel::openLatestDetail)
                CrayfishNewsMode.DJI -> LatestNewsContent(state.djiNews, viewModel::openDjiDetail)
                CrayfishNewsMode.ARCHIVE -> ArchiveContent(state, viewModel)
            }
        }
    }
}

@Composable
private fun LatestNewsContent(news: CrayfishNewsDetail?, onClick: () -> Unit) {
    if (news == null) {
        EmptyState(icon = Icons.Default.Article, title = "暂无内容", subtitle = "小龙虾新闻暂时没有可显示的数据")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        item {
            CustomCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    val title = news.filename.removeSuffix(".md")
                        .replace("news-summary-", "")
                        .replace("dji-news-", "")
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    news.generated?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
                    news.sections.forEach { (sectionTitle, _) ->
                        if (sectionTitle.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                                Spacer(Modifier.width(Spacing.xs))
                                Text(sectionTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
        if (news.content.isNotBlank()) {
            item {
                SelectionContainer {
                    Markdown(
                        content = news.content,
                        typography = MarkdownStyles.cardTypography(),
                        padding = MarkdownStyles.cardPadding(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchiveContent(state: CrayfishNewsState, viewModel: CrayfishNewsViewModel) {
    val allItems = state.archiveGeneral.map { it to "general" } + state.archiveDji.map { it to "dji" }
    if (allItems.isEmpty()) {
        EmptyState(icon = Icons.Default.History, title = "暂无内容", subtitle = "小龙虾新闻暂时没有历史数据")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        items(allItems, key = { it.first.filename }) { (item, category) ->
            ArchiveItemCard(item, category, onClick = {
                viewModel.openArchiveItem(item.filename, category)
            })
        }
    }
}

@Composable
private fun ArchiveItemCard(item: CrayfishNewsListItem, category: String, onClick: () -> Unit) {
    CustomCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val displayTitle = item.filename.removeSuffix(".md")
                    .replace("news-summary-", "")
                    .replace("dji-news-", "")
                Text(displayTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (category == "general") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        if (category == "general") "综合" else "DJI",
                        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            item.generated?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item.preview.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CrayfishNewsError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text("重试") }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/crayfishnews/CrayfishNewsScreen.kt
git commit -m "feat: add crayfish news main screen"
```

---

## Task 7: Crayfish News Settings

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/crayfishnews/CrayfishNewsSettingsViewModel.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/crayfishnews/CrayfishNewsSettingsScreen.kt`

- [ ] **Step 1: Create CrayfishNewsSettingsViewModel.kt**

```kotlin
package com.dailysatori.ui.feature.settings.crayfishnews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.crayfishnews.CrayfishNewsConfigValues
import com.dailysatori.service.crayfishnews.CrayfishNewsResult
import com.dailysatori.service.crayfishnews.CrayfishNewsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CrayfishNewsSettingsState(
    val baseUrl: String = "",
    val token: String = "",
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val message: String? = null,
)

class CrayfishNewsSettingsViewModel(
    private val settingRepo: SettingRepository,
    private val crayfishNewsService: CrayfishNewsService,
) : ViewModel() {
    private val _state = MutableStateFlow(CrayfishNewsSettingsState())
    val state: StateFlow<CrayfishNewsSettingsState> = _state.asStateFlow()

    init { load() }

    fun updateBaseUrl(value: String) = _state.update { it.copy(baseUrl = value, message = null) }

    fun updateToken(value: String) = _state.update { it.copy(token = value, message = null) }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    baseUrl = settingRepo.get(SettingKeys.crayfishNewsBaseUrl).orEmpty(),
                    token = settingRepo.get(SettingKeys.crayfishNewsApiToken).orEmpty(),
                )
            }
        }
    }

    fun save() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isSaving = true, message = null) }
            settingRepo.upsert(SettingKeys.crayfishNewsBaseUrl, state.value.baseUrl.trim())
            settingRepo.upsert(SettingKeys.crayfishNewsApiToken, state.value.token.trim())
            _state.update { it.copy(isSaving = false, message = "小龙虾新闻设置已保存") }
        }
    }

    fun testConnection() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isTesting = true, message = null) }
            val config = crayfishNewsService.configOrFailure(state.value.baseUrl, state.value.token)
            val message = when (config) {
                is CrayfishNewsResult.Failure -> config.message
                is CrayfishNewsResult.Success<CrayfishNewsConfigValues> -> when (val result = crayfishNewsService.healthCheck(config.value)) {
                    is CrayfishNewsResult.Success -> "连接成功 (用户: ${result.value.user})"
                    is CrayfishNewsResult.Failure -> result.message
                }
            }
            _state.update { it.copy(isTesting = false, message = message) }
        }
    }
}
```

- [ ] **Step 2: Create CrayfishNewsSettingsScreen.kt**

```kotlin
package com.dailysatori.ui.feature.settings.crayfishnews

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
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun CrayfishNewsSettingsScreen(onBack: () -> Unit) {
    val viewModel: CrayfishNewsSettingsViewModel = koinViewModel()
    val state = viewModel.state.collectAsStateWithLifecycle().value

    AppScaffold(title = "小龙虾新闻设置", onBack = onBack) { modifier ->
        Column(
            modifier = modifier.padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = viewModel::updateBaseUrl,
                label = { Text("服务地址") },
                placeholder = { Text("http://192.168.1.10:3847") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.token,
                onValueChange = viewModel::updateToken,
                label = { Text("API Token") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
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
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.isSaving) "保存中..." else "保存") }
            TextButton(
                onClick = viewModel::testConnection,
                enabled = !state.isTesting,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.isTesting) "测试中..." else "测试连接") }
        }
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/settings/crayfishnews/CrayfishNewsSettingsViewModel.kt app/src/main/kotlin/com/dailysatori/ui/feature/settings/crayfishnews/CrayfishNewsSettingsScreen.kt
git commit -m "feat: add crayfish news settings screen and viewmodel"
```

---

## Task 8: Integration - RemoteNewsMenu + Settings

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt`

- [ ] **Step 1: Add CRAYFISH to RemoteNewsMode enum**

In `RemoteNewsViewModel.kt`, add to the enum:

```kotlin
enum class RemoteNewsMode(val title: String) {
    DIGESTS("远程新闻"),
    ARTICLES("远程文章"),
    FEEDS("信息源"),
    CRAYFISH("小龙虾新闻"),
}
```

Also update `switchMode()` to handle CRAYFISH mode (no loading needed, just switch):

In `switchMode()`, change the `needsLoad` check to:

```kotlin
    fun switchMode(mode: RemoteNewsMode) {
        _state.update { it.copy(mode = mode, error = null, loadMoreError = null) }
        if (mode == RemoteNewsMode.CRAYFISH) return
        val needsLoad = when (mode) {
            RemoteNewsMode.DIGESTS -> _state.value.digests.isEmpty()
            RemoteNewsMode.ARTICLES -> _state.value.articles.isEmpty()
            RemoteNewsMode.FEEDS -> _state.value.feeds.isEmpty()
            RemoteNewsMode.CRAYFISH -> false
        }
        if (needsLoad) loadMode(mode, refresh = false)
    }
```

Also update `loadMore()` to handle CRAYFISH:

```kotlin
    fun loadMore() {
        val current = _state.value
        if (current.isLoading || current.isRefreshing || current.isLoadingMore) return
        if (current.mode == RemoteNewsMode.CRAYFISH) return
        val nextPage = when (current.mode) {
            RemoteNewsMode.DIGESTS -> current.digestPagination?.next
            RemoteNewsMode.ARTICLES -> current.articlePagination?.next
            RemoteNewsMode.FEEDS -> current.feedPagination?.next
            RemoteNewsMode.CRAYFISH -> null
        } ?: return
        loadPage(current.mode, nextPage, append = true)
    }
```

And `loadInitial()`:

```kotlin
    fun loadInitial() {
        if (_state.value.mode == RemoteNewsMode.CRAYFISH) return
        if (_state.value.digests.isEmpty()) loadMode(RemoteNewsMode.DIGESTS, refresh = false)
    }
```

And `RemoteNewsListContent` to handle CRAYFISH:

```kotlin
    val itemsCount = when (state.mode) {
        RemoteNewsMode.DIGESTS -> state.digests.size
        RemoteNewsMode.ARTICLES -> state.articles.size
        RemoteNewsMode.FEEDS -> state.feeds.size
        RemoteNewsMode.CRAYFISH -> 0
    }
```

And `RemoteNewsLazyList` to handle CRAYFISH:

```kotlin
        when (state.mode) {
            RemoteNewsMode.DIGESTS -> items(state.digests, key = { it.id }) { digest -> ... }
            RemoteNewsMode.ARTICLES -> items(state.articles, key = { it.id }) { ... }
            RemoteNewsMode.FEEDS -> items(state.feeds, key = { it.id }) { ... }
            RemoteNewsMode.CRAYFISH -> {}
        }
```

- [ ] **Step 2: Update RemoteNewsScreen.kt - add menu item + conditional rendering**

In the `RemoteNewsMenu` composable, add a "小龙虾新闻" menu item before "刷新":

```kotlin
            if (mode != RemoteNewsMode.CRAYFISH) MenuItem("小龙虾新闻", Icons.Default.Article) { viewModel.switchMode(RemoteNewsMode.CRAYFISH); expanded = false }
```

In `RemoteNewsScreen`, modify the routing to include CrayfishNewsScreen:

```kotlin
@Composable
fun RemoteNewsScreen() {
    val viewModel: RemoteNewsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    when {
        state.mode == RemoteNewsMode.CRAYFISH -> CrayfishNewsScreen()
        state.selectedArticle != null -> RemoteArticleDetailScreen(state.selectedArticle!!, viewModel::closeArticle)
        state.selectedDigest != null -> RemoteDigestDetailScreen(state.selectedDigest!!, viewModel::closeDigest, viewModel::openArticle)
        else -> RemoteNewsListScreen(state, viewModel)
    }
}
```

Remove the `LaunchedEffect(Unit) { viewModel.loadInitial() }` from `RemoteNewsScreen` and move it into `RemoteNewsListScreen`:

In `RemoteNewsListScreen`, add:

```kotlin
    LaunchedEffect(Unit) { viewModel.loadInitial() }
```

Add import for `CrayfishNewsScreen`:

```kotlin
import com.dailysatori.ui.feature.crayfishnews.CrayfishNewsScreen
```

- [ ] **Step 3: Update SettingsScreen.kt - add settings page**

Add import:
```kotlin
import com.dailysatori.ui.feature.settings.crayfishnews.CrayfishNewsSettingsScreen
```

Add `CRAYFISH_NEWS_SETTINGS` to `SettingsPage` enum:
```kotlin
    CRAYFISH_NEWS_SETTINGS,
```

Add case in `when` block:
```kotlin
        SettingsPage.CRAYFISH_NEWS_SETTINGS -> CrayfishNewsSettingsScreen(onBack = { currentPage = SettingsPage.MAIN })
```

Add row in `NetworkSection` after the existing "远程新闻设置" row:

```kotlin
        SettingsRow(
            icon = Icons.Default.Language,
            title = "小龙虾新闻设置",
            subtitle = "配置小龙虾新闻服务地址和 Token",
            onClick = { onNavigate(SettingsPage.CRAYFISH_NEWS_SETTINGS) },
        )
```

- [ ] **Step 4: Verify compilation**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt
git commit -m "feat: integrate crayfish news into remote news tab and settings"
```

---

## Task 9: Final Build Verification + Deploy

- [ ] **Step 1: Full build verification**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Install on device**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug && adb shell am start -n com.dailysatori/.MainActivity`
Expected: App installs and launches successfully

- [ ] **Step 3: Manual verification**

1. Go to Settings -> "网络与同步" -> verify "小龙虾新闻设置" row appears
2. Enter Base URL and Token, save, test connection
3. Go to Remote News tab -> tap "..." menu -> verify "小龙虾新闻" option appears
4. Switch to Crayfish News mode -> verify list loads
5. Switch between 综合/DJI/历史 modes via submenu
6. Tap a news item -> verify detail view renders markdown

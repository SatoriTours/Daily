# Crayfish News (小龙虾新闻) Feature Design

## Overview

Add a new news source "Crayfish News" (小龙虾新闻) to the existing remote news tab, accessible via the "..." overflow menu. The feature fetches news from an independent API service and includes its own settings configuration.

## Requirements

1. New news source integrated into the existing remote news tab via "..." menu switching
2. Supports three views: latest general news, DJI news, and historical news archive
3. Independent settings for API Base URL and Token
4. Markdown rendering of news content
5. Full API coverage: health check, latest news, DJI news, news list, individual news files

## Architecture

### Data Flow

```
SQLite (setting table)
  -> SettingRepository.get(SettingKeys.crayfishNewsBaseUrl / crayfishNewsApiToken)
  -> CrayfishNewsViewModel.currentConfigOrSetError()
  -> CrayfishNewsService.fetch*(config, ...)
  -> CrayfishNewsResult<T>
  -> CrayfishNewsState (MutableStateFlow)
  -> Composable UI
```

### Module Structure

#### shared/ layer

**CrayfishNewsModels.kt** (`shared/src/commonMain/kotlin/com/dailysatori/service/crayfishnews/`)
- `CrayfishNewsConfigValues(baseUrl: String, token: String)`
- `CrayfishNewsResult<T>` - sealed class (Success/Failure), mirrors `RemoteNewsResult`
- `CrayfishNewsDetail` - single news item with sections, content, preview
- `CrayfishNewsListItem` - list item with filename, generated, source, preview
- `CrayfishNewsListResponse` - categorized list response (general + dji arrays)
- `CrayfishHealthResponse` - health check response (status, user, ts)

**CrayfishNewsService.kt** (`shared/src/commonMain/kotlin/com/dailysatori/service/crayfishnews/`)
- Uses injected Ktor `HttpClient` singleton
- API calls with Bearer token auth
- Methods:
  - `healthCheck(config)` -> `CrayfishNewsResult<CrayfishHealthResponse>`
  - `fetchLatest(config)` -> `CrayfishNewsResult<CrayfishNewsDetail>`
  - `fetchDji(config)` -> `CrayfishNewsResult<CrayfishNewsDetail>`
  - `fetchNewsList(config, category?, limit?)` -> `CrayfishNewsResult<CrayfishNewsListResponse>`
  - `fetchNewsFile(config, category, filename)` -> `CrayfishNewsResult<CrayfishNewsDetail>`
  - `configOrFailure(baseUrl?, token?)` -> `CrayfishNewsResult<CrayfishNewsConfigValues>`
- Error handling mirrors `RemoteNewsService` pattern
- URL building: `{baseUrl}/news/latest`, `{baseUrl}/news/dji`, `{baseUrl}/news?category=&limit=`, `{baseUrl}/news/{category}/{filename}`

**Config.kt additions** (`shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt`)
- `SettingKeys.crayfishNewsBaseUrl = "crayfish_news_base_url"`
- `SettingKeys.crayfishNewsApiToken = "crayfish_news_api_token"`

#### app/ layer - ViewModel

**CrayfishNewsViewModel.kt** (`app/src/main/kotlin/com/dailysatori/ui/feature/crayfishnews/`)
- `CrayfishNewsMode` enum: LATEST("小龙虾新闻"), DJI("大疆新闻"), ARCHIVE("历史新闻")
- `CrayfishNewsState` data class:
  - mode, latestNews, djiNews, archiveItems (general + dji)
  - selectedNews (for detail view)
  - isLoading, isRefreshing, error, etc.
- Methods: loadInitial, switchMode, refresh, openNews(filename, category), closeNews
- Reads config from SettingRepository using new SettingKeys

#### app/ layer - UI

**CrayfishNewsScreen.kt** (`app/src/main/kotlin/com/dailysatori/ui/feature/crayfishnews/`)
- Entry composable: routes between list view and detail view
- List screen: renders based on current mode
  - LATEST/DJI: single card showing the latest news with sections preview
  - ARCHIVE: LazyColumn of `CrayfishNewsListItem` cards
- Own "..." sub-menu for switching between LATEST/DJI/ARCHIVE + refresh
- Pull-to-refresh support

**CrayfishNewsDetailScreen.kt** (`app/src/main/kotlin/com/dailysatori/ui/feature/crayfishnews/`)
- Full markdown rendering of news content using `Markdown` component
- Section headers with colored indicators (matching existing `DigestBody` pattern)
- Generated timestamp display

**Integration with RemoteNewsScreen:**
- Add `RemoteNewsMode.CRAYFISH("小龙虾新闻")` to existing enum
- Add "小龙虾新闻" menu item in `RemoteNewsMenu`
- When mode is `CRAYFISH`, render `CrayfishNewsScreen()` instead of existing list content
- CrayfishNewsScreen gets its own ViewModel via `koinViewModel()`

#### app/ layer - Settings

**CrayfishNewsSettingsScreen.kt** (`app/src/main/kotlin/com/dailysatori/ui/feature/settings/crayfishnews/`)
- Form with Base URL and API Token fields
- Save and Test Connection buttons
- Test calls `/health` endpoint

**CrayfishNewsSettingsViewModel.kt** (`app/src/main/kotlin/com/dailysatori/ui/feature/settings/crayfishnews/`)
- Loads/saves crayfish news settings using `SettingKeys.crayfishNewsBaseUrl` / `crayfishNewsApiToken`
- Test connection calls `CrayfishNewsService.healthCheck()`

**SettingsScreen.kt changes:**
- Add `CRAYFISH_NEWS_SETTINGS` to `SettingsPage` enum
- Add "小龙虾新闻设置" row in "NetworkSection" under existing "远程新闻设置"
- Wire up navigation

### DI Registration

**SharedModule.kt:** Register `CrayfishNewsService(get())`
**ViewModelModule.kt:** Register `CrayfishNewsViewModel(get(), get())` and `CrayfishNewsSettingsViewModel(get(), get())`

## UI Design

### News List Item
- Card with filename-derived date as title
- Preview text (truncated)
- Category badge (综合/DJI)
- Generated timestamp

### News Detail
- Title from filename
- Generated timestamp with calendar icon
- Sections rendered with colored indicator bars (reuse `DigestBody` section pattern)
- Full markdown content

### Settings
- Same layout as existing `RemoteNewsSettingsScreen`
- Two OutlinedTextFields + Save + Test Connection
- Test uses `/health` endpoint

## Error Handling

Same pattern as existing remote news:
- 401 -> "Token 无效，请检查小龙虾新闻设置"
- 5xx -> "小龙虾新闻服务暂时不可用"
- Connection error -> "无法连接小龙虾新闻服务"
- Missing config -> "请先配置小龙虾新闻服务"

## Files to Create

1. `shared/src/commonMain/kotlin/com/dailysatori/service/crayfishnews/CrayfishNewsModels.kt`
2. `shared/src/commonMain/kotlin/com/dailysatori/service/crayfishnews/CrayfishNewsService.kt`
3. `app/src/main/kotlin/com/dailysatori/ui/feature/crayfishnews/CrayfishNewsViewModel.kt`
4. `app/src/main/kotlin/com/dailysatori/ui/feature/crayfishnews/CrayfishNewsScreen.kt`
5. `app/src/main/kotlin/com/dailysatori/ui/feature/crayfishnews/CrayfishNewsDetailScreen.kt`
6. `app/src/main/kotlin/com/dailysatori/ui/feature/settings/crayfishnews/CrayfishNewsSettingsViewModel.kt`
7. `app/src/main/kotlin/com/dailysatori/ui/feature/settings/crayfishnews/CrayfishNewsSettingsScreen.kt`

## Files to Modify

1. `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt` - add SettingKeys
2. `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt` - register service
3. `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt` - register ViewModels
4. `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt` - add CRAYFISH mode
5. `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsScreen.kt` - add menu item + conditional rendering
6. `app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt` - add settings page

# Daily Satori Flutter → KMP/CMP Migration Design

## Overview

Migrate Daily Satori from Flutter to Kotlin Multiplatform (KMP) + Compose Multiplatform (CMP), preserving all existing UI and functionality. The migration targets Android first while retaining cross-platform capability for future iOS support.

## Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Architecture | Layered shared (KMP shared logic + CMP UI in platform modules) | Maximum code reuse, clean separation, iOS-ready |
| Database | SQLDelight | Most mature KMP database solution, type-safe SQL |
| WebView scraping | Android WebView (expect/actual) | Handles JS-rendered pages, matches original capability |
| Migration strategy | New project on `android` branch | Clean start, parallel maintenance possible |
| State management | ViewModel + StateFlow | Natural Android fit, lifecycle-aware |
| DI framework | Koin | KMP-friendly, simple DSL |
| Navigation | Compose Navigation (Type-Safe) | Declarative, type-safe routes |
| Network | Ktor Client | KMP-native HTTP client |
| Serialization | kotlinx.serialization | Kotlin-native, multiplatform |
| Image loading | Coil | Compose-native, mature |

## Project Structure

```
DailySatori/                          # Root (android branch)
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
│
├── shared/                           # KMP shared module
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/com/dailysatori/
│       │   ├── data/
│       │   │   ├── db/               # SQLDelight schemas
│       │   │   ├── repository/       # Repository implementations
│       │   │   └── model/            # Data models (data class)
│       │   ├── domain/
│       │   │   ├── usecase/          # Business use cases
│       │   │   └── entity/           # Domain entities
│       │   ├── service/
│       │   │   ├── ai/               # OpenAI-compatible API client
│       │   │   ├── i18n/             # YAML-based i18n (zh/en)
│       │   │   ├── backup/           # ZIP backup logic
│       │   │   ├── book/             # Book search engines
│       │   │   └── webcontent/       # Web content processing pipeline
│       │   └── platform/             # expect declarations
│       │       ├── HttpClientFactory.kt
│       │       ├── WebViewLoader.kt
│       │       ├── FileManager.kt
│       │       ├── AppInfoProvider.kt
│       │       └── PlatformContext.kt
│       ├── commonMain/resources/     # Shared resources
│       │   ├── i18n/                 # zh.yaml, en.yaml
│       │   └── config/              # ai_models.yaml, ai_prompts.yaml
│       ├── androidMain/kotlin/       # Android actual implementations
│       └── iosMain/kotlin/           # iOS actual (reserved)
│
├── app/                              # Android application module
│   ├── build.gradle.kts
│   └── src/main/
│       ├── kotlin/com/dailysatori/
│       │   ├── ui/
│       │   │   ├── theme/            # MaterialTheme, colors, typography
│       │   │   ├── components/       # Reusable composables
│       │   │   ├── navigation/       # NavHost, routes
│       │   │   └── pages/            # Page composables
│       │   │       ├── home/
│       │   │       ├── articles/
│       │   │       ├── article_detail/
│       │   │       ├── diary/
│       │   │       ├── books/
│       │   │       ├── aichat/
│       │   │       ├── aiconfig/
│       │   │       ├── settings/
│       │   │       ├── share_dialog/
│       │   │       ├── weekly_summary/
│       │   │       ├── backup_restore/
│       │   │       ├── backup_settings/
│       │   │       └── plugin_center/
│       │   ├── viewmodel/            # ViewModels (one per page)
│       │   ├── platform/             # Android actual implementations
│       │   │   ├── AndroidWebViewLoader.kt
│       │   │   ├── AndroidFileManager.kt
│       │   │   └── AndroidHttpClientFactory.kt
│       │   ├── service/              # Android-specific services
│       │   │   ├── WebServerService.kt
│       │   │   ├── ClipboardMonitorService.kt
│       │   │   └── ShareReceiveActivity.kt
│       │   ├── MainActivity.kt
│       │   └── DailySatoriApp.kt     # Composable entry + Koin setup
│       ├── res/
│       └── assets/
│           ├── js/                   # Readability.js, parse_content.js, etc.
│           ├── css/                  # common.css
│           ├── fonts/                # Lato font family
│           ├── images/               # cover.jpeg
│           ├── easylistchina+easylist.txt
│           └── website/              # Admin HTML/CSS/JS
│
├── iosApp/                           # iOS entry (reserved, not implemented)
└── docs/
```

## Dependency Mapping (Flutter → KMP/Android)

### State Management & DI
| Flutter | KMP/Android |
|---------|-------------|
| flutter_riverpod | ViewModel + StateFlow + Koin |
| riverpod_annotation | Plain Kotlin class |
| freezed | data class + kotlinx.serialization |

### Navigation
| Flutter | KMP/Android |
|---------|-------------|
| go_router | Compose Navigation (Type-Safe) |

### Database
| Flutter | KMP/Android |
|---------|-------------|
| objectbox | SQLDelight |
| sqflite | SQLDelight (replaces both) |

### Network & Web
| Flutter | KMP/Android |
|---------|-------------|
| dio | Ktor Client |
| openai_dart | Custom Ktor-based OpenAI client |
| connectivity_plus | ConnectivityManager (expect/actual) |
| url_launcher | Android Intent (expect/actual) |
| flutter_inappwebview | Android WebView (expect/actual) |
| shelf / shelf_router / shelf_static | Ktor Server (embedded) |
| web_socket_channel | Ktor WebSocket |
| html | Jsoup (Android) |
| googleapis | Ktor direct API calls |

### Media & Content
| Flutter | KMP/Android |
|---------|-------------|
| cached_network_image | Coil (Compose) |
| flutter_markdown | compose-multiplatform-markdown |
| flutter_html | Custom Compose HTML renderer or HtmlCompat |
| photo_view | Custom Compose pinch-zoom |
| image_picker | Activity Result API |
| file_picker | SAF (Storage Access Framework) |
| google_fonts | Bundled font assets |

### Platform & Utility
| Flutter | KMP/Android |
|---------|-------------|
| path_provider | Context.getDir() (expect/actual) |
| share_plus | Intent.ACTION_SEND |
| android_intent_plus | Android Intent API |
| permission_handler | Android Permission API |
| package_info_plus | PackageInfo (expect/actual) |
| logger | Kermit (KMP) |
| intl | kotlinx-datetime |
| pinyin | pinyin4j |
| jinja | Custom template engine (simplified) |
| yaml | kaml (Kotlin YAML) |
| archive / flutter_archive | java.util.zip |
| open_file | Android Intent |

### Icons
| Flutter | KMP/Android |
|---------|-------------|
| font_awesome_flutter | Material Icons + custom SVG |
| feather_icons | Material Icons + custom SVG |
| cupertino_icons | Material Icons |

## Data Layer

### SQLDelight Schema

All datetime fields stored as INTEGER (epoch millis). Boolean fields stored as INTEGER (0/1).

#### Tables

**article**
```sql
CREATE TABLE article (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT,
    ai_title TEXT,
    content TEXT,
    ai_content TEXT,
    html_content TEXT,
    ai_markdown_content TEXT,
    url TEXT UNIQUE,
    is_favorite INTEGER DEFAULT 0,
    comment TEXT,
    status TEXT DEFAULT 'pending',
    cover_image TEXT,
    cover_image_url TEXT,
    pub_date INTEGER,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

**tag**
```sql
CREATE TABLE tag (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT UNIQUE NOT NULL,
    icon TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

**article_tag** (many-to-many join)
```sql
CREATE TABLE article_tag (
    article_id INTEGER NOT NULL REFERENCES article(id) ON DELETE CASCADE,
    tag_id INTEGER NOT NULL REFERENCES tag(id) ON DELETE CASCADE,
    PRIMARY KEY (article_id, tag_id)
);
```

**image**
```sql
CREATE TABLE image (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    url TEXT,
    path TEXT,
    article_id INTEGER REFERENCES article(id) ON DELETE CASCADE,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

**book**
```sql
CREATE TABLE book (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    author TEXT NOT NULL,
    category TEXT NOT NULL,
    cover_image TEXT NOT NULL,
    introduction TEXT NOT NULL,
    has_update INTEGER DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

**book_viewpoint**
```sql
CREATE TABLE book_viewpoint (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    book_id INTEGER NOT NULL REFERENCES book(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    example TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

**diary**
```sql
CREATE TABLE diary (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    content TEXT NOT NULL,
    tags TEXT,
    mood TEXT,
    images TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

**ai_config**
```sql
CREATE TABLE ai_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    api_address TEXT NOT NULL,
    api_token TEXT NOT NULL,
    model_name TEXT NOT NULL,
    function_type INTEGER DEFAULT 0,
    inherit_from_general INTEGER DEFAULT 0,
    is_default INTEGER DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

**setting**
```sql
CREATE TABLE setting (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    key TEXT UNIQUE,
    value TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

**weekly_summary**
```sql
CREATE TABLE weekly_summary (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    week_start_date INTEGER NOT NULL,
    week_end_date INTEGER NOT NULL,
    content TEXT NOT NULL,
    article_count INTEGER DEFAULT 0,
    diary_count INTEGER DEFAULT 0,
    viewpoint_count INTEGER DEFAULT 0,
    article_ids TEXT,
    diary_ids TEXT,
    viewpoint_ids TEXT,
    app_ideas TEXT,
    status TEXT DEFAULT 'pending',
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

**session**
```sql
CREATE TABLE session (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT UNIQUE NOT NULL,
    is_authenticated INTEGER DEFAULT 0,
    username TEXT,
    last_accessed_at INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

#### Key Queries

```sql
selectArticles:
SELECT * FROM article ORDER BY created_at DESC;

selectArticlesPaginated:
SELECT * FROM article ORDER BY created_at DESC LIMIT ? OFFSET ?;

selectArticlesByStatus:
SELECT * FROM article WHERE status = ? ORDER BY created_at DESC;

selectArticlesByTag:
SELECT a.* FROM article a
INNER JOIN article_tag at ON a.id = at.article_id
WHERE at.tag_id = ?
ORDER BY a.created_at DESC;

searchArticles:
SELECT * FROM article
WHERE title LIKE '%' || ? || '%'
   OR ai_title LIKE '%' || ? || '%'
   OR content LIKE '%' || ? || '%'
ORDER BY created_at DESC;

selectArticlesByDateRange:
SELECT * FROM article
WHERE created_at >= ? AND created_at <= ?
ORDER BY created_at DESC;

selectFavoriteArticles:
SELECT * FROM article WHERE is_favorite = 1 ORDER BY created_at DESC;

selectArticleDailyCounts:
SELECT date(created_at / 1000, 'unixepoch') as day, COUNT(*) as count
FROM article
GROUP BY day
ORDER BY day;

selectAllTags:
SELECT * FROM tag ORDER BY name;

getTagsByArticle:
SELECT t.* FROM tag t
INNER JOIN article_tag at ON t.id = at.tag_id
WHERE at.article_id = ?;

insertArticleTag:
INSERT OR IGNORE INTO article_tag (article_id, tag_id) VALUES (?, ?);

deleteArticleTags:
DELETE FROM article_tag WHERE article_id = ?;

selectImagesByArticle:
SELECT * FROM image WHERE article_id = ?;

searchDiaries:
SELECT * FROM diary
WHERE content LIKE '%' || ? || '%'
   OR tags LIKE '%' || ? || '%'
ORDER BY created_at DESC;

selectDiariesByDateRange:
SELECT * FROM diary
WHERE created_at >= ? AND created_at <= ?
ORDER BY created_at DESC;

searchBooks:
SELECT * FROM book
WHERE title LIKE '%' || ? || '%'
   OR author LIKE '%' || ? || '%'
ORDER BY created_at DESC;

selectViewpointsByBook:
SELECT * FROM book_viewpoint WHERE book_id = ? ORDER BY created_at DESC;

selectDefaultAiConfig:
SELECT * FROM ai_config WHERE is_default = 1 AND function_type = ?;

selectSettingByKey:
SELECT * FROM setting WHERE key = ?;

upsertSetting:
INSERT OR REPLACE INTO setting (key, value, created_at, updated_at)
VALUES (?, ?, ?, ?);

selectLatestWeeklySummary:
SELECT * FROM weekly_summary ORDER BY week_start_date DESC LIMIT 1;

selectWeeklySummaries:
SELECT * FROM weekly_summary ORDER BY week_start_date DESC;

selectArticleCount:
SELECT COUNT(*) FROM article;
```

### Repository Layer

Each repository provides Flow-based reactive queries and synchronous CRUD:

```kotlin
class ArticleRepository(db: DailySatoriDatabase) {
    fun getAll(): Flow<List<Article>>
    fun getPaginated(limit: Long, offset: Long): Flow<List<Article>>
    fun getByStatus(status: String): Flow<List<Article>>
    fun getByTag(tagId: Long): Flow<List<Article>>
    fun search(query: String): Flow<List<Article>>
    fun getByDateRange(start: Instant, end: Instant): Flow<List<Article>>
    fun getFavorites(): Flow<List<Article>>
    fun getDailyCounts(): Flow<Map<String, Long>>
    fun getById(id: Long): Article?
    fun insert(article: Article): Long
    fun update(article: Article)
    fun delete(id: Long)
    fun toggleFavorite(id: Long)
    fun count(): Long
}
```

Similar pattern for: DiaryRepository, BookRepository, BookViewpointRepository, TagRepository, ImageRepository, AIConfigRepository, SettingRepository, WeeklySummaryRepository, SessionRepository.

## Service Layer

### Service Architecture

Services live in `shared/commonMain` for cross-platform logic, with platform-specific operations delegated via expect/actual.

### Priority Classification

**Critical (must init first):**
- DatabaseService (SQLDelight init)
- SettingService (settings CRUD)
- I18nService (load YAML translations)
- TimeService (date utilities)

**High:**
- AIConfigService (AI config resolution, inheritance)
- HttpClientService (Ktor client setup)
- FileService (expect/actual file operations)
- ADBlockService (rule loading)

**Normal:**
- AiService (OpenAI API calls)
- WebpageParserService (content pipeline)
- BackupService (ZIP backup/restore)
- BookSearchService (multi-engine search)
- PluginService (prompt/preset management)
- MigrationService (data migration)

**Low (Android-specific):**
- WebServerService (Ktor Server)
- ClipboardMonitorService
- AppUpgradeService
- ShareReceiveService
- ArticleRecoveryService
- WeeklySummaryService

### Web Content Pipeline

```
URL → WebpageParserService
        ├── ArticleManager (create/reset article in DB)
        ├── ContentExtractor (WebView load → extract HTML)
        │   ├── expect/actual WebViewLoader
        │   ├── Readability.js parsing
        │   └── ADBlock filtering
        ├── AiProcessor (summarize, translate, html-to-markdown)
        │   └── AiService → Ktor → OpenAI API
        └── ImageProcessor (download + cache images)
            └── FileService (expect/actual)
```

### AI Service

```kotlin
class AiService(private val httpClient: HttpClient, private val configService: AIConfigService) {
    suspend fun chat(messages: List<ChatMessage>, config: AiConfig): String
    suspend fun summarize(content: String, config: AiConfig): String
    suspend fun translate(text: String, config: AiConfig): String
    suspend fun htmlToMarkdown(html: String, config: AiConfig): String
    suspend fun singleLineSummary(content: String, config: AiConfig): String
}
```

Uses Ktor to call OpenAI-compatible chat completion endpoints. Supports configurable base URL for any provider.

### I18n Service

```kotlin
class I18nService {
    fun setLanguage(lang: String)
    fun t(key: String): String
    fun t(key: String, vararg args: Any?): String
}

// Extension function for convenience
fun String.t(vararg args: Any?): String = i18nService.t(this, *args)
```

YAML files loaded from commonMain resources. Fallback chain: requested language → Chinese → key itself.

### Book Search Engines

```kotlin
interface BookSearchEngine {
    suspend fun search(query: String, limit: Int = 10): List<BookSearchResult>
}

class GoogleBooksSearchEngine(client: HttpClient) : BookSearchEngine
class OpenLibrarySearchEngine(client: HttpClient) : BookSearchEngine
class ISBNdbSearchEngine(client: HttpClient) : BookSearchEngine

class BookSearchService(private val engines: List<BookSearchEngine>) {
    suspend fun search(query: String): List<BookSearchResult>
}
```

## expect/actual Interfaces

```kotlin
// Platform context
expect class PlatformContext

// HTTP client factory
expect class HttpClientFactory {
    fun create(): HttpClient
}

// Headless WebView content extraction
expect class WebViewLoader {
    fun loadContent(url: String, timeoutMs: Long, callback: (Result<String>) -> Unit)
    fun loadContentSync(url: String, timeoutMs: Long): String
}

// File system operations
expect class FileManager {
    fun getAppDataDir(): String
    fun getImagesDir(): String
    fun getBackupDir(): String
    fun getCacheDir(): String
    fun writeFile(relativePath: String, data: ByteArray)
    fun readFile(relativePath: String): ByteArray
    fun deleteFile(relativePath: String): Boolean
    fun exists(relativePath: String): Boolean
    fun listFiles(relativePath: String): List<String>
    fun copyFile(src: String, dest: String)
    fun fileSize(relativePath: String): Long
    fun freeSpace(): Long
    fun totalSpace(): Long
}

// App information
expect class AppInfoProvider {
    fun getAppVersion(): String
    fun getAppName(): String
    fun getPackageName(): String
    fun isDebugMode(): Boolean
}

// Network connectivity
expect class ConnectivityProvider {
    fun isOnline(): Flow<Boolean>
}
```

## UI Layer

### Theme System

```kotlin
object AppColors {
    val primary = Color(0xFF...)
    val onPrimary = Color(0xFF...)
    // ... maps from Flutter AppColors
}

object AppSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    // ... maps from Flutter Dimensions
}

object AppTypography {
    val headlineLarge: TextStyle
    val headlineMedium: TextStyle
    val titleLarge: TextStyle
    val bodyLarge: TextStyle
    val bodyMedium: TextStyle
    val labelSmall: TextStyle
    // Uses Lato font family
}

@Composable
fun DailySatoriTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) darkColorScheme(...) else lightColorScheme(...)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(...),
        content = content
    )
}
```

### Navigation

```kotlin
@Serializable data object HomeRoute
@Serializable data object ArticlesRoute
@Serializable data class ArticleDetailRoute(val articleId: Long)
@Serializable data object DiaryRoute
@Serializable data object BooksRoute
@Serializable data object BookSearchRoute
@Serializable data object AiChatRoute
@Serializable data object AiConfigRoute
@Serializable data class AiConfigEditRoute(val configId: Long? = null, val functionType: Int = 0)
@Serializable data object SettingsRoute
@Serializable data class ShareDialogRoute(val url: String)
@Serializable data object WeeklySummaryRoute
@Serializable data object BackupRestoreRoute
@Serializable data object BackupSettingsRoute
@Serializable data object PluginCenterRoute

@Composable
fun DailySatoriNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = HomeRoute) {
        composable<HomeRoute> { HomeScreen(navController) }
        composable<ArticlesRoute> { ArticlesScreen(navController) }
        composable<ArticleDetailRoute> { ArticleDetailScreen(it.articleId, navController) }
        composable<DiaryRoute> { DiaryScreen(navController) }
        composable<BooksRoute> { BooksScreen(navController) }
        composable<BookSearchRoute> { BookSearchScreen(navController) }
        composable<AiChatRoute> { AiChatScreen(navController) }
        composable<AiConfigRoute> { AiConfigScreen(navController) }
        composable<AiConfigEditRoute> { AiConfigEditScreen(navController) }
        composable<SettingsRoute> { SettingsScreen(navController) }
        composable<ShareDialogRoute> { ShareDialogScreen(it.url, navController) }
        composable<WeeklySummaryRoute> { WeeklySummaryScreen(navController) }
        composable<BackupRestoreRoute> { BackupRestoreScreen(navController) }
        composable<BackupSettingsRoute> { BackupSettingsScreen(navController) }
        composable<PluginCenterRoute> { PluginCenterScreen(navController) }
    }
}
```

### Page Composables Mapping

| Flutter Page | Compose Screen | Key Composables |
|---|---|---|
| HomeView | HomeScreen | Scaffold, BottomNavigation (5 tabs) |
| ArticlesView | ArticlesScreen | SearchBar, LazyColumn, ArticleCard, FilterChips |
| ArticleDetailView | ArticleDetailScreen | TabRow (Summary/Original), Markdown, ImageGallery |
| DiaryView | DiaryScreen | LazyColumn, DiaryCard, SearchBar, FAB |
| DiaryEditor | DiaryEditorSheet | BottomSheetScaffold, MarkdownToolbar, ImagePicker |
| BooksView | BooksScreen | HorizontalPager, ViewpointCard, BookFilter |
| BookSearchView | BookSearchScreen | SearchBar, LazyColumn, BookSearchResultCard |
| AIChatView | AiChatScreen | LazyColumn, MessageBubble, ChatInput |
| AIConfigView | AiConfigScreen | LazyColumn, ConfigCard |
| AIConfigEditView | AiConfigEditScreen | Form fields, Selection sheets |
| SettingsView | SettingsScreen | Preference items |
| ShareDialogView | ShareDialogScreen | AlertDialog, URL, title, tags, comment |
| WeeklySummaryView | WeeklySummaryScreen | Markdown, HorizontalPager (history) |
| BackupRestoreView | BackupRestoreScreen | FilePicker, ProgressBar |
| BackupSettingsView | BackupSettingsScreen | DirectoryPicker |
| PluginCenterView | PluginCenterScreen | LazyColumn, PluginCard |
| LeftBarView | NavigationRail | Side navigation |

### Shared Components

| Flutter Component | Compose Component |
|---|---|
| SAppBar | Custom TopAppBar composable |
| ArticleCard | ArticleCard composable |
| DiaryCard | DiaryCard composable |
| ViewpointCard | ViewpointCard composable |
| ChatInput | ChatInput composable |
| MessageBubble | MessageBubble composable |
| CustomCard | Card composable with custom styling |
| SearchTextField | SearchBar composable |
| CommentField | TextField composable |
| LoadingIndicator | CircularProgressIndicator |
| EmptyStateWidget | EmptyState composable |
| ProcessingDialog | AlertDialog with CircularProgressIndicator |
| GenericSearchBar | SearchBar composable |
| GenericFilterDialog | AlertDialog with filter options |
| SmartImage | AsyncImage (Coil) with placeholder |
| CommonCalendar | DatePicker or custom calendar composable |

## ViewModel Layer

Each page has a corresponding ViewModel:

```kotlin
class ArticlesViewModel(
    private val articleRepo: ArticleRepository,
    private val tagRepo: TagRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ArticlesState())
    val state: StateFlow<ArticlesState> = _state.asStateFlow()

    data class ArticlesState(
        val articles: List<Article> = emptyList(),
        val isLoading: Boolean = false,
        val searchQuery: String = "",
        val selectedTagId: Long? = null,
        val showFavoritesOnly: Boolean = false,
    )

    fun loadArticles() { ... }
    fun search(query: String) { ... }
    fun filterByTag(tagId: Long?) { ... }
    fun toggleFavorite(articleId: Long) { ... }
}
```

ViewModels are provided via Koin and scoped to Compose navigation destinations.

## Koin DI Setup

```kotlin
val sharedModule = module {
    // Database
    single { DatabaseDriverFactory(get()).createDriver() }
    single { DailySatoriDatabase(get()) }

    // Repositories
    single { ArticleRepository(get()) }
    single { DiaryRepository(get()) }
    single { BookRepository(get()) }
    single { BookViewpointRepository(get()) }
    single { TagRepository(get()) }
    single { ImageRepository(get()) }
    single { AIConfigRepository(get()) }
    single { SettingRepository(get()) }
    single { WeeklySummaryRepository(get()) }
    single { SessionRepository(get()) }

    // Services
    single { SettingService(get()) }
    single { I18nService() }
    single { AIConfigService(get()) }
    single { AiService(get(), get()) }
    single { BookSearchService(listOf(get<GoogleBooksSearchEngine>(), get<OpenLibrarySearchEngine>())) }
    single { BackupService(get(), get(), get()) }
    single { PluginService(get()) }
    single { WebpageParserService(get(), get(), get(), get()) }
}

val platformModule = module {
    // Platform-specific
    single<HttpClientFactory> { AndroidHttpClientFactory() }
    single<FileManager> { AndroidFileManager(get()) }
    single<WebViewLoader> { AndroidWebViewLoader(get()) }
    single<AppInfoProvider> { AndroidAppInfoProvider(get()) }
}

val viewModelModule = module {
    viewModel { ArticlesViewModel(get(), get()) }
    viewModel { ArticleDetailViewModel(get(), get(), get()) }
    viewModel { DiaryViewModel(get()) }
    viewModel { BooksViewModel(get(), get()) }
    viewModel { AiChatViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
    // ... one per page
}
```

## Migration Phases

### Phase 1: Project Scaffold & Foundation
- Create KMP project with shared + app modules
- Configure Gradle: SQLDelight, Ktor, Koin, Compose Navigation, Coil
- Implement theme system (colors, typography, spacing, shapes)
- Set up navigation skeleton (all routes, placeholder screens)
- Set up Koin DI modules

### Phase 2: Data Layer
- Define all SQLDelight schemas (.sq files)
- Generate Kotlin data classes from SQLDelight
- Implement all Repository classes
- Implement SettingService, I18nService

### Phase 3: Core Pages (Articles + Diary + Books)
- ArticlesScreen: list, search, filter, calendar, favorites
- ArticleDetailScreen: summary/original tabs, markdown, images, tags
- DiaryScreen: list, search, filter, calendar
- DiaryEditorSheet: markdown editing, image picker, mood, tags
- BooksScreen: viewpoint carousel, book filter
- BookSearchScreen: search results

### Phase 4: AI Features
- AiService: OpenAI-compatible API via Ktor
- WebView Content Extractor: Android WebView + Readability.js
- WebpageParserService pipeline
- ImageProcessor
- ADBlockService

### Phase 5: AI UI Pages
- AiChatScreen: chat interface, message bubbles, search results
- AiConfigScreen: config management
- AiConfigEditScreen: config form
- ShareDialogScreen: URL save dialog

### Phase 6: Settings & Auxiliary
- SettingsScreen
- BackupRestoreScreen + BackupSettingsScreen
- WeeklySummaryScreen
- PluginCenterScreen

### Phase 7: Platform-Specific Features
- WebServerService (Ktor Server with REST API + auth)
- ClipboardMonitorService
- ShareReceiveActivity
- AppUpgradeService
- ArticleRecoveryService

### Phase 8: Polish & Testing
- Full theme parity verification (light/dark)
- All pages UI review vs original
- Integration testing
- Performance optimization
- Data migration tool (optional: import ObjectBox data into SQLDelight)

## Key Technical Considerations

### WebView Content Extraction
The headless WebView approach requires Android-specific implementation:
- Use `android.webkit.WebView` in headless mode
- Inject JavaScript (`Readability.js`, `parse_content.js`) after page load
- Extract parsed content via `evaluateJavascript` + callback
- Handle timeout and error cases

### Backup Compatibility
New backup format should be documented. Old ObjectBox backups cannot be directly imported. Consider writing a migration tool that:
1. Reads the old ZIP structure
2. Parses ObjectBox data (may need Flutter CLI tool)
3. Generates SQLDelight-compatible data

### Local Web Server
Replace shelf with Ktor Server embedded:
```kotlin
embeddedServer(Netty, port = config.port) {
    install(Authentication)
    install(ContentNegotiation)
    routing {
        route("/api/articles") { ... }
        route("/api/books") { ... }
        route("/api/diaries") { ... }
        route("/api/stats") { ... }
    }
}.start()
```

### Markdown Rendering
For CMP markdown rendering, use `compose-multiplatform-markdown` library or `mikepenz/multiplatform-markdown-renderer`. Both support basic Markdown with Compose. For advanced rendering (tables, code blocks), may need custom composables.

### Image Handling
- Coil for network image loading and caching
- `AsyncImage` composable replaces `CachedNetworkImage`
- Local images loaded via `Image(bitmap = ImageBitmap(...))`
- Pinch-zoom gallery via custom `Modifier.pointerInput` + `TransformableState`

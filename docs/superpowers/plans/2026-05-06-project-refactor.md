# Daily Satori Project Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor Daily Satori in small behavior-preserving phases so UI files, settings directories, ViewModel boundaries, and shared services are easier to understand without changing features or major UI layout.

**Architecture:** Keep existing KMP layering: Android UI/ViewModels in `app/`, shared repositories and services in `shared/`. Prefer extraction and package moves over rewrites; keep public routes, database schema, service APIs, and user-visible behavior stable. Each phase must compile before moving to the next.

**Tech Stack:** Kotlin Multiplatform, Jetpack Compose Material 3, Koin, ViewModel + StateFlow, SQLDelight, Gradle Android build.

---

## File Structure Map

### Existing Files To Modify

- `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt`: Keep as the book feature orchestration screen; remove extracted private UI helpers.
- `app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt`: Update imports after settings files move; optionally use `SettingsSectionCard` if repeated card blocks are localized.
- `app/src/main/kotlin/com/dailysatori/core/navigation/NavHost.kt`: Update package imports after settings file moves only.
- `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`: Update imports and register any new ViewModels created during boundary cleanup.
- `app/src/main/kotlin/com/dailysatori/ui/feature/settings/McpServerScreen.kt`: Later phase; move repository access and IO state to a ViewModel.
- `app/src/main/kotlin/com/dailysatori/ui/feature/settings/DataImportScreen.kt`: Later phase; move IO/state to a ViewModel if direct service/repository access exists.
- `app/src/main/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigEditScreen.kt`: Later phase; move direct repository/service access to existing or new ViewModel if present.
- `shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt`: Later phase; extract pure helpers and prompt constants.
- `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentService.kt`: Later phase; extract prompt/result helpers without changing public API.
- `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpToolRegistry.kt`: Later phase; split domain tool builders while preserving tool names and schemas.

### New Files To Create

- `app/src/main/kotlin/com/dailysatori/ui/feature/book/component/BookPickerComponents.kt`: Book picker row, swipe row, and related shape helpers extracted from `BooksScreen.kt`.
- `app/src/main/kotlin/com/dailysatori/ui/feature/book/component/BookSearchSheets.kt`: Add/search sheet UI, search result card, content search sheet, and search status component extracted from `BooksScreen.kt`.
- `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookReadingLocation.kt`: Small state helper for remembering reading position.
- `app/src/main/kotlin/com/dailysatori/ui/feature/settings/backup/BackupSettingsScreen.kt`: Moved from flat settings package.
- `app/src/main/kotlin/com/dailysatori/ui/feature/settings/backup/BackupSettingsViewModel.kt`: Moved from flat settings package.
- `app/src/main/kotlin/com/dailysatori/ui/feature/settings/backup/BackupRestoreScreen.kt`: Moved from flat settings package.
- `app/src/main/kotlin/com/dailysatori/ui/feature/settings/backup/BackupRestoreViewModel.kt`: Moved from flat settings package.
- `app/src/main/kotlin/com/dailysatori/ui/feature/settings/importing/DataImportScreen.kt`: Moved from flat settings package.
- `app/src/main/kotlin/com/dailysatori/ui/feature/settings/mcp/McpServerScreen.kt`: Moved from flat settings package.
- `app/src/main/kotlin/com/dailysatori/ui/feature/settings/plugin/PluginCenterScreen.kt`: Moved from flat settings package.
- `app/src/main/kotlin/com/dailysatori/ui/feature/settings/plugin/PluginCenterViewModel.kt`: Moved from flat settings package.
- `app/src/main/kotlin/com/dailysatori/ui/feature/settings/weekly/WeeklySummaryScreen.kt`: Moved from flat settings package.
- `app/src/main/kotlin/com/dailysatori/ui/feature/settings/weekly/WeeklySummaryViewModel.kt`: Moved from flat settings package.
- `app/src/main/kotlin/com/dailysatori/ui/component/settings/SettingsSectionCard.kt`: Shared settings section wrapper only if it removes repeated identical card patterns.
- `shared/src/commonMain/kotlin/com/dailysatori/service/parser/ArticleImageHelpers.kt`: Pure image URL and HTML helper extraction.
- `shared/src/commonMain/kotlin/com/dailysatori/service/parser/TwitterContentParser.kt`: Twitter JSON and HTML helper extraction.
- `shared/src/commonMain/kotlin/com/dailysatori/service/parser/ArticleParserPrompts.kt`: Exact prompt/string constants extracted from parser service.
- `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentPrompts.kt`: Exact MCP prompt constants and builders extracted from agent service.
- `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpToolResultFormatter.kt`: Deterministic formatting helpers extracted from MCP agent/tool code.

---

## Task 0: Baseline Verification

**Files:**
- Read only: repository root

- [ ] **Step 1: Check working tree**

Run: `git status --short --branch`

Expected: current branch is `main`; uncommitted files may include only planning docs unless the user has made unrelated changes.

- [ ] **Step 2: Run baseline compile**

Run: `./gradlew :app:compileDebugKotlin`

Expected: build exits with code 0 before refactoring starts.

- [ ] **Step 3: Record baseline if compile fails**

If compile fails before edits, stop and report the failure. Do not refactor on top of a broken baseline.

---

## Task 1: Split Book Feature Pure UI Components

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookReadingLocation.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/book/component/BookPickerComponents.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/book/component/BookSearchSheets.kt`

- [ ] **Step 1: Create book component directory**

Run: `mkdir -p app/src/main/kotlin/com/dailysatori/ui/feature/book/component`

Expected: command exits with code 0.

- [ ] **Step 2: Extract reading-location helper**

Move the existing declarations from `BooksScreen.kt` into `BookReadingLocation.kt` without changing bodies:

```kotlin
package com.dailysatori.ui.feature.book

data class BookReadingLocation(val bookId: Long, val page: Int)

fun rememberReadingLocation(currentBookId: Long?, currentPage: Int): BookReadingLocation? =
    currentBookId?.let { BookReadingLocation(it, currentPage) }
```

Then delete the same declarations from `BooksScreen.kt`.

- [ ] **Step 3: Extract book picker components**

Create `BookPickerComponents.kt` with package `com.dailysatori.ui.feature.book.component`. Move these existing declarations from `BooksScreen.kt` without changing UI behavior:

```text
BookPickerSwipeRow
BookPickerRow
bookPickerRowShape
bookPickerDeleteActionShape
```

Make moved declarations `internal` instead of `private` when they are called from `BooksScreen.kt` or another extracted file. Import app theme constants with `import com.dailysatori.ui.theme.*`. Delete the moved declarations from `BooksScreen.kt`.

- [ ] **Step 4: Extract book search sheets**

Create `BookSearchSheets.kt` with package `com.dailysatori.ui.feature.book.component`. Move these existing declarations from `BooksScreen.kt` without changing bodies except visibility/imports:

```text
BookAddSearchSheet
BookSearchResultCard
BookContentSearchSheet
SearchSheetStatus
```

Make moved declarations `internal`. Delete the moved declarations from `BooksScreen.kt`.

- [ ] **Step 5: Update imports in BooksScreen**

Add imports for extracted components:

```kotlin
import com.dailysatori.ui.feature.book.component.BookAddSearchSheet
import com.dailysatori.ui.feature.book.component.BookContentSearchSheet
import com.dailysatori.ui.feature.book.component.BookPickerSwipeRow
```

Remove imports that are no longer used by `BooksScreen.kt`. Do not change route logic, ViewModel calls, or state variables.

- [ ] **Step 6: Compile after book extraction**

Run: `./gradlew :app:compileDebugKotlin`

Expected: build exits with code 0.

- [ ] **Step 7: Optional checkpoint commit**

Only commit if the user explicitly asks for commits:

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/book
git commit -m "refactor: split book screen components"
```

---

## Task 2: Reorganize Settings Feature Directories

**Files:**
- Move: settings backup/import/MCP/plugin/weekly screens and ViewModels into domain folders
- Modify: `app/src/main/kotlin/com/dailysatori/core/navigation/NavHost.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`

- [ ] **Step 1: Create settings domain directories**

Run:

```bash
mkdir -p app/src/main/kotlin/com/dailysatori/ui/feature/settings/backup app/src/main/kotlin/com/dailysatori/ui/feature/settings/importing app/src/main/kotlin/com/dailysatori/ui/feature/settings/mcp app/src/main/kotlin/com/dailysatori/ui/feature/settings/plugin app/src/main/kotlin/com/dailysatori/ui/feature/settings/weekly
```

Expected: command exits with code 0.

- [ ] **Step 2: Move backup files**

Run:

```bash
mv app/src/main/kotlin/com/dailysatori/ui/feature/settings/BackupSettingsScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/settings/backup/BackupSettingsScreen.kt
mv app/src/main/kotlin/com/dailysatori/ui/feature/settings/BackupSettingsViewModel.kt app/src/main/kotlin/com/dailysatori/ui/feature/settings/backup/BackupSettingsViewModel.kt
mv app/src/main/kotlin/com/dailysatori/ui/feature/settings/BackupRestoreScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/settings/backup/BackupRestoreScreen.kt
mv app/src/main/kotlin/com/dailysatori/ui/feature/settings/BackupRestoreViewModel.kt app/src/main/kotlin/com/dailysatori/ui/feature/settings/backup/BackupRestoreViewModel.kt
```

Update each moved file package declaration from:

```kotlin
package com.dailysatori.ui.feature.settings
```

to:

```kotlin
package com.dailysatori.ui.feature.settings.backup
```

- [ ] **Step 3: Move import screen**

Run:

```bash
mv app/src/main/kotlin/com/dailysatori/ui/feature/settings/DataImportScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/settings/importing/DataImportScreen.kt
```

Update package declaration to:

```kotlin
package com.dailysatori.ui.feature.settings.importing
```

- [ ] **Step 4: Move MCP screen**

Run:

```bash
mv app/src/main/kotlin/com/dailysatori/ui/feature/settings/McpServerScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/settings/mcp/McpServerScreen.kt
```

Update package declaration to:

```kotlin
package com.dailysatori.ui.feature.settings.mcp
```

- [ ] **Step 5: Move plugin files**

Run:

```bash
mv app/src/main/kotlin/com/dailysatori/ui/feature/settings/PluginCenterScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/settings/plugin/PluginCenterScreen.kt
mv app/src/main/kotlin/com/dailysatori/ui/feature/settings/PluginCenterViewModel.kt app/src/main/kotlin/com/dailysatori/ui/feature/settings/plugin/PluginCenterViewModel.kt
```

Update each moved file package declaration to:

```kotlin
package com.dailysatori.ui.feature.settings.plugin
```

- [ ] **Step 6: Move weekly summary files**

Run:

```bash
mv app/src/main/kotlin/com/dailysatori/ui/feature/settings/WeeklySummaryScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/settings/weekly/WeeklySummaryScreen.kt
mv app/src/main/kotlin/com/dailysatori/ui/feature/settings/WeeklySummaryViewModel.kt app/src/main/kotlin/com/dailysatori/ui/feature/settings/weekly/WeeklySummaryViewModel.kt
```

Update each moved file package declaration to:

```kotlin
package com.dailysatori.ui.feature.settings.weekly
```

- [ ] **Step 7: Update imports after package moves**

In `NavHost.kt` and `ViewModelModule.kt`, replace imports from `com.dailysatori.ui.feature.settings.*` with exact new package imports for moved screens/ViewModels. Keep `SettingsScreen` and `SettingsViewModel` in `com.dailysatori.ui.feature.settings`.

- [ ] **Step 8: Compile after settings moves**

Run: `./gradlew :app:compileDebugKotlin`

Expected: build exits with code 0.

- [ ] **Step 9: Optional checkpoint commit**

Only commit if the user explicitly asks for commits:

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/settings app/src/main/kotlin/com/dailysatori/core/navigation/NavHost.kt app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt
git commit -m "refactor: organize settings feature by domain"
```

---

## Task 3: Introduce Minimal Settings Section Component

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/component/settings/SettingsSectionCard.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt`

- [ ] **Step 1: Create `SettingsSectionCard`**

Add this component using existing style constants:

```kotlin
package com.dailysatori.ui.component.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.dailysatori.ui.theme.*
import androidx.compose.material3.Text

@Composable
fun SettingsSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(defaultElevation = Elevation.none),
        ) {
            Column(content = content)
        }
    }
}
```

- [ ] **Step 2: Replace only identical local section patterns**

In `SettingsScreen.kt`, replace repeated section title + card + rows blocks with `SettingsSectionCard`. Do not change row text, click handlers, icons, route names, or spacing outside the section wrapper.

- [ ] **Step 3: Compile after component extraction**

Run: `./gradlew :app:compileDebugKotlin`

Expected: build exits with code 0.

---

## Task 4: Move MCP Server Screen IO Into ViewModel

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/mcp/McpServerViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/mcp/McpServerScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`

- [ ] **Step 1: Create ViewModel state shell**

Create `McpServerViewModel.kt` with package `com.dailysatori.ui.feature.settings.mcp`. Start with state fields matching the screen's existing mutable state values. Use this structure and add only fields that currently exist in `McpServerScreen.kt`:

```kotlin
package com.dailysatori.ui.feature.settings.mcp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.McpServerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class McpServerUiState(
    val servers: List<Mcp_server> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
)

class McpServerViewModel(
    private val repository: McpServerRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(McpServerUiState())
    val state: StateFlow<McpServerUiState> = _state.asStateFlow()

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun observeServers() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.getAll().collect { servers ->
                _state.update { it.copy(servers = servers) }
            }
        }
    }

    fun toggleServerEnabled(server: Mcp_server, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.update(server.id, server.name, server.server_url, server.api_key, if (enabled) 1L else 0L)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }
}
```

Add this import to the file:

```kotlin
import com.dailysatori.shared.db.Mcp_server
```

- [ ] **Step 2: Register the ViewModel**

In `ViewModelModule.kt`, add:

```kotlin
viewModel { McpServerViewModel(get()) }
```

Import `com.dailysatori.ui.feature.settings.mcp.McpServerViewModel`.

- [ ] **Step 3: Move list observation and enable toggle**

In `McpServerScreen.kt`, replace `remember { KoinPlatform.getKoin().get<McpServerRepository>() }`, local `servers`, and `LaunchedEffect(Unit) { repo.getAll().collect ... }` with `koinViewModel<McpServerViewModel>()`, `state.collectAsState()`, and `LaunchedEffect(Unit) { viewModel.observeServers() }`. Replace the switch update body with:

```kotlin
onCheckedChange = { enabled ->
    viewModel.toggleServerEnabled(server, enabled)
}
```

- [ ] **Step 4: Add preset batch save method**

Move `saveSelectedMcpTemplates` from `McpServerScreen.kt` into `McpServerViewModel` as:

```kotlin
fun saveSelectedTemplates(
    provider: McpProvider,
    templates: List<McpTemplate>,
    apiKey: String,
    onResult: (McpBatchSaveResult) -> Unit,
) {
    viewModelScope.launch(Dispatchers.IO) {
        _state.update { it.copy(isSaving = true, error = null) }
        try {
            var added = 0
            var skipped = 0
            templates.forEach { template ->
                if (repository.getByServerUrl(template.serverUrl) != null) {
                    skipped += 1
                } else {
                    repository.insertPreset(
                        name = mcpTemplateDisplayName(provider, template),
                        serverUrl = template.serverUrl,
                        apiKey = apiKey,
                        provider = provider.id,
                        templateId = template.id,
                        templateType = template.type.name.lowercase(),
                        configJson = renderMcpConfigJson(template),
                    )
                    added += 1
                }
            }
            onResult(McpBatchSaveResult(added = added, skipped = skipped))
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message) }
        } finally {
            _state.update { it.copy(isSaving = false) }
        }
    }
}
```

Move the required imports from `McpServerScreen.kt` to the ViewModel file: `McpProvider`, `McpTemplate`, `mcpTemplateDisplayName`, and `renderMcpConfigJson`.

- [ ] **Step 5: Add edit load/save methods**

Add methods to `McpServerViewModel` for edit mode:

```kotlin
fun loadServer(serverId: Long, onLoaded: (Mcp_server) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        repository.getById(serverId)?.let(onLoaded)
    }
}

fun saveServer(
    serverId: Long?,
    name: String,
    serverUrl: String,
    apiKey: String,
    enabled: Boolean,
    onSaved: () -> Unit,
) {
    viewModelScope.launch(Dispatchers.IO) {
        _state.update { it.copy(isSaving = true, error = null) }
        try {
            if (serverId != null && serverId > 0) {
                repository.update(serverId, name, serverUrl, apiKey, if (enabled) 1L else 0L)
            } else {
                repository.insert(name, serverUrl, apiKey, if (enabled) 1L else 0L)
            }
            onSaved()
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message) }
        } finally {
            _state.update { it.copy(isSaving = false) }
        }
    }
}
```

Update `McpServerEditScreen` to call `viewModel.loadServer(...)` in `LaunchedEffect(serverId)` and `viewModel.saveServer(...)` from the save button. Keep the existing local text field states in the screen.

- [ ] **Step 6: Compile after MCP ViewModel boundary cleanup**

Run: `./gradlew :app:compileDebugKotlin`

Expected: build exits with code 0.

---

## Task 5: Extract Webpage Parser Pure Helpers

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/parser/ArticleImageHelpers.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/parser/TwitterContentParser.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/parser/ArticleParserPrompts.kt`

- [ ] **Step 1: Extract image helper declarations exactly**

Move these declarations from `WebpageParserService.kt` to `ArticleImageHelpers.kt` and change visibility from `private` to `internal` only when needed by `WebpageParserService`:

```text
extractOgImageUrl
extractLargestContentImgSrc
extractFirstImgSrc
imageCandidate
attrValue
String.shouldSkipImage
String.hasSkippedKeyword
String.toAbsoluteUrl
String.origin
ImageCandidate
```

Keep function bodies unchanged.

- [ ] **Step 2: Extract Twitter helper declarations exactly**

Move these declarations from `WebpageParserService.kt` to `TwitterContentParser.kt` and change visibility from `private` to `internal` only when needed:

```text
tweetImageUrls
buildTweetHtml
JsonObject.stringValue
JsonObject.longValue
escapeHtml
String.normalizeTwitterImageUrl
```

Keep function bodies unchanged.

- [ ] **Step 3: Extract prompt constants without text changes**

Move long prompt strings from `WebpageParserService.kt` to `ArticleParserPrompts.kt` as `internal` constants or `internal` functions. The generated prompt text must be byte-for-byte equivalent for the same inputs.

- [ ] **Step 4: Compile after parser helper extraction**

Run: `./gradlew :app:compileDebugKotlin`

Expected: build exits with code 0.

---

## Task 6: Extract MCP Agent Helpers

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentService.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpToolRegistry.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentPrompts.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpToolResultFormatter.kt`

- [ ] **Step 1: Extract prompt builders without text changes**

Move prompt-building functions and constants from `McpAgentService.kt` to `McpAgentPrompts.kt`. Keep output strings equivalent for identical inputs.

- [ ] **Step 2: Extract deterministic result formatting**

Move formatting helpers that do not call repositories, network, AI, or database into `McpToolResultFormatter.kt`. Keep function bodies equivalent except visibility/imports.

- [ ] **Step 3: Leave registry dispatch intact**

Do not split `McpToolRegistry.kt` dispatch until prompt and formatting extraction compiles. Tool names, argument names, JSON structure, and returned text must remain unchanged.

- [ ] **Step 4: Compile after MCP helper extraction**

Run: `./gradlew :app:compileDebugKotlin`

Expected: build exits with code 0.

---

## Task 7: Final Verification And Manual Smoke Checks

**Files:**
- Read only: repository root

- [ ] **Step 1: Compile final state**

Run: `./gradlew :app:compileDebugKotlin`

Expected: build exits with code 0.

- [ ] **Step 2: Install debug build if Android device is connected**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`

Expected: build and install exit with code 0. If no device is connected, report the exact failure and do not claim device verification.

- [ ] **Step 3: Launch app if install succeeds**

Run: `adb shell am start -n com.dailysatori/.MainActivity`

Expected: command exits with code 0.

- [ ] **Step 4: Manual smoke checklist**

Verify touched areas only:

```text
Book feature: list opens, picker opens, search sheet opens, add/search/delete actions are visible.
Settings feature: settings home opens, backup/import/MCP/plugin/weekly pages navigate.
Parser/MCP: no compile-time API changes; if configured, run one article parse and one AI chat query.
```

- [ ] **Step 5: Report final diff summary**

Run: `git status --short --branch` and `git diff --stat`.

Expected: report changed files grouped by phase. Do not commit unless the user explicitly asks.

---

## Self-Review Notes

- Spec coverage: This plan covers phased UI splitting, settings organization, reusable UI components, ViewModel boundary cleanup, shared service helper extraction, and verification.
- Scope control: Each task compiles before the next starts and preserves routes, schema, public service APIs, and major UI layout.
- No commits are performed automatically because repository instructions require explicit user approval for commits.

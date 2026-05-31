# Project Refactor Phase 1 DI Boundaries Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove direct Koin dependency lookups from the Phase 1 UI/ViewModel boundary while preserving existing AI config edit, memory search, and settings behavior.

**Architecture:** Composables render state and forward user events to ViewModels. ViewModels receive repositories and services through Koin constructor injection. This phase avoids shared-service rewrites and keeps all visible UI and behavior unchanged.

**Tech Stack:** Kotlin, Android Jetpack Compose, ViewModel, StateFlow, Koin, Gradle, kotlin.test source-structure tests.

---

## Scope

This plan implements only Phase 1 from `docs/superpowers/specs/2026-05-31-project-refactor-phased-design.md`.

Do not create a git worktree. Project instructions explicitly prohibit git worktrees.

Do not deploy to an emulator. Install only to a connected phone during final verification.

Do not commit unless the user explicitly asks for commits. The implementation checkpoints below use `git diff` review instead of commits.

## File Structure

### Create

- `app/src/main/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigEditViewModel.kt`
  - Owns AI config editor state, initial config loading, provider/model selection, connection testing, and save operations.
- `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MemorySearchViewModel.kt`
  - Owns memory sheet query state, memory list loading, rebuild progress, and rebuild orchestration.
- `app/src/test/kotlin/com/dailysatori/core/di/DependencyBoundaryTest.kt`
  - Guards Phase 1 boundaries with source-structure assertions.

### Modify

- `app/src/main/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigEditScreen.kt`
  - Remove `KoinPlatform.getKoin()` calls and coroutine-owned repository/service work.
  - Use `koinViewModel()` and `collectAsState()`.
- `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MemorySearchSheet.kt`
  - Remove `koinInject` repository/service calls and coroutine-owned business work.
  - Use `koinViewModel()` and `collectAsState()`.
- `app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsViewModel.kt`
  - Inject `SettingRepository` through constructor.
  - Remove `KoinJavaComponent.get` usage.
- `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`
  - Register `AiConfigEditViewModel` and `MemorySearchViewModel`.
  - Pass `SettingRepository` into `SettingsViewModel`.
  - Prefer named arguments for touched registrations.
- `app/src/test/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigLayoutTest.kt`
  - Update the snapshot-state assertions to reflect ViewModel ownership instead of local screen snapshots.

---

### Task 1: Add Dependency Boundary Regression Tests

**Files:**
- Create: `app/src/test/kotlin/com/dailysatori/core/di/DependencyBoundaryTest.kt`
- Modify: none
- Test: `app/src/test/kotlin/com/dailysatori/core/di/DependencyBoundaryTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/dailysatori/core/di/DependencyBoundaryTest.kt`:

```kotlin
package com.dailysatori.core.di

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DependencyBoundaryTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun aiConfigEditorDoesNotPullDependenciesFromKoinInsideComposable() {
        val source = source("src/main/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigEditScreen.kt")

        assertFalse(source.contains("KoinPlatform.getKoin()"))
        assertFalse(source.contains("AIConfigRepository"))
        assertFalse(source.contains("AiService"))
        assertTrue(source.contains("AiConfigEditViewModel"))
        assertTrue(source.contains("koinViewModel"))
    }

    @Test
    fun memorySearchSheetDoesNotInjectRepositoriesInsideComposable() {
        val source = source("src/main/kotlin/com/dailysatori/ui/feature/aichat/MemorySearchSheet.kt")

        assertFalse(source.contains("koinInject"))
        assertFalse(source.contains("MemoryRepository"))
        assertFalse(source.contains("MemoryExtractService"))
        assertFalse(source.contains("ArticleRepository"))
        assertFalse(source.contains("DiaryRepository"))
        assertFalse(source.contains("BookRepository"))
        assertFalse(source.contains("BookViewpointRepository"))
        assertTrue(source.contains("MemorySearchViewModel"))
        assertTrue(source.contains("koinViewModel"))
    }

    @Test
    fun settingsViewModelUsesConstructorInjectedSettingRepository() {
        val source = source("src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsViewModel.kt")

        assertFalse(source.contains("KoinJavaComponent"))
        assertFalse(source.contains("get<SettingRepository>"))
        assertTrue(source.contains("private val settingRepo: SettingRepository"))
    }

    @Test
    fun viewModelModuleRegistersPhaseOneViewModels() {
        val source = source("src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt")

        assertTrue(source.contains("AiConfigEditViewModel("))
        assertTrue(source.contains("MemorySearchViewModel("))
        assertTrue(source.contains("settingRepo = get<SettingRepository>()"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.core.di.DependencyBoundaryTest
```

Expected: FAIL. The current code still contains `KoinPlatform.getKoin()`, `koinInject`, internal `get<SettingRepository>`, and missing Phase 1 ViewModel registrations.

---

### Task 2: Constructor-Inject SettingRepository Into SettingsViewModel

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`
- Test: `app/src/test/kotlin/com/dailysatori/core/di/DependencyBoundaryTest.kt`

- [ ] **Step 1: Update `SettingsViewModel` constructor and remove Koin lookup import**

In `SettingsViewModel.kt`, replace the class constructor and remove `import org.koin.java.KoinJavaComponent.get`.

```kotlin
class SettingsViewModel(
    private val webServerService: WebServerService,
    private val appUpgradeService: AppUpgradeService,
    private val settingRepo: SettingRepository,
) : ViewModel() {
```

- [ ] **Step 2: Replace internal SettingRepository lookups**

In `SettingsViewModel.kt`, replace `loadWebServiceInfo()` with:

```kotlin
private fun loadWebServiceInfo() {
    viewModelScope.launch(Dispatchers.IO) {
        val token = settingRepo.get("web_server_token") ?: ""
        val port = webServerService.getPort()
        val address = if (webServerService.isRunning() && port > 0) {
            getDeviceIp()?.let { "http://$it:$port" } ?: "http://localhost:$port"
        } else ""
        _state.update { it.copy(webServerToken = token, webServerAddress = address) }
    }
}
```

Replace `refreshToken()` with:

```kotlin
fun refreshToken() {
    viewModelScope.launch(Dispatchers.IO) {
        val newToken = generateToken()
        settingRepo.upsert("web_server_token", newToken)
        _state.update { it.copy(webServerToken = newToken) }
    }
}
```

Replace `ensureToken()` with:

```kotlin
private fun ensureToken() {
    val existing = settingRepo.get("web_server_token")
    if (existing == null) {
        val newToken = generateToken()
        settingRepo.upsert("web_server_token", newToken)
        _state.update { it.copy(webServerToken = newToken) }
    }
}
```

- [ ] **Step 3: Update Koin registration**

In `ViewModelModule.kt`, update the existing `SettingsViewModel` registration:

```kotlin
viewModel {
    SettingsViewModel(
        webServerService = get<WebServerService>(),
        appUpgradeService = get<AppUpgradeService>(),
        settingRepo = get<SettingRepository>(),
    )
}
```

- [ ] **Step 4: Run targeted boundary test**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.core.di.DependencyBoundaryTest.settingsViewModelUsesConstructorInjectedSettingRepository
```

Expected: PASS.

---

### Task 3: Move AI Config Editor Operations Into ViewModel

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigEditViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigEditScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigLayoutTest.kt`
- Test: `app/src/test/kotlin/com/dailysatori/core/di/DependencyBoundaryTest.kt`

- [ ] **Step 1: Create `AiConfigEditViewModel.kt`**

Create `app/src/main/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigEditViewModel.kt`:

```kotlin
package com.dailysatori.ui.feature.aiconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.config.AiModel
import com.dailysatori.config.AiProvider
import com.dailysatori.config.findProvider
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.service.ai.AiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AiConfigEditState(
    val selectedProvider: AiProvider? = null,
    val selectedModel: AiModel? = null,
    val apiToken: String = "",
    val customModelName: String = "",
    val isDefault: Boolean = false,
    val wasDefault: Boolean = false,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val testResult: String? = null,
    val testSuccess: Boolean? = null,
)

class AiConfigEditViewModel(
    private val repo: AIConfigRepository,
    private val aiService: AiService,
) : ViewModel() {
    private val _state = MutableStateFlow(AiConfigEditState())
    val state: StateFlow<AiConfigEditState> = _state.asStateFlow()

    fun load(configId: Long?) {
        if (configId == null) return
        viewModelScope.launch(Dispatchers.IO) {
            val config = repo.getById(configId) ?: return@launch
            val provider = findProvider(config.provider)
            val model = provider?.models?.find { it.id == config.model_name }
            _state.update {
                it.copy(
                    apiToken = config.api_token,
                    isDefault = config.is_default == 1L,
                    wasDefault = config.is_default == 1L,
                    selectedProvider = provider,
                    selectedModel = model,
                    customModelName = if (model == null) config.model_name else "",
                )
            }
        }
    }

    fun selectProvider(provider: AiProvider) {
        _state.update {
            it.copy(
                selectedProvider = provider,
                selectedModel = null,
                customModelName = "",
                testResult = null,
                testSuccess = null,
            )
        }
    }

    fun selectModel(model: AiModel) {
        _state.update { it.copy(selectedModel = model, customModelName = "") }
    }

    fun updateApiToken(value: String) {
        _state.update { it.copy(apiToken = value) }
    }

    fun updateCustomModelName(value: String) {
        _state.update { it.copy(customModelName = value) }
    }

    fun updateIsDefault(value: Boolean) {
        _state.update { it.copy(isDefault = value) }
    }

    fun testConnection() {
        val snapshot = _state.value
        val provider = snapshot.selectedProvider ?: return
        val modelId = currentModelId(snapshot.selectedProvider.models.isEmpty(), snapshot.customModelName, snapshot.selectedModel) ?: return
        val token = snapshot.apiToken
        viewModelScope.launch {
            _state.update { it.copy(isTesting = true, testResult = null, testSuccess = null) }
            val result = withContext(Dispatchers.IO) {
                aiService.testConnection(
                    apiAddress = provider.apiHost,
                    apiToken = token,
                    modelName = modelId,
                    provider = provider.id,
                )
            }
            _state.update {
                it.copy(
                    testSuccess = result.isSuccess,
                    testResult = result.fold(
                        onSuccess = { message -> "连接成功：${message.take(80)}" },
                        onFailure = { error -> error.message ?: "连接失败" },
                    ),
                    isTesting = false,
                )
            }
        }
    }

    fun save(configId: Long?, onSaved: () -> Unit) {
        val snapshot = _state.value
        val provider = snapshot.selectedProvider ?: return
        val modelId = currentModelId(provider.models.isEmpty(), snapshot.customModelName, snapshot.selectedModel) ?: return
        val token = snapshot.apiToken
        val defaultValue = snapshot.isDefault
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                withContext(Dispatchers.IO) {
                    if (configId != null) {
                        repo.update(configId, provider.id, provider.apiHost, token, modelId, if (defaultValue) 1L else 0L)
                    } else {
                        repo.insert(provider.id, provider.apiHost, token, modelId, if (defaultValue) 1L else 0L)
                    }
                }
                onSaved()
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }
}

private fun currentModelId(
    isCustomModel: Boolean,
    customModelName: String,
    selectedModel: AiModel?,
): String? {
    return when {
        isCustomModel && customModelName.isNotBlank() -> customModelName.trim()
        selectedModel != null -> selectedModel.id
        else -> null
    }
}
```

- [ ] **Step 2: Register `AiConfigEditViewModel`**

In `ViewModelModule.kt`, add import:

```kotlin
import com.dailysatori.ui.feature.aiconfig.AiConfigEditViewModel
```

Add registration after `AiConfigViewModel`:

```kotlin
viewModel {
    AiConfigEditViewModel(
        repo = get<AIConfigRepository>(),
        aiService = get(),
    )
}
```

- [ ] **Step 3: Update `AiConfigEditScreen` dependencies and state**

In `AiConfigEditScreen.kt`, remove these imports:

```kotlin
import androidx.compose.runtime.rememberCoroutineScope
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.service.ai.AiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform
```

Add imports:

```kotlin
import androidx.compose.runtime.collectAsState
import org.koin.androidx.compose.koinViewModel
```

Replace local dependency and state setup in `AiConfigEditScreen` with:

```kotlin
val viewModel: AiConfigEditViewModel = koinViewModel()
val state by viewModel.state.collectAsState()

var providerExpanded by remember { mutableStateOf(false) }
var modelExpanded by remember { mutableStateOf(false) }

val selectedProvider = state.selectedProvider
val selectedModel = state.selectedModel
val apiToken = state.apiToken
val customModelName = state.customModelName
val isDefault = state.isDefault
val isSaving = state.isSaving
val isTesting = state.isTesting
val testResult = state.testResult
val testSuccess = state.testSuccess
val models = selectedProvider?.models ?: emptyList()
val isCustomModel = selectedProvider != null && models.isEmpty()
val currentModel = currentModelId(isCustomModel, customModelName, selectedModel)
val canTest = selectedProvider != null && apiToken.isNotBlank() && currentModel != null
val canSave = selectedProvider != null && apiToken.isNotBlank() && currentModel != null
```

Replace `LaunchedEffect(configId)` with:

```kotlin
LaunchedEffect(configId) {
    viewModel.load(configId)
}
```

- [ ] **Step 4: Update `AiConfigEditScreen` event handlers**

In `SettingsEditorBottomBar`, replace `onTest` and `onSave` lambdas with:

```kotlin
onTest = viewModel::testConnection,
onSave = { viewModel.save(configId, onBack) },
```

Replace provider selection handler with:

```kotlin
onClick = {
    viewModel.selectProvider(provider)
    providerExpanded = false
}
```

Replace API token field handler with:

```kotlin
onValueChange = viewModel::updateApiToken,
```

Replace custom model field handler with:

```kotlin
onValueChange = viewModel::updateCustomModelName,
```

Replace model selection handler with:

```kotlin
onClick = {
    viewModel.selectModel(model)
    modelExpanded = false
}
```

Replace default switch handler with:

```kotlin
onCheckedChange = viewModel::updateIsDefault,
```

- [ ] **Step 5: Update existing AI config source test**

In `AiConfigLayoutTest.kt`, replace `editorSnapshotsFormStateBeforeIoWork()` with:

```kotlin
@Test
fun editorDelegatesStatefulIoWorkToViewModel() {
    val screenSource = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigEditScreen.kt").readText()
    val viewModelSource = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigEditViewModel.kt").readText()

    assertEquals(true, screenSource.contains("AiConfigEditViewModel"))
    assertEquals(false, screenSource.contains("KoinPlatform.getKoin()"))
    assertEquals(true, viewModelSource.contains("val token = snapshot.apiToken"))
    assertEquals(true, viewModelSource.contains("val defaultValue = snapshot.isDefault"))
    assertEquals(false, screenSource.contains("val token = apiToken"))
}
```

- [ ] **Step 6: Run targeted tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.core.di.DependencyBoundaryTest.aiConfigEditorDoesNotPullDependenciesFromKoinInsideComposable --tests com.dailysatori.ui.feature.aiconfig.AiConfigLayoutTest
```

Expected: PASS.

---

### Task 4: Move Memory Search Operations Into ViewModel

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MemorySearchViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MemorySearchSheet.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`
- Test: `app/src/test/kotlin/com/dailysatori/core/di/DependencyBoundaryTest.kt`

- [ ] **Step 1: Create `MemorySearchViewModel.kt`**

Create `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MemorySearchViewModel.kt`:

```kotlin
package com.dailysatori.ui.feature.aichat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.MemoryRepository
import com.dailysatori.service.memory.MemoryExtractService
import com.dailysatori.shared.db.Memory_entry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MemorySearchState(
    val searchQuery: String = "",
    val memories: List<Memory_entry> = emptyList(),
    val isRebuilding: Boolean = false,
    val rebuildProgress: String = "",
)

class MemorySearchViewModel(
    private val memoryRepo: MemoryRepository,
    private val extractService: MemoryExtractService,
    private val articleRepo: ArticleRepository,
    private val diaryRepo: DiaryRepository,
    private val bookRepo: BookRepository,
    private val viewpointRepo: BookViewpointRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MemorySearchState())
    val state: StateFlow<MemorySearchState> = _state.asStateFlow()

    fun loadMemories() {
        loadMemories(_state.value.searchQuery)
    }

    fun search(query: String) {
        _state.update { it.copy(searchQuery = query) }
        loadMemories(query)
    }

    fun rebuildAll() {
        if (_state.value.isRebuilding) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isRebuilding = true) }
            extractService.rebuildAll(
                articleRepo,
                diaryRepo,
                bookRepo,
                viewpointRepo,
                onProgress = { progress -> _state.update { it.copy(rebuildProgress = progress) } },
            )
            val memories = memoryRepo.getAllSync()
            _state.update { it.copy(memories = memories, isRebuilding = false) }
        }
    }

    private fun loadMemories(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val memories = if (query.isBlank()) {
                memoryRepo.getAllSync()
            } else {
                memoryRepo.search(query, 50)
            }
            _state.update { it.copy(memories = memories) }
        }
    }
}
```

- [ ] **Step 2: Register `MemorySearchViewModel`**

In `ViewModelModule.kt`, add import:

```kotlin
import com.dailysatori.ui.feature.aichat.MemorySearchViewModel
```

Add registration after `AiReferenceDetailViewModel`:

```kotlin
viewModel {
    MemorySearchViewModel(
        memoryRepo = get(),
        extractService = get<MemoryExtractService>(),
        articleRepo = get<ArticleRepository>(),
        diaryRepo = get<DiaryRepository>(),
        bookRepo = get<BookRepository>(),
        viewpointRepo = get<BookViewpointRepository>(),
    )
}
```

- [ ] **Step 3: Update `MemorySearchSheet` dependencies and state**

In `MemorySearchSheet.kt`, remove these imports:

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.MemoryRepository
import com.dailysatori.service.memory.MemoryExtractService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
```

Add imports:

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import org.koin.androidx.compose.koinViewModel
```

Replace the top of `MemorySearchSheet` with:

```kotlin
val viewModel: MemorySearchViewModel = koinViewModel()
val state by viewModel.state.collectAsState()

LaunchedEffect(Unit) {
    viewModel.loadMemories()
}
```

Replace `searchQuery`, `memories`, `isRebuilding`, and `rebuildProgress` usages with `state.searchQuery`, `state.memories`, `state.isRebuilding`, and `state.rebuildProgress`.

- [ ] **Step 4: Update `MemorySearchSheet` event handlers**

Replace search field handler:

```kotlin
onValueChange = viewModel::search,
```

Replace clear handler:

```kotlin
IconButton(onClick = { viewModel.search("") }) {
```

Replace rebuild button handler:

```kotlin
onClick = viewModel::rebuildAll,
enabled = !state.isRebuilding,
```

Replace memory count and list references:

```kotlin
"${state.memories.size} 条记忆"
```

```kotlin
items(state.memories, key = { it.id }) { memory ->
    MemoryEntryCard(memory)
}
```

- [ ] **Step 5: Run targeted boundary test**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.core.di.DependencyBoundaryTest.memorySearchSheetDoesNotInjectRepositoriesInsideComposable
```

Expected: PASS.

---

### Task 5: Clean Up Touched Koin Registrations And Run All Phase 1 Tests

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`
- Test: `app/src/test/kotlin/com/dailysatori/core/di/DependencyBoundaryTest.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigLayoutTest.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/settings/SettingsUpdateProgressTest.kt`

- [ ] **Step 1: Make touched `UnifiedNewsViewModel` registration readable only if editing nearby imports already touched the file**

Replace the existing compact registration:

```kotlin
viewModel {
    UnifiedNewsViewModel(
        get(), get(), get(), get(), get(), get<ArticleRepository>(),
        get<WebpageParserService>(), com.dailysatori.BuildConfig.DEBUG,
    )
}
```

with named arguments:

```kotlin
viewModel {
    UnifiedNewsViewModel(
        summaryRepo = get(),
        summaryService = get(),
        settingRepo = get<SettingRepository>(),
        remoteNewsService = get(),
        remoteNewsSourceRepo = get(),
        articleRepo = get<ArticleRepository>(),
        webpageParserService = get<WebpageParserService>(),
        isDebugBuild = com.dailysatori.BuildConfig.DEBUG,
    )
}
```

Replace:

```kotlin
viewModel { RemoteNewsViewModel(get(), get(), get<ArticleRepository>(), get<WebpageParserService>()) }
```

with:

```kotlin
viewModel {
    RemoteNewsViewModel(
        settingRepo = get<SettingRepository>(),
        remoteNewsService = get(),
        articleRepo = get<ArticleRepository>(),
        webpageParserService = get<WebpageParserService>(),
    )
}
```

- [ ] **Step 2: Run Phase 1 unit tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.core.di.DependencyBoundaryTest --tests com.dailysatori.ui.feature.aiconfig.AiConfigLayoutTest --tests com.dailysatori.ui.feature.settings.SettingsUpdateProgressTest
```

Expected: PASS.

- [ ] **Step 3: Review diff before full verification**

Run:

```bash
git diff -- app/src/main/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigEditScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigEditViewModel.kt app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MemorySearchSheet.kt app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MemorySearchViewModel.kt app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsViewModel.kt app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt app/src/test/kotlin/com/dailysatori/core/di/DependencyBoundaryTest.kt app/src/test/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigLayoutTest.kt
```

Expected: diff only contains Phase 1 boundary cleanup, new ViewModels, Koin registration updates, and tests.

---

### Task 6: Full Phase 1 Verification On Phone

**Files:**
- Modify: none
- Test: Gradle build and connected phone install/start

- [ ] **Step 1: Compile debug Kotlin**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Build debug APK**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Verify a phone is connected**

Run:

```bash
adb devices
```

Expected: at least one physical phone appears with `device` status. Do not start or deploy to an emulator.

- [ ] **Step 4: Install debug build to phone**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
```

Expected: BUILD SUCCESSFUL and install succeeds on the connected phone.

- [ ] **Step 5: Start the app on phone**

Run:

```bash
adb shell am start -n com.dailysatori/.MainActivity
```

Expected: command succeeds and Daily Satori opens on the phone.

- [ ] **Step 6: Report verification results**

Report the exact commands that passed. If any command fails, stop and diagnose the failure before claiming Phase 1 is complete.

---

## Self-Review Notes

- Spec coverage: Phase 1 covers AI config direct Koin lookup, memory search direct Composable injection, SettingsViewModel internal Koin lookup, and Koin registration readability for touched dependencies.
- Placeholder scan: no `TBD`, `TODO`, or unspecified implementation steps remain.
- Type consistency: new ViewModel names are `AiConfigEditViewModel` and `MemorySearchViewModel`; state names are `AiConfigEditState` and `MemorySearchState`; all registrations reference the same names.

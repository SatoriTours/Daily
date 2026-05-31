# Settings Configuration Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Normalize the remaining settings/configuration screens around ViewModel-owned operations without changing UI, Chinese text, navigation, data, or runtime behavior.

**Architecture:** Keep Composables thin: they render `StateFlow` state, collect user input, and call ViewModel event functions. Move import, restore, and MCP save coroutine ownership into ViewModels with Koin constructor injection. Preserve existing shared services and repositories; do not change database schema or backup/import formats.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, AndroidX ViewModel, Kotlin coroutines, StateFlow, Koin, existing KMP shared services.

**Workspace Note:** Project instructions forbid git worktrees and commits unless explicitly requested. Execute in the current workspace and do not include commit steps.

---

## File Structure

- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/importing/DataImportViewModel.kt`
  - Owns `ImportService` calls and import state for the data import screen.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/importing/DataImportScreen.kt`
  - Removes direct Koin lookup and Composable-owned import coroutine.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/backup/BackupRestoreViewModel.kt`
  - Converts restore from caller-launched suspend API to ViewModel-launched event API.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/backup/BackupRestoreScreen.kt`
  - Removes `rememberCoroutineScope` restore call and delegates restore to the ViewModel.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/mcp/McpServerViewModel.kt`
  - Converts MCP save operations from suspend APIs to ViewModel-launched event APIs.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/mcp/McpServerScreen.kt`
  - Removes Composable-owned save coroutines while preserving local form state and messages.
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`
  - Registers `DataImportViewModel` and makes settings ViewModel constructor arguments explicit.
- Modify: `app/src/test/kotlin/com/dailysatori/core/di/DependencyBoundaryTest.kt`
  - Adds source-level guardrails for import screen DI boundaries and Koin registration readability.
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/settings/BackupRestoreScreenFeedbackTest.kt`
  - Adds source-level guardrails for restore coroutine ownership.
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/settings/McpServerScreenTest.kt`
  - Adds source-level guardrails for MCP save coroutine ownership.

---

### Task 1: Add Data Import Boundary Tests

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/core/di/DependencyBoundaryTest.kt`

- [ ] **Step 1: Add failing tests for data import boundaries and registration**

Append these tests before the existing `viewModelModuleRegistersPhaseOneViewModels` test:

```kotlin
    @Test
    fun dataImportScreenUsesViewModelForImportOperation() {
        val source = source("src/main/kotlin/com/dailysatori/ui/feature/settings/importing/DataImportScreen.kt")

        assertFalse(source.contains("KoinPlatform.getKoin()"))
        assertFalse(source.contains("rememberCoroutineScope"))
        assertFalse(source.contains("importService.importFromZip"))
        assertTrue(source.contains("DataImportViewModel"))
        assertTrue(source.contains("koinViewModel"))
    }

    @Test
    fun dataImportViewModelOwnsImportService() {
        val source = source("src/main/kotlin/com/dailysatori/ui/feature/settings/importing/DataImportViewModel.kt")

        assertTrue(source.contains("private val importService: ImportService"))
        assertTrue(source.contains("viewModelScope.launch(Dispatchers.IO)"))
        assertTrue(source.contains("importService.importFromZip(path)"))
        assertTrue(source.contains("DataImportState(error = \"无法读取文件\")"))
    }
```

Replace the existing `viewModelModuleRegistersPhaseOneViewModels` test with this expanded version:

```kotlin
    @Test
    fun viewModelModuleRegistersRefactorViewModelsWithNamedDependencies() {
        val source = source("src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt")

        assertTrue(source.contains("AiConfigEditViewModel("))
        assertTrue(source.contains("MemorySearchViewModel("))
        assertTrue(source.contains("settingRepo = get<SettingRepository>()"))
        assertTrue(source.contains("DataImportViewModel("))
        assertTrue(source.contains("importService = get<ImportService>()"))
        assertTrue(source.contains("sourceRepo = get()"))
        assertTrue(source.contains("remoteNewsService = get()"))
        assertTrue(source.contains("repo = get()"))
        assertTrue(source.contains("remoteMcpClient = get()"))
        assertTrue(source.contains("repository = get()"))
        assertTrue(source.contains("connectionTester = get()"))
    }
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.core.di.DependencyBoundaryTest
```

Expected: FAIL because `DataImportViewModel.kt` does not exist yet and `DataImportScreen.kt` still contains `KoinPlatform.getKoin()`, `rememberCoroutineScope`, and `importService.importFromZip`.

---

### Task 2: Move Data Import Operation Into a ViewModel

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/importing/DataImportViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/importing/DataImportScreen.kt`

- [ ] **Step 1: Create `DataImportViewModel.kt`**

Create the file with this complete content:

```kotlin
package com.dailysatori.ui.feature.settings.importing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.service.import.ImportService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DataImportState(
    val isImporting: Boolean = false,
    val progress: Float = 0f,
    val result: ImportService.ImportResult? = null,
    val error: String? = null,
)

class DataImportViewModel(
    private val importService: ImportService,
) : ViewModel() {
    private val _state = MutableStateFlow(DataImportState())
    val state: StateFlow<DataImportState> = _state.asStateFlow()

    init {
        observeImportProgress()
    }

    fun importFromZip(path: String?) {
        if (path == null) {
            _state.value = DataImportState(error = "无法读取文件")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = DataImportState(isImporting = true)
            try {
                val result = importService.importFromZip(path)
                _state.value = DataImportState(progress = 1f, result = result)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = DataImportState(error = error.message ?: "导入失败")
            }
        }
    }

    private fun observeImportProgress() {
        viewModelScope.launch(Dispatchers.IO) {
            importService.progress.collect { progress ->
                _state.update { state ->
                    if (state.isImporting) state.copy(progress = progress.toFloat()) else state
                }
            }
        }
    }
}
```

- [ ] **Step 2: Update `DataImportScreen.kt` imports**

Remove these imports:

```kotlin
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
```

Add these imports:

```kotlin
import androidx.compose.runtime.collectAsState
import org.koin.androidx.compose.koinViewModel
```

Keep `import androidx.compose.runtime.getValue` because `state` will still use delegated `by` syntax.

- [ ] **Step 3: Remove the screen-local import state data class**

Delete this block from `DataImportScreen.kt`:

```kotlin
data class ImportState(
    val isImporting: Boolean = false,
    val progress: Float = 0f,
    val result: ImportService.ImportResult? = null,
    val error: String? = null,
)
```

- [ ] **Step 4: Replace direct dependency lookup and screen coroutine with ViewModel state**

Replace this block inside `DataImportScreen`:

```kotlin
    val context = LocalContext.current
    val importService = remember { KoinPlatform.getKoin().get<ImportService>() }
    val coroutineScope = rememberCoroutineScope()
    var state by remember { mutableStateOf(ImportState()) }
```

With:

```kotlin
    val context = LocalContext.current
    val viewModel: DataImportViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
```

- [ ] **Step 5: Replace the launcher callback**

Replace the current `rememberLauncherForActivityResult` callback body:

```kotlin
    val pickZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let {
            val path = copyUriToTempFile(context, it)
            if (path != null) {
                coroutineScope.launch {
                    state = ImportState(isImporting = true)
                    try {
                        val result = importService.importFromZip(path)
                        state = ImportState(result = result)
                    } catch (e: Exception) {
                        state = ImportState(error = e.message ?: "导入失败")
                    }
                }
            } else {
                state = ImportState(error = "无法读取文件")
            }
        }
    }
```

With:

```kotlin
    val pickZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importFromZip(copyUriToTempFile(context, it))
        }
    }
```

- [ ] **Step 6: Run the focused boundary test and verify the implementation still needs DI registration**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.core.di.DependencyBoundaryTest
```

Expected: FAIL only on `viewModelModuleRegistersRefactorViewModelsWithNamedDependencies` because `DataImportViewModel` is not registered yet.

---

### Task 3: Move Backup Restore Coroutine Ownership Into the ViewModel

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/settings/BackupRestoreScreenFeedbackTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/backup/BackupRestoreViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/backup/BackupRestoreScreen.kt`

- [ ] **Step 1: Update backup restore tests first**

Add `assertFalse` to the imports in `BackupRestoreScreenFeedbackTest.kt`:

```kotlin
import kotlin.test.assertFalse
```

Append this test to `BackupRestoreScreenFeedbackTest`:

```kotlin
    @Test
    fun restoreScreenDelegatesRestoreWorkToViewModel() {
        val screen = File("src/main/kotlin/com/dailysatori/ui/feature/settings/backup/BackupRestoreScreen.kt").readText()
        val viewModel = File("src/main/kotlin/com/dailysatori/ui/feature/settings/backup/BackupRestoreViewModel.kt").readText()

        assertFalse(screen.contains("rememberCoroutineScope"), "Restore screen should not own restore coroutine scope")
        assertFalse(screen.contains("scope.launch"), "Restore screen should not launch restore work")
        assertTrue(screen.contains("viewModel.restoreBackup(restorePassword)"), "Restore screen should call ViewModel event")
        assertTrue(viewModel.contains("fun restoreBackup(password: String)"), "Restore should be a ViewModel event")
        assertFalse(viewModel.contains("suspend fun restoreBackup"), "Restore API should not require Composable coroutine ownership")
    }
```

- [ ] **Step 2: Run the focused backup test and verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.settings.BackupRestoreScreenFeedbackTest
```

Expected: FAIL because `BackupRestoreScreen.kt` still uses `rememberCoroutineScope` and `scope.launch`, and `BackupRestoreViewModel.restoreBackup` is still suspend.

- [ ] **Step 3: Replace `restoreBackup` in `BackupRestoreViewModel.kt`**

Add this import if it is not already present:

```kotlin
import kotlinx.coroutines.CancellationException
```

Replace the current `suspend fun restoreBackup(password: String): Boolean` function with these functions:

```kotlin
    fun restoreBackup(password: String) {
        val backupName = selectedBackupName(password) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    isRestoring = true,
                    restoreProgress = 0f,
                    statusMessage = "准备恢复...",
                    successMessage = "",
                    errorMessage = "",
                )
            }
            try {
                val success = backupService.restore(backupName, password)
                if (success) {
                    _state.update { it.copy(successMessage = "恢复完成", errorMessage = "") }
                } else {
                    _state.update { it.copy(errorMessage = backupService.lastMessage.value.ifBlank { "恢复失败，请检查密码" }) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message ?: "恢复失败") }
            } finally {
                _state.update { it.copy(isRestoring = false) }
            }
        }
    }

    private fun selectedBackupName(password: String): String? {
        val current = _state.value
        val index = current.selectedBackupIndex
        if (index < 0 || index >= current.backupList.size) {
            _state.update { it.copy(successMessage = "", statusMessage = "", errorMessage = "未选择备份文件") }
            return null
        }
        if (password.isBlank()) {
            _state.update { it.copy(successMessage = "", statusMessage = "", errorMessage = "请输入备份密码") }
            return null
        }
        return current.backupList[index]
    }
```

- [ ] **Step 4: Update `BackupRestoreScreen.kt` imports**

Remove these imports:

```kotlin
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
```

- [ ] **Step 5: Remove the restore coroutine scope from `BackupRestoreScreen`**

Delete this line:

```kotlin
    val scope = rememberCoroutineScope()
```

- [ ] **Step 6: Preserve password clearing on successful restore**

Add this effect after the existing `LaunchedEffect(Unit)` block:

```kotlin
    LaunchedEffect(state.successMessage) {
        if (state.successMessage.isNotBlank()) {
            restorePassword = ""
        }
    }
```

- [ ] **Step 7: Replace restore confirmation callback**

Replace this block:

```kotlin
                onConfirm = {
                    showPasswordDialog = false
                    scope.launch {
                        if (viewModel.restoreBackup(restorePassword)) {
                            restorePassword = ""
                        }
                    }
                },
```

With:

```kotlin
                onConfirm = {
                    showPasswordDialog = false
                    viewModel.restoreBackup(restorePassword)
                },
```

- [ ] **Step 8: Run the focused backup test and verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.settings.BackupRestoreScreenFeedbackTest
```

Expected: PASS.

---

### Task 4: Move MCP Save Coroutine Ownership Into the ViewModel

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/settings/McpServerScreenTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/mcp/McpServerViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/mcp/McpServerScreen.kt`

- [ ] **Step 1: Add source-level MCP coroutine boundary tests**

Add `java.io.File` to the imports in `McpServerScreenTest.kt`:

```kotlin
import java.io.File
```

Append these tests to `McpServerScreenTest` before `unsafeMcpServerViewModel`:

```kotlin
    @Test
    fun mcpScreenDelegatesSaveWorkToViewModel() {
        val screen = File("src/main/kotlin/com/dailysatori/ui/feature/settings/mcp/McpServerScreen.kt").readText()

        assertFalse(screen.contains("rememberCoroutineScope"), "MCP screen should not own save coroutine scope")
        assertFalse(screen.contains("scope.launch"), "MCP screen should not launch save work")
        assertFalse(screen.contains("kotlinx.coroutines.launch"), "MCP screen should not import coroutine launch")
        assertTrue(screen.contains("viewModel.saveSelectedTemplates(provider, selectedTemplates, apiKey) { result ->"))
        assertTrue(screen.contains("viewModel.saveServer(serverId, name, serverUrl, apiKey, enabled) {"))
    }

    @Test
    fun mcpViewModelOwnsSaveOperations() {
        val viewModel = File("src/main/kotlin/com/dailysatori/ui/feature/settings/mcp/McpServerViewModel.kt").readText()

        assertTrue(viewModel.contains("fun saveSelectedTemplates("))
        assertFalse(viewModel.contains("suspend fun saveSelectedTemplates"))
        assertTrue(viewModel.contains("onSuccess: (McpBatchSaveResult) -> Unit"))
        assertTrue(viewModel.contains("fun saveServer("))
        assertFalse(viewModel.contains("suspend fun saveServer"))
        assertTrue(viewModel.contains("onSaved: () -> Unit"))
        assertTrue(viewModel.contains("withContext(Dispatchers.Main) { onSuccess(result) }"))
        assertTrue(viewModel.contains("withContext(Dispatchers.Main) { onSaved() }"))
    }
```

- [ ] **Step 2: Run the focused MCP test and verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.settings.McpServerScreenTest
```

Expected: FAIL because `McpServerScreen.kt` still uses `rememberCoroutineScope`, `scope.launch`, and suspend save APIs.

- [ ] **Step 3: Replace `saveSelectedTemplates` in `McpServerViewModel.kt`**

Replace the current `suspend fun saveSelectedTemplates(...)` function with this non-suspend ViewModel event:

```kotlin
    fun saveSelectedTemplates(
        provider: McpProvider,
        templates: List<McpTemplate>,
        apiKey: String,
        onSuccess: (McpBatchSaveResult) -> Unit,
    ) {
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = saveTemplates(provider, templates, apiKey)
                withContext(Dispatchers.Main) { onSuccess(result) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message) }
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }
```

- [ ] **Step 4: Replace `saveServer` in `McpServerViewModel.kt`**

Replace the current `suspend fun saveServer(...)` function with this non-suspend ViewModel event:

```kotlin
    fun saveServer(
        serverId: Long?,
        name: String,
        serverUrl: String,
        apiKey: String,
        enabled: Boolean,
        onSaved: () -> Unit,
    ) {
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (serverId != null && serverId > 0) {
                    repo.update(serverId, name, serverUrl, apiKey, if (enabled) 1L else 0L)
                } else {
                    repo.insert(name, serverUrl, apiKey, if (enabled) 1L else 0L)
                }
                withContext(Dispatchers.Main) { onSaved() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message) }
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }
```

- [ ] **Step 5: Update `McpServerScreen.kt` imports**

Remove these imports:

```kotlin
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
```

- [ ] **Step 6: Remove preset add coroutine scope**

Delete this line from `McpServerPresetAddScreen`:

```kotlin
    val scope = rememberCoroutineScope()
```

Replace the `onSave` lambda inside `McpPresetAddActions` with:

```kotlin
                onSave = {
                    val provider = selectedProvider ?: return@McpPresetAddActions
                    viewModel.saveSelectedTemplates(provider, selectedTemplates, apiKey) { result ->
                        saveMessage = mcpBatchSaveResultMessage(result)
                        selectedTemplateIds = emptySet()
                    }
                },
```

- [ ] **Step 7: Remove edit screen coroutine scope**

Delete this line from `McpServerEditScreen`:

```kotlin
    val scope = rememberCoroutineScope()
```

Replace the `onSave` lambda inside `SettingsEditorBottomBar` with:

```kotlin
                onSave = {
                    viewModel.saveServer(serverId, name, serverUrl, apiKey, enabled) {
                        onBack()
                    }
                },
```

- [ ] **Step 8: Run the focused MCP test and verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.settings.McpServerScreenTest
```

Expected: PASS.

---

### Task 5: Register Settings ViewModels With Explicit Dependencies

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`
- Verify: `app/src/test/kotlin/com/dailysatori/core/di/DependencyBoundaryTest.kt`

- [ ] **Step 1: Add imports to `ViewModelModule.kt`**

Add these imports:

```kotlin
import com.dailysatori.service.import.ImportService
import com.dailysatori.ui.feature.settings.importing.DataImportViewModel
```

- [ ] **Step 2: Register `DataImportViewModel`**

Add this registration after the `BackupRestoreViewModel` registration:

```kotlin
    viewModel {
        DataImportViewModel(
            importService = get<ImportService>(),
        )
    }
```

- [ ] **Step 3: Replace compact settings registrations with named arguments**

Replace these lines:

```kotlin
    viewModel { RemoteNewsSettingsViewModel(get(), get()) }
```

```kotlin
    viewModel { McpServerViewModel(get(), get()) }
    viewModel { SkillSettingsViewModel(get(), get()) }
```

With:

```kotlin
    viewModel {
        RemoteNewsSettingsViewModel(
            sourceRepo = get(),
            remoteNewsService = get(),
        )
    }
```

```kotlin
    viewModel {
        McpServerViewModel(
            repo = get(),
            remoteMcpClient = get(),
        )
    }
    viewModel {
        SkillSettingsViewModel(
            repository = get(),
            connectionTester = get(),
        )
    }
```

- [ ] **Step 4: Run the dependency boundary test and verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.core.di.DependencyBoundaryTest
```

Expected: PASS.

---

### Task 6: Full Verification And Device Install

**Files:**
- Verify all modified files.

- [ ] **Step 1: Run all app unit tests**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run Kotlin compile check**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Build debug APK**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Install only to the physical phone**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ANDROID_SERIAL=ba5e2328 ./gradlew :app:installDebug
```

Expected: BUILD SUCCESSFUL and installation targets device `ba5e2328`, not `emulator-5554`.

- [ ] **Step 5: Launch the app only on the physical phone**

Run:

```bash
adb -s ba5e2328 shell am start -n com.dailysatori/.MainActivity
```

Expected: `Starting: Intent { cmp=com.dailysatori/.MainActivity }` or equivalent successful launch output.

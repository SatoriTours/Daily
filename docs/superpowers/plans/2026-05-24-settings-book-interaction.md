# Settings And Book Add Interaction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Unify settings configuration editors around a clean test-and-save detail flow, and update the add-book result card to prioritize book introductions with WeRead on the left and add/analyze on the right.

**Architecture:** Add one small reusable Compose bottom-action component for configuration editors, then adopt it in AI, MCP, Skill, and Plugin screens without changing settings navigation. Keep backend changes minimal: reuse existing AI and Skill testing, add a focused MCP connection test on `RemoteMcpClient`, and expose existing plugin server URL editing with lightweight test/save actions.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Koin, Ktor client, Kotlin test, Android Gradle plugin.

---

## File Structure

- Create `app/src/main/kotlin/com/dailysatori/ui/component/settings/SettingsEditorActions.kt`: reusable bottom bar and inline message helpers for test/save editor pages.
- Create `app/src/test/kotlin/com/dailysatori/ui/component/settings/SettingsEditorActionsTest.kt`: unit tests for shared action labels and layout contract helpers.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigEditScreen.kt`: move test/save actions to `AppScaffold.bottomBar`, keep inline test result, and save before returning.
- Modify `app/src/test/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigLayoutTest.kt`: add source-level assertions that AI config uses the shared bottom bar and no longer renders an inline cancel/save row.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/RemoteMcpClient.kt`: add a public `testConnection(server)` method that initializes the server and lists tools.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/settings/mcp/McpServerViewModel.kt`: inject `RemoteMcpClient`, add test state, expose `testServer`.
- Modify `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`: pass `RemoteMcpClient` into `McpServerViewModel`.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/settings/mcp/McpServerScreen.kt`: update manual/edit page bottom bar from cancel/save to test/save and display test results inline.
- Modify `app/src/test/kotlin/com/dailysatori/ui/feature/settings/McpServerScreenTest.kt`: add tests for MCP test labels and result messages.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/settings/skills/SkillSettingsScreen.kt`: replace inline test/save buttons with shared bottom test/save bar.
- Modify `app/src/test/kotlin/com/dailysatori/ui/feature/settings/skills/SkillSettingsTextTest.kt`: assert bottom action labels remain consistent.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/plugin/PluginService.kt`: add a small `testServer(url)` method that performs a GET and returns success/failure without persisting.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/settings/plugin/PluginCenterViewModel.kt`: add server URL edit/test/save state methods.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/settings/plugin/PluginCenterScreen.kt`: add a plugin server config editor page with the shared bottom test/save bar while keeping plugin list refresh/update behavior.
- Create `app/src/test/kotlin/com/dailysatori/ui/feature/settings/plugin/PluginCenterTextTest.kt`: tests for plugin config labels and source contract.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/book/component/BookSearchSheets.kt`: change result card action row to left WeRead text link and right add/analyze primary button.
- Modify `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookSearchUiTextTest.kt`: strengthen assertions for the chosen book card action layout.

Version control note: do not create git commits during execution unless the user explicitly asks for commits.

---

### Task 1: Shared Settings Editor Actions

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/component/settings/SettingsEditorActions.kt`
- Create: `app/src/test/kotlin/com/dailysatori/ui/component/settings/SettingsEditorActionsTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/dailysatori/ui/component/settings/SettingsEditorActionsTest.kt`:

```kotlin
package com.dailysatori.ui.component.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsEditorActionsTest {
    @Test
    fun sharedSettingsEditorActionsUseTestAndSave() {
        assertEquals("测试连接", settingsEditorTestActionText(isTesting = false))
        assertEquals("测试中...", settingsEditorTestActionText(isTesting = true))
        assertEquals("保存", settingsEditorSaveActionText(isSaving = false))
        assertEquals("保存中...", settingsEditorSaveActionText(isSaving = true))
        assertEquals(true, settingsEditorActionsUseTestAndSave())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.component.settings.SettingsEditorActionsTest`

Expected: FAIL because `settingsEditorTestActionText`, `settingsEditorSaveActionText`, and `settingsEditorActionsUseTestAndSave` do not exist.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/kotlin/com/dailysatori/ui/component/settings/SettingsEditorActions.kt`:

```kotlin
package com.dailysatori.ui.component.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dailysatori.ui.theme.Spacing

@Composable
fun SettingsEditorBottomBar(
    canTest: Boolean,
    canSave: Boolean,
    isTesting: Boolean,
    isSaving: Boolean,
    onTest: () -> Unit,
    onSave: () -> Unit,
    testText: String = settingsEditorTestActionText(isTesting),
    saveText: String = settingsEditorSaveActionText(isSaving),
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        OutlinedButton(
            onClick = onTest,
            modifier = Modifier.weight(1f),
            enabled = canTest && !isTesting && !isSaving,
        ) { Text(testText) }
        Button(
            onClick = onSave,
            modifier = Modifier.weight(1f),
            enabled = canSave && !isSaving && !isTesting,
        ) { Text(saveText) }
    }
}

@Composable
fun SettingsEditorMessage(message: String, isError: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

fun settingsEditorTestActionText(isTesting: Boolean): String = if (isTesting) "测试中..." else "测试连接"

fun settingsEditorSaveActionText(isSaving: Boolean): String = if (isSaving) "保存中..." else "保存"

fun settingsEditorActionsUseTestAndSave(): Boolean = true
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.component.settings.SettingsEditorActionsTest`

Expected: PASS.

---

### Task 2: AI Config Editor Bottom Actions

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigEditScreen.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigLayoutTest.kt`

- [ ] **Step 1: Write the failing test**

Append to `AiConfigLayoutTest`:

```kotlin
    @Test
    fun editorUsesSharedBottomTestAndSaveActions() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigEditScreen.kt").readText()

        assertEquals(true, source.contains("bottomBar ="))
        assertEquals(true, source.contains("SettingsEditorBottomBar("))
        assertEquals(true, source.contains("SettingsEditorMessage("))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.aiconfig.AiConfigLayoutTest`

Expected: FAIL because `AiConfigEditScreen.kt` does not use `SettingsEditorBottomBar`.

- [ ] **Step 3: Update imports and save/test helpers**

In `AiConfigEditScreen.kt`, add imports:

```kotlin
import com.dailysatori.ui.component.settings.SettingsEditorBottomBar
import com.dailysatori.ui.component.settings.SettingsEditorMessage
```

Inside `AiConfigEditScreen`, after `val isCustomModel = selectedProvider != null && models.isEmpty()`, add:

```kotlin
    val currentModel = currentModelId(isCustomModel, customModelName, selectedModel)
    val canTest = selectedProvider != null && apiToken.isNotBlank() && currentModel != null
    val canSave = selectedProvider != null && apiToken.isNotBlank() && currentModel != null
```

- [ ] **Step 4: Replace scaffold and action items**

Change the `AppScaffold` call to include `bottomBar`:

```kotlin
    AppScaffold(
        title = if (configId != null) "编辑配置" else "添加配置",
        onBack = onBack,
        bottomBar = {
            SettingsEditorBottomBar(
                canTest = canTest,
                canSave = canSave,
                isTesting = isTesting,
                isSaving = isSaving,
                onTest = {
                    val provider = selectedProvider ?: return@SettingsEditorBottomBar
                    val modelId = currentModel ?: return@SettingsEditorBottomBar
                    scope.launch {
                        isTesting = true
                        testResult = null
                        testSuccess = null
                        val result = withContext(Dispatchers.IO) {
                            aiService.testConnection(
                                apiAddress = provider.apiHost,
                                apiToken = apiToken,
                                modelName = modelId,
                                provider = provider.id,
                            )
                        }
                        testSuccess = result.isSuccess
                        testResult = result.fold(
                            onSuccess = { "连接成功：${it.take(80)}" },
                            onFailure = { it.message ?: "连接失败" },
                        )
                        isTesting = false
                    }
                },
                onSave = {
                    val provider = selectedProvider ?: return@SettingsEditorBottomBar
                    val modelId = currentModel ?: return@SettingsEditorBottomBar
                    scope.launch {
                        isSaving = true
                        try {
                            withContext(Dispatchers.IO) {
                                if (configId != null) {
                                    repo.update(configId, provider.id, provider.apiHost, apiToken, modelId, if (isDefault) 1L else 0L)
                                } else {
                                    repo.insert(provider.id, provider.apiHost, apiToken, modelId, if (isDefault) 1L else 0L)
                                }
                            }
                            onBack()
                        } finally {
                            isSaving = false
                        }
                    }
                },
            )
        },
    ) { modifier ->
```

Remove the inline `OutlinedButton` test item and the inline cancel/save `Row` item. Keep this message item near the end of the `LazyColumn`:

```kotlin
            if (testResult != null) {
                item {
                    SettingsEditorMessage(
                        message = testResult ?: "",
                        isError = testSuccess != true,
                    )
                }
            }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.aiconfig.AiConfigLayoutTest`

Expected: PASS.

---

### Task 3: MCP Connection Test Support

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/RemoteMcpClient.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/mcp/McpServerViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/settings/McpServerScreenTest.kt`

- [ ] **Step 1: Write the failing test**

Append to `McpServerScreenTest`:

```kotlin
    @Test
    fun formatsMcpConnectionTestMessages() {
        assertEquals("测试连接", mcpTestButtonText(isTesting = false))
        assertEquals("测试中...", mcpTestButtonText(isTesting = true))
        assertEquals("连接成功，发现 3 个工具", mcpConnectionSuccessMessage(toolCount = 3))
        assertEquals("连接成功，未发现工具", mcpConnectionSuccessMessage(toolCount = 0))
        assertEquals("请输入服务地址", mcpConnectionValidationMessage(name = "搜索", serverUrl = ""))
        assertEquals(null, mcpConnectionValidationMessage(name = "搜索", serverUrl = "https://mcp.example.com"))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.settings.McpServerScreenTest`

Expected: FAIL because the helper functions do not exist.

- [ ] **Step 3: Add public MCP test client method**

In `RemoteMcpClient`, add this public method inside the class:

```kotlin
    suspend fun testConnection(server: Mcp_server): Result<Int> = try {
        val sessionId = initialize(server)
        sendInitialized(server, sessionId)
        Result.success(listTools(server, sessionId).size)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
```

- [ ] **Step 4: Update MCP view model state and constructor**

In `McpServerViewModel.kt`, add import:

```kotlin
import com.dailysatori.service.mcp.RemoteMcpClient
```

Change state:

```kotlin
internal data class McpServerUiState(
    val servers: List<Mcp_server> = emptyList(),
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val testMessage: String? = null,
    val testSucceeded: Boolean? = null,
    val error: String? = null,
)
```

Change constructor:

```kotlin
internal class McpServerViewModel(
    private val repo: McpServerRepository,
    private val remoteMcpClient: RemoteMcpClient,
) : ViewModel() {
```

Add method:

```kotlin
    fun clearTestMessage() {
        _state.update { it.copy(testMessage = null, testSucceeded = null) }
    }

    fun testServer(name: String, serverUrl: String, apiKey: String) {
        val validation = mcpConnectionValidationMessage(name, serverUrl)
        if (validation != null) {
            _state.update { it.copy(testMessage = validation, testSucceeded = false) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isTesting = true, testMessage = null, testSucceeded = null) }
            val server = Mcp_server(
                id = -1L,
                name = name.trim(),
                server_url = serverUrl.trim(),
                api_key = apiKey.trim(),
                enabled = 1L,
                provider = "",
                template_id = "",
                template_type = "",
                config_json = "",
                created_at = 0L,
                updated_at = 0L,
            )
            val result = remoteMcpClient.testConnection(server)
            _state.update {
                it.copy(
                    isTesting = false,
                    testSucceeded = result.isSuccess,
                    testMessage = result.fold(
                        onSuccess = { count -> mcpConnectionSuccessMessage(count) },
                        onFailure = { error -> error.message ?: "连接失败" },
                    ),
                )
            }
        }
    }
```

If the generated `Mcp_server` constructor differs, inspect generated SQLDelight type and pass the same columns in the exact declared order.

- [ ] **Step 5: Add MCP text helpers**

In `McpServerScreen.kt`, add top-level helpers near existing helper functions:

```kotlin
internal fun mcpTestButtonText(isTesting: Boolean): String = if (isTesting) "测试中..." else "测试连接"

internal fun mcpConnectionSuccessMessage(toolCount: Int): String =
    if (toolCount > 0) "连接成功，发现 $toolCount 个工具" else "连接成功，未发现工具"

internal fun mcpConnectionValidationMessage(name: String, serverUrl: String): String? = when {
    name.trim().isBlank() -> "请输入服务名称"
    serverUrl.trim().isBlank() -> "请输入服务地址"
    else -> null
}
```

- [ ] **Step 6: Update DI**

In `ViewModelModule.kt`, replace:

```kotlin
    viewModel { McpServerViewModel(get()) }
```

with:

```kotlin
    viewModel { McpServerViewModel(get(), get()) }
```

- [ ] **Step 7: Run test to verify it passes**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.settings.McpServerScreenTest`

Expected: PASS.

---

### Task 4: MCP Editor Bottom Actions

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/mcp/McpServerScreen.kt`

- [ ] **Step 1: Update imports**

Add imports to `McpServerScreen.kt`:

```kotlin
import com.dailysatori.ui.component.settings.SettingsEditorBottomBar
import com.dailysatori.ui.component.settings.SettingsEditorMessage
```

- [ ] **Step 2: Replace cancel/save bottom bar with test/save**

In `McpServerEditScreen`, replace the `bottomBar` `Row` with:

```kotlin
        bottomBar = {
            SettingsEditorBottomBar(
                canTest = mcpConnectionValidationMessage(name, serverUrl) == null,
                canSave = name.isNotBlank() && serverUrl.isNotBlank(),
                isTesting = state.isTesting,
                isSaving = state.isSaving,
                testText = mcpTestButtonText(state.isTesting),
                onTest = { viewModel.testServer(name, serverUrl, apiKey) },
                onSave = {
                    scope.launch {
                        if (viewModel.saveServer(serverId, name, serverUrl, apiKey, enabled)) {
                            onBack()
                        }
                    }
                },
            )
        },
```

- [ ] **Step 3: Show inline test message**

In the edit `LazyColumn`, after the `state.error` item and before fields, add:

```kotlin
            state.testMessage?.let { message ->
                item {
                    SettingsEditorMessage(
                        message = message,
                        isError = state.testSucceeded != true,
                    )
                }
            }
```

- [ ] **Step 4: Clear stale test result on field edits**

Change field `onValueChange` lambdas:

```kotlin
onValueChange = { name = it; viewModel.clearTestMessage() }
onValueChange = { serverUrl = it; viewModel.clearTestMessage() }
onValueChange = { apiKey = it; viewModel.clearTestMessage() }
```

- [ ] **Step 5: Compile the edited MCP files**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL. If `Mcp_server` constructor columns differ, fix the constructor call in `McpServerViewModel.testServer` and rerun.

---

### Task 5: Skill Editor Bottom Actions

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/skills/SkillSettingsScreen.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/settings/skills/SkillSettingsTextTest.kt`

- [ ] **Step 1: Write the failing test**

Append to `SkillSettingsTextTest`:

```kotlin
    @Test
    fun skillEditorUsesBottomTestAndSaveActions() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/settings/skills/SkillSettingsScreen.kt").readText()

        assertTrue(source.contains("bottomBar ="))
        assertTrue(source.contains("SettingsEditorBottomBar("))
        assertTrue(source.contains("SettingsEditorMessage("))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.settings.skills.SkillSettingsTextTest`

Expected: FAIL because the skill editor still renders inline buttons.

- [ ] **Step 3: Update imports and scaffold**

In `SkillSettingsScreen.kt`, add imports:

```kotlin
import com.dailysatori.ui.component.settings.SettingsEditorBottomBar
import com.dailysatori.ui.component.settings.SettingsEditorMessage
```

Replace `AppScaffold(title = skill?.name ?: skillAddButtonText(), onBack = onBack) { modifier ->` with:

```kotlin
    AppScaffold(
        title = skill?.name ?: skillAddButtonText(),
        onBack = onBack,
        bottomBar = {
            SettingsEditorBottomBar(
                canTest = !isTesting,
                canSave = !isSaving,
                isTesting = isTesting,
                isSaving = isSaving,
                testText = skillTestButtonText(isTesting),
                saveText = skillSaveButtonText(isSaving),
                onTest = { onTest(fields.toInput(skill?.id)) },
                onSave = { onSave(fields.toInput(skill?.id)) },
            )
        },
    ) { modifier ->
```

- [ ] **Step 4: Replace inline messages/buttons**

Inside the `LazyColumn`, replace:

```kotlin
            if (error != null) item { SkillErrorText(error) }
            if (testMessage != null) item { SkillTestMessageText(testMessage) }
            item { SkillTestButton(skill?.id, fields, isTesting, onTest) }
            item { SkillSaveButton(skill?.id, fields, isSaving, onSave) }
```

with:

```kotlin
            if (error != null) item { SettingsEditorMessage(error, isError = true) }
            if (testMessage != null) item { SettingsEditorMessage(testMessage, isError = false) }
```

Remove now-unused private composables `SkillErrorText`, `SkillTestMessageText`, `SkillTestButton`, and `SkillSaveButton` if the compiler reports unused private declarations are acceptable either way. Prefer removing them to keep the file focused.

- [ ] **Step 5: Run test to verify it passes**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.settings.skills.SkillSettingsTextTest`

Expected: PASS.

---

### Task 6: Plugin Server Config Editor

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/plugin/PluginService.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/plugin/PluginCenterViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/plugin/PluginCenterScreen.kt`
- Create: `app/src/test/kotlin/com/dailysatori/ui/feature/settings/plugin/PluginCenterTextTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/dailysatori/ui/feature/settings/plugin/PluginCenterTextTest.kt`:

```kotlin
package com.dailysatori.ui.feature.settings.plugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluginCenterTextTest {
    @Test
    fun pluginServerConfigUsesSharedTestAndSaveFlow() {
        assertEquals("插件服务器", pluginServerConfigTitle())
        assertEquals("请输入插件服务器地址", pluginServerValidationMessage(""))
        assertEquals(null, pluginServerValidationMessage("https://plugins.example.com"))

        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/settings/plugin/PluginCenterScreen.kt").readText()
        assertTrue(source.contains("SettingsEditorBottomBar("))
        assertTrue(source.contains("PluginServerEditScreen("))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.settings.plugin.PluginCenterTextTest`

Expected: FAIL because helper functions and editor screen do not exist.

- [ ] **Step 3: Add plugin service test method**

In `PluginService.kt`, add:

```kotlin
    suspend fun testServer(url: String): Result<Unit> = try {
        client.get(url.trim()).bodyAsText()
        Result.success(Unit)
    } catch (e: Exception) {
        log.e(e) { "Failed to test plugin server" }
        Result.failure(e)
    }
```

- [ ] **Step 4: Add plugin view model state and methods**

Update `PluginCenterState`:

```kotlin
data class PluginCenterState(
    val plugins: List<PluginInfo> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val updatingPluginId: String = "",
    val serverUrl: String = "",
    val testMessage: String? = null,
    val testSucceeded: Boolean? = null,
    val error: String? = null,
)
```

Replace `saveServerUrl` with:

```kotlin
    fun saveServerUrl(url: String) {
        val validation = pluginServerValidationMessage(url)
        if (validation != null) {
            _state.update { it.copy(error = validation) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                settingRepo.upsert("plugin_server_url", url.trim())
                _state.update { it.copy(serverUrl = url.trim()) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    fun testServerUrl(url: String) {
        val validation = pluginServerValidationMessage(url)
        if (validation != null) {
            _state.update { it.copy(testMessage = validation, testSucceeded = false) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isTesting = true, testMessage = null, testSucceeded = null) }
            val result = pluginService.testServer(url)
            _state.update {
                it.copy(
                    isTesting = false,
                    testSucceeded = result.isSuccess,
                    testMessage = result.fold(
                        onSuccess = { "插件服务器可访问" },
                        onFailure = { error -> error.message ?: "插件服务器不可访问" },
                    ),
                )
            }
        }
    }

    fun clearTestMessage() {
        _state.update { it.copy(testMessage = null, testSucceeded = null, error = null) }
    }
```

- [ ] **Step 5: Add plugin screen helpers and editor mode**

In `PluginCenterScreen.kt`, add imports:

```kotlin
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.dailysatori.ui.component.settings.SettingsEditorBottomBar
import com.dailysatori.ui.component.settings.SettingsEditorMessage
```

Add top-level helpers:

```kotlin
fun pluginServerConfigTitle(): String = "插件服务器"

fun pluginServerValidationMessage(url: String): String? =
    if (url.trim().isBlank()) "请输入插件服务器地址" else null
```

At the start of `PluginCenterScreen`, add:

```kotlin
    var editingServer by remember { mutableStateOf(false) }
    if (editingServer) {
        PluginServerEditScreen(
            state = state,
            onBack = { editingServer = false; viewModel.loadPlugins() },
            onUrlChange = { viewModel.clearTestMessage() },
            onTest = viewModel::testServerUrl,
            onSave = viewModel::saveServerUrl,
        )
        return
    }
```

Add an action button in `AppScaffold.actions` before refresh:

```kotlin
            IconButton(onClick = { editingServer = true }) {
                Icon(Icons.Default.Settings, contentDescription = pluginServerConfigTitle())
            }
```

- [ ] **Step 6: Add plugin server editor composable**

Add to `PluginCenterScreen.kt`:

```kotlin
@Composable
private fun PluginServerEditScreen(
    state: PluginCenterState,
    onBack: () -> Unit,
    onUrlChange: () -> Unit,
    onTest: (String) -> Unit,
    onSave: (String) -> Unit,
) {
    var url by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }
    AppScaffold(
        title = pluginServerConfigTitle(),
        onBack = onBack,
        bottomBar = {
            SettingsEditorBottomBar(
                canTest = pluginServerValidationMessage(url) == null,
                canSave = pluginServerValidationMessage(url) == null,
                isTesting = state.isTesting,
                isSaving = state.isSaving,
                onTest = { onTest(url) },
                onSave = { onSave(url); onBack() },
            )
        },
    ) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(horizontal = Spacing.m),
            contentPadding = PaddingValues(vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item {
                Text("服务器地址", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(Spacing.xs))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; onUrlChange() },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://plugins.example.com") },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.s),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
            state.error?.let { item { SettingsEditorMessage(it, isError = true) } }
            state.testMessage?.let { item { SettingsEditorMessage(it, isError = state.testSucceeded != true) } }
        }
    }
}
```

If saving returns to the list before async save completes, adjust `saveServerUrl` to return a Boolean suspend function and close only after success. Keep the user-visible behavior: save persists and returns to plugin list.

- [ ] **Step 7: Run test to verify it passes**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.settings.plugin.PluginCenterTextTest`

Expected: PASS.

---

### Task 7: Book Add Result Card Layout

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/component/BookSearchSheets.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookSearchUiTextTest.kt`

- [ ] **Step 1: Write the failing test**

Update `addBookResultPrioritizesIntroductionAndClearPrimaryAction` in `BookSearchUiTextTest` to include:

```kotlin
        assertEquals(true, bookResultActionsUseBottomRow())
        assertEquals("微信读书", bookResultSourceActionText())
```

Add helper import if needed for `bookResultSourceActionText`.

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.book.BookSearchUiTextTest`

Expected: FAIL because `bookResultSourceActionText` does not exist.

- [ ] **Step 3: Add source action text helper**

In `BooksScreen.kt`, add near existing book result helpers:

```kotlin
fun bookResultSourceActionText(): String = "微信读书"
```

- [ ] **Step 4: Update card imports**

In `BookSearchSheets.kt`, add import:

```kotlin
import com.dailysatori.ui.feature.book.bookResultSourceActionText
```

- [ ] **Step 5: Replace action row layout**

In `BookSearchResultCard`, replace the two weighted `FilledTonalButton`s inside the action `Row` with:

```kotlin
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenSource),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = bookResultSourceActionDescription(),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(Spacing.xxs))
                    Text(
                        bookResultSourceActionText(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                FilledTonalButton(
                    onClick = onAdd,
                    enabled = !isAnalyzing,
                    contentPadding = PaddingValues(horizontal = Spacing.s, vertical = Spacing.xxs),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = bookResultAddActionDescription(),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(Spacing.xxs))
                    Text(bookResultPrimaryActionText(isAnalyzing), style = MaterialTheme.typography.labelSmall)
                }
```

Keep the introduction `Text` above this row and keep `bookContentSearchPreview(result.introduction, bookResultIntroductionPreviewLength())`.

- [ ] **Step 6: Run test to verify it passes**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.book.BookSearchUiTextTest`

Expected: PASS.

---

### Task 8: Full Verification And Device Deploy

**Files:**
- No new files.

- [ ] **Step 1: Run focused unit tests**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.component.settings.SettingsEditorActionsTest --tests com.dailysatori.ui.feature.aiconfig.AiConfigLayoutTest --tests com.dailysatori.ui.feature.settings.McpServerScreenTest --tests com.dailysatori.ui.feature.settings.skills.SkillSettingsTextTest --tests com.dailysatori.ui.feature.settings.plugin.PluginCenterTextTest --tests com.dailysatori.ui.feature.book.BookSearchUiTextTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Compile app Kotlin**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Install debug build to connected device**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`

Expected: BUILD SUCCESSFUL and install succeeds. If no device is connected, report the exact Gradle/ADB error.

- [ ] **Step 4: Launch app**

Run: `adb shell am start -n com.dailysatori/.MainActivity`

Expected: Activity starts without an ADB error.

- [ ] **Step 5: Manual smoke check**

Check these screens on the device:

```text
Settings > AI 配置 > edit/add: bottom actions show 测试连接 and 保存.
Settings > MCP 服务 > manual/edit: bottom actions show 测试连接 and 保存; list switch remains available.
Settings > Skills > edit/add: bottom actions show 测试 Skill and 保存 Skill.
Settings > 插件中心 > 插件服务器: bottom actions show 测试连接 and 保存.
Books > 添加书籍 search result: 微信读书 is a left text action and 添加并分析 is the right primary action.
```

Expected: All screens render without clipped controls on a narrow phone width.

---

## Self-Review

- Spec coverage: settings list/detail pattern covered by Tasks 1-6; book result card covered by Task 7; verification and device deployment covered by Task 8.
- Scope: no database schema changes, no settings navigation redesign, no plugin marketplace behavior.
- Placeholder scan: plan contains concrete paths, helper names, commands, and expected outcomes.
- Type consistency: shared bottom bar uses `canTest`, `canSave`, `isTesting`, `isSaving`, `onTest`, and `onSave` consistently across AI, MCP, Skills, and Plugin screens.

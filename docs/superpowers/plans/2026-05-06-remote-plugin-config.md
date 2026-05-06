# Remote Plugin Config Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add remote HTTP plugin configuration for AI prompts and AI provider/model presets while preserving built-in defaults when remote data is unavailable or invalid.

**Architecture:** Keep remote plugin data as validated JSON cached in `SettingRepository`. `PluginService` owns parsing, validation, update, and fallback lookup. Runtime callers ask `PluginService` for prompt/provider data and receive cached remote values only when valid; otherwise they receive built-in defaults.

**Tech Stack:** Kotlin Multiplatform, Ktor `HttpClient`, kotlinx.serialization JSON, SQLDelight-backed `SettingRepository`, Jetpack Compose, Koin.

---

## File Structure

- Create `shared/src/commonMain/kotlin/com/dailysatori/service/plugin/PluginModels.kt`
  - Serializable DTOs for `index.json`, prompt files, provider files, update results, and constants.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/plugin/PluginService.kt`
  - Parse cached JSON, update index/files from HTTP, validate plugin files, expose prompt/provider fallback APIs.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/parser/ArticleParserPrompts.kt`
  - Add prompt key constants and keep built-in prompt functions as fallback content.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt`
  - Use `PluginService` for article summary and Markdown prompts.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`
  - Inject `PluginService` into `WebpageParserService`.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/config/AiProviderModels.kt`
  - Add a helper that returns built-in providers explicitly; keep existing `aiProviders` compatibility.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigEditScreen.kt`
  - Load provider presets from `PluginService`, fallback to built-ins.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/settings/plugin/PluginCenterViewModel.kt`
  - Load cached index entries, save base URL, update index/all/plugin files, surface errors.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/settings/plugin/PluginCenterScreen.kt`
  - Add base URL editor, better empty state, plugin metadata and update status.
- Add `shared/src/commonTest/kotlin/com/dailysatori/service/plugin/PluginServiceTest.kt`
  - Unit tests for parsing, validation, fallback, update preservation.
- Modify `shared/src/commonTest/kotlin/com/dailysatori/service/parser/ArticleProcessingContentTest.kt`
  - Keep prompt tests focused on built-in fallback prompt content.

---

### Task 1: Plugin Models And Pure Parsers

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/plugin/PluginModels.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/plugin/PluginServiceTest.kt`

- [ ] **Step 1: Write failing parser tests**

Create `shared/src/commonTest/kotlin/com/dailysatori/service/plugin/PluginServiceTest.kt` with:

```kotlin
package com.dailysatori.service.plugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PluginServiceTest {
    @Test
    fun parsesValidPluginIndex() {
        val json = """
            {
              "version": 1,
              "plugins": [
                {"id":"article-summary","type":"prompt","file":"prompts/article-summary.json","version":3,"name":"文章摘要提示词"},
                {"id":"ai-providers","type":"aiProviders","file":"providers/ai-providers.json","version":5,"name":"AI 服务商列表"}
              ]
            }
        """.trimIndent()

        val index = parsePluginIndex(json)

        assertEquals(1, index.version)
        assertEquals(2, index.plugins.size)
        assertEquals("article-summary", index.plugins.first().id)
        assertEquals(PluginType.PROMPT, index.plugins.first().type)
        assertEquals("providers/ai-providers.json", index.plugins.last().file)
    }

    @Test
    fun rejectsPluginIndexWithUnsupportedType() {
        val json = """
            {"version":1,"plugins":[{"id":"bad","type":"code","file":"bad.json","version":1,"name":"Bad"}]}
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> { parsePluginIndex(json) }
    }

    @Test
    fun rejectsPluginIndexWithBlankFile() {
        val json = """
            {"version":1,"plugins":[{"id":"bad","type":"prompt","file":"","version":1,"name":"Bad"}]}
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> { parsePluginIndex(json) }
    }

    @Test
    fun parsesValidPromptPlugin() {
        val prompt = parsePromptPlugin(
            """{"id":"article-summary","version":3,"content":"远程提示词"}""",
        )

        assertEquals("article-summary", prompt.id)
        assertEquals(3, prompt.version)
        assertEquals("远程提示词", prompt.content)
    }

    @Test
    fun rejectsBlankPromptContent() {
        assertFailsWith<IllegalArgumentException> {
            parsePromptPlugin("""{"id":"article-summary","version":3,"content":"   "}""")
        }
    }

    @Test
    fun parsesValidAiProviderPlugin() {
        val plugin = parseAiProvidersPlugin(
            """
                {
                  "version": 5,
                  "providers": [
                    {"id":"openai","name":"OpenAI","apiHost":"https://api.openai.com","models":[{"id":"gpt-5.5","name":"GPT-5.5"}]}
                  ]
                }
            """.trimIndent(),
        )

        assertEquals(5, plugin.version)
        assertEquals("openai", plugin.providers.single().id)
        assertEquals("gpt-5.5", plugin.providers.single().models.single().id)
    }

    @Test
    fun rejectsAiProviderWithBlankHost() {
        assertFailsWith<IllegalArgumentException> {
            parseAiProvidersPlugin(
                """{"version":1,"providers":[{"id":"openai","name":"OpenAI","apiHost":"","models":[]}]}""",
            )
        }
    }
}
```

- [ ] **Step 2: Run tests to verify RED**

Run: `./gradlew :shared:testDebugUnitTest`

Expected: compile fails because `parsePluginIndex`, `PluginType`, `parsePromptPlugin`, and `parseAiProvidersPlugin` are unresolved.

- [ ] **Step 3: Implement plugin models and parsers**

Create `shared/src/commonMain/kotlin/com/dailysatori/service/plugin/PluginModels.kt`:

```kotlin
package com.dailysatori.service.plugin

import com.dailysatori.config.AiModel
import com.dailysatori.config.AiProvider
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal const val PLUGIN_INDEX_FILE = "index.json"
internal const val SETTING_PLUGIN_SERVER_URL = "plugin_server_url"
internal const val SETTING_PLUGIN_INDEX_JSON = "plugin_index_json"
internal const val SETTING_PLUGIN_CONTENT_PREFIX = "plugin_content_"
internal const val SETTING_PLUGIN_UPDATED_AT_PREFIX = "plugin_updated_at_"

const val PROMPT_ARTICLE_SUMMARY = "article-summary"
const val PROMPT_ARTICLE_MARKDOWN = "article-markdown"
const val PLUGIN_AI_PROVIDERS = "ai-providers"

enum class PluginType { PROMPT, AI_PROVIDERS }

@Serializable
internal data class PluginIndexDto(
    val version: Int = 1,
    val plugins: List<PluginEntryDto> = emptyList(),
)

@Serializable
internal data class PluginEntryDto(
    val id: String,
    val type: String,
    val file: String,
    val version: Int = 1,
    val name: String = "",
)

data class PluginIndex(
    val version: Int,
    val plugins: List<PluginEntry>,
)

data class PluginEntry(
    val id: String,
    val type: PluginType,
    val file: String,
    val version: Int,
    val name: String,
)

@Serializable
internal data class PromptPluginDto(
    val id: String,
    val version: Int = 1,
    val content: String,
)

data class PromptPlugin(
    val id: String,
    val version: Int,
    val content: String,
)

@Serializable
internal data class AiProvidersPluginDto(
    val version: Int = 1,
    val providers: List<AiProviderDto> = emptyList(),
)

@Serializable
internal data class AiProviderDto(
    val id: String,
    val name: String,
    val apiHost: String,
    val models: List<AiModelDto> = emptyList(),
)

@Serializable
internal data class AiModelDto(
    val id: String,
    val name: String,
)

data class AiProvidersPlugin(
    val version: Int,
    val providers: List<AiProvider>,
)

data class PluginUpdateResult(
    val updated: List<String>,
    val failed: List<String>,
) {
    val isSuccess: Boolean get() = failed.isEmpty()
}

private val pluginJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun parsePluginIndex(content: String): PluginIndex {
    val dto = pluginJson.decodeFromString<PluginIndexDto>(content)
    val entries = dto.plugins.map { entry ->
        val id = entry.id.trim()
        val file = entry.file.trim()
        require(id.isNotBlank()) { "Plugin id is blank" }
        require(file.isNotBlank()) { "Plugin file is blank" }
        require(!file.startsWith("/") && ".." !in file) { "Plugin file path is invalid" }
        PluginEntry(
            id = id,
            type = parsePluginType(entry.type),
            file = file,
            version = entry.version,
            name = entry.name.trim().ifBlank { id },
        )
    }
    return PluginIndex(version = dto.version, plugins = entries)
}

fun parsePromptPlugin(content: String): PromptPlugin {
    val dto = pluginJson.decodeFromString<PromptPluginDto>(content)
    val id = dto.id.trim()
    val prompt = dto.content.trim()
    require(id.isNotBlank()) { "Prompt id is blank" }
    require(prompt.isNotBlank()) { "Prompt content is blank" }
    return PromptPlugin(id = id, version = dto.version, content = prompt)
}

fun parseAiProvidersPlugin(content: String): AiProvidersPlugin {
    val dto = pluginJson.decodeFromString<AiProvidersPluginDto>(content)
    val providers = dto.providers.map { provider ->
        val id = provider.id.trim()
        val name = provider.name.trim()
        val apiHost = provider.apiHost.trim().trimEnd('/')
        require(id.isNotBlank()) { "AI provider id is blank" }
        require(name.isNotBlank()) { "AI provider name is blank" }
        require(apiHost.isNotBlank()) { "AI provider apiHost is blank" }
        AiProvider(
            id = id,
            name = name,
            apiHost = apiHost,
            models = provider.models.map { model ->
                require(model.id.isNotBlank()) { "AI model id is blank" }
                AiModel(model.id.trim(), model.name.trim().ifBlank { model.id.trim() })
            },
        )
    }
    require(providers.isNotEmpty()) { "AI providers list is empty" }
    return AiProvidersPlugin(version = dto.version, providers = providers)
}

private fun parsePluginType(type: String): PluginType = when (type.trim()) {
    "prompt" -> PluginType.PROMPT
    "aiProviders" -> PluginType.AI_PROVIDERS
    else -> throw IllegalArgumentException("Unsupported plugin type: $type")
}
```

- [ ] **Step 4: Run tests to verify GREEN**

Run: `./gradlew :shared:testDebugUnitTest`

Expected: `PluginServiceTest` compiles and parser tests pass.

---

### Task 2: PluginService Cache, Fallback, And Update API

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/plugin/PluginService.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/plugin/PluginServiceTest.kt`

- [ ] **Step 1: Add failing tests for cache fallback**

Append to `PluginServiceTest`:

```kotlin
    @Test
    fun promptLookupFallsBackToDefaultWhenCacheMissing() {
        val service = PluginService(FakeHttpClient.unused(), FakeSettingRepository())

        assertEquals("默认提示词", service.getPromptOrDefault(PROMPT_ARTICLE_SUMMARY, "默认提示词"))
    }

    @Test
    fun promptLookupUsesCachedRemotePromptWhenValid() {
        val settings = FakeSettingRepository(
            SETTING_PLUGIN_CONTENT_PREFIX + "prompts/article-summary.json" to
                """{"id":"article-summary","version":3,"content":"远程摘要提示词"}""",
        )
        val service = PluginService(FakeHttpClient.unused(), settings)

        assertEquals("远程摘要提示词", service.getPromptOrDefault(PROMPT_ARTICLE_SUMMARY, "默认提示词"))
    }

    @Test
    fun promptLookupIgnoresInvalidCachedPrompt() {
        val settings = FakeSettingRepository(
            SETTING_PLUGIN_CONTENT_PREFIX + "prompts/article-summary.json" to
                """{"id":"article-summary","version":3,"content":""}""",
        )
        val service = PluginService(FakeHttpClient.unused(), settings)

        assertEquals("默认提示词", service.getPromptOrDefault(PROMPT_ARTICLE_SUMMARY, "默认提示词"))
    }
```

Also add lightweight fakes at the bottom of the test file. If `SettingRepository` cannot be faked because it is final, skip service-instantiation tests and keep these behaviors as pure helper tests by extracting `promptFromCachedPlugins(...)` in Task 2 implementation.

- [ ] **Step 2: Run tests to verify RED**

Run: `./gradlew :shared:testDebugUnitTest`

Expected: compile fails because `getPromptOrDefault` is unresolved.

- [ ] **Step 3: Implement cache lookup and update methods**

Replace `PluginService.kt` with a focused implementation:

```kotlin
package com.dailysatori.service.plugin

import co.touchlab.kermit.Logger
import com.dailysatori.config.AiProvider
import com.dailysatori.data.repository.SettingRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.Clock

class PluginService(
    private val client: HttpClient,
    private val settingRepo: SettingRepository,
) {
    private val log = Logger.withTag("Plugin")

    val serverUrl: String get() = settingRepo.get(SETTING_PLUGIN_SERVER_URL) ?: ""

    fun getCachedIndex(): PluginIndex? {
        val content = settingRepo.get(SETTING_PLUGIN_INDEX_JSON) ?: return null
        return runCatching { parsePluginIndex(content) }.getOrNull()
    }

    fun getPromptOrDefault(key: String, default: String): String {
        return getCachedIndex()
            ?.plugins
            ?.asSequence()
            ?.filter { it.type == PluginType.PROMPT && it.id == key }
            ?.mapNotNull { entry -> cachedPrompt(entry.file, key) }
            ?.firstOrNull()
            ?: default
    }

    fun getAiProvidersOrDefault(defaults: List<AiProvider>): List<AiProvider> {
        val entry = getCachedIndex()?.plugins?.firstOrNull {
            it.type == PluginType.AI_PROVIDERS && it.id == PLUGIN_AI_PROVIDERS
        } ?: return defaults
        val content = settingRepo.get(SETTING_PLUGIN_CONTENT_PREFIX + entry.file) ?: return defaults
        return runCatching { parseAiProvidersPlugin(content).providers }.getOrDefault(defaults)
    }

    suspend fun updateIndex(): Result<PluginIndex> = runCatching {
        val baseUrl = normalizedServerUrl()
        require(baseUrl.isNotBlank()) { "插件服务器地址未配置" }
        val content = client.get("$baseUrl/$PLUGIN_INDEX_FILE").bodyAsText()
        val index = parsePluginIndex(content)
        settingRepo.upsert(SETTING_PLUGIN_INDEX_JSON, content)
        index
    }.onFailure { e -> log.e(e) { "Failed to update plugin index" } }

    suspend fun updatePlugin(entry: PluginEntry): Boolean {
        val baseUrl = normalizedServerUrl()
        if (baseUrl.isBlank()) return false
        return try {
            val content = client.get("$baseUrl/${entry.file}").bodyAsText()
            validatePluginContent(entry, content)
            settingRepo.upsert(SETTING_PLUGIN_CONTENT_PREFIX + entry.file, content)
            settingRepo.upsert(SETTING_PLUGIN_UPDATED_AT_PREFIX + entry.file, Clock.System.now().toEpochMilliseconds().toString())
            true
        } catch (e: Exception) {
            log.e(e) { "Failed to update plugin file: ${entry.file}" }
            false
        }
    }

    suspend fun updateAll(): PluginUpdateResult {
        val index = updateIndex().getOrElse { return PluginUpdateResult(emptyList(), listOf(PLUGIN_INDEX_FILE)) }
        val updated = mutableListOf<String>()
        val failed = mutableListOf<String>()
        index.plugins.forEach { entry ->
            if (updatePlugin(entry)) updated += entry.file else failed += entry.file
        }
        return PluginUpdateResult(updated = updated, failed = failed)
    }

    fun saveServerUrl(url: String) {
        settingRepo.upsert(SETTING_PLUGIN_SERVER_URL, url.trim().trimEnd('/'))
    }

    private fun cachedPrompt(file: String, expectedId: String): String? {
        val content = settingRepo.get(SETTING_PLUGIN_CONTENT_PREFIX + file) ?: return null
        return runCatching { parsePromptPlugin(content) }
            .getOrNull()
            ?.takeIf { it.id == expectedId }
            ?.content
    }

    private fun validatePluginContent(entry: PluginEntry, content: String) {
        when (entry.type) {
            PluginType.PROMPT -> require(parsePromptPlugin(content).id == entry.id) { "Prompt id mismatch" }
            PluginType.AI_PROVIDERS -> parseAiProvidersPlugin(content)
        }
    }

    private fun normalizedServerUrl(): String = serverUrl.trim().trimEnd('/')
}
```

- [ ] **Step 4: Run tests to verify GREEN**

Run: `./gradlew :shared:testDebugUnitTest`

Expected: plugin service tests pass. If fake repository construction is impractical, revise tests to target pure parsing/helpers only and add service coverage in app/shared integration tests later.

---

### Task 3: Wire Remote Prompts Into Article Processing

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/parser/ArticleParserPrompts.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/parser/ArticleProcessingContentTest.kt`

- [ ] **Step 1: Write failing prompt key test**

Append to `ArticleProcessingContentTest`:

```kotlin
    @Test
    fun articlePromptKeysMatchRemotePluginIds() {
        assertEquals("article-summary", ARTICLE_SUMMARY_PROMPT_KEY)
        assertEquals("article-markdown", ARTICLE_MARKDOWN_PROMPT_KEY)
    }
```

- [ ] **Step 2: Run tests to verify RED**

Run: `./gradlew :shared:testDebugUnitTest`

Expected: compile fails because `ARTICLE_SUMMARY_PROMPT_KEY` and `ARTICLE_MARKDOWN_PROMPT_KEY` are unresolved.

- [ ] **Step 3: Add prompt key constants**

At the top of `ArticleParserPrompts.kt` after the package line:

```kotlin
internal const val ARTICLE_SUMMARY_PROMPT_KEY = "article-summary"
internal const val ARTICLE_MARKDOWN_PROMPT_KEY = "article-markdown"
```

- [ ] **Step 4: Inject PluginService into WebpageParserService**

Modify constructor in `WebpageParserService.kt`:

```kotlin
import com.dailysatori.service.plugin.PluginService

class WebpageParserService(
    private val articleRepo: ArticleRepository,
    private val tagRepo: TagRepository,
    private val imageRepo: ImageRepository,
    private val aiService: AiService,
    private val aiConfigService: AiConfigService,
    private val pluginService: PluginService,
    private val webViewLoader: WebViewLoader,
    private val fileManager: FileManager,
    private val httpClient: HttpClient,
) {
```

Change summary prompt call:

```kotlin
pluginService.getPromptOrDefault(ARTICLE_SUMMARY_PROMPT_KEY, articleSummaryPrompt())
```

Change Markdown prompt call:

```kotlin
pluginService.getPromptOrDefault(ARTICLE_MARKDOWN_PROMPT_KEY, htmlToReadableMarkdownPrompt())
```

Modify `SharedModule.kt` WebpageParserService binding:

```kotlin
single { WebpageParserService(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
```

- [ ] **Step 5: Run tests and compile**

Run: `./gradlew :shared:testDebugUnitTest`

Expected: tests pass.

Run: `./gradlew :app:compileDebugKotlin`

Expected: compile succeeds. If constructor call count fails, update the exact Koin binding with the additional `PluginService` dependency.

---

### Task 4: Remote AI Provider Presets In AI Config UI

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/config/AiProviderModels.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigEditScreen.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/plugin/PluginServiceTest.kt`

- [ ] **Step 1: Write failing provider fallback test**

Append to `PluginServiceTest`:

```kotlin
    @Test
    fun invalidAiProviderPluginFallsBackToBuiltInProviders() {
        val defaults = listOf(com.dailysatori.config.AiProvider("builtin", "Built In", "https://example.com", emptyList()))
        val result = runCatching { parseAiProvidersPlugin("""{"version":1,"providers":[]}""") }

        assertEquals(true, result.isFailure)
        assertEquals("builtin", defaults.single().id)
    }
```

- [ ] **Step 2: Run tests to verify RED or existing parser behavior**

Run: `./gradlew :shared:testDebugUnitTest`

Expected: pass if Task 1 already rejects empty providers. If it passes immediately, keep it as regression coverage and proceed because Task 4 primarily wires UI behavior.

- [ ] **Step 3: Add built-in provider helper**

At the bottom of `AiProviderModels.kt`, keep existing public list and add:

```kotlin
fun builtInAiProviders(): List<AiProvider> = aiProviders
```

- [ ] **Step 4: Load providers from PluginService in AI config editor**

Modify imports in `AiConfigEditScreen.kt`:

```kotlin
import com.dailysatori.config.builtInAiProviders
import com.dailysatori.service.plugin.PluginService
```

Replace direct `aiProviders` usage with state:

```kotlin
val pluginService = remember { KoinPlatform.getKoin().get<PluginService>() }
var providerPresets by remember { mutableStateOf(builtInAiProviders()) }

LaunchedEffect(Unit) {
    providerPresets = withContext(Dispatchers.IO) {
        pluginService.getAiProvidersOrDefault(builtInAiProviders())
    }
}
```

Change provider lookup in edit load:

```kotlin
selectedProvider = providerPresets.find { it.id == config.provider }
```

Change dropdown iteration:

```kotlin
providerPresets.forEach { provider ->
```

- [ ] **Step 5: Compile AI config UI**

Run: `./gradlew :app:compileDebugKotlin`

Expected: compile succeeds and no unresolved `aiProviders` import remains in `AiConfigEditScreen.kt`.

---

### Task 5: Plugin Center UI And ViewModel Updates

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/plugin/PluginCenterViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/plugin/PluginCenterScreen.kt`

- [ ] **Step 1: Update view model state and methods**

Replace `PluginInfo` and state in `PluginCenterViewModel.kt` with:

```kotlin
data class PluginInfo(
    val id: String,
    val type: String,
    val fileName: String,
    val name: String,
    val version: Int,
    val contentLength: Int,
    val lastUpdated: String = "",
)

data class PluginCenterState(
    val plugins: List<PluginInfo> = emptyList(),
    val isLoading: Boolean = false,
    val updatingPluginId: String = "",
    val serverUrl: String = "",
    val error: String? = null,
)
```

Implement `loadPlugins()` using `pluginService.getCachedIndex()` and `settingRepo.get(...)`:

```kotlin
fun loadPlugins() {
    viewModelScope.launch(Dispatchers.IO) {
        _state.update { it.copy(isLoading = true, error = null) }
        try {
            val serverUrl = settingRepo.get(SETTING_PLUGIN_SERVER_URL) ?: ""
            val index = pluginService.getCachedIndex()
            val plugins = index?.plugins.orEmpty().map { entry ->
                val content = settingRepo.get(SETTING_PLUGIN_CONTENT_PREFIX + entry.file).orEmpty()
                PluginInfo(
                    id = entry.id,
                    type = entry.type.name,
                    fileName = entry.file,
                    name = entry.name,
                    version = entry.version,
                    contentLength = content.length,
                    lastUpdated = settingRepo.get(SETTING_PLUGIN_UPDATED_AT_PREFIX + entry.file).orEmpty(),
                )
            }
            _state.update { it.copy(plugins = plugins, serverUrl = serverUrl, isLoading = false) }
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message, isLoading = false) }
        }
    }
}
```

Update all actions:

```kotlin
fun updatePlugin(fileName: String) {
    viewModelScope.launch(Dispatchers.IO) {
        _state.update { it.copy(updatingPluginId = fileName, error = null) }
        val entry = pluginService.getCachedIndex()?.plugins?.firstOrNull { it.file == fileName }
        val ok = entry != null && pluginService.updatePlugin(entry)
        if (!ok) _state.update { it.copy(error = "插件更新失败：$fileName") }
        _state.update { it.copy(updatingPluginId = "") }
        loadPlugins()
    }
}

fun updateAllPlugins() {
    viewModelScope.launch(Dispatchers.IO) {
        _state.update { it.copy(isLoading = true, error = null) }
        val result = pluginService.updateAll()
        val error = if (result.failed.isEmpty()) null else "部分插件更新失败：${result.failed.joinToString()}"
        _state.update { it.copy(error = error, updatingPluginId = "", isLoading = false) }
        loadPlugins()
    }
}

fun saveServerUrl(url: String) {
    pluginService.saveServerUrl(url)
    _state.update { it.copy(serverUrl = url.trim().trimEnd('/')) }
}
```

- [ ] **Step 2: Update Plugin Center UI**

In `PluginCenterScreen.kt`, add imports:

```kotlin
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
```

At the top of content block before loading/empty checks, add a URL editor section:

```kotlin
var serverUrlText by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }

Column(modifier = modifier.fillMaxSize().padding(Spacing.m)) {
    OutlinedTextField(
        value = serverUrlText,
        onValueChange = { serverUrlText = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("插件服务器地址") },
        placeholder = { Text("https://example.com/daily-plugins") },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { viewModel.saveServerUrl(serverUrlText) }, modifier = Modifier.weight(1f)) { Text("保存地址") }
        Button(onClick = { viewModel.updateAllPlugins() }, modifier = Modifier.weight(1f)) { Text("更新全部") }
    }
    Spacer(modifier = Modifier.height(Spacing.m))
    // Move the existing loading/empty/list UI inside this Column and use Modifier.weight(1f).
}
```

Update card text:

```kotlin
Text(plugin.name, style = MaterialTheme.typography.titleSmall)
Text("${plugin.type} · v${plugin.version} · ${plugin.fileName}", style = MaterialTheme.typography.bodySmall)
Text("${plugin.contentLength} 字符", style = MaterialTheme.typography.bodySmall)
```

Update empty subtitle logic:

```kotlin
subtitle = if (state.serverUrl.isBlank()) "请先配置插件服务器地址" else "未缓存有效插件，请点击更新全部"
```

- [ ] **Step 3: Compile UI**

Run: `./gradlew :app:compileDebugKotlin`

Expected: compile succeeds. Fix only import/layout compile errors introduced by this task.

---

### Task 6: Final Verification

**Files:**
- Verify all modified files.

- [ ] **Step 1: Run shared tests**

Run: `./gradlew :shared:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run app compile check**

Run: `./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`. Existing deprecation warnings are acceptable; new errors are not.

- [ ] **Step 3: Install when a device is connected**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`

Expected when device is connected: `BUILD SUCCESSFUL` and app installed. If no device is connected, record the exact `No connected devices!` failure and do not claim install verification passed.

- [ ] **Step 4: Manual sanity check**

Open Plugin Center and verify:

- Empty server URL shows local/default behavior message.
- Saving a URL persists it.
- Failed update shows an error and does not crash.
- AI config screen still shows built-in providers if no valid remote provider plugin is cached.
- Article processing still runs with built-in prompts if no valid remote prompts are cached.

---

## Self-Review

Spec coverage:

- Remote `index.json` and independent plugin files: Task 1 and Task 2.
- Prompt plugin and AI provider plugin types: Task 1.
- Local fallback for unavailable/invalid remote data: Task 2, Task 3, Task 4.
- Plugin Center URL/update/list UI: Task 5.
- Article summary and Markdown prompt integration: Task 3.
- AI config provider/model integration: Task 4.
- Validation and failed update preservation: Task 1 and Task 2.
- Verification commands: Task 6.

Placeholder scan:

- No `TBD`, `TODO`, or unspecified implementation steps remain.
- The only conditional instruction is for no connected device during install verification, which is an environment-dependent verification outcome.

Type consistency:

- `PluginType.PROMPT` and `PluginType.AI_PROVIDERS` are defined in Task 1 and used in later tasks.
- Prompt keys are `article-summary` and `article-markdown` consistently across spec and plan.
- Setting keys match the spec and are centralized in `PluginModels.kt`.

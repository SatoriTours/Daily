# MCP Provider Presets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a provider-based MCP add flow where users select GLM, DeepSeek, MiniMax, or ChatGPT/OpenAI presets, enter one API key, select multiple MCP services, and save complete MCP configuration metadata.

**Architecture:** Add a shared MCP provider catalog with normal and coding-plan template types, migrate `mcp_server` to store provider/template metadata plus config JSON, then update the Android settings UI to support batch preset add while preserving manual add/edit. Keep preset knowledge in one shared file so later provider fixes do not touch UI code.

**Tech Stack:** Kotlin Multiplatform, SQLDelight, Jetpack Compose Material 3, Koin, kotlin.test.

---

## File Structure

- Create `shared/src/commonMain/kotlin/com/dailysatori/config/McpProviderCatalog.kt`: provider/template definitions, config JSON rendering, grouping helpers, duplicate key helpers.
- Create `shared/src/commonTest/kotlin/com/dailysatori/config/McpProviderCatalogTest.kt`: verifies provider coverage, category separation, config JSON placeholders, and duplicate filtering.
- Modify `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`: add new `mcp_server` columns and queries for preset insert and duplicate lookup.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt`: bump schema version from `5L` to `6L`.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt`: add V5 to V6 migration for new MCP columns.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/data/repository/McpServerRepository.kt`: expose duplicate lookup and preset insert while keeping existing manual insert/update.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/settings/McpServerScreen.kt`: add preset add screen, provider dropdown, grouped checkbox list, batch save result, and manual add fallback.
- Keep `docs/superpowers/specs/2026-05-04-mcp-provider-presets-design.md` as the source design reference.

## Commit Policy

This repository instruction says commits require explicit user approval. During execution, treat each listed commit checkpoint as a pause point and ask the user before running `git commit`.

---

### Task 1: Shared MCP Provider Catalog

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/config/McpProviderCatalog.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/config/McpProviderCatalogTest.kt`

- [ ] **Step 1: Write the failing catalog tests**

Create `shared/src/commonTest/kotlin/com/dailysatori/config/McpProviderCatalogTest.kt`:

```kotlin
package com.dailysatori.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class McpProviderCatalogTest {
    @Test
    fun includesRequestedProviders() {
        val ids = mcpProviders.map { it.id }

        assertTrue(ids.contains("glm"))
        assertTrue(ids.contains("deepseek"))
        assertTrue(ids.contains("minimax"))
        assertTrue(ids.contains("openai"))
    }

    @Test
    fun separatesNormalAndCodingPlanTemplates() {
        val glm = findMcpProvider("glm")

        assertNotNull(glm)
        assertTrue(glm.templates.any { it.type == McpTemplateType.NORMAL })
        assertTrue(glm.templates.any { it.type == McpTemplateType.CODING_PLAN })
    }

    @Test
    fun buildsDisplayNameFromProviderAndTemplate() {
        val provider = findMcpProvider("minimax")!!
        val template = provider.templates.first { it.id == "minimax-coding-plan" }

        assertEquals("MiniMax / Coding Plan", mcpTemplateDisplayName(provider, template))
    }

    @Test
    fun rendersConfigJsonWithApiKeyPlaceholder() {
        val provider = findMcpProvider("glm")!!
        val template = provider.templates.first { it.id == "glm-web-search" }
        val json = renderMcpConfigJson(template)

        assertTrue(json.contains("https://open.bigmodel.cn/api/mcp/web_search_prime/mcp"))
        assertTrue(json.contains("Bearer "))
        assertTrue(json.contains("\${apiKey}"))
        assertFalse(json.contains("sk-real-secret"))
    }

    @Test
    fun filtersTemplatesThatAlreadyExistByServerUrl() {
        val provider = findMcpProvider("glm")!!
        val existingUrls = setOf("https://open.bigmodel.cn/api/mcp/web_reader/mcp")
        val addable = filterNewMcpTemplates(provider.templates, existingUrls)

        assertFalse(addable.any { it.serverUrl == "https://open.bigmodel.cn/api/mcp/web_reader/mcp" })
        assertTrue(addable.any { it.serverUrl == "https://open.bigmodel.cn/api/mcp/web_search_prime/mcp" })
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:allTests --tests "com.dailysatori.config.McpProviderCatalogTest"`

Expected: FAIL because `McpProviderCatalog.kt` does not exist and symbols like `mcpProviders` are unresolved.

- [ ] **Step 3: Implement the catalog**

Create `shared/src/commonMain/kotlin/com/dailysatori/config/McpProviderCatalog.kt`:

```kotlin
package com.dailysatori.config

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class McpTemplateType(val displayName: String) {
    NORMAL("普通 MCP"),
    CODING_PLAN("Coding Plan MCP"),
}

data class McpProvider(
    val id: String,
    val name: String,
    val apiKeyPlaceholder: String,
    val templates: List<McpTemplate>,
)

data class McpTemplate(
    val id: String,
    val name: String,
    val description: String,
    val type: McpTemplateType,
    val transport: String,
    val serverUrl: String,
    val command: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
)

private val mcpJson = Json { prettyPrint = false }

val mcpProviders: List<McpProvider> = listOf(
    McpProvider(
        id = "glm",
        name = "GLM / 智谱",
        apiKeyPlaceholder = "请输入智谱 API Key",
        templates = listOf(
            McpTemplate(
                id = "glm-web-search",
                name = "联网搜索",
                description = "GLM Coding Plan 远程搜索 MCP，提供 webSearchPrime 工具。",
                type = McpTemplateType.CODING_PLAN,
                transport = "remote",
                serverUrl = "https://open.bigmodel.cn/api/mcp/web_search_prime/mcp",
                env = mapOf("Authorization" to "Bearer \${apiKey}"),
            ),
            McpTemplate(
                id = "glm-web-reader",
                name = "网页读取",
                description = "GLM Coding Plan 远程网页读取 MCP，提供 webReader 工具。",
                type = McpTemplateType.CODING_PLAN,
                transport = "remote",
                serverUrl = "https://open.bigmodel.cn/api/mcp/web_reader/mcp",
                env = mapOf("Authorization" to "Bearer \${apiKey}"),
            ),
            McpTemplate(
                id = "glm-zread",
                name = "开源仓库读取",
                description = "GLM Coding Plan ZRead MCP，支持搜索仓库文档、结构和文件。",
                type = McpTemplateType.CODING_PLAN,
                transport = "remote",
                serverUrl = "https://open.bigmodel.cn/api/mcp/zread/mcp",
                env = mapOf("Authorization" to "Bearer \${apiKey}"),
            ),
            McpTemplate(
                id = "glm-vision",
                name = "视觉理解",
                description = "GLM Coding Plan 本地视觉 MCP，使用 @z_ai/mcp-server。",
                type = McpTemplateType.CODING_PLAN,
                transport = "local",
                serverUrl = "npx -y @z_ai/mcp-server",
                command = listOf("npx", "-y", "@z_ai/mcp-server"),
                env = mapOf("Z_AI_API_KEY" to "\${apiKey}", "Z_AI_MODE" to "ZHIPU"),
            ),
            McpTemplate(
                id = "glm-compatible-api",
                name = "OpenAI 兼容 API",
                description = "GLM 通用 OpenAI 兼容配置，用于普通模型接口场景。",
                type = McpTemplateType.NORMAL,
                transport = "remote",
                serverUrl = "https://open.bigmodel.cn/api/paas/v4",
                env = mapOf("Authorization" to "Bearer \${apiKey}"),
            ),
        ),
    ),
    McpProvider(
        id = "minimax",
        name = "MiniMax",
        apiKeyPlaceholder = "请输入 MiniMax API Key",
        templates = listOf(
            McpTemplate(
                id = "minimax-multimodal",
                name = "多模态生成",
                description = "MiniMax 官方 MCP，支持语音、图片、视频、音乐等能力。",
                type = McpTemplateType.NORMAL,
                transport = "local",
                serverUrl = "uvx minimax-mcp -y",
                command = listOf("uvx", "minimax-mcp", "-y"),
                env = mapOf(
                    "MINIMAX_API_KEY" to "\${apiKey}",
                    "MINIMAX_API_HOST" to "https://api.minimax.io",
                    "MINIMAX_MCP_BASE_PATH" to "",
                    "MINIMAX_API_RESOURCE_MODE" to "url",
                ),
            ),
            McpTemplate(
                id = "minimax-coding-plan",
                name = "Coding Plan",
                description = "MiniMax Coding Plan MCP，提供 web_search 和 understand_image。",
                type = McpTemplateType.CODING_PLAN,
                transport = "local",
                serverUrl = "uvx minimax-coding-plan-mcp -y",
                command = listOf("uvx", "minimax-coding-plan-mcp", "-y"),
                env = mapOf("MINIMAX_API_KEY" to "\${apiKey}", "MINIMAX_API_HOST" to "https://api.minimax.io"),
            ),
        ),
    ),
    McpProvider(
        id = "deepseek",
        name = "DeepSeek",
        apiKeyPlaceholder = "请输入 DeepSeek MCP Token",
        templates = listOf(
            McpTemplate(
                id = "deepseek-remote",
                name = "DeepSeek MCP",
                description = "DeepSeek 远程 MCP，支持模型、补全和余额等能力。",
                type = McpTemplateType.NORMAL,
                transport = "remote",
                serverUrl = "https://deepseek-mcp.ragweld.com/mcp",
                env = mapOf("Authorization" to "Bearer \${apiKey}"),
            ),
        ),
    ),
    McpProvider(
        id = "openai",
        name = "ChatGPT / OpenAI",
        apiKeyPlaceholder = "请输入 OpenAI API Key",
        templates = listOf(
            McpTemplate(
                id = "openai-deep-research",
                name = "Deep Research MCP 模板",
                description = "OpenAI Deep Research 兼容 MCP 模板，服务端需实现 search 和 fetch。",
                type = McpTemplateType.NORMAL,
                transport = "remote",
                serverUrl = "https://platform.openai.com/docs/mcp",
                env = mapOf("Authorization" to "Bearer \${apiKey}"),
            ),
        ),
    ),
)

fun findMcpProvider(id: String): McpProvider? = mcpProviders.find { it.id == id }

fun mcpTemplateDisplayName(provider: McpProvider, template: McpTemplate): String =
    "${provider.name} / ${template.name}"

fun renderMcpConfigJson(template: McpTemplate): String = mcpJson.encodeToString(
    mapOf(
        "transport" to template.transport,
        "url" to template.serverUrl,
        "command" to template.command,
        "env" to template.env,
    ),
)

fun filterNewMcpTemplates(
    templates: List<McpTemplate>,
    existingServerUrls: Set<String>,
): List<McpTemplate> = templates.filterNot { existingServerUrls.contains(it.serverUrl) }
```

- [ ] **Step 4: Run catalog tests**

Run: `./gradlew :shared:allTests --tests "com.dailysatori.config.McpProviderCatalogTest"`

Expected: PASS.

- [ ] **Step 5: Commit checkpoint**

Ask the user whether to commit. If approved, run:

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/config/McpProviderCatalog.kt shared/src/commonTest/kotlin/com/dailysatori/config/McpProviderCatalogTest.kt
git commit -m "feat: add mcp provider presets"
```

---

### Task 2: Persist MCP Preset Metadata

**Files:**
- Modify: `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq:89-97,344-363`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt:23-26`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt:16-40,180-216`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/McpServerRepository.kt:11-43`

- [ ] **Step 1: Update SQLDelight schema and queries**

In `DailySatori.sq`, change the `mcp_server` table to include preset metadata:

```sql
CREATE TABLE mcp_server (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    server_url TEXT NOT NULL,
    api_key TEXT NOT NULL DEFAULT '',
    enabled INTEGER NOT NULL DEFAULT 1,
    provider TEXT NOT NULL DEFAULT '',
    template_id TEXT NOT NULL DEFAULT '',
    template_type TEXT NOT NULL DEFAULT '',
    config_json TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

Replace MCP queries with:

```sql
-- MCP Server queries
selectAllMcpServers:
SELECT * FROM mcp_server ORDER BY name;

selectMcpServerById:
SELECT * FROM mcp_server WHERE id = ?;

selectMcpServerByUrl:
SELECT * FROM mcp_server WHERE server_url = ? LIMIT 1;

selectEnabledMcpServers:
SELECT * FROM mcp_server WHERE enabled = 1;

insertMcpServer:
INSERT INTO mcp_server (name, server_url, api_key, enabled, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?);

insertMcpServerPreset:
INSERT INTO mcp_server (name, server_url, api_key, enabled, provider, template_id, template_type, config_json, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

updateMcpServer:
UPDATE mcp_server SET name = ?, server_url = ?, api_key = ?, enabled = ?, updated_at = ?
WHERE id = ?;

deleteMcpServer:
DELETE FROM mcp_server WHERE id = ?;
```

- [ ] **Step 2: Bump schema version**

In `Config.kt`, change:

```kotlin
object DatabaseConfig {
    const val name = "daily_satori.db"
    const val currentSchemaVersion = 6L
}
```

- [ ] **Step 3: Add V5 to V6 migration**

In `DatabaseMigration.kt`, add this after the V4 to V5 block in `runMigrations()`:

```kotlin
if (currentVersion < 6) {
    migrateV5ToV6()
}
```

Add this method before `runSql`:

```kotlin
/**
 * V5 -> V6: Add MCP provider preset metadata.
 */
private fun migrateV5ToV6() {
    log.i { "Migration V5 -> V6: MCP preset metadata" }
    val columns = listOf(
        "provider TEXT NOT NULL DEFAULT ''",
        "template_id TEXT NOT NULL DEFAULT ''",
        "template_type TEXT NOT NULL DEFAULT ''",
        "config_json TEXT NOT NULL DEFAULT ''",
    )
    columns.forEach { column ->
        try {
            runSql("ALTER TABLE mcp_server ADD COLUMN $column")
            log.i { "Added mcp_server column: $column" }
        } catch (e: Exception) {
            log.w(e) { "Could not add mcp_server column: $column" }
        }
    }
}
```

- [ ] **Step 4: Update repository methods**

In `McpServerRepository.kt`, add these methods while keeping existing `insert`, `update`, and `delete`:

```kotlin
fun getByServerUrl(serverUrl: String) = q.selectMcpServerByUrl(serverUrl).executeAsOneOrNull()

fun insertPreset(
    name: String,
    serverUrl: String,
    apiKey: String,
    provider: String,
    templateId: String,
    templateType: String,
    configJson: String,
    enabled: Long = 1,
) {
    val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
    q.insertMcpServerPreset(name, serverUrl, apiKey, enabled, provider, templateId, templateType, configJson, now, now)
}
```

- [ ] **Step 5: Compile shared sources**

Run: `./gradlew :shared:compileKotlinAndroid`

Expected: PASS. If SQLDelight generation changes method argument names, update `McpServerRepository.kt` to match the generated query signatures.

- [ ] **Step 6: Commit checkpoint**

Ask the user whether to commit. If approved, run:

```bash
git add shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt shared/src/commonMain/kotlin/com/dailysatori/data/repository/McpServerRepository.kt
git commit -m "feat: persist mcp preset metadata"
```

---

### Task 3: Provider-Based Add UI

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/McpServerScreen.kt:1-244`

- [ ] **Step 1: Add imports**

Add these imports to `McpServerScreen.kt`:

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumnScope
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import com.dailysatori.config.McpProvider
import com.dailysatori.config.McpTemplate
import com.dailysatori.config.McpTemplateType
import com.dailysatori.config.filterNewMcpTemplates
import com.dailysatori.config.mcpProviders
import com.dailysatori.config.mcpTemplateDisplayName
import com.dailysatori.config.renderMcpConfigJson
import kotlinx.coroutines.withContext
```

- [ ] **Step 2: Replace edit state with screen mode**

Add this enum near the top of the file:

```kotlin
private enum class McpScreenMode {
    LIST,
    PRESET_ADD,
    MANUAL_ADD,
}
```

In `McpServerScreen`, replace `showEdit` with:

```kotlin
var mode by remember { mutableStateOf(McpScreenMode.LIST) }
var editingServerId by remember { mutableStateOf<Long?>(null) }
```

Use these early returns:

```kotlin
if (mode == McpScreenMode.PRESET_ADD) {
    McpServerPresetAddScreen(
        onBack = { mode = McpScreenMode.LIST },
        onManualAdd = {
            editingServerId = -1L
            mode = McpScreenMode.MANUAL_ADD
        },
    )
    return
}

if (mode == McpScreenMode.MANUAL_ADD || editingServerId != null) {
    McpServerEditScreen(
        serverId = editingServerId,
        onBack = {
            editingServerId = null
            mode = McpScreenMode.LIST
        },
    )
    return
}
```

Update the floating add button:

```kotlin
FloatingActionButton(
    onClick = { mode = McpScreenMode.PRESET_ADD },
    containerColor = MaterialTheme.colorScheme.primary,
) {
    Icon(Icons.Default.Add, contentDescription = "添加 MCP 服务")
}
```

Update existing card click:

```kotlin
Card(
    onClick = { editingServerId = server.id },
    shape = RoundedCornerShape(Radius.m),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
) {
```

- [ ] **Step 3: Add preset add screen shell**

Add this composable before `McpServerEditScreen`:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun McpServerPresetAddScreen(
    onBack: () -> Unit,
    onManualAdd: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val repo = remember { KoinPlatform.getKoin().get<McpServerRepository>() }

    var selectedProvider by remember { mutableStateOf<McpProvider?>(mcpProviders.firstOrNull()) }
    var providerExpanded by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }
    var selectedTemplateIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSaving by remember { mutableStateOf(false) }
    var existingUrls by remember { mutableStateOf<Set<String>>(emptySet()) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        repo.getAll().collect { servers -> existingUrls = servers.map { it.server_url }.toSet() }
    }

    val provider = selectedProvider
    val addableTemplates = provider?.let { filterNewMcpTemplates(it.templates, existingUrls) } ?: emptyList()
    val selectedTemplates = addableTemplates.filter { selectedTemplateIds.contains(it.id) }

    AppScaffold(
        title = "添加 MCP 服务",
        onBack = onBack,
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.m),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                OutlinedButton(onClick = onManualAdd, modifier = Modifier.weight(1f)) { Text("手动添加") }
                Button(
                    onClick = {
                        val currentProvider = provider ?: return@Button
                        scope.launch {
                            isSaving = true
                            val result = withContext(Dispatchers.IO) {
                                saveSelectedMcpTemplates(repo, currentProvider, selectedTemplates, apiKey.trim())
                            }
                            isSaving = false
                            saveMessage = "已添加 ${result.added} 个 MCP，跳过 ${result.skipped} 个已存在服务"
                            if (result.added > 0) onBack()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving && provider != null && apiKey.isNotBlank() && selectedTemplates.isNotEmpty(),
                ) { Text(if (isSaving) "添加中..." else "添加选中 MCP") }
            }
        },
    ) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(horizontal = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            contentPadding = PaddingValues(vertical = Spacing.m),
        ) {
            item {
                Text("选择服务商", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(Spacing.xs))
                ExposedDropdownMenuBox(
                    expanded = providerExpanded,
                    onExpandedChange = { providerExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedProvider?.name ?: "请选择服务商",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                        shape = RoundedCornerShape(Radius.s),
                        singleLine = true,
                    )
                    ExposedDropdownMenu(
                        expanded = providerExpanded,
                        onDismissRequest = { providerExpanded = false },
                    ) {
                        mcpProviders.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.name) },
                                onClick = {
                                    selectedProvider = item
                                    selectedTemplateIds = emptySet()
                                    providerExpanded = false
                                },
                            )
                        }
                    }
                }
            }
            item {
                Text("API Key", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(Spacing.xs))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(selectedProvider?.apiKeyPlaceholder ?: "请输入 API Key") },
                    shape = RoundedCornerShape(Radius.s),
                    singleLine = true,
                )
            }
            addTemplateGroup(McpTemplateType.NORMAL, addableTemplates, selectedTemplateIds) { selectedTemplateIds = it }
            addTemplateGroup(McpTemplateType.CODING_PLAN, addableTemplates, selectedTemplateIds) { selectedTemplateIds = it }
            saveMessage?.let { message ->
                item { Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
            if (provider != null && addableTemplates.isEmpty()) {
                item { Text("该服务商的预设 MCP 都已添加", style = MaterialTheme.typography.bodyMedium) }
            }
            item { Spacer(modifier = Modifier.height(Spacing.xl)) }
        }
    }
}
```

- [ ] **Step 4: Add grouped template list helper**

Add this helper below `McpServerPresetAddScreen`:

```kotlin
private fun LazyColumnScope.addTemplateGroup(
    type: McpTemplateType,
    templates: List<McpTemplate>,
    selectedTemplateIds: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
) {
    val group = templates.filter { it.type == type }
    if (group.isEmpty()) return

    item {
        Text(type.displayName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
    items(group, key = { it.id }) { template ->
        McpTemplateRow(
            template = template,
            checked = selectedTemplateIds.contains(template.id),
            onCheckedChange = { checked ->
                onSelectionChange(
                    if (checked) selectedTemplateIds + template.id else selectedTemplateIds - template.id,
                )
            },
        )
    }
}

@Composable
private fun McpTemplateRow(
    template: McpTemplate,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(Radius.m),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Spacer(modifier = Modifier.width(Spacing.s))
            Column(modifier = Modifier.weight(1f)) {
                Text(template.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    template.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    template.serverUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
```

- [ ] **Step 5: Add batch save helper**

Add this below the row composable:

```kotlin
private data class McpBatchSaveResult(val added: Int, val skipped: Int)

private fun saveSelectedMcpTemplates(
    repo: McpServerRepository,
    provider: McpProvider,
    templates: List<McpTemplate>,
    apiKey: String,
): McpBatchSaveResult {
    var added = 0
    var skipped = 0
    templates.forEach { template ->
        if (repo.getByServerUrl(template.serverUrl) != null) {
            skipped += 1
        } else {
            repo.insertPreset(
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
    return McpBatchSaveResult(added = added, skipped = skipped)
}
```

- [ ] **Step 6: Compile app UI**

Run: `./gradlew :app:compileDebugKotlin`

Expected: PASS.

- [ ] **Step 7: Commit checkpoint**

Ask the user whether to commit. If approved, run:

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/settings/McpServerScreen.kt
git commit -m "feat: add mcp provider preset picker"
```

---

### Task 4: Final Verification

**Files:**
- Modify only if verification exposes compile or behavior issues.

- [ ] **Step 1: Run shared tests**

Run: `./gradlew :shared:allTests --tests "com.dailysatori.config.McpProviderCatalogTest"`

Expected: PASS.

- [ ] **Step 2: Run required compile check**

Run: `./gradlew :app:compileDebugKotlin`

Expected: PASS.

- [ ] **Step 3: Install debug build to connected device**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`

Expected: `BUILD SUCCESSFUL` and the debug APK installed.

- [ ] **Step 4: Launch app**

Run: `adb shell am start -n com.dailysatori/.MainActivity`

Expected: Android reports the activity start without an error.

- [ ] **Step 5: Manual smoke test**

In the app, verify:

1. Settings opens `MCP 服务`.
2. `+` opens provider preset add screen.
3. GLM shows separate `普通 MCP` and `Coding Plan MCP` sections.
4. MiniMax shows normal multimodal and Coding Plan entries separately.
5. Entering one API key and selecting multiple templates saves multiple rows.
6. Adding the same provider templates again skips existing `server_url` entries.
7. `手动添加` still opens the manual form.
8. Existing saved MCP rows can still be edited and toggled.

- [ ] **Step 6: Final status**

Run: `git status --short`

Expected: only files intentionally modified by these tasks are listed.

- [ ] **Step 7: Final commit checkpoint**

Ask the user whether to commit remaining changes. If approved, run:

```bash
git add docs/superpowers/specs/2026-05-04-mcp-provider-presets-design.md docs/superpowers/plans/2026-05-04-mcp-provider-presets.md
git commit -m "docs: plan mcp provider presets"
```

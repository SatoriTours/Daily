# Agent Skills Management Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a generic Skills configuration system with WeRead as a built-in, non-deletable Skill while keeping the existing book runtime working from the new Skill token storage.

**Architecture:** Phase 1 creates the `skill_config` persistence layer, repository, DI, and Settings UI. WeRead remains the only runtime-integrated Skill for now, but its token is read from `skill_config` with fallback migration from the legacy `weread_api_key` setting. Later phases can build scenario agents and expose enabled Skills as AI tools on top of this storage and UI.

**Tech Stack:** Kotlin Multiplatform, SQLDelight, Koin, SecretCipher, Jetpack Compose Material 3, kotlinx.coroutines Flow, Gradle Android unit tests.

---

## File Structure

- Modify `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`: add `skill_config` table and SQLDelight queries.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt`: bump schema version and add Skill constants.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt`: add V8 -> V9 migration, create table, insert built-in WeRead, migrate legacy token.
- Create `shared/src/commonMain/kotlin/com/dailysatori/data/repository/SkillConfigRepository.kt`: encrypted CRUD and built-in delete protection.
- Create `shared/src/commonMain/kotlin/com/dailysatori/service/skill/SkillConfigModels.kt`: constants and pure helpers for built-in WeRead metadata and UI labels.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`: register repository.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/book/WeReadSkillService.kt`: read WeRead token from `SkillConfigRepository`, fallback-upgrade legacy setting.
- Create `app/src/main/kotlin/com/dailysatori/ui/feature/settings/skills/SkillSettingsViewModel.kt`: list/edit state and save/delete behavior.
- Create `app/src/main/kotlin/com/dailysatori/ui/feature/settings/skills/SkillSettingsScreen.kt`: Skills list and edit UI.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt`: replace WeRead settings row with Skills.
- Modify `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`: register Skills ViewModel and remove WeRead-specific ViewModel registration if unused.
- Keep `app/src/main/kotlin/com/dailysatori/ui/feature/settings/weread/*` only until no references remain, then delete it in Task 5.
- Add tests under `shared/src/commonTest/kotlin/com/dailysatori/service/skill/`, `shared/src/commonTest/kotlin/com/dailysatori/data/repository/`, and `app/src/test/kotlin/com/dailysatori/ui/feature/settings/skills/`.

## Task 1: Add Skill Schema And Migration

**Files:**
- Modify: `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/skill/SkillSchemaSourceTest.kt`

- [ ] **Step 1: Write source tests for schema and migration**

Create `shared/src/commonTest/kotlin/com/dailysatori/service/skill/SkillSchemaSourceTest.kt`:

```kotlin
package com.dailysatori.service.skill

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class SkillSchemaSourceTest {
    @Test
    fun skillConfigTableHasRequiredColumnsAndQueries() {
        val schema = File("src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()

        assertTrue(schema.contains("CREATE TABLE skill_config"))
        listOf(
            "name TEXT NOT NULL",
            "description TEXT NOT NULL DEFAULT ''",
            "gateway_url TEXT NOT NULL",
            "api_token TEXT NOT NULL DEFAULT ''",
            "skill_version TEXT NOT NULL DEFAULT ''",
            "enabled INTEGER NOT NULL DEFAULT 0",
            "builtin INTEGER NOT NULL DEFAULT 0",
            "provider TEXT NOT NULL DEFAULT ''",
            "template_id TEXT NOT NULL DEFAULT ''",
            "tool_schema_json TEXT NOT NULL DEFAULT ''",
        ).forEach { assertTrue(schema.contains(it), "Missing schema fragment: $it") }
        assertTrue(schema.contains("selectAllSkillConfigs:"))
        assertTrue(schema.contains("selectSkillConfigById:"))
        assertTrue(schema.contains("selectSkillConfigByTemplateId:"))
        assertTrue(schema.contains("selectEnabledSkillConfigs:"))
        assertTrue(schema.contains("insertSkillConfig:"))
        assertTrue(schema.contains("updateSkillConfig:"))
        assertTrue(schema.contains("deleteSkillConfig:"))
    }

    @Test
    fun databaseMigrationCreatesWeReadBuiltInSkill() {
        val config = File("src/commonMain/kotlin/com/dailysatori/config/Config.kt").readText()
        val migration = File("src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt").readText()

        assertTrue(config.contains("currentSchemaVersion = 9L"))
        assertTrue(migration.contains("migrateV8ToV9()"))
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS skill_config"))
        assertTrue(migration.contains("template_id"))
        assertTrue(migration.contains("weread"))
        assertTrue(migration.contains("weread_api_key"))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.skill.SkillSchemaSourceTest"`

Expected: FAIL because `skill_config`, schema version 9, and V9 migration do not exist.

- [ ] **Step 3: Add schema and queries**

In `DailySatori.sq`, add after the `mcp_server` table:

```sql
CREATE TABLE skill_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    gateway_url TEXT NOT NULL,
    api_token TEXT NOT NULL DEFAULT '',
    skill_version TEXT NOT NULL DEFAULT '',
    enabled INTEGER NOT NULL DEFAULT 0,
    builtin INTEGER NOT NULL DEFAULT 0,
    provider TEXT NOT NULL DEFAULT '',
    template_id TEXT NOT NULL DEFAULT '',
    tool_schema_json TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

Add SQLDelight queries after MCP server queries:

```sql
selectAllSkillConfigs:
SELECT * FROM skill_config ORDER BY builtin DESC, name;

selectSkillConfigById:
SELECT * FROM skill_config WHERE id = ?;

selectSkillConfigByTemplateId:
SELECT * FROM skill_config WHERE template_id = ? LIMIT 1;

selectEnabledSkillConfigs:
SELECT * FROM skill_config WHERE enabled = 1 ORDER BY builtin DESC, name;

insertSkillConfig:
INSERT INTO skill_config (name, description, gateway_url, api_token, skill_version, enabled, builtin, provider, template_id, tool_schema_json, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

updateSkillConfig:
UPDATE skill_config SET name = ?, description = ?, gateway_url = ?, api_token = ?, skill_version = ?, enabled = ?, provider = ?, template_id = ?, tool_schema_json = ?, updated_at = ?
WHERE id = ?;

deleteSkillConfig:
DELETE FROM skill_config WHERE id = ? AND builtin = 0;
```

- [ ] **Step 4: Add config constants and migration**

In `Config.kt`, change:

```kotlin
const val currentSchemaVersion = 8L
```

to:

```kotlin
const val currentSchemaVersion = 9L
```

Add to `SettingKeys`:

```kotlin
const val legacyWeReadApiKey = weReadApiKey
```

In `DatabaseMigration.runMigrations()`, add after V8:

```kotlin
if (currentVersion < 9) {
    migrateV8ToV9()
}
```

Add methods near `migrateV7ToV8()`:

```kotlin
private fun migrateV8ToV9() {
    log.i { "Migration V8 -> V9: Skill config table" }
    try {
        runSql("""
            CREATE TABLE IF NOT EXISTS skill_config (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                gateway_url TEXT NOT NULL,
                api_token TEXT NOT NULL DEFAULT '',
                skill_version TEXT NOT NULL DEFAULT '',
                enabled INTEGER NOT NULL DEFAULT 0,
                builtin INTEGER NOT NULL DEFAULT 0,
                provider TEXT NOT NULL DEFAULT '',
                template_id TEXT NOT NULL DEFAULT '',
                tool_schema_json TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """.trimIndent())
        log.i { "Created skill_config table" }
    } catch (e: Exception) {
        log.w(e) { "Could not create skill_config table" }
    }

    try {
        insertBuiltInWeReadSkillIfMissing()
    } catch (e: Exception) {
        log.w(e) { "Could not insert WeRead skill" }
    }
}

private fun insertBuiltInWeReadSkillIfMissing() {
    if (queryLong("SELECT COUNT(*) FROM skill_config WHERE template_id = 'weread'") != 0L) return
    val legacyToken = settingRepo.get(SettingKeys.weReadApiKey)?.trim().orEmpty()
    val enabled = if (legacyToken.isBlank()) 0 else 1
    runSql("""
        INSERT INTO skill_config (name, description, gateway_url, api_token, skill_version, enabled, builtin, provider, template_id, tool_schema_json, created_at, updated_at)
        VALUES ('微信读书', '微信读书 Skill，用于搜索书籍、获取图书信息、目录和书评。', 'https://i.weread.qq.com/api/agent/gateway',
            '${legacyToken.sqlEscaped()}', '1.0.3', $enabled, 1, 'weread', 'weread', '',
            strftime('%s', 'now') * 1000, strftime('%s', 'now') * 1000)
    """.trimIndent())
}
```

- [ ] **Step 5: Run schema source tests**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.skill.SkillSchemaSourceTest"`

Expected: PASS.

- [ ] **Step 6: Run SQLDelight generation and compile**

Run: `./gradlew :shared:generateCommonMainDailySatoriDatabaseInterface :shared:compileDebugKotlinAndroid`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt shared/src/commonTest/kotlin/com/dailysatori/service/skill/SkillSchemaSourceTest.kt
git commit -m "feat: add skill config schema"
```

## Task 2: Add Skill Repository And Built-In Metadata Helpers

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/skill/SkillConfigModels.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/SkillConfigRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/skill/SkillConfigModelsTest.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/data/repository/SkillConfigRepositorySourceTest.kt`

- [ ] **Step 1: Write model helper tests**

Create `shared/src/commonTest/kotlin/com/dailysatori/service/skill/SkillConfigModelsTest.kt`:

```kotlin
package com.dailysatori.service.skill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkillConfigModelsTest {
    @Test
    fun exposesWeReadBuiltInDefaults() {
        assertEquals("weread", BuiltInSkillTemplates.weRead)
        assertEquals("微信读书", builtInWeReadSkillName())
        assertEquals("https://i.weread.qq.com/api/agent/gateway", builtInWeReadGatewayUrl())
        assertEquals("1.0.3", builtInWeReadSkillVersion())
    }

    @Test
    fun tokenStatusAndDeleteRulesAreStable() {
        assertEquals("缺少 Token", skillTokenStatus(""))
        assertEquals("已配置 Token", skillTokenStatus("abc12345"))
        assertFalse(canDeleteSkill(builtin = 1L))
        assertTrue(canDeleteSkill(builtin = 0L))
    }
}
```

- [ ] **Step 2: Write repository source tests**

Create `shared/src/commonTest/kotlin/com/dailysatori/data/repository/SkillConfigRepositorySourceTest.kt`:

```kotlin
package com.dailysatori.data.repository

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class SkillConfigRepositorySourceTest {
    @Test
    fun repositoryEncryptsTokensAndProtectsBuiltIns() {
        val source = File("src/commonMain/kotlin/com/dailysatori/data/repository/SkillConfigRepository.kt").readText()

        assertTrue(source.contains("SecretCipher"))
        assertTrue(source.contains("secretCipher.encrypt(apiToken)"))
        assertTrue(source.contains("secretCipher.decrypt"))
        assertTrue(source.contains("deleteSkillConfig"))
        assertTrue(source.contains("canDeleteSkill"))
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.skill.SkillConfigModelsTest" --tests "com.dailysatori.data.repository.SkillConfigRepositorySourceTest"`

Expected: FAIL because files/classes do not exist.

- [ ] **Step 4: Add Skill model helpers**

Create `SkillConfigModels.kt`:

```kotlin
package com.dailysatori.service.skill

object BuiltInSkillTemplates {
    const val weRead = "weread"
}

fun builtInWeReadSkillName(): String = "微信读书"

fun builtInWeReadDescription(): String = "微信读书 Skill，用于搜索书籍、获取图书信息、目录和书评。"

fun builtInWeReadGatewayUrl(): String = "https://i.weread.qq.com/api/agent/gateway"

fun builtInWeReadSkillVersion(): String = "1.0.3"

fun skillSettingsTitle(): String = "Skills"

fun skillAddActionText(): String = "添加 Skill"

fun skillTokenStatus(apiToken: String): String = if (apiToken.trim().isBlank()) "缺少 Token" else "已配置 Token"
fun skillEnabledStatus(enabled: Long): String = if (enabled == 1L) "已启用" else "未启用"
fun skillBuiltinBadge(builtin: Long): String = if (builtin == 1L) "内置" else "自定义"
fun canDeleteSkill(builtin: Long): Boolean = builtin == 0L
```

- [ ] **Step 5: Add repository**

Create `SkillConfigRepository.kt`:

```kotlin
package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dailysatori.service.security.SecretCipher
import com.dailysatori.service.skill.canDeleteSkill
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Skill_config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SkillConfigRepository(
    private val db: DailySatoriDatabase,
    private val secretCipher: SecretCipher,
) {
    private val q get() = db.dailySatoriQueries

    fun getAll(): Flow<List<Skill_config>> =
        q.selectAllSkillConfigs().asFlow().mapToList(Dispatchers.IO).map { skills -> skills.map(::decryptSkill) }

    fun getById(id: Long): Skill_config? = q.selectSkillConfigById(id).executeAsOneOrNull()?.let(::decryptSkill)

    fun getByTemplateId(templateId: String): Skill_config? =
        q.selectSkillConfigByTemplateId(templateId).executeAsOneOrNull()?.let(::decryptSkill)

    fun getEnabled(): List<Skill_config> = q.selectEnabledSkillConfigs().executeAsList().map(::decryptSkill)

    fun insert(
        name: String,
        description: String,
        gatewayUrl: String,
        apiToken: String,
        skillVersion: String,
        enabled: Long,
        builtin: Long = 0,
        provider: String = "",
        templateId: String = "",
        toolSchemaJson: String = "",
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.insertSkillConfig(
            name,
            description,
            gatewayUrl,
            secretCipher.encrypt(apiToken),
            skillVersion,
            enabled,
            builtin,
            provider,
            templateId,
            toolSchemaJson,
            now,
            now,
        )
    }

    fun update(
        id: Long,
        name: String,
        description: String,
        gatewayUrl: String,
        apiToken: String,
        skillVersion: String,
        enabled: Long,
        provider: String,
        templateId: String,
        toolSchemaJson: String,
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.updateSkillConfig(
            name,
            description,
            gatewayUrl,
            secretCipher.encrypt(apiToken),
            skillVersion,
            enabled,
            provider,
            templateId,
            toolSchemaJson,
            now,
            id,
        )
    }

    fun delete(id: Long) {
        val skill = getById(id) ?: return
        if (canDeleteSkill(skill.builtin)) q.deleteSkillConfig(id)
    }

    fun ensureBuiltInWeRead() {
        if (getByTemplateId(com.dailysatori.service.skill.BuiltInSkillTemplates.weRead) != null) return
        insert(
            name = com.dailysatori.service.skill.builtInWeReadSkillName(),
            description = com.dailysatori.service.skill.builtInWeReadDescription(),
            gatewayUrl = com.dailysatori.service.skill.builtInWeReadGatewayUrl(),
            apiToken = "",
            skillVersion = com.dailysatori.service.skill.builtInWeReadSkillVersion(),
            enabled = 0,
            builtin = 1,
            provider = "weread",
            templateId = com.dailysatori.service.skill.BuiltInSkillTemplates.weRead,
        )
    }

    private fun decryptSkill(skill: Skill_config): Skill_config =
        skill.copy(api_token = secretCipher.decrypt(skill.api_token))
}
```

- [ ] **Step 6: Register repository in DI**

In `SharedModule.kt`, import and register:

```kotlin
import com.dailysatori.data.repository.SkillConfigRepository
```

Add near repositories:

```kotlin
single { SkillConfigRepository(get(), get()) }
```

- [ ] **Step 7: Run tests and compile**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.skill.SkillConfigModelsTest" --tests "com.dailysatori.data.repository.SkillConfigRepositorySourceTest"`

Expected: PASS.

Run: `./gradlew :shared:compileDebugKotlinAndroid`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/skill/SkillConfigModels.kt shared/src/commonMain/kotlin/com/dailysatori/data/repository/SkillConfigRepository.kt shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt shared/src/commonTest/kotlin/com/dailysatori/service/skill/SkillConfigModelsTest.kt shared/src/commonTest/kotlin/com/dailysatori/data/repository/SkillConfigRepositorySourceTest.kt
git commit -m "feat: add skill config repository"
```

## Task 3: Read WeRead Token From Skills

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/book/WeReadSkillService.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/book/WeReadSkillServiceTest.kt`

- [ ] **Step 1: Write failing token resolver tests**

Append tests to `WeReadSkillServiceTest`:

```kotlin
@Test
fun preferEnabledWeReadSkillTokenOverLegacySetting() {
    val token = resolveWeReadTokenFromSkillOrLegacy(
        skillToken = " skill-token ",
        skillEnabled = true,
        legacyStored = " legacy-token ",
        isEncrypted = { false },
        decrypt = { it },
        onLegacyPlaintext = {},
    )

    assertEquals("skill-token", token)
}

@Test
fun disabledWeReadSkillIsTreatedAsMissingToken() {
    val token = resolveWeReadTokenFromSkillOrLegacy(
        skillToken = "skill-token",
        skillEnabled = false,
        legacyStored = "legacy-token",
        isEncrypted = { false },
        decrypt = { it },
        onLegacyPlaintext = {},
    )

    assertEquals("", token)
}

@Test
fun legacyWeReadTokenStillWorksWhenSkillRowIsMissing() {
    var upgraded = ""
    val token = resolveWeReadTokenFromSkillOrLegacy(
        skillToken = null,
        skillEnabled = false,
        legacyStored = " legacy-token ",
        isEncrypted = { false },
        decrypt = { it },
        onLegacyPlaintext = { upgraded = it },
    )

    assertEquals("legacy-token", token)
    assertEquals("legacy-token", upgraded)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.book.WeReadSkillServiceTest"`

Expected: FAIL because `resolveWeReadTokenFromSkillOrLegacy` does not exist.

- [ ] **Step 3: Update service constructor and token lookup**

In `WeReadSkillService.kt`, import:

```kotlin
import com.dailysatori.data.repository.SkillConfigRepository
import com.dailysatori.service.skill.BuiltInSkillTemplates
```

Change constructor to include `SkillConfigRepository`:

```kotlin
class WeReadSkillService(
    private val client: HttpClient,
    private val settingRepository: SettingRepository,
    private val secretCipher: SecretCipher,
    private val aiFallbackGenerator: BookAiFallbackGenerator,
    private val skillConfigRepository: SkillConfigRepository,
) : BookIntelligenceSource {
```

Replace `readStoredWeReadApiKey()` with:

```kotlin
private fun readStoredWeReadApiKey(): String {
    val skill = skillConfigRepository.getByTemplateId(BuiltInSkillTemplates.weRead)
    val legacyStored = settingRepository.get(SettingKeys.weReadApiKey).orEmpty()
    return resolveWeReadTokenFromSkillOrLegacy(
        skillToken = skill?.api_token,
        skillEnabled = skill?.enabled == 1L,
        legacyStored = legacyStored,
        isEncrypted = secretCipher::isEncrypted,
        decrypt = secretCipher::decrypt,
        onLegacyPlaintext = { key -> settingRepository.upsert(SettingKeys.weReadApiKey, secretCipher.encrypt(key)) },
    )
}
```

Add public resolver near `resolveStoredWeReadApiKey`:

```kotlin
fun resolveWeReadTokenFromSkillOrLegacy(
    skillToken: String?,
    skillEnabled: Boolean,
    legacyStored: String,
    isEncrypted: (String) -> Boolean,
    decrypt: (String) -> String,
    onLegacyPlaintext: (String) -> Unit = {},
): String {
    if (skillToken != null) return if (skillEnabled) skillToken.trim() else ""
    return resolveStoredWeReadApiKey(
        stored = legacyStored,
        isEncrypted = isEncrypted,
        decrypt = decrypt,
        onPlaintext = onLegacyPlaintext,
    )
}
```

Change missing key message in `requireWeReadApiKey`:

```kotlin
?: throw WeReadSkillException(WeReadSkillErrorType.MissingApiKey, "请先在 Skills 中配置微信读书 Token")
```

Update `weReadUserMessage` missing key branch to the same string.

Update `SharedModule.kt` WeRead service registration:

```kotlin
single { WeReadSkillService(get(), get(), get(), get(), get()) }
```

- [ ] **Step 4: Update existing tests for new missing-token text**

In `WeReadSkillServiceTest.missingApiKeyUsesDedicatedErrorType`, change expected message to:

```kotlin
"请先在 Skills 中配置微信读书 Token"
```

In `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookSearchUiTextTest.kt`, update any WeRead missing key expected string to the same value.

- [ ] **Step 5: Run tests and compile**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.book.WeReadSkillServiceTest"`

Expected: PASS.

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.book.BookSearchUiTextTest"`

Expected: PASS.

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/book/WeReadSkillService.kt shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt shared/src/commonTest/kotlin/com/dailysatori/service/book/WeReadSkillServiceTest.kt app/src/test/kotlin/com/dailysatori/ui/feature/book/BookSearchUiTextTest.kt
git commit -m "feat: resolve WeRead token from skills"
```

## Task 4: Add Skills Settings ViewModel

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/skills/SkillSettingsViewModel.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/settings/skills/SkillSettingsTextTest.kt`

- [ ] **Step 1: Write text and validation tests**

Create `app/src/test/kotlin/com/dailysatori/ui/feature/settings/skills/SkillSettingsTextTest.kt`:

```kotlin
package com.dailysatori.ui.feature.settings.skills

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkillSettingsTextTest {
    @Test
    fun exposesSkillSettingsLabels() {
        assertEquals("Skills", skillSettingsScreenTitle())
        assertEquals("添加 Skill", skillAddButtonText())
        assertEquals("保存", skillSaveButtonText(false))
        assertEquals("保存中...", skillSaveButtonText(true))
        assertEquals("内置 Skill 不能删除", skillBuiltinDeleteBlockedMessage())
    }

    @Test
    fun validatesSkillEditInput() {
        assertEquals("请输入 Skill 名称", validateSkillInput("", "https://example.com", "{}"))
        assertEquals("请输入 Gateway URL", validateSkillInput("测试", "", "{}"))
        assertEquals("Tool Schema 必须是 JSON 对象或数组", validateSkillInput("测试", "https://example.com", "not-json"))
        assertEquals(null, validateSkillInput("测试", "https://example.com", ""))
        assertEquals(null, validateSkillInput("测试", "https://example.com", "{}"))
        assertEquals(null, validateSkillInput("测试", "https://example.com", "[]"))
    }

    @Test
    fun builtInFieldEditabilityIsRestricted() {
        assertFalse(skillCoreFieldsEditable(builtin = 1L))
        assertTrue(skillCoreFieldsEditable(builtin = 0L))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.settings.skills.SkillSettingsTextTest"`

Expected: FAIL because the functions do not exist.

- [ ] **Step 3: Add ViewModel and helpers**

Create `SkillSettingsViewModel.kt`:

```kotlin
package com.dailysatori.ui.feature.settings.skills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.SkillConfigRepository
import com.dailysatori.service.skill.canDeleteSkill
import com.dailysatori.shared.db.Skill_config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

data class SkillSettingsState(
    val skills: List<Skill_config> = emptyList(),
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class SkillSettingsViewModel(
    private val repository: SkillConfigRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SkillSettingsState())
    val state: StateFlow<SkillSettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.ensureBuiltInWeRead()
            repository.getAll().collect { skills ->
                _state.update { it.copy(skills = skills) }
            }
        }
    }

    fun save(input: SkillEditInput) {
        val validation = validateSkillInput(input.name, input.gatewayUrl, input.toolSchemaJson)
        if (validation != null) {
            _state.update { it.copy(error = validation, message = null) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isSaving = true, error = null, message = null) }
            if (input.id == null) {
                repository.insert(input.name.trim(), input.description.trim(), input.gatewayUrl.trim(), input.apiToken.trim(), input.skillVersion.trim(), if (input.enabled) 1 else 0, 0, input.provider.trim(), input.templateId.trim(), input.toolSchemaJson.trim())
            } else {
                repository.update(input.id, input.name.trim(), input.description.trim(), input.gatewayUrl.trim(), input.apiToken.trim(), input.skillVersion.trim(), if (input.enabled) 1 else 0, input.provider.trim(), input.templateId.trim(), input.toolSchemaJson.trim())
            }
            _state.update { it.copy(isSaving = false, message = skillSavedMessage()) }
        }
    }

    fun delete(skill: Skill_config) {
        if (!canDeleteSkill(skill.builtin)) {
            _state.update { it.copy(error = skillBuiltinDeleteBlockedMessage()) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) { repository.delete(skill.id) }
    }
}

data class SkillEditInput(
    val id: Long?,
    val name: String,
    val description: String,
    val gatewayUrl: String,
    val apiToken: String,
    val skillVersion: String,
    val enabled: Boolean,
    val provider: String,
    val templateId: String,
    val toolSchemaJson: String,
)

fun skillSettingsScreenTitle(): String = "Skills"
fun skillAddButtonText(): String = "添加 Skill"
fun skillSaveButtonText(isSaving: Boolean): String = if (isSaving) "保存中..." else "保存"
fun skillSavedMessage(): String = "Skill 已保存"
fun skillBuiltinDeleteBlockedMessage(): String = "内置 Skill 不能删除"
fun skillCoreFieldsEditable(builtin: Long): Boolean = builtin == 0L

fun validateSkillInput(name: String, gatewayUrl: String, toolSchemaJson: String): String? {
    if (name.trim().isBlank()) return "请输入 Skill 名称"
    if (gatewayUrl.trim().isBlank()) return "请输入 Gateway URL"
    val schema = toolSchemaJson.trim()
    if (schema.isBlank()) return null
    val element = runCatching { Json.parseToJsonElement(schema) }.getOrNull()
        ?: return "Tool Schema 必须是 JSON 对象或数组"
    if (element !is JsonObject && element !is JsonArray) return "Tool Schema 必须是 JSON 对象或数组"
    return null
}
```

- [ ] **Step 4: Run text tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.settings.skills.SkillSettingsTextTest"`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/settings/skills/SkillSettingsViewModel.kt app/src/test/kotlin/com/dailysatori/ui/feature/settings/skills/SkillSettingsTextTest.kt
git commit -m "feat: add skills settings state"
```

## Task 5: Add Skills Settings UI And Navigation

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/skills/SkillSettingsScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`
- Delete: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/weread/WeReadSettingsScreen.kt`
- Delete: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/weread/WeReadSettingsViewModel.kt`
- Modify/Delete test: `app/src/test/kotlin/com/dailysatori/ui/feature/settings/weread/WeReadSettingsTextTest.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/settings/skills/SkillSettingsTextTest.kt`

- [ ] **Step 1: Extend text tests for Settings row labels**

Add to `SkillSettingsTextTest`:

```kotlin
@Test
fun skillsSettingsRowUsesGenericSkillsText() {
    assertEquals("Skills", skillSettingsRowTitle())
    assertEquals("管理 Agent 可调用的外部 Skills", skillSettingsRowSubtitle())
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.settings.skills.SkillSettingsTextTest.skillsSettingsRowUsesGenericSkillsText"`

Expected: FAIL because row helper functions do not exist.

- [ ] **Step 3: Add row helpers**

In `SkillSettingsViewModel.kt`, add:

```kotlin
fun skillSettingsRowTitle(): String = "Skills"
fun skillSettingsRowSubtitle(): String = "管理 Agent 可调用的外部 Skills"
```

- [ ] **Step 4: Add Skills screen UI**

Create `SkillSettingsScreen.kt` with a list/edit pattern:

```kotlin
package com.dailysatori.ui.feature.settings.skills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.dailysatori.service.skill.canDeleteSkill
import com.dailysatori.service.skill.skillBuiltinBadge
import com.dailysatori.service.skill.skillEnabledStatus
import com.dailysatori.service.skill.skillTokenStatus
import com.dailysatori.shared.db.Skill_config
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun SkillSettingsScreen(onBack: () -> Unit) {
    val viewModel: SkillSettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    var editing by remember { mutableStateOf<Skill_config?>(null) }
    var adding by remember { mutableStateOf(false) }

    val target = editing
    if (adding || target != null) {
        SkillEditScreen(
            skill = target,
            isSaving = state.isSaving,
            error = state.error,
            onSave = { input -> viewModel.save(input); adding = false; editing = null },
            onBack = { adding = false; editing = null },
        )
        return
    }

    AppScaffold(
        title = skillSettingsScreenTitle(),
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Default.Add, contentDescription = skillAddButtonText())
            }
        },
    ) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            item { Text("${state.skills.size} 个 Skill", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
            items(state.skills, key = { it.id }) { skill ->
                SkillCard(skill = skill, onEdit = { editing = skill }, onDelete = { viewModel.delete(skill) })
            }
        }
    }
}

@Composable
private fun SkillCard(skill: Skill_config, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onEdit,
        shape = RoundedCornerShape(Radius.m),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(skill.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (canDeleteSkill(skill.builtin)) {
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "删除 Skill") }
                }
            }
            Text("${skillBuiltinBadge(skill.builtin)} · ${skillEnabledStatus(skill.enabled)} · ${skillTokenStatus(skill.api_token)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (skill.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(skill.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
```

Append the edit composable in the same file. Keep it simple and use all fields from `SkillEditInput`; core fields are disabled when `skillCoreFieldsEditable(skill.builtin)` is false.

```kotlin
@Composable
private fun SkillEditScreen(
    skill: Skill_config?,
    isSaving: Boolean,
    error: String?,
    onSave: (SkillEditInput) -> Unit,
    onBack: () -> Unit,
) {
    val editable = skillCoreFieldsEditable(skill?.builtin ?: 0)
    var name by remember(skill?.id) { mutableStateOf(skill?.name.orEmpty()) }
    var description by remember(skill?.id) { mutableStateOf(skill?.description.orEmpty()) }
    var gatewayUrl by remember(skill?.id) { mutableStateOf(skill?.gateway_url.orEmpty()) }
    var apiToken by remember(skill?.id) { mutableStateOf(skill?.api_token.orEmpty()) }
    var skillVersion by remember(skill?.id) { mutableStateOf(skill?.skill_version.orEmpty()) }
    var enabled by remember(skill?.id) { mutableStateOf(skill?.enabled == 1L) }
    var provider by remember(skill?.id) { mutableStateOf(skill?.provider.orEmpty()) }
    var templateId by remember(skill?.id) { mutableStateOf(skill?.template_id.orEmpty()) }
    var toolSchemaJson by remember(skill?.id) { mutableStateOf(skill?.tool_schema_json.orEmpty()) }

    AppScaffold(title = skill?.name ?: skillAddButtonText(), onBack = onBack) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(horizontal = Spacing.m),
            contentPadding = PaddingValues(vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("名称") }, enabled = editable, singleLine = true) }
            item { OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("给 AI 的能力描述") }, enabled = editable, minLines = 3) }
            item { OutlinedTextField(gatewayUrl, { gatewayUrl = it }, Modifier.fillMaxWidth(), label = { Text("Gateway URL") }, enabled = editable, singleLine = true) }
            item { OutlinedTextField(skillVersion, { skillVersion = it }, Modifier.fillMaxWidth(), label = { Text("Skill Version") }, enabled = editable, singleLine = true) }
            item { OutlinedTextField(apiToken, { apiToken = it }, Modifier.fillMaxWidth(), label = { Text("API Token") }, singleLine = true) }
            item { OutlinedTextField(provider, { provider = it }, Modifier.fillMaxWidth(), label = { Text("Provider") }, enabled = editable, singleLine = true) }
            item { OutlinedTextField(templateId, { templateId = it }, Modifier.fillMaxWidth(), label = { Text("Template ID") }, enabled = editable, singleLine = true) }
            item { OutlinedTextField(toolSchemaJson, { toolSchemaJson = it }, Modifier.fillMaxWidth(), label = { Text("Tool Schema JSON") }, enabled = editable, minLines = 4) }
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("启用", style = MaterialTheme.typography.bodyMedium)
                        Text("启用后 Agent 可以调用这个 Skill", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
            if (error != null) item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            item {
                Button(
                    onClick = {
                        onSave(SkillEditInput(skill?.id, name, description, gatewayUrl, apiToken, skillVersion, enabled, provider, templateId, toolSchemaJson))
                    },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(skillSaveButtonText(isSaving)) }
            }
        }
    }
}
```

- [ ] **Step 5: Replace Settings navigation**

In `SettingsScreen.kt`:

Change import from WeRead screen to Skills screen:

```kotlin
import com.dailysatori.ui.feature.settings.skills.SkillSettingsScreen
import com.dailysatori.ui.feature.settings.skills.skillSettingsRowSubtitle
import com.dailysatori.ui.feature.settings.skills.skillSettingsRowTitle
```

Rename enum `WE_READ` to `SKILLS` and route:

```kotlin
SettingsPage.SKILLS -> SkillSettingsScreen(onBack = { currentPage = SettingsPage.MAIN })
```

Replace the row:

```kotlin
SettingsRow(Icons.AutoMirrored.Filled.MenuBook, skillSettingsRowTitle(), skillSettingsRowSubtitle(), onClick = { onNavigate(SettingsPage.SKILLS) })
```

- [ ] **Step 6: Update ViewModel DI and remove old WeRead screen files**

In `ViewModelModule.kt`, replace WeRead ViewModel import/registration with:

```kotlin
import com.dailysatori.ui.feature.settings.skills.SkillSettingsViewModel
```

and:

```kotlin
viewModel { SkillSettingsViewModel(get()) }
```

Delete:

```bash
app/src/main/kotlin/com/dailysatori/ui/feature/settings/weread/WeReadSettingsScreen.kt
app/src/main/kotlin/com/dailysatori/ui/feature/settings/weread/WeReadSettingsViewModel.kt
app/src/test/kotlin/com/dailysatori/ui/feature/settings/weread/WeReadSettingsTextTest.kt
```

- [ ] **Step 7: Run app tests and compile**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.settings.skills.SkillSettingsTextTest"`

Expected: PASS.

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/settings/skills app/src/test/kotlin/com/dailysatori/ui/feature/settings/skills app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt app/src/main/kotlin/com/dailysatori/ui/feature/settings/weread app/src/test/kotlin/com/dailysatori/ui/feature/settings/weread
git commit -m "feat: add skills settings screen"
```

## Task 6: Add Phase 1 Skill Registry Contracts

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/skill/SkillRegistry.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/skill/SkillRegistryTest.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`

- [ ] **Step 1: Write registry tests**

Create `shared/src/commonTest/kotlin/com/dailysatori/service/skill/SkillRegistryTest.kt`:

```kotlin
package com.dailysatori.service.skill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkillRegistryTest {
    @Test
    fun exposesWeReadToolNamesForBuiltInSkill() {
        val tools = builtInWeReadToolNames()

        assertEquals(listOf("weread_search_books", "weread_get_book_info", "weread_get_chapters", "weread_get_reviews"), tools)
    }

    @Test
    fun genericCustomSkillToolSchemaMentionsRequiredArguments() {
        val schema = buildCallExternalSkillToolDefinition().toString()

        assertTrue(schema.contains("call_external_skill"))
        assertTrue(schema.contains("skill_id"))
        assertTrue(schema.contains("api_name"))
        assertTrue(schema.contains("params_json"))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.skill.SkillRegistryTest"`

Expected: FAIL because registry functions do not exist.

- [ ] **Step 3: Add registry contracts**

Create `SkillRegistry.kt`:

```kotlin
package com.dailysatori.service.skill

import com.dailysatori.data.repository.SkillConfigRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject

class SkillRegistry(
    private val skillConfigRepository: SkillConfigRepository,
) {
    fun enabledSkillCount(): Int = skillConfigRepository.getEnabled().size

    fun buildToolDefinitions(): List<JsonObject> =
        builtInWeReadToolNames().map(::buildSimpleToolDefinition) + buildCallExternalSkillToolDefinition()
}

fun builtInWeReadToolNames(): List<String> = listOf(
    "weread_search_books",
    "weread_get_book_info",
    "weread_get_chapters",
    "weread_get_reviews",
)

fun buildCallExternalSkillToolDefinition(): JsonObject = buildSimpleToolDefinition(
    name = "call_external_skill",
    description = "调用用户配置的外部 Skill。参数必须包含 skill_id、api_name 和 params_json。",
)

private fun buildSimpleToolDefinition(
    name: String,
    description: String = "Agent 可调用的 Skill 工具。",
): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("function"))
    putJsonObject("function") {
        put("name", JsonPrimitive(name))
        put("description", JsonPrimitive(description))
        putJsonObject("parameters") {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {
                putJsonObject("skill_id") { put("type", JsonPrimitive("integer")) }
                putJsonObject("api_name") { put("type", JsonPrimitive("string")) }
                putJsonObject("params_json") { put("type", JsonPrimitive("string")) }
            }
        }
    }
}
```

- [ ] **Step 4: Register registry in DI**

In `SharedModule.kt`, import and register:

```kotlin
import com.dailysatori.service.skill.SkillRegistry
```

Add near services:

```kotlin
single { SkillRegistry(get()) }
```

- [ ] **Step 5: Run tests and compile**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.skill.SkillRegistryTest"`

Expected: PASS.

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/skill/SkillRegistry.kt shared/src/commonTest/kotlin/com/dailysatori/service/skill/SkillRegistryTest.kt shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt
git commit -m "feat: add skill registry contracts"
```

## Task 7: Full Phase 1 Verification

**Files:**
- Verify only; no code edits unless a failure reveals a defect.

- [ ] **Step 1: Run shared skill and book tests**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.skill.*" --tests "com.dailysatori.data.repository.SkillConfigRepositorySourceTest" --tests "com.dailysatori.service.book.*"`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run app settings and book tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.settings.skills.*" --tests "com.dailysatori.ui.feature.book.*"`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run required compile check**

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Install and launch on connected device**

Run: `adb devices`

If multiple devices are connected and `ba5e2328` is listed, use the physical device for launch:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
adb -s ba5e2328 shell am start -n com.dailysatori/.MainActivity
```

Expected: install reports BUILD SUCCESSFUL and launch prints `Starting: Intent { cmp=com.dailysatori/.MainActivity }`.

- [ ] **Step 5: Commit verification fixes if needed**

If verification required code changes, commit them:

```bash
git add <changed files>
git commit -m "fix: stabilize skills management phase one"
```

If no files changed, do not create an empty commit.

## Self-Review

- Spec coverage: Phase 1 covers `skill_config`, WeRead preload, legacy token migration, Skills Settings replacing WeRead settings, encrypted token repository, WeRead runtime reading from Skills, built-in delete protection, and registry contracts for later agent phases.
- Scope: Article summary, book agent routing, AI Chat agent routing, and full external Skill execution are intentionally left to later phases as the approved spec requested staged delivery.
- Placeholder scan: No TBD/TODO placeholders remain. Every task has exact files, code snippets, commands, and expected results.
- Type consistency: `Skill_config`, `SkillConfigRepository`, `BuiltInSkillTemplates.weRead`, `SkillSettingsViewModel`, and `SkillRegistry` are introduced before later tasks use them.

# Shared Service Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Simplify selected shared service/repository helper logic with pure functions while preserving all AI prompts, remote API behavior, backup format, database writes, and runtime behavior.

**Architecture:** Keep shared services as orchestration/business logic and repositories as SQLDelight data access. Extract only small pure parser/formatter/policy helpers beside existing shared files, then update current call sites to delegate to those helpers. Protect every extraction with shared common tests before production changes.

**Tech Stack:** Kotlin Multiplatform, kotlinx.serialization JSON, kotlinx.datetime, SQLDelight models, Kotlin common tests, Android debug build verification.

**Workspace Note:** Project instructions forbid git worktrees and commits unless explicitly requested. Execute in the current workspace and do not include commit steps.

---

## File Structure

- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/RemoteArticleLocalPolicy.kt`
  - Pure text cleanup, viewpoint markdown, language sample/count helpers for remote-article local persistence policy.
- Create: `shared/src/commonTest/kotlin/com/dailysatori/data/repository/RemoteArticleLocalPolicyTest.kt`
  - Tests for text trimming, viewpoint markdown, language thresholds, and sample construction.
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/RemoteArticleFavoriteMapper.kt`
  - Delegate text cleanup and language counting to the new policy helpers without changing mapped fields.
- Modify: `shared/src/commonTest/kotlin/com/dailysatori/data/repository/RemoteArticleFavoriteMapperTest.kt`
  - Add guardrail tests for blank URL and cached-article fallback behavior.
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/remotenews/RemoteArticleViewpointsParser.kt`
  - Pure JSON-element parser for the remote article `viewpoints` field.
- Create: `shared/src/commonTest/kotlin/com/dailysatori/service/remotenews/RemoteArticleViewpointsParserTest.kt`
  - Tests for array, newline string, blank entries, null, and unsupported JSON shapes.
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/remotenews/RemoteNewsModels.kt`
  - Delegate `RemoteArticleViewpointsSerializer.deserialize()` to the parser helper.
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpReferenceParser.kt`
  - Pure parser/matcher helpers for `<!-- refs: ... -->` MCP answer metadata.
- Create: `shared/src/commonTest/kotlin/com/dailysatori/service/mcp/McpReferenceParserTest.kt`
  - Tests for parsing reference IDs, ignoring malformed refs, matching results, and handling `none`.
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpToolResultFormatter.kt`
  - Delegate reference parsing and result matching to `McpReferenceParser` while preserving fallback behavior.

---

### Task 1: Extract Remote Article Local Policy Helpers

**Files:**
- Create: `shared/src/commonTest/kotlin/com/dailysatori/data/repository/RemoteArticleLocalPolicyTest.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/RemoteArticleLocalPolicy.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/RemoteArticleFavoriteMapper.kt`
- Modify: `shared/src/commonTest/kotlin/com/dailysatori/data/repository/RemoteArticleFavoriteMapperTest.kt`

- [ ] **Step 1: Write failing policy helper tests**

Create `RemoteArticleLocalPolicyTest.kt` with this complete content:

```kotlin
package com.dailysatori.data.repository

import com.dailysatori.service.remotenews.RemoteArticle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteArticleLocalPolicyTest {
    @Test
    fun cleanRemoteArticleTextTrimsBlankStringsToNull() {
        assertEquals("标题", cleanRemoteArticleText(" 标题 "))
        assertNull(cleanRemoteArticleText("   "))
        assertNull(cleanRemoteArticleText(null))
    }

    @Test
    fun remoteArticleViewpointMarkdownKeepsOnlyNonBlankPoints() {
        assertEquals(
            "## 关键观点\n\n- 观点 A\n- 观点 B",
            remoteArticleViewpointMarkdown(listOf(" 观点 A ", "", "  ", "观点 B")),
        )
        assertNull(remoteArticleViewpointMarkdown(listOf(" ", "")))
    }

    @Test
    fun remoteArticleLanguageSampleUsesTitleSummaryViewpointsAndContent() {
        val article = RemoteArticle(
            id = 1,
            title = "Title",
            summary = "Summary",
            viewpoints = listOf("Point A", "Point B"),
            content = "Body",
        )

        assertEquals("Title\nSummary\nPoint A\nPoint B\nBody", remoteArticleLanguageSample(article))
    }

    @Test
    fun remoteArticleLanguagePolicyKeepsChineseAndReprocessesEnglish() {
        val chinese = RemoteArticle(
            id = 2,
            url = "https://example.com/zh",
            title = "这是中文标题",
            summary = "这里有足够多的中文内容用于判断。",
            content = "更多中文内容确保超过阈值。",
        )
        val english = RemoteArticle(
            id = 3,
            url = "https://example.com/en",
            title = "OpenAI launches new coding model",
            summary = "The company announced a major update for developers and enterprise teams.",
            content = "The model improves reliability and long context reasoning for teams building software every day.",
        )

        assertTrue(hasEnoughChineseForLocalArticle(chinese))
        assertFalse(chinese.needsLocalAiReprocessingForChineseOutput())
        assertTrue(hasEnoughEnglishForLocalArticle(english))
        assertTrue(english.needsLocalAiReprocessingForChineseOutput())
    }
}
```

- [ ] **Step 2: Run the focused helper test and verify it fails**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests com.dailysatori.data.repository.RemoteArticleLocalPolicyTest
```

Expected: FAIL because `cleanRemoteArticleText`, `remoteArticleViewpointMarkdown`, `remoteArticleLanguageSample`, `hasEnoughChineseForLocalArticle`, and `hasEnoughEnglishForLocalArticle` do not exist.

- [ ] **Step 3: Create `RemoteArticleLocalPolicy.kt`**

Create the file with this complete content:

```kotlin
package com.dailysatori.data.repository

import com.dailysatori.service.remotenews.RemoteArticle

private val ChineseCharacterRegex = Regex("[\\u4e00-\\u9fff]")
private val EnglishWordRegex = Regex("\\b[A-Za-z][A-Za-z'-]{2,}\\b")
private const val LOCAL_REPROCESS_LANGUAGE_SAMPLE_LIMIT = 4_000
private const val LOCAL_REPROCESS_CHINESE_THRESHOLD = 8
private const val LOCAL_REPROCESS_ENGLISH_WORD_THRESHOLD = 12

internal fun cleanRemoteArticleText(value: String?): String? =
    value?.trim()?.takeIf { it.isNotBlank() }

internal fun remoteArticleViewpointMarkdown(viewpoints: List<String>): String? {
    val cleanViewpoints = viewpoints.mapNotNull(::cleanRemoteArticleText)
    return cleanViewpoints
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = "\n") { "- $it" }
        ?.let { "## 关键观点\n\n$it" }
}

internal fun remoteArticleLanguageSample(article: RemoteArticle): String = listOfNotNull(
    article.title,
    article.summary,
    article.viewpoints.joinToString("\n"),
    article.content,
).joinToString("\n").take(LOCAL_REPROCESS_LANGUAGE_SAMPLE_LIMIT)

internal fun hasEnoughChineseForLocalArticle(article: RemoteArticle): Boolean =
    ChineseCharacterRegex.findAll(remoteArticleLanguageSample(article)).count() >= LOCAL_REPROCESS_CHINESE_THRESHOLD

internal fun hasEnoughEnglishForLocalArticle(article: RemoteArticle): Boolean =
    EnglishWordRegex.findAll(remoteArticleLanguageSample(article)).count() >= LOCAL_REPROCESS_ENGLISH_WORD_THRESHOLD
```

- [ ] **Step 4: Update `RemoteArticleFavoriteMapper.kt` to use policy helpers**

Replace `toLocalFavoriteArticleFields()` with this implementation:

```kotlin
fun RemoteArticle.toLocalFavoriteArticleFields(): LocalFavoriteArticleFields {
    val cleanTitle = cleanRemoteArticleText(title)
    return LocalFavoriteArticleFields(
        title = cleanTitle,
        aiTitle = cleanTitle,
        aiContent = remoteArticleSummaryForLocalFavorite(summary, viewpoints),
        aiMarkdownContent = cleanRemoteArticleText(content),
        url = cleanRemoteArticleText(url),
        coverImageUrl = cleanRemoteArticleText(coverUrl),
        pubDate = remoteArticleTimeMillis(publishedAt) ?: remoteArticleTimeMillis(processedAt) ?: remoteArticleTimeMillis(createdAt),
    )
}
```

Replace `needsLocalAiReprocessingForChineseOutput()` with this implementation:

```kotlin
fun RemoteArticle.needsLocalAiReprocessingForChineseOutput(): Boolean {
    if (url.isNullOrBlank()) return false
    if (hasEnoughChineseForLocalArticle(this)) return false
    return hasEnoughEnglishForLocalArticle(this)
}
```

Replace `remoteArticleSummaryForLocalFavorite()` with this implementation:

```kotlin
internal fun remoteArticleSummaryForLocalFavorite(summary: String?, viewpoints: List<String>): String? = listOfNotNull(
    cleanRemoteArticleText(summary),
    remoteArticleViewpointMarkdown(viewpoints),
).joinToString("\n\n").takeIf { it.isNotBlank() }
```

Delete these constants from `RemoteArticleFavoriteMapper.kt` because they now live in `RemoteArticleLocalPolicy.kt`:

```kotlin
private const val LOCAL_REPROCESS_LANGUAGE_SAMPLE_LIMIT = 4_000
private const val LOCAL_REPROCESS_CHINESE_THRESHOLD = 8
private const val LOCAL_REPROCESS_ENGLISH_WORD_THRESHOLD = 12
```

- [ ] **Step 5: Add mapper guardrail tests**

Append these tests to `RemoteArticleFavoriteMapperTest.kt`:

```kotlin
    @Test
    fun blankRemoteArticleUrlDoesNotTriggerLocalAiReprocessing() {
        val article = RemoteArticle(
            id = 12,
            title = "OpenAI launches new coding model",
            url = " ",
            summary = "The company announced a major update for developers and enterprise teams.",
            content = "The model improves reliability and long context reasoning for teams building software every day.",
        )

        assertFalse(article.needsLocalAiReprocessingForChineseOutput())
    }

    @Test
    fun cachedRemoteArticleUsesSummaryOrTitleAsMarkdownFallback() {
        val withSummary = RemoteArticle(id = 13, title = "Title", summary = "Summary")
        val titleOnly = RemoteArticle(id = 14, title = "Title Only")

        assertEquals("Summary", withSummary.toLocalCachedArticleFields().aiMarkdownContent)
        assertEquals("Title Only", titleOnly.toLocalCachedArticleFields().aiMarkdownContent)
    }
```

- [ ] **Step 6: Run focused shared tests and verify they pass**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests com.dailysatori.data.repository.RemoteArticleLocalPolicyTest --tests com.dailysatori.data.repository.RemoteArticleFavoriteMapperTest
```

Expected: BUILD SUCCESSFUL.

---

### Task 2: Extract Remote Article Viewpoints Parser

**Files:**
- Create: `shared/src/commonTest/kotlin/com/dailysatori/service/remotenews/RemoteArticleViewpointsParserTest.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/remotenews/RemoteArticleViewpointsParser.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/remotenews/RemoteNewsModels.kt`

- [ ] **Step 1: Write failing parser tests**

Create `RemoteArticleViewpointsParserTest.kt` with this complete content:

```kotlin
package com.dailysatori.service.remotenews

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals

class RemoteArticleViewpointsParserTest {
    @Test
    fun parsesJsonArrayViewpointsAndDropsBlankEntries() {
        val element = Json.parseToJsonElement("""["观点 A", "", "  ", "观点 B"]""")

        assertEquals(listOf("观点 A", "观点 B"), remoteArticleViewpointsFromJsonElement(element))
    }

    @Test
    fun parsesNewlineSeparatedStringViewpoints() {
        val element = Json.parseToJsonElement(""""观点 A\n\n 观点 B \n """")

        assertEquals(listOf("观点 A", "观点 B"), remoteArticleViewpointsFromJsonElement(element))
    }

    @Test
    fun unsupportedViewpointShapesReturnEmptyList() {
        val jsonObject = Json.parseToJsonElement("""{"text":"观点"}""")
        val jsonNull = Json.parseToJsonElement("null")

        assertEquals(emptyList(), remoteArticleViewpointsFromJsonElement(jsonObject))
        assertEquals(emptyList(), remoteArticleViewpointsFromJsonElement(jsonNull))
    }

    @Test
    fun serializerUsesParserForArrayAndStringPayloads() {
        val arrayArticle = Json.decodeFromString<RemoteArticle>(
            """{"id":1,"viewpoints":["观点 A","观点 B"]}""",
        )
        val stringArticle = Json.decodeFromString<RemoteArticle>(
            """{"id":2,"viewpoints":"观点 A\n观点 B"}""",
        )

        assertEquals(listOf("观点 A", "观点 B"), arrayArticle.viewpoints)
        assertEquals(listOf("观点 A", "观点 B"), stringArticle.viewpoints)
    }
}
```

- [ ] **Step 2: Run the focused parser test and verify it fails**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests com.dailysatori.service.remotenews.RemoteArticleViewpointsParserTest
```

Expected: FAIL because `remoteArticleViewpointsFromJsonElement` does not exist.

- [ ] **Step 3: Create `RemoteArticleViewpointsParser.kt`**

Create the file with this complete content:

```kotlin
package com.dailysatori.service.remotenews

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal fun remoteArticleViewpointsFromJsonElement(element: JsonElement): List<String> = when (element) {
    JsonNull -> emptyList()
    is JsonArray -> element.mapNotNull { item -> item.jsonPrimitive.contentOrNull?.trim()?.takeIf { it.isNotBlank() } }
    is JsonPrimitive -> element.contentOrNull
        ?.split('\n')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()
    else -> emptyList()
}
```

- [ ] **Step 4: Update `RemoteNewsModels.kt` serializer**

Remove these imports because the parser owns them:

```kotlin
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
```

Replace `RemoteArticleViewpointsSerializer.deserialize()` body with:

```kotlin
    override fun deserialize(decoder: Decoder): List<String> {
        if (decoder !is JsonDecoder) return delegate.deserialize(decoder)
        return remoteArticleViewpointsFromJsonElement(decoder.decodeJsonElement())
    }
```

- [ ] **Step 5: Run focused remote-news shared tests and verify they pass**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests com.dailysatori.service.remotenews.RemoteArticleViewpointsParserTest --tests com.dailysatori.service.remotenews.RemoteNewsServiceTest
```

Expected: BUILD SUCCESSFUL.

---

### Task 3: Extract MCP Reference Parser Helpers

**Files:**
- Create: `shared/src/commonTest/kotlin/com/dailysatori/service/mcp/McpReferenceParserTest.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpReferenceParser.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpToolResultFormatter.kt`

- [ ] **Step 1: Write failing MCP reference parser tests**

Create `McpReferenceParserTest.kt` with this complete content:

```kotlin
package com.dailysatori.service.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpReferenceParserTest {
    @Test
    fun parsesReferenceIdsByTypeAndIgnoresMalformedEntries() {
        val refs = parseMcpReferenceIds("article_1, diary_2, book_3, book_viewpoint_4, bad, article_x")

        assertEquals(setOf(1L), refs.getValue("article"))
        assertEquals(setOf(2L), refs.getValue("diary"))
        assertEquals(setOf(3L), refs.getValue("book"))
        assertEquals(setOf(4L), refs.getValue("book_viewpoint"))
    }

    @Test
    fun emptyReferenceMapHasNoReferencedIds() {
        assertFalse(parseMcpReferenceIds("bad, article_x").hasMcpReferenceIds())
        assertTrue(parseMcpReferenceIds("article_1").hasMcpReferenceIds())
    }

    @Test
    fun matchesSearchResultsByReferenceTypeAndId() {
        val refs = parseMcpReferenceIds("article_1, diary_2")

        assertTrue(McpSearchResult(1, "article", "文章", null, null).matchesMcpReferenceIds(refs))
        assertTrue(McpSearchResult(2, "diary", "日记", null, null).matchesMcpReferenceIds(refs))
        assertFalse(McpSearchResult(3, "article", "文章", null, null).matchesMcpReferenceIds(refs))
        assertFalse(McpSearchResult(1, "unknown", "未知", null, null).matchesMcpReferenceIds(refs))
    }

    @Test
    fun noneReferenceContentFiltersAllResults() {
        assertTrue(mcpReferenceContentRequestsNoResults("none"))
        assertTrue(mcpReferenceContentRequestsNoResults(" NONE "))
        assertFalse(mcpReferenceContentRequestsNoResults("article_1"))
    }
}
```

- [ ] **Step 2: Run the focused parser test and verify it fails**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests com.dailysatori.service.mcp.McpReferenceParserTest
```

Expected: FAIL because `parseMcpReferenceIds`, `hasMcpReferenceIds`, `matchesMcpReferenceIds`, and `mcpReferenceContentRequestsNoResults` do not exist.

- [ ] **Step 3: Create `McpReferenceParser.kt`**

Create the file with this complete content:

```kotlin
package com.dailysatori.service.mcp

internal fun parseMcpReferenceIds(refsContent: String): Map<String, Set<Long>> {
    val ids = mutableMapOf(
        "article" to mutableSetOf<Long>(),
        "diary" to mutableSetOf<Long>(),
        "book" to mutableSetOf<Long>(),
        "book_viewpoint" to mutableSetOf<Long>(),
    )
    for (ref in refsContent.split(",").map { it.trim() }) {
        val match = Regex("(article|diary|book|book_viewpoint)_(\\d+)").find(ref) ?: continue
        val type = match.groupValues[1]
        val id = match.groupValues[2].toLongOrNull() ?: continue
        ids[type]?.add(id)
    }
    return ids
}

internal fun Map<String, Set<Long>>.hasMcpReferenceIds(): Boolean = values.sumOf { it.size } > 0

internal fun McpSearchResult.matchesMcpReferenceIds(referencedIds: Map<String, Set<Long>>): Boolean = when (type) {
    "article" -> referencedIds["article"]?.contains(id) == true
    "diary" -> referencedIds["diary"]?.contains(id) == true
    "book" -> referencedIds["book"]?.contains(id) == true
    "book_viewpoint" -> referencedIds["book_viewpoint"]?.contains(id) == true
    else -> false
}

internal fun mcpReferenceContentRequestsNoResults(refsContent: String): Boolean =
    refsContent.trim().lowercase() == "none"
```

- [ ] **Step 4: Update `McpToolResultFormatter.kt` reference filtering**

Replace this block in `filterRelevantMcpResults()`:

```kotlin
    if (refsContent.lowercase() == "none") return emptyList()
    if (refsContent.isEmpty()) return filterByTitleMatch(results, answer)

    val referencedIds = parseReferencedIds(refsContent)
    if (referencedIds.values.sumOf { it.size } == 0) {
        return filterByTitleMatch(results, answer)
    }

    val filtered = results.filter { r ->
        when (r.type) {
            "article" -> referencedIds["article"]?.contains(r.id) == true
            "diary" -> referencedIds["diary"]?.contains(r.id) == true
            "book" -> referencedIds["book"]?.contains(r.id) == true
            "book_viewpoint" -> referencedIds["book_viewpoint"]?.contains(r.id) == true
            else -> false
        }
    }
```

With:

```kotlin
    if (mcpReferenceContentRequestsNoResults(refsContent)) return emptyList()
    if (refsContent.isEmpty()) return filterByTitleMatch(results, answer)

    val referencedIds = parseMcpReferenceIds(refsContent)
    if (!referencedIds.hasMcpReferenceIds()) {
        return filterByTitleMatch(results, answer)
    }

    val filtered = results.filter { it.matchesMcpReferenceIds(referencedIds) }
```

Delete the old private `parseReferencedIds(refsContent: String)` function from `McpToolResultFormatter.kt`.

- [ ] **Step 5: Run focused MCP shared tests and verify they pass**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests com.dailysatori.service.mcp.McpReferenceParserTest --tests com.dailysatori.service.mcp.McpAgentPresentationTest --tests com.dailysatori.service.mcp.McpSearchResultPersistenceTest
```

Expected: BUILD SUCCESSFUL.

---

### Task 4: Full Verification And Physical Device Install

**Files:**
- Verify all modified files.

- [ ] **Step 1: Run shared unit tests**

Run:

```bash
./gradlew :shared:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run all app unit tests**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run Kotlin compile check**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Build debug APK**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Install only to the physical phone**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ANDROID_SERIAL=ba5e2328 ./gradlew :app:installDebug
```

Expected: BUILD SUCCESSFUL and installation targets device `ba5e2328`, not `emulator-5554`.

- [ ] **Step 6: Launch the app only on the physical phone**

Run:

```bash
adb -s ba5e2328 shell am start -n com.dailysatori/.MainActivity
```

Expected: `Starting: Intent { cmp=com.dailysatori/.MainActivity }` or equivalent successful launch output.

# Daily Summary Quality Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve daily unified news summary quality by filtering weak inputs, preserving source diversity, validating citation grounding, and keeping partial failures visible.

**Architecture:** Keep the existing `UnifiedNewsSummaryService` orchestration, but move deterministic input preparation and citation grounding into testable pure functions near the existing unified-news service code. Start with small top-level functions in existing files; only extract a new file if the implementation makes `UnifiedNewsSummaryService.kt` harder to scan.

**Tech Stack:** Kotlin Multiplatform shared module, Android app unit tests, Kotlin test assertions, existing Gradle tasks.

---

## File Map

- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt`
  - Replace simple count-based budgeting with source preparation that filters, deduplicates, budgets by source type, and truncates content.
  - Integrate prepared sources into `generate()` before AI configuration lookup.
  - Change invalid/ungrounded citation handling from “any invalid token fails” to “sanitize invalid references, then require at least one valid citation”.
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsPrompt.kt`
  - Add helper functions for valid citation checks and invalid citation removal.
  - Update prompt language to request a daily briefing structure while keeping Markdown list rendering compatible.
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`
  - Add pure function regression tests for source preparation, prompt shape, citation sanitization, and ungrounded summary rejection.
  - Update existing prompt text tests to match the new brief structure.

---

## Task 1: Source Preparation

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt`

- [ ] **Step 1: Write failing tests for source filtering, dedupe, and source diversity**

Add these imports to `UnifiedNewsBehaviorTest.kt` with the existing unified news imports:

```kotlin
import com.dailysatori.service.unifiednews.prepareUnifiedNewsSources
```

Add these tests near the existing `budgetUnifiedNewsSourcesLimitsCountAndContentLength` test:

```kotlin
@Test
fun prepareUnifiedNewsSourcesDropsBlankAndLowContentItems() {
    val sources = listOf(
        UnifiedNewsSourceItem(
            refKey = "R1",
            sourceType = UnifiedNewsSourceType.REMOTE_ARTICLE,
            title = " ",
            summary = "valid summary with enough detail",
            content = "valid content with enough detail",
        ),
        UnifiedNewsSourceItem(
            refKey = "R2",
            sourceType = UnifiedNewsSourceType.REMOTE_ARTICLE,
            title = "短内容",
            summary = "短",
            content = "少",
        ),
        UnifiedNewsSourceItem(
            refKey = "F1",
            sourceType = UnifiedNewsSourceType.LOCAL_FAVORITE,
            title = "有效收藏",
            summary = "这是一条包含足够信息量的收藏摘要",
            content = "这是一条包含足够信息量的收藏正文，用来参与每日汇总。",
        ),
    )

    val prepared = prepareUnifiedNewsSources(sources, minTextChars = 20)

    assertEquals(listOf("有效收藏"), prepared.map { it.title })
}

@Test
fun prepareUnifiedNewsSourcesDeduplicatesUrlAndTitleSourceKeepingRicherItem() {
    val sources = listOf(
        UnifiedNewsSourceItem(
            refKey = "R1",
            sourceType = UnifiedNewsSourceType.REMOTE_ARTICLE,
            sourceUrl = "https://example.com/same",
            title = "同一新闻",
            summary = "短摘要但有效",
            content = "短正文但有效内容超过阈值",
        ),
        UnifiedNewsSourceItem(
            refKey = "R2",
            sourceType = UnifiedNewsSourceType.REMOTE_ARTICLE,
            sourceUrl = "https://example.com/same",
            title = "同一新闻更新",
            summary = "更完整摘要，解释了事件的上下文和影响",
            content = "更完整正文，包含更多事实、背景、影响以及后续观察点。",
        ),
        UnifiedNewsSourceItem(
            refKey = "R3",
            sourceType = UnifiedNewsSourceType.REMOTE_ARTICLE,
            title = "重复标题",
            summary = "第一条重复标题摘要，信息量较少",
            content = "第一条重复标题正文，信息量较少。",
        ),
        UnifiedNewsSourceItem(
            refKey = "R4",
            sourceType = UnifiedNewsSourceType.REMOTE_ARTICLE,
            title = "重复标题",
            summary = "第二条重复标题摘要，信息量明显更多，保留这一条",
            content = "第二条重复标题正文，信息量明显更多，包含更多细节，应当保留。",
        ),
    )

    val prepared = prepareUnifiedNewsSources(sources, minTextChars = 10)

    assertEquals(setOf("同一新闻更新", "重复标题"), prepared.map { it.title }.toSet())
    assertTrue(prepared.single { it.title == "重复标题" }.content.contains("应当保留"))
}

@Test
fun prepareUnifiedNewsSourcesLimitsPerSourceTypeBeforeGlobalBudget() {
    val remote = (1..8).map { index ->
        UnifiedNewsSourceItem(
            refKey = "R$index",
            sourceType = UnifiedNewsSourceType.REMOTE_ARTICLE,
            title = "远程新闻 $index",
            summary = "远程摘要 $index 信息量充足",
            content = "远程正文 $index 信息量充足，用来验证来源预算不会被单一来源占满。",
            sourceTime = 1000L - index,
        )
    }
    val favorites = (1..2).map { index ->
        UnifiedNewsSourceItem(
            refKey = "F$index",
            sourceType = UnifiedNewsSourceType.LOCAL_FAVORITE,
            title = "收藏新闻 $index",
            summary = "收藏摘要 $index 信息量充足",
            content = "收藏正文 $index 信息量充足，用来验证收藏来源会被保留。",
            sourceTime = 2000L - index,
        )
    }

    val prepared = prepareUnifiedNewsSources(
        sources = remote + favorites,
        maxSources = 5,
        maxPerSourceType = 3,
        minTextChars = 10,
    )

    assertEquals(5, prepared.size)
    assertEquals(3, prepared.count { it.sourceType == UnifiedNewsSourceType.REMOTE_ARTICLE })
    assertEquals(2, prepared.count { it.sourceType == UnifiedNewsSourceType.LOCAL_FAVORITE })
}
```

- [ ] **Step 2: Run the failing source preparation tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.prepareUnifiedNewsSourcesDropsBlankAndLowContentItems --tests com.dailysatori.UnifiedNewsBehaviorTest.prepareUnifiedNewsSourcesDeduplicatesUrlAndTitleSourceKeepingRicherItem --tests com.dailysatori.UnifiedNewsBehaviorTest.prepareUnifiedNewsSourcesLimitsPerSourceTypeBeforeGlobalBudget --no-configuration-cache
```

Expected: FAIL because `prepareUnifiedNewsSources` does not exist.

- [ ] **Step 3: Implement source preparation as a pure function**

In `UnifiedNewsSummaryService.kt`, replace the current `budgetUnifiedNewsSources` and `deduplicateUnifiedNewsSources` functions with this implementation. Keep existing functions below it unchanged.

```kotlin
fun prepareUnifiedNewsSources(
    sources: List<UnifiedNewsSourceItem>,
    maxSources: Int = 30,
    maxContentChars: Int = 8000,
    maxPerSourceType: Int = 18,
    minTextChars: Int = 40,
): List<UnifiedNewsSourceItem> {
    val useful = sources
        .filter { it.title.isNotBlank() }
        .filter { it.usefulTextLength() >= minTextChars }
    val deduped = deduplicateUnifiedNewsSources(useful)
    return deduped
        .sortedWith(compareByDescending<UnifiedNewsSourceItem> { it.sourceTime ?: 0L }.thenByDescending { it.usefulTextLength() })
        .groupBy { it.sourceType }
        .values
        .flatMap { it.take(maxPerSourceType) }
        .sortedWith(compareByDescending<UnifiedNewsSourceItem> { it.sourceTime ?: 0L }.thenByDescending { it.usefulTextLength() })
        .take(maxSources)
        .map { source -> source.copy(content = source.content.take(maxContentChars)) }
}

fun budgetUnifiedNewsSources(
    sources: List<UnifiedNewsSourceItem>,
    maxSources: Int = 30,
    maxContentChars: Int = 8000,
): List<UnifiedNewsSourceItem> = prepareUnifiedNewsSources(
    sources = sources,
    maxSources = maxSources,
    maxContentChars = maxContentChars,
)

fun deduplicateUnifiedNewsSources(items: List<UnifiedNewsSourceItem>): List<UnifiedNewsSourceItem> =
    items
        .groupBy { it.dedupeKey() }
        .values
        .map { candidates -> candidates.maxBy { it.usefulTextLength() } }

private fun UnifiedNewsSourceItem.dedupeKey(): String {
    val normalizedUrl = sourceUrl?.trim()?.lowercase().orEmpty()
    if (normalizedUrl.isNotBlank()) return "url:$normalizedUrl"
    return "title:${sourceType.dbValue}:${title.trim().lowercase()}"
}

private fun UnifiedNewsSourceItem.usefulTextLength(): Int =
    listOf(title, summary, content)
        .joinToString(" ")
        .trim()
        .length
```

- [ ] **Step 4: Run source preparation tests again**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.prepareUnifiedNewsSourcesDropsBlankAndLowContentItems --tests com.dailysatori.UnifiedNewsBehaviorTest.prepareUnifiedNewsSourcesDeduplicatesUrlAndTitleSourceKeepingRicherItem --tests com.dailysatori.UnifiedNewsBehaviorTest.prepareUnifiedNewsSourcesLimitsPerSourceTypeBeforeGlobalBudget --no-configuration-cache
```

Expected: PASS.

- [ ] **Step 5: Run existing budget compatibility test**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.budgetUnifiedNewsSourcesLimitsCountAndContentLength --no-configuration-cache
```

Expected: PASS. If it fails because old fixtures are too short for the new usefulness filter, update only the fixture strings in that existing test so they contain at least 40 useful characters.

- [ ] **Step 6: Commit source preparation**

Run:

```bash
git add app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt
git commit -m "refactor: prepare unified news sources before summarizing"
```

---

## Task 2: Prompt Shape and Citation Grounding

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsPrompt.kt`

- [ ] **Step 1: Write failing tests for prompt structure and citation sanitizing**

Add this import to `UnifiedNewsBehaviorTest.kt`:

```kotlin
import com.dailysatori.service.unifiednews.hasValidCitationTokens
import com.dailysatori.service.unifiednews.removeInvalidCitationTokens
```

Add these tests near the existing citation validation tests:

```kotlin
@Test
fun citationGroundingRequiresAtLeastOneValidToken() {
    val sources = listOf(
        UnifiedNewsSourceItem(refKey = "R1", sourceType = UnifiedNewsSourceType.REMOTE_ARTICLE, title = "远程", summary = "摘要"),
    )

    assertTrue(hasValidCitationTokens("有效事实 [R1]", sources))
    assertFalse(hasValidCitationTokens("没有引用的事实", sources))
    assertFalse(hasValidCitationTokens("无效事实 [R99]", sources))
}

@Test
fun removeInvalidCitationTokensDropsOnlyMissingSourceReferences() {
    val sources = listOf(
        UnifiedNewsSourceItem(refKey = "R1", sourceType = UnifiedNewsSourceType.REMOTE_ARTICLE, title = "远程", summary = "摘要"),
        UnifiedNewsSourceItem(refKey = "F1", sourceType = UnifiedNewsSourceType.LOCAL_FAVORITE, title = "收藏", summary = "摘要"),
    )
    val content = "有效事实 [R1][R99]\n收藏背景 [F1][X2]\n[原文](https://example.com)"

    val sanitized = removeInvalidCitationTokens(content, sources)

    assertTrue(sanitized.contains("[R1]"))
    assertTrue(sanitized.contains("[F1]"))
    assertFalse(sanitized.contains("[R99]"))
    assertFalse(sanitized.contains("[X2]"))
    assertTrue(sanitized.contains("[原文](https://example.com)"))
}

@Test
fun unifiedNewsPromptRequestsDailyBriefingStructure() {
    val prompt = buildUnifiedNewsPrompt(
        window = UnifiedNewsWindow(
            key = UnifiedNewsWindowKey.DAILY,
            summaryDate = "2026-05-18",
            startMs = 0,
            endMs = 1,
        ),
        sources = listOf(
            UnifiedNewsSourceItem(
                refKey = "R1",
                sourceType = UnifiedNewsSourceType.REMOTE_ARTICLE,
                title = "OpenAI 发布更新",
                summary = "OpenAI 发布产品更新",
                content = "OpenAI 发布产品更新，影响开发者工具链。",
            ),
        ),
    )

    assertTrue(prompt.contains("今日要点"))
    assertTrue(prompt.contains("重要变化"))
    assertTrue(prompt.contains("值得关注"))
    assertTrue(prompt.contains("不要编造事实"))
    assertTrue(prompt.contains("每个关键判断都必须带引用"))
    assertTrue(prompt.contains("- 新闻标题或短句 [R1]"))
}
```

- [ ] **Step 2: Run the failing prompt and citation tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.citationGroundingRequiresAtLeastOneValidToken --tests com.dailysatori.UnifiedNewsBehaviorTest.removeInvalidCitationTokensDropsOnlyMissingSourceReferences --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsPromptRequestsDailyBriefingStructure --no-configuration-cache
```

Expected: FAIL because `hasValidCitationTokens`, `removeInvalidCitationTokens`, or the new prompt strings do not exist yet.

- [ ] **Step 3: Implement citation grounding helpers and prompt update**

In `UnifiedNewsPrompt.kt`, keep `citationTokens()` and `invalidCitationTokens()` compatible, then add these helpers below `invalidCitationTokens()`:

```kotlin
fun hasValidCitationTokens(content: String, sources: List<UnifiedNewsSourceItem>): Boolean {
    val valid = sources.map { it.refKey }.toSet()
    return citationTokens(content).any { it in valid }
}

fun removeInvalidCitationTokens(content: String, sources: List<UnifiedNewsSourceItem>): String {
    val valid = sources.map { it.refKey }.toSet()
    return CitationLikeRegex.replace(content) { match ->
        val token = match.groupValues[1]
        when {
            match.isMarkdownInlineLinkLabel(content) -> match.value
            match.isMarkdownReferenceLinkLabel(content) -> match.value
            match.isMarkdownReferenceLabel(content) -> match.value
            token in valid -> match.value
            else -> ""
        }
    }
}
```

Replace the returned prompt body in `buildUnifiedNewsPrompt()` with this text:

```kotlin
return """请基于以下来源，生成中文 Markdown 每日新闻简报。

要求：
1. 只能使用给定来源，不要编造事实。
2. 每个关键判断都必须带引用，例如 [R1][F2]。
3. 引用必须完全匹配来源编号，不要创造不存在的编号。
4. 输出结构使用这三个二级标题：`## 今日要点`、`## 重要变化`、`## 值得关注`。
5. 每个二级标题下面使用 Markdown 无序列表，格式为 `- 新闻标题或短句 [R1]`，不要输出无列表符号的长段落。
6. 优先做跨来源综合，不要机械逐条复述来源。
7. 对远程来源优先使用来源标题，保持短句，不要改写成长摘要。
8. 如果来源不足以支持可靠判断，请明确说明无法可靠生成，不要猜测。
9. 不要输出总标题。

日期: ${window.summaryDate}
窗口: ${window.key.value}

来源：
$sourceText""".trimIndent()
```

- [ ] **Step 4: Run prompt and citation tests again**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.citationGroundingRequiresAtLeastOneValidToken --tests com.dailysatori.UnifiedNewsBehaviorTest.removeInvalidCitationTokensDropsOnlyMissingSourceReferences --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsPromptRequestsDailyBriefingStructure --no-configuration-cache
```

Expected: PASS.

- [ ] **Step 5: Update older prompt text tests if they assert removed dynamic-category copy**

Search failures from the previous command or run this broader prompt subset:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest --no-configuration-cache
```

Expected: Some existing text-based prompt tests may fail because the prompt no longer asks for dynamic 3-5 categories. Update those assertions to require the new fixed daily briefing headings and keep the existing compatibility assertions for `不要输出总标题`, `- 新闻标题或短句 [R1]`, and `对远程来源优先使用来源标题`.

Concrete replacement for any test that currently requires `按今天新闻内容动态合并为 3-5 个大类`:

```kotlin
assertTrue(prompt.contains("## 今日要点"))
assertTrue(prompt.contains("## 重要变化"))
assertTrue(prompt.contains("## 值得关注"))
assertTrue(prompt.contains("优先做跨来源综合"))
```

- [ ] **Step 6: Commit prompt and citation grounding**

Run:

```bash
git add app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsPrompt.kt
git commit -m "feat: ground unified news summaries with valid citations"
```

---

## Task 3: Service Integration and Partial-Failure Behavior

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt`

- [ ] **Step 1: Write failing source-level regression tests for service integration**

Add this test near `unifiedNewsSummaryServicePersistsInvalidCitationFailures` or the existing service source-text tests:

```kotlin
@Test
fun unifiedNewsSummaryServicePreparesSourcesBeforeAiPromptAndRejectsUngroundedOutput() {
    val source = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt").readText()

    assertTrue(source.contains("val preparedSources = prepareUnifiedNewsSources("))
    assertTrue(source.contains("if (preparedSources.isEmpty()) return persistEmpty(window, warnings)"))
    assertTrue(source.contains("buildUnifiedNewsPrompt(window, preparedSources)"))
    assertTrue(source.contains("removeInvalidCitationTokens(content, preparedSources)"))
    assertTrue(source.contains("hasValidCitationTokens(sanitizedContent, preparedSources)"))
    assertTrue(source.contains("AI 返回内容缺少有效来源引用"))
    assertFalse(source.contains("val invalid = invalidCitationTokens(content, sources)"))
}
```

Add this test near warning or banner tests:

```kotlin
@Test
fun unifiedNewsPartialFailureWarningIsUserFacingAndNonTechnical() {
    val source = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt").readText()

    assertTrue(source.contains("部分新闻来源暂时不可用，本次汇总基于已获取内容生成"))
    assertFalse(source.contains("skip the AI call"))
}
```

Add this test near the same service source-text tests:

```kotlin
@Test
fun unifiedNewsAllSourceFailureStillReturnsFailurePath() {
    val source = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt").readText()

    assertTrue(source.contains("catch (e: Exception)"))
    assertTrue(source.contains("Unified news source collection failed"))
    assertTrue(source.contains("return saveFailure(window, emptyList(), warnings, \"新闻来源收集失败，请稍后重试\")"))
}
```

- [ ] **Step 2: Run the failing service integration tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsSummaryServicePreparesSourcesBeforeAiPromptAndRejectsUngroundedOutput --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsPartialFailureWarningIsUserFacingAndNonTechnical --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsAllSourceFailureStillReturnsFailurePath --no-configuration-cache
```

Expected: FAIL because service integration still uses `budgetUnifiedNewsSources(collectSources(...))`, raw `content`, and the old empty message. The all-source failure test may already pass; keep it as regression coverage.

- [ ] **Step 3: Integrate prepared sources and grounded content in `generate()`**

In `UnifiedNewsSummaryService.kt`, replace this block in `generate()`:

```kotlin
val warnings = mutableListOf<String>()
val sources = try {
    budgetUnifiedNewsSources(collectSources(window, warnings, ignoreSourceTimeFilter))
} catch (e: Exception) {
    log.w(e) { "Unified news source collection failed" }
    return saveFailure(window, emptyList(), warnings, "新闻来源收集失败，请稍后重试")
}
if (sources.isEmpty()) return persistEmpty(window, warnings)
```

with:

```kotlin
val warnings = mutableListOf<String>()
val sources = try {
    collectSources(window, warnings, ignoreSourceTimeFilter)
} catch (e: Exception) {
    log.w(e) { "Unified news source collection failed" }
    return saveFailure(window, emptyList(), warnings, "新闻来源收集失败，请稍后重试")
}
val preparedSources = prepareUnifiedNewsSources(sources)
if (preparedSources.isEmpty()) return persistEmpty(window, warnings)
```

Replace downstream references in `generate()`:

```kotlin
if (config == null) return saveFailure(window, sources, warnings, "请先配置默认 AI 服务")
```

with:

```kotlin
if (config == null) return saveFailure(window, preparedSources, warnings, "请先配置默认 AI 服务")
```

Replace the `aiService.summarize` prompt argument:

```kotlin
content = buildUnifiedNewsPrompt(window, sources),
```

with:

```kotlin
content = buildUnifiedNewsPrompt(window, preparedSources),
```

Replace the catch failure source argument:

```kotlin
return saveFailure(window, sources, warnings, aiGenerationFailureMessage(e))
```

with:

```kotlin
return saveFailure(window, preparedSources, warnings, aiGenerationFailureMessage(e))
```

Replace the invalid citation block:

```kotlin
val invalid = invalidCitationTokens(content, sources)
if (invalid.isNotEmpty()) {
    return saveFailure(window, sources, warnings, "AI 返回了无效引用：${invalid.joinToString()}")
}
return persistSuccess(window, sources, warnings, content)
```

with:

```kotlin
val sanitizedContent = removeInvalidCitationTokens(content, preparedSources)
if (!hasValidCitationTokens(sanitizedContent, preparedSources)) {
    return saveFailure(window, preparedSources, warnings, "AI 返回内容缺少有效来源引用，请稍后重试")
}
return persistSuccess(window, preparedSources, warnings, sanitizedContent)
```

- [ ] **Step 4: Make warnings user-facing and remove the mixed-language empty message**

In `UnifiedNewsSummaryService.kt`, replace `warnRemoteSourceFailure()` with:

```kotlin
private fun warnRemoteSourceFailure(warnings: MutableList<String>, sourceName: String, message: String) {
    log.w { "$sourceName: $message" }
    warnings += "部分新闻来源暂时不可用，本次汇总基于已获取内容生成"
}
```

Then replace `persistEmpty()` message:

```kotlin
val message = "当前时间窗口暂无可总结新闻，skip the AI call"
```

with:

```kotlin
val message = "当前时间窗口暂无足够可靠的新闻内容可总结"
```

Keep `warnSourceFailure()` only if other code still calls it. If it becomes unused, delete it.

- [ ] **Step 5: Run service integration tests again**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsSummaryServicePreparesSourcesBeforeAiPromptAndRejectsUngroundedOutput --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsPartialFailureWarningIsUserFacingAndNonTechnical --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsAllSourceFailureStillReturnsFailurePath --no-configuration-cache
```

Expected: PASS.

- [ ] **Step 6: Run full behavior test class**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest --no-configuration-cache
```

Expected: PASS. Fix only regressions caused by this change; do not alter unrelated tests.

- [ ] **Step 7: Commit service integration**

Run:

```bash
git add app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt
git commit -m "feat: improve daily summary source quality"
```

---

## Task 4: Final Verification

**Files:**
- No planned file edits.

- [ ] **Step 1: Run quick project checks**

Run:

```bash
./test.sh quick
```

Expected: PASS.

- [ ] **Step 2: Run required Kotlin compile check**

Run:

```bash
./gradlew :app:compileDebugKotlin --no-configuration-cache
```

Expected: PASS.

- [ ] **Step 3: Run app and shared unit tests**

Run:

```bash
./gradlew :app:testDebugUnitTest :shared:testDebugUnitTest --no-configuration-cache
```

Expected: PASS.

- [ ] **Step 4: Build debug APK if previous checks pass**

Run:

```bash
./gradlew :app:assembleDebug --no-configuration-cache
```

Expected: PASS.

- [ ] **Step 5: Check git status**

Run:

```bash
git status --short --branch
```

Expected: current branch is `refactor/staged-project-cleanup`; no uncommitted files remain after the implementation commits.

---

## Notes for Implementers

- Do not change database schema.
- Do not change visible navigation or article detail routes.
- Keep UI changes out of this slice unless a compile error requires an import or call-site adjustment.
- Avoid running multiple Gradle commands in parallel; previous runs hit incremental storage/cache conflicts.
- If a connected Android device is unavailable, record that install verification was skipped because `adb devices` had no target.

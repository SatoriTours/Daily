# Diary AI Enrichment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an opt-in diary editor assistant that enriches selected knowledge with sourced AI context and extracts concise content from pasted article or Douyin links through a safe preview workflow.

**Architecture:** A shared `DiaryAssistantService` classifies a selection, delegates to a knowledge enricher or link extractor, and returns a platform-neutral preview model. Android Compose owns selection snapshots, URL-paste prompting, conflict detection, preview editing, and insertion; the service never writes diary data directly.

**Tech Stack:** Kotlin Multiplatform, Kotlin coroutines, Ktor, kotlinx.serialization, Compose Material 3, Koin, existing `AiService`, `RemoteMcpClient`, `McpServerRepository`, and `WebpageParserService`.

**Spec:** `docs/superpowers/specs/2026-08-29-diary-ai-enrichment-design.md`

## Global Constraints

- Pasting or opening the editor must perform zero network, AI, or third-party API requests.
- Send only selected text, at most 240 characters of context on each side, or the explicitly confirmed URL; never send the full diary by default.
- Every generated result must be previewed before modifying editor text or diary persistence.
- Keep at most three Markdown source links and expose whether the result was web-verified, model-only, page-extracted, or metadata-only.
- Douyin support may read only publicly accessible share pages; it must not bypass login, call private signature APIs, or download video/audio.
- Cache successful normalized URLs only for the lifetime of one editor session.
- Preserve unrelated dirty-worktree changes and stage only files or hunks belonging to this feature.

---

## File Structure

- Create `shared/src/commonMain/kotlin/com/dailysatori/service/diary/DiaryAssistantModels.kt`: stable request/result/source/status contracts and URL classification.
- Create `shared/src/commonMain/kotlin/com/dailysatori/service/diary/DiaryAssistantAiFormat.kt`: prompt construction and strict/fallback AI-response parsing.
- Create `shared/src/commonMain/kotlin/com/dailysatori/service/diary/DiaryLinkExtractor.kt`: ordinary webpage extraction and public Douyin metadata/subtitle parsing.
- Create `shared/src/commonMain/kotlin/com/dailysatori/service/diary/DiaryKnowledgeEnricher.kt`: remote MCP web evidence collection and model-only fallback.
- Create `shared/src/commonMain/kotlin/com/dailysatori/service/diary/DiaryAssistantService.kt`: request routing, bounded inputs, and result orchestration.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`: register the new services.
- Create `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryAssistantEditorState.kt`: pure paste detection, snapshots, conflict checks, insert/replace transforms, and session URL cache.
- Create `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryAssistantPreviewSheet.kt`: loading/error/preview UI.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorToolbar.kt`: add the AI action.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorSheet.kt`: run/cancel tasks and integrate preview safely.
- Add focused common and Android unit tests beside the new units; do not require network in tests.

---

### Task 1: Assistant Contracts, URL Detection, and Input Privacy

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/diary/DiaryAssistantModels.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/diary/DiaryAssistantModelsTest.kt`

**Interfaces:**
- Produces: `DiaryAssistantRequest`, `DiaryAssistantResult`, `DiaryAssistantSource`, `DiaryAssistantVerification`, `DiaryAssistantExtraction`, `DiaryAssistantTarget`, `detectDiaryAssistantUrl(String): String?`, `normalizeDiaryAssistantUrl(String): String`, and `boundedDiaryAssistantContext(String, IntRange): Pair<String, String>`.

- [ ] **Step 1: Write failing contract and privacy tests**

```kotlin
@Test
fun detectsOnlyHttpLinksAndNormalizesTrailingPunctuation() {
    assertEquals("https://v.douyin.com/abc/", detectDiaryAssistantUrl("看看 https://v.douyin.com/abc/。"))
    assertNull(detectDiaryAssistantUrl("ftp://private.example/file"))
}

@Test
fun contextNeverIncludesWholeLongDiary() {
    val diary = "A".repeat(500) + "selected" + "B".repeat(500)
    val (before, after) = boundedDiaryAssistantContext(diary, 500 until 508)
    assertEquals(240, before.length)
    assertEquals(240, after.length)
    assertFalse((before + after).contains("selected"))
}

@Test
fun classifiesDouyinAndOrdinaryWebTargets() {
    assertEquals(DiaryAssistantTarget.DOUYIN, diaryAssistantTarget("https://v.douyin.com/a/"))
    assertEquals(DiaryAssistantTarget.WEBPAGE, diaryAssistantTarget("https://example.com/a"))
    assertEquals(DiaryAssistantTarget.KNOWLEDGE, diaryAssistantTarget(null))
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./gradlew :shared:testDebugUnitTest --tests 'com.dailysatori.service.diary.DiaryAssistantModelsTest'`

Expected: compilation fails because the assistant contracts and helpers do not exist.

- [ ] **Step 3: Implement minimal models and bounded helpers**

```kotlin
data class DiaryAssistantRequest(
    val selectedText: String,
    val contextBefore: String = "",
    val contextAfter: String = "",
    val url: String? = null,
    val allowModelKnowledgeFallback: Boolean = false,
)

data class DiaryAssistantSource(val title: String, val url: String)
enum class DiaryAssistantVerification { WEB_VERIFIED, MODEL_ONLY, PAGE_EXTRACTED }
enum class DiaryAssistantExtraction { FULL_TEXT, PUBLIC_METADATA, NO_SUBTITLES }
enum class DiaryAssistantTarget { KNOWLEDGE, WEBPAGE, DOUYIN }

data class DiaryAssistantResult(
    val content: String,
    val sources: List<DiaryAssistantSource>,
    val verification: DiaryAssistantVerification,
    val extraction: DiaryAssistantExtraction? = null,
    val warnings: List<String> = emptyList(),
)
```

Use a single HTTP/HTTPS regex, remove only sentence-ending punctuation outside the URL, recognize `douyin.com` and `iesdouyin.com` hosts case-insensitively, and clamp each adjacent context segment to 240 characters.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run: `./gradlew :shared:testDebugUnitTest --tests 'com.dailysatori.service.diary.DiaryAssistantModelsTest'`

Expected: all model/helper tests pass.

- [ ] **Step 5: Commit the contracts**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/diary/DiaryAssistantModels.kt shared/src/commonTest/kotlin/com/dailysatori/service/diary/DiaryAssistantModelsTest.kt
git commit -m "feat: add diary assistant contracts"
```

---

### Task 2: Deterministic AI Prompt and Response Formatting

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/diary/DiaryAssistantAiFormat.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/diary/DiaryAssistantAiFormatTest.kt`

**Interfaces:**
- Consumes: `DiaryAssistantSource` and status enums from Task 1.
- Produces: `buildDiaryKnowledgePrompt(...)`, `buildDiaryLinkSummaryPrompt(...)`, `parseDiaryAssistantAiResponse(String, fallbackSources): DiaryAssistantParsedAi`, and `renderDiaryAssistantMarkdown(...)`.

- [ ] **Step 1: Write failing format tests**

```kotlin
@Test
fun parserCapsAndDeduplicatesSources() {
    val parsed = parseDiaryAssistantAiResponse(
        """{"content":"背景说明","sources":[{"title":"A","url":"https://a.example"},{"title":"A2","url":"https://a.example"},{"title":"B","url":"https://b.example"},{"title":"C","url":"https://c.example"},{"title":"D","url":"https://d.example"}]}""",
        emptyList(),
    )
    assertEquals(3, parsed.sources.size)
    assertEquals(listOf("https://a.example", "https://b.example", "https://c.example"), parsed.sources.map { it.url })
}

@Test
fun markdownAppendsCompactSources() {
    assertEquals("背景说明\n\n来源：[资料 A](https://a.example)", renderDiaryAssistantMarkdown("背景说明", listOf(DiaryAssistantSource("资料 A", "https://a.example"))))
}
```

Also assert that prompts contain the selected text and bounded context but never interpolate an unrelated full diary fixture.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `./gradlew :shared:testDebugUnitTest --tests 'com.dailysatori.service.diary.DiaryAssistantAiFormatTest'`

Expected: compilation fails because format functions do not exist.

- [ ] **Step 3: Implement JSON-first parsing with a safe text fallback**

Require AI output in this form:

```json
{"content":"简洁补充正文","sources":[{"title":"来源标题","url":"https://example.com"}]}
```

Strip code fences before JSON parsing. Accept only nonblank `http://` or `https://` source URLs, deduplicate by normalized URL, and keep the first three. If JSON parsing fails, treat the nonblank response as content and use extractor/search-provided fallback sources; never manufacture URLs.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run: `./gradlew :shared:testDebugUnitTest --tests 'com.dailysatori.service.diary.DiaryAssistantAiFormatTest'`

Expected: all prompt, parsing, cap, and Markdown tests pass.

- [ ] **Step 5: Commit formatting**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/diary/DiaryAssistantAiFormat.kt shared/src/commonTest/kotlin/com/dailysatori/service/diary/DiaryAssistantAiFormatTest.kt
git commit -m "feat: format diary assistant results"
```

---

### Task 3: Ordinary Webpage and Public Douyin Extraction

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/diary/DiaryLinkExtractor.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/diary/DiaryLinkExtractorTest.kt`

**Interfaces:**
- Consumes: `WebpageParserService.extractContent(String): ExtractedContent` and Task 1 result types.
- Produces: `DiaryLinkMaterial`, `DiaryLinkContentExtractor`, `DefaultDiaryLinkContentExtractor`, and pure `parsePublicDouyinMaterial(url, ExtractedContent): DiaryLinkMaterial`.

- [ ] **Step 1: Write failing extractor tests using a fake page loader**

```kotlin
@Test
fun ordinaryPageUsesReadableTitleAndContent() = runBlocking {
    val extractor = DefaultDiaryLinkContentExtractor(fetch = {
        ExtractedContent("文章标题", "正文内容", "<html/>", null)
    })
    val result = extractor.extract("https://example.com/post")
    assertEquals("文章标题", result.title)
    assertEquals("正文内容", result.text)
    assertEquals(DiaryAssistantExtraction.FULL_TEXT, result.extraction)
}

@Test
fun douyinWithoutCaptionReturnsExplicitDegradedMaterial() {
    val result = parsePublicDouyinMaterial(
        "https://v.douyin.com/a/",
        ExtractedContent("作者的视频", "公开文案", """<meta property="og:description" content="公开文案">""", null),
    )
    assertEquals(DiaryAssistantExtraction.NO_SUBTITLES, result.extraction)
    assertTrue("未获取到视频字幕" in result.warnings)
}
```

Add fixtures for JSON-LD title/author/description, a public caption field, invalid/blank pages, and cancellation propagation.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `./gradlew :shared:testDebugUnitTest --tests 'com.dailysatori.service.diary.DiaryLinkExtractorTest'`

Expected: compilation fails because link extraction types do not exist.

- [ ] **Step 3: Implement the extractor behind an injectable fetch function**

```kotlin
data class DiaryLinkMaterial(
    val url: String,
    val title: String,
    val author: String? = null,
    val text: String,
    val extraction: DiaryAssistantExtraction,
    val warnings: List<String> = emptyList(),
)

fun interface DiaryLinkContentExtractor {
    suspend fun extract(url: String): DiaryLinkMaterial
}
```

Production construction passes `webpageParserService::extractContent`. For ordinary pages, require nonblank usable content and cap material at 20,000 characters. For Douyin, parse only already loaded public HTML/text: JSON-LD, Open Graph fields, and explicit caption/subtitle text. Prefer caption over description; if no caption exists, return title/author/description with `NO_SUBTITLES`. Propagate `CancellationException`; convert empty or blocked content to a typed `DiaryAssistantExtractionException`.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run: `./gradlew :shared:testDebugUnitTest --tests 'com.dailysatori.service.diary.DiaryLinkExtractorTest'`

Expected: webpage, Douyin, degradation, blank-page, and cancellation tests pass.

- [ ] **Step 5: Commit link extraction**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/diary/DiaryLinkExtractor.kt shared/src/commonTest/kotlin/com/dailysatori/service/diary/DiaryLinkExtractorTest.kt
git commit -m "feat: extract diary link content"
```

---

### Task 4: Sourced Knowledge Enrichment and Unified Orchestration

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/diary/DiaryKnowledgeEnricher.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/diary/DiaryAssistantService.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/diary/DiaryAssistantServiceTest.kt`

**Interfaces:**
- Consumes: Tasks 1–3, `RemoteMcpClient.collectWebSearchNotes`, `McpServerRepository.getEnabled`, `AiConfigService.getDefaultConfig`, and `AiService.complete`.
- Produces: `DiaryKnowledgeEnricher.enrich(request)`, `DiaryAssistantService.run(request)`, and `DiaryAssistantFallbackRequiredException`.

- [ ] **Step 1: Write failing orchestration tests with fake dependencies**

```kotlin
@Test
fun knowledgeUsesWebEvidenceAndReturnsVerifiedSources() = runBlocking {
    val service = assistant(searchNotes = "资料：https://history.example/source", aiText = verifiedJson)
    val result = service.run(DiaryAssistantRequest(selectedText = "安史之乱"))
    assertEquals(DiaryAssistantVerification.WEB_VERIFIED, result.verification)
    assertEquals("https://history.example/source", result.sources.single().url)
}

@Test
fun searchFailureRequiresExplicitFallbackConsent() = runBlocking {
    val service = assistant(searchNotes = "", aiText = modelOnlyJson)
    assertFailsWith<DiaryAssistantFallbackRequiredException> {
        service.run(DiaryAssistantRequest(selectedText = "某个概念"))
    }
    val result = service.run(DiaryAssistantRequest(selectedText = "某个概念", allowModelKnowledgeFallback = true))
    assertEquals(DiaryAssistantVerification.MODEL_ONLY, result.verification)
}

@Test
fun linkRequestRoutesToExtractorAndKeepsOriginalUrl() = runBlocking {
    val result = assistant(linkMaterial = articleMaterial).run(
        DiaryAssistantRequest(selectedText = "https://example.com/post", url = "https://example.com/post"),
    )
    assertEquals(DiaryAssistantVerification.PAGE_EXTRACTED, result.verification)
    assertTrue(result.sources.any { it.url == "https://example.com/post" })
}
```

Also test missing AI configuration, empty AI output, maximum context enforcement, three-source cap, and cancellation propagation.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `./gradlew :shared:testDebugUnitTest --tests 'com.dailysatori.service.diary.DiaryAssistantServiceTest'`

Expected: compilation fails because the enricher and service do not exist.

- [ ] **Step 3: Implement knowledge enrichment and route requests**

Define small injectable functions for tests:

```kotlin
class DiaryKnowledgeEnricher(
    private val collectWebNotes: suspend (String) -> String,
    private val complete: suspend (prompt: String, systemPrompt: String) -> String,
)

class DiaryAssistantService(
    private val knowledgeEnricher: DiaryKnowledgeEnricher,
    private val linkExtractor: DiaryLinkContentExtractor,
    private val summarize: suspend (prompt: String, systemPrompt: String) -> String,
) {
    suspend fun run(request: DiaryAssistantRequest): DiaryAssistantResult
}
```

The Koin production factory must obtain enabled HTTP MCP servers from `McpServerRepository`, call `RemoteMcpClient.collectWebSearchNotes`, and call `AiService.complete` with the current default AI configuration. If web notes contain no usable HTTP source, throw `DiaryAssistantFallbackRequiredException` unless `allowModelKnowledgeFallback` is true. Link summarization always retains the normalized original URL as a fallback source.

- [ ] **Step 4: Register production dependencies**

Add Koin singletons in `SharedModule.kt` for `DiaryLinkContentExtractor`, `DiaryKnowledgeEnricher`, and `DiaryAssistantService`. Use typed constructor arguments or named lambdas so `AiService.complete` wiring is unambiguous. Do not add a database table or background task.

- [ ] **Step 5: Run service and DI tests**

Run: `./gradlew :shared:testDebugUnitTest --tests 'com.dailysatori.service.diary.DiaryAssistantServiceTest' :shared:compileDebugKotlinAndroid`

Expected: orchestration tests pass and Android compilation confirms the three Koin registrations resolve their declared types.

- [ ] **Step 6: Commit the shared service**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/diary/DiaryKnowledgeEnricher.kt shared/src/commonMain/kotlin/com/dailysatori/service/diary/DiaryAssistantService.kt shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt shared/src/commonTest/kotlin/com/dailysatori/service/diary/DiaryAssistantServiceTest.kt
git commit -m "feat: enrich diary selections with AI"
```

---

### Task 5: Pure Editor Snapshots, Paste Prompt, Cache, and Text Transforms

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryAssistantEditorState.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/diary/DiaryAssistantEditorStateTest.kt`

**Interfaces:**
- Consumes: Task 1 request/result models and Compose `TextFieldValue`/`TextRange`.
- Produces: `DiaryAssistantSelectionSnapshot`, `detectNewlyPastedDiaryUrl`, `canReplaceDiaryAssistantSelection`, `insertDiaryAssistantResult`, `replaceDiaryAssistantSelection`, and `DiaryAssistantSessionCache`.

- [ ] **Step 1: Write failing editor-state tests**

```kotlin
@Test
fun pasteDetectionDoesNotInvokeAnyLoader() {
    assertEquals("https://example.com/a", detectNewlyPastedDiaryUrl("before", "before https://example.com/a"))
    assertNull(detectNewlyPastedDiaryUrl("same https://example.com/a", "same https://example.com/a"))
}

@Test
fun changedOriginalSelectionCannotBeReplaced() {
    val snapshot = DiaryAssistantSelectionSnapshot("hello world", TextRange(6, 11), "world")
    assertFalse(canReplaceDiaryAssistantSelection(TextFieldValue("hello earth"), snapshot))
}

@Test
fun insertionAndReplacementAreSingleUndoableValues() {
    val snapshot = DiaryAssistantSelectionSnapshot("hello world", TextRange(6, 11), "world")
    assertEquals("hello world\n\nbackground", insertDiaryAssistantResult(TextFieldValue("hello world"), snapshot, "background").text)
    assertEquals("hello background", replaceDiaryAssistantSelection(TextFieldValue("hello world"), snapshot, "background").text)
}
```

Add tests for reversed selections, current-cursor insertion after conflicts, cache normalization, cache misses, and a maximum cache size of ten entries.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.diary.DiaryAssistantEditorStateTest'`

Expected: compilation fails because editor-state helpers do not exist.

- [ ] **Step 3: Implement pure state and transformations**

Snapshot the full text only locally for conflict comparison; construct `DiaryAssistantRequest` with selected text and the bounded context helpers from Task 1. Insert results using blank-line separation without duplicating whitespace. Replacement must compare both the original full-text version and the selected substring. The session cache is an in-memory `LinkedHashMap<String, DiaryAssistantResult>` cleared when the sheet leaves composition.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.diary.DiaryAssistantEditorStateTest'`

Expected: all snapshot, paste, conflict, transform, and cache tests pass.

- [ ] **Step 5: Commit editor state**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryAssistantEditorState.kt app/src/test/kotlin/com/dailysatori/ui/feature/diary/DiaryAssistantEditorStateTest.kt
git commit -m "feat: add safe diary assistant edits"
```

---

### Task 6: Compose Toolbar, Confirmation Prompt, and Preview Workflow

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryAssistantPreviewSheet.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorToolbar.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorSheet.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/diary/DiaryAssistantUiSourceTest.kt`

**Interfaces:**
- Consumes: `DiaryAssistantService.run`, Task 5 snapshots/transforms/cache, current `TextFieldValue`, and the existing undo stack.
- Produces: complete opt-in editor UI without changing `DiaryRepository` or `DiaryViewModel` APIs.

- [ ] **Step 1: Write failing UI structure tests**

```kotlin
@Test
fun editorOffersExplicitAssistantActions() {
    val editor = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorSheet.kt").readText()
    val preview = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryAssistantPreviewSheet.kt").readText()
    assertTrue(editor.contains("提取核心内容"))
    assertTrue(editor.contains("assistantService.run"))
    assertTrue(preview.contains("插入到原文后"))
    assertTrue(preview.contains("替换原文"))
    assertTrue(preview.contains("未联网查证"))
}

@Test
fun pastePathCannotCallAssistantUntilConfirmation() {
    val editor = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorSheet.kt").readText()
    assertTrue(editor.contains("pendingPastedUrl"))
    assertTrue(editor.contains("onExtractConfirmed"))
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.diary.DiaryAssistantUiSourceTest'`

Expected: tests fail because the preview UI and assistant hooks do not exist.

- [ ] **Step 3: Add the toolbar action and local task lifecycle**

Add an `AutoAwesome` toolbar icon with description “AI 补充”. Enable it only for a noncollapsed, nonblank selection. In `DiaryEditorSheet`, obtain `DiaryAssistantService` through Koin, keep exactly one `Job`, cancel it before starting another, and cancel it from `DisposableEffect` on sheet disposal. Detect URL paste only inside `onValueChange`; set `pendingPastedUrl` but do not call the service there.

- [ ] **Step 4: Add confirmation and preview UI**

Show a compact prompt row for `pendingPastedUrl` with “提取核心内容” and dismiss actions. Only its confirm callback starts extraction. `DiaryAssistantPreviewSheet` must represent loading, fallback confirmation, editable result, sources, warnings, retry, cancellation, insertion, and replacement. Disable replacement when `canReplaceDiaryAssistantSelection` is false and label the remaining action “插入当前光标”.

- [ ] **Step 5: Wire atomic edit and undo behavior**

Before either insertion or replacement, call the existing `pushUndo()` exactly once; assign the returned `TextFieldValue` once. Never call `onSave` from assistant callbacks. Use the session cache before `assistantService.run`, and cache only successful link results.

- [ ] **Step 6: Run UI and existing diary editor tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.diary.DiaryAssistantUiSourceTest' --tests 'com.dailysatori.ui.feature.diary.DiaryAssistantEditorStateTest' --tests 'com.dailysatori.ui.feature.diary.DiaryModuleStructureTest'`

Expected: all assistant/editor tests pass; existing editor structure remains valid.

- [ ] **Step 7: Commit Compose integration**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryAssistantPreviewSheet.kt app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorToolbar.kt app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorSheet.kt app/src/test/kotlin/com/dailysatori/ui/feature/diary/DiaryAssistantUiSourceTest.kt
git commit -m "feat: add diary AI assistant preview"
```

---

### Task 7: End-to-End Regression and Privacy Verification

**Files:**
- Modify only if a verified failure requires it: files introduced in Tasks 1–6.
- Test: all tests introduced in Tasks 1–6.

**Interfaces:**
- Consumes: complete shared service and Compose editor integration.
- Produces: a buildable, regression-tested feature ready for device acceptance.

- [ ] **Step 1: Run the shared diary assistant suite**

Run: `./gradlew :shared:testDebugUnitTest --tests 'com.dailysatori.service.diary.DiaryAssistant*Test'`

Expected: all assistant model, formatting, extraction, orchestration, cancellation, and privacy tests pass.

- [ ] **Step 2: Run the Android diary assistant suite**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.diary.DiaryAssistant*Test' --tests 'com.dailysatori.ui.feature.diary.DiaryModuleStructureTest'`

Expected: all selection, paste, cache, preview, and existing structure tests pass.

- [ ] **Step 3: Run final project verification once**

Run: `./gradlew :shared:test :app:testDebugUnitTest :app:assembleDebug && git diff --check`

Expected: `BUILD SUCCESSFUL`, zero failed tests, debug APK assembled, and no whitespace errors.

- [ ] **Step 4: Inspect the final scoped diff**

Run: `git diff --stat && git status --short`

Expected: feature changes are limited to the files named in this plan; pre-existing unrelated modified/untracked files remain untouched and unstaged.

- [ ] **Step 5: Device acceptance checklist**

On a device, verify without committing generated data:

1. Paste a normal article URL: no request occurs until “提取核心内容” is tapped.
2. Paste a public Douyin share URL: metadata appears; missing subtitles are labeled.
3. Select a historical phrase: web-sourced preview includes at most three links.
4. Edit the diary while a request runs: replacement disables and current-cursor insertion remains safe.
5. Insert, undo, cancel, retry, close the sheet, and reopen: no stale result modifies the diary.

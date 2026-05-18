# Shared Service Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Simplify large shared services by extracting pure helpers and shared HTTP URL/auth behavior while preserving public service APIs and app behavior.

**Architecture:** Keep service constructors and public methods stable. Move deterministic parsing, URL normalization, request setup, and import transformation code into small internal helpers that can be tested without Android dependencies.

**Tech Stack:** Kotlin Multiplatform, Ktor client, kotlinx.serialization, SQLDelight repositories, Gradle.

---

## File Structure

- `shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt`: keep public parser service behavior stable; extract pure markdown, media, and content-quality helpers.
- `shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserHelpers.kt`: optional home for internal parser helpers when same-file private functions remain too large.
- `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt`: keep public summary orchestration stable; extract grouping, deduplication, source selection, and prompt-input helpers.
- `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryHelpers.kt`: optional home for internal unified-news helper functions.
- `shared/src/commonMain/kotlin/com/dailysatori/service/import/ImportService.kt`: keep public import entry points stable; extract validation, mapping, and result aggregation helpers.
- `shared/src/commonMain/kotlin/com/dailysatori/service/import/ImportHelpers.kt`: optional home for internal import helper functions.
- `shared/src/commonMain/kotlin/com/dailysatori/service/http/HttpRequestHelpers.kt`: create for shared URL normalization and auth-header request helpers used by shared services.
- `shared/src/commonMain/kotlin/com/dailysatori/service/remotenews/RemoteNewsService.kt`: update only if existing URL/auth helpers move to the shared HTTP helper file.

## Guardrails

- Do not change public service method signatures unless every caller is updated in the same task and the change is explicitly justified.
- Do not change serialized model names, repository contracts, or persistence semantics.
- Keep helpers `internal` unless external modules need them.
- Prefer pure helper tests for extracted behavior before changing service orchestration.
- Do not log API tokens, URLs containing credentials, imported content bodies, or AI prompt payloads.

## Task 1: Extract Webpage Parser Pure Helpers

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt`
- Optional create: `shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserHelpers.kt`
- Test: add or extend shared parser tests under the existing shared test source set.

- [ ] **Step 1: Identify pure parser helpers and side-effect boundaries**

Run:

```bash
grep -n "fun .*Markdown\|fun .*Image\|fun .*Twitter\|HttpClient\|suspend fun" shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt
```

Expected: pure markdown/media/content helpers are separable from `HttpClient`, repository writes, and AI calls.

- [ ] **Step 2: Add focused tests for current helper behavior**

Cover markdown image normalization, Twitter/X URL detection, content quality rejection, cover image validation, and fallback title behavior using current outputs as expected values.

- [ ] **Step 3: Move helpers without behavior changes**

Move only deterministic helper functions into `WebpageParserHelpers.kt` when it reduces file size. Keep parser service public methods, constructor dependencies, repository writes, and retry behavior in `WebpageParserService.kt`.

- [ ] **Step 4: Verify parser behavior**

Run:

```bash
./gradlew :shared:allTests --no-configuration-cache
./gradlew :app:compileDebugKotlin --no-configuration-cache
```

Expected: tests and compile finish with `BUILD SUCCESSFUL`.

## Task 2: Extract Unified News Summary Helpers

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt`
- Optional create: `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryHelpers.kt`
- Test: add or extend shared unified-news tests under the existing shared test source set.

- [ ] **Step 1: Identify pure summary helpers**

Run:

```bash
grep -n "distinctBy\|groupBy\|sorted\|source\|prompt\|suspend fun" shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt
```

Expected: deduplication, source mapping, ordering, prompt input construction, and result merging are visible separately from network and repository calls.

- [ ] **Step 2: Add focused tests for current summary transformations**

Cover duplicate removal by source/title/url, source metadata fallback, empty-source handling, item ordering, and failed-source aggregation using current behavior as expected values.

- [ ] **Step 3: Move helpers without changing orchestration**

Move deterministic transformations into `UnifiedNewsSummaryHelpers.kt` if it keeps the service focused. Keep public methods, remote-news calls, AI calls, and repository persistence in `UnifiedNewsSummaryService.kt`.

- [ ] **Step 4: Verify unified news behavior**

Run:

```bash
./gradlew :shared:allTests --no-configuration-cache
./gradlew :app:compileDebugKotlin --no-configuration-cache
```

Expected: tests and compile finish with `BUILD SUCCESSFUL`.

## Task 3: Extract Import Service Helpers

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/import/ImportService.kt`
- Optional create: `shared/src/commonMain/kotlin/com/dailysatori/service/import/ImportHelpers.kt`
- Test: add or extend shared import tests under the existing shared test source set.

- [ ] **Step 1: Identify import transformation boundaries**

Run:

```bash
grep -n "fun .*import\|fun .*Import\|decode\|insert\|update\|Result" shared/src/commonMain/kotlin/com/dailysatori/service/import/ImportService.kt
```

Expected: parsing, validation, entity mapping, duplicate handling, and result aggregation can be separated from repository writes.

- [ ] **Step 2: Add focused tests for current import transformations**

Cover supported import payload detection, invalid payload reporting, duplicate handling, partial-success result aggregation, and timestamp preservation using current behavior as expected values.

- [ ] **Step 3: Move helpers without changing import entry points**

Move validation, mapping, and result aggregation helpers into `ImportHelpers.kt` if the service remains easier to read. Keep public import methods, repository transactions, and error propagation stable.

- [ ] **Step 4: Verify import behavior**

Run:

```bash
./gradlew :shared:allTests --no-configuration-cache
./gradlew :app:compileDebugKotlin --no-configuration-cache
```

Expected: tests and compile finish with `BUILD SUCCESSFUL`.

## Task 4: Consolidate Shared HTTP URL And Auth Helpers

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/http/HttpRequestHelpers.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/remotenews/RemoteNewsService.kt`
- Modify only if duplicated behavior exists: `shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt`
- Modify only if duplicated behavior exists: `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt`

- [ ] **Step 1: Inventory URL and auth duplication**

Run:

```bash
grep -R "bearerAuth\|X-Api-Token\|URLBuilder\|trimEnd('/')\|trim()" shared/src/commonMain/kotlin/com/dailysatori/service --include='*.kt'
```

Expected: duplicated URL normalization or token/header setup is visible before extraction.

- [ ] **Step 2: Add helper tests before extraction**

Cover base URL trimming, `/api/v1/external` root derivation, top-articles URL normalization, blank token rejection, bearer auth setup, and `X-Api-Token` setup where current services require both headers.

- [ ] **Step 3: Create shared HTTP helper file**

Create `HttpRequestHelpers.kt` with internal functions for URL normalization and request auth setup. Keep helper names specific to the existing behavior, such as remote-news external API URL construction, instead of creating broad abstractions.

- [ ] **Step 4: Replace duplicated code in services**

Use the new helpers in `RemoteNewsService.kt` first, then only update parser or summary services where the exact same URL/auth behavior already exists.

- [ ] **Step 5: Verify HTTP helper behavior**

Run:

```bash
./gradlew :shared:allTests --no-configuration-cache
./gradlew :app:compileDebugKotlin --no-configuration-cache
git diff --check
```

Expected: tests and compile finish with `BUILD SUCCESSFUL`; no whitespace errors.

## Verification

- [ ] **Run shared tests and Android compile after each service task**

Run:

```bash
./gradlew :shared:allTests --no-configuration-cache
./gradlew :app:compileDebugKotlin --no-configuration-cache
```

Expected: both commands finish with `BUILD SUCCESSFUL`.

- [ ] **Run device smoke check after the full stage**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
adb shell am start -n com.dailysatori/.MainActivity
```

Expected: app launches; article parsing, unified news refresh, remote news access, and data import flows do not crash.

- [ ] **Check patch hygiene**

Run:

```bash
git diff --check
```

Expected: no whitespace errors.

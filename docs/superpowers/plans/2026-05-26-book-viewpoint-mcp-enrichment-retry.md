# Book Viewpoint MCP Enrichment Retry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate useful book viewpoints with WeRead grounding, per-viewpoint MCP web evidence, AI judgment, and single-viewpoint retry for failed cards.

**Architecture:** Split book viewpoint generation into outline creation and per-viewpoint enrichment. Persist each viewpoint slot with status and outline context so partial failures remain readable/retryable. Reuse `RemoteMcpClient.collectWebSearchNotes()` for external evidence, but make MCP failures non-fatal and let AI decide whether search results are useful, real references, or only support analogy-style examples.

**Tech Stack:** Kotlin Multiplatform, SQLDelight, Koin, Ktor, kotlinx.coroutines, kotlinx.serialization, Android Compose, Gradle JVM/Android tests.

---

## Files

- Modify: `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq` for viewpoint status/context columns and queries.
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt` to bump schema version.
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt` to add book viewpoint status/context migration.
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/BookViewpointRepository.kt` to insert/update status, outline, error, and source notes.
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/book/BookIntelligenceService.kt` to carry per-viewpoint generation state.
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/book/WeReadSkillService.kt` to implement outline generation, MCP-backed enrichment, failed drafts, and single retry support.
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/RemoteMcpClient.kt` to prioritize search tools and support `search_query` arguments.
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt` to inject MCP repository/client into the book AI generator.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookSearchViewModel.kt` to save partial failed viewpoints instead of rolling back when enrichment partially fails.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksViewModel.kt` to retry one failed viewpoint.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt` and `ViewpointCard.kt` to render failed/generating states and retry action.
- Tests: `shared/src/commonTest/kotlin/com/dailysatori/service/book/WeReadSkillServiceTest.kt`, `shared/src/commonTest/kotlin/com/dailysatori/service/mcp/RemoteMcpClientTest.kt`, `app/src/test/kotlin/com/dailysatori/ui/feature/book/ViewpointCardLayoutTest.kt`, `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookSearchUiTextTest.kt`.

## Task 1: Persistence Model

- [ ] Add failing SQL/repository tests or source assertions for new `book_viewpoint` columns: `status`, `error_message`, `outline_json`, `source_notes`.
- [ ] Run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :shared:allTests --tests com.dailysatori.service.book.WeReadSkillServiceTest` and confirm missing-column/query failures.
- [ ] Update `DailySatori.sq` `book_viewpoint` table and queries:
  - `status TEXT NOT NULL DEFAULT 'ready'`
  - `error_message TEXT NOT NULL DEFAULT ''`
  - `outline_json TEXT NOT NULL DEFAULT ''`
  - `source_notes TEXT NOT NULL DEFAULT ''`
  - `updateViewpointStatusContext` query for single-row retry state.
- [ ] Bump `DatabaseConfig.currentSchemaVersion` from `9L` to `10L`.
- [ ] Add `migrateV9ToV10()` in `DatabaseMigration.kt` with `ALTER TABLE book_viewpoint ADD COLUMN ...` calls wrapped in try/catch, and call it from `runMigrations()`.
- [ ] Extend `BookViewpointRepository` with:
  - `insert(bookId, title, content, example, status, errorMessage, outlineJson, sourceNotes)`.
  - `updateStatusContext(id, status, errorMessage, outlineJson, sourceNotes)`.
  - Keep existing `insert(bookId, title, content, example)` and `update(id, title, content, example)` delegating to ready defaults.
- [ ] Run `./gradlew :shared:generateCommonMainDailySatoriDatabaseInterface` or the focused test command that triggers SQLDelight generation.

## Task 2: MCP Search Suitability

- [ ] Add failing tests in `RemoteMcpClientTest.kt`:
  - `buildMcpToolArguments` uses `search_query` when present.
  - `isLikelyWebSearchTool` prefers search tools over reader tools for plain query text.
- [ ] Run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :shared:allTests --tests com.dailysatori.service.mcp.RemoteMcpClientTest` and confirm failures.
- [ ] Update `buildMcpToolArguments()` query key priority to include `search_query` before fallback keys.
- [ ] Add a small helper that ranks web MCP tools: search/query tools first, reader/read tools second, irrelevant tools excluded.
- [ ] Update `callWebSearchTool()` to select the best ranked tool instead of the first likely tool.
- [ ] Rerun the focused MCP test.

## Task 3: Book Viewpoint Generation Pipeline

- [ ] Add failing tests in `WeReadSkillServiceTest.kt` for:
  - Outline prompt requires 10 skeletons, `searchQuery`, `caseIntent`, and MCP evidence judgment.
  - Enrichment prompt includes WeRead material, outline, MCP notes, and forbids pretending analogy cases are real.
  - One failed enrichment returns a failed draft while successful drafts remain ready.
- [ ] Run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :shared:allTests --tests com.dailysatori.service.book.WeReadSkillServiceTest` and confirm failures.
- [ ] Add serializable `BookViewpointOutline` and `GeneratedBookViewpointDraft` models with fields: `title`, `brief`, `focus`, `searchQuery`, `caseIntent`, `content`, `example`, `status`, `errorMessage`, `outlineJson`, `sourceNotes`.
- [ ] Change `BookViewpointGenerationResult.drafts` to carry status/context without breaking existing import helpers; keep ready defaults for existing tests.
- [ ] Split `DefaultBookAiFallbackGenerator.generate()` into:
  - `generateOutlines()` using one short AI request.
  - `enrichOutline()` using MCP notes plus one AI request.
  - `generate()` orchestrating 10 outlines and per-outline enrichment.
- [ ] Inject `McpServerRepository` and `RemoteMcpClient` into `DefaultBookAiFallbackGenerator`.
- [ ] Use enabled HTTP MCP servers for every outline. If MCP returns blank or throws, pass empty notes to AI and do not fail the viewpoint.
- [ ] Limit enrichment concurrency with small constants, initially `MCP_ENRICH_CONCURRENCY = 4` and `AI_ENRICH_CONCURRENCY = 3`.
- [ ] If AI enrichment fails after retry, return a failed draft with title/outline preserved and empty content/example.
- [ ] Rerun focused shared tests.

## Task 4: Add Book With Partial Failures

- [ ] Add failing tests in `BookSearchUiTextTest.kt` for partial completion copy, e.g. `《实践论》已添加，7 个观点已生成，3 个可在阅读页重试`.
- [ ] Update `bookViewpointDraftsForImport()` and `BookSearchViewModel.addAndAnalyzeBook()` to insert all 10 draft slots with status/context.
- [ ] Stop rolling back the book when enrichment has per-viewpoint failures; only roll back when search, insert, WeRead fetch, or outline generation fails before draft slots exist.
- [ ] Update completion notice logic for ready/failed counts.
- [ ] Run focused app unit tests for book search UI text.

## Task 5: Single Viewpoint Retry

- [ ] Add failing ViewModel/source tests asserting `BooksViewModel` exposes retry state for a single viewpoint and does not regenerate the whole book.
- [ ] Add a `BookViewpointRetryService` or focused method on the existing book generation service that accepts an existing `Book_viewpoint` and its `outline_json`, reruns MCP + AI for only that outline, and returns one ready draft or failure.
- [ ] Wire `BooksViewModel.regenerateViewpoint(viewpointId)`:
  - mark row `generating`.
  - call single retry service on `Dispatchers.IO`.
  - update row to `ready` with new content/example/source notes on success.
  - update row to `failed` with `error_message` on failure.
- [ ] Ensure retry does not change `currentBookId`, other viewpoints, or pager page.
- [ ] Run focused ViewModel tests.

## Task 6: Reading UI Failure State

- [ ] Add failing source tests in `ViewpointCardLayoutTest.kt` for retry labels and status-aware rendering helpers.
- [ ] Update `ViewpointCard` parameters with `status`, `errorMessage`, `isRetrying`, and `onRetry`.
- [ ] For `status == "failed"`, show title/book line, concise failure text, and a `重新生成这个观点` button.
- [ ] For `status == "generating"`, show `正在重新生成这个观点...` and disable retry action.
- [ ] For `ready`, keep current reading layout.
- [ ] Update `BooksScreen` to pass `vp.status`, `vp.error_message`, and `onRetry = { viewModel.regenerateViewpoint(vp.id) }`.
- [ ] Run focused UI tests.

## Task 7: Verification

- [ ] Run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :shared:allTests --tests com.dailysatori.service.book.WeReadSkillServiceTest --tests com.dailysatori.service.mcp.RemoteMcpClientTest`.
- [ ] Run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.book.BookSearchUiTextTest --tests com.dailysatori.ui.feature.book.ViewpointCardLayoutTest`.
- [ ] Run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin`.
- [ ] Run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:assembleDebug`.
- [ ] Run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`.
- [ ] Run `adb shell am start -n com.dailysatori/.MainActivity`.
- [ ] Manually verify: add a book with MCP enabled, inspect failed cards if any, tap single-card retry, confirm only that viewpoint changes.

## Self-Review

- Spec coverage: covers WeRead grounding, MCP per viewpoint, AI evidence judgment, analogy-vs-real case honesty, partial failures, single retry, and UI state.
- Placeholder scan: no TBD/TODO placeholders remain.
- Type consistency: persistence status fields use `ready`, `failed`, and `generating`; retry and generation both reuse stored `outline_json`.

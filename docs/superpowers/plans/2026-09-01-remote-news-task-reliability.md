# Remote News and Background Task Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure a remote-news outage cannot fail the daily summary or leave background tasks running indefinitely.

**Architecture:** Treat remote sources as optional inputs to a daily summary, validate remote responses at the boundary, and enforce an execution deadline in the shared task runner. Keep the existing durable task repository and WorkManager chain, but guarantee every chain stage reaches a terminal or retry state within a bounded time.

**Tech Stack:** Kotlin, coroutines, SQLDelight, WorkManager, Ktor, kotlin.test.

**Spec:** Approved diagnosis and repair strategy in the 2026-09-01 conversation.

## Global Constraints

- Preserve existing successful summaries and expose remote-source degradation as warnings.
- Never treat an unrecognized HTTP 200 payload as a successful empty article response.
- A live heartbeat is ownership renewal, not permission to execute without a deadline.
- Preserve unrelated dirty Diary, Article, News UI, and Reminder UI changes.

---

### Task 1: Validate remote sync results

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/remotenews/RemoteNewsService.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/task/RemoteArticleSyncTaskHandler.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/remotenews/RemoteNewsServiceTest.kt`
- Test: `app/src/test/kotlin/com/dailysatori/core/task/RemoteArticleSyncTaskHandlerTest.kt`

**Interfaces:**
- Produces: strict `parseTopArticlesTodayResponse` and partial/all-failure task results.

- [ ] Add a failing parser test proving an unrecognized success payload is rejected while a recognized empty `articles` array remains valid.
- [ ] Add failing handler tests proving partial source failure succeeds with warnings and all-source failure returns `RetryableFailure`.
- [ ] Make the parser throw for unrecognized envelopes and count successful sources in the handler.
- [ ] Run the two focused test classes and confirm they pass.

### Task 2: Make daily summaries degrade gracefully

**Files:**
- Modify: `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/RemoteArticleSyncRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

**Interfaces:**
- Consumes: locally persisted remote mappings.
- Produces: a recent-snapshot fallback and successful EMPTY summary when every optional source is unavailable.

- [ ] Add failing tests for a missing current-day snapshot with a recent fallback and for no available sources producing `EMPTY`, not `FAILED`.
- [ ] Add a bounded latest-mapping query and use snapshots no older than three days, bypassing the current-window timestamp filter only for fallback mappings.
- [ ] Replace the warnings-plus-empty failure branch with `persistEmpty(window, warnings)`.
- [ ] Run unified-news behavior tests and confirm they pass.

### Task 3: Bound background task execution

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/asynctask/AsyncTaskRunner.kt`
- Modify: `shared/src/commonTest/kotlin/com/dailysatori/service/asynctask/AsyncTaskRunnerTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/worker/AsyncTaskWorker.kt`
- Test: `app/src/test/kotlin/com/dailysatori/core/worker/AsyncTaskWorkerSourceTest.kt`

**Interfaces:**
- Produces: `asyncTaskExecutionTimeoutMs(type)` with bounded defaults and timeout-to-retry behavior.

- [ ] Add a failing runner test whose handler suspends forever and must become `retrying` with error code `timeout`.
- [ ] Add a failing worker contract test requiring remote sync and summary to run as foreground long tasks.
- [ ] Wrap handler execution in `withTimeout`, catch `TimeoutCancellationException` before general cancellation, stop the heartbeat, and mark a controlled retry.
- [ ] Add remote sync and summary to the foreground long-task set.
- [ ] Run async-task runner/worker tests and confirm they pass.

### Task 4: Final verification

- [ ] Run `./gradlew :shared:testDebugUnitTest --tests '*RemoteNews*Test' --tests '*AsyncTaskRunnerTest' :app:testDebugUnitTest --tests '*UnifiedNewsBehaviorTest' --tests '*AsyncTaskWorkerSourceTest' :app:assembleDebug`.
- [ ] Run `git diff --check` and inspect only the task-related diff.
- [ ] Record the remaining limitation that real network behavior cannot be exercised while the configured device is unreachable.

# Async Task Core Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make async task execution, de-duplication, errors, logs, and task-center observation reliable and easy to test.

**Architecture:** Keep SQLite as the source of truth and WorkManager as the Android wake-up mechanism. Move task execution decisions into a shared `AsyncTaskRunner`, keep repository methods as explicit state transitions, and expose task/log observation to UI through flows.

**Tech Stack:** Kotlin Multiplatform shared module, SQLDelight SQLite, Android WorkManager, Kotlin Flow, existing file-based task logs.

---

## File Structure

- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/asynctask/AsyncTaskModels.kt`
  - Add `AsyncTaskLogger`, `NoopAsyncTaskLogger`, `AsyncTaskRunOutcome`, and `AsyncTaskRunner`.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/data/repository/AsyncTaskRepository.kt`
  - Keep enqueue transactional, make state transitions explicit, add guarded progress updates.
- Modify `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`
  - Keep active unique-key index and add guarded update queries if needed.
- Modify `app/src/main/kotlin/com/dailysatori/core/worker/AsyncTaskWorker.kt`
  - Make `GenericAsyncTaskWorker` delegate to `AsyncTaskRunner`.
- Modify `app/src/main/kotlin/com/dailysatori/core/task/AsyncTaskLogStore.kt`
  - Implement `AsyncTaskLogger` and keep observable file-log flow.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/settings/taskcenter/TaskCenterViewModel.kt`
  - Continue observing selected task row and log flow live.
- Add/modify tests under:
  - `shared/src/commonTest/kotlin/com/dailysatori/data/repository/AsyncTaskRepositoryTest.kt`
  - `shared/src/commonTest/kotlin/com/dailysatori/service/asynctask/AsyncTaskRunnerTest.kt`
  - `app/src/test/kotlin/com/dailysatori/core/worker/AsyncTaskWorkerSourceTest.kt`
  - `app/src/test/kotlin/com/dailysatori/core/task/AsyncTaskLogStoreTest.kt`
  - `app/src/test/kotlin/com/dailysatori/ui/feature/settings/taskcenter/TaskCenterScreenSourceTest.kt`

## Task 1: Repository State Machine Coverage

- [ ] Add tests proving duplicate active unique keys are rejected, terminal tasks allow a new unique-key task, running tasks cannot be claimed twice, and terminal tasks cannot receive progress updates.
- [ ] Add guarded SQL/repository methods if a failing test shows state can be mutated after terminal status.
- [ ] Run `./gradlew :shared:testDebugUnitTest --tests 'com.dailysatori.data.repository.AsyncTaskRepositoryTest' --no-configuration-cache`.

## Task 2: Shared Runner

- [ ] Add `AsyncTaskLogger` with `append(taskId: Long, message: String)`.
- [ ] Add `AsyncTaskRunner` in shared code. It claims a task, writes lifecycle logs, dispatches the handler, and records success, retry, or permanent failure.
- [ ] Add `AsyncTaskRunnerTest` for success, permanent failure, retryable failure, missing handler, thrown exception, and max-attempt failure.
- [ ] Run `./gradlew :shared:testDebugUnitTest --tests 'com.dailysatori.service.asynctask.AsyncTaskRunnerTest' --no-configuration-cache`.

## Task 3: Worker Delegation

- [ ] Replace duplicated execution logic in `GenericAsyncTaskWorker` with a call to `AsyncTaskRunner`.
- [ ] Map runner outcomes to WorkManager `Result.success()`, `Result.failure()`, or `Result.retry()`.
- [ ] Keep `AsyncTaskLogStore` as the Android logger implementation.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.core.worker.AsyncTaskWorkerSourceTest' --no-configuration-cache`.

## Task 4: Observable Logs and Task Detail

- [ ] Keep `AsyncTaskLogStore.observe(taskId)` as a polling flow so file logs update while the detail page is open.
- [ ] Keep `TaskCenterViewModel` selected detail based on `selectedTaskId`, `repository.observeTaskById(id)`, and `logStore.observe(id)`.
- [ ] Add source tests that reject one-shot `repository.getById(taskId) to logStore.read(taskId)` snapshots.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.settings.taskcenter.TaskCenterScreenSourceTest' --tests 'com.dailysatori.core.task.AsyncTaskLogStoreTest' --no-configuration-cache`.

## Task 5: External Favorite Regression

- [ ] Keep active unique-key behavior for `external_favorite_sync:$sourceId:$mode`.
- [ ] Keep ordinary sync from consuming old AI backlog when no new/import/repair work occurred.
- [ ] Run `./gradlew :shared:testDebugUnitTest --tests 'com.dailysatori.service.externalfavorites.FavoriteSyncServiceTest' --tests 'com.dailysatori.service.externalfavorites.XBookmarksConnectorTest' --no-configuration-cache`.

## Task 6: Final Verification

- [ ] Run `git diff --check`.
- [ ] Run `./gradlew :app:assembleDebug --no-configuration-cache`.
- [ ] If a phone port is available, install with `./gradlew :app:installDebug --no-configuration-cache`.

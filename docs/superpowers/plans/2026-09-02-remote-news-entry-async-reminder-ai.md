# Remote News Entry and Async Reminder AI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the missing profile entry for remote-news sync and move reminder AI parsing into a durable background workflow with retries, persisted drafts, completion notifications, and confirmation navigation.

**Architecture:** Persist reminder AI batches separately from formal reminders, execute parsing through the existing `async_task` runner, and use batch IDs as the only notification/navigation identity. The UI enqueues and returns immediately; the handler owns AI timing, retry classification, draft persistence, and terminal notification delivery.

**Tech Stack:** Kotlin Multiplatform, SQLDelight, Coroutines/Flow, Android WorkManager, Jetpack Compose Navigation, NotificationCompat, Koin, kotlin.test.

**Spec:** `docs/superpowers/specs/2026-09-02-remote-news-entry-async-reminder-ai-design.md`

## Global Constraints

- All reminder text is parsed by AI; do not add local natural-language parsing rules.
- AI success never schedules reminders until the user confirms drafts.
- Retry delays are 30 seconds then 2 minutes, with at most three attempts.
- Final failure persists the original input and sends a notification.
- Notification intents carry only the batch ID, never reminder text or credentials.
- Reuse the existing async-task runner and process recovery.

---

### Task 1: Personal-page remote-news entry

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/profile/ProfileViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/profile/ProfileScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/navigation/NavHost.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/navigation/Routes.kt`
- Modify: `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/RemoteNewsSourceRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/RemoteArticleSyncRepository.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/profile/ProfileStateTest.kt`

**Interfaces:**
- Consumes: new `RemoteNewsSourceRepository.observeEnabledCount(): Flow<Long>` and `RemoteArticleSyncRepository.observeCount(): Flow<Long>` SQLDelight query flows.
- Produces: `ProfileUiState.remoteNewsArticleCount`, `ProfileUiState.enabledRemoteNewsSourceCount`, and `ProfileScreen.onRemoteNews`.

- [x] **Step 1: Write the failing profile test**

Assert that profile destinations include `remote_news`, that the projection exposes article/source counts, and that `ProfileScreen` has an `onRemoteNews` row labeled `远程新闻`.

- [x] **Step 2: Run the focused test and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*ProfileStateTest'`

- [x] **Step 3: Implement the profile state and navigation**

Add the two repository flows to `ProfileViewModel`, combine them, render `已同步 N 篇 · M 个来源` (or `尚未配置来源`), and navigate to the existing remote-news settings destination.

- [x] **Step 4: Run the focused test and verify GREEN**

Run: `./gradlew :app:testDebugUnitTest --tests '*ProfileStateTest'`

- [ ] **Step 5: Commit the task**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/profile app/src/main/kotlin/com/dailysatori/core/navigation app/src/test/kotlin/com/dailysatori/ui/feature/profile/ProfileStateTest.kt shared/src/commonMain/sqldelight shared/src/commonMain/kotlin/com/dailysatori/data/repository
git commit -m "feat: add remote news profile entry"
```

### Task 2: Persist reminder AI batches and drafts

**Files:**
- Modify: `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/ReminderAiBatchRepository.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/reminder/ReminderAiBatchModels.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/data/repository/ReminderAiBatchRepositoryTest.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/migration/ReminderMigrationTest.kt`

**Interfaces:**
- Produces: `ReminderAiBatchStatus`, `ReminderAiBatch`, `ReminderAiDraftRecord`, and `ReminderAiBatchRepository` methods `enqueueOrReuse`, `markRunning`, `markReady`, `markFailed`, `observeBatch`, `getBatch`, and `markConfirmed`.

- [x] **Step 1: Write failing repository and migration tests**

Cover active-request deduplication by normalized input/timezone/local date, ordered `source_index` drafts, original-text preservation on failure, terminal notification marker, and schema migration from version 24 to 25.

- [x] **Step 2: Run tests and verify RED**

Run: `./gradlew :shared:testDebugUnitTest --tests '*ReminderAiBatchRepositoryTest' --tests '*ReminderMigrationTest'`

- [x] **Step 3: Add schema and migration**

Create `reminder_ai_batch` with batch ID, original input, normalized key, timezone, local date, status, task ID, attempt metadata, error summary, notification timestamp, and timestamps. Create `reminder_ai_draft` with batch ID, source index, source text, encoded draft JSON, and confirmation state. Raise `currentSchemaVersion` to `25` and add `migrateV24ToV25()`.

- [x] **Step 4: Implement repository transactions**

Use a partial unique index for active statuses and a transaction for terminal state plus draft replacement. `markReady` must atomically persist all decoded drafts; `markFailed` must retain original input even when no drafts exist.

- [x] **Step 5: Run tests and verify GREEN**

Run: `./gradlew :shared:testDebugUnitTest --tests '*ReminderAiBatchRepositoryTest' --tests '*ReminderMigrationTest'`

- [ ] **Step 6: Commit the task**

```bash
git add shared/src/commonMain/sqldelight shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt shared/src/commonMain/kotlin/com/dailysatori/service/migration shared/src/commonMain/kotlin/com/dailysatori/data/repository/ReminderAiBatchRepository.kt shared/src/commonMain/kotlin/com/dailysatori/service/reminder/ReminderAiBatchModels.kt shared/src/commonTest
git commit -m "feat: persist reminder AI batches"
```

### Task 3: Durable AI parsing task and measured retry policy

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/core/task/ReminderAiParseTaskHandler.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/reminder/ReminderTextInterpreter.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/asynctask/AsyncTaskModels.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/asynctask/AsyncTaskRunner.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/AppModule.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/worker/AsyncTaskWorker.kt`
- Test: `app/src/test/kotlin/com/dailysatori/core/task/ReminderAiParseTaskHandlerTest.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/reminder/ReminderTextInterpreterTest.kt`

**Interfaces:**
- Consumes: `ReminderAiBatchRepository`, `ReminderInterpretationRemote.interpretBatch`, and `ReminderBatchCodec`.
- Produces: async type `reminder_ai_parse`, `reminderAiRetryDecision(error, attempt)`, and stage timing fields in task checkpoints.

- [x] **Step 1: Write failing handler tests**

Use fake AI and clock dependencies. Verify one AI call for multiple fragments, no local parser invocation, stage durations, success persistence, retry delays `30_000` and `120_000`, permanent failure for missing configuration/auth/valid-but-malformed JSON, and final original-input failure persistence.

- [x] **Step 2: Run tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReminderAiParseTaskHandlerTest' :shared:testDebugUnitTest --tests '*ReminderTextInterpreterTest'`

- [x] **Step 3: Make batch parsing AI-only**

Remove `parseLocally` from `interpretBatch`; send all fragments in one call. Keep strict `source_index` validation and expose a method that returns decoded results without UI state.

- [x] **Step 4: Implement the task handler**

Decode `{batchId}`, mark the batch running, time config/request/decode/persist boundaries, and return `RetryableFailure(retryAfterMs=...)` or `PermanentFailure` based on the explicit policy. Register the handler and include its type in network/long-running classifications with a bounded timeout.

- [x] **Step 5: Run tests and verify GREEN**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReminderAiParseTaskHandlerTest' :shared:testDebugUnitTest --tests '*ReminderTextInterpreterTest'`

- [ ] **Step 6: Commit the task**

```bash
git add app/src/main/kotlin/com/dailysatori/core/task app/src/main/kotlin/com/dailysatori/core/di/AppModule.kt app/src/main/kotlin/com/dailysatori/core/worker/AsyncTaskWorker.kt shared/src/commonMain/kotlin/com/dailysatori/service shared/src/commonTest app/src/test/kotlin/com/dailysatori/core/task
git commit -m "feat: run reminder AI parsing in background"
```

### Task 4: Terminal notifications and cold-start navigation

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/core/reminder/ReminderAiParseNotification.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/MainActivity.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/navigation/Routes.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/navigation/NavHost.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/kotlin/com/dailysatori/core/reminder/ReminderAiParseNotificationTest.kt`
- Test: `app/src/test/kotlin/com/dailysatori/core/reminder/ReminderRecoveryTest.kt`

**Interfaces:**
- Produces: `ReminderAiParseNotifier.notifyReady(batchId)` and `notifyFailed(batchId)`, plus `ReminderAiBatchRoute(batchId: String)`.

- [x] **Step 1: Write failing notification/navigation tests**

Assert unique PendingIntent identity per batch, batch-ID-only extras, success/failure copy, notify-once repository marker, foreground intent handling, and cold-start route restoration.

- [x] **Step 2: Run tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReminderAiParseNotificationTest' --tests '*ReminderRecoveryTest'`

- [x] **Step 3: Implement notifier and navigation intake**

Use a dedicated low-importance channel, immutable update-current PendingIntents, and an application-level open-request state modeled after `ReminderOpenRequest`. Consume each request once and navigate to `ReminderAiBatchRoute(batchId)`.

- [x] **Step 4: Trigger notifications from terminal persistence**

After `markReady` or final `markFailed`, atomically claim the notification marker before posting. Recovered workers must observe an existing marker and skip duplicate delivery.

- [x] **Step 5: Run tests and verify GREEN**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReminderAiParseNotificationTest' --tests '*ReminderRecoveryTest'`

- [ ] **Step 6: Commit the task**

```bash
git add app/src/main/kotlin/com/dailysatori/core/reminder app/src/main/kotlin/com/dailysatori/core/navigation app/src/main/kotlin/com/dailysatori/MainActivity.kt app/src/main/AndroidManifest.xml app/src/test/kotlin/com/dailysatori/core/reminder
git commit -m "feat: notify completed reminder AI parses"
```

### Task 5: Non-blocking submission and persisted confirmation screen

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/reminder/ReminderViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/reminder/ReminderEditScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/reminder/ReminderBatchPreview.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/reminder/ReminderAiBatchViewModel.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/reminder/ReminderAiBatchScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/reminder/ReminderAiAsyncFlowTest.kt`

**Interfaces:**
- Consumes: `ReminderAiBatchRepository`, `AsyncTaskRepository`, `AsyncTaskScheduler`, and `ReminderAiBatchRoute`.
- Produces: `submitReminderAiBatch(text): batchId`, `retryBatch(batchId)`, and persisted confirmation/discard actions.

- [x] **Step 1: Write failing UI-flow tests**

Verify submit returns after enqueue without invoking AI, duplicate submit reuses the active batch, ready batches load after process recreation, failed batches show original text and retry, partial confirmation creates only selected reminders, and repeated notification opens do not duplicate reminders.

- [x] **Step 2: Run tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReminderAiAsyncFlowTest'`

- [x] **Step 3: Replace synchronous interpretation submission**

Change `interpretAiPrompt()` to persist/enqueue only, set a submitted acknowledgement, and clear the editor safely. Do not call `ReminderTextInterpreter` from the UI ViewModel.

- [x] **Step 4: Implement batch confirmation and retry UI**

Load the batch by route ID. For `READY_FOR_CONFIRMATION`, reuse the existing draft cards and save gate; for `PARSE_FAILED`, show original input, error summary, and `重新解析`; for processed batches, show a read-only terminal message.

- [x] **Step 5: Run tests and verify GREEN**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReminderAiAsyncFlowTest'`

- [ ] **Step 6: Commit the task**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/reminder app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt app/src/test/kotlin/com/dailysatori/ui/feature/reminder
git commit -m "feat: add async reminder confirmation flow"
```

### Task 6: Integrated recovery and release verification

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/core/worker/AsyncTaskWorkerSourceTest.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/reminder/ReminderUiSourceTest.kt`
- Create: `docs/versions/changelog_5.1.59.md`

**Interfaces:**
- Verifies every interface produced by Tasks 1–5.

- [x] **Step 1: Add the process-recovery integration case**

Create a queued batch, simulate process-start recovery, execute a retry to success, assert one ready notification, reopen by batch ID, confirm one draft, and assert exactly one formal reminder.

- [x] **Step 2: Run focused cross-layer tests**

Run: `./gradlew :shared:testDebugUnitTest --tests '*Reminder*' --tests '*AsyncTaskRunnerTest' :app:testDebugUnitTest --tests '*Reminder*' --tests '*ProfileStateTest' --tests '*AsyncTaskWorkerSourceTest'`

- [x] **Step 3: Run final build and static checks**

Run: `./gradlew :shared:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`

Run: `git diff --check`

- [x] **Step 4: Update the changelog with user-visible behavior**

Document the new personal-page remote-news entry, background reminder parsing, terminal notifications, and recoverable failed drafts.

- [ ] **Step 5: Commit verification artifacts**

```bash
git add app/src/test shared/src/commonTest docs/versions
git commit -m "test: cover async reminder AI recovery"
```

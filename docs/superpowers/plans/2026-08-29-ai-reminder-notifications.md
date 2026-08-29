# AI Reminder Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add confirmed AI-created reminders that deliver configurable Android notifications, back off after dismissal, respect sleep/work rules, and expire at each configured day's cutoff.

**Architecture:** Shared code owns reminder contracts, persistence, validation, and a pure next-occurrence state machine. Android code owns a hybrid AlarmManager/WorkManager scheduler, receivers, notification channels, and recovery. The existing MCP assistant returns structured reminder drafts to an editable Compose card; only explicit confirmation persists and schedules a reminder.

**Tech Stack:** Kotlin Multiplatform, SQLDelight, kotlinx-datetime/serialization/coroutines, Android AlarmManager, WorkManager, NotificationCompat, BroadcastReceiver, Koin, Compose Material 3.

**Spec:** `docs/superpowers/specs/2026-08-29-ai-reminder-notifications-design.md`

## Global Constraints

- Work directly in the current workspace; do not create a git worktree.
- Preserve all unrelated dirty and untracked files. `DailySatori.sq` and existing diary/AI files may already contain user edits; stage only reminder-related hunks.
- AI produces `ReminderDraft` only. No active reminder is persisted or scheduled until the user confirms the card.
- Single-day reminders expire at local 24:00; multi-day reminders reset daily and expire after the final active date.
- Default sleep interval is 00:00–09:00 with no notification. Default work interval is Monday–Friday 09:00–18:00 with visible but silent/non-vibrating notification.
- Complete is terminal for the entire reminder. Swipe is never complete and advances daytime backoff to 2h, then 4h, capped at 4h.
- Strong mode overrides daytime backoff from 22:00 until before 24:00 with hourly reminders.
- Schedule only the next occurrence. Delivery, swipe, edit, pause, resume, boot, time/timezone change, and permission change all recompute it idempotently.
- Exact-alarm access is optional; use a one-time WorkManager fallback when unavailable.
- Repeated delivery never invokes AI and logs never include full reminder content.

---

### Task 1: Reminder contracts and pure schedule engine

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/reminder/ReminderModels.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/reminder/ReminderScheduleEngine.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/reminder/ReminderScheduleEngineTest.kt`

**Interfaces:**
- Produces: `ReminderDraft`, `Reminder`, `ReminderProfileSnapshot`, `ReminderStatus`, `ReminderActiveDayRule`, `ReminderDeliveryReason`, `ReminderScheduleInput`, `ReminderScheduleDecision`, and `ReminderScheduleEngine.next(input)`.
- Consumers: repository in Task 2, Android coordinator in Task 3, AI/UI in Tasks 4–5.

- [ ] **Step 1: Write failing schedule-state tests**

Cover literal cases for untouched hourly repeat, swipe 2h then 4h cap, Strong 22:00 hourly override, 24:00 daily rollover/terminal expiry, multi-day reset, 00:00–09:00 suppression with one 09:00 recovery, weekday work-hour `silent=true`, paused/completed/expired terminal states, timezone local-wall-clock preservation, DST gap-forward and overlap-first-occurrence.

```kotlin
@Test fun strongDismissalBackoffAndEveningOverride() {
    val first = engine.next(input(now = local("2026-09-02T12:00"), dismissals = 1))
    assertEquals(local("2026-09-02T14:00"), first.at)
    val evening = engine.next(input(now = local("2026-09-02T22:05"), dismissals = 2))
    assertEquals(local("2026-09-02T23:00"), evening.at)
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./gradlew :shared:testDebugUnitTest --tests 'com.dailysatori.service.reminder.ReminderScheduleEngineTest'`

Expected: compilation/test failure because reminder contracts and engine do not exist.

- [ ] **Step 3: Implement immutable contracts and one pure engine**

Use `LocalDate`, `LocalTime`, `Instant`, and `TimeZone`. Store active dates/rules and profile behavior explicitly. `next` returns either `Schedule(at, silent, reason, expectedVersion)` or terminal `None(status)`. Keep Android types out of shared code. Compute eligibility in this order: terminal status → final cutoff → active date → sleep → first time/daytime backoff → evening reinforcement → work-hour silence.

- [ ] **Step 4: Run focused GREEN**

Run the command from Step 2. Expected: all schedule-engine tests pass.

- [ ] **Step 5: Commit Task 1**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/reminder shared/src/commonTest/kotlin/com/dailysatori/service/reminder
git commit -m "feat: add reminder schedule engine"
```

### Task 2: Reminder persistence and atomic lifecycle

**Files:**
- Modify: `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/ReminderRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/data/repository/ReminderRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 1 reminder types.
- Produces: `createConfirmed(draft, profileSnapshot)`, `get(id)`, `observeAll()`, `activeAt(now)`, `markDelivered(id, expectedVersion, at)`, `markDismissed(id, expectedVersion, at)`, `complete(id)`, `pause(id)`, `resume(id)`, `update(id, edit)`, `expire(id)`, and bounded `recordEvent`.

- [ ] **Step 1: Write failing repository behavior tests**

Test confirmation-only creation, profile snapshot persistence, active/terminal queries, daily backoff reset, optimistic version rejection, Complete winning over stale delivery, bounded event history, and content-free event metadata.

```kotlin
@Test fun completionWinsAgainstStaleDelivery() {
    val reminder = repo.createConfirmed(draft(), strongProfile())
    repo.complete(reminder.id)
    assertFalse(repo.markDelivered(reminder.id, reminder.version, now))
    assertEquals(ReminderStatus.COMPLETED, repo.get(reminder.id)!!.status)
}
```

- [ ] **Step 2: Run repository tests and verify RED**

Run: `./gradlew :shared:testDebugUnitTest --tests 'com.dailysatori.data.repository.ReminderRepositoryTest'`

- [ ] **Step 3: Add schema and repository**

Add `reminder`, `reminder_profile`, and `reminder_event` tables and indexed queries. Persist profile snapshots as strict bounded JSON and schedule timestamps as epoch milliseconds while retaining local date/time/timezone behavior fields. Add idempotent schema creation to `DatabaseMigration` following existing migration conventions. Patch only reminder-related schema hunks.

- [ ] **Step 4: Register repository and run GREEN**

Run the command from Step 2 plus `./gradlew :shared:testDebugUnitTest`.

- [ ] **Step 5: Commit Task 2**

```bash
git add shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq shared/src/commonMain/kotlin/com/dailysatori/data/repository/ReminderRepository.kt shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt shared/src/commonTest/kotlin/com/dailysatori/data/repository/ReminderRepositoryTest.kt
git commit -m "feat: persist reminder lifecycle"
```

### Task 3: Hybrid Android scheduler and notifications

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/core/reminder/ReminderCoordinator.kt`
- Create: `app/src/main/kotlin/com/dailysatori/core/reminder/ReminderScheduler.kt`
- Create: `app/src/main/kotlin/com/dailysatori/core/reminder/ReminderWorker.kt`
- Create: `app/src/main/kotlin/com/dailysatori/core/reminder/ReminderReceiver.kt`
- Create: `app/src/main/kotlin/com/dailysatori/core/reminder/ReminderNotification.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/AppModule.kt`
- Test: `app/src/test/kotlin/com/dailysatori/core/reminder/ReminderDeliveryTest.kt`

**Interfaces:**
- Consumes: Task 1 engine and Task 2 repository.
- Produces: `ReminderCoordinator.recompute(id)`, `recomputeAll()`, `complete(id)`, `dismiss(id, version)`, `viewIntent(id)`, and a `ReminderScheduler` abstraction with exact and WorkManager implementations.

- [ ] **Step 1: Write failing delivery-policy tests**

Use fake scheduler/notifier/repository boundaries. Assert exact alarm selected only when allowed, WorkManager fallback otherwise, one unique pending occurrence per reminder, immutable reminder-specific PendingIntent identity, duplicate delivery idempotence, swipe backoff, Complete cancellation, visible-but-silent work-hour notification, lock-screen redaction, and no post after expiry.

- [ ] **Step 2: Run Android focused tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.core.reminder.ReminderDeliveryTest'`

- [ ] **Step 3: Implement scheduler/coordinator**

Use `AlarmManager.canScheduleExactAlarms()` before exact scheduling. Fallback uses `OneTimeWorkRequestBuilder<ReminderWorker>()`, initial delay, and unique work name `reminder-next:<id>`. Both paths carry reminder ID and expected version only. Coordinator atomically marks delivery before posting and always schedules the next decision afterward.

- [ ] **Step 4: Implement channels, notification actions, and manifest receivers**

Create behavior-specific channels for sound/importance combinations. Use `deleteIntent` for swipe, action PendingIntent for Complete, and content PendingIntent for View. Register delivery/action receivers and boot/time/timezone/package-replaced restoration receivers with the minimum exported surface. Add `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, and existing notification permission handling without making exact access mandatory.

- [ ] **Step 5: Run focused GREEN and manifest tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.core.reminder.*Test' :app:processDebugManifest`

- [ ] **Step 6: Commit Task 3**

```bash
git add app/src/main/kotlin/com/dailysatori/core/reminder app/src/main/AndroidManifest.xml app/src/main/kotlin/com/dailysatori/core/di/AppModule.kt app/src/test/kotlin/com/dailysatori/core/reminder
git commit -m "feat: deliver reminder notifications"
```

### Task 4: AI reminder draft tool and confirmation-only handoff

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/reminder/ReminderDraftCodec.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpToolRegistry.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentService.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentPrompts.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatViewModel.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/reminder/ReminderDraftToolTest.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/ReminderDraftChatStateTest.kt`

**Interfaces:**
- Produces: `create_reminder_draft` MCP tool; `McpAgentResult.reminderDrafts: List<ReminderDraft>`; chat state containing pending drafts.
- Constraint: tool validates/normalizes and returns draft JSON only; it never calls `ReminderRepository.createConfirmed` or scheduler APIs.

- [ ] **Step 1: Write failing tool-contract tests**

Assert absolute local dates/times, consecutive-day and weekday rules, bounded content, missing/ambiguous required fields as validation errors, current timezone capture, strict JSON, and zero active-reminder writes.

- [ ] **Step 2: Run shared tool tests and verify RED**

Run: `./gradlew :shared:testDebugUnitTest --tests 'com.dailysatori.service.reminder.ReminderDraftToolTest'`

- [ ] **Step 3: Add tool definition, codec, and typed agent result**

Add exact tool fields from the spec. Thread a dedicated `MutableList<ReminderDraft>` through both streaming and non-streaming tool execution and return it on `McpAgentResult`; do not encode drafts as citations. Update the assistant prompt to call the tool for reminder intent and state that scheduling requires card confirmation.

- [ ] **Step 4: Add chat-state draft handoff tests and implementation**

Ensure cancellation/stale streaming cannot attach a draft to the wrong assistant message and persisted text history does not activate a draft on reload.

Run: `./gradlew :shared:testDebugUnitTest --tests 'com.dailysatori.service.reminder.ReminderDraftToolTest' :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.aichat.ReminderDraftChatStateTest'`

- [ ] **Step 5: Commit Task 4**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/reminder/ReminderDraftCodec.kt shared/src/commonMain/kotlin/com/dailysatori/service/mcp shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatViewModel.kt shared/src/commonTest/kotlin/com/dailysatori/service/reminder/ReminderDraftToolTest.kt app/src/test/kotlin/com/dailysatori/ui/feature/aichat/ReminderDraftChatStateTest.kt
git commit -m "feat: create reminder drafts from AI chat"
```

### Task 5: Confirmation card, reminder list, and unified settings

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/reminder/ReminderDraftCard.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/reminder/ReminderListScreen.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/reminder/ReminderViewModel.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/reminder/ReminderSettingsScreen.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/reminder/ReminderSettingsViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/reminder/ReminderUiStateTest.kt`

**Interfaces:**
- Consumes: pending drafts from Task 4, repository from Task 2, coordinator from Task 3.
- Produces: editable confirmation, explicit `confirmDraft`, list/detail actions, profile CRUD, global default/quiet/work settings, and permission/channel recovery actions.

- [ ] **Step 1: Write failing UI-state tests**

Test required-field validation, absolute date/time display, every card option editable, confirm-once semantics, cancel without persistence, profile snapshot on confirm, per-reminder override isolation, pause/resume/edit/complete/delete, built-in profiles, custom profile validation, and settings defaults 00:00–09:00 / weekdays 09:00–18:00.

- [ ] **Step 2: Run focused UI tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.reminder.ReminderUiStateTest'`

- [ ] **Step 3: Implement confirmation card and explicit confirmation transaction**

Render the card below its assistant message. Disable Confirm while invalid or saving. On confirm, call `createConfirmed` once, then `coordinator.recompute(id)`; if scheduling fails, retain the active reminder and surface permission/fallback state instead of creating duplicates.

- [ ] **Step 4: Implement list/detail and settings/profile editor**

Add Active/Paused/Completed/Expired filters. Surface notification permission, exact-alarm access, and disabled channels with direct system-setting actions. Settings changes affect future confirmations only unless the user explicitly applies the latest profile to an existing reminder.

- [ ] **Step 5: Run focused GREEN and Compose compilation**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.reminder.*Test' :app:assembleDebug`

- [ ] **Step 6: Commit Task 5**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/reminder app/src/main/kotlin/com/dailysatori/ui/feature/settings/reminder app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml app/src/test/kotlin/com/dailysatori/ui/feature/reminder
git commit -m "feat: manage reminders and profiles"
```

### Task 6: Recovery, permission changes, and end-to-end invariants

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/MainActivity.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/reminder/ReminderReceiver.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/reminder/ReminderCoordinator.kt`
- Test: `app/src/test/kotlin/com/dailysatori/core/reminder/ReminderRecoveryTest.kt`

**Interfaces:**
- Consumes: all prior tasks.
- Produces: startup/boot/time/timezone/package-replaced/exact-access recovery and late-delivery policy.

- [ ] **Step 1: Write failing recovery tests**

Assert reboot schedules one next occurrence per active reminder, time/timezone changes recompute local intent, permission return rechecks `canScheduleExactAlarms`, late work posts at most once when eligible, sleep-window late work schedules 09:00 without posting, and expired/completed reminders never resurrect.

- [ ] **Step 2: Run recovery tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.core.reminder.ReminderRecoveryTest'`

- [ ] **Step 3: Implement recovery entry points and generation gates**

Use repository version in every PendingIntent/Work request and compare before mutation. Recompute on `MainActivity.onResume` only when relevant permission capability changed; do not create a general app-open polling loop.

- [ ] **Step 4: Run focused GREEN**

Run the command from Step 2 plus all `com.dailysatori.core.reminder.*Test` tests.

- [ ] **Step 5: Commit Task 6**

```bash
git add app/src/main/kotlin/com/dailysatori/MainActivity.kt app/src/main/kotlin/com/dailysatori/core/reminder app/src/test/kotlin/com/dailysatori/core/reminder/ReminderRecoveryTest.kt
git commit -m "fix: restore reminder schedules safely"
```

### Task 7: Final verification and device acceptance

**Files:**
- Modify only if verification exposes a reminder-related defect.

**Interfaces:**
- Verifies the complete feature without expanding scope.

- [ ] **Step 1: Run focused reminder suites**

```bash
./gradlew :shared:testDebugUnitTest --tests 'com.dailysatori.service.reminder.*' --tests 'com.dailysatori.data.repository.ReminderRepositoryTest'
./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.core.reminder.*' --tests 'com.dailysatori.ui.feature.reminder.*' --tests 'com.dailysatori.ui.feature.aichat.ReminderDraftChatStateTest'
```

- [ ] **Step 2: Run final project verification once**

```bash
./gradlew :shared:test :app:testDebugUnitTest :app:assembleDebug
git diff --check
```

- [ ] **Step 3: Perform device acceptance**

On an Android device, use a compressed test profile to verify: swipe is not completion; untouched hourly repeat; 2h→4h dismissal transitions; 22:00 override; 24:00 stop; consecutive-day reset; 00:00–09:00 suppression/09:00 recovery; work-hour visible silence; out-of-work sound/vibration; lock-screen redaction; Complete terminal action; exact-alarm denied fallback; process death/reboot restoration; notification/channel/exact-access recovery screens.

- [ ] **Step 4: Inspect scope and commit only verified fixes**

```bash
git status --short
git diff --check
```

Do not stage or alter pre-existing unrelated changes. If device acceptance is unavailable, record it as an unverified release risk rather than claiming it passed.

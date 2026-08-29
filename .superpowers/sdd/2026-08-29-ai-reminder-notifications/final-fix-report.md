# AI Reminder Notifications Final Fix Report

Date: 2026-08-30

## Scope and result

This single final-review fix wave closes all five Important findings without changing the pre-existing `DailySatori.sq` diary hunk or other unrelated dirty work.

## Finding 1 — final notification actions and durable cutoff

- Root cause: `ReminderScheduleEngine` returned terminal `EXPIRED` as soon as no later notification slot existed. `ReminderCoordinator.recomputeSchedule` immediately persisted that result, bumping the generation before the daily cutoff and making the visible Complete/Dismiss intents stale.
- Fix: added a distinct `ReminderScheduleDecision.Cutoff` and `ReminderOccurrenceKind.CUTOFF`. AlarmManager and WorkManager now carry the occurrence kind, and receiver/worker dispatch cutoff to `ReminderCoordinator.cutoff` without posting a notification. The cutoff transition uses repository CAS to either reset the daily state/version or expire the final date. Final-slot Complete/Dismiss remains valid until cutoff.
- Evidence: `finalVisibleSlotRemainsActionableUntilDurableCutoffTransition`, `finalSlotActionsStayCurrentUntilCutoffThenExpireWithoutPosting`, `finalSlotDismissRemainsCurrentAndReschedulesOnlyTheCutoff`, `cutoffRollsMultiDayReminderAndResetsGenerationWithoutPosting`, and `dailyCutoffTransitionIsDurableResetsBackoffAndRejectsStaleGeneration`.
- Files: `ReminderModels.kt`, `ReminderScheduleEngine.kt`, `ReminderRepository.kt`, `ReminderCoordinator.kt`, `ReminderScheduler.kt`, `ReminderReceiver.kt`, `ReminderWorker.kt`, and their focused tests.

## Finding 2 — View notification navigation

- Root cause: the notification built a valid `ACTION_VIEW_REMINDER` PendingIntent, but `MainActivity` never consumed it and no app navigation state represented the requested reminder.
- Fix: `MainActivity.onCreate` and `onNewIntent` both route the action/id into `ReminderOpenRequestState`; `DailySatoriApp` consumes it and navigates to typed `ReminderRoute(id)`. The route opens Reminder Settings/List and selects that reminder detail. The PendingIntent uses `CLEAR_TOP | SINGLE_TOP` so an existing activity reaches `onNewIntent`.
- Evidence: `ReminderNavigationTest` covers action/id filtering and one-shot state consumption; production compilation covers both activity entry points and typed route integration.
- Files: `ReminderNavigation.kt`, `MainActivity.kt`, `DailySatoriApp.kt`, `Routes.kt`, `NavHost.kt`, `ReminderSettingsScreen.kt`, `ReminderListScreen.kt`, `ReminderNotification.kt`.

## Finding 3 — quiet hours crossing midnight

- Root cause: quiet-hour normalization replaced the time with `sleepEnd` but retained the same date, producing a past 09:00 instant for a 23:00 input.
- Fix: wrap intervals are detected explicitly. A due reminder entering a 22:00–09:00 interval schedules the durable midnight cutoff first; rollover preserves a due notification state only for the wrap case, resets daily backoff/version, rechecks the active/final date, and then schedules one 09:00 wake recovery. Final-date wrap reaches terminal cutoff and never loops immediately.
- Evidence: `wrappingQuietHoursWakeOnNextDayAndRespectFinalBoundary` and `wrappingQuietHoursCutoffRollsThenSchedulesNextDayWake`.
- Files: `ReminderScheduleEngine.kt`, `ReminderCoordinator.kt`, `ReminderRepository.kt`, schedule/delivery tests.

## Finding 4 — malformed advanced confirmation input

- Root cause: Compose `onValueChange` used `mapNotNull`/`toIntOrNull`, replacing malformed input with a sanitized profile, so validation saw a valid snapshot and left Confirm enabled.
- Fix: `ReminderDraftUiState` now owns the raw backoff and evening-interval strings. Strict token/count/range parsing marks `ADVANCED_PROFILE`, preserves the user's text, shows the existing localized validation error, and disables confirmation. Only valid parsed input mutates the snapshot.
- Evidence: `draftAdvancedInputsPreserveMalformedTextAndDisableConfirmation` covers malformed tokens and out-of-range interval; existing profile-editor malformed-token coverage remains green.
- Files: `ReminderViewModel.kt`, `ReminderDraftCard.kt`, `ReminderUiStateTest.kt`.

## Finding 5 — persisted custom profiles on message cards

- Root cause: `ReminderDraftCard` constructed a fixed built-in list and had no stable selected profile identity.
- Fix: `ReminderViewModel.profiles` combines built-ins with `ReminderRepository.observeProfiles()` and is collected by `AiChatScreen`, then passed to every message-scoped card. Card state records `profileId`; choosing a custom profile copies its snapshot, so later repository/profile edits do not mutate the confirmation payload. Existing global-default and AI built-in suggestion selection remain distinct through the fallback/suggested profile ID.
- Evidence: `persistedCustomProfileCanBeSelectedAndConfirmationKeepsItsSnapshot`; focused repository/profile and draft tests remain green.
- Files: `ReminderViewModel.kt`, `ReminderDraftCard.kt`, `AiChatScreen.kt`, `ReminderUiStateTest.kt`.

## TDD evidence

- RED command: `./gradlew :shared:testDebugUnitTest --tests 'com.dailysatori.service.reminder.ReminderScheduleEngineTest' :app:testDebugUnitTest --tests 'com.dailysatori.core.reminder.ReminderDeliveryTest' --tests 'com.dailysatori.core.reminder.ReminderNavigationTest' --tests 'com.dailysatori.ui.feature.reminder.ReminderUiStateTest'`
- Initial RED result: failed as expected on missing `Cutoff`, occurrence kind/cutoff coordinator path, reminder navigation state/router, raw advanced-input APIs, and custom profile selection APIs. Intermediate GREEN work also exposed the original past-date wrap result (`expected 2026-09-03T09:00Z, actual 2026-09-02T09:00Z`) before the cutoff/wake correction.
- Final focused GREEN command: `./gradlew :shared:testDebugUnitTest --tests 'com.dailysatori.service.reminder.*' --tests 'com.dailysatori.data.repository.ReminderRepositoryTest' :app:testDebugUnitTest --tests 'com.dailysatori.core.reminder.*' --tests 'com.dailysatori.ui.feature.reminder.*' --tests 'com.dailysatori.ui.feature.aichat.ReminderDraftChatStateTest'`
- Final focused result: BUILD SUCCESSFUL; 90 tests, 0 failures/errors/skips.
- Build command: `./gradlew :app:assembleDebug`
- Build result: BUILD SUCCESSFUL.

## Self-review and concerns

- Reviewed the cutoff generation flow across engine → scheduler backend → receiver/worker → coordinator → repository CAS. Cutoff never calls the notifier post path; duplicate exact/work callbacks lose the generation race idempotently.
- Reviewed custom selection for snapshot isolation and verified malformed raw fields cannot reach `confirmationPayload`.
- Reviewed navigation from both activity entry points through typed navigation to selected detail.
- No reminder-related code concern remains from the five findings.
- Real-device notification-tap, process-task-stack, exact-alarm, and compressed-clock acceptance was not run in this wave; it remains the only unverified release risk. Per task direction, no unrelated aggregate test was rerun.

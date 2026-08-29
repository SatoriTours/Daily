# Task 5 Report

## Status

Implemented the editable AI confirmation card, reminder lifecycle list/detail, unified reminder settings, profile CRUD, persistent profile snapshots, delivery recovery status, and notification importance/lock-screen visibility.

## TDD evidence

- RED: `./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.reminder.ReminderUiStateTest'` failed at test compilation because `ReminderDraftUiState`, filters/actions, settings defaults, and profile editor APIs did not exist.
- RED (snapshot extension): focused app tests failed because `ReminderImportance`, `ReminderLockScreenVisibility`, and notification-policy fields did not exist.
- GREEN: `./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.reminder.*Test' --tests 'com.dailysatori.core.reminder.ReminderDeliveryTest' :shared:testDebugUnitTest --tests 'com.dailysatori.data.repository.ReminderRepositoryTest' :app:assembleDebug` — BUILD SUCCESSFUL.

## Main files

- `ui/feature/reminder/ReminderDraftCard.kt`, `ReminderListScreen.kt`, `ReminderViewModel.kt`
- `ui/feature/settings/reminder/ReminderSettingsScreen.kt`, `ReminderSettingsViewModel.kt`
- `AiChatScreen.kt`, `SettingsScreen.kt`, `ViewModelModule.kt`
- `ReminderModels.kt`, `ReminderRepository.kt`, `DailySatori.sq`, `ReminderNotification.kt`
- `ReminderUiStateTest.kt`, `ReminderRepositoryTest.kt`, `ReminderDeliveryTest.kt`

## Self-review

- Confirm is atomically gated by draft UI state; retries reuse an already-created reminder instead of inserting duplicates.
- Cancel only changes transient UI state and performs no repository call.
- Scheduling errors and missing notification/exact-alarm access retain the active reminder and show warning/fallback state.
- Confirmed reminders copy their profile snapshot. Global settings are read only when a new draft is registered; existing reminders change only through explicit apply-latest.
- Delete explicitly removes reminder events before the reminder so behavior does not depend on driver-specific foreign-key settings.
- Existing snapshot JSON without importance/visibility remains readable with HIGH/PRIVATE defaults.
- The SQLDelight user hunk `selectAllDiaryAttachments` was preserved and is not part of this task's staged changes.

## Concerns

- No device/instrumentation acceptance was run for real permission return flows, disabled-channel recovery, picker ergonomics, or lock-screen rendering.
- The settings surface is information-dense on small screens and merits device UX review; functional Compose compilation is covered.

## Fix round 1

- Added horizontally scrollable dense controls and one vertically scrollable settings/reminder surface; removed the nested weighted reminder list that could be clipped on small screens.
- Empty `SelectedWeekdays` is now a validation error, is shown on the card, and disables confirmation.
- Delivery access now combines the Android runtime permission with the system-wide notification switch, uses an injectable checker/controller, and refreshes on settings-page `ON_RESUME` without polling.
- Moved all Task 5 user-visible text to matching `values` / `values-en` resources, including picker, lifecycle, profile, permission, and action labels.
- Custom backoff input preserves the raw token string and rejects malformed, empty, non-integer, over-count, and out-of-range tokens instead of dropping them.
- RED: focused tests initially failed to compile because active-day validation, strict input state, and injectable delivery access APIs were absent.
- GREEN: `ReminderUiStateTest` and `ReminderUiSourceTest` pass after the fixes.

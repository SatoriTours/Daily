# Reminder and Profile UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build yearly reminders, a compact reminder workflow, AI-assisted reminder creation, a single-line home header, and a real personal center without adding a reminder bottom-navigation tab.

**Architecture:** Extend the reminder domain and persistence model first, then expose deterministic occurrence/list projections to Compose ViewModels. Build reminder list/detail/create/settings as separate routes, and finally replace the home header plus the current settings-as-profile surface with a personal dashboard that consumes repository summaries rather than duplicating data.

**Tech Stack:** Kotlin Multiplatform, Kotlinx DateTime/Serialization/Coroutines, SQLDelight, Jetpack Compose Material 3, Navigation Compose, Koin, Android AlarmManager/WorkManager, kotlin.test/JUnit.

**Spec:** `docs/superpowers/specs/2026-08-31-reminder-and-profile-ui-design.md`

## Global Constraints

- Bottom navigation remains exactly four items: Today, Diary, Reading, AI.
- All reminder child pages use the existing centered app bar convention.
- Reminder API parsing is explicit; typing never triggers paid calls.
- Yearly recurrence is stored as recurrence data, never expanded into future duplicate rows.
- Existing reminder rows migrate as one-time reminders without changing their current behavior.
- Notification dismissal does not complete a reminder or remove it from today's badge count.
- Unsupported “Read later” and browsing-history entries must not appear in the profile UI.
- Preserve corrupt-profile quarantine and recovery behavior.
- Use the current workspace and stage only files named by the active task.

---

### Task 1: Recurrence Domain and Occurrence Projection

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/reminder/ReminderModels.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/reminder/ReminderOccurrence.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/reminder/ReminderOccurrenceTest.kt`

**Interfaces:**
- Produces: `ReminderRecurrence`, `LeapDayPolicy`, `Reminder.nextOccurrenceOnOrAfter(LocalDate): LocalDate?`, and `ReminderOccurrence(reminderId, date, startAt)`.
- Consumes: existing `Reminder`, `ReminderActiveDayRule`, `TimeZone`, and `LocalDate`.

- [ ] **Step 1: Write failing recurrence projection tests**

```kotlin
@Test fun yearlyReminderRollsIntoNextYear() {
    val reminder = reminder(recurrence = ReminderRecurrence.Yearly(9, 2, LeapDayPolicy.FEBRUARY_28))
    assertEquals(LocalDate(2027, 9, 2), reminder.nextOccurrenceOnOrAfter(LocalDate(2026, 9, 3)))
}

@Test fun leapDayUsesSelectedFallback() {
    val reminder = reminder(recurrence = ReminderRecurrence.Yearly(2, 29, LeapDayPolicy.MARCH_1))
    assertEquals(LocalDate(2027, 3, 1), reminder.nextOccurrenceOnOrAfter(LocalDate(2027, 1, 1)))
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./gradlew :shared:testDebugUnitTest --tests com.dailysatori.service.reminder.ReminderOccurrenceTest`

Expected: compilation fails because recurrence types and projection do not exist.

- [ ] **Step 3: Add recurrence types and pure date projection**

```kotlin
sealed interface ReminderRecurrence {
    data object Once : ReminderRecurrence
    data class Monthly(val dayOfMonth: Int) : ReminderRecurrence
    data class Yearly(val month: Int, val dayOfMonth: Int, val leapDayPolicy: LeapDayPolicy) : ReminderRecurrence
}

enum class LeapDayPolicy { FEBRUARY_28, MARCH_1 }

data class ReminderOccurrence(val reminderId: String, val date: LocalDate, val startAt: Instant)
```

Add `recurrence: ReminderRecurrence = ReminderRecurrence.Once` to both `ReminderDraft` and `Reminder`. Validate month `1..12`, monthly day `1..31`, and legal month/day pairs; only February 29 may require a fallback policy.

- [ ] **Step 4: Run the recurrence suite and verify GREEN**

Run: `./gradlew :shared:testDebugUnitTest --tests com.dailysatori.service.reminder.ReminderOccurrenceTest`

Expected: all recurrence and leap-day cases pass.

- [ ] **Step 5: Commit domain recurrence**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/reminder/ReminderModels.kt shared/src/commonMain/kotlin/com/dailysatori/service/reminder/ReminderOccurrence.kt shared/src/commonTest/kotlin/com/dailysatori/service/reminder/ReminderOccurrenceTest.kt
git commit -m "feat: model recurring reminder occurrences"
```

### Task 2: Persist Recurrence and Migrate Existing Reminders

**Files:**
- Modify: `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/ReminderRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/reminder/ReminderDraftCodec.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/data/repository/ReminderRepositoryTest.kt`
- Modify: `shared/src/commonTest/kotlin/com/dailysatori/service/migration/ReminderMigrationTest.kt`
- Create: `shared/src/commonTest/kotlin/com/dailysatori/service/reminder/ReminderDraftCodecTest.kt`

**Interfaces:**
- Consumes: recurrence types from Task 1.
- Produces: encoded values `once`, `monthly:<day>`, `yearly:<month>:<day>:<policy>` and repository round trips.

- [ ] **Step 1: Write persistence and migration failures**

```kotlin
@Test fun yearlyRecurrenceRoundTrips() = withRepository { repo ->
    val saved = repo.createConfirmed(draft().copy(recurrence = ReminderRecurrence.Yearly(9, 2, LeapDayPolicy.FEBRUARY_28)), strongProfile())
    assertEquals(ReminderRecurrence.Yearly(9, 2, LeapDayPolicy.FEBRUARY_28), repo.get(saved.id)?.recurrence)
}

@Test fun legacyReminderMigratesAsOnce() {
    migrateFromVersion23()
    assertEquals("once", queries.selectReminderById("legacy").executeAsOne().recurrence_rule)
}
```

- [ ] **Step 2: Run repository, codec, and migration tests to verify RED**

Run: `./gradlew :shared:testDebugUnitTest --tests '*ReminderRepositoryTest' --tests '*ReminderDraftCodecTest' --tests '*ReminderMigrationTest'`

Expected: schema/model compilation fails for `recurrence_rule`.

- [ ] **Step 3: Add bounded recurrence storage**

Add to `reminder`:

```sql
recurrence_rule TEXT NOT NULL DEFAULT 'once'
```

Add the same column through migration version 24. Update insert/update/select mapping and draft JSON. Decoder must reject malformed rules rather than silently converting them. Existing rows receive `once`.

- [ ] **Step 4: Regenerate SQLDelight and verify GREEN**

Run: `./gradlew :shared:testDebugUnitTest --tests '*ReminderRepositoryTest' --tests '*ReminderDraftCodecTest' --tests '*ReminderMigrationTest'`

Expected: recurrence round-trip, legacy migration, invalid encoding, and corrupt-profile tests pass.

- [ ] **Step 5: Commit recurrence persistence**

```bash
git add shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt shared/src/commonMain/kotlin/com/dailysatori/data/repository/ReminderRepository.kt shared/src/commonMain/kotlin/com/dailysatori/service/reminder/ReminderDraftCodec.kt shared/src/commonTest
git commit -m "feat: persist recurring reminder rules"
```

### Task 3: Recurring Scheduling and Today's Pending Count

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/reminder/ReminderScheduleEngine.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/reminder/ReminderCoordinator.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/reminder/ReminderSummary.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/reminder/ReminderScheduleEngineTest.kt`
- Create: `shared/src/commonTest/kotlin/com/dailysatori/service/reminder/ReminderSummaryTest.kt`
- Test: `app/src/test/kotlin/com/dailysatori/core/reminder/ReminderCoordinatorTest.kt`

**Interfaces:**
- Produces: `ReminderSummary.todayPendingCount(reminders, today)` and recurrence-aware cutoff rollover.
- Consumes: persisted recurrence and occurrence projection.

- [ ] **Step 1: Add failing rollover and badge-count tests**

```kotlin
@Test fun dismissedReminderStillCountsAsPendingToday() {
    assertEquals(1, ReminderSummary.todayPendingCount(listOf(reminder(status = ReminderStatus.DISMISSED)), today))
}

@Test fun completedYearlyCycleSchedulesNextYear() {
    assertEquals(LocalDate(2027, 9, 2), nextCycleDate(yearlyReminder, LocalDate(2026, 9, 2)))
}
```

- [ ] **Step 2: Run focused scheduling tests and verify RED**

Run: `./gradlew :shared:testDebugUnitTest --tests '*ReminderScheduleEngineTest' --tests '*ReminderSummaryTest' :app:testDebugUnitTest --tests '*ReminderCoordinatorTest'`

Expected: yearly rollover and summary APIs are missing.

- [ ] **Step 3: Implement recurrence-aware cycle ownership**

Keep one database reminder row. At daily cutoff, advance a repeatable reminder to its next occurrence instead of terminally expiring it. Count `ACTIVE`, `NOTIFIED`, and `DISMISSED` occurrences active today; exclude `PAUSED`, `COMPLETED`, `EXPIRED`, future occurrences, and quarantined reminders.

- [ ] **Step 4: Run scheduling tests and verify GREEN**

Run: `./gradlew :shared:testDebugUnitTest --tests '*ReminderScheduleEngineTest' --tests '*ReminderSummaryTest' :app:testDebugUnitTest --tests '*ReminderCoordinatorTest'`

Expected: timezone, cutoff, dismissal, yearly rollover, and count tests pass.

- [ ] **Step 5: Commit recurring scheduling**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/reminder app/src/main/kotlin/com/dailysatori/core/reminder app/src/test/kotlin/com/dailysatori/core/reminder shared/src/commonTest/kotlin/com/dailysatori/service/reminder
git commit -m "feat: schedule recurring reminder cycles"
```

### Task 4: Reminder List Projection and Compact List UI

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/reminder/ReminderListState.kt`
- Replace responsibilities in: `app/src/main/kotlin/com/dailysatori/ui/feature/reminder/ReminderListScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/reminder/ReminderViewModel.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Create: `app/src/test/kotlin/com/dailysatori/ui/feature/reminder/ReminderListStateTest.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/reminder/ReminderUiStateTest.kt`

**Interfaces:**
- Produces: `ReminderListMode { RECENT, MONTHS, FINISHED }`, `ReminderListFilter`, `ReminderListItemUi`, `ReminderMonthUi`, and `buildReminderListState(...)`.
- Consumes: recurrence occurrence projection and repository Flow.

- [ ] **Step 1: Write failing list projection tests**

```kotlin
@Test fun recentSortsByNextOccurrenceAcrossYearBoundary() {
    val state = buildReminderListState(reminders, now, ReminderListMode.RECENT, ReminderListFilter())
    assertEquals(listOf("september", "january-next-year"), state.sections.flatMap { it.items }.map { it.id })
}

@Test fun filterPanelStateDoesNotChangeListScrollKey() {
    assertEquals(closed.listIdentity, opened.listIdentity)
}
```

- [ ] **Step 2: Run reminder UI-state tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReminderListStateTest' --tests '*ReminderUiStateTest'`

Expected: list mode and projection types are missing.

- [ ] **Step 3: Implement pure projection then Compose rendering**

Use a centered title, search/settings actions, compact segmented control, one-line 30-day context, time-grouped cards, month density grid, and fixed add FAB. The filter panel is anchored directly under the toolbar, dims the list without moving it, and hides the FAB while open.

- [ ] **Step 4: Run UI-state tests and compile**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReminderListStateTest' --tests '*ReminderUiStateTest' :app:assembleDebug`

Expected: list projections pass and Compose compiles.

- [ ] **Step 5: Commit reminder list**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/reminder app/src/main/res/values app/src/main/res/values-en app/src/test/kotlin/com/dailysatori/ui/feature/reminder
git commit -m "feat: redesign recurring reminder list"
```

### Task 5: Separate Reminder Routes, Detail, Create, and Edit

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/core/navigation/Routes.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/navigation/NavHost.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/reminder/ReminderDetailScreen.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/reminder/ReminderEditScreen.kt`
- Refactor: `app/src/main/kotlin/com/dailysatori/ui/feature/reminder/ReminderDraftCard.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/reminder/ReminderViewModel.kt`
- Create: `app/src/test/kotlin/com/dailysatori/ui/feature/reminder/ReminderRouteStateTest.kt`

**Interfaces:**
- Produces: `ReminderListRoute`, `ReminderDetailRoute(id)`, `ReminderEditRoute(id?)`, and reusable `ReminderEditorState`.
- Consumes: list item IDs, existing repository edits, profiles, and scheduler recompute.

- [ ] **Step 1: Write failing route/editor summary tests**

```kotlin
@Test fun yearlyEditorSummaryExplainsActualBehavior() {
    assertEquals("每年9月2日至4日，20:00开始提醒；工作时间仅显示通知，不播放声音。", editor.actualBehaviorSummary())
}
```

- [ ] **Step 2: Run route-state tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReminderRouteStateTest'`

Expected: separate routes and editor state do not exist.

- [ ] **Step 3: Implement separate read and edit surfaces**

Detail uses a timeline and explicit Complete/Pause/Edit/Delete actions. Create/edit share the same editor state, expose Once/Monthly/Yearly/Consecutive choices, require leap-day fallback when applicable, and show an actual-behavior summary before save.

- [ ] **Step 4: Run route tests and assemble**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReminderRouteStateTest' --tests '*ReminderUiStateTest' :app:assembleDebug`

Expected: routes, editor validation, summaries, and build pass.

- [ ] **Step 5: Commit reminder routes**

```bash
git add app/src/main/kotlin/com/dailysatori/core/navigation app/src/main/kotlin/com/dailysatori/ui/feature/reminder app/src/test/kotlin/com/dailysatori/ui/feature/reminder
git commit -m "feat: add reminder detail and editor routes"
```

### Task 6: Explicit Local-First AI Reminder Parsing

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/reminder/ReminderTextInterpreter.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/reminder/ReminderDraftCodec.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/reminder/ReminderViewModel.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/reminder/ReminderTextInterpreterTest.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/reminder/ReminderAiParseStateTest.kt`

**Interfaces:**
- Produces: `suspend fun ReminderTextInterpreter.interpret(text: String, now: Instant, zone: TimeZone): ReminderInterpretation` and content-hash cache.
- Consumes: current AI client configuration and `ReminderDraftCodec`; never saves directly.

- [ ] **Step 1: Write local, cache, and fallback failures**

```kotlin
@Test fun exactSameTextUsesCachedInterpretation() = runBlocking {
    interpreter.interpret(text, now, zone)
    interpreter.interpret(text, now, zone)
    assertEquals(1, remote.calls)
}

@Test fun typingDoesNotInterpretUntilExplicitSubmit() {
    state = state.onPromptChanged("每年9月2日提醒我充值")
    assertEquals(0, fakeInterpreter.calls)
}
```

- [ ] **Step 2: Run parser-state tests and verify RED**

Run: `./gradlew :shared:testDebugUnitTest --tests '*ReminderTextInterpreterTest' :app:testDebugUnitTest --tests '*ReminderAiParseStateTest'`

Expected: interpreter and explicit-submit state are missing.

- [ ] **Step 3: Implement local-first explicit parsing**

Parse strict date/time/yearly/monthly phrases locally. Only explicit submit calls the configured AI fallback. Cache successful results by normalized text plus timezone; mark missing/conflicting fields for confirmation; preserve input on all failures.

- [ ] **Step 4: Run parser tests and verify GREEN**

Run: `./gradlew :shared:testDebugUnitTest --tests '*ReminderTextInterpreterTest' :app:testDebugUnitTest --tests '*ReminderAiParseStateTest'`

Expected: local parsing, one-call caching, fallback, missing configuration, and failure preservation pass.

- [ ] **Step 5: Commit AI parsing**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/reminder shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt shared/src/commonTest/kotlin/com/dailysatori/service/reminder app/src/main/kotlin/com/dailysatori/ui/feature/reminder app/src/test/kotlin/com/dailysatori/ui/feature/reminder
git commit -m "feat: parse reminder text on explicit request"
```

### Task 7: Card-Based Reminder Settings

**Files:**
- Refactor: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/reminder/ReminderSettingsScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/reminder/ReminderSettingsViewModel.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/reminder/ReminderProfileManagementScreen.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/settings/reminder/ReminderSettingsStateTest.kt`

**Interfaces:**
- Produces: compact default-rhythm, notification-effect, quiet-rule, and advanced profile sections.
- Consumes: existing settings state and profile repository; does not contain a reminder list.

- [ ] **Step 1: Write failing settings projection tests**

```kotlin
@Test fun settingsSummarySeparatesDefaultsFromProfileManagement() {
    assertEquals("22:00–24:00 · 每小时", state.defaultRhythm.eveningSummary)
    assertFalse(state.primarySections.any { it.id == "profiles" })
}
```

- [ ] **Step 2: Run settings tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReminderSettingsStateTest'`

Expected: section projection is missing.

- [ ] **Step 3: Refactor settings into focused cards**

Remove embedded list. Keep default profile, rhythm, sound/vibration/lock-screen, sleep `00:00–09:00`, work quiet hours, and delivery access. Move built-in/custom profile management to its own page under Advanced.

- [ ] **Step 4: Run tests and assemble**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReminderSettingsStateTest' :app:assembleDebug`

Expected: settings projections and build pass.

- [ ] **Step 5: Commit reminder settings**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/settings/reminder app/src/test/kotlin/com/dailysatori/ui/feature/settings/reminder
git commit -m "feat: simplify reminder settings hierarchy"
```

### Task 8: Single-Line Home Header and Personal Center

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/component/appbar/HomeCompactHeader.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/profile/ProfileScreen.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/profile/ProfileViewModel.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/profile/DataPrivacyScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/navigation/Routes.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/navigation/NavHost.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/home/HomeCompactHeaderTest.kt`
- Create: `app/src/test/kotlin/com/dailysatori/ui/feature/profile/ProfileStateTest.kt`

**Interfaces:**
- Produces: `HomeCompactHeader(avatarBadgeCount, tabs, selectedTab, onAvatar, onSearch)`, `ProfileUiState`, and `ProfileRoute`.
- Consumes: `ReminderSummary.todayPendingCount`, favorite counts, external source state, and async task summary.

- [ ] **Step 1: Write failing header/profile tests**

```kotlin
@Test fun profileContainsOnlyRealDestinations() {
    assertEquals(listOf("reminders", "favorites", "external_favorites", "tasks", "settings", "privacy"), state.destinations.map { it.id })
    assertFalse(state.destinations.any { it.id in setOf("read_later", "history") })
}

@Test fun reminderBadgeIsHiddenWhenCountIsZero() {
    assertEquals(null, reminderBadgeLabel(0))
    assertEquals("9+", reminderBadgeLabel(12))
}
```

- [ ] **Step 2: Run home/profile tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*HomeCompactHeaderTest' --tests '*ProfileStateTest'`

Expected: compact header and profile state do not exist.

- [ ] **Step 3: Implement compact home navigation and profile dashboard**

Keep four bottom tabs. News uses avatar / news tabs / search in one row. Avatar opens Profile. Profile contains Today Reminders with View All/Add, Favorites, External Favorites, Sync and Tasks, Settings, and Data/Privacy. Do not add Read Later, History, duplicate Diary/Reading shortcuts, or a reminder bottom tab.

- [ ] **Step 4: Run tests and assemble**

Run: `./gradlew :app:testDebugUnitTest --tests '*HomeCompactHeaderTest' --tests '*ProfileStateTest' --tests '*UnifiedNewsBehaviorTest' :app:assembleDebug`

Expected: compact header, badge semantics, profile destinations, navigation, and build pass.

- [ ] **Step 5: Commit home/profile redesign**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/component/appbar app/src/main/kotlin/com/dailysatori/ui/feature/home app/src/main/kotlin/com/dailysatori/ui/feature/profile app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews app/src/main/kotlin/com/dailysatori/core/navigation app/src/test/kotlin/com/dailysatori/ui/feature/home app/src/test/kotlin/com/dailysatori/ui/feature/profile
git commit -m "feat: add compact home header and profile center"
```

### Task 9: Integrated Verification and Device Acceptance

**Files:**
- Modify only if a failing in-scope test exposes a defect in Tasks 1–8.

**Interfaces:**
- Consumes all previous tasks; produces a verified debug APK and device acceptance record in the final response.

- [ ] **Step 1: Run reminder and navigation suites together**

Run: `./gradlew :shared:testDebugUnitTest --tests '*Reminder*Test' :app:testDebugUnitTest --tests '*Reminder*Test' --tests '*Profile*Test' --tests '*HomeCompactHeaderTest'`

Expected: all in-scope tests pass.

- [ ] **Step 2: Run build and static diff checks**

Run: `./gradlew :app:assembleDebug`

Run: `git diff --check`

Expected: Debug APK builds and no whitespace errors exist.

- [ ] **Step 3: Install without clearing data**

```bash
adb connect 192.168.2.120:42519
adb -s 192.168.2.120:42519 install -r app/build/outputs/apk/debug/app-debug.apk
```

Expected: `Success`; existing reminders and settings remain.

- [ ] **Step 4: Verify device behavior**

Confirm: one-line news header, profile navigation, today badge count, add/edit/yearly reminder flows, top-anchored filter, notification-to-detail route, notification dismissal count, process restart recovery, sleep quiet hours, work-hour sound suppression, and next-year rollover using a controlled near-term test reminder.

- [ ] **Step 5: Commit only genuine acceptance fixes**

If Step 4 required code fixes, stage only those files and commit:

```bash
git commit -m "fix: close reminder redesign acceptance findings"
```

If no code changed, do not create an empty commit.

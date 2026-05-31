# Daily Satori Phased Refactor Design

## Goal

Refactor Daily Satori across the whole project in safe phases while keeping existing features, UI, text, navigation, data, and runtime behavior unchanged.

The refactor should make the code simpler, reduce duplication, improve architecture boundaries, and align implementation with the project guides:

- `app/` owns Android UI and ViewModels.
- `shared/` owns repositories, services, KMP business logic, and data access.
- ViewModels receive dependencies through Koin constructor injection.
- Composables do not directly call repositories or services.
- Repository classes stay focused on data access.
- Service classes own business logic.
- Styling continues to use the existing theme system.

## Non-Goals

- No feature changes.
- No UI redesign or visual polish.
- No database schema changes unless separately approved.
- No emulator deployment.
- No broad rewrites without compile/build/device validation between phases.

## Refactor Strategy

Use vertical, phased slices instead of a large horizontal rewrite. Each phase touches a bounded set of related files and ends with verification. This keeps regressions easy to locate and protects behavior.

The preferred order is low-risk to high-risk:

1. Dependency injection and boundary cleanup.
2. Diary module cleanup.
3. Settings and configuration cleanup.
4. News module cleanup.
5. Shared service cleanup.
6. Style constant consolidation.

## Phase 1: Dependency Injection And Boundaries

### Scope

- `app/src/main/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigEditScreen.kt`
- `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MemorySearchSheet.kt`
- `app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsViewModel.kt`
- `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`

### Design

Move direct dependency lookups out of UI and ViewModel internals. Dependencies should be supplied through ViewModel constructors registered in Koin.

Expected changes:

- Add a ViewModel for AI config editing if needed, preserving the existing screen state and save/test flows.
- Add a ViewModel for memory search/rebuild if needed, preserving the existing sheet behavior.
- Inject `SettingRepository` into `SettingsViewModel` rather than calling Koin from inside the ViewModel.
- Prefer named constructor arguments in Koin registrations where current registrations are hard to read.

### Acceptance Criteria

- UI screens look and behave the same.
- No Composable directly owns repository/service business operations for the touched flows.
- ViewModels follow `MutableStateFlow`/`StateFlow` and constructor injection patterns.

## Phase 2: Diary Module Cleanup

### Scope

- `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryScreen.kt`
- `app/src/main/kotlin/com/dailysatori/ui/component/card/DiaryCard.kt`
- `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryViewModel.kt`
- New small files under the existing diary/component packages only if they reduce file responsibility.

### Design

Keep the diary page visually identical while reducing mixed responsibilities.

Expected changes:

- Extract repeated diary tag/image parsing into pure helpers.
- Extract date/month label formatting from the screen into focused pure helpers.
- Split large diary UI sections only when it makes ownership clearer.
- Preserve the current timeline grouping, month summary copy, tag filtering, editor sheet, and delete confirmation behavior.

### Acceptance Criteria

- Diary list grouping and card content are unchanged.
- Existing search, tag filter, edit, delete, and image display behavior remains unchanged.
- Pure helpers have focused tests where practical.

## Phase 3: Settings And Configuration Cleanup

### Scope

- `app/src/main/kotlin/com/dailysatori/ui/feature/settings/**`
- `app/src/main/kotlin/com/dailysatori/ui/feature/aiconfig/**`
- Related Koin registrations.

### Design

Normalize settings and configuration screens around the existing ViewModel pattern.

Expected changes:

- Keep UI screens thin: render state, call ViewModel events.
- Move save/test/toggle/rebuild operations into ViewModels or shared services.
- Avoid duplicating try/catch and loading/error state patterns where a local helper can keep code clearer.
- Keep all current Chinese UI text and flows unchanged.

### Acceptance Criteria

- AI config add/edit/test flows are unchanged.
- Settings update checks, web server controls, backup, restore, plugin, skill, MCP, and remote news settings behavior remain unchanged.
- No direct repository/service business operation is introduced in Composables.

## Phase 4: News Module Cleanup

### Scope

- `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`
- `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/**`
- `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/**`
- Related shared services only when needed for clear boundaries.

### Design

Reduce news module complexity without changing data flow.

Expected changes:

- Extract pure state transition helpers from `UnifiedNewsViewModel` where they clarify behavior.
- Keep remote source loading, summary regeneration, local article navigation, favorite toggling, and citation detail behavior unchanged.
- Avoid moving business logic into UI components.
- Avoid changing remote API calls, cache keys, or database writes in this phase unless required by tests.

### Acceptance Criteria

- Unified news summary generation and refresh behavior remains unchanged.
- Remote source article loading and detail navigation remains unchanged.
- Local article favorite behavior remains unchanged.
- Existing debug-build behavior is preserved.

## Phase 5: Shared Service Cleanup

### Scope

- `shared/src/commonMain/kotlin/com/dailysatori/service/**`
- `shared/src/commonMain/kotlin/com/dailysatori/data/repository/**`
- `shared/src/commonTest/**`

### Design

Only refactor shared services with test protection. Shared code contains high-value business behavior for AI, MCP, article parsing, backup, remote news, unified news, books, and memory extraction, so changes should be conservative.

Expected changes:

- Simplify long constructors or Koin registrations only where readability improves.
- Extract duplicated formatter/parser/policy logic into small pure functions.
- Keep repositories focused on SQLDelight data access.
- Keep services responsible for orchestration and business decisions.
- Do not change database schema in this refactor unless a separate migration task is approved.

### Acceptance Criteria

- Existing shared tests pass or are updated only to reflect equivalent behavior.
- No AI prompt, remote API, backup format, or database behavior changes accidentally.
- No sensitive information is logged.

## Phase 6: Style Constant Consolidation

### Scope

- High-frequency UI components and feature screens that contain repeated hard-coded dimensions.
- `app/src/main/kotlin/com/dailysatori/ui/theme/**` only if new constants are justified.

### Design

Consolidate obvious repeated style values into the existing theme system while preserving the current look.

Expected changes:

- Replace repeated spacing/radius/elevation values with existing theme constants where equivalent.
- Add new constants only when the value is reused and names a real design concept.
- Leave one-off layout tuning values alone if extracting them would reduce clarity or risk visual drift.

### Acceptance Criteria

- Screens remain visually equivalent.
- Theme usage becomes more consistent.
- No broad visual redesign occurs.

## Verification

Each implementation phase must finish with:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
adb shell am start -n com.dailysatori/.MainActivity
```

The app must be installed only to a connected phone. Do not deploy to an emulator.

## Risk Management

- Keep each phase small enough to review independently.
- Prefer moving existing code over rewriting it.
- Prefer pure helpers and constructor injection over new abstraction layers.
- Add tests around extracted pure behavior when practical.
- Do not revert unrelated user or agent changes in the worktree.
- Stop and ask before changing database schema, feature behavior, API behavior, backup format, or visible UI behavior.

## Open Decisions

- Exact file split names will be chosen during implementation to match existing package conventions.
- Each phase should get its own implementation plan before code changes begin.

# Book Skill Settings Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix book bottom sheets, cap imported book viewpoints at 20, add Skill test UI state, and make Settings system back return to news summary.

**Architecture:** Keep all changes local to existing book, Skill settings, and unified news screens. Add small pure helpers for testable UI/behavior decisions, and wire them into current Compose/ViewModel code without introducing new navigation or agent runtime layers.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Kotlin coroutines, KMP shared services, existing Gradle Android unit tests.

---

### Task 1: Book Sheet UI Helpers And Picker Scroll

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/component/BookSearchSheets.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookSearchUiTextTest.kt`

- [ ] Add failing tests asserting bottom sheet helpers: `bookPickerUsesLazyList() == true`, `bookPickerBottomPaddingDp() >= 32`, `bookResultIntroductionPreviewLength() >= 160`, and `bookResultPrimaryActionText(false) == "添加并分析"`.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.book.BookSearchUiTextTest"` and verify the new tests fail because helpers are missing.
- [ ] Add the helpers in `BooksScreen.kt` / `BookSearchViewModel.kt` and use a bounded `LazyColumn` in the book picker sheet with bottom padding.
- [ ] Update `BookSearchResultCard` to show introduction as the main body and use cleaner primary/secondary buttons.
- [ ] Rerun the same test and verify it passes.

### Task 2: Cap Book Viewpoint Imports At 20

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookSearchViewModel.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookSearchUiTextTest.kt`

- [ ] Add failing tests for `bookViewpointImportLimit() == 20` and `bookViewpointDraftsForImport((1..25).toList()).size == 20`.
- [ ] Run the targeted app book test and verify failure.
- [ ] Add `bookViewpointImportLimit()` and `bookViewpointDraftsForImport()` helpers, then call the helper before inserting viewpoints.
- [ ] Rerun the targeted app book test and verify it passes.

### Task 3: Skill Test Button State

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/skills/SkillSettingsViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/skills/SkillSettingsScreen.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/settings/skills/SkillSettingsTextTest.kt`

- [ ] Add failing tests for labels `skillTestButtonText(false) == "测试 Skill"`, `skillTestButtonText(true) == "测试中..."`, validation failures for missing gateway/token, and non-persistence by testing state only.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.settings.skills.SkillSettingsTextTest"` and verify failure.
- [ ] Add state fields `isTesting` and `testMessage`, helper validation, `testSkill(input)`, and `consumeTestMessage()` if needed.
- [ ] Add the test button and status text to `SkillEditScreen`; keep save behavior unchanged.
- [ ] Rerun targeted Skill settings test and verify it passes.

### Task 4: Settings Back Navigation

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] Add a failing source-level test asserting `SettingsScreen(settingsViewModel, onBack = { viewModel.switchPage(UnifiedNewsPage.SUMMARY) })` exists.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.UnifiedNewsBehaviorTest"` and verify failure.
- [ ] Pass the `onBack` callback when rendering Settings from `UnifiedNewsPage.SETTINGS`.
- [ ] Rerun the targeted test and verify it passes.

### Task 5: Final Verification And Device Install

**Files:**
- No production files beyond Tasks 1-4.

- [ ] Run `./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.book.*"`.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.book.*" --tests "com.dailysatori.ui.feature.settings.skills.*" --tests "com.dailysatori.UnifiedNewsBehaviorTest"`.
- [ ] Run `./gradlew :app:compileDebugKotlin`.
- [ ] Run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`.
- [ ] Run `adb -s ba5e2328 shell am start -n com.dailysatori/.MainActivity`.

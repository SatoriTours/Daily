# External Favorites Settings Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the external favorites settings page to match the approved dark mockup interaction: health summary first, source cards with clear state/action hierarchy, and a cleaner X connection page.

**Architecture:** Keep the existing ViewModel, repository, OAuth, and sync worker behavior. Add small text/state helpers for summary metrics and action labels, then restructure `ExternalFavoritesSettingsScreen.kt` to render the approved layout with reusable local composables.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, existing Daily Satori settings components, Gradle unit tests.

---

### Task 1: Lock User-Facing Helper Contract

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsTextTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsViewModel.kt`

- [ ] **Step 1: Write failing helper tests**

Add tests for summary metric values, provider badge text, menu delete label, auth repair action label, and add-page privacy notes.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.settings.externalfavorites.*'`

Expected: fails because the new helper functions do not exist yet.

- [ ] **Step 3: Implement minimal helpers**

Add pure helper functions in `ExternalFavoritesSettingsViewModel.kt`, keeping existing behavior intact.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.settings.externalfavorites.*'`

Expected: pass.

### Task 2: Rebuild Source List Page Layout

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsScreen.kt`

- [ ] **Step 1: Replace section-card summary with approved summary panel**

Render a compact header panel with icon, title, subtitle, and three metrics.

- [ ] **Step 2: Replace source cards**

Render identity row, health pill, detail block, optional notice/error, one-line primary/history/more actions, and move delete behind the more action.

- [ ] **Step 3: Preserve interactions**

Keep add, sync now, import older, pause/enable, delete confirmation, and OAuth browser launch behavior unchanged.

### Task 3: Rebuild Empty And Add Pages

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsScreen.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesAddPageTextTest.kt`

- [ ] **Step 1: Match empty state mockup**

Show summary panel, centered empty card, and three-step explanation.

- [ ] **Step 2: Match add page mockup**

Show helper panel, Client ID field, three read-only notes, primary connect button, and cancel action.

### Task 4: Verify

**Files:**
- No new files.

- [ ] **Step 1: Run focused tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.settings.externalfavorites.*'`

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 3: Build debug APK**

Run: `./gradlew :app:assembleDebug`

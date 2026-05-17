# Remote News UI Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve remote news settings list/editor and remote article detail readability while preserving existing behavior.

**Architecture:** Keep existing ViewModel/data flow. Update Compose UI only, using MaterialTheme plus existing Spacing/Radius/IconSize tokens. Add source-text regression tests to prevent reverting to the rough list/form and raw detail layout.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Gradle unit tests.

---

### Task 1: Remote Settings List and Editor Polish

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/remotenews/RemoteNewsSettingsScreen.kt`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] Add assertions for status dot, URL label chip, full-row click, helper card, password visibility toggle, and result message card.
- [ ] Update list rows to show status dot, service name, URL, enabled state, and full URL chip.
- [ ] Update editor page with helper card, password visual transformation toggle, grouped action buttons, and message card.
- [ ] Run focused unified news tests.

### Task 2: Remote Article Detail Reading Polish

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsScreen.kt`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] Add assertions for article hero card, metadata chips, summary card, viewpoints section, content section, and original link card.
- [ ] Replace raw detail layout with a readable article detail screen.
- [ ] Keep existing remote article data model and navigation behavior.
- [ ] Run focused unified news tests.

### Task 3: Verification and Install

- [ ] Run `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest --no-configuration-cache`.
- [ ] Run `./gradlew :app:compileDebugKotlin --no-configuration-cache`.
- [ ] Run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug --no-configuration-cache`.
- [ ] Run `adb shell am force-stop com.dailysatori && adb shell am start -n com.dailysatori/.MainActivity`.

Self-review: Covers the two requested pages only, avoids data model changes, uses existing theme tokens, and includes verification steps.

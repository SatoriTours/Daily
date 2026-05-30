# AI Compact Bottom Bar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the AI tab bottom navigation with a compact home-plus-input bar and remove the AI page’s separate standalone input.

**Architecture:** `HomeScreen` remains responsible for top-level tab state and bottom bar selection. `AiChatScreen` exposes the chat input controls to its caller so the home-level AI compact bar can submit/stop messages without duplicating input state.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Kotlin test, Gradle.

---

## File Structure

- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt`: render normal 4-tab navigation for non-AI tabs and compact AI input bar for the AI tab.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt`: remove the internal `Scaffold.bottomBar` input and provide input state/actions to `HomeScreen`.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/ChatInputBar.kt`: extract reusable compact input content if needed while preserving current full-width input behavior.
- Modify tests under `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt` and/or home tests to cover the behavior.

### Task 1: Red Tests

- [ ] Add tests for `homeUsesCompactAiBottomBarOnAiTab`, `compactAiHomeButtonReturnsToTodayTab`, and `aiChatScreenDoesNotOwnStandaloneBottomInput`.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.aichat.AiChatUiStateTest` and confirm failure.

### Task 2: Compact Bar Implementation

- [ ] Hoist AI input controls from `AiChatScreen` to `HomeScreen` through a small callback/state holder.
- [ ] Render compact AI bar with a home icon on the left and reusable chat input field on the right.
- [ ] Hide normal tab icons on the AI tab; clicking home selects `TODAY_TAB_INDEX`.

### Task 3: Verification

- [ ] Run the targeted unit test.
- [ ] Run `./gradlew :app:compileDebugKotlin`.
- [ ] Run `./gradlew :app:assembleDebug`.
- [ ] Run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`.
- [ ] Run `adb shell am start -n com.dailysatori/.MainActivity`.
